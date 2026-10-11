import SwiftUI

// MARK: - Модели состояния сессии тренировки

struct ActiveSetState: Identifiable, Equatable, Codable {
    var id = UUID()
    /// Номер подхода в записях. После удаления подхода номера не сдвигаются:
    /// по ним лежат уже записанные факты.
    let index: Int
    /// Что было в прошлый раз в этом подходе — его можно подставить одним нажатием.
    var previous: SetEntry?
    var weight: Double
    var reps: Int
    var isDone: Bool
    /// Подход побил лучший вес упражнения до этой тренировки.
    var isRecord: Bool = false

    var previousText: String {
        guard let previous else { return "—" }
        return "\(WeightFormat.plain(previous.weight))×\(previous.reps)"
    }
}

struct ActiveExerciseState: Identifiable, Equatable, Codable {
    var id: String { prescription.id }
    let prescription: ExercisePrescription
    var restSeconds: Int
    var sets: [ActiveSetState]
    /// Лучший вес до этой тренировки — от него считается рекорд.
    var bestBefore: Double = 0
}

/// Всё, что нужно, чтобы поднять тренировку после выгрузки приложения.
private struct SavedSession: Codable {
    var profileID: UUID
    var workout: ScheduledWorkout
    var startDate: Date
    var exercises: [ActiveExerciseState]
    var noteText: String
    var snapshot: ProgramProfile.DaySnapshot?
}

// MARK: - Менеджер активной тренировки

@Observable
final class WorkoutSessionManager {
    static let shared = WorkoutSessionManager()

    var activeWorkout: ScheduledWorkout? = nil
    var isPresented: Bool = false
    var isRunning: Bool { activeWorkout != nil }

    var startDate: Date = Date()
    var exercises: [ActiveExerciseState] = []

    var noteText: String = "" {
        didSet { if noteText != oldValue { save() } }
    }
    var showingZeroSetsAlert: Bool = false
    var showingSummary: Bool = false
    var showingNoteSheet: Bool = false
    var customRestTargetExerciseIndex: Int? = nil

    /// Тренировка, которую попросили начать, пока идёт другая.
    var pendingWorkout: ScheduledWorkout? = nil
    var showingReplaceConfirm: Bool = false

    /// Упражнение, чью картинку сейчас меняют.
    var artTarget: ExerciseArtTarget? = nil

    /// Последний побитый рекорд — экран показывает плашку и гасит её сам.
    var lastRecord: RecordNotice? = nil

    struct RecordNotice: Equatable, Identifiable {
        let id = UUID()
        let exercise: String
        let weight: Double
    }

    var replaceTitle: String {
        guard let current = activeWorkout else { return "Уже идёт тренировка" }
        return "Уже идёт: \(current.day.title)"
    }

    /// Отметки дня на момент старта. Отмена возвращает к ним, а не стирает день:
    /// раньше отмена сессии по уже закрытому дню выбрасывала его из истории.
    @ObservationIgnored private var startSnapshot: ProgramProfile.DaySnapshot?
    @ObservationIgnored private var profileID: UUID?
    @ObservationIgnored private var saveScheduled = false

    // MARK: - Время

    /// Время считается от даты старта, а не тиками: в заблокированном телефоне
    /// таймер не тикает. И раз оно не хранится, экран не перерисовывается
    /// целиком каждую секунду — тикает только сам секундомер (`SessionClock`).
    func elapsed(at now: Date = Date()) -> Int {
        guard activeWorkout != nil else { return 0 }
        return max(0, Int(now.timeIntervalSince(startDate)))
    }

    var elapsedSeconds: Int { elapsed() }

    // MARK: - Сводка

    var totalCompletedSets: Int {
        exercises.reduce(0) { $0 + $1.sets.filter(\.isDone).count }
    }

    var totalSetsCount: Int {
        exercises.reduce(0) { $0 + $1.sets.count }
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

    // MARK: - Старт и замена

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
            expand()
            return
        }

        activeWorkout = workout
        profileID = profile.profileID
        startDate = Date()
        startSnapshot = profile.snapshotDay(week: workout.week, day: workout.day.number)
        noteText = profile.note(week: workout.week, day: workout.day.number)
        showingZeroSetsAlert = false
        showingSummary = false
        showingNoteSheet = false
        lastRecord = nil

        exercises = profile.exercises(for: workout).map { presc in
            // История — один проход на упражнение. Раньше её пересобирали
            // по всему плану для каждого подхода и ещё раз для веса.
            let history = profile.history(forExerciseNamed: presc.name)
                .filter { !($0.week == workout.week && $0.day == workout.day.number) }
            let last = history.last { !$0.entries.isEmpty }?.entries ?? []
            let best = history.map(\.best).max() ?? 0
            let plannedWeight = plannedWeight(for: presc, profile: profile, workout: workout, last: last)
            let plannedReps = presc.plannedReps ?? 10
            // Сколько подходов в плане, столько и строк: два — значит два.
            let sets = (0..<max(presc.sets, 1)).map { index in
                ActiveSetState(
                    index: index,
                    previous: index < last.count ? last[index] : last.first,
                    weight: plannedWeight,
                    reps: plannedReps,
                    isDone: false
                )
            }
            return ActiveExerciseState(
                prescription: presc,
                restSeconds: presc.benchSession != nil ? 180 : profile.defaultRestSeconds,
                sets: sets,
                bestBefore: best
            )
        }

        save()
        withAnimation(Motion.sheet) { isPresented = true }
    }

    func minimize() {
        withAnimation(Motion.sheet) { isPresented = false }
    }

    func expand() {
        withAnimation(Motion.sheet) { isPresented = true }
    }

    // MARK: - Завершение и отмена

    func finishWorkout(profile: ProgramProfile, customDuration: Int? = nil, effort: String? = nil) {
        guard let workout = activeWorkout else { return }

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
        if !profile.isCompleted(week: workout.week, day: workout.day.number) {
            profile.toggleCompleted(
                week: workout.week,
                day: workout.day.number,
                now: Date(),
                durationSeconds: finalDuration,
                effort: effort
            )
        }

        reset()
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }

    func discardWorkout(profile: ProgramProfile) {
        guard let workout = activeWorkout else { return }
        if let snapshot = startSnapshot {
            profile.restoreDay(snapshot, week: workout.week, day: workout.day.number)
        }
        reset()
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
    }

    private func reset() {
        startSnapshot = nil
        profileID = nil
        showingZeroSetsAlert = false
        lastRecord = nil
        activeWorkout = nil
        isPresented = false
        exercises = []
        clearSaved()
    }

    // MARK: - Подходы

    func toggleSetDone(exIdx: Int, sIdx: Int, profile: ProgramProfile, restTimer: RestTimer) {
        guard exercises.indices.contains(exIdx), exercises[exIdx].sets.indices.contains(sIdx),
              let workout = activeWorkout else { return }
        exercises[exIdx].sets[sIdx].isDone.toggle()
        let set = exercises[exIdx].sets[sIdx]
        let ex = exercises[exIdx]

        if set.isDone {
            profile.recordSet(
                week: workout.week,
                day: workout.day.number,
                exercise: ex.prescription,
                index: set.index,
                reps: set.reps,
                weight: set.weight
            )
            // Рекорд — только против прошлых тренировок: первый раз в жизни
            // сделанное упражнение рекордом не считается.
            let isRecord = ex.bestBefore > 0 && set.weight > ex.bestBefore && set.reps > 0
            exercises[exIdx].sets[sIdx].isRecord = isRecord
            if isRecord {
                lastRecord = RecordNotice(exercise: ex.prescription.name, weight: set.weight)
                UINotificationFeedbackGenerator().notificationOccurred(.success)
            } else {
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            }
            let completed = exercises[exIdx].sets.filter(\.isDone).count
            restTimer.start(
                TimeInterval(ex.restSeconds),
                exerciseName: ex.prescription.name,
                setDetails: "\(WeightFormat.kilogramsPrecise(set.weight)) × \(set.reps)",
                completedSets: completed,
                totalSets: exercises[exIdx].sets.count
            )
        } else {
            exercises[exIdx].sets[sIdx].isRecord = false
            profile.clearSetEntry(
                week: workout.week,
                day: workout.day.number,
                exercise: ex.prescription,
                index: set.index
            )
            profile.updateCompletedSets(
                week: workout.week,
                day: workout.day.number,
                exercise: ex.prescription,
                count: exercises[exIdx].sets.filter(\.isDone).count
            )
        }
        save()
    }

    func addSet(to exIdx: Int, profile: ProgramProfile) {
        guard exercises.indices.contains(exIdx), activeWorkout != nil else { return }
        let existing = exercises[exIdx].sets
        // Следующий номер — после самого большого: после удаления подхода
        // «количество» совпало бы с номером ещё живого подхода.
        let index = (existing.map(\.index).max() ?? -1) + 1
        exercises[exIdx].sets.append(ActiveSetState(
            index: index,
            previous: existing.last?.previous,
            weight: existing.last?.weight ?? 50,
            reps: existing.last?.reps ?? 10,
            isDone: false
        ))
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        save()
    }

    /// Удаляет подход. Последний оставшийся не удаляется — у упражнения
    /// должна быть хоть одна строка.
    func deleteSet(exIdx: Int, sIdx: Int, profile: ProgramProfile) {
        guard exercises.indices.contains(exIdx), exercises[exIdx].sets.indices.contains(sIdx),
              exercises[exIdx].sets.count > 1, let workout = activeWorkout else { return }
        let set = exercises[exIdx].sets.remove(at: sIdx)
        if set.isDone {
            let ex = exercises[exIdx]
            profile.clearSetEntry(week: workout.week, day: workout.day.number, exercise: ex.prescription, index: set.index)
            profile.updateCompletedSets(
                week: workout.week,
                day: workout.day.number,
                exercise: ex.prescription,
                count: ex.sets.filter(\.isDone).count
            )
        }
        UIImpactFeedbackGenerator(style: .rigid).impactOccurred()
        save()
    }

    /// Подставляет в подход то, что было в прошлый раз.
    func applyPrevious(exIdx: Int, sIdx: Int, profile: ProgramProfile) {
        guard exercises.indices.contains(exIdx), exercises[exIdx].sets.indices.contains(sIdx),
              let previous = exercises[exIdx].sets[sIdx].previous else { return }
        exercises[exIdx].sets[sIdx].weight = previous.weight
        exercises[exIdx].sets[sIdx].reps = previous.reps
        rerecordIfDone(exIdx: exIdx, sIdx: sIdx, profile: profile)
        UISelectionFeedbackGenerator().selectionChanged()
        save()
    }

    /// Вес из поля ввода. Если подход уже отмечен, запись правим сразу:
    /// иначе до «Завершить» в истории висел бы старый вес.
    func setWeight(_ weight: Double, exIdx: Int, sIdx: Int, profile: ProgramProfile) {
        guard exercises.indices.contains(exIdx), exercises[exIdx].sets.indices.contains(sIdx) else { return }
        exercises[exIdx].sets[sIdx].weight = max(0, weight)
        rerecordIfDone(exIdx: exIdx, sIdx: sIdx, profile: profile)
        save()
    }

    func setReps(_ reps: Int, exIdx: Int, sIdx: Int, profile: ProgramProfile) {
        guard exercises.indices.contains(exIdx), exercises[exIdx].sets.indices.contains(sIdx) else { return }
        exercises[exIdx].sets[sIdx].reps = max(0, reps)
        rerecordIfDone(exIdx: exIdx, sIdx: sIdx, profile: profile)
        save()
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

    /// Переносит упражнение на другое место. Порядок меняется только в этой
    /// тренировке, план остаётся прежним.
    func moveExercise(from source: Int, to destination: Int) {
        guard exercises.indices.contains(source), exercises.indices.contains(destination), source != destination else { return }
        let item = exercises.remove(at: source)
        exercises.insert(item, at: destination)
        save()
    }

    func setRestDuration(_ seconds: Int) {
        if let idx = customRestTargetExerciseIndex, exercises.indices.contains(idx) {
            exercises[idx].restSeconds = seconds
            save()
        }
    }

    private func plannedWeight(for presc: ExercisePrescription, profile: ProgramProfile, workout: ScheduledWorkout, last: [SetEntry]) -> Double {
        if case .kilograms(let value) = presc.load { return value }
        if let hint = profile.accessoryHint(for: presc, week: workout.week, day: workout.day.number)?.weight {
            return hint
        }
        return last.map(\.weight).max() ?? 50
    }

    // MARK: - Сохранение на диск

    /// Тренировка переживает выгрузку приложения: iOS закрывает свёрнутые
    /// приложения, пока человек отдыхает между подходами с другим приложением.
    private static var saveURL: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("active-workout.json")
    }

    /// Запись откладывается до конца текущего прохода: ввод веса меняет
    /// состояние на каждую цифру, писать файл на каждую незачем.
    private func save() {
        guard !saveScheduled else { return }
        saveScheduled = true
        DispatchQueue.main.async { [weak self] in
            self?.saveScheduled = false
            self?.writeNow()
        }
    }

    /// Немедленная запись — при уходе приложения в фон.
    func writeNow() {
        guard let workout = activeWorkout, let profileID else { return }
        let session = SavedSession(
            profileID: profileID,
            workout: workout,
            startDate: startDate,
            exercises: exercises,
            noteText: noteText,
            snapshot: startSnapshot
        )
        guard let data = try? JSONEncoder().encode(session) else { return }
        let url = Self.saveURL
        try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? data.write(to: url, options: .atomic)
    }

    func clearSaved() {
        try? FileManager.default.removeItem(at: Self.saveURL)
    }

    /// Для тестов: забыть тренировку в памяти, не трогая файл, — как после выгрузки.
    func forgetForTesting() {
        startSnapshot = nil
        profileID = nil
        activeWorkout = nil
        isPresented = false
        exercises = []
    }

    /// Поднимает сохранённую тренировку этого профиля. Экран открывается
    /// свёрнутым: внизу появляется плашка «продолжить».
    func restoreIfNeeded(for profile: ProgramProfile) {
        guard activeWorkout == nil,
              let data = try? Data(contentsOf: Self.saveURL),
              let session = try? JSONDecoder().decode(SavedSession.self, from: data),
              session.profileID == profile.profileID
        else { return }
        // Тренировку, начатую больше суток назад, не поднимаем: её забыли.
        guard Date().timeIntervalSince(session.startDate) < 24 * 3600 else {
            clearSaved()
            return
        }
        activeWorkout = session.workout
        profileID = session.profileID
        startDate = session.startDate
        exercises = session.exercises
        startSnapshot = session.snapshot
        noteText = session.noteText
        isPresented = false
    }
}
