import SwiftUI
import SwiftData

/// Черновик своего упражнения: то, что пользователь набирает в форме.
struct ExerciseDraft: Identifiable, Hashable {
    var id = UUID()
    /// Что заменяем из программы. `nil` — добавляем своё или редактируем добавленное.
    var target: ExercisePrescription?
    /// Существующая модификация плана, если редактируем уже созданное упражнение.
    var existingEdit: PlanEdit?
    var name: String = ""
    var sets: Int = 3
    var reps: String = "8–12"
    var weightText: String = ""
    var loadText: String = ""

    var trimmedName: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }

    /// Запятая как разделитель: на русской раскладке цифровой клавиатуры она под рукой.
    var kilograms: Double? {
        let cleaned = weightText.replacingOccurrences(of: ",", with: ".").trimmingCharacters(in: .whitespaces)
        guard !cleaned.isEmpty, let value = Double(cleaned), value > 0 else { return nil }
        return value
    }

    var load: String? {
        let cleaned = loadText.trimmingCharacters(in: .whitespacesAndNewlines)
        return cleaned.isEmpty ? nil : cleaned
    }
}

/// Настройка одного дня программы: заменить, переименовать, убрать или дописать своё упражнение.
struct DayCustomizeView: View {
    @Environment(\.dismiss) private var dismiss
    @Bindable var profile: ProgramProfile
    let week: Int
    let day: WorkoutDayPlan

    @State private var scope: PlanEditScope = .everyWeek
    @State private var draft: ExerciseDraft?
    @State private var ordering: [ExercisePrescription] = []

    private var generated: [ExercisePrescription] {
        profile.generatedExercises(week: week, day: day.number)
    }

    private var dayEdits: [PlanEdit] { profile.edits(week: week, day: day.number) }

    private var addedEdits: [PlanEdit] { dayEdits.filter { $0.kind == .add } }

    private func edit(for target: ExercisePrescription) -> PlanEdit? {
        dayEdits.first { $0.targetID == target.id }
    }

    /// Итоговый состав дня — то, что реально увидишь на тренировке.
    private var current: [ExercisePrescription] {
        profile.workoutPlan.weeks.first { $0.number == week }?
            .days.first { $0.number == day.number }?.exercises ?? []
    }

    var body: some View {
        NavigationStack {
            Form {
                scopeSection
                programSection
                addedSection
                orderSection
                if !dayEdits.isEmpty { resetSection }
            }
            .navigationTitle("Настройка упражнений")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Готово") { dismiss() }.font(.body.weight(.semibold))
                }
            }
            .onAppear { ordering = current }
            .onChange(of: current) { _, value in
                ordering = value
            }
            .navigationDestination(item: $draft) { value in
                ExerciseFormView(
                    draft: value,
                    scope: scope,
                    onSave: { saved in
                        apply(saved)
                        draft = nil
                    },
                    onDelete: {
                        deleteDraft(value)
                        draft = nil
                    }
                )
            }
        }
    }

    // MARK: - Секции

    private var scopeSection: some View {
        Section {
            Picker("Куда применить", selection: $scope) {
                ForEach(PlanEditScope.allCases) { Text($0.rawValue).tag($0) }
            }
            .pickerStyle(.segmented)
        } header: {
            Text("Применить изменения")
        } footer: {
            Text(scope.explanation)
        }
    }

    private var programSection: some View {
        Section {
            ForEach(generated) { exercise in
                let currentEdit = edit(for: exercise)
                Button {
                    // НАЖАТИЕ НА ЛЮБОЕ УПРАЖНЕНИЕ ОТКРЫВАЕТ ЕГО РЕДАКТИРОВАНИЕ
                    draft = ExerciseDraft(
                        target: exercise,
                        existingEdit: currentEdit,
                        name: currentEdit?.name ?? exercise.name,
                        sets: currentEdit?.sets ?? max(exercise.sets, 1),
                        reps: currentEdit?.reps ?? exercise.reps,
                        weightText: currentEdit?.kilograms.map(WeightFormat.plain) ?? weightText(of: exercise),
                        loadText: currentEdit?.loadText ?? ""
                    )
                } label: {
                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(currentEdit?.name ?? exercise.name)
                                .font(.body.weight(.medium))
                                .foregroundStyle(currentEdit?.kind == .hide ? .secondary : .primary)
                                .strikethrough(currentEdit?.kind == .hide)

                            if let currentEdit {
                                Text(currentEdit.summary + " · " + currentEdit.scope.rawValue.lowercased())
                                    .font(.caption)
                                    .foregroundStyle(Theme.warning)
                            } else {
                                Text(subtitle(of: exercise)).font(.caption).foregroundStyle(.secondary)
                            }
                        }

                        Spacer(minLength: 6)

                        Image(systemName: "slider.horizontal.2.square")
                            .font(.title3)
                            .foregroundStyle(Theme.accent)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                    Button(role: .destructive) {
                        profile.hideExercise(exercise, week: week, day: day.number, scope: scope)
                    } label: {
                        Label("Убрать", systemImage: "eye.slash")
                    }

                    if currentEdit != nil {
                        Button {
                            if let currentEdit { profile.removeEdit(currentEdit) }
                        } label: {
                            Label("Сброс", systemImage: "arrow.uturn.backward")
                        }
                        .tint(.blue)
                    }
                }
            }
        } header: {
            Text("Упражнения программы")
        } footer: {
            Text("Нажмите на любое упражнение, чтобы изменить его название, подходы, повторения, вес или убрать из дня.")
        }
    }

    private var addedSection: some View {
        Section {
            ForEach(addedEdits) { item in
                Button {
                    // ТЕПЕРЬ НАЖАТИЕ НА ДОБАВЛЕННОЕ УПРАЖНЕНИЕ ТОЖЕ ОТКРЫВАЕТ ЕГО РЕДАКТИРОВАНИЕ
                    draft = ExerciseDraft(
                        target: nil,
                        existingEdit: item,
                        name: item.name,
                        sets: max(item.sets, 1),
                        reps: item.reps,
                        weightText: item.kilograms.map(WeightFormat.plain) ?? "",
                        loadText: item.loadText ?? ""
                    )
                } label: {
                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(item.name)
                                .font(.body.weight(.medium))
                                .foregroundStyle(.primary)
                            Text(detail(of: item) + " · " + item.scope.rawValue.lowercased())
                                .font(.caption).foregroundStyle(.secondary)
                        }

                        Spacer(minLength: 6)

                        Image(systemName: "pencil.circle.fill")
                            .font(.title3)
                            .foregroundStyle(Theme.accent)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .swipeActions {
                    Button(role: .destructive) { profile.removeEdit(item) } label: {
                        Label("Удалить", systemImage: "trash")
                    }
                }
            }

            Button {
                draft = ExerciseDraft()
            } label: {
                Label("Добавить новое упражнение", systemImage: "plus.circle.fill")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(Theme.accent)
            }
        } header: {
            Text("Свои добавленные упражнения")
        } footer: {
            Text("Нажмите на созданное упражнение, чтобы отредактировать его или удалить.")
        }
    }

    private var orderSection: some View {
        Section {
            ForEach(ordering) { exercise in
                HStack(spacing: 10) {
                    Image(systemName: "line.3.horizontal")
                        .font(.footnote).foregroundStyle(.tertiary)
                    Text(exercise.name).lineLimit(1)
                }
            }
            .onMove { source, destination in
                ordering.move(fromOffsets: source, toOffset: destination)
                profile.setOrder(ordering.map(\.id), week: week, day: day.number, scope: scope)
            }
        } header: {
            HStack {
                Text("Порядок выполнения")
                Spacer()
                EditButton().font(.footnote)
            }
        } footer: {
            Text("Нажмите «Изменить» и перетащите за полоски, чтобы поменять порядок.")
        }
    }

    private var resetSection: some View {
        Section {
            Button("Вернуть стандартную программу дня", role: .destructive) {
                profile.removeEdits(week: week, day: day.number)
            }
        } footer: {
            Text("Снимет все замены, убранные упражнения и добавленные движения этого дня.")
        }
    }

    // MARK: - Логика

    private func apply(_ value: ExerciseDraft) {
        if let existing = value.existingEdit {
            // Если редактировали уже существующий edit: убираем старый и ставим новый с новым именем/весом
            profile.removeEdit(existing)
            if let target = value.target {
                profile.replaceExercise(
                    target,
                    name: value.trimmedName,
                    sets: value.sets,
                    reps: value.reps,
                    kilograms: value.kilograms,
                    loadText: value.load,
                    week: week,
                    day: day.number,
                    scope: scope
                )
            } else {
                profile.addExercise(
                    name: value.trimmedName,
                    sets: value.sets,
                    reps: value.reps,
                    kilograms: value.kilograms,
                    loadText: value.load,
                    week: week,
                    day: day.number,
                    scope: scope
                )
            }
        } else if let target = value.target {
            profile.replaceExercise(
                target,
                name: value.trimmedName,
                sets: value.sets,
                reps: value.reps,
                kilograms: value.kilograms,
                loadText: value.load,
                week: week,
                day: day.number,
                scope: scope
            )
        } else {
            profile.addExercise(
                name: value.trimmedName,
                sets: value.sets,
                reps: value.reps,
                kilograms: value.kilograms,
                loadText: value.load,
                week: week,
                day: day.number,
                scope: scope
            )
        }
    }

    private func deleteDraft(_ value: ExerciseDraft) {
        if let existing = value.existingEdit {
            profile.removeEdit(existing)
        } else if let target = value.target {
            profile.hideExercise(target, week: week, day: day.number, scope: scope)
        }
    }

    private func weightText(of exercise: ExercisePrescription) -> String {
        guard case .kilograms(let value) = exercise.load else { return "" }
        return WeightFormat.kilogramsPrecise(value).replacingOccurrences(of: " кг", with: "").replacingOccurrences(of: " lbs", with: "")
    }

    private func subtitle(of exercise: ExercisePrescription) -> String {
        let volume = exercise.sets > 0 ? "\(exercise.sets) × \(exercise.reps)" : exercise.reps
        return volume + " · " + exercise.load.displayText
    }

    private func detail(of edit: PlanEdit) -> String {
        let volume = edit.sets > 0 ? "\(edit.sets) × \(edit.reps)" : edit.reps
        return volume + " · " + edit.load.displayText
    }
}

// MARK: - Форма упражнения

private struct ExerciseFormView: View {
    @State var draft: ExerciseDraft
    let scope: PlanEditScope
    let onSave: (ExerciseDraft) -> Void
    let onDelete: (() -> Void)?

    @Environment(\.dismiss) private var dismiss

    private var isReplacing: Bool { draft.target != nil }

    var body: some View {
        Form {
            Section("Название и повторения") {
                TextField("Название упражнения", text: $draft.name)
                    .textInputAutocapitalization(.sentences)
                    .font(.body.weight(.semibold))

                Stepper("Подходов: \(draft.sets)", value: $draft.sets, in: 1...15)
                TextField("Повторения (например 8–12 или 5)", text: $draft.reps)
            }

            Section {
                TextField("Вес (например 80 или 102.5)", text: $draft.weightText)
                    .keyboardType(.decimalPad)
                TextField("Или подсказка: RPE 8, до отказа, разминка", text: $draft.loadText)
            } header: {
                Text("Рабочий вес / Нагрузка")
            } footer: {
                Text("Если указать вес числом, STRAIN рассчитает разминочные подходы и блины.")
            }

            Section {
                Button {
                    onSave(draft)
                    dismiss()
                } label: {
                    HStack {
                        Spacer()
                        Text("Сохранить изменения")
                            .font(.body.weight(.bold))
                            .foregroundStyle(.white)
                        Spacer()
                    }
                    .padding(.vertical, 4)
                }
                .listRowBackground(draft.trimmedName.isEmpty ? Color.gray.opacity(0.3) : Theme.accent)
                .disabled(draft.trimmedName.isEmpty)
            } footer: {
                Text(scope.explanation)
            }

            if isReplacing || draft.existingEdit != nil {
                Section {
                    Button(role: .destructive) {
                        onDelete?()
                        dismiss()
                    } label: {
                        HStack {
                            Spacer()
                            Label(isReplacing ? "Убрать это упражнение из дня" : "Удалить упражнение", systemImage: "trash.fill")
                                .font(.body.weight(.semibold))
                                .foregroundStyle(.red)
                            Spacer()
                        }
                    }
                }
            }
        }
        .navigationTitle(draft.name.isEmpty ? "Новое упражнение" : draft.name)
        .navigationBarTitleDisplayMode(.inline)
    }
}
