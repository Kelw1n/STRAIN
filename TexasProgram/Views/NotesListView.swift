import SwiftUI

/// Все заметки профиля одним списком.
///
/// Заметка живёт на своей тренировке, и это правильно — писать её удобно там же.
/// Но смысл в ней появляется, только когда месяц можно перечитать подряд.
struct NotesListView: View {
    @Bindable var profile: ProgramProfile

    /// От свежих к старым: перечитывают обычно последнее.
    private var notes: [(key: String, week: Int, day: Int, text: String, archived: Bool)] {
        let dates = Dictionary(profile.completionLog.map { ($0.key, $0.date) }, uniquingKeysWith: { first, _ in first })
        return profile.workoutNotes
            .compactMap { key, text in
                let current = profile.noteSlot(for: key)
                guard let slot = current ?? profile.previousDaySlot(for: key) else { return nil }
                return (key: key, week: slot.week, day: slot.day, text: text, archived: current == nil)
            }
            .sorted { first, second in
                if first.archived != second.archived { return !first.archived }
                return (dates[first.key] ?? .distantPast, first.week, first.day, first.key) >
                       (dates[second.key] ?? .distantPast, second.week, second.day, second.key)
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
                ForEach(Array(notes.enumerated()), id: \.offset) { index, note in
                    CardView(padding: 15, spacing: 7) {
                        HStack {
                            Text(note.archived
                                 ? L("Прошлый цикл · Неделя \(note.week), день \(note.day)")
                                 : title(week: note.week, day: note.day))
                                .font(.caption.weight(.bold))
                                .foregroundStyle(Theme.accentGradient)
                            Spacer(minLength: 8)
                            Button {
                                withAnimation(Motion.card) {
                                    profile.workoutNotes.removeValue(forKey: note.key)
                                }
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
                    .appearIn(index)
                    .softScroll()
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

    private func title(week: Int, day: Int) -> String {
        guard week == ProgramProfile.freeWeek else { return "Неделя \(week), день \(day)" }
        return profile.freeDay(day)?.title ?? "Свободная тренировка"
    }
}
