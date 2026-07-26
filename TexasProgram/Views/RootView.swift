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
            } else {
                ProgramOnboardingView { profile in
                    modelContext.insert(profile)
                    hasSeenSetup = true
                }
            }
        }
        .tint(.teal)
    }
}

struct ProgramOnboardingView: View {
    let onSave: (ProgramProfile) -> Void
    @State private var selectedProgram: TrainingProgramKind?

    var body: some View {
        Group {
            switch selectedProgram {
            case .texas:
                SetupView(onBack: { selectedProgram = nil }) { onSave(ProgramProfile(input: $0)) }
            case .upperLower:
                UpperLowerSetupView(onBack: { selectedProgram = nil }) { onSave(ProgramProfile(upperLowerInput: $0)) }
            case nil:
                ProgramSelectionView(selection: $selectedProgram)
            }
        }
    }
}

struct ProgramSelectionView: View {
    @Binding var selection: TrainingProgramKind?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    VStack(alignment: .leading, spacing: 8) {
                        Image(systemName: "figure.strengthtraining.traditional")
                            .font(.system(size: 42)).foregroundStyle(.teal)
                        Text("Выбери программу")
                            .font(.system(size: 34, weight: .bold, design: .rounded))
                        Text("После выбора введи свои максимумы. Вернуться сюда можно полным сбросом в настройках.")
                            .foregroundStyle(.secondary)
                    }.padding(.vertical, 20)
                    ForEach(TrainingProgramKind.allCases) { kind in
                        Button { withAnimation(.snappy) { selection = kind } } label: {
                            CardView {
                                HStack(alignment: .top) {
                                    VStack(alignment: .leading, spacing: 8) {
                                        Text(kind.rawValue).font(.title2.weight(.semibold)).foregroundStyle(.primary)
                                        Text(kind.subtitle).font(.subheadline).foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.right.circle.fill").font(.title2).foregroundStyle(.teal)
                                }
                            }
                        }.buttonStyle(.plain)
                    }
                }.padding()
            }.background(Color(uiColor: .systemGroupedBackground))
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
                VStack(alignment: .leading, spacing: 24) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Техасская программа").font(.system(size: 34, weight: .bold, design: .rounded))
                        Text("Введи реальные протестированные 5ПМ — приложение рассчитает 12 недель автоматически.")
                            .foregroundStyle(.secondary)
                    }
                    .padding(.top, 24)
                    CardView {
                        Text("Твои 5ПМ").font(.title3.weight(.semibold))
                        NumberField(title: "Приседания", text: $squat)
                        NumberField(title: "Жим лёжа", text: $bench)
                        NumberField(title: "Становая тяга", text: $deadlift)
                        Text("Шаг округления: 2,5 кг").font(.caption).foregroundStyle(.secondary)
                    }
                    CardView {
                        Text("Уровень пикирования").font(.title3.weight(.semibold))
                        Picker("Уровень", selection: $level) {
                            ForEach(TrainingLevel.allCases) { Text($0.rawValue).tag($0) }
                        }
                        .pickerStyle(.segmented)
                        Text("Для первого прохождения рекомендуется «Начальный».")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    CardView {
                        HStack {
                            Text("Дополнительные упражнения").font(.title3.weight(.semibold))
                            Spacer()
                            Button(showAllOptions ? "Скрыть" : "Выбрать") { withAnimation { showAllOptions.toggle() } }
                        }
                        if showAllOptions {
                            ForEach(AdditionalExerciseCategory.allCases) { category in
                                ExercisePicker(category: category, value: Binding(get: { selection[category] }, set: { selection[category] = $0 }))
                            }
                        } else {
                            Text("Необязательные упражнения можно добавить сейчас или позже в настройках.")
                                .font(.subheadline).foregroundStyle(.secondary)
                        }
                    }
                    Button {
                        let input = ProgramInput(squat5RM: Double(squat.replacingOccurrences(of: ",", with: ".")) ?? 100, bench5RM: Double(bench.replacingOccurrences(of: ",", with: ".")) ?? 100, deadlift5RM: Double(deadlift.replacingOccurrences(of: ",", with: ".")) ?? 100, level: level, pull: selection[.pull], arms: selection[.arms], core: selection[.core], back: selection[.back], press: selection[.press])
                        onSave(input)
                    } label: {
                        Text("Построить программу").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                    .disabled(!validMaxes)
                }
                .padding(.horizontal)
                .padding(.bottom, 24)
            }
            .background(Color(uiColor: .systemGroupedBackground))
            .navigationTitle("Техасский метод")
            .toolbar { ToolbarItem(placement: .topBarLeading) { Button("Назад", action: onBack) } }
        }
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
                VStack(alignment: .leading, spacing: 24) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Верх / Низ").font(.system(size: 34, weight: .bold, design: .rounded))
                        Text("4 тренировки в неделю. Введи протестированные 1ПМ — базовые веса и 14 жимовых тренировок рассчитаются автоматически.").foregroundStyle(.secondary)
                    }.padding(.top, 24)
                    CardView {
                        Text("Твои 1ПМ").font(.title3.weight(.semibold))
                        NumberField(title: "Приседания", text: $squat)
                        NumberField(title: "Жим лёжа", text: $bench)
                        NumberField(title: "Становая тяга", text: $deadlift)
                        Text("Рабочие веса округляются к 5 кг, как в исходной таблице.").font(.caption).foregroundStyle(.secondary)
                    }
                    CardView {
                        Label("7 недель · 28 тренировок", systemImage: "calendar")
                        Text("Понедельник — тяжёлый верх, вторник — тяжёлый низ, четверг — объёмный верх, пятница — объёмный низ.").font(.subheadline).foregroundStyle(.secondary)
                    }
                    Button {
                        onSave(UpperLowerInput(squat1RM: value(squat), bench1RM: value(bench), deadlift1RM: value(deadlift)))
                    } label: { Text("Построить программу").frame(maxWidth: .infinity) }
                    .buttonStyle(.borderedProminent).controlSize(.large).disabled(!validMaxes)
                }.padding(.horizontal).padding(.bottom, 24)
            }.background(Color(uiColor: .systemGroupedBackground))
                .navigationTitle("Верх / Низ")
                .toolbar { ToolbarItem(placement: .topBarLeading) { Button("Назад", action: onBack) } }
        }
    }

    private func value(_ text: String) -> Double { Double(text.replacingOccurrences(of: ",", with: ".")) ?? 0 }
    private var validMaxes: Bool { [squat, bench, deadlift].allSatisfy { value($0) > 0 } }
}

struct NumberField: View {
    let title: String
    @Binding var text: String
    var body: some View {
        HStack {
            Text(title)
            Spacer()
            TextField("кг", text: $text).keyboardType(.decimalPad).multilineTextAlignment(.trailing).frame(width: 100)
            Text("кг").foregroundStyle(.secondary)
        }
    }
}

struct ExercisePicker: View {
    let category: AdditionalExerciseCategory
    @Binding var value: String?
    var body: some View {
        Menu {
            Button("Не выбрано") { value = nil }
            ForEach(category.options, id: \.self) { option in Button(option) { value = option } }
        } label: {
            HStack { Text(category.rawValue); Spacer(); Text(value ?? "Не выбрано").foregroundStyle(.secondary).lineLimit(1) }
        }
    }
}

struct CardView<Content: View>: View {
    let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) { content }
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(.background, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .shadow(color: .black.opacity(0.05), radius: 16, y: 6)
    }
}
