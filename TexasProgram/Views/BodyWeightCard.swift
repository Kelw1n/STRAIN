import Charts
import SwiftUI

/// Вес тела и относительная сила.
///
/// Нужно ради второго: на сушке штанга стоит на месте, и по одному графику весов
/// кажется, что прогресса нет. Отношение максимума к своему весу в этот момент
/// как раз растёт.
struct BodyWeightCard: View {
    @Bindable var profile: ProgramProfile

    @State private var input = ""
    @FocusState private var typing: Bool

    private var series: [BodyWeightEntry] { profile.bodyWeightSeries }

    /// Насколько изменился вес с первого замера.
    private var delta: Double? {
        guard let first = series.first?.weight, let last = series.last?.weight, series.count > 1 else { return nil }
        return last - first
    }

    var body: some View {
        CardView {
            HStack(alignment: .firstTextBaseline) {
                Text("Вес тела").font(.title3.weight(.bold))
                Spacer(minLength: 8)
                if let latest = profile.latestBodyWeight {
                    Text(WeightFormat.kilogramsPrecise(latest))
                        .font(.subheadline.weight(.bold).monospacedDigit())
                        .foregroundStyle(Theme.accentGradient)
                }
            }

            HStack(spacing: 10) {
                TextField("Сегодня, кг", text: $input)
                    .keyboardType(.decimalPad)
                    .focused($typing)
                    .font(.footnote)
                    .padding(.vertical, 9)
                    .padding(.horizontal, 12)
                    .background(Color.primary.opacity(0.06), in: RoundedRectangle(cornerRadius: 11, style: .continuous))
                Button("Записать") { record() }
                    .font(.footnote.weight(.bold))
                    .foregroundStyle(.white)
                    .padding(.vertical, 10)
                    .padding(.horizontal, 16)
                    .background(Theme.accentGradient.opacity(parsed == nil ? 0.35 : 1),
                                in: RoundedRectangle(cornerRadius: 11, style: .continuous))
                    .buttonStyle(.plain)
                    .disabled(parsed == nil)
            }
            .toolbar {
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("Готово") { typing = false }
                }
            }

            if series.count > 1 {
                Chart(series) { item in
                    LineMark(
                        x: .value("Дата", item.date),
                        y: .value("Вес", item.weight)
                    )
                    .foregroundStyle(Theme.accentGradient)
                    .interpolationMethod(.catmullRom)
                    AreaMark(
                        x: .value("Дата", item.date),
                        y: .value("Вес", item.weight)
                    )
                    .foregroundStyle(LinearGradient(colors: [Theme.accent.opacity(0.28), .clear],
                                                    startPoint: .top, endPoint: .bottom))
                    .interpolationMethod(.catmullRom)
                }
                .chartYScale(domain: .automatic(includesZero: false))
                .chartYAxis {
                    AxisMarks(position: .leading) { value in
                        AxisGridLine().foregroundStyle(Color.primary.opacity(0.08))
                        AxisValueLabel {
                            if let weight = value.as(Double.self) {
                                Text(WeightFormat.plain(weight)).font(.caption2)
                            }
                        }
                    }
                }
                .frame(height: 130)

                if let delta {
                    Text(delta > 0
                         ? "+\(WeightFormat.plain(delta)) кг с первого замера"
                         : "\(WeightFormat.plain(delta)) кг с первого замера")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }

            if profile.latestBodyWeight != nil {
                Divider().overlay(Color.primary.opacity(0.08))
                Text("К СВОЕМУ ВЕСУ")
                    .font(.caption2.weight(.bold)).tracking(1.2)
                    .foregroundStyle(.secondary)
                HStack(spacing: 16) {
                    ratio("Присед", profile.squat5RM)
                    ratio("Жим", profile.bench5RM)
                    ratio("Тяга", profile.deadlift5RM)
                }
                Text("Отношение \(profile.maximumLabel) к весу тела. Держится при похудении — сила осталась, лишнее ушло.")
                    .font(.caption2).foregroundStyle(.tertiary)
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                Text("Запиши вес — рядом появится отношение максимумов к нему.")
                    .font(.caption).foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func ratio(_ title: String, _ maximum: Double) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(profile.relativeStrength(maximum).map { String(format: "%.2f", $0) } ?? "—")
                .font(.subheadline.weight(.bold).monospacedDigit())
            Text(title).font(.caption2).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// Запятая и точка в поле веса значат одно и то же — на клавиатуре они разные.
    private var parsed: Double? {
        let text = input.replacingOccurrences(of: ",", with: ".").trimmingCharacters(in: .whitespaces)
        guard let value = Double(text), value > 20, value < 400 else { return nil }
        return value
    }

    private func record() {
        guard let parsed else { return }
        withAnimation(Motion.card) {
            profile.recordBodyWeight(parsed)
            input = ""
            typing = false
        }
    }
}
