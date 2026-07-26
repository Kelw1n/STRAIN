import SwiftUI
import SwiftData

/// Отдельный раздел: волна из 14 жимовых тренировок цикла «Верх / Низ».
struct BenchWaveView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Bindable var profile: ProgramProfile
    @Binding var focused: Int?

    @State private var expanded: Int?
    @State private var pulsing: Int?

    private var sessions: [BenchSessionPlan] { profile.benchWave }
    private var completed: Set<Int> { Set(profile.completedBenchSessions) }
    private var nextSession: Int? { profile.nextBenchSession }

    private var progress: Double {
        guard !sessions.isEmpty else { return 0 }
        return Double(completed.count) / Double(sessions.count)
    }

    var body: some View {
        NavigationStack {
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(spacing: 16) {
                        header.appearIn(0).id("bench-header")
                        chartCard.appearIn(1)
                        legend.appearIn(2)

                        ForEach(Array(sessions.enumerated()), id: \.element.id) { index, session in
                            BenchSessionCard(
                                session: session,
                                isDone: completed.contains(session.id),
                                isNext: nextSession == session.id,
                                isExpanded: expanded == session.id,
                                isPulsing: pulsing == session.id,
                                onTap: { toggleExpanded(session.id) },
                                onToggleDone: {
                                    withAnimation(Motion.maybe(Motion.bouncy, reduce: reduceMotion)) {
                                        profile.toggleBenchCompleted(session.id)
                                    }
                                }
                            )
                            .id(session.id)
                            .appearIn(min(index + 3, 10))
                            .softScroll()
                        }
                    }
                    .padding(.horizontal)
                    .padding(.bottom, 28)
                }
                .scrollIndicators(.hidden)
                .onAppear {
                    if expanded == nil { expanded = focused ?? nextSession }
                    if let focused { jump(to: focused, proxy: proxy) }
                }
                .onChange(of: focused) { _, newValue in
                    guard let newValue else { return }
                    jump(to: newValue, proxy: proxy)
                }
            }
            .screenBackground()
            .navigationTitle("Жим 14")
        }
    }

    private func toggleExpanded(_ id: Int) {
        withAnimation(Motion.maybe(Motion.card, reduce: reduceMotion)) {
            expanded = expanded == id ? nil : id
        }
    }

    private func jump(to session: Int, proxy: ScrollViewProxy) {
        withAnimation(Motion.maybe(Motion.card, reduce: reduceMotion)) {
            expanded = session
            proxy.scrollTo(session, anchor: .center)
        }
        pulsing = session
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.4) {
            if pulsing == session { pulsing = nil }
            if focused == session { focused = nil }
        }
    }

    // MARK: - Шапка

    private var header: some View {
        HighlightCard {
            HStack(alignment: .center, spacing: 18) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("ВОЛНОВАЯ ПРОГРЕССИЯ")
                        .font(.caption2.weight(.bold))
                        .tracking(1.3)
                        .foregroundStyle(Theme.accentGradient)
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        Text("\(completed.count)")
                            .font(.system(size: 38, weight: .bold, design: .rounded))
                            .contentTransition(.numericText())
                        Text("из \(sessions.count) тренировок")
                            .font(.subheadline).foregroundStyle(.secondary)
                    }
                    if let nextSession {
                        Text("Следующая — №\(nextSession)")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(.secondary)
                    } else {
                        Text("Волна пройдена полностью")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(Theme.success)
                    }
                }
                Spacer(minLength: 0)
                RingProgress(value: progress, lineWidth: 10) {
                    Image(systemName: "figure.strengthtraining.traditional")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundStyle(Theme.accentGradient)
                }
                .frame(width: 84, height: 84)
            }

            HStack(spacing: 10) {
                benchStat(title: "1ПМ жима", value: profile.bench5RM)
                if let target = sessions.last?.topWeight {
                    benchStat(title: "Цель волны", value: target, gradient: Theme.recordGradient)
                }
            }
        }
    }

    private func benchStat(title: String, value: Double, gradient: LinearGradient = Theme.accentGradient) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.caption2).foregroundStyle(.secondary)
            Text(value.formatted(.number.precision(.fractionLength(0))) + " кг")
                .font(.system(size: 19, weight: .bold, design: .rounded))
                .foregroundStyle(gradient)
                .contentTransition(.numericText())
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 10)
        .padding(.horizontal, 12)
        .background(Color.primary.opacity(0.05), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    // MARK: - График волны

    private var chartCard: some View {
        CardView(padding: 16, spacing: 12) {
            Text("Верхний вес по тренировкам")
                .font(.subheadline.weight(.semibold))
            BenchWaveChart(
                sessions: sessions,
                completed: completed,
                selected: expanded,
                onSelect: { id in
                    withAnimation(Motion.maybe(Motion.card, reduce: reduceMotion)) { expanded = id }
                }
            )
        }
    }

    private var legend: some View {
        HStack(spacing: 8) {
            ForEach([BenchSetKind.negative, .test, .record], id: \.self) { kind in
                HStack(spacing: 5) {
                    Image(systemName: kind.systemImage).font(.caption2.weight(.bold)).foregroundStyle(kind.tint)
                    Text(kind.rawValue.capitalized).font(.caption2).foregroundStyle(.secondary)
                }
                .padding(.horizontal, 9)
                .padding(.vertical, 6)
                .background(.ultraThinMaterial, in: Capsule())
            }
            Spacer(minLength: 0)
        }
    }
}

// MARK: - График

struct BenchWaveChart: View {
    let sessions: [BenchSessionPlan]
    let completed: Set<Int>
    let selected: Int?
    let onSelect: (Int) -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var grown = false

    private var maxWeight: Double { sessions.map(\.topWeight).max() ?? 1 }
    private var minWeight: Double { sessions.map(\.topWeight).min() ?? 0 }

    private func ratio(_ weight: Double) -> Double {
        guard maxWeight > minWeight else { return 1 }
        return 0.34 + 0.66 * (weight - minWeight) / (maxWeight - minWeight)
    }

    var body: some View {
        HStack(alignment: .bottom, spacing: 5) {
            ForEach(Array(sessions.enumerated()), id: \.element.id) { index, session in
                let isSelected = selected == session.id
                Button { onSelect(session.id) } label: {
                    VStack(spacing: 6) {
                        ZStack(alignment: .top) {
                            Capsule()
                                .fill(session.accent)
                                .frame(height: grown ? max(12, 96 * ratio(session.topWeight)) : 5)
                                .opacity(selected == nil || isSelected ? 1 : 0.4)
                                .overlay(
                                    Capsule().strokeBorder(Color.white.opacity(isSelected ? 0.9 : 0), lineWidth: 1.5)
                                )
                            if completed.contains(session.id) {
                                Image(systemName: "checkmark")
                                    .font(.system(size: 8, weight: .black))
                                    .foregroundStyle(.white)
                                    .padding(.top, 4)
                            }
                        }
                        .animation(
                            Motion.maybe(.spring(response: 0.65, dampingFraction: 0.78).delay(Double(index) * 0.035), reduce: reduceMotion),
                            value: grown
                        )
                        Text("\(session.id)")
                            .font(.system(size: 9, weight: isSelected ? .heavy : .medium, design: .rounded))
                            .foregroundStyle(isSelected ? session.accentColor : Color.secondary)
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Тренировка \(session.id), до \(Int(session.topWeight)) кг")
            }
        }
        .frame(height: 122, alignment: .bottom)
        .onAppear {
            guard !grown else { return }
            if reduceMotion { grown = true } else {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { grown = true }
            }
        }
    }
}

// MARK: - Карточка одной жимовой тренировки

struct BenchSessionCard: View {
    let session: BenchSessionPlan
    let isDone: Bool
    let isNext: Bool
    let isExpanded: Bool
    let isPulsing: Bool
    let onTap: () -> Void
    let onToggleDone: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var doneTrigger = 0

    var body: some View {
        Group {
            if isNext && !isDone {
                HighlightCard(gradient: session.accent) { content }
            } else {
                CardView { content }
            }
        }
        .scaleEffect(isPulsing ? 1.03 : 1)
        .animation(Motion.maybe(Motion.bouncy, reduce: reduceMotion), value: isPulsing)
    }

    @ViewBuilder
    private var content: some View {
        Button(action: onTap) {
            HStack(spacing: 14) {
                numberBadge
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text("Тренировка \(session.id)")
                            .font(.headline)
                            .foregroundStyle(.primary)
                        if let highlight = session.highlight {
                            OutlineBadge(text: highlight.rawValue, tint: highlight.tint)
                        }
                    }
                    Text("Неделя \(session.week) · \(session.shortDayTitle)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 4)
                VStack(alignment: .trailing, spacing: 2) {
                    Text(session.topWeight.formatted(.number.precision(.fractionLength(0))) + " кг")
                        .font(.system(size: 17, weight: .bold, design: .rounded))
                        .foregroundStyle(session.accent)
                    Text("\(session.sets.count) подх.")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
                Image(systemName: "chevron.down")
                    .font(.footnote.weight(.bold))
                    .foregroundStyle(.secondary)
                    .rotationEffect(.degrees(isExpanded ? 180 : 0))
                    .animation(Motion.maybe(Motion.snappy, reduce: reduceMotion), value: isExpanded)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.pressable)

        if isExpanded {
            VStack(spacing: 8) {
                ForEach(Array(session.sets.enumerated()), id: \.element.id) { index, set in
                    BenchSetRow(set: set, index: index)
                }
                if let highlight = session.highlight {
                    HStack(spacing: 6) {
                        Image(systemName: "exclamationmark.triangle.fill").font(.caption2)
                        Text(highlight.hint).font(.caption)
                    }
                    .foregroundStyle(highlight.tint)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, 2)
                }
                Button {
                    doneTrigger += 1
                    onToggleDone()
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: isDone ? "checkmark.circle.fill" : "circle")
                            .symbolEffect(.bounce, value: doneTrigger)
                        Text(isDone ? "Жим выполнен" : "Отметить жим")
                    }
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white)
                    .padding(.vertical, 12)
                    .frame(maxWidth: .infinity)
                    .background(isDone ? Theme.successGradient : session.accent, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .buttonStyle(.pressable)
                .sensoryFeedback(trigger: doneTrigger) { _, _ in
                    isDone ? SensoryFeedback.success : SensoryFeedback.impact(weight: .light)
                }
                .padding(.top, 4)
            }
            .transition(.asymmetric(
                insertion: .opacity.combined(with: .offset(y: -10)),
                removal: .opacity.combined(with: .offset(y: -6))
            ))
        }
    }

    private var numberBadge: some View {
        ZStack {
            Circle()
                .fill(isDone ? AnyShapeStyle(Theme.successGradient) : AnyShapeStyle(session.accentColor.opacity(0.18)))
                .frame(width: 42, height: 42)
            if isDone {
                Image(systemName: "checkmark")
                    .font(.system(size: 16, weight: .black))
                    .foregroundStyle(.white)
                    .transition(.scale.combined(with: .opacity))
            } else {
                Text("\(session.id)")
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                    .foregroundStyle(session.accent)
            }
        }
        .overlay(Circle().strokeBorder(session.accentColor.opacity(isDone ? 0 : 0.4), lineWidth: 1))
    }
}

struct BenchSetRow: View {
    let set: BenchSetPlan
    let index: Int

    var body: some View {
        HStack(spacing: 12) {
            Text("\(set.id)")
                .font(.system(size: 12, weight: .bold, design: .rounded))
                .foregroundStyle(.secondary)
                .frame(width: 20)
            Text(set.weightText)
                .font(.system(size: 16, weight: .bold, design: .rounded))
                .monospacedDigit()
                .foregroundStyle(set.kind.isSpecial ? AnyShapeStyle(set.kind.gradient) : AnyShapeStyle(Color.primary))
            Text("×\(set.reps)")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.secondary)
            Spacer(minLength: 4)
            if set.kind.isSpecial {
                TagBadge(text: set.kind.rawValue, systemImage: set.kind.systemImage, gradient: set.kind.gradient)
            } else {
                Text(set.percentText)
                    .font(.caption.weight(.medium))
                    .foregroundStyle(.tertiary)
            }
        }
        .padding(.vertical, 9)
        .padding(.horizontal, 12)
        .background(Color.primary.opacity(0.04), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .appearIn(index)
    }
}
