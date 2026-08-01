import SwiftUI

/// Сборка своей программы: схема разбивки, число недель и упражнения по дням.
struct CustomProgramBuilderView: View {
    var onBack: () -> Void = {}
    let onSave: (CustomProgram) -> Void

    @State private var program = CustomProgram.skeleton(template: .fullBody, name: "Своя программа", weeks: 8)
    @State private var editing: CustomExerciseDraft?

    var body: some View {
        NavigationStack {
            Form {
                Section("Название") {
                    TextField("Как назвать", text: $program.name)
                }

                Section {
                    Picker("Схема", selection: $program.template) {
                        ForEach(SplitTemplate.allCases) { Text($0.rawValue).tag($0) }
                    }
                    Stepper("Недель: \(program.weeks)", value: $program.weeks, in: 1...24)
                } header: {
                    Text("Разбивка")
                } footer: {
                    Text(program.template.explanation)
                }

                ForEach($program.days) { $day in
                    Section {
                        TextField("Название дня", text: $day.title)
                        ForEach(day.exercises) { exercise in
                            Button {
                                editing = CustomExerciseDraft(dayID: day.id, exercise: exercise)
                            } label: {
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(exercise.name).foregroundStyle(.primary)
                                    Text(exercise.detail).font(.caption).foregroundStyle(.secondary)
                                }
                            }
                        }
                        .onDelete { offsets in
                            day.exercises.remove(atOffsets: offsets)
                        }
                        .onMove { source, destination in
                            day.exercises.move(fromOffsets: source, toOffset: destination)
                        }
                        Button {
                            editing = CustomExerciseDraft(dayID: day.id, exercise: nil)
                        } label: {
                            Label("Добавить упражнение", systemImage: "plus.circle.fill")
                        }
                    } header: {
                        HStack {
                            Text("День \(day.number)")
                            Spacer()
                            if !day.exercises.isEmpty { EditButton().font(.footnote) }
                        }
                    }
                }

                Section {
                    Button {
                        onSave(program)
                    } label: {
                        Label("Запустить программу", systemImage: "play.circle.fill")
                    }
                    .disabled(!program.isRunnable)
                } footer: {
                    Text(program.isRunnable
                         ? "Дни лягут на календарь по настройкам расписания. Программу можно будет править из «Своих упражнений»."
                         : "Чтобы запустить, добавь хотя бы одно упражнение в каждый день.")
                }
            }
            .navigationTitle("Своя программа")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Назад", action: onBack)
                }
            }
            // Смена схемы пересобирает дни: у неё своё число и свои названия.
            // Уже вписанные упражнения при этом теряются, поэтому меняем каркас
            // только когда он действительно другой.
            .onChange(of: program.template) { _, template in
                guard program.days.map(\.title) != template.dayTitles else { return }
                program = CustomProgram.skeleton(template: template, name: program.name, weeks: program.weeks)
            }
            .sheet(item: $editing) { draft in
                CustomExerciseForm(draft: draft) { dayID, exercise in
                    guard let index = program.days.firstIndex(where: { $0.id == dayID }) else { return }
                    if let position = program.days[index].exercises.firstIndex(where: { $0.id == exercise.id }) {
                        program.days[index].exercises[position] = exercise
                    } else {
                        program.days[index].exercises.append(exercise)
                    }
                } onDelete: { dayID, exerciseID in
                    guard let index = program.days.firstIndex(where: { $0.id == dayID }) else { return }
                    program.days[index].exercises.removeAll { $0.id == exerciseID }
                }
            }
        }
    }
}

/// Что редактируем: упражнение конкретного дня или новое.
struct CustomExerciseDraft: Identifiable, Hashable {
    let dayID: String
    let exercise: CustomExercise?

    var id: String { dayID + "|" + (exercise?.id ?? "new") }
}

private struct CustomExerciseForm: View {
    @Environment(\.dismiss) private var dismiss
    let draft: CustomExerciseDraft
    let onSave: (String, CustomExercise) -> Void
    let onDelete: (String, String) -> Void

    @State private var name = ""
    @State private var sets = 3
    @State private var reps = "5"
    @State private var weightText = ""
    @State private var loadText = ""
    @State private var incrementText = ""
    @State private var loaded = false

    private var trimmedName: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }

    private func number(_ text: String) -> Double? {
        let cleaned = text.replacingOccurrences(of: ",", with: ".").trimmingCharacters(in: .whitespaces)
        guard !cleaned.isEmpty, let value = Double(cleaned) else { return nil }
        return value
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Упражнение") {
                    TextField("Название", text: $name)
                    Stepper("Подходов: \(sets)", value: $sets, in: 1...12)
                    TextField("Повторения, например 5 или 8–12", text: $reps)
                }

                Section {
                    TextField("Вес, кг", text: $weightText).keyboardType(.decimalPad)
                    TextField("Или подпись: RPE 8, свой вес", text: $loadText)
                    TextField("Прибавка за неделю, кг", text: $incrementText).keyboardType(.decimalPad)
                } header: {
                    Text("Нагрузка")
                } footer: {
                    Text("Прибавка работает только вместе с весом: каждую следующую неделю к нему добавится указанное и округлится до блина.")
                }

                Section {
                    Button {
                        onSave(draft.dayID, CustomExercise(
                            id: draft.exercise?.id ?? "own-" + UUID().uuidString,
                            name: trimmedName,
                            sets: sets,
                            reps: reps,
                            kilograms: number(weightText),
                            loadText: loadText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : loadText,
                            weeklyIncrement: number(incrementText) ?? 0
                        ))
                        dismiss()
                    } label: {
                        Label(draft.exercise == nil ? "Добавить" : "Сохранить", systemImage: "checkmark.circle.fill")
                    }
                    .disabled(trimmedName.isEmpty)

                    if let existing = draft.exercise {
                        Button(role: .destructive) {
                            onDelete(draft.dayID, existing.id)
                            dismiss()
                        } label: {
                            Label("Убрать из дня", systemImage: "trash")
                        }
                    }
                }
            }
            .navigationTitle(draft.exercise == nil ? "Новое упражнение" : "Упражнение")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Отмена") { dismiss() } }
            }
            .onAppear {
                guard !loaded else { return }
                loaded = true
                guard let exercise = draft.exercise else { return }
                name = exercise.name
                sets = exercise.sets
                reps = exercise.reps
                weightText = exercise.kilograms.map(WeightFormat.plain) ?? ""
                loadText = exercise.loadText ?? ""
                incrementText = exercise.weeklyIncrement == 0 ? "" : WeightFormat.plain(exercise.weeklyIncrement)
            }
        }
    }
}
