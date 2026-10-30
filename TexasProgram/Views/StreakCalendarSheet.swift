import SwiftUI
import UIKit

/// Календарь серии, как у REBRN: месяц со стрелками, серия в неделях,
/// тренировки в ней и все дни месяца. Открывается по нажатию на «Серию активностей».
struct StreakCalendarSheet: View {
    @Bindable var profile: ProgramProfile
    @State private var month: Date = Date()

    private var calendar: Calendar { WorkoutScheduler.trainingCalendar() }

    var body: some View {
        let streak = profile.calendarStreak()
        let today = calendar.startOfDay(for: Date())
        let done = Set(profile.completionLog.map { calendar.startOfDay(for: $0.date) })
        let trainingWeekdays = Set(profile.weekdays)
        let isCurrentMonth = calendar.isDate(month, equalTo: today, toGranularity: .month)

        VStack(alignment: .leading, spacing: 22) {
            HStack {
                Text(Self.monthTitle(month, calendar: calendar))
                    .font(.system(size: 26, weight: .heavy, design: Theme.fontDesign))
                    .foregroundStyle(.ink)
                    .contentTransition(.opacity)
                Spacer()
                arrow("chevron.left", enabled: true) { shift(-1) }
                arrow("chevron.right", enabled: !isCurrentMonth) { shift(1) }
            }

            HStack(spacing: 40) {
                stat("Твоя серия", "\(streak.weeks) \(RuPlural.word(streak.weeks, "неделя", "недели", "недель"))")
                stat("Тренировок в серии", "\(streak.workouts)")
            }

            VStack(spacing: 10) {
                HStack(spacing: 0) {
                    ForEach(["ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС"], id: \.self) { title in
                        Text(title)
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(Color.ink.opacity(0.4))
                            .frame(maxWidth: .infinity)
                    }
                }
                ForEach(Self.weeks(of: month, calendar: calendar), id: \.self) { week in
                    HStack(spacing: 0) {
                        ForEach(week, id: \.self) { day in
                            DayCell(
                                date: day,
                                inMonth: calendar.isDate(day, equalTo: month, toGranularity: .month),
                                isToday: day == today,
                                isPast: day < today,
                                isDone: done.contains(day),
                                isTrainingDay: trainingWeekdays.contains(calendar.component(.weekday, from: day)),
                                calendar: calendar
                            )
                            .frame(maxWidth: .infinity)
                        }
                    }
                }
            }
            .id(Self.monthTitle(month, calendar: calendar))
            .transition(.opacity)

            Spacer(minLength: 0)

            Text("Неделя засчитана, если в ней была хотя бы одна тренировка. Начатая неделя серию не обрывает: у тебя есть время до воскресенья.")
                .font(.system(size: 13))
                .foregroundStyle(Color.ink.opacity(0.5))
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 22)
        .padding(.top, 28)
        .padding(.bottom, 16)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Color.tone(0.07).ignoresSafeArea())
        .presentationDetents([.fraction(0.82), .large])
        .presentationDragIndicator(.visible)
        .presentationCornerRadius(32)
    }

    private func stat(_ title: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(Color.ink.opacity(0.55))
            Text(value)
                .font(.system(size: 24, weight: .heavy, design: Theme.fontDesign))
                .foregroundStyle(.ink)
                .contentTransition(.numericText())
        }
    }

    private func arrow(_ symbol: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(Color.ink.opacity(enabled ? 1 : 0.25))
                .frame(width: 40, height: 40)
                .background(Color.ink.opacity(enabled ? 0.1 : 0.04), in: Circle())
        }
        .buttonStyle(.pressable)
        .disabled(!enabled)
    }

    private func shift(_ months: Int) {
        guard let next = calendar.date(byAdding: .month, value: months, to: month) else { return }
        UISelectionFeedbackGenerator().selectionChanged()
        withAnimation(Motion.snappy) { month = next }
    }

    // MARK: Раскладка

    /// Полные недели (пн–вс), покрывающие месяц: соседние дни тоже показываем, приглушёнными.
    static func weeks(of month: Date, calendar: Calendar) -> [[Date]] {
        guard let interval = calendar.dateInterval(of: .month, for: month),
              let first = calendar.dateInterval(of: .weekOfYear, for: interval.start)?.start,
              let lastDay = calendar.date(byAdding: .day, value: -1, to: interval.end),
              let end = calendar.dateInterval(of: .weekOfYear, for: lastDay)?.end
        else { return [] }
        var days: [Date] = []
        var cursor = first
        while cursor < end {
            days.append(calendar.startOfDay(for: cursor))
            guard let next = calendar.date(byAdding: .day, value: 1, to: cursor) else { break }
            cursor = next
        }
        return stride(from: 0, to: days.count, by: 7).map { Array(days[$0..<min($0 + 7, days.count)]) }
    }

    private static let monthNames = ["Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
                                     "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"]

    static func monthTitle(_ date: Date, calendar: Calendar) -> String {
        let parts = calendar.dateComponents([.year, .month], from: date)
        return "\(monthNames[(parts.month ?? 1) - 1]) \(parts.year ?? 0)"
    }
}

/// День в календаре серии. Сделанный — залит акцентом, сегодня — кольцо и точка,
/// будущий тренировочный — оранжевое кольцо, прошедший — серый, чужого месяца — тусклый.
private struct DayCell: View {
    let date: Date
    let inMonth: Bool
    let isToday: Bool
    let isPast: Bool
    let isDone: Bool
    let isTrainingDay: Bool
    let calendar: Calendar

    var body: some View {
        let number = calendar.component(.day, from: date)
        ZStack {
            background
            Text("\(number)")
                .font(.system(size: 17, weight: isToday ? .heavy : .semibold, design: Theme.fontDesign))
                .monospacedDigit()
                .foregroundStyle(textColor)
            if isToday {
                Circle()
                    .fill(isDone ? Color.canvas : Color.ink)
                    .frame(width: 5, height: 5)
                    .offset(y: 13)
            }
        }
        .frame(width: 44, height: 44)
        .padding(.vertical, 2)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(number)\(isDone ? ", тренировка" : "")\(isToday ? ", сегодня" : "")")
    }

    @ViewBuilder
    private var background: some View {
        if !inMonth {
            Circle().fill(Color.ink.opacity(0.05))
        } else if isDone {
            Circle().fill(Theme.accent)
        } else if isToday {
            Circle().strokeBorder(Theme.accent, lineWidth: 2.5)
        } else if isPast {
            Circle().fill(Color.ink.opacity(0.12))
        } else if isTrainingDay {
            Circle().strokeBorder(Theme.accent, lineWidth: 2)
        } else {
            Circle().strokeBorder(Color.ink.opacity(0.15), lineWidth: 1.5)
        }
    }

    private var textColor: Color {
        if !inMonth { return Color.ink.opacity(0.22) }
        if isDone { return .canvas }
        return .ink.opacity(isPast && !isToday ? 0.85 : 1)
    }
}
