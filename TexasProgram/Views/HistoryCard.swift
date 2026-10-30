import SwiftUI

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
                            .font(.caption2.weight(.canvas))
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
