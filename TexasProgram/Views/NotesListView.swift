import SwiftUI

/// Все заметки цикла одним списком.
///
/// Заметка живёт на своей тренировке, и это правильно — писать её удобно там же.
/// Но смысл в ней появляется, только когда месяц можно перечитать подряд.
struct NotesListView: View {
    @Bindable var profile: ProgramProfile

    /// От свежих к старым: перечитывают обычно последнее.
    private var notes: [(week: Int, day: Int, text: String)] {
        profile.workoutNotes
            .compactMap { entry -> (week: Int, day: Int, text: String)? in
                let key = entry.key, text = entry.value
                // Заметки свободных тренировок — вне циклов, у них свой ключ.
                if key.hasPrefix("free-") {
                    guard let number = Int(key.dropFirst("free-".count)) else { return nil }
                    return (ProgramProfile.freeWeek, number, text)
                }
                guard profile.isOwnKey(key) else { return nil }
                let parts = key.dropFirst(profile.keyPrefix.count).split(separator: "-")
                guard parts.count == 2, let week = Int(parts[0]), let day = Int(parts[1]) else { return nil }
                return (week, day, text)
            }
            .sorted { ($0.week, $0.day) > ($1.week, $1.day) }
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
                            Text(title(week: note.week, day: note.day))
                                .font(.caption.weight(.bold))
                                .foregroundStyle(Theme.accentGradient)
                            Spacer(minLength: 8)
                            Button {
                                withAnimation(Motion.card) {
                                    profile.setNote("", week: note.week, day: note.day)
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
