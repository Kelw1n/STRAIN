import ActivityKit
import AudioToolbox
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
    private(set) var startedAt: Date?
    private(set) var total: TimeInterval = 0
    private(set) var remaining: TimeInterval = 0

    // Контекст упражнения, после которого запущен отдых (стиль REBRN)
    private(set) var exerciseName: String?
    private(set) var setDetails: String?
    private(set) var completedSets: Int = 0
    private(set) var totalSets: Int = 0

    /// Включён ли звуковой сигнал и отсчёт таймера.
    var isSoundEnabled: Bool {
        get { UserDefaults.standard.object(forKey: "rest_timer_sound_enabled") as? Bool ?? true }
        set { UserDefaults.standard.set(newValue, forKey: "rest_timer_sound_enabled") }
    }

    /// Почему не завелась Live Activity. Пусто — активность создана.
    private(set) var activityIssue: String?

    @ObservationIgnored private var ticker: Timer?
    @ObservationIgnored private var notificationID: String?
    @ObservationIgnored private var activity: Activity<RestActivityAttributes>?
    @ObservationIgnored private var lastTickSecond: Int?

    var isRunning: Bool { endsAt != nil }

    init() {
        restore()
    }

    var progress: Double {
        guard total > 0 else { return 0 }
        return min(max(1 - remaining / total, 0), 1)
    }

    var remainingText: String {
        let value = max(0, Int(remaining.rounded(.up)))
        return String(format: "%d:%02d", value / 60, value % 60)
    }

    var elapsedSeconds: Int {
        guard let startedAt else { return 0 }
        return max(0, Int(Date().timeIntervalSince(startedAt)))
    }

    var elapsedText: String {
        let val = elapsedSeconds
        return String(format: "%d:%02d", val / 60, val % 60)
    }

    func start(
        _ duration: TimeInterval,
        exerciseName: String? = nil,
        setDetails: String? = nil,
        completedSets: Int = 0,
        totalSets: Int = 0
    ) {
        cancelNotification()
        let started = Date()
        startedAt = started
        total = duration
        endsAt = started.addingTimeInterval(duration)
        remaining = duration

        self.exerciseName = exerciseName
        self.setDetails = setDetails
        self.completedSets = completedSets
        self.totalSets = totalSets

        lastTickSecond = nil
        scheduleTicker()
        scheduleNotification(after: duration)
        startActivity(
            from: started,
            to: started.addingTimeInterval(duration),
            exerciseName: exerciseName ?? "",
            setDetails: setDetails ?? "",
            completedSets: completedSets,
            totalSets: totalSets,
            imageFile: exerciseName.flatMap { ExerciseArt.shared.publishForLiveActivity(name: $0) }
        )
        persist()
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    func add(_ extra: TimeInterval) {
        guard let endsAt else { return }
        cancelNotification()
        let updated = endsAt.addingTimeInterval(extra)
        if updated <= Date() {
            stop()
            return
        }
        self.endsAt = updated
        total = max(15, total + extra)
        remaining = max(0, updated.timeIntervalSinceNow)
        lastTickSecond = nil
        scheduleNotification(after: max(0, remaining))
        updateActivity(to: updated)
        persist()
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    func stop() {
        ticker?.invalidate()
        ticker = nil
        endsAt = nil
        startedAt = nil
        remaining = 0
        total = 0
        exerciseName = nil
        setDetails = nil
        completedSets = 0
        totalSets = 0
        lastTickSecond = nil
        cancelNotification()
        endActivity()
        persist()
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

            let secondsLeft = Int(ceil(self.remaining))
            if self.isSoundEnabled && secondsLeft <= 3 && secondsLeft >= 1 && self.lastTickSecond != secondsLeft {
                self.lastTickSecond = secondsLeft
                AudioServicesPlaySystemSound(1057) // Короткий мягкий тик
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            }

            if self.remaining <= 0 { self.finish() }
        }
        RunLoop.main.add(timer, forMode: .common)
        ticker = timer
    }

    private func finish() {
        ticker?.invalidate()
        ticker = nil
        endsAt = nil
        startedAt = nil
        remaining = 0
        total = 0
        lastTickSecond = nil
        endActivity()
        persist()
        if UIApplication.shared.applicationState == .active {
            if isSoundEnabled {
                AudioServicesPlaySystemSound(1025) // Завершающий приятный аккорд
            }
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        }
    }

    // MARK: - Live Activity

    private func startActivity(
        from start: Date,
        to end: Date,
        exerciseName: String,
        setDetails: String,
        completedSets: Int,
        totalSets: Int,
        imageFile: String?
    ) {
        endActivity()
        activityIssue = nil

        guard ActivityAuthorizationInfo().areActivitiesEnabled else {
            activityIssue = "Живые активности выключены в настройках iOS"
            return
        }

        let state = RestActivityAttributes.ContentState(
            startedAt: start,
            endsAt: end,
            exerciseName: exerciseName,
            setDetails: setDetails,
            completedSets: completedSets,
            totalSets: totalSets,
            imageFile: imageFile
        )
        do {
            activity = try Activity.request(
                attributes: RestActivityAttributes(),
                content: ActivityContent(state: state, staleDate: end),
                pushType: nil
            )
        } catch {
            activityIssue = "Активность не создана: \(error.localizedDescription)"
        }
    }

    private func updateActivity(to end: Date) {
        guard let activity else { return }
        let state = RestActivityAttributes.ContentState(
            startedAt: activity.content.state.startedAt,
            endsAt: end,
            exerciseName: exerciseName ?? activity.content.state.exerciseName,
            setDetails: setDetails ?? activity.content.state.setDetails,
            completedSets: completedSets,
            totalSets: totalSets,
            imageFile: activity.content.state.imageFile
        )
        Task {
            await activity.update(ActivityContent(state: state, staleDate: end))
        }
    }

    private func endActivity() {
        guard let activity else { return }
        self.activity = nil
        Task { await activity.end(nil, dismissalPolicy: .immediate) }
    }

    // MARK: - Сохранение

    /// Отдых переживает выгрузку приложения: iOS закрывает свёрнутое приложение,
    /// а Live Activity на экране остаётся. Без этого после перезапуска таймер
    /// был пуст, а кнопки на острове уже ничего не делали.
    private struct Saved: Codable {
        var startedAt: Date
        var endsAt: Date
        var total: TimeInterval
        var exerciseName: String?
        var setDetails: String?
        var completedSets: Int
        var totalSets: Int
        var notificationID: String?
    }

    private static let storageKey = "rest_timer_state"

    private func persist() {
        guard let startedAt, let endsAt else {
            UserDefaults.standard.removeObject(forKey: Self.storageKey)
            return
        }
        let saved = Saved(
            startedAt: startedAt, endsAt: endsAt, total: total,
            exerciseName: exerciseName, setDetails: setDetails,
            completedSets: completedSets, totalSets: totalSets,
            notificationID: notificationID
        )
        if let data = try? JSONEncoder().encode(saved) {
            UserDefaults.standard.set(data, forKey: Self.storageKey)
        }
    }

    private func restore() {
        let live = Activity<RestActivityAttributes>.activities
        guard let data = UserDefaults.standard.data(forKey: Self.storageKey),
              let saved = try? JSONDecoder().decode(Saved.self, from: data),
              saved.endsAt > Date()
        else {
            // Отдых кончился, пока приложения не было, — гасим осиротевшие активности.
            UserDefaults.standard.removeObject(forKey: Self.storageKey)
            for activity in live {
                Task { await activity.end(nil, dismissalPolicy: .immediate) }
            }
            return
        }
        startedAt = saved.startedAt
        endsAt = saved.endsAt
        total = saved.total
        remaining = saved.endsAt.timeIntervalSinceNow
        exerciseName = saved.exerciseName
        setDetails = saved.setDetails
        completedSets = saved.completedSets
        totalSets = saved.totalSets
        notificationID = saved.notificationID
        activity = live.first
        for extra in live.dropFirst() {
            Task { await extra.end(nil, dismissalPolicy: .immediate) }
        }
        scheduleTicker()
    }

    // MARK: - Уведомление

    private func scheduleNotification(after duration: TimeInterval) {
        guard duration > 0 else { return }
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound]) { granted, _ in
            guard granted else { return }
            let content = UNMutableNotificationContent()
            content.title = self.exerciseName != nil ? "Отдых закончен: \(self.exerciseName!)" : "Отдых закончен"
            content.body = self.setDetails != nil ? "Следующий подход: \(self.setDetails!). Пора!" : "Пора к следующему подходу."
            content.sound = .default

            let id = UUID().uuidString
            let request = UNNotificationRequest(
                identifier: id,
                content: content,
                trigger: UNTimeIntervalNotificationTrigger(timeInterval: duration, repeats: false)
            )
            center.add(request)
            DispatchQueue.main.async {
                self.notificationID = id
                self.persist()
            }
        }
    }

    private func cancelNotification() {
        guard let notificationID else { return }
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [notificationID])
        self.notificationID = nil
    }
}
