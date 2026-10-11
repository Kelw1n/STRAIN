import Observation
import UIKit

/// Картинки упражнений.
///
/// Стандартная берётся из набора иллюстраций по ключевым словам в названии:
/// так картинку получают и упражнения, которые человек вписал сам. Поверх
/// стандартной можно поставить свою из галереи — она хранится по названию
/// упражнения, поэтому одна и та же во всех днях и неделях.
@Observable
final class ExerciseArt {
    static let shared = ExerciseArt()

    /// Растёт при каждой смене своей картинки: экраны, которые его читают,
    /// перерисуются сами.
    private(set) var revision = 0

    @ObservationIgnored private let cache = NSCache<NSString, UIImage>()
    @ObservationIgnored private let fileManager = FileManager.default

    private init() {
        cache.countLimit = 80
    }

    // MARK: - Подбор стандартной картинки

    /// Правила проверяются по порядку: узкие раньше общих. «Тяга в наклоне»
    /// должна стать тягой раньше, чем «наклон» сделает её наклонным жимом.
    private static let rules: [(key: String, match: (String) -> Bool)] = [
        ("front_squat", { $0.contains("фронтальн") || $0.contains("зерчер") }),
        ("goblet", { $0.contains("гоблет") || ($0.contains("присед") && ($0.contains("гир") || $0.contains("гантел") || $0.contains("колодец"))) }),
        ("lunge", { $0.contains("выпад") || $0.contains("болгар") }),
        ("leg_press", { $0.contains("жим ног") || $0.contains("жим платформ") }),
        ("squat", { $0.contains("присед") || $0.contains("squat") }),
        ("rdl", { $0.contains("румынск") || $0.contains("прямых ног") || $0.contains("гуд монинг") || $0.contains("rdl") }),
        ("deadlift", { $0.contains("станов") || $0.contains("трэп") || $0.contains("плинт") || $0.contains("deadlift") }),
        ("hip_thrust", { $0.contains("мост") || $0.contains("ягодичн") || $0.contains("thrust") }),
        ("hyperext", { $0.contains("гиперэкстенз") || $0.contains("glute ham") || $0.contains("ghr") }),
        ("pullup", { $0.contains("подтяг") || $0.contains("pull-up") || $0.contains("pullup") }),
        ("row", { $0.contains("тяг") && $0.contains("наклон") }),
        ("pulldown", { ($0.contains("тяг") && ($0.contains("верхн") || $0.contains("вертикальн") || $0.contains("хамер"))) || $0.contains("pulldown") }),
        ("cable_row", { $0.contains("тяг") && ($0.contains("сидя") || $0.contains("горизонтальн") || $0.contains("блок")) }),
        ("row", { $0.contains("тяг") || $0.contains("row") }),
        ("dips", { $0.contains("брусь") || $0.contains("dips") }),
        ("ohp", { $0.contains("отжиман") && $0.contains("стойке") }),
        ("pushup", { $0.contains("отжиман") || $0.contains("push-up") }),
        ("incline", { $0.contains("жим") && $0.contains("наклон") }),
        ("bench", { $0.contains("жим") && $0.contains("леж") }),
        ("seated_press", { $0.contains("жим") && ($0.contains("сидя") || $0.contains("гантел")) }),
        ("ohp", { $0.contains("жим") && ($0.contains("стоя") || $0.contains("армейск") || $0.contains("вертикальн") || $0.contains("z-жим") || $0.contains("гир") || $0.contains("над голов")) }),
        ("bench", { $0.contains("жим") || $0.contains("bench") }),
        ("lateral_raise", { $0.contains("мах") || $0.contains("разведен") || $0.contains("дельт") }),
        ("calf", { $0.contains("носк") || $0.contains("икр") || $0.contains("голен") }),
        ("curl", { $0.contains("бицепс") || $0.contains("сгибание рук") || $0.contains("молот") || $0.contains("curl") }),
        ("pushdown", { $0.contains("трицепс") || $0.contains("разгибание рук") || $0.contains("французск") }),
        ("leg_curl", { $0.contains("сгибание ног") }),
        ("leg_extension", { $0.contains("разгибание ног") }),
        ("plank", { $0.contains("планк") || $0.contains("plank") }),
        ("hanging_raise", { $0.contains("вис") || $0.contains("подъем ног") }),
        ("crunch", { $0.contains("скруч") || $0.contains("пресс") || $0.contains("кранч") || $0.contains("молитв") || $0.contains("колес") }),
        ("shrug", { $0.contains("шраг") || $0.contains("фермер") || $0.contains("трапец") || $0.contains("предплеч") || $0.contains("шея") }),
    ]

    /// Ключ стандартной иллюстрации для названия упражнения.
    static func key(for name: String) -> String {
        let text = name.lowercased().replacingOccurrences(of: "ё", with: "е")
        return rules.first { $0.match(text) }?.key ?? "generic"
    }

    // MARK: - Картинка

    /// Своя картинка, если поставлена, иначе стандартная.
    func image(for name: String) -> UIImage? {
        _ = revision
        if let custom = customImage(for: name) { return custom }
        return bundled(Self.key(for: name))
    }

    func hasCustomImage(for name: String) -> Bool {
        _ = revision
        return fileManager.fileExists(atPath: customURL(for: name).path)
    }

    private func bundled(_ key: String) -> UIImage? {
        let id = "ex_" + key
        if let hit = cache.object(forKey: id as NSString) { return hit }
        // Синхронизированная папка проекта кладёт ресурсы в корень бандла,
        // но на случай сохранённой структуры ищем и в подпапке.
        let path = Bundle.main.path(forResource: id, ofType: "png")
            ?? Bundle.main.path(forResource: id, ofType: "png", inDirectory: "Exercises")
            ?? Bundle.main.path(forResource: id, ofType: "png", inDirectory: "Resources/Exercises")
        guard let path, let image = UIImage(contentsOfFile: path) else { return nil }
        cache.setObject(image, forKey: id as NSString)
        return image
    }

    // MARK: - Своя картинка

    private var customDirectory: URL {
        let base = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent("ExerciseImages", isDirectory: true)
    }

    private func customURL(for name: String) -> URL {
        // Имя файла — из названия без символов, которые нельзя в пути.
        let safe = name.lowercased()
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty }
            .joined(separator: "_")
        return customDirectory.appendingPathComponent((safe.isEmpty ? "exercise" : safe) + ".jpg")
    }

    private func customImage(for name: String) -> UIImage? {
        let url = customURL(for: name)
        let id = "custom:" + url.lastPathComponent
        if let hit = cache.object(forKey: id as NSString) { return hit }
        guard let image = UIImage(contentsOfFile: url.path) else { return nil }
        cache.setObject(image, forKey: id as NSString)
        return image
    }

    /// Сохраняет свою картинку квадратом 480×480: больше на экране не нужно,
    /// а полноразмерное фото из галереи весило бы мегабайты.
    func setCustomImage(_ image: UIImage, for name: String) {
        let square = Self.squareThumbnail(image, side: 480)
        guard let data = square.jpegData(compressionQuality: 0.82) else { return }
        try? fileManager.createDirectory(at: customDirectory, withIntermediateDirectories: true)
        let url = customURL(for: name)
        try? data.write(to: url, options: .atomic)
        cache.removeObject(forKey: ("custom:" + url.lastPathComponent) as NSString)
        revision += 1
    }

    func removeCustomImage(for name: String) {
        let url = customURL(for: name)
        try? fileManager.removeItem(at: url)
        cache.removeObject(forKey: ("custom:" + url.lastPathComponent) as NSString)
        revision += 1
    }

    // MARK: - Для Live Activity

    /// Кладёт уменьшенную картинку в общий контейнер, откуда её читает
    /// Dynamic Island. Возвращает имя файла — его передают в состоянии активности.
    func publishForLiveActivity(name: String) -> String? {
        guard let image = image(for: name),
              let container = fileManager.containerURL(forSecurityApplicationGroupIdentifier: WidgetStore.appGroup)
        else { return nil }
        let file = "rest-art-" + Self.key(for: name) + (hasCustomImage(for: name) ? "-custom-\(revision)" : "") + ".png"
        let url = container.appendingPathComponent(file)
        if !fileManager.fileExists(atPath: url.path) {
            let small = Self.squareThumbnail(image, side: 120)
            guard let data = small.pngData() else { return nil }
            try? data.write(to: url, options: .atomic)
        }
        return file
    }

    static func squareThumbnail(_ image: UIImage, side: CGFloat) -> UIImage {
        let size = image.size
        let edge = min(size.width, size.height)
        let crop = CGRect(x: (size.width - edge) / 2, y: (size.height - edge) / 2, width: edge, height: edge)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format).image { _ in
            let scale = side / edge
            image.draw(in: CGRect(
                x: -crop.minX * scale,
                y: -crop.minY * scale,
                width: size.width * scale,
                height: size.height * scale
            ))
        }
    }
}
