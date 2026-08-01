import SwiftUI

/// Настройка фулбади: стаж и пятиповторные максимумы.
///
/// Стаж спрашиваем первым: от него зависит вся схема, а не только числа.
struct FullBodySetupView: View {
    var onBack: () -> Void = {}
    let onSave: (ProgramInput, FullBodyLevel) -> Void

    @State private var level: FullBodyLevel = .aboutYear
    @State private var squat = "100"
    @State private var bench = "100"
    @State private var deadlift = "100"
    @State private var back: String?
    @State private var press: String?
    @State private var arms: String?
    @State private var core: String?

    private func number(_ text: String) -> Double? {
        let cleaned = text.replacingOccurrences(of: ",", with: ".").trimmingCharacters(in: .whitespaces)
        guard let value = Double(cleaned), value > 0 else { return nil }
        return value
    }

    private var isReady: Bool {
        number(squat) != nil && number(bench) != nil && number(deadlift) != nil
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    ForEach(FullBodyLevel.allCases) { item in
                        Button {
                            level = item
                        } label: {
                            HStack(alignment: .top, spacing: 12) {
                                Image(systemName: level == item ? "largecircle.fill.circle" : "circle")
                                    .foregroundStyle(level == item ? AnyShapeStyle(Theme.accent) : AnyShapeStyle(.secondary))
                                    .padding(.top, 2)
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(item.rawValue).foregroundStyle(.primary)
                                    Text(item.subtitle).font(.caption).foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                } header: {
                    Text("Сколько ходишь в зал")
                } footer: {
                    Text(level.explanation)
                }

                Section {
                    TextField("Приседания", text: $squat).keyboardType(.decimalPad)
                    TextField("Жим лёжа", text: $bench).keyboardType(.decimalPad)
                    TextField("Становая тяга", text: $deadlift).keyboardType(.decimalPad)
                } header: {
                    Text("Пятиповторный максимум")
                } footer: {
                    Text("Вес, который поднимаешь на пять повторений с запасом в один. Протестируй перед началом — от этих чисел считается вся программа.")
                }

                Section {
                    ExercisePicker(category: .back, value: $back)
                    ExercisePicker(category: .press, value: $press)
                    ExercisePicker(category: .arms, value: $arms)
                    ExercisePicker(category: .core, value: $core)
                } header: {
                    Text("Подсобка")
                } footer: {
                    Text("Тяга, руки и кор идут в каждой тренировке. Не выберешь — подставятся стандартные.")
                }

                Section {
                    Button {
                        guard let squatValue = number(squat),
                              let benchValue = number(bench),
                              let deadliftValue = number(deadlift) else { return }
                        onSave(
                            ProgramInput(
                                squat5RM: squatValue,
                                bench5RM: benchValue,
                                deadlift5RM: deadliftValue,
                                level: .beginner,
                                pull: nil,
                                arms: arms,
                                core: core,
                                back: back,
                                press: press
                            ),
                            level
                        )
                    } label: {
                        Label("Начать программу", systemImage: "play.circle.fill")
                    }
                    .disabled(!isReady)
                }
            }
            .navigationTitle("Фулбади")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button("Назад", action: onBack) }
            }
        }
    }
}
