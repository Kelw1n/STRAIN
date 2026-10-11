import SwiftUI
import Combine

// MARK: - Модели состояния сессии тренировки

struct ActiveSetState: Identifiable, Equatable {
    let id = UUID()
    let index: Int
    var previousText: String
    var weight: Double
    var reps: Int
    var isDone: Bool
}

struct ActiveExerciseState: Identifiable, Equatable {
    var id: String { prescription.id }
    let prescription: ExercisePrescription
    var restSeconds: Int
    var sets: [ActiveSetState]
}

// MARK: - Менеджер активной тренировки (Сворачивание, сохранение, секундомер)

@Observable
final class WorkoutSessionManager {
    static let shared = WorkoutSessionManager()

    var activeWorkout: ScheduledWorkout? = nil
    var isPresented: Bool = false
    var isRunning: Bool { activeWorkout != nil }

    var startDate: Date = Date()
    var elapsedSeconds: Int = 0
    var exercises: [ActiveExerciseState] = []

    var noteText: String = ""
    var showingZeroSetsAlert: Bool = false
    var showingSummary: Bool = false
    var showingNoteSheet: Bool = false
    var customRestTargetExerciseIndex: Int? = nil

    /// Тренировка, которую попросили начать, пока идёт другая.
    var pendingWorkout: ScheduledWorkout? = nil
    var showingReplaceConfirm: Bool = false

    var replaceTitle: String {
        guard let current = activeWorkout else { return "Уже идёт тренировка" }
        return "Уже идёт: \(current.day.title)"
    }

    /// Отметки дня на момент старта. Отмена возвращает к ним, а не стирает день:
    /// раньше отмена сессии по уже закрытому дню выбрасывала его из истории.
    @ObservationIgnored private var startSnapshot: ProgramProfile.DaySnapshot?

    private var timerCancellable: AnyCancellable?

    var totalCompletedSets: Int {
        exercises.flatMap(\.sets).filter(\.isDone).count
    }

    var totalSetsCount: Int {
        exercises.flatMap(\.sets).count
    }

    var totalTonnage: Double {
        exercises.flatMap(\.sets)
            .filter(\.isDone)
            .reduce(0.0) { $0 + ($1.weight * Double($1.reps)) }
    }

    var density: Double {
        let mins = max(1.0, Double(elapsedSeconds) / 60.0)
        return totalTonnage / mins
    }

    /// Вход для кнопок «Начать». Если идёт другая тренировка, молча её не
    /// подменяем — спрашиваем, что делать.
    func requestStart(workout: ScheduledWorkout, profile: ProgramProfile) {
        if let current = activeWorkout, current.id != workout.id {
            pendingWorkout = workout
            showingReplaceConfirm = true
            return
        }
        startWorkout(workout: workout, profile: profile)
    }

    func cancelReplace() {
        pendingWorkout = nil
        expand()
    }

    func confirmReplace(profile: ProgramProfile) {
        guard let next = pendingWorkout else { return }
        pendingWorkout = nil
        discardWorkout(profile: profile)
        startWorkout(workout: next, profile: profile)
    }

    /// Полноэкранную тренировку нельзя показать, пока открыт лист: система
    /// отказывает молча, а флаг остаётся поднятым, и кнопка «Продолжить»
    /// больше ничего не делает. Лист закрылся — показываем заново.
    func representAfterSheet() {
        if isPresented {
            isPresented = false
            DispatchQueue.main.async { self.isPresented = true }
        }
        if showingReplaceConfirm {
            showingReplaceConfirm = false
            DispatchQueue.main.async { self.showingReplaceConfirm = true }
        }
    }

    func startWorkout(workout: ScheduledWorkout, profile: ProgramProfile) {
        if let current = activeWorkout, current.id == workout.id {
            // Уже идет эта же тренировка — просто разворачиваем!
            isPresented = true
            return
        }

        // Новая тренировка
        activeWorkout = workout
        startDate = Date()
        elapsedSeconds = 0
        startSnapshot = profile.snapshotDay(week: workout.week, day: workout.day.number)
        noteText = profile.note(week: workout.week, day: workout.day.number)
        showingZeroSetsAlert = false
        showingSummary = false
        showingNoteSheet = false

        // Инициализируем упражнения
        let prescs = profile.exercises(for: workout)
        exercises = prescs.map { presc in
            let defaultRest = presc.benchSession != nil ? 180 : profile.defaultRestSeconds
            let plannedWeight = extractPlannedWeight(for: presc, profile: profile, workout: workout)
            let plannedReps = extractPlannedReps(for: presc)
            // Сколько подходов в плане, столько и строк: два — значит два.
            let setsCount = max(presc.sets, 1)

            let sets = (0..<setsCount).map { sIdx in
                ActiveSetState(
                    index: sIdx,
                    previousText: previousPerformance(for: presc, setIndex: sIdx, profile: profile),
                    weight: plannedWeight,
                    reps: plannedReps,
                    isDone: false // ВАЖНО: всегда начинается с 0 выполненных подходов!
                )
            }

            return ActiveExerciseState(
                prescription: presc,
                restSeconds: defaultRest,
                sets: sets
            )
        }

        startTimer()
        isPresented = true
    }

    func minimize() {
        withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
            isPresented = false
        }
    }

    func expand() {
        withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
            isPresented = true
        }
    }

    func finishWorkout(profile: ProgramProfile, customDuration: Int? = nil, effort: String? = nil) {
        guard let workout = activeWorkout else { return }

        // Записываем подходы
        for ex in exercises {
            for set in ex.sets where set.isDone {
                profile.recordSet(
                    week: workout.week,
                    day: workout.day.number,
                    exercise: ex.prescription,
                    index: set.index,
                    reps: set.reps,
                    weight: set.weight
                )
            }
        }

        let finalDuration = customDuration ?? elapsedSeconds

        if !workout.isCompleted {
            profile.toggleCompleted(
                week: workout.week,
                day: workout.day.number,
                now: Date(),
                durationSeconds: finalDuration,
                effort: effort
            )
        }

        stopTimer()
        startSnapshot = nil
        activeWorkout = nil
        isPresented = false
        exercises = []
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }

    func discardWorkout(profile: ProgramProfile) {
        guard let workout = activeWorkout else { return }
        if let snapshot = startSnapshot {
            profile.restoreDay(snapshot, week: workout.week, day: workout.day.number)
        }
        startSnapshot = nil
        showingZeroSetsAlert = false

        stopTimer()
        activeWorkout = nil
        isPresented = false
        exercises = []
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
    }

    func toggleSetDone(exIdx: Int, sIdx: Int, profile: ProgramProfile, restTimer: RestTimer) {
        guard exIdx < exercises.count, sIdx < exercises[exIdx].sets.count, let workout = activeWorkout else { return }
        exercises[exIdx].sets[sIdx].isDone.toggle()
        let isDone = exercises[exIdx].sets[sIdx].isDone
        let currentSet = exercises[exIdx].sets[sIdx]
        let ex = exercises[exIdx]

        if isDone {
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            profile.recordSet(
                week: workout.week,
                day: workout.day.number,
                exercise: ex.prescription,
                index: currentSet.index,
                reps: currentSet.reps,
                weight: currentSet.weight
            )
            let completedCount = exercises[exIdx].sets.filter(\.isDone).count
            let totalCount = exercises[exIdx].sets.count
            let details = "\(WeightFormat.kilogramsPrecise(currentSet.weight)) × \(currentSet.reps)"
            restTimer.start(
                TimeInterval(ex.restSeconds),
                exerciseName: ex.prescription.name,
                setDetails: details,
                completedSets: completedCount,
                totalSets: totalCount
            )
        } else {
            profile.clearSetEntry(
                week: workout.week,
                day: workout.day.number,
                exercise: ex.prescription,
                index: currentSet.index
            )
            let remainingDone = exercises[exIdx].sets.filter(\.isDone).count
            profile.updateCompletedSets(
                week: workout.week,
                day: workout.day.number,
                exercise: ex.prescription,
                count: remainingDone
            )
        }
    }

    func addSet(to exIdx: Int, profile: ProgramProfile) {
        guard exIdx < exercises.count, activeWorkout != nil else { return }
        let existingSets = exercises[exIdx].sets
        let lastWeight = existingSets.last?.weight ?? 50.0
        let lastReps = existingSets.last?.reps ?? 10
        let newIndex = existingSets.count

        let newSet = ActiveSetState(
            index: newIndex,
            previousText: previousPerformance(for: exercises[exIdx].prescription, setIndex: newIndex, profile: profile),
            weight: lastWeight,
            reps: lastReps,
            isDone: false
        )
        exercises[exIdx].sets.append(newSet)
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    /// Вес из поля ввода. Если подход уже отмечен, запись правим сразу:
    /// иначе до «Завершить» в истории висел бы старый вес.
    func setWeight(_ weight: Double, exIdx: Int, sIdx: Int, profile: ProgramProfile) {
        guard exIdx < exercises.count, sIdx < exercises[exIdx].sets.count else { return }
        exercises[exIdx].sets[sIdx].weight = max(0, weight)
        rerecordIfDone(exIdx: exIdx, sIdx: sIdx, profile: profile)
    }

    func setReps(_ reps: Int, exIdx: Int, sIdx: Int, profile: ProgramProfile) {
        guard exIdx < exercises.count, sIdx < exercises[exIdx].sets.count else { return }
        exercises[exIdx].sets[sIdx].reps = max(0, reps)
        rerecordIfDone(exIdx: exIdx, sIdx: sIdx, profile: profile)
    }

    private func rerecordIfDone(exIdx: Int, sIdx: Int, profile: ProgramProfile) {
        guard let workout = activeWorkout else { return }
        let set = exercises[exIdx].sets[sIdx]
        guard set.isDone else { return }
        profile.recordSet(
            week: workout.week,
            day: workout.day.number,
            exercise: exercises[exIdx].prescription,
            index: set.index,
            reps: set.reps,
            weight: set.weight
        )
    }

    /// Сдвигает упражнение на `offset` позиций в пределах тренировки.
    func moveExercise(from index: Int, by offset: Int) {
        let target = index + offset
        guard exercises.indices.contains(index), exercises.indices.contains(target) else { return }
        exercises.swapAt(index, target)
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    func adjustWeight(exIdx: Int, sIdx: Int, delta: Double) {
        guard exIdx < exercises.count, sIdx < exercises[exIdx].sets.count else { return }
        exercises[exIdx].sets[sIdx].weight = max(0, exercises[exIdx].sets[sIdx].weight + delta)
    }

    func adjustReps(exIdx: Int, sIdx: Int, delta: Int) {
        guard exIdx < exercises.count, sIdx < exercises[exIdx].sets.count else { return }
        exercises[exIdx].sets[sIdx].reps = max(1, exercises[exIdx].sets[sIdx].reps + delta)
    }

    func setRestDuration(_ seconds: Int) {
        if let idx = customRestTargetExerciseIndex, idx < exercises.count {
            exercises[idx].restSeconds = seconds
        }
    }

    /// Время считаем от даты старта, а не тиками: в заблокированном телефоне
    /// таймер не тикает, и часовая тренировка показывала пятнадцать минут.
    private func startTimer() {
        stopTimer()
        timerCancellable = Timer.publish(every: 1, on: .main, in: .common)
            .autoconnect()
            .sink { [weak self] _ in
                self?.refreshElapsed()
            }
    }

    func refreshElapsed(now: Date = Date()) {
        guard activeWorkout != nil else { return }
        elapsedSeconds = max(0, Int(now.timeIntervalSince(startDate)))
    }

    private func stopTimer() {
        timerCancellable?.cancel()
        timerCancellable = nil
    }

    private func extractPlannedWeight(for presc: ExercisePrescription, profile: ProgramProfile, workout: ScheduledWorkout) -> Double {
        if case .kilograms(let value) = presc.load { return value }
        if let hint = profile.accessoryHint(for: presc, week: workout.week, day: workout.day.number)?.weight {
            return hint
        }
        let hist = profile.history(forExerciseNamed: presc.name)
        return hist.last?.best ?? 50.0
    }

    private func extractPlannedReps(for presc: ExercisePrescription) -> Int {
        if let reps = presc.plannedReps { return reps }
        return 10
    }

    private func previousPerformance(for presc: ExercisePrescription, setIndex: Int, profile: ProgramProfile) -> String {
        let hist = profile.history(forExerciseNamed: presc.name)
        guard let lastPoint = hist.last, !lastPoint.entries.isEmpty else { return "—" }
        if setIndex < lastPoint.entries.count {
            let entry = lastPoint.entries[setIndex]
            return "\(WeightFormat.kilogramsPrecise(entry.weight)) × \(entry.reps)"
        } else if let best = lastPoint.entries.first {
            return "\(WeightFormat.kilogramsPrecise(best.weight)) × \(best.reps)"
        }
        return "—"
    }
}
