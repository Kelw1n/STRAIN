import SwiftUI

// MARK: - Раскладка блинов и разминка

/// Раскрывающийся блок под упражнением с конкретным весом:
/// что вешать на гриф и с чего разминаться.
struct SetTracker {
    let done: Int
    let onTap: (Int) -> Void
    /// Долгий тап по точке: записать, что реально получилось.
    var onHold: ((Int) -> Void)?
    /// Номера подходов, у которых факт уже записан, — они помечаются точкой.
    var logged: Set<Int> = []
}

struct SetDotsView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let total: Int
    let tracker: SetTracker

    var body: some View {
        HStack(spacing: 8) {
            ForEach(0..<max(total, 0), id: \.self) { index in
                let filled = index < tracker.done
                let hasEntry = tracker.logged.contains(index)
                Button {
                    withAnimation(Motion.maybe(Motion.bouncy, reduce: reduceMotion)) {
                        tracker.onTap(index)
                    }
                } label: {
                    Circle()
                        .fill(filled ? AnyShapeStyle(Theme.accentGradient) : AnyShapeStyle(Color.primary.opacity(0.10)))
                        .frame(width: 22, height: 22)
                        .overlay {
                            if filled {
                                Image(systemName: "checkmark")
                                    .font(.system(size: 10, weight: .black))
                                    .foregroundStyle(.white)
                            }
                        }
                        .overlay(Circle().strokeBorder(Theme.accent.opacity(filled ? 0 : 0.28), lineWidth: 1))
                        // Записанный факт помечаем ободком: видно, где цифры есть,
                        // а где точка закрыта на глазок.
                        .overlay(Circle().strokeBorder(Theme.warning, lineWidth: hasEntry ? 2 : 0))
                }
                .buttonStyle(.pressable)
                .onLongPressGesture(minimumDuration: 0.4) { tracker.onHold?(index) }
                .accessibilityLabel("Подход \(index + 1)")
                .accessibilityHint(tracker.onHold == nil ? "" : "Удерживай, чтобы записать вес и повторы")
            }
            Spacer(minLength: 0)
            Text("\(tracker.done) / \(total)")
                .font(.caption2.weight(.semibold).monospacedDigit())
                .foregroundStyle(tracker.done >= total ? Theme.success : .secondary)
        }
    }
}

// MARK: - Таймер отдыха

/// Кнопки запуска отдыха под списком упражнений.
struct RestTimerLauncher: View {
    @Environment(RestTimer.self) private var timer
    @Bindable var profile: ProgramProfile

    var body: some View {
        CardView(padding: 16, spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "hourglass").font(.footnote.weight(.bold)).foregroundStyle(Theme.accent)
                Text("Отдых между подходами").font(.subheadline.weight(.semibold))
            }
            HStack(spacing: 8) {
                ForEach(RestTimer.presets, id: \.self) { preset in
                    let selected = profile.defaultRestSeconds == Int(preset)
                    Button {
                        // Выбранная длительность запоминается: с ней стартует
                        // автоматический отдых после отметки подхода.
                        profile.defaultRestSeconds = Int(preset)
                        timer.start(preset)
                    } label: {
                        Text(label(for: preset))
                            .font(.subheadline.weight(.bold).monospacedDigit())
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 11)
                            .background(Theme.accent.opacity(selected ? 0.26 : 0.14), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                            .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .strokeBorder(Theme.accent.opacity(selected ? 0.7 : 0.32), lineWidth: 1))
                            .foregroundStyle(Theme.accent)
                    }
                    .buttonStyle(.pressable)
                }
            }
            // Кроме трёх заготовок можно набрать своё время: шаг в пятнадцать
            // секунд достаточно мелкий, чтобы попасть в любую привычку.
            HStack(spacing: 10) {
                stepButton("minus") { change(-15) }
                VStack(spacing: 1) {
                    Text(timeText).font(.title3.weight(.bold).monospacedDigit())
                    Text("своё время").font(.caption2).foregroundStyle(.tertiary)
                }
                .frame(maxWidth: .infinity)
                stepButton("plus") { change(15) }
                Button {
                    timer.start(TimeInterval(profile.defaultRestSeconds))
                } label: {
                    Image(systemName: "play.fill")
                        .font(.footnote.weight(.bold))
                        .foregroundStyle(.white)
                        .frame(width: 40, height: 36)
                        .background(Theme.accentGradient, in: RoundedRectangle(cornerRadius: 11, style: .continuous))
                }
                .buttonStyle(.pressable)
            }

            // Кроме трёх заготовок можно набрать своё время: шаг в пятнадцать
            // секунд достаточно мелкий, чтобы попасть в любую привычку.
            HStack(spacing: 10) {
                stepButton("minus") { change(-15) }
                VStack(spacing: 1) {
                    Text(timeText).font(.title3.weight(.bold).monospacedDigit())
                    Text("своё время").font(.caption2).foregroundStyle(.tertiary)
                }
                .frame(maxWidth: .infinity)
                stepButton("plus") { change(15) }
                Button {
                    timer.start(TimeInterval(profile.defaultRestSeconds))
                } label: {
                    Image(systemName: "play.fill")
                        .font(.footnote.weight(.bold))
                        .foregroundStyle(.white)
                        .frame(width: 40, height: 36)
                        .background(Theme.accentGradient, in: RoundedRectangle(cornerRadius: 11, style: .continuous))
                }
                .buttonStyle(.pressable)
            }

            Text("Отмеченный подход запускает отдых сам — выбранной здесь длительности.")
                .font(.caption2).foregroundStyle(.tertiary)
        }
    }

    private func label(for seconds: TimeInterval) -> String {
        let minutes = Int(seconds) / 60
        return "\(minutes) мин"
    }

    private var timeText: String {
        let total = profile.defaultRestSeconds
        return String(format: "%d:%02d", total / 60, total % 60)
    }

    /// Меньше пятнадцати секунд отдыхать бессмысленно, больше получаса — уже перерыв.
    private func change(_ delta: Int) {
        profile.defaultRestSeconds = min(max(profile.defaultRestSeconds + delta, 15), 1800)
    }

    private func stepButton(_ symbol: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.footnote.weight(.bold))
                .foregroundStyle(Theme.accent)
                .frame(width: 40, height: 36)
                .background(Theme.accent.opacity(0.14), in: RoundedRectangle(cornerRadius: 11, style: .continuous))
        }
        .buttonStyle(.pressable)
    }

    private var timeText: String {
        let total = profile.defaultRestSeconds
        return String(format: "%d:%02d", total / 60, total % 60)
    }

    /// Меньше пятнадцати секунд отдыхать бессмысленно, больше получаса — уже перерыв.
    private func change(_ delta: Int) {
        profile.defaultRestSeconds = min(max(profile.defaultRestSeconds + delta, 15), 1800)
    }

    private func stepButton(_ symbol: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.footnote.weight(.bold))
                .foregroundStyle(Theme.accent)
                .frame(width: 40, height: 36)
                .background(Theme.accent.opacity(0.14), in: RoundedRectangle(cornerRadius: 11, style: .continuous))
        }
        .buttonStyle(.pressable)
    }
}

/// Плашка обратного отсчёта поверх вкладок.
struct RestTimerBar: View {
    @Environment(RestTimer.self) private var timer
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        if timer.isRunning {
            HStack(spacing: 14) {
                ZStack {
                    Circle().stroke(Color.primary.opacity(0.12), lineWidth: 4)
                    Circle()
                        .trim(from: 0, to: timer.progress)
                        .stroke(Theme.accentGradient, style: StrokeStyle(lineWidth: 4, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                }
                .frame(width: 34, height: 34)

                VStack(alignment: .leading, spacing: 1) {
                    Text(timer.remainingText)
                        .font(.system(size: 20, weight: .bold, design: .rounded).monospacedDigit())
                    // Пустой «остров» и несозданная активность выглядят одинаково,
                    // поэтому причину отказа показываем прямо здесь.
                    Text(timer.activityIssue ?? "отдых")
                        .font(.caption2)
                        .foregroundStyle(timer.activityIssue == nil ? Color.secondary : Theme.warning)
                        .lineLimit(2)
                }

                Spacer(minLength: 0)

                Button("+1 мин") { timer.add(60) }
                    .font(.caption.weight(.bold))
                    .foregroundStyle(Theme.accent)
                    .buttonStyle(.pressable)

                Button {
                    timer.stop()
                } label: {
                    Image(systemName: "xmark")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.secondary)
                        .frame(width: 30, height: 30)
                        .background(Color.primary.opacity(0.07), in: Circle())
                }
                .buttonStyle(.pressable)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(.regularMaterial, in: Capsule())
            .overlay(Capsule().strokeBorder(Theme.accent.opacity(0.35), lineWidth: 1))
            .shadow(color: .black.opacity(0.3), radius: 16, y: 6)
            .padding(.horizontal, 16)
            .transition(.move(edge: .bottom).combined(with: .opacity))
        }
    }
}
