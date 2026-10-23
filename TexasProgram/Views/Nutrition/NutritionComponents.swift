import SwiftUI

// MARK: - Формат

enum NutritionFormat {
    static func kcal(_ value: Double) -> String { String(Int(value.rounded())) }

    /// Граммы: от десяти — целыми, меньше — с десятыми.
    static func grams(_ value: Double) -> String {
        value >= 10 ? String(Int(value.rounded())) : WeightFormat.decimal((value * 10).rounded() / 10)
    }

    static func macros(_ value: Nutrients) -> String {
        "Б \(grams(value.protein)) · Ж \(grams(value.fat)) · У \(grams(value.carbs))"
    }

    /// «+0,3 кг», «−0,2 кг», «0 кг».
    static func change(_ value: Double) -> String {
        let rounded = (value * 10).rounded() / 10
        let sign = rounded > 0 ? "+" : rounded < 0 ? "−" : ""
        return sign + WeightFormat.decimal(abs(rounded)) + " кг"
    }

    /// Момент записи для выбранного дня: сегодня — сейчас, прошлый день — полдень.
    static func moment(on day: Date, calendar: Calendar = .current) -> Date {
        if calendar.isDateInToday(day) { return Date() }
        return calendar.date(byAdding: .hour, value: 12, to: calendar.startOfDay(for: day)) ?? day
    }

    static func dayTitle(_ day: Date, calendar: Calendar = .current) -> String {
        if calendar.isDateInToday(day) { return "Сегодня" }
        if calendar.isDateInYesterday(day) { return "Вчера" }
        return RuDate.dayMonth(day, calendar: calendar)
    }
}

// MARK: - Карточка и подписи

extension View {
    /// Графитовая карточка питания — как карточки «Замеров веса».
    func nutritionCard(padding: CGFloat = 18) -> some View {
        self
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
}

/// Мелкая заглавная подпись над блоком.
struct NutritionCaption: View {
    let text: String
    var color: Color = Color.white.opacity(0.45)

    var body: some View {
        Text(text.uppercased())
            .font(.system(size: 12, weight: .bold))
            .tracking(1.2)
            .foregroundStyle(color)
    }
}

/// Основная кнопка: оранжевая с чёрным текстом; вторая — графитовая.
struct NutritionButton: View {
    let title: String
    var systemImage: String?
    var primary = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let systemImage { Image(systemName: systemImage).font(.system(size: 15, weight: .bold)) }
                Text(title).font(.system(size: 15, weight: .bold))
            }
            .foregroundStyle(primary ? Color.black : Color.white)
            .frame(maxWidth: .infinity)
            .frame(height: 50)
            .background(primary ? Theme.accent : Color(white: 0.16), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.pressable)
    }
}

// MARK: - Поле числа

/// Число в поле ввода: запятая и точка — одно и то же, пустое поле — ноль.
struct AmountField: View {
    let title: String
    @Binding var value: Double
    var unit = "г"
    var placeholder = "0"

    @State private var text = ""
    /// Текст поменяли мы сами вслед за значением — обратно его не пишем,
    /// иначе округлённое до десятых число затирало бы точное.
    @State private var syncing = false
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(title.uppercased())
                .font(.system(size: 10, weight: .bold))
                .tracking(0.8)
                .foregroundStyle(Color.white.opacity(0.45))
            HStack(spacing: 3) {
                TextField(placeholder, text: $text)
                    .keyboardType(.decimalPad)
                    .focused($focused)
                    .font(.system(size: 17, weight: .bold, design: Theme.fontDesign))
                    .monospacedDigit()
                    .foregroundStyle(.white)
                Text(unit)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Color.white.opacity(0.4))
            }
            .padding(.horizontal, 10)
            .frame(height: 44)
            .background(Color(white: focused ? 0.17 : 0.13), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(Theme.accent.opacity(focused ? 1 : 0), lineWidth: 1.5))
            .animation(.easeOut(duration: 0.15), value: focused)
        }
        .onAppear { text = Self.format(value) }
        .onChange(of: text) { _, newText in
            if syncing {
                syncing = false
                return
            }
            let parsed = Self.parse(newText)
            if parsed != value { value = parsed }
        }
        // Значение поменяли снаружи — например, после пересчёта ИИ или порции.
        .onChange(of: value) { _, newValue in
            let formatted = Self.format(newValue)
            guard Self.parse(text) != newValue, formatted != text else { return }
            syncing = true
            text = formatted
        }
    }

    static func parse(_ text: String) -> Double {
        let value = Double(text.replacingOccurrences(of: ",", with: ".").trimmingCharacters(in: .whitespaces)) ?? 0
        return value.isFinite ? max(0, value) : 0
    }

    static func format(_ value: Double) -> String {
        value == 0 ? "" : WeightFormat.decimal((value * 10).rounded() / 10)
    }
}
