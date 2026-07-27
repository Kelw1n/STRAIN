import Charts
import SwiftUI

/// График рабочих весов по датам выполненных тренировок.
///
/// Точки берутся из истории отметок, а не пересчитываются из текущих максимумов:
/// иначе правка 5ПМ в настройках задним числом переписывала бы весь график.
struct ProgressChartView: View {
    let records: [CompletionRecord]

    private enum Lift: String, CaseIterable, Identifiable {
        case squat = "Присед"
        case bench = "Жим"
        case deadlift = "Тяга"

        var id: String { rawValue }

        var color: Color {
            switch self {
            case .squat: return Theme.accent
            case .bench: return Theme.accentDeep
            case .deadlift: return Theme.record
            }
        }

        func value(in record: CompletionRecord) -> Double? {
            switch self {
            case .squat: return record.squat
            case .bench: return record.bench
            case .deadlift: return record.deadlift
            }
        }
    }

    private struct Point: Identifiable {
        let id = UUID()
        let lift: Lift
        let date: Date
        let weight: Double
    }

    private var points: [Point] {
        records
            .sorted { $0.date < $1.date }
            .flatMap { record in
                Lift.allCases.compactMap { lift in
                    guard let weight = lift.value(in: record) else { return nil }
                    return Point(lift: lift, date: record.date, weight: weight)
                }
            }
    }

    private var hasEnoughData: Bool {
        // По одной точке линия не строится, а обманывать пустым графиком не стоит.
        Set(records.map { Calendar.current.startOfDay(for: $0.date) }).count >= 2
    }

    var body: some View {
        CardView {
            Text("Рабочие веса").font(.title3.weight(.bold))

            if hasEnoughData {
                Chart(points) { point in
                    LineMark(
                        x: .value("Дата", point.date, unit: .day),
                        y: .value("Вес", point.weight)
                    )
                    .foregroundStyle(by: .value("Движение", point.lift.rawValue))
                    .interpolationMethod(.monotone)
                    .lineStyle(StrokeStyle(lineWidth: 2.5, lineCap: .round))

                    PointMark(
                        x: .value("Дата", point.date, unit: .day),
                        y: .value("Вес", point.weight)
                    )
                    .foregroundStyle(by: .value("Движение", point.lift.rawValue))
                    .symbolSize(28)
                }
                .chartForegroundStyleScale([
                    Lift.squat.rawValue: Lift.squat.color,
                    Lift.bench.rawValue: Lift.bench.color,
                    Lift.deadlift.rawValue: Lift.deadlift.color
                ])
                .chartLegend(position: .bottom, spacing: 10)
                .chartYAxis {
                    AxisMarks(position: .leading) { value in
                        AxisGridLine().foregroundStyle(Color.primary.opacity(0.08))
                        AxisValueLabel {
                            if let weight = value.as(Double.self) {
                                Text("\(Int(weight))").font(.caption2)
                            }
                        }
                    }
                }
                .chartXAxis {
                    AxisMarks(values: .automatic(desiredCount: 4)) { value in
                        AxisGridLine().foregroundStyle(Color.primary.opacity(0.06))
                        AxisValueLabel(format: .dateTime.day().month(.abbreviated))
                    }
                }
                .frame(height: 190)
            } else {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Пока нечего показывать")
                        .font(.subheadline.weight(.semibold))
                    Text("График появится после двух отмеченных тренировок в разные дни.")
                        .font(.caption).foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.vertical, 12)
            }
        }
    }
}

/// Список последних тренировок с датами.
struct HistoryCard: View {
    let records: [CompletionRecord]

    private var recent: [CompletionRecord] {
        Array(records.sorted { $0.date > $1.date }.prefix(8))
    }

    var body: some View {
        if !recent.isEmpty {
            CardView {
                Text("Последние тренировки").font(.title3.weight(.bold))
                ForEach(recent) { record in
                    HStack(spacing: 10) {
                        Text(RuDate.short(weekday: Calendar.current.component(.weekday, from: record.date)).uppercased())
                            .font(.caption2.weight(.black))
                            .foregroundStyle(Theme.accent)
                            .frame(width: 24, alignment: .leading)
                        Text("Неделя \(record.week) · день \(record.day)")
                            .font(.caption)
                        Spacer(minLength: 4)
                        Text(RuDate.dayMonth(record.date))
                            .font(.caption2).foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 6)
                    .padding(.horizontal, 10)
                    .background(Color.primary.opacity(0.04), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
            }
        }
    }
}
