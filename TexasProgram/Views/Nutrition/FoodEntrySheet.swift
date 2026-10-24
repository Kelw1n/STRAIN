import SwiftUI
import UIKit

/// Запись еды вручную или правка уже записанной. Калории считаются по Б/Ж/У
/// сами; знаешь калории с упаковки — впиши их, тогда они главнее.
struct FoodEntrySheet: View {
    @Environment(\.dismiss) private var dismiss

    let original: FoodEntry?
    let day: Date
    var recent: [FoodEntry] = []
    let onSave: (FoodEntry) -> Void
    var onDelete: (() -> Void)?

    @State private var name = ""
    @State private var grams = 0.0
    @State private var kcal = 0.0
    @State private var protein = 0.0
    @State private var fat = 0.0
    @State private var carbs = 0.0

    private var byMacros: Double { Nutrients.kcal(protein: protein, fat: fat, carbs: carbs) }
    private var effectiveKcal: Double { kcal > 0 ? kcal : byMacros }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    nameCard
                    numbersCard
                    if original == nil && !recent.isEmpty { recentCard }
                    if let onDelete {
                        Button(role: .destructive) {
                            onDelete()
                            dismiss()
                        } label: {
                            Text("Удалить запись")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundStyle(Theme.record)
                                .frame(maxWidth: .infinity)
                                .frame(height: 48)
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 32)
            }
            .scrollIndicators(.hidden)
            .scrollDismissesKeyboard(.interactively)
            .background(Color.black.ignoresSafeArea())
            .navigationTitle(original == nil ? "Добавить еду" : "Запись")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Отмена") { dismiss() }
                        .foregroundStyle(Color.white.opacity(0.7))
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Сохранить", action: save)
                        .font(.body.weight(.bold))
                        .foregroundStyle(effectiveKcal > 0 ? Theme.accent : Color.white.opacity(0.3))
                        .disabled(effectiveKcal <= 0)
                }
            }
        }
        .preferredColorScheme(.dark)
        .onAppear(perform: fill)
    }

    private var nameCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            NutritionCaption(text: "Что съел")
            TextField("Например, гречка с курицей", text: $name)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 14)
                .frame(height: 50)
                .background(Color(white: 0.13), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .nutritionCard()
    }

    private var numbersCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                AmountField(title: "Порция", value: $grams)
                AmountField(title: "Калории", value: $kcal, unit: "ккал", placeholder: byMacros > 0 ? NutritionFormat.kcal(byMacros) : "0")
            }
            HStack(spacing: 10) {
                AmountField(title: "Белки", value: $protein)
                AmountField(title: "Жиры", value: $fat)
                AmountField(title: "Углеводы", value: $carbs)
            }
            Text("Не вписал калории — они посчитаются по Б/Ж/У: 4 ккал в грамме белка и углеводов, 9 — в грамме жира. Порцию можно не писать.")
                .font(.system(size: 12))
                .foregroundStyle(Color.white.opacity(0.4))
                .fixedSize(horizontal: false, vertical: true)
        }
        .nutritionCard()
    }

    private var recentCard: some View {
        VStack(alignment: .leading, spacing: 6) {
            NutritionCaption(text: "Недавние")
                .padding(.bottom, 4)
            ForEach(recent) { entry in
                Button { use(entry) } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(entry.name)
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundStyle(.white)
                                .lineLimit(1)
                            Text(NutritionFormat.macros(entry.nutrients))
                                .font(.system(size: 12))
                                .foregroundStyle(Color.white.opacity(0.45))
                        }
                        Spacer()
                        Text("\(NutritionFormat.kcal(entry.nutrients.kcal)) ккал")
                            .font(.system(size: 14, weight: .bold, design: Theme.fontDesign))
                            .foregroundStyle(Color.white.opacity(0.8))
                    }
                    .padding(.vertical, 8)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.pressable)
            }
        }
        .nutritionCard()
    }

    private func fill() {
        guard let original else { return }
        use(original)
    }

    private func use(_ entry: FoodEntry) {
        name = entry.name
        grams = entry.grams ?? 0
        kcal = entry.nutrients.kcal
        protein = entry.nutrients.protein
        fat = entry.nutrients.fat
        carbs = entry.nutrients.carbs
        UISelectionFeedbackGenerator().selectionChanged()
    }

    private func save() {
        guard effectiveKcal > 0 else { return }
        let title = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let entry = FoodEntry(
            id: original?.id ?? UUID(),
            date: original?.date ?? NutritionFormat.moment(on: day),
            name: title.isEmpty ? "Без названия" : title,
            grams: grams > 0 ? grams : nil,
            nutrients: Nutrients(kcal: effectiveKcal.rounded(), protein: protein, fat: fat, carbs: carbs),
            fromPhoto: original?.fromPhoto ?? false
        )
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        onSave(entry)
        dismiss()
    }
}
