import SwiftUI

/// Настройка «Эвтаназии худобы»: три движения, два теста на каждое и уровень.
///
/// У этой программы вход не такой, как у остальных: она считается не от общих
/// максимумов, а от пары «один повторный максимум + время на КПШ» по каждому
/// движению отдельно.
struct EuthanasiaSetupView: View {
    var onBack: () -> Void = {}
    let onSave: (EuthanasiaInput) -> Void

    @State private var level: EuthanasiaLevel = .medium
    @State private var chosen: [EuthanasiaPattern: String] = [:]
    @State private var maxima: [EuthanasiaPattern: String] = [:]
    @State private var minutes: [EuthanasiaPattern: String] = [:]

    private func number(_ text: String?) -> Double? {
        let cleaned = (text ?? "").replacingOccurrences(of: ",", with: ".").trimmingCharacters(in: .whitespaces)
        guard let value = Double(cleaned), value > 0 else { return nil }
        return value
    }

    private func movement(_ pattern: EuthanasiaPattern) -> EuthanasiaMovement? {
        guard let max = number(maxima[pattern]), let time = number(minutes[pattern]) else { return nil }
        return EuthanasiaMovement(
            exercise: chosen[pattern] ?? pattern.options[0],
            oneRepMax: max,
            testMinutes: time
        )
    }

    private var isReady: Bool {
        EuthanasiaPattern.allCases.allSatisfy { movement($0) != nil }
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("Уровень", selection: $level) {
                        ForEach(EuthanasiaLevel.allCases) { Text($0.rawValue).tag($0) }
                    }
                    .pickerStyle(.segmented)
                } header: {
                    Text("Уровень сложности")
                } footer: {
                    Text(level.subtitle + ". Отличается только шагом уплотнения: веса и повторы одинаковые.")
                }

                ForEach(EuthanasiaPattern.allCases) { pattern in
                    Section {
                        Picker("Упражнение", selection: binding(chosen: pattern, fallback: pattern.options[0])) {
                            ForEach(pattern.options, id: \.self) { Text($0).tag($0) }
                        }
                        TextField("Тест 1ПМ, кг", text: binding(max: pattern))
                            .keyboardType(.decimalPad)
                        TextField("Тест \(pattern.totalReps) КПШ, минут", text: binding(minutes: pattern))
                            .keyboardType(.decimalPad)
                    } header: {
                        Text(pattern.rawValue)
                    } footer: {
                        Text("Сначала максимум на один раз, потом — за сколько минут пройдёшь \(pattern.totalReps) повторений с 75 % от него, не подходя к отказу. Ориентир: \(pattern.testHint).")
                    }
                }

                Section {
                    Button {
                        guard let pull = movement(.pull),
                              let squat = movement(.squat),
                              let press = movement(.press) else { return }
                        onSave(EuthanasiaInput(pull: pull, squat: squat, press: press, level: level))
                    } label: {
                        Label("Начать программу", systemImage: "play.circle.fill")
                    }
                    .disabled(!isReady)
                } footer: {
                    Text("Первая неделя цикла — тестовая: приложение поставит оба теста в план, чтобы числа можно было уточнить прямо на тренировке.")
                }
            }
            .navigationTitle("Эвтаназия худобы")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button("Назад", action: onBack) }
            }
        }
    }

    /// Три движения заполняются одинаково, поэтому состояния держим словарями,
    /// а привязки делаем по одной на поле — общий помощник со сравнением
    /// словарей ошибался бы, пока они оба пустые.
    private func binding(chosen pattern: EuthanasiaPattern, fallback: String) -> Binding<String> {
        Binding(get: { chosen[pattern] ?? fallback }, set: { chosen[pattern] = $0 })
    }

    private func binding(max pattern: EuthanasiaPattern) -> Binding<String> {
        Binding(get: { maxima[pattern] ?? "" }, set: { maxima[pattern] = $0 })
    }

    private func binding(minutes pattern: EuthanasiaPattern) -> Binding<String> {
        Binding(get: { minutes[pattern] ?? "" }, set: { minutes[pattern] = $0 })
    }
}
