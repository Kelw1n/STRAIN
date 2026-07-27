import ActivityKit
import SwiftUI
import WidgetKit

/// Палитра расширения. Тема приложения живёт в другом модуле, поэтому
/// нужные цвета продублированы здесь — их всего два.
private enum WidgetPalette {
    static let accent = Color(red: 0.16, green: 0.84, blue: 0.76)
    static let accentDeep = Color(red: 0.35, green: 0.47, blue: 0.98)
    static let done = Color(red: 0.27, green: 0.85, blue: 0.53)

    static var gradient: LinearGradient {
        LinearGradient(colors: [accent, accentDeep], startPoint: .leading, endPoint: .trailing)
    }
}

/// Живая активность таймера отдыха: экран блокировки и Dynamic Island.
struct RestLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: RestActivityAttributes.self) { context in
            LockScreenView(state: context.state, isDone: context.isStale)
                .activityBackgroundTint(Color.black.opacity(0.6))
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
                    // Пустые метки обязательны: иначе таймерный индикатор рисует
                    // собственную подпись с временем, и цифры дублируются под полосой.
                    ProgressView(timerInterval: context.state.range, countsDown: true) {
                        EmptyView()
                    } currentValueLabel: {
                        EmptyView()
                    }
                    .tint(done ? WidgetPalette.done : WidgetPalette.accent)
                }
            } compactLeading: {
                Image(systemName: done ? "checkmark.circle.fill" : "hourglass")
                    .foregroundStyle(done ? WidgetPalette.done : WidgetPalette.accent)
            } compactTrailing: {
                // showsHours: false — иначе текст резервирует ширину под «0:02:39»,
                // и «остров» остаётся растянутым всё время отсчёта.
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

            // Метки пустые: время уже показано крупно выше, дубль под полосой не нужен.
            ProgressView(timerInterval: state.range, countsDown: true) {
                EmptyView()
            } currentValueLabel: {
                EmptyView()
            }
            .tint(isDone ? WidgetPalette.done : WidgetPalette.accent)
        }
        .padding(16)
    }
}
