import Foundation
import UserNotifications

/// Напоминания о тренировке в дни расписания.
///
/// Уведомления повторяющиеся, по одному на день недели: система сама будит их
/// каждую неделю, приложению для этого просыпаться не нужно. Поэтому в тексте
/// нет весов — они меняются от недели к неделе, а само уведомление нет.
enum ReminderScheduler {

    private static let prefix = "strain-workout-"

    /// Пересобирает расписание напоминаний под текущие настройки профиля.
    ///
    /// Всегда сначала снимает старые: поменялся день недели или время —
    /// прошлое уведомление осталось бы висеть.
    static func reschedule(for profile: ProgramProfile) {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: identifiers)

        guard profile.reminderEnabled else { return }

        let requests = plan(for: profile)
        guard !requests.isEmpty else { return }

        center.requestAuthorization(options: [.alert, .sound]) { granted, _ in
            guard granted else { return }
            for request in requests { center.add(request) }
        }
    }

    static func cancel() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: identifiers)
    }

    /// Идентификаторы на все дни недели: снимаем с запасом, чтобы не зависеть
    /// от того, каким расписание было в прошлый раз.
    private static var identifiers: [String] {
        (1...7).flatMap { weekday in [prefix + "\(weekday)"] + (1...skipWeeks).map { prefix + "\(weekday)-\($0)" } }
    }

    /// На сколько недель вперёд ставим разовые напоминания вместо повторяющегося,
    /// когда сегодняшняя тренировка уже сделана.
    private static let skipWeeks = 4

    /// Что именно поставим. Вынесено отдельно, чтобы это можно было проверить
    /// тестом, не трогая систему уведомлений.
    static func plan(for profile: ProgramProfile, now: Date = Date(), calendar: Calendar = .current) -> [UNNotificationRequest] {
        guard profile.reminderEnabled else { return [] }
        let trainedToday = profile.completionLog.contains { calendar.isDate($0.date, inSameDayAs: now) }
        let today = calendar.component(.weekday, from: now)

        return profile.weekdays.enumerated().flatMap { index, weekday -> [UNNotificationRequest] in
            let content = UNMutableNotificationContent()
            content.title = "Сегодня тренировка"
            content.body = body(for: index + 1, profile: profile)
            content.sound = .default

            // Утром уже потренировался — повторяющееся напоминание всё равно
            // пришло бы вечером. Вместо него разовые на следующие недели;
            // при следующем запуске приложения вернётся обычное.
            if trainedToday, weekday == today,
               let tonight = calendar.date(bySettingHour: profile.reminderHour, minute: profile.reminderMinute, second: 0, of: now) {
                return (1...skipWeeks).compactMap { week -> UNNotificationRequest? in
                    guard let date = calendar.date(byAdding: .day, value: 7 * week, to: tonight) else { return nil }
                    return UNNotificationRequest(
                        identifier: prefix + "\(weekday)-\(week)",
                        content: content,
                        trigger: UNCalendarNotificationTrigger(
                            dateMatching: calendar.dateComponents([.year, .month, .day, .hour, .minute], from: date),
                            repeats: false
                        )
                    )
                }
            }

            var components = DateComponents()
            components.weekday = weekday
            components.hour = profile.reminderHour
            components.minute = profile.reminderMinute

            return [UNNotificationRequest(
                identifier: prefix + "\(weekday)",
                content: content,
                trigger: UNCalendarNotificationTrigger(dateMatching: components, repeats: true)
            )]
        }
    }

    /// В режиме очереди тип дня заранее неизвестен — он зависит от того,
    /// что осталось невыполненным, поэтому обещать «день 2» нельзя.
    private static func body(for day: Int, profile: ProgramProfile) -> String {
        guard profile.scheduleMode == .weekday else { return "Загляни в приложение — план ждёт." }
        return "По расписанию день \(day). Открой приложение и посмотри веса."
    }
}
