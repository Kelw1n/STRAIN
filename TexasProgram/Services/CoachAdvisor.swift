import Foundation

/// Ключ доступа к разбору, зашитый в приложение.
///
/// Лежит кусками и в base64 — так его не видно в `strings` над бинарником.
/// Это не защита: кто захочет, достанет за минуту. Смысл ровно один — чтобы
/// другу не пришлось заводить свой ключ.
enum CoachSecrets {
    private static let parts = [
        "QVEuQWI4Uk42Sy1aMG1CVnk2",
        "bDBIbTk4ckw2RWFja09vTjJo",
        "RVBETVZ3czlqd1hzZjF5ZlE="
    ]

    static var embeddedKey: String {
        guard let data = Data(base64Encoded: parts.joined()),
              let text = String(data: data, encoding: .utf8) else { return "" }
        return text
    }

    /// Модель по умолчанию. Вынесена в настройки: если Google переименует её,
    /// приложение чинится вводом строки, а не пересборкой.
    static let defaultModel = "gemini-2.5-flash"
}

/// Ошибки разбора — каждая со своим человеческим объяснением.
enum CoachError: LocalizedError {
    case noData
    case regionBlocked
    case badKey
    case quota
    case server(Int, String)
    case emptyAnswer

    var errorDescription: String? {
        switch self {
        case .noData:
            return "Пока нечего разбирать: нет ни заметок, ни записанных подходов."
        case .regionBlocked:
            return "Google не отвечает из этого региона. Включи VPN и попробуй снова."
        case .badKey:
            return "Ключ не принят. Проверь его в настройках."
        case .quota:
            return "Лимит запросов исчерпан. Попробуй позже."
        case .server(let code, let message):
            return "Google ответил ошибкой \(code). \(message)"
        case .emptyAnswer:
            return "Ответ пришёл пустым. Попробуй ещё раз."
        }
    }
}

/// Разбор тренировок по заметкам и записанным подходам.
///
/// Считает не модель: веса, прибавки и застой приложение знает само. Модель
/// нужна ровно для прозы — прочитать, что человек написал про самочувствие,
/// и связать это с цифрами.
enum CoachAdvisor {

    // MARK: - Сбор данных

    /// Собирает всё, что стоит показать модели, в один текст.
    ///
    /// Отправляем только тренировочные цифры и заметки. Имя профиля и даты
    /// рождения тут не нужны, поэтому их и нет.
    static func context(for profile: ProgramProfile, now: Date = Date()) -> String {
        var lines: [String] = []

        lines.append("Программа: \(profile.programKind.rawValue).")
        lines.append("Максимумы (\(profile.maximumLabel)): присед \(fmt(profile.squat5RM)), жим \(fmt(profile.bench5RM)), тяга \(fmt(profile.deadlift5RM)) кг.")
        lines.append("Отмечено тренировок: \(profile.completedDayCount) из \(profile.totalDays). Цикл \(profile.cycleNumber).")

        let streak = profile.weekStreak
        if streak.current > 0 || streak.best > 0 {
            lines.append("Серия недель без пропусков: сейчас \(streak.current), лучшая \(streak.best).")
        }

        if !profile.activeSkips.isEmpty {
            let text = profile.activeSkips.map { $0.title + ($0.holdsProgression ? " (веса задержаны)" : "") }.joined(separator: "; ")
            lines.append("Пропущено: \(text).")
        }

        let tonnage = profile.weeklyTonnage.suffix(6)
        if !tonnage.isEmpty {
            let text = tonnage.map { "неделя \($0.week) — \(Int($0.total.rounded())) кг" }.joined(separator: ", ")
            lines.append("Тоннаж по неделям: \(text).")
        }

        // Ответы авторегуляции — самое ценное: это оценка усилия своими словами.
        if !profile.liftReports.isEmpty {
            let own = profile.liftReports
                .filter { profile.isOwnKey($0.key) }
                .sorted { $0.date < $1.date }
                .suffix(9)
            if !own.isEmpty {
                let text = own.map { "\($0.lift.rawValue): \($0.outcome.rawValue)" }.joined(separator: "; ")
                lines.append("Как проходили тяжёлые дни (от старых к свежим): \(text).")
            }
        }

        if !profile.stalledLifts.isEmpty {
            lines.append("Приложение считает, что встали: \(profile.stalledLifts.map(\.rawValue).joined(separator: ", ")).")
        }

        // Заметки — ради них всё и затевалось.
        let prefix = profile.keyPrefix
        let notes = profile.workoutNotes
            .filter { profile.isOwnKey($0.key) }
            .sorted { order($0.key, prefix) < order($1.key, prefix) }
            .suffix(12)
        if !notes.isEmpty {
            lines.append("")
            lines.append("Заметки с тренировок:")
            for (key, text) in notes {
                lines.append("— \(readable(key: key, prefix: profile.keyPrefix)): \(text)")
            }
        }

        // Записанные подходы за последние недели: сколько реально поднято.
        let entries = recentEntries(profile)
        if !entries.isEmpty {
            lines.append("")
            lines.append("Записанные подходы:")
            lines.append(contentsOf: entries)
        }

        return lines.joined(separator: "\n")
    }

    /// Порядок дня внутри цикла: неделя важнее дня, префикс в счёт не идёт.
    private static func order(_ key: String, _ prefix: String) -> Int {
        let rest = key.hasPrefix(prefix) ? String(key.dropFirst(prefix.count)) : key
        let parts = rest.split(separator: "-").compactMap { Int($0) }
        guard let week = parts.first else { return 0 }
        return week * 100 + (parts.count > 1 ? parts[1] : 0)
    }

    /// Есть ли вообще что разбирать.
    static func hasData(_ profile: ProgramProfile) -> Bool {
        let notes = profile.workoutNotes.filter { profile.isOwnKey($0.key) }
        return !notes.isEmpty || !profile.setLog.isEmpty || !profile.liftReports.isEmpty
    }

    private static func recentEntries(_ profile: ProgramProfile, limit: Int = 24) -> [String] {
        let prefix = profile.keyPrefix
        var byDay: [String: [String]] = [:]
        for (key, entry) in profile.setLog {
            guard let dayKey = SetKeyParts.dayKey(from: key), profile.isOwnKey(dayKey) else { continue }
            byDay[dayKey, default: []].append("\(entry.reps)×\(fmt(entry.weight))")
        }
        return byDay
            .sorted { order($0.key, prefix) < order($1.key, prefix) }
            .suffix(limit)
            .map { "— \(readable(key: $0.key, prefix: prefix)): \($0.value.joined(separator: ", ")) кг" }
    }

    /// Ключ дня в вид «неделя 3, день 2» — модель не должна разбирать наши префиксы.
    private static func readable(key: String, prefix: String) -> String {
        let rest = key.hasPrefix(prefix) ? String(key.dropFirst(prefix.count)) : key
        let parts = rest.split(separator: "-")
        guard parts.count == 2 else { return rest }
        return "неделя \(parts[0]), день \(parts[1])"
    }

    private static func fmt(_ value: Double) -> String {
        value == value.rounded() ? String(Int(value)) : String(format: "%.1f", value)
    }

    // MARK: - Запрос

    private static let instruction = """
    Ты опытный тренер по силовым. Отвечай по-русски, коротко и по делу, \
    без вступлений и без списка того, что человек и так видит в приложении.

    Тебе дают цифры тренировок и заметки, которые человек писал после занятий. \
    Цифры приложение считает само — твоя задача связать заметки с цифрами.

    Ответь ровно тремя разделами:
    ЧТО ВИЖУ — два-три предложения о том, что происходит: где растёт, где стоит, \
    что видно по самочувствию из заметок.
    ЧТО ПОМЕНЯТЬ — два-четыре конкретных пункта. Каждый пункт — действие, \
    а не рассуждение: какое упражнение заменить, где снять объём, где добавить.
    НА ЧТО СМОТРЕТЬ — одно-два предложения о том, что отслеживать на ближайших тренировках.

    Не выдумывай данные, которых нет. Если заметок мало, так и скажи \
    в первом разделе и дай советы только по цифрам. Не ставь диагнозов: \
    при боли отправляй к врачу, а не советуй лечение. Не используй markdown-разметку.
    """

    static func analyze(profile: ProgramProfile, key: String, model: String) async throws -> String {
        guard hasData(profile) else { throw CoachError.noData }

        let name = model.trimmingCharacters(in: .whitespaces).isEmpty ? CoachSecrets.defaultModel : model
        let apiKey = key.trimmingCharacters(in: .whitespaces).isEmpty ? CoachSecrets.embeddedKey : key
        guard let url = URL(string: "https://generativelanguage.googleapis.com/v1beta/models/\(name):generateContent") else {
            throw CoachError.server(0, "Неверное имя модели.")
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 60
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        // Ключ уходит заголовком, а не в адресе: в адресной строке он попал бы в логи.
        request.setValue(apiKey, forHTTPHeaderField: "x-goog-api-key")
        request.httpBody = try JSONSerialization.data(withJSONObject: [
            "systemInstruction": ["parts": [["text": instruction]]],
            "contents": [["role": "user", "parts": [["text": context(for: profile)]]]],
            "generationConfig": ["temperature": 0.4, "maxOutputTokens": 1200]
        ])

        let (data, response) = try await URLSession.shared.data(for: request)
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]

        guard code == 200 else {
            let error = json?["error"] as? [String: Any]
            let message = error?["message"] as? String ?? ""
            let status = error?["status"] as? String ?? ""
            if status == "FAILED_PRECONDITION" || message.contains("location is not supported") {
                throw CoachError.regionBlocked
            }
            if code == 401 || code == 403 || message.contains("API key not valid") {
                throw CoachError.badKey
            }
            if code == 429 { throw CoachError.quota }
            throw CoachError.server(code, message)
        }

        guard let text = firstText(in: json), !text.isEmpty else { throw CoachError.emptyAnswer }
        return text
    }

    /// Достаёт текст ответа, не полагаясь на то, что часть ровно одна.
    private static func firstText(in json: [String: Any]?) -> String? {
        guard let candidates = json?["candidates"] as? [[String: Any]],
              let content = candidates.first?["content"] as? [String: Any],
              let parts = content["parts"] as? [[String: Any]] else { return nil }
        let text = parts.compactMap { $0["text"] as? String }.joined()
        return text.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
