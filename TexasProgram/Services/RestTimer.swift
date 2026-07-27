import ActivityKit
import Foundation
import Observation
import UIKit
import UserNotifications

/// Таймер отдыха между подходами.
///
/// Считает не «сколько тиков прошло», а разницу до сохранённой даты окончания:
/// иначе свёрнутое приложение теряло бы секунды. Плюс локальное уведомление,
/// чтобы сигнал пришёл, даже если экран погашен.
@Observable
final class RestTimer {
    static let presets: [TimeInterval] = [180, 300, 600]

    private(set) var endsAt: Date?
    private(set) var total: TimeInterval = 0
    private(set) var remaining: TimeInterval = 0
    /// Почему не завелась Live Activity. Пусто — активность создана.
    /// Нужно, чтобы отличать «система запретила» от «расширение молчит».
    private(set) var activityIssue: String?

    @ObservationIgnored private var ticker: Timer?
    @ObservationIgnored private var notificationID: String?
    @ObservationIgnored private var activity: Activity<RestActivityAttributes>?

    var isRunning: Bool { endsAt != nil }

    var progress: Double {
        guard total > 0 else { return 0 }
        return min(max(1 - remaining / total, 0), 1)
    }

    var remainingText: String {
        let value = max(0, Int(remaining.rounded(.up)))
        return String(format: "%d:%02d", value / 60, value % 60)
    }

    func start(_ duration: TimeInterval) {
        cancelNotification()
        let started = Date()
        total = duration
        endsAt = started.addingTimeInterval(duration)
        remaining = duration
        scheduleTicker()
        scheduleNotification(after: duration)
        startActivity(from: started, to: started.addingTimeInterval(duration))
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    func add(_ extra: TimeInterval) {
        guard let endsAt else { return }
        cancelNotification()
        let updated = endsAt.addingTimeInterval(extra)
        self.endsAt = updated
        total += extra
        remaining = updated.timeIntervalSinceNow
        scheduleNotification(after: max(0, remaining))
        updateActivity(to: updated)
    }

    func stop() {
        ticker?.invalidate()
        ticker = nil
        endsAt = nil
        remaining = 0
        total = 0
        cancelNotification()
        endActivity()
    }

    /// Пересчёт после возврата из фона.
    func refresh() {
        guard let endsAt else { return }
        remaining = endsAt.timeIntervalSinceNow
        if remaining <= 0 { finish() }
    }

    private func scheduleTicker() {
        ticker?.invalidate()
        let timer = Timer(timeInterval: 0.2, repeats: true) { [weak self] _ in
            guard let self, let endsAt = self.endsAt else { return }
            self.remaining = endsAt.timeIntervalSinceNow
            if self.remaining <= 0 { self.finish() }
        }
        RunLoop.main.add(timer, forMode: .common)
        ticker = timer
    }

    private func finish() {
        ticker?.invalidate()
        ticker = nil
        endsAt = nil
        remaining = 0
        total = 0
        endActivity()
        // Уведомление уже поставлено на этот момент, дублировать вибрацию не нужно,
        // если приложение в фоне.
        if UIApplication.shared.applicationState == .active {
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        }
    }

    // MARK: - Live Activity

    /// Отсчёт в Dynamic Island и на экране блокировки.
    ///
    /// Обновления содержимого не шлём каждую секунду: цифры рисует система по
    /// диапазону дат, а ActivityKit ограничивает частоту обновлений.
    private func startActivity(from start: Date, to end: Date) {
        endActivity()
        activityIssue = nil

        guard ActivityAuthorizationInfo().areActivitiesEnabled else {
            activityIssue = "Живые активности выключены в настройках iOS"
            return
        }

        let state = RestActivityAttributes.ContentState(startedAt: start, endsAt: end)
        do {
            activity = try Activity.request(
                attributes: RestActivityAttributes(),
                content: ActivityContent(state: state, staleDate: end.addingTimeInterval(60)),
                pushType: nil
            )
        } catch {
            // Ошибку не глотаем: без неё пустой «остров» не отличить от несозданной активности.
            activityIssue = "Активность не создана: \(error.localizedDescription)"
        }
    }

    private func updateActivity(to end: Date) {
        guard let activity else { return }
        let state = RestActivityAttributes.ContentState(
            startedAt: activity.content.state.startedAt,
            endsAt: end
        )
        Task {
            await activity.update(ActivityContent(state: state, staleDate: end.addingTimeInterval(60)))
        }
    }

    private func endActivity() {
        guard let activity else { return }
        self.activity = nil
        Task { await activity.end(nil, dismissalPolicy: .immediate) }
    }

    // MARK: - Уведомление

    private func scheduleNotification(after duration: TimeInterval) {
        guard duration > 0 else { return }
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound]) { granted, _ in
            guard granted else { return }
            let content = UNMutableNotificationContent()
            content.title = "Отдых закончен"
            content.body = "Пора к следующему подходу."
            content.sound = .default

            let id = UUID().uuidString
            let request = UNNotificationRequest(
                identifier: id,
                content: content,
                trigger: UNTimeIntervalNotificationTrigger(timeInterval: duration, repeats: false)
            )
            center.add(request)
            DispatchQueue.main.async { self.notificationID = id }
        }
    }

    private func cancelNotification() {
        guard let notificationID else { return }
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [notificationID])
        self.notificationID = nil
    }
}
