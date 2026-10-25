import SwiftUI
import UIKit

/// Метод Лимара простыми словами: почему формулы не работают и как найти
/// свою норму по записям. Отдельно — что приложение добавило от себя.
struct NutritionGuideView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    section("Почему формулы не работают", lines: [
                        "«Белок — вес × 2, жиры — вес × 0,8, калории — вес × 35, остальное — углеводы». Такие формулы гуляют по интернету, но у каждого своя активность и свой обмен веществ.",
                        "У двух людей с одинаковым весом расход за день может отличаться на сотни калорий: работа, шаги, обмен веществ. Формула этого не видит — а твои записи видят."
                    ])
                    steps
                    section("Как приложение это проверяет", lines: [
                        "Вес сравнивается средним за неделю, а не по дням: за день он может прыгать на килограмм — вода, соль, еда в желудке.",
                        "Неделя считается, если записаны еда и вес хотя бы 5 дней из 7. Иначе средние врут.",
                        "Вес «стоит», если средний сдвинулся меньше чем на 0,2 кг за неделю.",
                        "Если за неделю наблюдения вес заметно уехал, приложение скажет об этом и предложит понаблюдать ещё неделю.",
                        "На сушке предупредит, если жиры опустятся ниже 0,6 г на кило веса.",
                        "Норма меняется только по твоему нажатию — приложение лишь подсказывает."
                    ])
                    section("Фото еды", lines: [
                        "ИИ Google раскладывает тарелку на продукты и оценивает граммы на глаз — ошибка бывает 20–30%. Этикетку он читает точнее.",
                        "Для метода точность не главное: если каждый день считать одинаково, среднее за неделю всё равно найдёт поддержку."
                    ])
                    Text("Метод — Лимар, @freedom_limar. Приложение просто ведёт записи и считает за тебя.")
                        .font(.system(size: 12))
                        .foregroundStyle(Color.white.opacity(0.4))
                        .padding(.horizontal, 4)
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 32)
            }
            .scrollIndicators(.hidden)
            .background(Color.black.ignoresSafeArea())
            .navigationTitle("Как это работает")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Готово") { dismiss() }
                        .font(.body.weight(.bold))
                        .foregroundStyle(Theme.accent)
                }
            }
        }
        .preferredColorScheme(.dark)
    }

    private var steps: some View {
        VStack(alignment: .leading, spacing: 14) {
            NutritionCaption(text: "Метод Лимара", color: Theme.accent)
            step(1, "Неделю ничего не меняй", "Ешь как обычно — ел пиццу по вторникам, ешь и дальше. Каждый день записывай, что съел, и вес утром натощак.")
            step(2, "Смотри среднее", "Средние калории за неделю при весе, который стоит на месте, — это твоя поддержка.")
            step(3, "Набор: +30 г углеводов", "Каждую неделю добавляй к норме 30 г углеводов и смотри, как меняется вес. Пошёл вверх — держи норму.")
            step(4, "Сушка: минус жиры", "Каждую неделю убирай 13 г жиров — это те же 120 ккал — и смотри на вес. Пошёл вниз — держи норму.")
        }
        .nutritionCard()
    }

    private func step(_ number: Int, _ title: String, _ text: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Text("\(number)")
                .font(.system(size: 15, weight: .heavy, design: Theme.fontDesign))
                .foregroundStyle(.black)
                .frame(width: 28, height: 28)
                .background(Theme.accent, in: Circle())
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(.white)
                Text(text)
                    .font(.system(size: 14))
                    .foregroundStyle(Color.white.opacity(0.65))
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func section(_ title: String, lines: [String]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            NutritionCaption(text: title)
            ForEach(lines, id: \.self) { line in
                HStack(alignment: .top, spacing: 10) {
                    Circle()
                        .fill(Theme.accent)
                        .frame(width: 5, height: 5)
                        .padding(.top, 8)
                    Text(line)
                        .font(.system(size: 14))
                        .foregroundStyle(Color.white.opacity(0.75))
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .nutritionCard()
    }
}

/// Свой ключ Google AI: где взять, проверить и сохранить. Хранится в связке
/// ключей телефона и уходит только в Google.
struct GeminiKeySheet: View {
    @Environment(\.dismiss) private var dismiss
    /// Ключ один на всё: и для фото еды, и для ИИ-тренера.
    var title = "Ключ для фото еды"
    var intro = "Фото разбирает ИИ Google Gemini по твоему ключу. Ключ бесплатный и хранится только на этом телефоне."

    @State private var key = ""
    @State private var saved = GeminiKeychain.load() != nil
    @State private var checking = false
    @State private var message: String?
    @State private var success = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    VStack(alignment: .leading, spacing: 12) {
                        NutritionCaption(text: title, color: Theme.accent)
                        Text(intro)
                            .font(.system(size: 14))
                            .foregroundStyle(Color.white.opacity(0.75))
                            .fixedSize(horizontal: false, vertical: true)
                        instruction(1, "Открой Google AI Studio и войди в Google-аккаунт.")
                        instruction(2, "Нажми «Create API key» и скопируй ключ.")
                        instruction(3, "Вставь его ниже и нажми «Проверить и сохранить».")
                        if let url = URL(string: "https://aistudio.google.com/apikey") {
                            Link(destination: url) {
                                Label("Открыть Google AI Studio", systemImage: "arrow.up.right.square")
                                    .font(.system(size: 15, weight: .semibold))
                                    .foregroundStyle(Theme.accent)
                            }
                        }
                    }
                    .nutritionCard()

                    VStack(alignment: .leading, spacing: 12) {
                        NutritionCaption(text: saved ? "Ключ сохранён" : "Твой ключ")
                        SecureField(saved ? "Вставь новый, чтобы заменить" : "AIza…", text: $key)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .font(.system(size: 16, weight: .semibold, design: .monospaced))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 14)
                            .frame(height: 50)
                            .background(Color(white: 0.13), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        if let message {
                            Text(message)
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(success ? Theme.success : Theme.warning)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        NutritionButton(title: checking ? "Проверяю…" : "Проверить и сохранить", systemImage: "checkmark.seal.fill") {
                            Task { await check() }
                        }
                        .disabled(checking || trimmed.isEmpty)
                        .opacity(trimmed.isEmpty ? 0.5 : 1)
                        if saved {
                            Button(role: .destructive) {
                                GeminiKeychain.save(nil)
                                saved = false
                                success = false
                                message = "Ключ удалён с телефона."
                            } label: {
                                Text("Удалить ключ")
                                    .font(.system(size: 15, weight: .semibold))
                                    .foregroundStyle(Theme.record)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 44)
                            }
                        }
                        Text("Если Google пишет, что страна не поддерживается, — включи VPN.")
                            .font(.system(size: 12))
                            .foregroundStyle(Color.white.opacity(0.4))
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .nutritionCard()
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 32)
            }
            .scrollIndicators(.hidden)
            .scrollDismissesKeyboard(.interactively)
            .background(Color.black.ignoresSafeArea())
            .navigationTitle("Ключ Google")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Готово") { dismiss() }
                        .font(.body.weight(.bold))
                        .foregroundStyle(Theme.accent)
                }
            }
        }
        .preferredColorScheme(.dark)
    }

    private var trimmed: String { key.trimmingCharacters(in: .whitespacesAndNewlines) }

    private func instruction(_ number: Int, _ text: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Text("\(number).")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Theme.accent)
            Text(text)
                .font(.system(size: 14))
                .foregroundStyle(Color.white.opacity(0.75))
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    @MainActor
    private func check() async {
        let candidate = trimmed
        guard !candidate.isEmpty else { return }
        checking = true
        defer { checking = false }
        do {
            try await FoodRecognizer.checkKey(candidate)
            GeminiKeychain.save(candidate)
            saved = true
            success = true
            key = ""
            message = "Ключ работает и сохранён. Можно фотографировать еду."
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        } catch {
            success = false
            message = (error as? FoodRecognizer.Failure)?.errorDescription ?? error.localizedDescription
            UINotificationFeedbackGenerator().notificationOccurred(.error)
        }
    }
}
