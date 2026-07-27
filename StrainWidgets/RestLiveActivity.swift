import ActivityKit
import SwiftUI
import WidgetKit

/// Палитра расширения. Тема приложения живёт в другом модуле, поэтому
/// нужные цвета продублированы здесь — их всего два.
private enum WidgetPalette {
    static let accent = Color(red: 0.16, green: 0.84, blue: 0.76)
    static let accentDeep = Color(red: 0.35, green: 0.47, blue: 0.98)

    static var gradient: LinearGradient {
        LinearGradient(colors: [accent, accentDeep], startPoint: .leading, endPoint: .trailing)
    }
}

/// Живая активность таймера отдыха: экран блокировки и Dynamic Island.
struct RestLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: RestActivityAttributes.self) { context in
            LockScreenView(state: context.state)
                .activityBackgroundTint(Color.black.opacity(0.6))
                .activitySystemActionForegroundColor(WidgetPalette.accent)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label("Отдых", systemImage: "hourglass")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(WidgetPalette.accent)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text(timerInterval: context.state.range, countsDown: true)
                        .font(.system(size: 17, weight: .bold, design: .rounded))
                        .monospacedDigit()
                        .multilineTextAlignment(.trailing)
                        .frame(width: 62)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    ProgressView(timerInterval: context.state.range, countsDown: true) {
                        EmptyView()
                    } currentValueLabel: {
                        EmptyView()
                    }
                    .progressViewStyle(.linear)
                    .tint(WidgetPalette.accent)
                }
            } compactLeading: {
                Image(systemName: "hourglass")
                    .foregroundStyle(WidgetPalette.accent)
            } compactTrailing: {
                // Фиксированная ширина: без неё «остров» дёргается на смене цифр.
                Text(timerInterval: context.state.range, countsDown: true)
                    .font(.caption2.weight(.bold))
                    .monospacedDigit()
                    .multilineTextAlignment(.trailing)
                    .frame(width: 38)
                    .foregroundStyle(WidgetPalette.accent)
            } minimal: {
                Image(systemName: "hourglass")
                    .foregroundStyle(WidgetPalette.accent)
            }
            .keylineTint(WidgetPalette.accent)
        }
    }
}

private struct LockScreenView: View {
    let state: RestActivityAttributes.ContentState

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                Image(systemName: "hourglass")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 32, height: 32)
                    .background(WidgetPalette.gradient, in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                VStack(alignment: .leading, spacing: 1) {
                    Text("Отдых между подходами")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                    Text("STRAIN")
                        .font(.system(size: 11, weight: .black))
                        .tracking(1.2)
                        .foregroundStyle(WidgetPalette.accent)
                }

                Spacer(minLength: 4)

                Text(timerInterval: state.range, countsDown: true)
                    .font(.system(size: 26, weight: .bold, design: .rounded))
                    .monospacedDigit()
                    .multilineTextAlignment(.trailing)
                    .frame(width: 92)
            }

            ProgressView(timerInterval: state.range, countsDown: true) {
                EmptyView()
            } currentValueLabel: {
                EmptyView()
            }
            .progressViewStyle(.linear)
            .tint(WidgetPalette.accent)
        }
        .padding(16)
    }
}
