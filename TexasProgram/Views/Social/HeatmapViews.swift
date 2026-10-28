import SwiftUI
import UIKit

// MARK: - Карта тренировок за год

/// Год тренировок клеточками, как на GitHub: колонка — неделя с понедельника,
/// свежие справа. Чем тяжелее день, тем ярче клеточка. Нажатие — какой это день.
struct ProfileHeatmapCard: View {
    /// Уровни по дням, последний — сегодня.
    let levels: [Int]
    var today: Date = Date()

    @State private var selected: Int?

    private static let cell: CGFloat = 12
    private static let gap: CGFloat = 3
    private static let monthsHeight: CGFloat = 16
    private static let monthNames = ["янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек"]

    private static let dayFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ru_RU")
        formatter.dateFormat = "EE, d MMMM"
        return formatter
    }()

    static func color(_ level: Int) -> Color {
        switch level {
        case 1: return Theme.accent.opacity(0.3)
        case 2: return Theme.accent.opacity(0.52)
        case 3: return Theme.accent.opacity(0.76)
        case 4: return Theme.accent
        default: return Color(white: 0.16)
        }
    }

    private static func word(_ level: Int) -> String {
        switch level {
        case 1: return "лёгкая тренировка"
        case 2: return "тренировка"
        case 3: return "тяжёлая тренировка"
        case 4: return "очень тяжёлая тренировка"
        default: return "отдых"
        }
    }

    var body: some View {
        let count = TrainingHeatmap.visibleCount(today: today)
        let cells = Array(levels.suffix(count))
        let columns = (cells.count + 6) / 7
        let workouts = TrainingHeatmap.workouts(levels)
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("ГОД ТРЕНИРОВОК")
                    .font(.system(size: 13, weight: .bold))
                    .tracking(1.4)
                    .foregroundStyle(Color.white.opacity(0.5))
                Spacer()
                Text("\(workouts) \(RuPlural.word(workouts, "тренировка", "тренировки", "тренировок"))")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Color.white.opacity(0.75))
            }
            ScrollView(.horizontal, showsIndicators: false) {
                grid(cells: cells, columns: columns)
                    .padding(.horizontal, 1)
            }
            .defaultScrollAnchor(.trailing)
            footer(cells: cells)
        }
        .padding(18)
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Год тренировок: \(workouts) \(RuPlural.word(workouts, "тренировка", "тренировки", "тренировок"))")
    }

    private func grid(cells: [Int], columns: Int) -> some View {
        let step = Self.cell + Self.gap
        let width = CGFloat(columns) * step - Self.gap
        let height = Self.monthsHeight + 7 * step - Self.gap
        let labels = monthLabels(count: cells.count, columns: columns)
        return Canvas { context, _ in
            for label in labels {
                context.draw(
                    Text(label.text).font(.system(size: 10, weight: .semibold)).foregroundColor(Color.white.opacity(0.45)),
                    at: CGPoint(x: CGFloat(label.column) * step, y: 0),
                    anchor: .topLeading
                )
            }
            for (index, level) in cells.enumerated() {
                let rect = CGRect(x: CGFloat(index / 7) * step, y: Self.monthsHeight + CGFloat(index % 7) * step,
                                  width: Self.cell, height: Self.cell)
                let shape = Path(roundedRect: rect, cornerRadius: 3)
                context.fill(shape, with: .color(Self.color(level)))
                if index == selected {
                    context.stroke(Path(roundedRect: rect.insetBy(dx: -1.5, dy: -1.5), cornerRadius: 4), with: .color(.white), lineWidth: 1.5)
                }
            }
        }
        .frame(width: width, height: height)
        .contentShape(Rectangle())
        .gesture(
            SpatialTapGesture().onEnded { value in
                let column = Int(value.location.x / step)
                let row = Int((value.location.y - Self.monthsHeight) / step)
                guard value.location.y >= Self.monthsHeight, (0..<7).contains(row) else { return }
                let index = column * 7 + row
                guard cells.indices.contains(index) else { return }
                UISelectionFeedbackGenerator().selectionChanged()
                selected = selected == index ? nil : index
            }
        )
    }

    @ViewBuilder
    private func footer(cells: [Int]) -> some View {
        if let selected, cells.indices.contains(selected) {
            let date = Calendar.current.date(byAdding: .day, value: selected - (cells.count - 1), to: today) ?? today
            HStack(spacing: 6) {
                RoundedRectangle(cornerRadius: 3).fill(Self.color(cells[selected])).frame(width: 12, height: 12)
                Text("\(Self.dayFormatter.string(from: date)) — \(Self.word(cells[selected]))")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(.white)
            }
        } else {
            HStack(spacing: 4) {
                Text("Меньше")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Color.white.opacity(0.45))
                ForEach(0..<5, id: \.self) { level in
                    RoundedRectangle(cornerRadius: 3).fill(Self.color(level)).frame(width: 12, height: 12)
                }
                Text("Больше")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Color.white.opacity(0.45))
            }
        }
    }

    /// Подпись месяца над колонкой, с которой он начинается.
    private func monthLabels(count: Int, columns: Int) -> [(column: Int, text: String)] {
        let calendar = Calendar.current
        var labels: [(column: Int, text: String)] = []
        var previous: Int?
        for column in 0..<columns {
            let offset = column * 7 - (count - 1)
            guard let monday = calendar.date(byAdding: .day, value: offset, to: today) else { continue }
            let month = calendar.component(.month, from: monday)
            if month != previous {
                // Первая колонка с обрезком месяца — подпись налезла бы на следующую.
                if previous != nil || calendar.component(.day, from: monday) <= 7 {
                    labels.append((column, Self.monthNames[month - 1]))
                }
                previous = month
            }
        }
        return labels
    }
}
