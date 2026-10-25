import Foundation
import UIKit

/// Продукт, который ИИ нашёл на фото. До добавления в дневник его можно поправить.
struct RecognizedFood: Identifiable, Equatable, Sendable {
    var id = UUID()
    var name: String
    var grams: Double?
    var nutrients: Nutrients
}

struct RecognizedMeal: Equatable, Sendable {
    var title: String
    var items: [RecognizedFood]
    /// «высокая», «средняя», «низкая» или «оценка по фото».
    var confidence: String
    /// Откуда цифры: этикетка, известный продукт или оценка на глаз.
    var comment: String

    /// Итог — всегда сумма продуктов, а не то, что ИИ написал отдельно.
    var total: Nutrients { items.reduce(.zero) { $0 + $1.nutrients } }
}

/// Распознавание еды по фото через Google Gemini со своим ключом — как в Butterfly:
/// тот же промпт, те же модели по очереди, тот же разбор ответа.
enum FoodRecognizer {
    /// Первая, что ответит. Следующая пробуется, только если модели нет.
    static let models = ["gemini-3.5-flash", "gemini-3.1-flash-lite", "gemini-flash-latest"]
    private static let base = URL(string: "https://generativelanguage.googleapis.com/v1beta")!
    /// Фото уходит не больше 1600 точек по длинной стороне и не тяжелее 2,5 МБ.
    static let maxSide: CGFloat = 1600
    static let maxBytes = 2_520_000

    enum Failure: LocalizedError, Equatable {
        case noKey, badKey, region, quota, busy, nothingFound, unreadable
        case network(String)

        var errorDescription: String? {
            switch self {
            case .noKey: return "Нужен ключ Google AI — вставь его в настройках питания."
            case .badKey: return "Google не принял ключ. Проверь, что он скопирован целиком."
            case .region: return "Google не пускает к ИИ из твоей страны. Включи VPN и попробуй ещё раз."
            case .quota: return "Лимит запросов по ключу на сейчас исчерпан. Подожди минуту или проверь лимиты в Google AI Studio."
            case .busy: return "Сервис Google сейчас занят. Попробуй ещё раз через минуту."
            case .nothingFound: return "Еду на фото не нашёл. Сними поближе или при свете."
            case .unreadable: return "Не получилось разобрать ответ ИИ. Попробуй ещё раз."
            case .network(let message): return "Нет связи с Google: \(message)"
            }
        }
    }

    // MARK: Запросы

    static func recognize(_ image: UIImage, key: String, session: URLSession = .shared) async throws -> RecognizedMeal {
        guard let jpeg = uploadJPEG(image) else { throw Failure.unreadable }
        return try parse(try await ask(photoPrompt, image: jpeg, key: key, session: session))
    }

    /// Пересчёт после поправки словами: «там 200 г, а не 150», «это без сахара».
    static func correct(_ meal: RecognizedMeal, note: String, key: String, session: URLSession = .shared) async throws -> RecognizedMeal {
        try parse(try await ask(correctionPrompt(meal, note: note), image: nil, key: key, session: session))
    }

    /// Проверка ключа без фото — список моделей отвечает и на бесплатном ключе.
    static func checkKey(_ key: String, session: URLSession = .shared) async throws {
        var components = URLComponents(url: base.appendingPathComponent("models"), resolvingAgainstBaseURL: false)!
        components.queryItems = [URLQueryItem(name: "pageSize", value: "1")]
        var request = URLRequest(url: components.url!, timeoutInterval: 20)
        request.setValue(key, forHTTPHeaderField: "x-goog-api-key")
        let (data, response) = try await send(request, session: session)
        try verify(response, data: data)
    }

    private static func ask(_ prompt: String, image: Data?, key: String, session: URLSession) async throws -> String {
        var parts: [[String: Any]] = [["text": prompt]]
        if let image {
            parts.append(["inline_data": ["mime_type": "image/jpeg", "data": image.base64EncodedString()]])
        }
        let body: [String: Any] = [
            "contents": [["role": "user", "parts": parts]],
            "generationConfig": ["temperature": 0.1, "maxOutputTokens": 8192, "responseMimeType": "application/json"]
        ]
        return try await post(body, key: key, session: session)
    }

    /// Разговор с ИИ-тренером: правила и данные — системной инструкцией,
    /// дальше реплики по очереди. Ответ — JSON, как и у фото еды.
    static func converse(system: String, turns: [(role: String, text: String)], key: String, session: URLSession = .shared) async throws -> String {
        let body: [String: Any] = [
            "systemInstruction": ["parts": [["text": system]]],
            "contents": turns.map { ["role": $0.role, "parts": [["text": $0.text]]] },
            "generationConfig": ["temperature": 0.4, "maxOutputTokens": 4096, "responseMimeType": "application/json"]
        ]
        return try await post(body, key: key, session: session)
    }

    private static func post(_ body: [String: Any], key: String, session: URLSession) async throws -> String {
        let payload = try JSONSerialization.data(withJSONObject: body)
        var lastFailure: Error = Failure.busy
        for model in models {
            guard let url = URL(string: base.absoluteString + "/models/\(model):generateContent") else { continue }
            var request = URLRequest(url: url, timeoutInterval: 90)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.setValue(key, forHTTPHeaderField: "x-goog-api-key")
            request.httpBody = payload
            let (data, response) = try await send(request, session: session)
            do {
                try verify(response, data: data)
                return try responseText(data)
            } catch ModelMissing.missing {
                lastFailure = Failure.network("модель \(model) недоступна по этому ключу")
                continue
            }
        }
        throw lastFailure
    }

    private enum ModelMissing: Error { case missing }

    private static func send(_ request: URLRequest, session: URLSession) async throws -> (Data, URLResponse) {
        do {
            return try await session.data(for: request)
        } catch let error as URLError {
            throw Failure.network(error.localizedDescription)
        }
    }

    /// Ошибки Google — в понятные слова. Модели нет — пробуем следующую.
    private static func verify(_ response: URLResponse, data: Data) throws {
        guard let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) else { return }
        let message = ((try? JSONSerialization.jsonObject(with: data) as? [String: Any])?["error"] as? [String: Any])?["message"] as? String ?? ""
        let lower = message.lowercased()
        let status = http.statusCode
        let modelGone = lower.contains("model") && (lower.contains("not found") || lower.contains("unsupported") || lower.contains("not supported"))
        if status == 404 || ([400, 422].contains(status) && modelGone) { throw ModelMissing.missing }
        if [400, 403].contains(status) && lower.contains("location") { throw Failure.region }
        if status == 401 || status == 403 || (status == 400 && lower.contains("api key")) { throw Failure.badKey }
        if status == 429 { throw Failure.quota }
        if status >= 500 { throw Failure.busy }
        throw Failure.network(message.isEmpty ? "ошибка \(status)" : message)
    }

    // MARK: Фото

    /// JPEG для отправки: уменьшаем и поджимаем качество, пока не влезет в лимит.
    static func uploadJPEG(_ image: UIImage) -> Data? {
        let scaled = image.downscaled(maxSide: maxSide)
        var quality: CGFloat = 0.88
        guard var data = scaled.jpegData(compressionQuality: quality) else { return nil }
        while data.count > maxBytes, quality > 0.42 {
            quality -= 0.08
            guard let next = scaled.jpegData(compressionQuality: quality) else { break }
            data = next
        }
        return data.count <= maxBytes ? data : nil
    }

    // MARK: Разбор ответа

    /// Текст ответа Gemini: части первого варианта, без «мыслей» модели.
    static func responseText(_ data: Data) throws -> String {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw Failure.unreadable }
        let candidate = (json["candidates"] as? [[String: Any]])?.first
        let parts = (candidate?["content"] as? [String: Any])?["parts"] as? [[String: Any]] ?? []
        let text = parts.filter { ($0["thought"] as? Bool) != true }.compactMap { $0["text"] as? String }.joined()
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw Failure.nothingFound }
        return text
    }

    /// Разбор с запасом, как в Butterfly: ключи бывают разными, числа — строками
    /// с запятой, ответ — в ```json```. Итог пересчитываем сами.
    static func parse(_ raw: String) throws -> RecognizedMeal {
        guard let root = jsonObject(raw) else { throw Failure.unreadable }
        let object = root as? [String: Any] ?? [:]
        let list = (root as? [Any]) ?? (object["items"] ?? object["foods"] ?? object["products"] ?? object["ingredients"]) as? [Any] ?? []
        var items = list.prefix(100).enumerated().compactMap { index, value in
            (value as? [String: Any]).map { food($0, index: index) }
        }
        if items.isEmpty, object["name"] != nil || object["title"] != nil || object["calories"] != nil || object["kcal"] != nil {
            items = [food(object, index: 0)]
        }
        guard !items.isEmpty else { throw Failure.nothingFound }
        let title = text(object["title"] ?? object["meal_name"] ?? object["name"], limit: 120)
        return RecognizedMeal(
            title: title.isEmpty ? (items.first?.name ?? "Приём пищи") : title,
            items: items,
            confidence: confidence(text(object["confidence"], limit: 32)),
            comment: text(object["comment"] ?? object["note"], limit: 1000)
        )
    }

    private static func food(_ item: [String: Any], index: Int) -> RecognizedFood {
        let name = text(item["name"] ?? item["title"] ?? item["food"] ?? item["product"], limit: 120)
        let grams = number(item["portion_g"] ?? item["portionG"] ?? item["grams"] ?? item["weight_g"] ?? item["weight"], limit: 100_000)
            ?? number(item["amount"] ?? item["portion"], limit: 100_000)
        let protein = number(item["protein_g"] ?? item["protein"] ?? item["proteins"], limit: 2000) ?? 0
        let fat = number(item["fat_g"] ?? item["fat"] ?? item["fats"], limit: 2000) ?? 0
        let carbs = number(item["carbs_g"] ?? item["carbs"] ?? item["carbohydrates"], limit: 2000) ?? 0
        var kcal = number(item["calories"] ?? item["kcal"] ?? item["energy_kcal"], limit: 20_000) ?? 0
        if kcal == 0 { kcal = Nutrients.kcal(protein: protein, fat: fat, carbs: carbs) }
        return RecognizedFood(
            name: name.isEmpty ? "Продукт \(index + 1)" : name,
            grams: grams.map { $0.rounded() },
            nutrients: Nutrients(kcal: kcal.rounded(), protein: tenth(protein), fat: tenth(fat), carbs: tenth(carbs))
        )
    }

    /// JSON из ответа: как есть, без ```, по первым фигурным скобкам и с прямыми кавычками.
    private static func jsonObject(_ raw: String) -> Any? {
        let clean = raw.replacingOccurrences(of: "[\u{200B}-\u{200D}\u{FEFF}]", with: "", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let unfenced = clean
            .replacingOccurrences(of: "^```(?:json)?\\s*", with: "", options: [.regularExpression, .caseInsensitive])
            .replacingOccurrences(of: "\\s*```$", with: "", options: .regularExpression)
        var braces = unfenced
        if let open = unfenced.firstIndex(where: { $0 == "{" || $0 == "[" }),
           let close = unfenced.lastIndex(where: { $0 == "}" || $0 == "]" }), open < close {
            braces = String(unfenced[open...close])
        }
        let straight = braces.replacingOccurrences(of: "[“”„]", with: "\"", options: .regularExpression)
        for candidate in [clean, unfenced, braces, straight] {
            if let data = candidate.data(using: .utf8), let value = try? JSONSerialization.jsonObject(with: data) {
                return value
            }
        }
        return nil
    }

    /// Число из числа или строки: «12,5», «примерно 150 г». Вне пределов — нет числа.
    static func number(_ value: Any?, limit: Double) -> Double? {
        var result: Double?
        if let value = value as? NSNumber, CFGetTypeID(value) != CFBooleanGetTypeID() {
            result = value.doubleValue
        } else if let value = value as? String {
            let text = value.replacingOccurrences(of: ",", with: ".")
            if let range = text.range(of: "[0-9]+(?:\\.[0-9]+)?", options: .regularExpression) {
                result = Double(text[range])
            }
        }
        guard let result, result.isFinite, result >= 0, result <= limit else { return nil }
        return result
    }

    private static func text(_ value: Any?, limit: Int) -> String {
        guard let value else { return "" }
        let string = (value as? String) ?? "\(value)"
        return String(string.filter { !$0.isNewline || $0 == "\n" }.trimmingCharacters(in: .whitespacesAndNewlines).prefix(limit))
    }

    private static func confidence(_ raw: String) -> String {
        let value = raw.lowercased()
        if value.contains("выс") || value.contains("high") { return "высокая" }
        if value.contains("низ") || value.contains("low") { return "низкая" }
        if value.contains("сред") || value.contains("medium") { return "средняя" }
        return "оценка по фото"
    }

    private static func tenth(_ value: Double) -> Double { (value * 10).rounded() / 10 }

    // MARK: Промпты

    /// Промпт из Butterfly без изменений: этикетка важнее оценки на глаз.
    static let photoPrompt = """
    Проанализируй фото еды. Верни только валидный JSON. Не используй Markdown и не добавляй пояснения до или после JSON.

    Используй именно эти ключи на английском:
    {
      "title": "краткое название приёма пищи на русском",
      "items": [
        {
          "name": "название продукта на русском",
          "portion_g": 150,
          "calories": 248,
          "protein_g": 46.5,
          "fat_g": 5.4,
          "carbs_g": 0
        }
      ],
      "total": {
        "calories": 248,
        "protein_g": 46.5,
        "fat_g": 5.4,
        "carbs_g": 0
      },
      "confidence": "высокая|средняя|низкая",
      "comment": "кратко: откуда взяты КБЖУ (этикетка / известный продукт / оценка по виду)"
    }

    Правила:
    - оцени порцию по фото в граммах (portion_g — число, без единиц);
    - если блюдо смешанное, разбей его на понятные продукты или части блюда;
    - если не уверен, всё равно дай наиболее вероятную оценку;
    - числа должны быть числами, не строками и без единиц измерения;
    - total должен быть суммой items;
    - ответ должен начинаться с { и заканчиваться }.

    Упаковки и готовые продукты:
    - если на фото упаковка, этикетка или штрихкод готового продукта — не оценивай «как домашнее блюдо на глаз»;
    - сначала прочитай с упаковки: название, бренд, вес/порцию, таблицу КБЖУ (на 100 г и/или на порцию);
    - если цифры на этикетке читаются — бери именно их (пересчитай на видимую/указанную порцию);
    - если этикетка размыта, но продукт узнаваем (бренд + название) — используй известную пищевую ценность этого SKU/типичные данные производителя из своих знаний (как если бы сверился с карточкой товара), а не произвольную оценку;
    - в name укажи бренд и название с упаковки, если они читаются;
    - в comment коротко напиши: «этикетка», «известный продукт» или «оценка по виду».
    """

    static func correctionPrompt(_ meal: RecognizedMeal, note: String) -> String {
        func macros(_ value: Nutrients) -> [String: Any] {
            ["calories": value.kcal, "protein_g": value.protein, "fat_g": value.fat, "carbs_g": value.carbs]
        }
        let current: [String: Any] = [
            "title": meal.title,
            "items": meal.items.map { item -> [String: Any] in
                var row = macros(item.nutrients)
                row["name"] = item.name
                if let grams = item.grams { row["portion_g"] = grams }
                return row
            },
            "total": macros(meal.total)
        ]
        let json = (try? JSONSerialization.data(withJSONObject: current, options: [.prettyPrinted, .sortedKeys]))
            .flatMap { String(data: $0, encoding: .utf8) } ?? "{}"
        let fix = note.trimmingCharacters(in: .whitespacesAndNewlines)
        return """
        Ты пересчитываешь уже сохранённый прием пищи STRAIN. Пользователь исправил распознавание. Верни только валидный JSON без markdown в том же формате, что ниже.

        Текущее распознавание:
        \(json)

        Исправление пользователя:
        \(fix.isEmpty ? "Пользователь хочет уточнить состав." : fix)

        Если речь о готовом продукте/упаковке — опирайся на этикетку или известную пищевую ценность бренда, а не на «оценку на глаз».

        Формат ответа:
        {
          "title": "Название приема пищи",
          "items": [
            {"name": "продукт", "portion_g": 150, "calories": 250, "protein_g": 12, "fat_g": 8, "carbs_g": 30}
          ],
          "total": {"calories": 250, "protein_g": 12, "fat_g": 8, "carbs_g": 30},
          "confidence": "средняя",
          "comment": "что изменилось"
        }
        """
    }
}
