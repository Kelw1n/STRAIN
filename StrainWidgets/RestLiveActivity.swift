import ActivityKit
import SwiftUI
import UIKit
import WidgetKit

// MARK: - Палитра виджета (Фирменный стиль REBRN)

private enum WidgetPalette {
    static let accent = Color(red: 1.0, green: 0.376, blue: 0.161) // #FF6029
    static let done = Color(red: 0.18, green: 0.835, blue: 0.45) // #2ED573
    static let bg = Color(red: 0.05, green: 0.05, blue: 0.06)
    static let control = Color(white: 0.18)
}

/// Картинка упражнения: приложение кладёт её в общий контейнер при старте отдыха.
/// Нет контейнера или файла — показываем значок.
private func restArtwork(_ file: String?) -> UIImage? {
    guard let file,
          let url = FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: WidgetStore.appGroup)?
            .appendingPathComponent(file)
    else { return nil }
    return UIImage(contentsOfFile: url.path)
}

// MARK: - Живая активность (Live Activity & Dynamic Island)

struct RestLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: RestActivityAttributes.self) { context in
            LockScreenView(state: context.state, isDone: context.isStale)
                .activityBackgroundTint(WidgetPalette.bg)
                .activitySystemActionForegroundColor(WidgetPalette.accent)
        } dynamicIsland: { context in
            let state = context.state
            let done = context.isStale
            return DynamicIsland {
                // Как у REBRN: «◯ ОТДЫХ» слева, сколько уже отдыхаешь — справа.
                DynamicIslandExpandedRegion(.leading) {
                    RestBadge(done: done)
                        .padding(.leading, 6)
                        .padding(.top, 2)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    ElapsedText(state: state, done: done, size: 15)
                        .padding(.trailing, 6)
                        .padding(.top, 2)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(spacing: 12) {
                        ExerciseLine(state: state, thumb: 40, title: 16)
                        ControlsLine(state: state, done: done, timerSize: 28, button: 40)
                    }
                    .padding(.horizontal, 6)
                    .padding(.top, 4)
                }
            } compactLeading: {
                RestRing(done: done, size: 14)
                    .padding(.leading, 2)
            } compactTrailing: {
                if done {
                    Image(systemName: "checkmark")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(WidgetPalette.done)
                } else {
                    Text(timerInterval: state.range, countsDown: true, showsHours: false)
                        .font(.system(size: 13, weight: .heavy))
                        .monospacedDigit()
                        .foregroundStyle(WidgetPalette.accent)
                        .multilineTextAlignment(.trailing)
                        .frame(width: 40)
                }
            } minimal: {
                RestRing(done: done, size: 16)
            }
            .keylineTint(WidgetPalette.accent)
        }
    }
}

// MARK: - Части

/// Оранжевое кольцо — знак отдыха, как у REBRN.
private struct RestRing: View {
    let done: Bool
    var size: CGFloat = 14

    var body: some View {
        Circle()
            .strokeBorder(done ? WidgetPalette.done : WidgetPalette.accent, lineWidth: size * 0.16)
            .frame(width: size, height: size)
    }
}

private struct RestBadge: View {
    let done: Bool

    var body: some View {
        HStack(spacing: 6) {
            RestRing(done: done, size: 15)
            Text(done ? "ГОТОВО" : "ОТДЫХ")
                .font(.system(size: 13, weight: .heavy))
                .tracking(0.6)
                .foregroundStyle(done ? WidgetPalette.done : WidgetPalette.accent)
        }
    }
}

/// Сколько прошло с начала отдыха.
private struct ElapsedText: View {
    let state: RestActivityAttributes.ContentState
    let done: Bool
    var size: CGFloat = 15

    var body: some View {
        Group {
            if done {
                Text("к подходу")
                    .foregroundStyle(WidgetPalette.done)
            } else {
                Text(timerInterval: state.range, countsDown: false, showsHours: false)
                    .monospacedDigit()
                    .foregroundStyle(.white)
            }
        }
        .font(.system(size: size, weight: .bold))
        .multilineTextAlignment(.trailing)
        .frame(width: 70, alignment: .trailing)
    }
}

/// Картинка, название и точки подходов: сделанные залиты, следующий — кольцом.
private struct ExerciseLine: View {
    let state: RestActivityAttributes.ContentState
    var thumb: CGFloat = 40
    var title: CGFloat = 16

    var body: some View {
        HStack(spacing: 12) {
            Group {
                if let image = restArtwork(state.imageFile) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                } else {
                    Color(white: 0.85)
                        .overlay {
                            Image(systemName: "dumbbell.fill")
                                .font(.system(size: thumb * 0.42, weight: .semibold))
                                .foregroundStyle(Color(white: 0.4))
                        }
                }
            }
            .frame(width: thumb, height: thumb)
            .clipShape(RoundedRectangle(cornerRadius: thumb * 0.24, style: .continuous))

            VStack(alignment: .leading, spacing: 5) {
                Text(state.exerciseName.isEmpty ? "Отдых между подходами" : state.exerciseName)
                    .font(.system(size: title, weight: .bold))
                    .foregroundStyle(.white)
                    .lineLimit(1)

                HStack(spacing: 5) {
                    if state.totalSets > 0 {
                        ForEach(0..<min(state.totalSets, 8), id: \.self) { index in
                            dot(index)
                        }
                    }
                    if !state.setDetails.isEmpty {
                        Text(state.setDetails)
                            .font(.system(size: 13, weight: .medium))
                            .foregroundStyle(.white.opacity(0.6))
                            .padding(.leading, 3)
                    }
                }
            }
            Spacer(minLength: 0)
        }
    }

    @ViewBuilder
    private func dot(_ index: Int) -> some View {
        if index < state.completedSets {
            Circle().fill(WidgetPalette.accent).frame(width: 7, height: 7)
        } else if index == state.completedSets {
            Circle().strokeBorder(WidgetPalette.accent, lineWidth: 1.5).frame(width: 7, height: 7)
        } else {
            Circle().fill(Color.white.opacity(0.25)).frame(width: 7, height: 7)
        }
    }
}

/// «−», крупный отсчёт, «+» и «Пропустить» справа.
private struct ControlsLine: View {
    let state: RestActivityAttributes.ContentState
    let done: Bool
    var timerSize: CGFloat = 28
    var button: CGFloat = 40

    var body: some View {
        HStack(spacing: 10) {
            Link(destination: URL(string: "strain://timer/minus30")!) {
                Image(systemName: "minus")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: button, height: button)
                    .background(WidgetPalette.control, in: Circle())
            }

            Group {
                if done {
                    Text("0:00")
                } else {
                    Text(timerInterval: state.range, countsDown: true, showsHours: false)
                }
            }
            .font(.system(size: timerSize, weight: .heavy))
            .monospacedDigit()
            .foregroundStyle(WidgetPalette.accent)
            .multilineTextAlignment(.center)
            .frame(width: timerSize * 2.7)

            Link(destination: URL(string: "strain://timer/add30")!) {
                Image(systemName: "plus")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: button, height: button)
                    .background(WidgetPalette.control, in: Circle())
            }

            Spacer(minLength: 4)

            Link(destination: URL(string: "strain://timer/stop")!) {
                Text("Пропустить")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 20)
                    .frame(height: button + 2)
                    .background(WidgetPalette.accent, in: Capsule())
            }
        }
    }
}

// MARK: - Экран блокировки

private struct LockScreenView: View {
    let state: RestActivityAttributes.ContentState
    let isDone: Bool

    var body: some View {
        VStack(spacing: 12) {
            HStack {
                RestBadge(done: isDone)
                Spacer()
                ElapsedText(state: state, done: isDone, size: 14)
            }
            ExerciseLine(state: state, thumb: 44, title: 16)
            ControlsLine(state: state, done: isDone, timerSize: 30, button: 44)
        }
        .padding(16)
        .background(WidgetPalette.bg)
    }
}
