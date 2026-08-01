import Charts
import SwiftUI

/// Тоннаж по неделям: сумма «повторы × вес» записанных подходов.
///
/// Считается только по факту. Догадываться по плану нельзя: повторы там бывают
/// диапазоном («8–12») и списком («6 / 5 / 5 / 4 / 4»), и любая догадка дала бы
/// красивое, но выдуманное число.
struct TonnageChartView: View {
    let weeks: [WeeklyTonnage]

    private var total: Double { weeks.reduce(0) { $0 + $1.total } }
    private var setCount: Int { weeks.reduce(0) { $0 + $1.setCount } }

    var body: some View {
        CardView {
            HStack(alignment: .firstTextBaseline) {
                Text("Тоннаж").font(.title3.weight(.bold))
                Spacer(minLength: 8)
                if !weeks.isEmpty {
                    Text(WeightFormat.tonnage(total))
                        .font(.subheadline.weight(.bold).monospacedDigit())
                        .foregroundStyle(Theme.accentGradient)
                }
            }

            if weeks.isEmpty {
                Text("Записывай подходы долгим нажатием на кружок — тоннаж считается по ним.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                Chart(weeks) { item in
                    BarMark(
                        x: .value("Неделя", item.week),
                        y: .value("Тоннаж", item.total)
                    )
                    .foregroundStyle(Theme.accentGradient)
                    .cornerRadius(5)
                }
                .chartYAxis {
                    AxisMarks(position: .leading) { value in
                        AxisGridLine().foregroundStyle(Color.primary.opacity(0.08))
                        AxisValueLabel {
                            if let weight = value.as(Double.self) {
                                Text(WeightFormat.tonnage(weight)).font(.caption2)
                            }
                        }
                    }
                }
                .chartXAxis {
                    AxisMarks(values: weeks.map(\.week)) { value in
                        AxisValueLabel {
                            if let week = value.as(Int.self) {
                                Text("\(week)").font(.caption2)
                            }
                        }
                    }
                }
                .frame(height: 170)

                Text("\(setCount) записанных подходов за \(weeks.count) нед.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }
}
