import SwiftUI

// MARK: - Палитра

/// Две темы — тёмная REBRN (чистый OLED-чёрный, графитовые карточки) и светлая
/// (светло-серый фон, белые карточки) с одним пламенным оранжевым акцентом.
/// Экраны красятся динамическими цветами ниже: `ink` — текст и значки,
/// `canvas` — фон экрана, `tone(_:)` — серые ступени тёмной темы, которые в
/// светлой зеркалятся. Каждая сама берёт вид из текущей темы.
enum Theme {
    /// Гарнитура цифр и заголовков — плотный гротеск без скруглений.
    static let fontDesign: Font.Design = .default

    /// Фирменный пламенный оранжевый (#FF6029).
    static let accent = Color(red: 1.0, green: 0.38, blue: 0.16)
    /// Глубокий тлеющий янтарно-оранжевый.
    static let accentDeep = Color(red: 0.88, green: 0.28, blue: 0.10)
    /// Мятно-зелёный индикатор выполнения.
    static let success = Color(red: 0.18, green: 0.82, blue: 0.45)
    static let warning = Color(red: 1.0, green: 0.65, blue: 0.20)
    static let record = Color(red: 1.0, green: 0.32, blue: 0.32)

    static let accentGradient = LinearGradient(colors: [accent, accentDeep], startPoint: .topLeading, endPoint: .bottomTrailing)
    static let deepGradient = LinearGradient(colors: [accentDeep, accent], startPoint: .topLeading, endPoint: .bottomTrailing)
    static let successGradient = LinearGradient(colors: [success, accent], startPoint: .topLeading, endPoint: .bottomTrailing)
    static let recordGradient = LinearGradient(colors: [warning, record], startPoint: .topLeading, endPoint: .bottomTrailing)

    /// Карточки держатся на контрасте с чёрным фоном, без обводки.
    static func hairline(_ scheme: ColorScheme) -> LinearGradient {
        LinearGradient(colors: [.clear], startPoint: .top, endPoint: .bottom)
    }

    /// Заливка карточки: графитовая (#161617) в тёмной теме, белая в светлой.
    static func surface(_ scheme: ColorScheme) -> Color {
        .card
    }

    /// Серая ступень тёмной темы в светлой. Фон (почти чёрный) становится
    /// светло-серым, карточки (графит) — белыми, а поля и кнопки на карточках
    /// (светлее графита) — чуть темнее белого: в обеих темах они отделяются от
    /// карточки, на которой лежат. Средние и светлые серые — значки, заглушки
    /// картинок — одинаковы в обеих темах.
    static func lightTone(_ white: Double) -> Double {
        switch white {
        case ..<0.03: return 0.949
        case ..<0.115: return 1
        case ..<0.3: return max(0.82, 1 - (white - 0.08) * 0.9)
        default: return white
        }
    }
}

// MARK: - Оформление

/// Тема приложения на выбор в настройках. По умолчанию тёмная — как было.
enum AppAppearance: String, CaseIterable, Identifiable {
    case dark, light, system

    static let storageKey = "app_appearance"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .dark: return "Тёмная"
        case .light: return "Светлая"
        case .system: return "Как в системе"
        }
    }

    /// `nil` — как в настройках iPhone.
    var colorScheme: ColorScheme? {
        switch self {
        case .dark: return .dark
        case .light: return .light
        case .system: return nil
        }
    }

    var interfaceStyle: UIUserInterfaceStyle {
        switch self {
        case .dark: return .dark
        case .light: return .light
        case .system: return .unspecified
        }
    }

    static var current: AppAppearance {
        AppAppearance(rawValue: UserDefaults.standard.string(forKey: storageKey) ?? "") ?? .dark
    }

    /// Тема — всем окнам приложения: листам, меню и системным окнам тоже.
    /// `preferredColorScheme(nil)` после явной темы не возвращает системную до
    /// перезапуска, а стиль окна — сразу.
    @MainActor
    static func apply(_ appearance: AppAppearance = .current) {
        for scene in UIApplication.shared.connectedScenes {
            guard let scene = scene as? UIWindowScene else { continue }
            for window in scene.windows {
                window.overrideUserInterfaceStyle = appearance.interfaceStyle
            }
        }
    }
}

// MARK: - Цвета, которые меняются с темой

private func dynamic(light: UIColor, dark: UIColor) -> Color {
    Color(UIColor { $0.userInterfaceStyle == .light ? light : dark })
}

private let inkColor = dynamic(light: UIColor(white: 0.05, alpha: 1), dark: .white)
private let canvasColor = dynamic(light: UIColor(red: 0.949, green: 0.949, blue: 0.965, alpha: 1), dark: .black)
private let cardColor = dynamic(light: .white, dark: UIColor(red: 0.086, green: 0.086, blue: 0.09, alpha: 1))

extension ShapeStyle where Self == Color {
    /// Текст и значки: белые в тёмной теме, почти чёрные в светлой.
    static var ink: Color { inkColor }
    /// Фон экрана: чёрный в тёмной теме, светло-серый в светлой.
    static var canvas: Color { canvasColor }
    /// Карточка: графитовая в тёмной теме, белая в светлой.
    static var card: Color { cardColor }

    /// Серый тёмной темы (`Color(white:)`) и его пара в светлой — `Theme.lightTone`.
    static func tone(_ white: Double, opacity: Double = 1) -> Color {
        dynamic(
            light: UIColor(white: Theme.lightTone(white), alpha: opacity),
            dark: UIColor(white: white, alpha: opacity)
        )
    }

    /// Свой цвет для каждой темы.
    static func themed(light: Color, dark: Color) -> Color {
        dynamic(light: UIColor(light), dark: UIColor(dark))
    }
}

// MARK: - Анимации

/// Пружины почти без перелёта: экран не качается и не «дёргается»
/// после движения, а быстро встаёт на место.
enum Motion {
    static let card = Animation.spring(response: 0.36, dampingFraction: 0.92)
    static let snappy = Animation.spring(response: 0.28, dampingFraction: 0.9)
    /// Чуть живее — для отметок и наград, но без заметного качания.
    static let bouncy = Animation.spring(response: 0.34, dampingFraction: 0.78)
    static let smooth = Animation.easeInOut(duration: 0.22)
    /// Появление и уход крупных панелей: тренировка, листы, плашки.
    static let sheet = Animation.spring(response: 0.36, dampingFraction: 0.92)
    /// Перестановка и отклик на палец.
    static let reorder = Animation.spring(response: 0.26, dampingFraction: 0.86)
    /// Переключение вкладок.
    static let tab = Animation.easeOut(duration: 0.2)
    /// Сообщение в переписке: вылетает с лёгким отскоком, переписка едет следом.
    static let chat = Animation.spring(response: 0.4, dampingFraction: 0.74)
    /// Нажатие: кнопка прижимается сразу, без задержки.
    static let press = Animation.spring(response: 0.16, dampingFraction: 0.95)
    /// Отпускание: мягко пружинит назад с едва заметным отскоком.
    static let release = Animation.spring(response: 0.34, dampingFraction: 0.68)

    static func maybe(_ animation: Animation, reduce: Bool) -> Animation? {
        reduce ? nil : animation
    }
}

// MARK: - Фон приложения

/// Чистый OLED-чёрный фон: акцент живёт только в элементах.
struct AppBackground: View {
    var body: some View {
        Color.black.ignoresSafeArea()
    }
}

extension View {
    /// Общий фон экрана.
    func screenBackground() -> some View {
        background(AppBackground())
    }
}

extension View {
    /// Всегда в тёмной теме: фото и аватарка на весь экран, «Итоги» историями,
    /// карточки для сторис — они задуманы на чёрном и одинаковы в любой теме.
    func alwaysDark() -> some View {
        environment(\.colorScheme, .dark)
    }
}
