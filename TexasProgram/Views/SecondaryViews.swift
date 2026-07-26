import SwiftUI
import SwiftData

private struct RPEItem: Identifiable {
    let id: String
    let label: String
    let detail: String
}

struct ProgressScreen: View {
    @Bindable var profile: ProgramProfile
    private var plan: WorkoutPlan { profile.workoutPlan }
    private var completed: Int { profile.completedDayCount }
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    CardView {
                        HStack(alignment: .firstTextBaseline) { Text("\(completed)").font(.system(size: 42, weight: .bold, design: .rounded)); Text("из \(profile.totalDays) дней").foregroundStyle(.secondary) }
                        ProgressView(value: Double(completed), total: Double(profile.totalDays)).tint(.teal)
                    }
                    CardView {
                        Text("Текущие \(profile.maximumLabel)").font(.title3.weight(.semibold))
                        PRRow(title: "Приседания", value: profile.squat5RM)
                        PRRow(title: "Жим лёжа", value: profile.bench5RM)
                        PRRow(title: "Становая тяга", value: profile.deadlift5RM)
                    }
                    CardView {
                        Text("Недели").font(.title3.weight(.semibold))
                        ForEach(plan.weeks) { week in
                            let done = week.days.filter { profile.isCompleted(week: week.number, day: $0.number) }.count
                            HStack { Text("Неделя \(week.number)"); Spacer(); Text("\(done)/\(week.days.count)").foregroundStyle(.secondary); ProgressView(value: Double(done), total: Double(week.days.count)).frame(width: 80).tint(done == week.days.count ? .green : .teal) }
                        }
                    }
                }.padding()
            }.background(Color(uiColor: .systemGroupedBackground)).navigationTitle("Прогресс")
        }
    }
}

struct PRRow: View { let title: String; let value: Double; var body: some View { HStack { Text(title); Spacer(); Text("\(value, specifier: "%.1f") кг").fontWeight(.semibold) } } }

struct InstructionsView: View {
    let programKind: TrainingProgramKind
    var body: some View {
        NavigationStack {
            List {
                Section("Как начать") { Text(programKind == .texas ? "Протестируй настоящий пятиповторный максимум в приседаниях, жиме лёжа и становой тяге. Введи именно проверенные значения — программа рассчитает рабочие веса и постепенное увеличение нагрузки." : "Протестируй 1ПМ в приседаниях, жиме лёжа и становой тяге. Программа рассчитает базовые веса с округлением к 5 кг. Для остальных упражнений подбирай вес по указанному RPE.") }
                if programKind == .texas { Section("Дополнительные упражнения") { Text("Дополнительные упражнения необязательны, но выбранное упражнение следует выполнять на протяжении всего цикла. Растянутый суперсет рук означает чередование бицепса и трицепса с полноценным отдыхом после каждого подхода.") } }
                Section("Отдых") { Text("Отдыхай до полного восстановления. Минимум — 3 минуты; при необходимости отдых может занимать 10 минут и больше.") }
                if programKind == .texas { Section("Пикирование") { Text("Пикирование — необязательная часть после основной 12-недельной программы. Используй его только если хочешь проверить 1ПМ или подготовиться к соревнованиям. Перед запуском внеси свежие 5ПМ.") } }
                if programKind == .upperLower { Section("Жим 14") { Text("В программу встроена волновая прогрессия из 14 жимовых тренировок. Негативные повторы выполняй только со страхующим и заранее оговорённой подачей штанги.") } }
                Section("RPE") {
                    Text("RPE — субъективная оценка оставшихся повторов в запасе. RPE 8 означает примерно два повтора в запасе, RPE 9 — один. Записывай ощущения и регулируй нагрузку, если восстановление отличается от обычного.")
                    ForEach([
                        RPEItem(id: "7", label: "RPE 7", detail: "3 повтора в запасе"),
                        RPEItem(id: "8", label: "RPE 8", detail: "2 повтора в запасе"),
                        RPEItem(id: "9", label: "RPE 9", detail: "1 повтор в запасе"),
                        RPEItem(id: "10", label: "RPE 10", detail: "предел")
                    ]) { item in HStack { Text(item.label).fontWeight(.semibold); Spacer(); Text(item.detail).foregroundStyle(.secondary) } }
                }
            }.navigationTitle("Инструкция")
        }
    }
}

struct SettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @Bindable var profile: ProgramProfile
    let onResetApplication: () -> Void
    @State private var showPeaking = false
    @State private var showReset = false
    var body: some View {
        NavigationStack {
            Form {
                Section(profile.maximumLabel) {
                    TextField("Приседания", value: $profile.squat5RM, format: .number).keyboardType(.decimalPad)
                    TextField("Жим лёжа", value: $profile.bench5RM, format: .number).keyboardType(.decimalPad)
                    TextField("Становая тяга", value: $profile.deadlift5RM, format: .number).keyboardType(.decimalPad)
                }
                if profile.programKind == .texas {
                    Section("Уровень") { Picker("Уровень", selection: $profile.level) { ForEach(TrainingLevel.allCases) { Text($0.rawValue).tag($0) } }.pickerStyle(.segmented) }
                    Section("Дополнительные упражнения") { ForEach(AdditionalExerciseCategory.allCases) { category in ExercisePicker(category: category, value: binding(for: category)) } }
                    Section("После 12 недель") { Button("Открыть модуль пикирования") { showPeaking = true }.disabled(profile.completedDayCount < 36); Text("Сначала отметь все 36 дней основной программы.").font(.caption).foregroundStyle(.secondary) }
                }
                Section("Иконка приложения") { AlternateIconPicker() }
                Section { Button("Сбросить приложение", role: .destructive) { showReset = true }; Text("Удалит максимумы, выбранную программу и все отметки, затем вернёт на стартовый выбор.").font(.caption).foregroundStyle(.secondary) }
            }.navigationTitle("Настройки").toolbar { ToolbarItem(placement: .confirmationAction) { Button("Готово") { dismiss() } } }
        }
        .sheet(isPresented: $showPeaking) { PeakingView(profile: profile) }
        .confirmationDialog("Полностью сбросить приложение?", isPresented: $showReset) { Button("Сбросить приложение", role: .destructive) { dismiss(); onResetApplication() }; Button("Отмена", role: .cancel) {} }
    }
    private func binding(for category: AdditionalExerciseCategory) -> Binding<String?> { Binding(get: { get(category) }, set: { set(category, $0) }) }
    private func get(_ category: AdditionalExerciseCategory) -> String? {
        switch category {
        case .pull: return profile.pull
        case .arms: return profile.arms
        case .core: return profile.core
        case .back: return profile.back
        case .press: return profile.press
        }
    }
    private func set(_ category: AdditionalExerciseCategory, _ value: String?) { switch category { case .pull: profile.pull = value; case .arms: profile.arms = value; case .core: profile.core = value; case .back: profile.back = value; case .press: profile.press = value } }
}

struct PeakingView: View {
    @Environment(\.dismiss) private var dismiss
    @Bindable var profile: ProgramProfile
    private var plan: WorkoutPlan { ProgramCalculator.generatePeaking(input: profile.peakingInput) }
    var body: some View {
        NavigationStack {
            ScrollView { VStack(alignment: .leading, spacing: 16) { Text("Пикирование").font(.title.bold()); Text("Четыре недели подготовки к тесту 1ПМ. Подтверди свежие 5ПМ: они сохранятся отдельно и не изменят основную программу.").foregroundStyle(.secondary); CardView { TextField("Приседания", value: $profile.peakSquat5RM, format: .number).keyboardType(.decimalPad); TextField("Жим лёжа", value: $profile.peakBench5RM, format: .number).keyboardType(.decimalPad); TextField("Становая тяга", value: $profile.peakDeadlift5RM, format: .number).keyboardType(.decimalPad); Button(profile.peakingActive ? "Пикирование активно" : "Запустить пикирование") { profile.peakSquat5RM = profile.peakSquat5RM ?? profile.squat5RM; profile.peakBench5RM = profile.peakBench5RM ?? profile.bench5RM; profile.peakDeadlift5RM = profile.peakDeadlift5RM ?? profile.deadlift5RM; profile.peakingActive = true }.buttonStyle(.borderedProminent) }; ForEach(plan.weeks) { week in CardView { Text("Неделя \(week.number)").font(.headline); ForEach(week.days) { day in Text("\(day.title): " + day.exercises.map { $0.name + " · " + $0.load.displayText }.joined(separator: ", ")).font(.subheadline) } } }; if profile.peakingActive { Button("Отменить пикирование", role: .destructive) { profile.peakingActive = false; profile.peakSquat5RM = nil; profile.peakBench5RM = nil; profile.peakDeadlift5RM = nil } } }.padding() }.background(Color(uiColor: .systemGroupedBackground)).navigationTitle("Пикирование").toolbar { ToolbarItem(placement: .confirmationAction) { Button("Готово") { dismiss() } } }
        }
    }
}
