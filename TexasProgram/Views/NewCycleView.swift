import SwiftUI

/// Новый заход по программе с новыми максимумами.
///
/// Числа вводятся заново, а не пересчитываются из теста 1ПМ: техасская программа
/// считается от пятиповторного максимума, и в самой таблице сказано его протестировать.
/// Прикидка по формуле дала бы расхождение в несколько процентов и разное по движениям.
struct NewCycleView: View {
    @Environment(\.dismiss) private var dismiss
    @Bindable var profile: ProgramProfile

    @State private var squatText = ""
    @State private var benchText = ""
    @State private var deadliftText = ""
    @State private var loaded = false
    @State private var confirming = false

    private func number(_ text: String) -> Double? {
        let cleaned = text.replacingOccurrences(of: ",", with: ".").trimmingCharacters(in: .whitespaces)
        guard let value = Double(cleaned), value > 0 else { return nil }
        return value
    }

    private var isReady: Bool {
        number(squatText) != nil && number(benchText) != nil && number(deadliftText) != nil
    }

    /// Подсказка от пикирования: тест 1ПМ — это не 5ПМ, но ориентир.
    private var hint: String? {
        guard profile.programKind.usesFiveRepMax else { return nil }
        return "Пятиповторный максимум и результат теста 1ПМ — разные числа. Если пикирование пройдено, ориентировочный 5ПМ около 86–89 % от одноповторного, но это прикидка: проверь на первой тяжёлой тренировке."
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("Цикл \(profile.cycleNumber) закрыт. Отметки начнутся заново, а история и графики останутся сквозными — будет видно, как ты рос от цикла к циклу.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                Section {
                    TextField("Приседания", text: $squatText).keyboardType(.decimalPad)
                    TextField("Жим лёжа", text: $benchText).keyboardType(.decimalPad)
                    TextField("Становая тяга", text: $deadliftText).keyboardType(.decimalPad)
                } header: {
                    Text("Новый \(profile.maximumLabel)")
                } footer: {
                    if let hint { Text(hint) }
                }

                Section {
                    Button {
                        confirming = true
                    } label: {
                        Label("Начать цикл \(profile.cycleNumber + 1)", systemImage: "arrow.triangle.2.circlepath")
                    }
                    .disabled(!isReady)
                } footer: {
                    Text("Пикирование выключится, отметки жимовой волны обнулятся. Прошлый цикл никуда не денется: его записи остаются в истории.")
                }
            }
            .navigationTitle("Новый цикл")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Отмена") { dismiss() } }
            }
            .confirmationDialog("Начать новый цикл?", isPresented: $confirming, titleVisibility: .visible) {
                Button("Начать") {
                    guard let squat = number(squatText),
                          let bench = number(benchText),
                          let deadlift = number(deadliftText) else { return }
                    profile.startNewCycle(squat: squat, bench: bench, deadlift: deadlift)
                    dismiss()
                }
                Button("Отмена", role: .cancel) {}
            } message: {
                Text("Отметки текущего цикла спрячутся, программа начнётся с первой недели.")
            }
            .onAppear {
                guard !loaded else { return }
                loaded = true
                squatText = WeightFormat.plain(profile.squat5RM)
                benchText = WeightFormat.plain(profile.bench5RM)
                deadliftText = WeightFormat.plain(profile.deadlift5RM)
            }
        }
    }
}
