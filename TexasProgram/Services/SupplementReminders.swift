import Foundation
import UserNotifications

/// Напоминания о добавках: одно уведомление на каждое время, в нём все
/// добавки этого времени — «Креатин 5 г · Омега-3».
///
/// Обычно уведомление повторяется каждый день само. Если всё, что к этому
/// времени, сегодня уже выпито, — вместо повторяющегося ставим разовые на
/// следующие 7 дней: сегодня напоминать не о чем. Открыл приложение —
/// расписание собирается заново.
@MainActor
enum SupplementReminders {
    static let prefix = "strain-supplement-"
    private static let scheduledKey = "supplement_reminders_scheduled"
    static let skipDays = 7

    /// Одно время напоминания и добавки в нём.
    struct Slot: Equatable {
        let hour: Int
        let minute: Int
        let items: [Supplement]

        var id: String { String(format: "%02d%02d", hour, minute) }
    }

    /// Запрос в систему без самой системы — чтобы проверить тестом.
    struct Planned: Equatable {
        let identifier: String
        let components: DateComponents
        let repeats: Bool
        let body: String
    }

    nonisolated static func slots(_ data: SupplementData) -> [Slot] {
        let timed = SupplementTracker.ordered(data.items).filter { $0.reminder != nil }
        let groups = Dictionary(grouping: timed) { $0.reminder!.hour * 60 + $0.reminder!.minute }
        return groups.keys.sorted().map { minutes in
            Slot(hour: minutes / 60, minute: minutes % 60, items: groups[minutes] ?? [])
        }
    }

    nonisolated static func body(_ items: [Supplement]) -> String {
        items.map { item in
            let dose = item.dose.trimmingCharacters(in: .whitespaces)
            return dose.isEmpty ? item.name : "\(item.name) \(dose)"
        }
        .joined(separator: " · ")
    }

    nonisolated static func plan(_ data: SupplementData, now: Date = Date(), calendar: Calendar = .current) -> [Planned] {
        slots(data).flatMap { slot -> [Planned] in
            let body = body(slot.items)
            let allTaken = slot.items.allSatisfy { SupplementTracker.isTaken($0, on: now, in: data, calendar: calendar) }
            if allTaken, let today = calendar.date(bySettingHour: slot.hour, minute: slot.minute, second: 0, of: now) {
                return (1...skipDays).compactMap { offset -> Planned? in
                    guard let date = calendar.date(byAdding: .day, value: offset, to: today) else { return nil }
                    return Planned(
                        identifier: prefix + slot.id + "-\(offset)",
                        components: calendar.dateComponents([.year, .month, .day, .hour, .minute], from: date),
                        repeats: false,
                        body: body
                    )
                }
            }
            var components = DateComponents()
            components.hour = slot.hour
            components.minute = slot.minute
            return [Planned(identifier: prefix + slot.id, components: components, repeats: true, body: body)]
        }
    }

    /// Каждый вызов снимает поставленное прошлым — в том числе то, что прошлый
    /// ещё только собирался поставить, пока ждал разрешения на уведомления.
    private static var generation = 0

    static func reschedule(_ data: SupplementData) {
        let center = UNUserNotificationCenter.current()
        let defaults = UserDefaults.standard
        let old = defaults.stringArray(forKey: scheduledKey) ?? []
        if !old.isEmpty { center.removePendingNotificationRequests(withIdentifiers: old) }

        let planned = plan(data)
        defaults.set(planned.map(\.identifier), forKey: scheduledKey)
        generation += 1
        let current = generation
        guard !planned.isEmpty else { return }

        center.requestAuthorization(options: [.alert, .sound]) { granted, _ in
            guard granted else { return }
            Task { @MainActor in
                guard current == generation else { return }
                for item in planned {
                    let content = UNMutableNotificationContent()
                    content.title = "Добавки"
                    content.body = item.body
                    content.sound = .default
                    UNUserNotificationCenter.current().add(UNNotificationRequest(
                        identifier: item.identifier,
                        content: content,
                        trigger: UNCalendarNotificationTrigger(dateMatching: item.components, repeats: item.repeats)
                    ))
                }
            }
        }
    }

    static func cancel() {
        let defaults = UserDefaults.standard
        let old = defaults.stringArray(forKey: scheduledKey) ?? []
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: old)
        defaults.removeObject(forKey: scheduledKey)
        generation += 1
    }
}
