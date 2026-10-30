import Charts
import SwiftUI
import UIKit

/// Экран «Замеры веса» — на весь лист, в оформлении приложения: крупный
/// текущий вес, запись замера, график, сила к своему весу и все замеры списком.
/// Раньше в лист просто клали карточку, и она висела посередине серого поля.
struct BodyWeightScreen: View {
    @Environment(\.dismiss) private var dismiss
    @Bindable var profile: ProgramProfile

    @State private var input = ""
    @State private var isSyncingHealth = false
    @FocusState private var typing: Bool

    private var series: [BodyWeightEntry] { profile.bodyWeightSeries }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    hero
                    entryCard
                    if series.count > 1 { chartCard }
                    if profile.latestBodyWeight != nil { ratioCard }
                    if !series.isEmpty { historyCard }
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 32)
            }
            .scrollIndicators(.hidden)
            .scrollDismissesKeyboard(.interactively)
            .background(Color.canvas.ignoresSafeArea())
            .navigationTitle("Замеры веса")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Готово") { dismiss() }
                        .font(.body.weight(.bold))
                        .foregroundStyle(Theme.accent)
                }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("Готово") { typing = false }
                }
            }
        }
    }

    // MARK: Крупный вес

    private var hero: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("ТЕКУЩИЙ ВЕС")
                .font(.system(size: 12, weight: .bold))
                .tracking(1.2)
                .foregroundStyle(Color.ink.opacity(0.45))
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(profile.latestBodyWeight.map { WeightFormat.decimal($0) } ?? "—")
                    .font(.system(size: 54, weight: .heavy, design: Theme.fontDesign))
                    .monospacedDigit()
                    .foregroundStyle(.ink)
                    .contentTransition(.numericText())
                Text("кг")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(Color.ink.opacity(0.5))
                Spacer()
                if let change = change(days: 30) {
                    deltaChip(change, caption: "за 30 дней")
                }
            }
            Text(lastLine)
                .font(.system(size: 13))
                .foregroundStyle(Color.ink.opacity(0.5))
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }

    private var lastLine: String {
        guard let last = series.last else { return "Запиши первый замер — от него пойдёт график." }
        let calendar = Calendar.current
        if calendar.isDateInToday(last.date) { return "Замер сегодня" }
        if calendar.isDateInYesterday(last.date) { return "Последний замер вчера" }
        return "Последний замер \(RuDate.dayMonth(last.date))"
    }

    private func deltaChip(_ value: Double, caption: String) -> some View {
        VStack(alignment: .trailing, spacing: 2) {
            Text((value > 0 ? "+" : value < 0 ? "−" : "") + WeightFormat.decimal(abs(value)) + " кг")
                .font(.system(size: 15, weight: .bold, design: Theme.fontDesign))
                .foregroundStyle(value == 0 ? Color.ink.opacity(0.7) : Theme.accent)
            Text(caption)
                .font(.system(size: 11))
                .foregroundStyle(Color.ink.opacity(0.4))
        }
    }

    /// Изменение за последние дни: от замера на начало окна (или самого раннего в нём) до последнего.
    private func change(days: Int) -> Double? {
        guard let last = series.last, series.count > 1,
              let from = Calendar.current.date(byAdding: .day, value: -days, to: last.date) else { return nil }
        let base = series.last { $0.date <= from } ?? series.first { $0.date >= from }
        guard let base, base.date != last.date else { return nil }
        return last.weight - base.weight
    }

    // MARK: Запись

    private var entryCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Новый замер")
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(.ink)
            HStack(spacing: 10) {
                TextField("Сегодня, кг", text: $input)
                    .keyboardType(.decimalPad)
                    .focused($typing)
                    .font(.system(size: 18, weight: .bold, design: Theme.fontDesign))
                    .foregroundStyle(.ink)
                    .padding(.horizontal, 14)
                    .frame(height: 50)
                    .background(Color.tone(typing ? 0.17 : 0.13), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(Theme.accent.opacity(typing ? 1 : 0), lineWidth: 1.5))
                    .animation(.easeOut(duration: 0.15), value: typing)

                if HealthKitService.shared.isAvailable {
                    Button(action: syncHealth) {
                        Image(systemName: isSyncingHealth ? "arrow.triangle.2.circlepath" : "heart.fill")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(Color(red: 0.95, green: 0.35, blue: 0.45))
                            .frame(width: 50, height: 50)
                            .background(Color.tone(0.13), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }
                    .buttonStyle(.pressable)
                    .accessibilityLabel("Взять вес из «Здоровья»")
                }

                Button(action: record) {
                    Text("Записать")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(parsed == nil ? Color.ink.opacity(0.4) : .canvas)
                        .padding(.horizontal, 18)
                        .frame(height: 50)
                        .background(parsed == nil ? Color.tone(0.16) : Theme.accent, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .buttonStyle(.pressable)
                .disabled(parsed == nil)
            }
            Text("Второй замер за день заменяет первый. Лучше взвешиваться утром, натощак.")
                .font(.system(size: 12))
                .foregroundStyle(Color.ink.opacity(0.4))
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(18)
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    // MARK: График

    private var chartCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Динамика")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(.ink)
                Spacer()
                if let first = series.first, let last = series.last {
                    let total = last.weight - first.weight
                    Text((total > 0 ? "+" : total < 0 ? "−" : "") + WeightFormat.decimal(abs(total)) + " кг с первого замера")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Color.ink.opacity(0.5))
                }
            }
            Chart(series) { item in
                AreaMark(x: .value("Дата", item.date), y: .value("Вес", item.weight))
                    .foregroundStyle(LinearGradient(colors: [Theme.accent.opacity(0.3), .clear], startPoint: .top, endPoint: .bottom))
                    .interpolationMethod(.catmullRom)
                LineMark(x: .value("Дата", item.date), y: .value("Вес", item.weight))
                    .foregroundStyle(Theme.accent)
                    .lineStyle(StrokeStyle(lineWidth: 2.5, lineCap: .round))
                    .interpolationMethod(.catmullRom)
                if item.date == series.last?.date {
                    PointMark(x: .value("Дата", item.date), y: .value("Вес", item.weight))
                        .foregroundStyle(Theme.accent)
                        .symbolSize(60)
                }
            }
            .chartYScale(domain: .automatic(includesZero: false))
            .chartYAxis {
                AxisMarks(position: .leading) { value in
                    AxisGridLine().foregroundStyle(Color.ink.opacity(0.08))
                    AxisValueLabel {
                        if let weight = value.as(Double.self) {
                            Text(WeightFormat.plain(weight)).font(.caption2).foregroundStyle(Color.ink.opacity(0.45))
                        }
                    }
                }
            }
            .chartXAxis {
                AxisMarks(values: .automatic(desiredCount: 4)) { value in
                    AxisValueLabel {
                        if let date = value.as(Date.self) {
                            Text(RuDate.shortDayMonth(date)).font(.caption2).foregroundStyle(Color.ink.opacity(0.45))
                        }
                    }
                }
            }
            .frame(height: 170)
        }
        .padding(18)
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    // MARK: Сила к весу

    private var ratioCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Сила к своему весу")
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(.ink)
            HStack(spacing: 10) {
                ratio("Присед", profile.squat5RM)
                ratio("Жим", profile.bench5RM)
                ratio("Тяга", profile.deadlift5RM)
            }
            Text("Отношение \(profile.maximumLabel) к весу тела. Держится при похудении — значит, сила осталась, а лишнее ушло.")
                .font(.system(size: 12))
                .foregroundStyle(Color.ink.opacity(0.4))
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(18)
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private func ratio(_ title: String, _ maximum: Double) -> some View {
        VStack(spacing: 4) {
            Text(profile.relativeStrength(maximum).map { String(format: "%.2f", $0).replacingOccurrences(of: ".", with: ",") } ?? "—")
                .font(.system(size: 22, weight: .heavy, design: Theme.fontDesign))
                .monospacedDigit()
                .foregroundStyle(.ink)
            Text(title)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Color.ink.opacity(0.5))
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .background(Color.tone(0.12), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    // MARK: Список замеров

    private var historyCard: some View {
        let items = Array(series.reversed())
        return VStack(alignment: .leading, spacing: 4) {
            Text("Все замеры")
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(.ink)
                .padding(.bottom, 6)
            ForEach(Array(items.enumerated()), id: \.element.date) { index, entry in
                let previous = index + 1 < items.count ? items[index + 1] : nil
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(RuDate.dayMonth(entry.date))
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(.ink)
                        if let previous {
                            let diff = entry.weight - previous.weight
                            Text(diff == 0 ? "без изменений" : (diff > 0 ? "+" : "−") + WeightFormat.decimal(abs(diff)) + " кг")
                                .font(.system(size: 12))
                                .foregroundStyle(Color.ink.opacity(0.45))
                        }
                    }
                    Spacer()
                    Text(WeightFormat.kilogramsPrecise(entry.weight))
                        .font(.system(size: 16, weight: .bold, design: Theme.fontDesign))
                        .monospacedDigit()
                        .foregroundStyle(.ink)
                    Menu {
                        Button(role: .destructive) {
                            withAnimation(Motion.snappy) { profile.removeBodyWeight(entry) }
                        } label: {
                            Label("Удалить замер", systemImage: "trash")
                        }
                    } label: {
                        Image(systemName: "ellipsis")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(Color.ink.opacity(0.4))
                            .frame(width: 34, height: 34)
                            .contentShape(Rectangle())
                    }
                }
                .padding(.vertical, 8)
                if index < items.count - 1 {
                    Divider().overlay(Color.ink.opacity(0.06))
                }
            }
        }
        .padding(18)
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    // MARK: Действия

    /// Запятая и точка в поле веса значат одно и то же — на клавиатуре они разные.
    private var parsed: Double? {
        let text = input.replacingOccurrences(of: ",", with: ".").trimmingCharacters(in: .whitespaces)
        guard let value = Double(text), value > 20, value < 400 else { return nil }
        return value
    }

    private func record() {
        guard let parsed else { return }
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        withAnimation(Motion.card) {
            profile.recordBodyWeight(parsed)
            input = ""
            typing = false
        }
    }

    private func syncHealth() {
        Task {
            isSyncingHealth = true
            _ = await HealthKitService.shared.requestAuthorization()
            if let weight = await HealthKitService.shared.fetchLatestBodyWeight() {
                withAnimation(Motion.snappy) { input = WeightFormat.plain((weight * 10).rounded() / 10) }
            }
            isSyncingHealth = false
        }
    }
}
