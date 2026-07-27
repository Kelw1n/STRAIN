import Foundation
import WidgetKit

/// Складывает состояние профиля в общий контейнер и просит виджеты обновиться.
enum WidgetBridge {
    static func publish(_ profile: ProgramProfile, now: Date = Date()) {
        let schedule = profile.schedule
        let calendar = WorkoutScheduler.trainingCalendar()

        guard let focus = schedule.focus else {
            WidgetStore.save(WidgetSnapshot(
                relative: "Цикл завершён",
                dateText: RuDate.dayMonth(now, calendar: calendar),
                weekText: "",
                title: profile.programKind.rawValue,
                benchSession: nil,
                benchTopWeight: nil,
                doneDays: profile.completedDayCount,
                totalDays: profile.totalDays,
                isRestToday: false,
                updatedAt: now
            ))
            WidgetCenter.shared.reloadAllTimelines()
            return
        }

        let bench = focus.benchSession.flatMap { session in
            profile.benchWave.first { $0.id == session }
        }

        WidgetStore.save(WidgetSnapshot(
            relative: schedule.relativeTitle(for: focus),
            dateText: RuDate.dayMonth(focus.date, calendar: calendar),
            weekText: "Неделя \(focus.week)",
            title: focus.fullTitle,
            benchSession: focus.benchSession,
            benchTopWeight: bench.map { Int($0.topWeight.rounded()) },
            doneDays: profile.completedDayCount,
            totalDays: profile.totalDays,
            isRestToday: schedule.isRestDay,
            updatedAt: now
        ))
        WidgetCenter.shared.reloadAllTimelines()
    }
}
