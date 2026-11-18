import SwiftUI

struct RestoreWorkoutDatesSheet: View {
    let workouts: [ProgramProfile.UndatedWorkout]
    let onRestore: ([String: Date]) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var latest = Date()
    @State private var dates: [String: Date] = [:]
    @State private var excluded: Set<String> = []

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text(L("Выбери дату последней тренировки из списка. Остальные даты STRAIN предложит по расписанию. Проверь их и убери дни, когда не тренировался."))
                    DatePicker(L("Последняя тренировка"), selection: $latest, in: ...Date(), displayedComponents: .date)
                }
                Section(L("Проверь даты тренировок")) {
                    ForEach(workouts) { workout in
                        VStack(alignment: .leading, spacing: 8) {
                            Toggle(L("Неделя \(workout.week) · День \(workout.day)"), isOn: included(workout.id))
                            if !excluded.contains(workout.id) {
                                DatePicker(L("Дата"), selection: date(workout.id), in: ...Date(), displayedComponents: .date)
                            }
                        }
                    }
                }
            }
            .navigationTitle(L("Восстановить историю"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L("Отмена")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("Сохранить")) {
                        onRestore(dates.filter { !excluded.contains($0.key) })
                        dismiss()
                    }
                    .disabled(dates.isEmpty || workouts.allSatisfy { excluded.contains($0.id) })
                }
            }
        }
        .onAppear { if dates.isEmpty { suggestDates() } }
        .onChange(of: latest) { _, _ in suggestDates() }
    }

    private func suggestDates() {
        guard let last = workouts.last else { return }
        let calendar = Calendar.current
        let anchor = calendar.startOfDay(for: latest)
        dates = Dictionary(uniqueKeysWithValues: workouts.compactMap { workout in
            let offset = workout.scheduleOffset - last.scheduleOffset
            return calendar.date(byAdding: .day, value: offset, to: anchor).map { (workout.id, $0) }
        })
    }

    private func included(_ id: String) -> Binding<Bool> {
        Binding(get: { !excluded.contains(id) }, set: { value in
            if value { excluded.remove(id) } else { excluded.insert(id) }
        })
    }

    private func date(_ id: String) -> Binding<Date> {
        Binding(get: { dates[id] ?? latest }, set: { dates[id] = $0 })
    }
}
