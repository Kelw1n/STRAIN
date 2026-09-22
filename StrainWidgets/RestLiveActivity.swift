import ActivityKit
import AppIntents
import SwiftUI
import WidgetKit

// MARK: - App Intents для интерактивного управления отдыхом с экрана блокировки

@available(iOS 17.0, *)
struct AddRestSecondsIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "Добавить 30 секунд"
    static var description = IntentDescription("Продлевает текущий таймер отдыха на 30 секунд")

    @Parameter(title: "Секунды")
    var seconds: Double

    init() {
        self.seconds = 30
    }

    init(seconds: Double) {
        self.seconds = seconds
    }

    func perform() async throws -> some IntentResult {
        for activity in Activity<RestActivityAttributes>.activities {
            let current = activity.content.state
            let newEnd = current.endsAt.addingTimeInterval(seconds)
            let updated = RestActivityAttributes.ContentState(
                startedAt: current.startedAt,
                endsAt: newEnd
            )
            await activity.update(ActivityContent(state: updated, staleDate: newEnd))
        }
        return .result()
    }
}

@available(iOS 17.0, *)
struct StopRestIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "Завершить отдых"
    static var description = IntentDescription("Останавливает текущий таймер отдыха")

    init() {}

    func perform() async throws -> some IntentResult {
        for activity in Activity<RestActivityAttributes>.activities {
            await activity.end(nil, dismissalPolicy: .immediate)
        }
        return .result()
    }
}

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
                .activityBackgroundTint(Color(red: 0.12, green: 0.11, blue: 0.10).opacity(0.85))
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
                    VStack(spacing: 8) {
                        ProgressView(timerInterval: context.state.range, countsDown: true) {
                            EmptyView()
                        } currentValueLabel: {
                            EmptyView()
                        }
                        .tint(done ? WidgetPalette.done : WidgetPalette.accent)

                        if !done {
                            HStack {
                                Button(intent: AddRestSecondsIntent(seconds: 30)) {
                                    HStack(spacing: 3) {
                                        Image(systemName: "plus")
                                        Text("30 сек")
                                    }
                                    .font(.caption2.weight(.bold))
                                    .foregroundStyle(.white)
                                    .padding(.vertical, 4)
                                    .padding(.horizontal, 8)
                                    .background(WidgetPalette.accent.opacity(0.4), in: Capsule())
                                }
                                .buttonStyle(.plain)

                                Spacer()

                                Button(intent: StopRestIntent()) {
                                    HStack(spacing: 3) {
                                        Image(systemName: "checkmark")
                                        Text("Готово")
                                    }
                                    .font(.caption2.weight(.bold))
                                    .foregroundStyle(WidgetPalette.done)
                                    .padding(.vertical, 4)
                                    .padding(.horizontal, 8)
                                    .background(WidgetPalette.done.opacity(0.2), in: Capsule())
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
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

            // Интерактивные кнопки управления (iOS 17+)
            if !isDone {
                HStack(spacing: 12) {
                    Button(intent: AddRestSecondsIntent(seconds: 30)) {
                        HStack(spacing: 5) {
                            Image(systemName: "plus")
                                .font(.caption2.weight(.bold))
                            Text("+30 сек")
                                .font(.caption.weight(.semibold))
                        }
                        .foregroundStyle(.white)
                        .padding(.vertical, 6)
                        .padding(.horizontal, 12)
                        .background(WidgetPalette.accent.opacity(0.35), in: Capsule())
                    }
                    .buttonStyle(.plain)

                    Spacer()

                    Button(intent: StopRestIntent()) {
                        HStack(spacing: 5) {
                            Image(systemName: "checkmark")
                                .font(.caption2.weight(.bold))
                            Text("Завершить")
                                .font(.caption.weight(.semibold))
                        }
                        .foregroundStyle(WidgetPalette.done)
                        .padding(.vertical, 6)
                        .padding(.horizontal, 12)
                        .background(WidgetPalette.done.opacity(0.2), in: Capsule())
                    }
                    .buttonStyle(.plain)
                }
                .padding(.top, 2)
            }
        }
        .padding(16)
    }
}
