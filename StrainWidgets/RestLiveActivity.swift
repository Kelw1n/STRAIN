import ActivityKit
import SwiftUI
import WidgetKit

// MARK: - Палитра виджета

private enum WidgetPalette {
    // Цветовая гамма в стилистике Claude (терракота, теплая глина, шалфей)
    static let accent = Color(red: 0.851, green: 0.467, blue: 0.341)
    static let accentDeep = Color(red: 0.757, green: 0.380, blue: 0.255)
    static let done = Color(red: 0.298, green: 0.686, blue: 0.475)

    static var gradient: LinearGradient {
        LinearGradient(colors: [accent, accentDeep], startPoint: .leading, endPoint: .trailing)
    }
}

// MARK: - Живая активность

struct RestLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: RestActivityAttributes.self) { context in
            LockScreenView(state: context.state, isDone: context.isStale)
                .activityBackgroundTint(Color(red: 0.10, green: 0.09, blue: 0.08).opacity(0.92))
                .activitySystemActionForegroundColor(WidgetPalette.accent)
        } dynamicIsland: { context in
            let done = context.isStale
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label(done ? "Готово" : "Отдых", systemImage: done ? "checkmark.circle.fill" : "hourglass")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(done ? WidgetPalette.done : WidgetPalette.accent)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    if done {
                        Text("к подходу")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(WidgetPalette.done)
                    } else {
                        Text(timerInterval: context.state.range, countsDown: true, showsHours: false)
                            .font(.system(size: 17, weight: .bold, design: .rounded))
                            .monospacedDigit()
                            .multilineTextAlignment(.trailing)
                    }
                }
                DynamicIslandExpandedRegion(.bottom) {
                    HStack(spacing: 10) {
                        if !done {
                            Link(destination: URL(string: "strain://timer/add30")!) {
                                HStack(spacing: 3) {
                                    Image(systemName: "plus")
                                    Text("30с")
                                }
                                .font(.system(size: 11, weight: .bold, design: .rounded))
                                .foregroundStyle(.white)
                                .padding(.vertical, 5)
                                .padding(.horizontal, 9)
                                .background(WidgetPalette.accent, in: Capsule())
                            }
                        }

                        ProgressView(timerInterval: context.state.range, countsDown: true) {
                            EmptyView()
                        } currentValueLabel: {
                            EmptyView()
                        }
                        .tint(done ? WidgetPalette.done : WidgetPalette.accent)

                        Link(destination: URL(string: "strain://timer/stop")!) {
                            HStack(spacing: 3) {
                                Image(systemName: "checkmark")
                                Text("Готово")
                            }
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                            .foregroundStyle(.white)
                            .padding(.vertical, 5)
                            .padding(.horizontal, 9)
                            .background(WidgetPalette.done, in: Capsule())
                        }
                    }
                    .padding(.top, 4)
                }
            } compactLeading: {
                Image(systemName: done ? "checkmark.circle.fill" : "hourglass")
                    .foregroundStyle(done ? WidgetPalette.done : WidgetPalette.accent)
            } compactTrailing: {
                if done {
                    EmptyView()
                } else {
                    Text(timerInterval: context.state.range, countsDown: true, showsHours: false)
                        .font(.caption2.weight(.bold))
                        .monospacedDigit()
                        .frame(maxWidth: 46)
                        .foregroundStyle(WidgetPalette.accent)
                }
            } minimal: {
                Image(systemName: done ? "checkmark.circle.fill" : "hourglass")
                    .foregroundStyle(done ? WidgetPalette.done : WidgetPalette.accent)
            }
            .keylineTint(done ? WidgetPalette.done : WidgetPalette.accent)
        }
    }
}

// MARK: - Представление экрана блокировки

private struct LockScreenView: View {
    let state: RestActivityAttributes.ContentState
    let isDone: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                Image(systemName: isDone ? "checkmark" : "hourglass")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 32, height: 32)
                    .background(WidgetPalette.gradient, in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                VStack(alignment: .leading, spacing: 1) {
                    Text(isDone ? "Отдых закончен" : "Отдых между подходами")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                    Text("STRAIN")
                        .font(.system(size: 11, weight: .black))
                        .tracking(1.2)
                        .foregroundStyle(WidgetPalette.accent)
                }

                Spacer(minLength: 4)

                if isDone {
                    Text("Пора")
                        .font(.system(size: 20, weight: .bold, design: .rounded))
                        .foregroundStyle(WidgetPalette.done)
                } else {
                    Text(timerInterval: state.range, countsDown: true, showsHours: false)
                        .font(.system(size: 26, weight: .bold, design: .rounded))
                        .monospacedDigit()
                        .multilineTextAlignment(.trailing)
                        .fixedSize()
                }
            }

            // Индикатор выполнения
            ProgressView(timerInterval: state.range, countsDown: true) {
                EmptyView()
            } currentValueLabel: {
                EmptyView()
            }
            .tint(isDone ? WidgetPalette.done : WidgetPalette.accent)

            // Интерактивные кнопки управления через гарантированный Deep Link
            if !isDone {
                HStack(spacing: 12) {
                    Link(destination: URL(string: "strain://timer/add30")!) {
                        HStack(spacing: 5) {
                            Image(systemName: "plus")
                                .font(.caption2.weight(.bold))
                            Text("+30 сек")
                                .font(.caption.weight(.semibold))
                        }
                        .foregroundStyle(.white)
                        .padding(.vertical, 7)
                        .padding(.horizontal, 14)
                        .background(WidgetPalette.accent, in: Capsule())
                    }

                    Spacer()

                    Link(destination: URL(string: "strain://timer/stop")!) {
                        HStack(spacing: 5) {
                            Image(systemName: "checkmark")
                                .font(.caption2.weight(.bold))
                            Text("Завершить")
                                .font(.caption.weight(.semibold))
                        }
                        .foregroundStyle(.white)
                        .padding(.vertical, 7)
                        .padding(.horizontal, 14)
                        .background(WidgetPalette.done, in: Capsule())
                    }
                }
                .padding(.top, 2)
            }
        }
        .padding(16)
    }
}
