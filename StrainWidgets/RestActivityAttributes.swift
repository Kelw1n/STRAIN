import ActivityKit
import Foundation

/// Данные Live Activity таймера отдыха.
///
/// Файл общий для приложения и расширения-виджета: ActivityKit сопоставляет
/// активность по типу атрибутов, поэтому объявление должно быть ровно одно.
struct RestActivityAttributes: ActivityAttributes {
    /// Меняющаяся часть. Держим только даты: отсчёт рисует сама система через
    /// `Text(timerInterval:)`, так что обновления каждую секунду слать не нужно —
    /// иначе упрёмся в лимиты ActivityKit.
    struct ContentState: Codable, Hashable {
        var startedAt: Date
        var endsAt: Date

        var range: ClosedRange<Date> { startedAt...max(endsAt, startedAt.addingTimeInterval(1)) }
    }

    /// Постоянная часть — на будущее, если появятся разные типы таймеров.
    var title: String = "Отдых"
}
