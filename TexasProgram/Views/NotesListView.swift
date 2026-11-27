import SwiftUI

/// Все заметки профиля одним списком.
///
/// Заметка живёт на своей тренировке, и это правильно — писать её удобно там же.
/// Но смысл в ней появляется, только когда месяц можно перечитать подряд.
struct NotesListView: View {
    @Bindable var profile: ProgramProfile

    private struct Note: Identifiable {
        let id: String
        let week: Int
        let day: Int
        let text: String
        let archived: Bool
    }

    /// От свежих к старым: перечитывают обычно последнее.
    private var notes: [Note] {
        let dates = Dictionary(profile.completionLog.map { ($0.key, $0.date) }, uniquingKeysWith: { first, _ in first })
        return profile.workoutNotes
            .compactMap { key, text in
                let current = profile.noteSlot(for: key)
                guard let slot = current ?? profile.previousDaySlot(for: key) else { return nil }
                return Note(id: key, week: slot.week, day: slot.day, text: text, archived: current == nil)
            }
            .sorted { first, second in
                if first.archived != second.archived { return !first.archived }
                return (dates[first.id] ?? .distantPast, first.week, first.day, first.id) >
                       (dates[second.id] ?? .distantPast, second.week, second.day, second.id)
            }
    }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                if notes.isEmpty {
                    CardView {
                        Text(L("Заметок пока нет")).font(.headline)
                        Text(L("Поле для заметки есть на каждой тренировке — под списком упражнений. Через месяц по ним будет понятно, почему неделя вышла такой."))
                            .font(.footnote).foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .appearIn(0)
                }
                ForEach(Array(notes.enumerated()), id: \.element.id) { index, note in
                    row(note).appearIn(index).softScroll()
                }
            }
            .padding(.horizontal)
            .padding(.bottom, 28)
        }
        .scrollIndicators(.hidden)
        .screenBackground()
        .navigationTitle(L("Заметки"))
        .navigationBarTitleDisplayMode(.inline)
    }

    private func row(_ note: Note) -> some View {
        let header = note.archived
            ? L("Прошлый цикл · Неделя \(note.week), день \(note.day)")
            : title(week: note.week, day: note.day)
        return CardView(padding: 15, spacing: 7) {
            HStack {
                Text(header)
                    .font(.caption.weight(.bold))
                    .foregroundStyle(Theme.accentGradient)
                Spacer(minLength: 8)
                Button {
                    withAnimation(Motion.card) { profile.workoutNotes[note.id] = nil }
                } label: {
                    Image(systemName: "trash")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.pressable)
            }
            Text(verbatim: note.text)
                .font(.footnote)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func title(week: Int, day: Int) -> String {
        guard week == ProgramProfile.freeWeek else { return "Неделя \(week), день \(day)" }
        return profile.freeDay(day)?.title ?? "Свободная тренировка"
    }
}
