import ActivityKit
import SwiftUI
import WidgetKit

// MARK: - Палитра виджета (Фирменный стиль REBRN)

private enum WidgetPalette {
    static let accent = Color(red: 1.0, green: 0.376, blue: 0.161) // #FF6029
    static let accentDeep = Color(red: 0.88, green: 0.28, blue: 0.10)
    static let done = Color(red: 0.18, green: 0.835, blue: 0.45) // #2ED573
    static let bg = Color(red: 0.05, green: 0.05, blue: 0.06)

    static var gradient: LinearGradient {
        LinearGradient(colors: [accent, accentDeep], startPoint: .leading, endPoint: .trailing)
    }
}

// MARK: - Живая активность (Live Activity & Dynamic Island)

struct RestLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: RestActivityAttributes.self) { context in
            LockScreenView(state: context.state, isDone: context.isStale)
                .activityBackgroundTint(WidgetPalette.bg)
                .activitySystemActionForegroundColor(WidgetPalette.accent)
        } dynamicIsland: { context in
            let done = context.isStale
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    HStack(spacing: 5) {
                        Circle()
                            .fill(WidgetPalette.accent)
                            .frame(width: 8, height: 8)
                        Text(done ? "ГОТОВО" : "ОТДЫХ")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(done ? WidgetPalette.done : WidgetPalette.accent)
                    }
                    .padding(.leading, 4)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    if done {
                        Text("к подходу")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(WidgetPalette.done)
                    } else {
                        Text(timerInterval: context.state.range, countsDown: true, showsHours: false)
                            .font(.system(size: 19, weight: .black, design: .rounded))
                            .monospacedDigit()
                            .foregroundStyle(WidgetPalette.accent)
                    }
                }
                DynamicIslandExpandedRegion(.center) {
                    if !context.state.exerciseName.isEmpty {
                        Text(context.state.exerciseName)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(.white)
                            .lineLimit(1)
                    }
                }
                DynamicIslandExpandedRegion(.bottom) {
                    HStack(spacing: 12) {
                        Link(destination: URL(string: "strain://timer/minus30")!) {
                            Image(systemName: "minus")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundStyle(.white)
                                .frame(width: 36, height: 36)
                                .background(Color(white: 0.18), in: Circle())
                        }

                        if !context.state.setDetails.isEmpty {
                            Text(context.state.setDetails)
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(.white.opacity(0.7))
                        }

                        Spacer()

                        Link(destination: URL(string: "strain://timer/add30")!) {
                            Image(systemName: "plus")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundStyle(.white)
                                .frame(width: 36, height: 36)
                                .background(Color(white: 0.18), in: Circle())
                        }

                        Link(destination: URL(string: "strain://timer/stop")!) {
                            Text("Пропустить")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
                                .background(WidgetPalette.accent, in: Capsule())
                        }
                    }
                    .padding(.top, 4)
                }
            } compactLeading: {
                HStack(spacing: 4) {
                    Circle()
                        .fill(WidgetPalette.accent)
                        .frame(width: 7, height: 7)
                    Text("Отдых")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(WidgetPalette.accent)
                }
            } compactTrailing: {
                if done {
                    Image(systemName: "checkmark")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(WidgetPalette.done)
                } else {
                    Text(timerInterval: context.state.range, countsDown: true, showsHours: false)
                        .font(.system(size: 12, weight: .bold, design: .rounded))
                        .monospacedDigit()
                        .foregroundStyle(WidgetPalette.accent)
                }
            } minimal: {
                Circle()
                    .fill(WidgetPalette.accent)
                    .frame(width: 10, height: 10)
            }
            .keylineTint(WidgetPalette.accent)
        }
    }
}

// MARK: - Представление экрана блокировки (Точная копия REBRN)

private struct LockScreenView: View {
    let state: RestActivityAttributes.ContentState
    let isDone: Bool

    var body: some View {
        VStack(spacing: 12) {
            // Верхняя строка: «отдых» слева, таймер справа
            HStack {
                HStack(spacing: 6) {
                    Circle()
                        .stroke(WidgetPalette.accent.opacity(0.35), lineWidth: 2)
                        .overlay {
                            Circle()
                                .trim(from: 0, to: isDone ? 1.0 : 0.65)
                                .stroke(WidgetPalette.accent, style: StrokeStyle(lineWidth: 2, lineCap: .round))
                                .rotationEffect(.degrees(-90))
                        }
                        .frame(width: 14, height: 14)

                    Text("отдых")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(WidgetPalette.accent)
                }

                Spacer()

                // Общее прошедшее время
                Text(timerInterval: state.startedAt...state.endsAt, countsDown: false, showsHours: false)
                    .font(.system(size: 13, weight: .bold, design: .rounded))
                    .foregroundStyle(.white.opacity(0.85))
            }

            // Средняя строка: иконка упражнения + название + точки подходов и вес
            HStack(spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(Color(white: 0.16))
                        .frame(width: 44, height: 44)

                    Image(systemName: "figure.strengthtraining.traditional")
                        .font(.system(size: 22))
                        .foregroundStyle(WidgetPalette.accent)
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text(state.exerciseName.isEmpty ? "Отдых между подходами" : state.exerciseName)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.white)
                        .lineLimit(1)

                    HStack(spacing: 6) {
                        if state.totalSets > 0 {
                            ForEach(0..<min(state.totalSets, 8), id: \.self) { idx in
                                Circle()
                                    .fill(idx < state.completedSets ? WidgetPalette.accent : Color.white.opacity(0.25))
                                    .frame(width: 6, height: 6)
                            }
                        }

                        if !state.setDetails.isEmpty {
                            Text(state.setDetails)
                                .font(.system(size: 13, weight: .medium))
                                .foregroundStyle(.white.opacity(0.70))
                        }
                    }
                }

                Spacer()
            }

            // Нижняя строка: кнопки «−», большой таймер, «+», и оранжевая кнопка «Пропустить»
            HStack(spacing: 12) {
                Link(destination: URL(string: "strain://timer/minus30")!) {
                    Image(systemName: "minus")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: 44, height: 44)
                        .background(Color(white: 0.18), in: Circle())
                }

                Spacer()

                if isDone {
                    Text("0:00")
                        .font(.system(size: 28, weight: .black, design: .rounded))
                        .foregroundStyle(WidgetPalette.accent)
                } else {
                    Text(timerInterval: state.range, countsDown: true, showsHours: false)
                        .font(.system(size: 30, weight: .black, design: .rounded))
                        .monospacedDigit()
                        .foregroundStyle(WidgetPalette.accent)
                }

                Spacer()

                Link(destination: URL(string: "strain://timer/add30")!) {
                    Image(systemName: "plus")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: 44, height: 44)
                        .background(Color(white: 0.18), in: Circle())
                }

                Link(destination: URL(string: "strain://timer/stop")!) {
                    Text("Пропустить")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 22)
                        .padding(.vertical, 12)
                        .background(WidgetPalette.accent, in: Capsule())
                }
            }
        }
        .padding(16)
        .background(WidgetPalette.bg)
    }
}
