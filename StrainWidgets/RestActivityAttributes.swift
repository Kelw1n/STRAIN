import ActivityKit
import Foundation

/// Данные Live Activity таймера отдыха.
///
/// Файл общий для приложения и расширения-виджета: ActivityKit сопоставляет
/// активность по типу атрибутов, поэтому объявление должно быть ровно одно.
struct RestActivityAttributes: ActivityAttributes {
    /// Меняющаяся часть состояния.
    struct ContentState: Codable, Hashable {
        var startedAt: Date
        var endsAt: Date
        var exerciseName: String = ""
        var setDetails: String = ""
        var completedSets: Int = 0
        var totalSets: Int = 0

        var range: ClosedRange<Date> { startedAt...max(endsAt, startedAt.addingTimeInterval(1)) }
    }

    /// Постоянная часть — на будущее, если появятся разные типы таймеров.
    var title: String = "Отдых"
}
