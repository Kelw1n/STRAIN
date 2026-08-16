import SwiftUI

/// Какой подход записываем. Отдельный тип нужен `sheet(item:)`.
struct SetEntryTarget: Identifiable, Hashable {
    let week: Int
    let day: Int
    let exercise: ExercisePrescription
    let index: Int

    var id: String { "\(week)-\(day)|\(exercise.id)|\(index)" }
}

/// Ввод фактического результата подхода: сколько повторов и с каким весом.
///
/// План говорит, что делать, а это — что получилось. Поля заполняются
/// предписанными значениями, чтобы в обычном случае хватило одного нажатия.
struct SetEntrySheet: View {
    @Environment(\.dismiss) private var dismiss
    @Bindable var profile: ProgramProfile
    let target: SetEntryTarget

    @State private var repsText = ""
    @State private var weightText = ""
    @State private var loaded = false
    /// У цифровых клавиатур нет клавиши возврата — убрать их можно только кнопкой.
    @FocusState private var typing: Field?

    private enum Field: Hashable { case reps, weight }

    private var existing: SetEntry? {
        profile.setEntry(week: target.week, day: target.day, exercise: target.exercise, index: target.index)
    }

    /// Предписанный вес, если он задан числом.
    private var plannedWeight: Double? {
        guard case .kilograms(let value) = target.exercise.load else { return nil }
        return value
    }

    /// Предписанные повторы: из «5» берём 5, из «6 / 5 / 5 / 4 / 4» — нужный подход,
    /// из «8–12» — нижнюю границу. Диапазон вписать нельзя, поэтому берём разумное.
    private var plannedReps: Int? {
        let raw = target.exercise.reps
        let parts = raw.split(separator: "/").map { $0.trimmingCharacters(in: .whitespaces) }
        if parts.count > 1, target.index < parts.count, let value = Int(parts[target.index]) {
            return value
        }
        let digits = raw.split(whereSeparator: { !$0.isNumber })
        return digits.first.flatMap { Int($0) }
    }

    private var reps: Int? {
        guard let value = Int(repsText.trimmingCharacters(in: .whitespaces)), value > 0 else { return nil }
        return value
    }

    private var weight: Double? {
        let cleaned = weightText.replacingOccurrences(of: ",", with: ".").trimmingCharacters(in: .whitespaces)
        guard let value = Double(cleaned), value >= 0 else { return nil }
        return value
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text(target.exercise.name).font(.headline)
                    LabeledContent("По плану") {
                        Text(plannedText).foregroundStyle(.secondary)
                    }
                } header: {
                    Text("Подход \(target.index + 1)")
                }

                Section("Как получилось") {
                    TextField("Повторов", text: $repsText)
                        .keyboardType(.numberPad)
                        .focused($typing, equals: .reps)
                    TextField("Вес, кг", text: $weightText)
                        .keyboardType(.decimalPad)
                        .focused($typing, equals: .weight)
                }

                Section {
                    Button {
                        if let reps, let weight {
                            profile.recordSet(
                                week: target.week, day: target.day, exercise: target.exercise,
                                index: target.index, reps: reps, weight: weight
                            )
                        }
                        dismiss()
                    } label: {
                        Label("Записать", systemImage: "checkmark.circle.fill")
                    }
                    .disabled(reps == nil || weight == nil)

                    if existing != nil {
                        Button(role: .destructive) {
                            profile.clearSetEntry(
                                week: target.week, day: target.day,
                                exercise: target.exercise, index: target.index
                            )
                            dismiss()
                        } label: {
                            Label("Убрать запись", systemImage: "trash")
                        }
                    }
                } footer: {
                    Text("Записанные подходы попадают в историю упражнения и в график рабочих весов.")
                }
            }
            .navigationTitle("Результат подхода")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Отмена") { dismiss() }
                }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("Готово") { typing = nil }
                }
            }
            .onAppear {
                // Только при первом появлении: иначе набранное затёрлось бы
                // при каждом обновлении экрана.
                guard !loaded else { return }
                loaded = true
                repsText = existing.map { String($0.reps) } ?? plannedReps.map(String.init) ?? ""
                weightText = existing.map { WeightFormat.plain($0.weight) }
                    ?? plannedWeight.map(WeightFormat.plain) ?? ""
            }
        }
    }

    private var plannedText: String {
        let volume = target.exercise.sets > 0 ? "\(target.exercise.sets) × \(target.exercise.reps)" : target.exercise.reps
        return volume + " · " + target.exercise.load.displayText
    }
}
