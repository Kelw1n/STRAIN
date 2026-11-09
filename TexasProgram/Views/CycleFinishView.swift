import SwiftUI

/// Карточка на месте сегодняшней тренировки, когда все недели программы закрыты.
struct ProgramFinishedCard: View {
    let profile: ProgramProfile
    let onOpen: () -> Void

    var body: some View {
        let recap = profile.cycleRecap
        Button(action: onOpen) {
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("ПРОГРАММА ПРОЙДЕНА")
                            .font(.system(size: 11, weight: .bold))
                            .tracking(1.2)
                            .foregroundStyle(Theme.accent)
                        Text("\(recap.weeks) \(RuPlural.word(recap.weeks, "неделя", "недели", "недель")) позади")
                            .font(.system(size: 20, weight: .bold, design: Theme.fontDesign))
                            .foregroundStyle(.ink)
                    }
                    Spacer()
                    Image(systemName: "flag.checkered")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(Theme.accent)
                }
                HStack(spacing: 10) {
                    stat("\(recap.workouts)", RuPlural.word(recap.workouts, "тренировка", "тренировки", "тренировок"))
                    if recap.tonnage > 0 { stat(WeightFormat.tonnage(recap.tonnage), "поднято") }
                    if let best = recap.growth.max(by: { $0.delta < $1.delta }), best.delta > 0 {
                        stat("+" + WeightFormat.kilograms(best.delta), best.name.lowercased())
                    }
                }
                HStack {
                    Text("Что дальше")
                        .font(.system(size: 15, weight: .bold))
                    Spacer()
                    Image(systemName: "arrow.right")
                        .font(.system(size: 14, weight: .bold))
                }
                .foregroundStyle(.canvas)
                .padding(.horizontal, 18)
                .padding(.vertical, 14)
                .background(Color.ink, in: Capsule())
            }
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        }
        .buttonStyle(.pressable)
    }

    private func stat(_ value: String, _ caption: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value)
                .font(.system(size: 17, weight: .bold))
                .foregroundStyle(.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Text(caption)
                .font(.system(size: 11.5))
                .foregroundStyle(Color.ink.opacity(0.5))
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Итоги цикла и выбор, как начинать следующий.
struct CycleFinishView: View {
    @Environment(\.dismiss) private var dismiss
    @Bindable var profile: ProgramProfile

    @State private var feel: CycleFeel?
    @State private var showingEditor = false
    @State private var showingManual = false
    @State private var showingPeaking = false
    @State private var confirmRepeat = false

    private let recap: CycleRecap
    private let hint: CycleFeelHint?

    init(profile: ProgramProfile, feel: CycleFeel? = nil) {
        self.profile = profile
        _feel = State(initialValue: feel)
        recap = profile.cycleRecap
        hint = profile.cycleFeelHint
    }

    private var next: NextCycleStart? { feel.flatMap(profile.nextCycleStart) }
    private var nextNumber: Int { profile.cycleNumber + 1 }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    header
                    if !recap.growth.isEmpty { growthSection }
                    question
                    if let next { preview(next) }
                    actions
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 40)
            }
            .scrollIndicators(.hidden)
            .screenBackground()
            .navigationTitle(profile.isProgramFinished ? "Цикл \(profile.cycleNumber) пройден" : "Новый цикл")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Закрыть") { dismiss() } }
            }
            .sheet(isPresented: $showingEditor) { editor }
            .sheet(isPresented: $showingManual) {
                if case .maxes(let squat, let bench, let deadlift, _)? = next {
                    NewCycleView(profile: profile, suggested: (squat, bench, deadlift)) { dismiss() }
                } else {
                    NewCycleView(profile: profile) { dismiss() }
                }
            }
            .sheet(isPresented: $showingPeaking) { PeakingView(profile: profile) }
            .confirmationDialog("Повторить цикл с теми же весами?", isPresented: $confirmRepeat, titleVisibility: .visible) {
                Button("Повторить") {
                    profile.repeatCycle()
                    dismiss()
                }
                Button("Отмена", role: .cancel) {}
            } message: {
                Text("Программа начнётся с первой недели с прежними стартовыми весами. История и графики останутся.")
            }
        }
    }

    // MARK: - Итоги

    private var header: some View {
        HStack(spacing: 10) {
            tile("\(recap.workouts)", RuPlural.word(recap.workouts, "тренировка", "тренировки", "тренировок"))
            tile("\(recap.weeks)", RuPlural.word(recap.weeks, "неделя", "недели", "недель"))
            tile(recap.tonnage > 0 ? WeightFormat.tonnage(recap.tonnage) : "—", "поднято")
        }
    }

    private func tile(_ value: String, _ caption: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(value)
                .font(.system(size: 22, weight: .bold, design: Theme.fontDesign))
                .foregroundStyle(.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            Text(caption)
                .font(.system(size: 12))
                .foregroundStyle(Color.ink.opacity(0.5))
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private var growthSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionTitle("Как выросли веса")
            VStack(spacing: 0) {
                ForEach(Array(recap.growth.prefix(8).enumerated()), id: \.element.id) { index, item in
                    if index > 0 { Divider().overlay(Color.ink.opacity(0.06)) }
                    row(item)
                }
            }
            .padding(.horizontal, 16)
            .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
    }

    private func row(_ item: CycleGrowth) -> some View {
        HStack(spacing: 10) {
            Text(item.name)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(.ink)
                .lineLimit(1)
            Spacer(minLength: 8)
            Text("\(WeightFormat.short(item.from)) → \(WeightFormat.kilograms(item.to))")
                .font(.system(size: 14))
                .foregroundStyle(Color.ink.opacity(0.6))
                .monospacedDigit()
            if item.delta != 0 {
                Text((item.delta > 0 ? "+" : "−") + WeightFormat.short(abs(item.delta)))
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(item.delta > 0 ? Theme.accent : Color.ink.opacity(0.5))
                    .monospacedDigit()
                    .frame(minWidth: 44, alignment: .trailing)
            }
        }
        .padding(.vertical, 13)
    }

    // MARK: - Вопрос

    private var question: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionTitle("Как шли веса последние две недели?")
            ForEach(CycleFeel.allCases) { option in
                Button {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    withAnimation(Motion.snappy) { feel = option }
                } label: {
                    HStack(spacing: 14) {
                        Image(systemName: feel == option ? "largecircle.fill.circle" : "circle")
                            .font(.system(size: 22))
                            .foregroundStyle(feel == option ? Theme.accent : Color.ink.opacity(0.3))
                        VStack(alignment: .leading, spacing: 2) {
                            Text(option.title)
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundStyle(.ink)
                            Text(option.caption)
                                .font(.system(size: 13))
                                .foregroundStyle(Color.ink.opacity(0.55))
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 18, style: .continuous)
                            .strokeBorder(feel == option ? Theme.accent : .clear, lineWidth: 1.5)
                    )
                }
                .buttonStyle(.pressable)
            }
            if let hint {
                Text(hint.text)
                    .font(.system(size: 13))
                    .foregroundStyle(Color.ink.opacity(0.55))
                    .padding(.horizontal, 4)
            }
        }
    }

    // MARK: - Следующий цикл

    private func preview(_ next: NextCycleStart) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionTitle(isCustom ? "Цикл \(nextNumber) начнётся с" : "Новые максимумы")
            if let feel {
                Text(feel.outcome)
                    .font(.system(size: 14))
                    .foregroundStyle(Color.ink.opacity(0.7))
                    .padding(.horizontal, 4)
            }
            if next.changes.isEmpty {
                Text("В программе нет упражнений с прибавкой веса — стартовые веса останутся прежними.")
                    .font(.system(size: 14))
                    .foregroundStyle(Color.ink.opacity(0.55))
                    .padding(.horizontal, 4)
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(next.changes.prefix(10).enumerated()), id: \.element.id) { index, item in
                        if index > 0 { Divider().overlay(Color.ink.opacity(0.06)) }
                        row(item)
                    }
                }
                .padding(.horizontal, 16)
                .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            }
            if !isCustom {
                Text("Это прикидка от весов, до которых ты дошёл. Если есть возможность — проверь \(profile.maximumLabel) на первой тяжёлой тренировке и поправь числа.")
                    .font(.system(size: 12.5))
                    .foregroundStyle(Color.ink.opacity(0.5))
                    .padding(.horizontal, 4)
            }
        }
        .transition(.opacity.combined(with: .move(edge: .bottom)))
    }

    private var isCustom: Bool { profile.programKind == .custom }

    private var actions: some View {
        VStack(spacing: 10) {
            Button {
                start()
            } label: {
                Text(feel == nil ? "Выбери ответ выше" : "Начать цикл \(nextNumber)")
                    .font(.system(size: 16, weight: .black, design: Theme.fontDesign))
                    .foregroundStyle(.canvas)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 17)
                    .background(Color.ink.opacity(feel == nil ? 0.3 : 1), in: Capsule())
            }
            .buttonStyle(.pressable)
            .disabled(feel == nil)

            if feel != nil {
                secondary(isCustom ? "Поправить веса и упражнения" : "Ввести максимумы вручную") {
                    if isCustom { showingEditor = true } else { showingManual = true }
                }
            } else if isCustom {
                secondary("Изменить программу") { showingEditor = true }
            }
            if profile.programKind == .texas && !profile.isPeaking {
                secondary("Пикирование перед тестом") { showingPeaking = true }
            }
            secondary("Повторить с теми же весами") { confirmRepeat = true }
        }
        .padding(.top, 4)
    }

    private func secondary(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(.ink)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 15)
                .background(Theme.surface(.dark), in: Capsule())
        }
        .buttonStyle(.pressable)
    }

    private func sectionTitle(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 17, weight: .bold, design: Theme.fontDesign))
            .foregroundStyle(.ink)
            .padding(.horizontal, 4)
    }

    @ViewBuilder
    private var editor: some View {
        let base: CustomProgram? = {
            if case .custom(let program, _)? = next { return program }
            return profile.customProgram
        }()
        CustomProgramBuilderView(
            initial: base,
            title: "Цикл \(nextNumber)",
            saveTitle: "Начать цикл \(nextNumber)",
            onBack: { showingEditor = false }
        ) { program in
            profile.startNewCustomCycle(program)
            showingEditor = false
            dismiss()
        }
    }

    private func start() {
        guard let next else { return }
        UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
        switch next {
        case .custom(let program, _):
            profile.startNewCustomCycle(program)
        case .maxes(let squat, let bench, let deadlift, _):
            profile.startNewCycle(squat: squat, bench: bench, deadlift: deadlift)
        }
        dismiss()
    }
}
