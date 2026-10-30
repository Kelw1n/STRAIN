import Foundation

// MARK: - ИИ-тренер
//
// Отвечает Google Gemini по ключу человека — тому же, что и для фото еды.
// Тренер видит программу, максимумы, подходы за последние недели, тяжесть,
// застои, вес тела и питание. Программу сам не меняет: «Применить» есть
// только у проверенных действий — сброс максимума при застое и шаг прибавки.
// Переписка хранится только на телефоне.

/// Действие, которое предложил тренер. Применяется только по нажатию.
struct CoachAction: Codable, Hashable, Sendable {
    var type: String
    var lift: String?
    var exercise: String?
    var step: Double?
}

struct CoachMessage: Codable, Hashable, Identifiable, Sendable {
    enum Role: String, Codable, Sendable { case user, coach }

    var id = UUID()
    var role: Role
    var text: String
    var date = Date()
    var actions: [CoachAction] = []
    /// Номера уже применённых действий — кнопка становится галочкой.
    var applied: [Int] = []
    /// Разбор недели: карточка выглядит иначе, а неделя больше не предлагается.
    var reviewWeek: String?
}

/// Переписка с тренером одного профиля — файл на телефоне.
enum CoachHistory {
    static let limit = 80

    nonisolated static var directory: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent("Coach", isDirectory: true)
    }

    nonisolated static func url(_ profileID: UUID, in directory: URL = directory) -> URL {
        directory.appendingPathComponent("\(profileID.uuidString).json")
    }

    nonisolated static func read(_ profileID: UUID, in directory: URL = directory) -> [CoachMessage] {
        guard let data = try? Data(contentsOf: url(profileID, in: directory)) else { return [] }
        return (try? JSONDecoder().decode([CoachMessage].self, from: data)) ?? []
    }

    nonisolated static func write(_ messages: [CoachMessage], for profileID: UUID, in directory: URL = directory) {
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        guard let data = try? JSONEncoder().encode(Array(messages.suffix(limit))) else { return }
        try? data.write(to: url(profileID, in: directory), options: .atomic)
    }

    nonisolated static func delete(_ profileID: UUID, in directory: URL = directory) {
        try? FileManager.default.removeItem(at: url(profileID, in: directory))
    }
}

enum Coach {
    /// Быстрые вопросы под полем ввода.
    static let quickQuestions = [
        "Что улучшить на этой неделе?",
        "Почему встал вес?",
        "Оцени мой прогресс за месяц",
        "Сколько мне есть белка?",
        "Как восстановиться после тяжёлой недели?"
    ]

    static let reviewPrompt = "Разбери мою прошлую неделю с понедельника по воскресенье: что получилось, где отстаю от плана и что поменять на этой неделе. Коротко, по пунктам."

    static let system = """
    Ты — тренер по силовым тренировкам в приложении STRAIN. Отвечай по-русски, коротко и по делу: 2–6 предложений или короткий список. \
    Опирайся на данные человека ниже: программу, максимумы, подходы за последние недели, тяжесть, застои, вес тела и питание. \
    Не выдумывай цифры, которых нет в данных; если данных мало — скажи, что записывать. \
    Ты не меняешь программу сам. Если уместно, предложи действия только из этого списка: \
    stall_reset — сбросить максимум движения на 10% при застое (поле lift: squat, bench или deadlift); \
    weight_step — поменять шаг прибавки упражнения (поле exercise — точное название из данных, поле step — одно из 1, 1.25, 2.5, 5, 10). \
    Не предлагай действие, если для него нет причины в данных. Не советуй лекарства и не ставь диагнозы; при боли советуй врача. \
    Ответ — строго JSON без пояснений вокруг: {"reply": "текст ответа", "actions": [{"type": "stall_reset", "lift": "bench"}]}. \
    Если действий нет, actions — пустой список.
    """

    // MARK: Данные для тренера

    static func context(for profile: ProgramProfile, nutrition: NutritionData?, now: Date = Date(),
                        calendar: Calendar = WeeklyStatsBuilder.calendar) -> String {
        var lines: [String] = []
        let weekday = RuDate.full(weekday: calendar.component(.weekday, from: now)).lowercased()
        lines.append("Сегодня: \(RuDate.shortDayMonth(now, calendar: calendar)) \(calendar.component(.year, from: now)), \(weekday).")
        let weeks = profile.workoutPlan.weeks.count
        lines.append("Программа: \(profile.programKind.rawValue), неделя \(profile.currentWeek) из \(weeks), \(profile.trainingDayCount) тренировки в неделю, цикл \(profile.cycleNumber).")
        if let maxes = maxesLine(profile) { lines.append(maxes) }
        var steps = "Шаг прибавки по умолчанию: \(WeightFormat.kilogramsPrecise(profile.defaultWeightStep))"
        if !profile.weightSteps.isEmpty {
            steps += "; свои: " + profile.weightSteps.sorted { $0.key < $1.key }
                .map { "\($0.key) — \(WeightFormat.kilogramsPrecise($0.value))" }.joined(separator: ", ")
        }
        lines.append(steps + ".")
        let level = Progression.level(for: profile)
        lines.append("Уровень: \(level.level) (\(level.title)), опыт \(level.xp).")
        let stalled = profile.stalledLifts
        if !stalled.isEmpty {
            lines.append("Застой по оценкам тяжёлых дней: " + stalled.map(\.rawValue).joined(separator: ", ") + ".")
        }
        let weights = profile.bodyWeightSeries.suffix(6).reversed()
        if !weights.isEmpty {
            lines.append("Вес тела (новые первыми): " + weights.map {
                "\(WeightFormat.kilogramsPrecise($0.weight)) (\(RuDate.shortDayMonth($0.date, calendar: calendar)))"
            }.joined(separator: ", ") + ".")
        } else {
            lines.append("Вес тела не записан.")
        }
        if let nutrition = nutritionLine(nutrition, now: now, calendar: calendar) { lines.append(nutrition) }
        lines.append("Тренировки за 6 недель (новые первыми):")
        lines.append(contentsOf: workoutLines(profile, now: now, calendar: calendar))
        var text = lines.joined(separator: "\n")
        if text.count > 9000 { text = String(text.prefix(9000)) + "\n…" }
        return text
    }

    private static func maxesLine(_ profile: ProgramProfile) -> String? {
        guard supportsStallReset(profile.programKind) else { return nil }
        let kind = profile.programKind == .upperLower ? "1ПМ" : "5ПМ"
        return "Максимумы (\(kind)): присед \(WeightFormat.kilogramsPrecise(profile.squat5RM)), жим \(WeightFormat.kilogramsPrecise(profile.bench5RM)), тяга \(WeightFormat.kilogramsPrecise(profile.deadlift5RM))."
    }

    private static func nutritionLine(_ data: NutritionData?, now: Date, calendar: Calendar) -> String? {
        guard let data else { return nil }
        var parts: [String] = []
        if let plan = data.plan {
            if let goal = plan.goal { parts.append("цель — \(goal.title.lowercased())") }
            if let target = plan.target { parts.append("норма \(Int(target.kcal)) ккал, белок \(Int(target.protein)) г") }
        }
        let start = calendar.date(byAdding: .day, value: -7, to: calendar.startOfDay(for: now)) ?? now
        let recent = data.entries.filter { $0.date >= start && $0.date < now }
        let days = Dictionary(grouping: recent) { calendar.startOfDay(for: $0.date) }
        if !days.isEmpty {
            let kcal = days.values.map { $0.reduce(0) { $0 + $1.nutrients.kcal } }.reduce(0, +) / Double(days.count)
            let protein = days.values.map { $0.reduce(0) { $0 + $1.nutrients.protein } }.reduce(0, +) / Double(days.count)
            parts.append("в среднем за 7 дней \(Int(kcal.rounded())) ккал, белок \(Int(protein.rounded())) г (записано дней: \(days.count))")
        }
        return parts.isEmpty ? nil : "Питание: " + parts.joined(separator: "; ") + "."
    }

    private static func workoutLines(_ profile: ProgramProfile, now: Date, calendar: Calendar) -> [String] {
        guard let since = calendar.date(byAdding: .day, value: -42, to: calendar.startOfDay(for: now)) else { return [] }
        var latest: [String: CompletionRecord] = [:]
        for record in profile.completionLog where record.date >= since {
            if let current = latest[record.key], current.date >= record.date { continue }
            latest[record.key] = record
        }
        let records = latest.values.sorted { $0.date > $1.date }.prefix(30)
        guard !records.isEmpty else { return ["— за 6 недель тренировок нет."] }

        // Подходы по дню: ключ «день|упражнение|номер».
        var byDay: [String: [String: [(Int, SetEntry)]]] = [:]
        for (key, entry) in profile.setLog where entry.reps > 0 {
            let parts = key.split(separator: "|", omittingEmptySubsequences: false)
            guard parts.count >= 3, latest[String(parts[0])] != nil else { continue }
            byDay[String(parts[0]), default: [:]][String(parts[1]), default: []].append((Int(parts[2]) ?? 0, entry))
        }
        return records.map { record in
            var line = "- \(RuDate.shortDayMonth(record.date, calendar: calendar)), неделя \(record.week), день \(record.day)"
            if let effort = record.effort, !effort.isEmpty { line += " (тяжесть: \(effort))" }
            let exercises = (byDay[record.key] ?? [:]).sorted { $0.key < $1.key }.map { name, sets in
                "\(name) " + sets.sorted { $0.0 < $1.0 }.map { "\(WeightFormat.short($0.1.weight))×\($0.1.reps)" }.joined(separator: ", ")
            }
            line += exercises.isEmpty ? ": подходы не записаны" : ": " + exercises.joined(separator: "; ")
            return line
        }
    }

    // MARK: Действия

    static func supportsStallReset(_ kind: TrainingProgramKind) -> Bool {
        [.texas, .proTexas, .fullBody, .upperLower].contains(kind)
    }

    /// Проверенное действие: что покажет кнопка и как применить.
    struct Resolved: Equatable {
        let title: String
        let detail: String
    }

    static func lift(_ raw: String?) -> MainLift? {
        switch raw?.lowercased() {
        case "squat": return .squat
        case "bench": return .bench
        case "deadlift": return .deadlift
        default: return nil
        }
    }

    /// Упражнение из данных: тренер иногда пишет название чуть иначе.
    static func exercise(_ raw: String?, in profile: ProgramProfile) -> String? {
        guard let raw else { return nil }
        let key = ProgramProfile.weightStepKey(raw)
        return profile.loggedExerciseNames.first { ProgramProfile.weightStepKey($0) == key }
    }

    static func max(_ lift: MainLift, in profile: ProgramProfile) -> Double {
        switch lift {
        case .squat: return profile.squat5RM
        case .bench: return profile.bench5RM
        case .deadlift: return profile.deadlift5RM
        }
    }

    static func resetWeight(_ lift: MainLift, in profile: ProgramProfile) -> Double {
        let current = max(lift, in: profile)
        let step = profile.weightStep(for: lift.rawValue)
        var next = ProgramProfile.roundWeight(current * 0.9, step: step)
        if next >= current { next = current - step }
        return Swift.max(step, next)
    }

    /// Только то, что приложение умеет и что безопасно: иначе кнопки нет.
    static func resolve(_ action: CoachAction, profile: ProgramProfile) -> Resolved? {
        switch action.type {
        case "stall_reset":
            guard supportsStallReset(profile.programKind), let lift = lift(action.lift) else { return nil }
            let from = max(lift, in: profile)
            guard from > 0 else { return nil }
            return Resolved(
                title: "Сбросить \(lift.rawValue.lowercased()) на 10%",
                detail: "Максимум \(WeightFormat.kilogramsPrecise(from)) → \(WeightFormat.kilogramsPrecise(resetWeight(lift, in: profile))), веса программы пересчитаются"
            )
        case "weight_step":
            guard let name = exercise(action.exercise, in: profile), let step = action.step,
                  ProgramProfile.weightStepOptions.contains(step), step != profile.weightStep(for: name) else { return nil }
            return Resolved(
                title: "Шаг прибавки: \(WeightFormat.kilogramsPrecise(step))",
                detail: "\(name): сейчас \(WeightFormat.kilogramsPrecise(profile.weightStep(for: name)))"
            )
        default:
            return nil
        }
    }

    static func apply(_ action: CoachAction, to profile: ProgramProfile) {
        guard resolve(action, profile: profile) != nil else { return }
        switch action.type {
        case "stall_reset":
            guard let lift = lift(action.lift) else { return }
            let next = resetWeight(lift, in: profile)
            switch lift {
            case .squat: profile.squat5RM = next
            case .bench: profile.bench5RM = next
            case .deadlift: profile.deadlift5RM = next
            }
        case "weight_step":
            guard let name = exercise(action.exercise, in: profile), let step = action.step else { return }
            profile.setWeightStep(step, for: name)
        default:
            break
        }
    }

    // MARK: Ответ

    struct Reply: Equatable {
        let text: String
        let actions: [CoachAction]
    }

    /// Ответ JSON-ом; если модель всё же написала просто текст — берём его как есть.
    static func parse(_ raw: String) -> Reply {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        let unfenced = trimmed
            .replacingOccurrences(of: "^```(?:json)?\\s*", with: "", options: [.regularExpression, .caseInsensitive])
            .replacingOccurrences(of: "\\s*```$", with: "", options: .regularExpression)
        if let data = unfenced.data(using: .utf8),
           let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            let text = (object["reply"] ?? object["text"] ?? object["answer"]) as? String ?? ""
            let actions = (object["actions"] as? [[String: Any]] ?? []).prefix(3).compactMap { item -> CoachAction? in
                guard let type = item["type"] as? String else { return nil }
                let step = (item["step"] as? Double) ?? (item["step"] as? String).flatMap { Double($0.replacingOccurrences(of: ",", with: ".")) }
                return CoachAction(type: type, lift: item["lift"] as? String, exercise: item["exercise"] as? String, step: step)
            }
            if !text.isEmpty { return Reply(text: text, actions: Array(actions)) }
        }
        return Reply(text: trimmed, actions: [])
    }

    /// Ключ недели для разбора: «2026-W43».
    static func reviewWeek(now: Date = Date()) -> String { WeeklyStatsBuilder.weekKey(for: now) }

    /// Разбор предлагаем в понедельник и вторник, если за эту неделю его ещё не было
    /// и на прошлой неделе были тренировки.
    static func needsReview(_ messages: [CoachMessage], profile: ProgramProfile, now: Date = Date(),
                            calendar: Calendar = WeeklyStatsBuilder.calendar) -> Bool {
        let weekday = calendar.component(.weekday, from: now)
        guard weekday == 2 || weekday == 3 else { return false }
        let week = reviewWeek(now: now)
        guard !messages.contains(where: { $0.reviewWeek == week }) else { return false }
        guard let thisWeek = calendar.dateInterval(of: .weekOfYear, for: now)?.start,
              let lastWeek = calendar.date(byAdding: .day, value: -7, to: thisWeek) else { return false }
        return profile.completionLog.contains { $0.date >= lastWeek && $0.date < thisWeek }
    }
}
