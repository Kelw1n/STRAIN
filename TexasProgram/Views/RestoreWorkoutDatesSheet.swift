import SwiftUI

struct RestoreWorkoutDatesSheet: View {
    let workouts: [ProgramProfile.UndatedWorkout]
    let onRestore: ([String: Date]) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var latest: Date
    @State private var reviewing = false
    @State private var dates: [String: Date] = [:]
    @State private var excluded: Set<String> = []

    init(workouts: [ProgramProfile.UndatedWorkout], onRestore: @escaping ([String: Date]) -> Void) {
        self.workouts = workouts
        self.onRestore = onRestore
        let calendar = WorkoutScheduler.trainingCalendar()
        let today = calendar.startOfDay(for: Date())
        let todayOffset = (calendar.component(.weekday, from: today) + 5) % 7
        let daysBack = workouts.last.map { (todayOffset - $0.scheduleOffset % 7 + 6) % 7 + 1 } ?? 1
        _latest = State(initialValue: calendar.date(byAdding: .day, value: -daysBack, to: today) ?? today)
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text(L("Укажи, когда ты действительно сделал последнюю тренировку из списка. STRAIN предложит даты остальных по расписанию."))
                    if let last = workouts.last {
                        DatePicker(L("Неделя \(last.week) · День \(last.day)"), selection: $latest, in: ...Date(), displayedComponents: .date)
                    }
                    if !reviewing {
                        Button(L("Показать даты")) {
                            suggestDates()
                            reviewing = true
                        }
                    }
                }
                if reviewing {
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
                    .disabled(!reviewing || dates.isEmpty || workouts.allSatisfy { excluded.contains($0.id) })
                }
            }
        }
        .onChange(of: latest) { _, _ in if reviewing { suggestDates() } }
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
