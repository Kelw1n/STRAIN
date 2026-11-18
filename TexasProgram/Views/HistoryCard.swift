import SwiftUI

/// Список последних тренировок с датами.
struct HistoryCard: View {
    let records: [CompletionRecord]
    /// Названия своих тренировок по номеру — у них нет недели и дня программы.
    var freeTitles: [Int: String] = [:]
    let onEditDate: (CompletionRecord) -> Void

    private var recent: [CompletionRecord] {
        Array(records.sorted { $0.date > $1.date }.prefix(8))
    }

    var body: some View {
        if !recent.isEmpty {
            CardView {
                Text(L("Последние тренировки")).font(.title3.weight(.bold))
                ForEach(recent) { record in
                    if record.week == ProgramProfile.freeWeek {
                        row(record, editable: false)
                    } else {
                        Button { onEditDate(record) } label: {
                            row(record, editable: true)
                        }
                        .buttonStyle(.plain)
                        .accessibilityHint(L("Изменить дату тренировки"))
                    }
                }
            }
        }
    }

    private func row(_ record: CompletionRecord, editable: Bool) -> some View {
        HStack(spacing: 10) {
            Text(RuDate.short(weekday: Calendar.current.component(.weekday, from: record.date)).uppercased())
                .font(.caption2.weight(.black))
                .foregroundStyle(Theme.accent)
                .frame(width: 24, alignment: .leading)
            Text(editable
                 ? L("Неделя \(record.week) · день \(record.day)")
                 : (freeTitles[record.day] ?? L("Своя тренировка")))
                .font(.caption)
            Spacer(minLength: 4)
            Text(RuDate.dayMonth(record.date))
                .font(.caption2).foregroundStyle(.secondary)
            if editable {
                Image(systemName: "pencil")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .accessibilityHidden(true)
            }
        }
        .padding(.vertical, 6)
        .padding(.horizontal, 10)
        .background(Color.primary.opacity(0.04), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        .contentShape(Rectangle())
    }
}

struct WorkoutDateEditSheet: View {
    let record: CompletionRecord
    let onSave: (Date) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var date: Date

    init(record: CompletionRecord, onSave: @escaping (Date) -> Void) {
        self.record = record
        self.onSave = onSave
        _date = State(initialValue: min(record.date, Date()))
    }

    var body: some View {
        NavigationStack {
            Form {
                Section(L("Неделя \(record.week) · день \(record.day)")) {
                    DatePicker(L("Дата"), selection: $date, in: ...Date(), displayedComponents: .date)
                }
            }
            .navigationTitle(L("Изменить дату тренировки"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L("Отмена")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("Сохранить")) {
                        onSave(date)
                        dismiss()
                    }
                    .disabled(Calendar.current.isDate(date, inSameDayAs: record.date))
                }
            }
        }
    }
}
