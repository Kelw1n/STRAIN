import SwiftUI
import UIKit

/// Фото еды → продукты с граммами и Б/Ж/У. До добавления в дневник всё можно
/// поправить руками или словами: «там 200 г, а не 150».
struct FoodPhotoSheet: View {
    @Environment(\.dismiss) private var dismiss

    let image: UIImage
    let onAdd: ([RecognizedFood]) -> Void

    @State private var title = ""
    @State private var items: [RecognizedFood] = []
    @State private var confidence = ""
    @State private var comment = ""
    @State private var working = false
    @State private var failure: String?
    @State private var needsKey = false
    @State private var note = ""
    @State private var showingKey = false

    private var total: Nutrients { items.reduce(.zero) { $0 + $1.nutrients } }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                        .frame(height: 220)
                        .frame(maxWidth: .infinity)
                        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                        .overlay(alignment: .bottomLeading) {
                            if working { workingBadge.padding(12) }
                        }

                    if let failure { failureCard(failure) }
                    if !items.isEmpty {
                        summaryCard
                        ForEach($items) { $item in
                            RecognizedFoodEditor(item: $item) {
                                withAnimation(Motion.snappy) { items.removeAll { $0.id == item.id } }
                            }
                        }
                        correctionCard
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 32)
                .animation(Motion.card, value: items.count)
                .animation(Motion.card, value: failure)
            }
            .scrollIndicators(.hidden)
            .scrollDismissesKeyboard(.interactively)
            .background(Color.canvas.ignoresSafeArea())
            .navigationTitle(title.isEmpty ? "Фото еды" : title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Отмена") { dismiss() }
                        .foregroundStyle(Color.ink.opacity(0.7))
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Добавить") {
                        UINotificationFeedbackGenerator().notificationOccurred(.success)
                        onAdd(items)
                        dismiss()
                    }
                    .font(.body.weight(.bold))
                    .foregroundStyle(canAdd ? Theme.accent : Color.ink.opacity(0.3))
                    .disabled(!canAdd)
                }
            }
        }
        .task { if items.isEmpty && !working { await recognize() } }
        .sheet(isPresented: $showingKey, onDismiss: {
            if needsKey, GeminiKeychain.load() != nil { Task { await recognize() } }
        }) {
            GeminiKeySheet()
        }
    }

    private var canAdd: Bool { !working && !items.isEmpty && total.kcal > 0 }

    private var workingBadge: some View {
        HStack(spacing: 8) {
            ProgressView().tint(.ink)
            Text("ИИ разбирает фото…")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(.ink)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        // Плашка лежит поверх снятого фото — то же стекло, что у кнопок поверх экрана.
        .chatGlass(Capsule())
    }

    private func failureCard(_ message: String) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(message)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(.ink)
                .fixedSize(horizontal: false, vertical: true)
            if needsKey {
                NutritionButton(title: "Вставить ключ Google", systemImage: "key.fill") { showingKey = true }
            } else {
                NutritionButton(title: "Попробовать ещё раз", systemImage: "arrow.clockwise") {
                    Task { await recognize() }
                }
            }
        }
        .nutritionCard()
    }

    private var summaryCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                NutritionCaption(text: "Всего на фото")
                Spacer()
                Text("точность: \(confidence)")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(confidence == "низкая" ? Theme.warning : Color.ink.opacity(0.45))
            }
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(NutritionFormat.kcal(total.kcal))
                    .font(.system(size: 40, weight: .heavy, design: Theme.fontDesign))
                    .monospacedDigit()
                    .foregroundStyle(.ink)
                    .contentTransition(.numericText())
                Text("ккал")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(Color.ink.opacity(0.5))
            }
            Text(NutritionFormat.macros(total))
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Color.ink.opacity(0.75))
            if !comment.isEmpty {
                Text(comment)
                    .font(.system(size: 12))
                    .foregroundStyle(Color.ink.opacity(0.45))
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .nutritionCard()
    }

    private var correctionCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            NutritionCaption(text: "Поправить словами")
            HStack(spacing: 10) {
                TextField("Например: там 200 г риса, курица без кожи", text: $note, axis: .vertical)
                    .lineLimit(1...4)
                    .font(.system(size: 15))
                    .foregroundStyle(.ink)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 13)
                    .background(Color.tone(0.13), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                Button {
                    Task { await correct() }
                } label: {
                    Image(systemName: "arrow.up")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(sendable ? Color.canvas : Color.ink.opacity(0.4))
                        .frame(width: 48, height: 48)
                        .background(sendable ? Theme.accent : Color.tone(0.16), in: Circle())
                }
                .buttonStyle(.pressable)
                .disabled(!sendable)
                .accessibilityLabel("Пересчитать")
            }
            Text("ИИ оценивает порцию на глаз и может ошибиться на 20–30%. Для метода это не страшно: главное — считать одинаково каждый день.")
                .font(.system(size: 12))
                .foregroundStyle(Color.ink.opacity(0.4))
                .fixedSize(horizontal: false, vertical: true)
        }
        .nutritionCard()
    }

    private var sendable: Bool { !working && !note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

    // MARK: Запросы

    @MainActor
    private func recognize() async {
        guard let key = GeminiKeychain.load() else {
            needsKey = true
            failure = FoodRecognizer.Failure.noKey.errorDescription
            return
        }
        needsKey = false
        failure = nil
        working = true
        defer { working = false }
        do {
            apply(try await FoodRecognizer.recognize(image, key: key))
        } catch {
            show(error)
        }
    }

    @MainActor
    private func correct() async {
        guard let key = GeminiKeychain.load() else {
            needsKey = true
            failure = FoodRecognizer.Failure.noKey.errorDescription
            return
        }
        let current = RecognizedMeal(title: title, items: items, confidence: confidence, comment: comment)
        let text = note
        failure = nil
        working = true
        defer { working = false }
        do {
            apply(try await FoodRecognizer.correct(current, note: text, key: key))
            note = ""
        } catch {
            show(error)
        }
    }

    private func apply(_ meal: RecognizedMeal) {
        withAnimation(Motion.card) {
            title = meal.title
            items = meal.items
            confidence = meal.confidence
            comment = meal.comment
        }
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }

    private func show(_ error: Error) {
        let failure = error as? FoodRecognizer.Failure
        needsKey = failure == .badKey || failure == .noKey
        self.failure = failure?.errorDescription ?? error.localizedDescription
        UINotificationFeedbackGenerator().notificationOccurred(.error)
    }
}

/// Один продукт с фото: название, порция и Б/Ж/У. Меняешь порцию —
/// калории и Б/Ж/У пересчитываются в той же пропорции.
private struct RecognizedFoodEditor: View {
    @Binding var item: RecognizedFood
    let onRemove: () -> Void

    /// От чего считать пропорцию: последняя порция, которую правили руками.
    @State private var baseGrams = 0.0
    @State private var base = Nutrients.zero

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                TextField("Продукт", text: $item.name)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(.ink)
                Button(action: onRemove) {
                    Image(systemName: "trash")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Color.ink.opacity(0.4))
                        .frame(width: 32, height: 32)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel("Убрать продукт")
            }
            HStack(spacing: 8) {
                AmountField(title: "Порция", value: gramsBinding)
                AmountField(title: "Калории", value: macro(\.kcal), unit: "ккал")
            }
            HStack(spacing: 8) {
                AmountField(title: "Белки", value: macro(\.protein))
                AmountField(title: "Жиры", value: macro(\.fat))
                AmountField(title: "Углеводы", value: macro(\.carbs))
            }
        }
        .nutritionCard(padding: 16)
        .onAppear {
            baseGrams = item.grams ?? 0
            base = item.nutrients
        }
    }

    private var gramsBinding: Binding<Double> {
        Binding(
            get: { item.grams ?? 0 },
            set: { grams in
                item.grams = grams > 0 ? grams : nil
                guard baseGrams > 0, grams > 0 else { return }
                item.nutrients = base.scaled(by: grams / baseGrams)
            }
        )
    }

    /// Правка калорий или Б/Ж/У руками — новая точка отсчёта для пропорции.
    private func macro(_ path: WritableKeyPath<Nutrients, Double>) -> Binding<Double> {
        Binding(
            get: { item.nutrients[keyPath: path] },
            set: { value in
                guard item.nutrients[keyPath: path] != value else { return }
                item.nutrients[keyPath: path] = value
                base = item.nutrients
                baseGrams = item.grams ?? 0
            }
        )
    }
}
