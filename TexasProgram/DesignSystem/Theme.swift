import SwiftUI

// MARK: - Палитра

/// Одна тема — REBRN: чистый OLED-чёрный, графитовые карточки и пламенный
/// оранжевый акцент. Других тем нет: экраны свёрстаны под чёрный фон.
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

    /// Графитовая заливка карточки (#161617).
    static func surface(_ scheme: ColorScheme) -> Color {
        Color(red: 0.086, green: 0.086, blue: 0.09)
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
