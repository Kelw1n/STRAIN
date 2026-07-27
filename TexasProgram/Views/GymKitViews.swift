import SwiftUI

// MARK: - Раскладка блинов и разминка

/// Раскрывающийся блок под упражнением с конкретным весом:
/// что вешать на гриф и с чего разминаться.
struct LoadHelperView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let weight: Double
    @State private var expanded = false

    private var plates: String? { PlateMath.perSideText(for: weight) }
    private var warmups: [WarmupSet] { PlateMath.warmups(to: weight) }

    var body: some View {
        if plates != nil || !warmups.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                Button {
                    withAnimation(Motion.maybe(Motion.card, reduce: reduceMotion)) { expanded.toggle() }
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "circle.hexagongrid.fill").font(.caption2)
                        Text(expanded ? "Скрыть разбор" : "Блины и разминка")
                            .font(.caption.weight(.semibold))
                        Image(systemName: "chevron.down")
                            .font(.system(size: 9, weight: .bold))
                            .rotationEffect(.degrees(expanded ? 180 : 0))
                    }
                    .foregroundStyle(Theme.accent)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.pressable)

                if expanded {
                    VStack(alignment: .leading, spacing: 10) {
                        if let plates {
                            VStack(alignment: .leading, spacing: 3) {
                                Text("НА СТОРОНУ")
                                    .font(.system(size: 9, weight: .bold)).tracking(1)
                                    .foregroundStyle(.secondary)
                                Text(plates)
                                    .font(.callout.weight(.bold).monospacedDigit())
                                    .foregroundStyle(Theme.accentGradient)
                                Text("гриф \(Int(PlateMath.barWeight)) кг")
                                    .font(.caption2).foregroundStyle(.tertiary)
                            }
                        }
                        if !warmups.isEmpty {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("РАЗМИНКА")
                                    .font(.system(size: 9, weight: .bold)).tracking(1)
                                    .foregroundStyle(.secondary)
                                ForEach(warmups) { set in
                                    HStack(spacing: 8) {
                                        Text("\(set.weightText) кг")
                                            .font(.caption.weight(.semibold).monospacedDigit())
                                            .frame(width: 62, alignment: .leading)
                                        Text("× \(set.reps)")
                                            .font(.caption).foregroundStyle(.secondary)
                                        Spacer(minLength: 0)
                                        if let side = PlateMath.perSideText(for: set.weight) {
                                            Text(side).font(.caption2).foregroundStyle(.tertiary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.primary.opacity(0.04), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .transition(.opacity.combined(with: .offset(y: -8)))
                }
            }
        }
    }
}

// MARK: - Таймер отдыха

/// Кнопки запуска отдыха под списком упражнений.
struct RestTimerLauncher: View {
    @Environment(RestTimer.self) private var timer

    var body: some View {
        CardView(padding: 16, spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "hourglass").font(.footnote.weight(.bold)).foregroundStyle(Theme.accent)
                Text("Отдых между подходами").font(.subheadline.weight(.semibold))
            }
            HStack(spacing: 8) {
                ForEach(RestTimer.presets, id: \.self) { preset in
                    Button {
                        timer.start(preset)
                    } label: {
                        Text(label(for: preset))
                            .font(.subheadline.weight(.bold).monospacedDigit())
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 11)
                            .background(Theme.accent.opacity(0.14), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                            .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .strokeBorder(Theme.accent.opacity(0.32), lineWidth: 1))
                            .foregroundStyle(Theme.accent)
                    }
                    .buttonStyle(.pressable)
                }
            }
            Text("Минимум три минуты; на тяжёлых подходах можно и больше.")
                .font(.caption2).foregroundStyle(.tertiary)
        }
    }

    private func label(for seconds: TimeInterval) -> String {
        let minutes = Int(seconds) / 60
        return "\(minutes) мин"
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
                    Text("отдых").font(.caption2).foregroundStyle(.secondary)
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
