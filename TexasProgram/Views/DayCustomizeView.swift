import SwiftUI
import SwiftData

/// Черновик своего упражнения: то, что пользователь набирает в форме.
struct ExerciseDraft: Identifiable, Hashable {
    var id = UUID()
    /// Что заменяем. `nil` — добавляем новое упражнение.
    var target: ExercisePrescription?
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

/// Настройка одного дня программы: заменить, убрать или дописать своё упражнение.
struct DayCustomizeView: View {
    @Environment(\.dismiss) private var dismiss
    @Bindable var profile: ProgramProfile
    let week: Int
    let day: WorkoutDayPlan

    @State private var scope: PlanEditScope = .everyWeek
    @State private var draft: ExerciseDraft?

    private var generated: [ExercisePrescription] {
        profile.generatedExercises(week: week, day: day.number)
    }

    private var dayEdits: [PlanEdit] { profile.edits(week: week, day: day.number) }

    private var addedEdits: [PlanEdit] { dayEdits.filter { $0.kind == .add } }

    private func edit(for target: ExercisePrescription) -> PlanEdit? {
        dayEdits.first { $0.targetID == target.id }
    }

    var body: some View {
        NavigationStack {
            Form {
                scopeSection
                programSection
                addedSection
                if !dayEdits.isEmpty { resetSection }
            }
            .navigationTitle("Свои упражнения")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Готово") { dismiss() }.font(.body.weight(.semibold))
                }
            }
            .navigationDestination(item: $draft) { value in
                ExerciseFormView(draft: value, scope: scope) { saved in
                    apply(saved)
                    draft = nil
                }
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
            Text("Куда применить")
        } footer: {
            Text(scope.explanation)
        }
    }

    private var programSection: some View {
        Section {
            ForEach(generated) { exercise in
                row(for: exercise)
            }
        } header: {
            Text("Упражнения программы")
        } footer: {
            Text("Приседания, жим и становая кормят график прогресса. Если заменить или убрать их, в графике за эти дни будет пусто.")
        }
    }

    @ViewBuilder
    private func row(for exercise: ExercisePrescription) -> some View {
        let current = edit(for: exercise)
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                Text(exercise.name)
                    .foregroundStyle(current?.kind == .hide ? .secondary : .primary)
                    .strikethrough(current?.kind == .hide)
                Spacer(minLength: 6)
                Menu {
                    Button {
                        draft = ExerciseDraft(
                            target: exercise,
                            name: exercise.name,
                            sets: max(exercise.sets, 1),
                            reps: exercise.reps,
                            weightText: weightText(of: exercise)
                        )
                    } label: {
                        Label("Заменить", systemImage: "arrow.triangle.2.circlepath")
                    }
                    Button(role: .destructive) {
                        profile.hideExercise(exercise, week: week, day: day.number, scope: scope)
                    } label: {
                        Label("Убрать из дня", systemImage: "eye.slash")
                    }
                    if let current {
                        Divider()
                        Button {
                            profile.removeEdit(current)
                        } label: {
                            Label("Вернуть как было", systemImage: "arrow.uturn.backward")
                        }
                    }
                } label: {
                    Image(systemName: "ellipsis.circle").font(.body.weight(.semibold))
                }
            }
            if let current {
                Text(current.summary + " · " + current.scope.rawValue.lowercased())
                    .font(.caption)
                    .foregroundStyle(Theme.warning)
            } else {
                Text(subtitle(of: exercise)).font(.caption).foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 2)
    }

    private var addedSection: some View {
        Section {
            ForEach(addedEdits) { item in
                VStack(alignment: .leading, spacing: 4) {
                    Text(item.name)
                    Text(detail(of: item) + " · " + item.scope.rawValue.lowercased())
                        .font(.caption).foregroundStyle(.secondary)
                }
                .swipeActions {
                    Button(role: .destructive) { profile.removeEdit(item) } label: {
                        Label("Удалить", systemImage: "trash")
                    }
                }
            }
            Button {
                draft = ExerciseDraft()
            } label: {
                Label("Добавить своё упражнение", systemImage: "plus.circle.fill")
            }
        } header: {
            Text("Свои упражнения")
        } footer: {
            Text(addedEdits.isEmpty
                 ? "Упражнение встанет в конец дня и будет отмечаться по подходам наравне с остальными."
                 : "Смахни влево, чтобы убрать.")
        }
    }

    private var resetSection: some View {
        Section {
            Button(role: .destructive) {
                profile.removeEdits(week: week, day: day.number)
            } label: {
                Label("Вернуть день как было", systemImage: "arrow.uturn.backward.circle")
            }
        } footer: {
            Text("Уберёт все правки этого дня и вернёт упражнения программы.")
        }
    }

    // MARK: - Действия

    private func apply(_ value: ExerciseDraft) {
        guard !value.trimmedName.isEmpty else { return }
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
    }

    private func weightText(of exercise: ExercisePrescription) -> String {
        guard case .kilograms(let value) = exercise.load else { return "" }
        return WeightFormat.kilogramsPrecise(value).replacingOccurrences(of: " кг", with: "")
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

    @Environment(\.dismiss) private var dismiss

    private var isReplacing: Bool { draft.target != nil }

    var body: some View {
        Form {
            Section("Упражнение") {
                TextField("Название", text: $draft.name)
                    .textInputAutocapitalization(.sentences)
                Stepper("Подходов: \(draft.sets)", value: $draft.sets, in: 1...12)
                TextField("Повторения, например 8–12", text: $draft.reps)
            }

            Section {
                TextField("Вес, кг", text: $draft.weightText)
                    .keyboardType(.decimalPad)
                TextField("Или подпись: RPE 8, до отказа", text: $draft.loadText)
            } header: {
                Text("Нагрузка")
            } footer: {
                Text("Если указать вес, приложение посчитает блины и разминку. Оставишь пусто — покажет подпись.")
            }

            Section {
                Button {
                    onSave(draft)
                    dismiss()
                } label: {
                    Label(isReplacing ? "Заменить" : "Добавить", systemImage: "checkmark.circle.fill")
                }
                .disabled(draft.trimmedName.isEmpty)
            } footer: {
                Text(scope.explanation)
            }
        }
        .navigationTitle(isReplacing ? "Замена" : "Своё упражнение")
        .navigationBarTitleDisplayMode(.inline)
    }
}
