import SwiftUI
import SwiftData

struct RootView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var profiles: [ProgramProfile]
    @AppStorage("hasSeenSetup") private var hasSeenSetup = false

    var body: some View {
        Group {
            if let profile = profiles.first {
                MainTabView(profile: profile) {
                    modelContext.delete(profile)
                    try? modelContext.save()
                    hasSeenSetup = false
                }
                .transition(.opacity.combined(with: .scale(scale: 1.03)))
            } else {
                ProgramOnboardingView { profile in
                    modelContext.insert(profile)
                    hasSeenSetup = true
                }
                .transition(.opacity)
            }
        }
        .animation(.spring(response: 0.5, dampingFraction: 0.9), value: profiles.isEmpty)
        .tint(Theme.accent)
    }
}

struct ProgramOnboardingView: View {
    let onSave: (ProgramProfile) -> Void
    @State private var selectedProgram: TrainingProgramKind?

    private var forward: AnyTransition {
        .asymmetric(
            insertion: .move(edge: .trailing).combined(with: .opacity),
            removal: .move(edge: .trailing).combined(with: .opacity)
        )
    }

    var body: some View {
        Group {
            switch selectedProgram {
            case .texas:
                SetupView(onBack: { selectedProgram = nil }) { onSave(ProgramProfile(input: $0)) }
                    .transition(forward)
            case .upperLower:
                UpperLowerSetupView(onBack: { selectedProgram = nil }) { onSave(ProgramProfile(upperLowerInput: $0)) }
                    .transition(forward)
            case nil:
                ProgramSelectionView(selection: $selectedProgram)
                    .transition(.asymmetric(
                        insertion: .move(edge: .leading).combined(with: .opacity),
                        removal: .move(edge: .leading).combined(with: .opacity)
                    ))
            }
        }
        .animation(.spring(response: 0.48, dampingFraction: 0.88), value: selectedProgram)
    }
}

struct ProgramSelectionView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Binding var selection: TrainingProgramKind?
    @State private var breathe = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 14) {
                        Image(systemName: "figure.strengthtraining.traditional")
                            .font(.system(size: 40, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(width: 78, height: 78)
                            .background(Theme.accentGradient, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
                            .shadow(color: Theme.accent.opacity(breathe ? 0.55 : 0.25), radius: breathe ? 28 : 14, y: 10)
                            .scaleEffect(breathe ? 1.04 : 1)
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Выбери программу")
                                .font(.system(size: 34, weight: .bold, design: .rounded))
                            Text("После выбора введи свои максимумы. Вернуться сюда можно полным сбросом в настройках.")
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(.top, 12)
                    .appearIn(0)

                    ForEach(Array(TrainingProgramKind.allCases.enumerated()), id: \.element.id) { index, kind in
                        Button {
                            withAnimation(Motion.maybe(Motion.snappy, reduce: reduceMotion)) { selection = kind }
                        } label: {
                            ProgramOptionCard(kind: kind)
                        }
                        .buttonStyle(.pressable)
                        .appearIn(index + 1)
                    }
                }
                .padding(.horizontal)
                .padding(.bottom, 32)
            }
            .scrollIndicators(.hidden)
            .screenBackground()
            .onAppear {
                guard !reduceMotion, !breathe else { return }
                withAnimation(.easeInOut(duration: 2.6).repeatForever(autoreverses: true)) { breathe = true }
            }
        }
    }
}

struct ProgramOptionCard: View {
    let kind: TrainingProgramKind

    private var gradient: LinearGradient {
        kind == .texas ? Theme.accentGradient : Theme.recordGradient
    }

    private var symbol: String {
        kind == .texas ? "chart.line.uptrend.xyaxis" : "square.split.2x1.fill"
    }

    var body: some View {
        CardView(padding: 20) {
            HStack(alignment: .top, spacing: 14) {
                Image(systemName: symbol)
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 46, height: 46)
                    .background(gradient, in: RoundedRectangle(cornerRadius: 15, style: .continuous))
                VStack(alignment: .leading, spacing: 6) {
                    Text(kind.rawValue)
                        .font(.title3.weight(.bold))
                        .foregroundStyle(.primary)
                    Text(kind.subtitle)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                    if kind == .upperLower {
                        TagBadge(text: "Раздел «Жим 14»", systemImage: "waveform.path.ecg", gradient: Theme.recordGradient)
                            .padding(.top, 2)
                    }
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.bold))
                    .foregroundStyle(.secondary)
                    .padding(.top, 6)
            }
        }
    }
}

struct SetupView: View {
    let onBack: () -> Void
    let onSave: (ProgramInput) -> Void
    @State private var squat = "100"
    @State private var bench = "100"
    @State private var deadlift = "100"
    @State private var level: TrainingLevel = .beginner
    @State private var selection: [AdditionalExerciseCategory: String] = [:]
    @State private var showAllOptions = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    ScreenHeader(
                        eyebrow: "12 недель · 3 дня",
                        title: "Техасская программа",
                        subtitle: "Введи реальные протестированные 5ПМ — приложение рассчитает 12 недель автоматически."
                    )
                    .padding(.top, 12)
                    .appearIn(0)

                    CardView {
                        Text("Твои 5ПМ").font(.title3.weight(.bold))
                        NumberField(title: "Приседания", text: $squat)
                        NumberField(title: "Жим лёжа", text: $bench)
                        NumberField(title: "Становая тяга", text: $deadlift)
                        Text("Шаг округления: 2,5 кг").font(.caption).foregroundStyle(.secondary)
                    }
                    .appearIn(1)

                    CardView {
                        Text("Уровень пикирования").font(.title3.weight(.bold))
                        Picker("Уровень", selection: $level) {
                            ForEach(TrainingLevel.allCases) { Text($0.rawValue).tag($0) }
                        }
                        .pickerStyle(.segmented)
                        Text("Для первого прохождения рекомендуется «Начальный».")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    .appearIn(2)

                    CardView {
                        HStack {
                            Text("Дополнительные упражнения").font(.title3.weight(.bold))
                            Spacer()
                            Button(showAllOptions ? "Скрыть" : "Выбрать") {
                                withAnimation(Motion.card) { showAllOptions.toggle() }
                            }
                            .font(.subheadline.weight(.semibold))
                        }
                        if showAllOptions {
                            ForEach(AdditionalExerciseCategory.allCases) { category in
                                ExercisePicker(category: category, value: Binding(
                                    get: { selection[category] },
                                    set: { selection[category] = $0 }
                                ))
                            }
                            .transition(.opacity.combined(with: .offset(y: -8)))
                        } else {
                            Text("Необязательные упражнения можно добавить сейчас или позже в настройках.")
                                .font(.subheadline).foregroundStyle(.secondary)
                        }
                    }
                    .appearIn(3)

                    Button {
                        onSave(ProgramInput(
                            squat5RM: value(squat),
                            bench5RM: value(bench),
                            deadlift5RM: value(deadlift),
                            level: level,
                            pull: selection[.pull],
                            arms: selection[.arms],
                            core: selection[.core],
                            back: selection[.back],
                            press: selection[.press]
                        ))
                    } label: {
                        Label("Построить программу", systemImage: "sparkles")
                    }
                    .buttonStyle(.gradientProminent)
                    .disabled(!validMaxes)
                    .opacity(validMaxes ? 1 : 0.5)
                    .appearIn(4)
                }
                .padding(.horizontal)
                .padding(.bottom, 28)
            }
            .scrollIndicators(.hidden)
            .screenBackground()
            .navigationTitle("Техасский метод")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(action: onBack) { Label("Назад", systemImage: "chevron.left") }
                        .buttonStyle(.pressable)
                }
            }
        }
    }

    private func value(_ text: String) -> Double {
        Double(text.replacingOccurrences(of: ",", with: ".")) ?? 100
    }

    private var validMaxes: Bool {
        [squat, bench, deadlift].allSatisfy { (Double($0.replacingOccurrences(of: ",", with: ".")) ?? 0) > 0 }
    }
}

struct UpperLowerSetupView: View {
    let onBack: () -> Void
    let onSave: (UpperLowerInput) -> Void
    @State private var squat = "100"
    @State private var bench = "100"
    @State private var deadlift = "130"

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    ScreenHeader(
                        eyebrow: "7 недель · 4 дня",
                        title: "Верх / Низ",
                        subtitle: "Введи протестированные 1ПМ — базовые веса и волна из 14 жимовых тренировок рассчитаются автоматически."
                    )
                    .padding(.top, 12)
                    .appearIn(0)

                    CardView {
                        Text("Твои 1ПМ").font(.title3.weight(.bold))
                        NumberField(title: "Приседания", text: $squat)
                        NumberField(title: "Жим лёжа", text: $bench)
                        NumberField(title: "Становая тяга", text: $deadlift)
                        Text("Рабочие веса округляются к 5 кг, как в исходной таблице.")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    .appearIn(1)

                    CardView {
                        HStack(spacing: 12) {
                            Image(systemName: "waveform.path.ecg")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundStyle(.white)
                                .frame(width: 42, height: 42)
                                .background(Theme.recordGradient, in: RoundedRectangle(cornerRadius: 13, style: .continuous))
                            VStack(alignment: .leading, spacing: 3) {
                                Text("Жим 14").font(.headline)
                                Text("Отдельный раздел с волной жима и своим прогрессом.")
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                        }
                        Divider().opacity(0.4)
                        Label("7 недель · 28 тренировок", systemImage: "calendar")
                            .font(.subheadline.weight(.semibold))
                        Text("Понедельник — тяжёлый верх, вторник — тяжёлый низ, четверг — объёмный верх, пятница — объёмный низ.")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    .appearIn(2)

                    Button {
                        onSave(UpperLowerInput(squat1RM: value(squat), bench1RM: value(bench), deadlift1RM: value(deadlift)))
                    } label: {
                        Label("Построить программу", systemImage: "sparkles")
                    }
                    .buttonStyle(.gradientProminent)
                    .disabled(!validMaxes)
                    .opacity(validMaxes ? 1 : 0.5)
                    .appearIn(3)
                }
                .padding(.horizontal)
                .padding(.bottom, 28)
            }
            .scrollIndicators(.hidden)
            .screenBackground()
            .navigationTitle("Верх / Низ")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(action: onBack) { Label("Назад", systemImage: "chevron.left") }
                        .buttonStyle(.pressable)
                }
            }
        }
    }

    private func value(_ text: String) -> Double { Double(text.replacingOccurrences(of: ",", with: ".")) ?? 0 }
    private var validMaxes: Bool { [squat, bench, deadlift].allSatisfy { value($0) > 0 } }
}

// MARK: - Поле веса с шагом

struct NumberField: View {
    let title: String
    @Binding var text: String
    var step: Double = 2.5

    private var current: Double { Double(text.replacingOccurrences(of: ",", with: ".")) ?? 0 }

    private func apply(_ delta: Double) {
        let next = max(0, current + delta)
        text = next == next.rounded() ? String(Int(next)) : String(format: "%.1f", next)
    }

    var body: some View {
        HStack(spacing: 10) {
            Text(title).font(.subheadline.weight(.medium))
            Spacer(minLength: 4)
            stepButton("minus", delta: -step)
            HStack(spacing: 3) {
                TextField("0", text: $text)
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.trailing)
                    .font(.system(size: 18, weight: .bold, design: .rounded))
                    .frame(width: 62)
                Text("кг").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            }
            .padding(.vertical, 8)
            .padding(.horizontal, 10)
            .background(Color.primary.opacity(0.05), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            stepButton("plus", delta: step)
        }
    }

    private func stepButton(_ symbol: String, delta: Double) -> some View {
        Button { apply(delta) } label: {
            Image(systemName: symbol)
                .font(.system(size: 12, weight: .black))
                .foregroundStyle(Theme.accent)
                .frame(width: 30, height: 30)
                .background(Theme.accent.opacity(0.14), in: Circle())
        }
        .buttonStyle(.pressable)
        .accessibilityLabel(delta > 0 ? "Увеличить \(title)" : "Уменьшить \(title)")
    }
}

struct ExercisePicker: View {
    let category: AdditionalExerciseCategory
    @Binding var value: String?

    var body: some View {
        Menu {
            Button("Не выбрано") { value = nil }
            ForEach(category.options, id: \.self) { option in
                Button(option) { value = option }
            }
        } label: {
            HStack {
                Text(category.rawValue).foregroundStyle(.primary)
                Spacer()
                Text(value ?? "Не выбрано")
                    .foregroundStyle(value == nil ? Color.secondary : Theme.accent)
                    .lineLimit(1)
                Image(systemName: "chevron.up.chevron.down")
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(.secondary)
            }
            .font(.subheadline)
            .padding(.vertical, 10)
            .padding(.horizontal, 12)
            .background(Color.primary.opacity(0.04), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
    }
}
