import SwiftUI
import Combine

// MARK: - Модели состояния активной тренировки

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

// MARK: - Экран активной тренировки

struct ActiveWorkoutSessionView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(RestTimer.self) private var restTimer
    @Bindable var profile: ProgramProfile
    let workout: ScheduledWorkout

    @State private var exercises: [ActiveExerciseState] = []
    @State private var startDate = Date()
    @State private var elapsedSeconds = 0
    @State private var timerCancellable: AnyCancellable?
    @State private var showingZeroSetsAlert = false
    @State private var showingSummary = false
    @State private var showingNoteSheet = false
    @State private var noteText = ""
    @State private var customRestTargetExerciseIndex: Int?

    var body: some View {
        NavigationStack {
            ZStack {
                // Основной контент тренировки
                ScrollView {
                    LazyVStack(spacing: 16) {
                        ForEach(Array(exercises.indices), id: \.self) { exIndex in
                            exerciseCard(at: exIndex)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 12)
                    .padding(.bottom, 60)
                }
                .scrollIndicators(.hidden)
                .background(Color(red: 0.05, green: 0.05, blue: 0.06).ignoresSafeArea())

                // Всплывающее окно подтверждения при 0 отмеченных подходах (как в REBRN)
                if showingZeroSetsAlert {
                    zeroSetsAlertOverlay
                        .transition(.opacity.combined(with: .scale(scale: 0.95)))
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        if totalCompletedSets == 0 {
                            showingZeroSetsAlert = true
                        } else {
                            dismiss()
                        }
                    } label: {
                        Image(systemName: "chevron.down")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(.white)
                            .padding(8)
                            .background(Color.white.opacity(0.12), in: Circle())
                    }
                }

                ToolbarItem(placement: .principal) {
                    HStack(spacing: 8) {
                        Text(formatTimer(elapsedSeconds))
                            .font(.system(size: 20, weight: .bold, design: .rounded))
                            .monospacedDigit()
                            .foregroundStyle(.white)
                    }
                }

                ToolbarItem(placement: .topBarTrailing) {
                    HStack(spacing: 10) {
                        Menu {
                            Button {
                                showingNoteSheet = true
                            } label: {
                                Label("Заметка к тренировке", systemImage: "note.text")
                            }

                            Button(role: .destructive) {
                                showingZeroSetsAlert = true
                            } label: {
                                Label("Отменить тренировку", systemImage: "trash")
                            }
                        } label: {
                            Image(systemName: "ellipsis")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundStyle(.white)
                                .padding(8)
                                .background(Color.white.opacity(0.12), in: Circle())
                        }

                        Button {
                            handleFinishTapped()
                        } label: {
                            Text("Завершить")
                                .font(.system(size: 14, weight: .bold, design: .rounded))
                                .foregroundStyle(.black)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                                .background(Color.white, in: Capsule())
                        }
                    }
                }
            }
            .sheet(isPresented: $showingSummary) {
                workoutSummarySheet
            }
            .sheet(isPresented: $showingNoteSheet) {
                workoutNoteSheet
            }
            .confirmationDialog("Время отдыха", isPresented: Binding(
                get: { customRestTargetExerciseIndex != nil },
                set: { if !$0 { customRestTargetExerciseIndex = nil } }
            )) {
                Button("1 мин (01:00)") { setRestDuration(60) }
                Button("1.5 мин (01:30)") { setRestDuration(90) }
                Button("2 мин (02:00)") { setRestDuration(120) }
                Button("2.5 мин (02:30)") { setRestDuration(150) }
                Button("3 мин (03:00)") { setRestDuration(180) }
                Button("4 мин (04:00)") { setRestDuration(240) }
            }
        }
        .preferredColorScheme(.dark)
        .onAppear {
            setupInitialExercises()
            startStopwatch()
        }
        .onDisappear {
            stopStopwatch()
        }
    }

    // MARK: - Карточка упражнения

    @ViewBuilder
    private func exerciseCard(at exIndex: Int) -> some View {
        let exerciseState = exercises[exIndex]

        VStack(alignment: .leading, spacing: 14) {
            // Шапка упражнения
            HStack(spacing: 12) {
                Image(systemName: "circle.grid.2x3.fill")
                    .font(.system(size: 14))
                    .foregroundStyle(Color.white.opacity(0.25))

                exerciseThumbnail(for: exerciseState.prescription)

                VStack(alignment: .leading, spacing: 3) {
                    Text(exerciseState.prescription.name)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.white)
                        .lineLimit(2)
                }

                Spacer(minLength: 4)

                // Кнопка времени отдыха
                Button {
                    customRestTargetExerciseIndex = exIndex
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: "timer")
                            .font(.system(size: 12, weight: .bold))
                        Text(formatRestTime(exerciseState.restSeconds))
                            .font(.system(size: 13, weight: .bold, design: .rounded))
                            .monospacedDigit()
                    }
                    .foregroundStyle(Color.white.opacity(0.85))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Color.white.opacity(0.10), in: Capsule())
                }

                Menu {
                    Button {
                        addSet(to: exIndex)
                    } label: {
                        Label("Добавить подход", systemImage: "plus")
                    }
                } label: {
                    Image(systemName: "ellipsis")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Color.white.opacity(0.5))
                        .frame(width: 28, height: 28)
                }
            }

            // Таблица подходов
            VStack(spacing: 8) {
                // Заголовки колонок
                HStack(spacing: 8) {
                    Text("#")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Color.white.opacity(0.4))
                        .frame(width: 28, alignment: .center)

                    Text("БЫЛО")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Color.white.opacity(0.4))
                        .frame(width: 66, alignment: .center)

                    Text("КГ")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Color.white.opacity(0.4))
                        .frame(maxWidth: .infinity, alignment: .center)

                    Text("ПОВТ")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Color.white.opacity(0.4))
                        .frame(maxWidth: .infinity, alignment: .center)

                    Text("")
                        .frame(width: 38)
                }
                .padding(.horizontal, 4)

                // Строки подходов
                ForEach(Array(exerciseState.sets.indices), id: \.self) { setIndex in
                    setRow(exerciseIndex: exIndex, setIndex: setIndex)
                }

                // Кнопка «+ Добавить подход»
                Button {
                    addSet(to: exIndex)
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "plus")
                            .font(.system(size: 13, weight: .bold))
                        Text("Добавить подход")
                            .font(.system(size: 14, weight: .semibold))
                    }
                    .foregroundStyle(Color.white.opacity(0.85))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 11)
                    .background(Color(white: 0.14), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .padding(.top, 4)
            }
        }
        .padding(16)
        .background(Color(white: 0.09), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .strokeBorder(Color.white.opacity(0.06), lineWidth: 1)
        )
    }

    // MARK: - Строка подхода

    @ViewBuilder
    private func setRow(exerciseIndex exIdx: Int, setIndex sIdx: Int) -> some View {
        let currentSet = exercises[exIdx].sets[sIdx]
        let isCurrent = !currentSet.isDone && exercises[exIdx].sets.prefix(sIdx).allSatisfy(\.isDone)

        HStack(spacing: 8) {
            // Индикатор активного подхода + Номер
            HStack(spacing: 4) {
                RoundedRectangle(cornerRadius: 2)
                    .fill(isCurrent ? Theme.accent : Color.clear)
                    .frame(width: 3, height: 16)

                Text("\(sIdx + 1)")
                    .font(.system(size: 14, weight: .bold, design: .rounded))
                    .foregroundStyle(isCurrent ? Theme.accent : Color.white.opacity(0.75))
            }
            .frame(width: 28, alignment: .center)

            // «БЫЛО»
            Text(currentSet.previousText)
                .font(.system(size: 13, weight: .medium, design: .rounded))
                .foregroundStyle(Color.white.opacity(0.45))
                .frame(width: 66, alignment: .center)
                .lineLimit(1)

            // «КГ» (Интерактивное поле / Степер)
            Menu {
                Button("+5 кг") { adjustWeight(exIdx: exIdx, sIdx: sIdx, delta: 5) }
                Button("+2.5 кг") { adjustWeight(exIdx: exIdx, sIdx: sIdx, delta: 2.5) }
                Button("+1 кг") { adjustWeight(exIdx: exIdx, sIdx: sIdx, delta: 1) }
                Divider()
                Button("-1 кг") { adjustWeight(exIdx: exIdx, sIdx: sIdx, delta: -1) }
                Button("-2.5 кг") { adjustWeight(exIdx: exIdx, sIdx: sIdx, delta: -2.5) }
                Button("-5 кг") { adjustWeight(exIdx: exIdx, sIdx: sIdx, delta: -5) }
            } label: {
                Text(WeightFormat.kilogramsPrecise(currentSet.weight))
                    .font(.system(size: 16, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 38)
                    .background(Color(white: 0.16), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            }
            .buttonStyle(.plain)

            // «ПОВТ» (Интерактивное поле / Степер)
            Menu {
                Button("+2 повт") { adjustReps(exIdx: exIdx, sIdx: sIdx, delta: 2) }
                Button("+1 повт") { adjustReps(exIdx: exIdx, sIdx: sIdx, delta: 1) }
                Divider()
                Button("-1 повт") { adjustReps(exIdx: exIdx, sIdx: sIdx, delta: -1) }
                Button("-2 повт") { adjustReps(exIdx: exIdx, sIdx: sIdx, delta: -2) }
            } label: {
                Text("\(currentSet.reps)")
                    .font(.system(size: 16, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 38)
                    .background(Color(white: 0.16), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            }
            .buttonStyle(.plain)

            // Чекбокс отметки подхода (Оранжевый круг с галочкой)
            Button {
                toggleSetDone(exIdx: exIdx, sIdx: sIdx)
            } label: {
                ZStack {
                    if currentSet.isDone {
                        Circle()
                            .fill(Theme.accent)
                            .frame(width: 32, height: 32)
                        Image(systemName: "checkmark")
                            .font(.system(size: 14, weight: .black))
                            .foregroundStyle(.white)
                    } else {
                        Circle()
                            .strokeBorder(Color.white.opacity(0.3), lineWidth: 2.2)
                            .frame(width: 32, height: 32)
                    }
                }
                .frame(width: 38)
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: - Иконка/превью движения

    private func exerciseThumbnail(for prescription: ExercisePrescription) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 11, style: .continuous)
                .fill(Color(white: 0.18))
                .frame(width: 44, height: 44)

            Image(systemName: iconName(for: prescription.name))
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(Theme.accent)
        }
    }

    private func iconName(for name: String) -> String {
        let lower = name.lowercased()
        if lower.contains("присед") { return "figure.strengthtraining.functional" }
        if lower.contains("жим") { return "figure.strengthtraining.traditional" }
        if lower.contains("тяг") { return "figure.stand" }
        if lower.contains("подтяг") { return "figure.cross.training" }
        return "dumbbell.fill"
    }

    // MARK: - Окно «Ни одного подхода не отмечено» (Скриншот 2)

    private var zeroSetsAlertOverlay: some View {
        ZStack {
            Color.black.opacity(0.78)
                .ignoresSafeArea()
                .onTapGesture {
                    showingZeroSetsAlert = false
                }

            VStack(spacing: 18) {
                // Красная мусорка
                Image(systemName: "trash")
                    .font(.system(size: 44, weight: .semibold))
                    .foregroundStyle(Color.red)
                    .padding(.top, 8)

                Text("Ни одного подхода не отмечено")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)

                Text("Отметь выполненные подходы галочкой — иначе сохранять нечего. Тренировка удалится вместе с введёнными значениями.")
                    .font(.system(size: 14))
                    .foregroundStyle(Color.white.opacity(0.65))
                    .multilineTextAlignment(.center)
                    .lineSpacing(3)
                    .padding(.horizontal, 10)

                // Слайдер «>>> УДАЛИТЬ»
                SlideToConfirmView(title: "УДАЛИТЬ") {
                    dismiss()
                }
                .padding(.top, 8)

                // Кнопка возврата
                Button {
                    showingZeroSetsAlert = false
                } label: {
                    Text("Вернуться к тренировке")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Color.white.opacity(0.85))
                        .padding(.vertical, 8)
                }
            }
            .padding(24)
            .background(Color(white: 0.12), in: RoundedRectangle(cornerRadius: 26, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 26, style: .continuous)
                    .strokeBorder(Color.white.opacity(0.1), lineWidth: 1)
            )
            .padding(.horizontal, 28)
        }
    }

    // MARK: - Экран завершения тренировки

    private var workoutSummarySheet: some View {
        NavigationStack {
            VStack(spacing: 24) {
                Spacer()

                // Праздничная анимация
                Image(systemName: "trophy.fill")
                    .font(.system(size: 64))
                    .foregroundStyle(Theme.accentGradient)
                    .shadow(color: Theme.accent.opacity(0.4), radius: 16)

                VStack(spacing: 6) {
                    Text("Тренировка завершена!")
                        .font(.system(size: 26, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                    Text(workout.fullTitle)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }

                // Статистика сессии
                HStack(spacing: 16) {
                    summaryStatCard(title: "Время", value: formatDuration(elapsedSeconds))
                    summaryStatCard(title: "Объём", value: "\(Int(totalTonnage)) кг")
                    summaryStatCard(title: "Плотность", value: "\(Int(density)) кг/мин")
                }
                .padding(.horizontal, 16)

                Spacer()

                Button {
                    saveAndFinishWorkout()
                } label: {
                    Text("Сохранить и закрыть")
                        .font(.headline.weight(.bold))
                        .foregroundStyle(.black)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(Color.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 16)
            }
            .background(Color(red: 0.05, green: 0.05, blue: 0.06).ignoresSafeArea())
        }
    }

    private func summaryStatCard(title: String, value: String) -> some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.system(size: 18, weight: .bold, design: .rounded))
                .foregroundStyle(.white)
            Text(title)
                .font(.caption2.weight(.medium))
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .background(Color(white: 0.12), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    // MARK: - Заметка

    private var workoutNoteSheet: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                TextEditor(text: $noteText)
                    .padding(12)
                    .background(Color(white: 0.12), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .scrollContentBackground(.hidden)
                    .foregroundStyle(.white)

                Spacer()
            }
            .padding()
            .background(Color(red: 0.05, green: 0.05, blue: 0.06).ignoresSafeArea())
            .navigationTitle("Заметка к тренировке")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Готово") {
                        if !noteText.isEmpty {
                            profile.setNote(
                                noteText,
                                week: workout.week,
                                day: workout.day.number
                            )
                        }
                        showingNoteSheet = false
                    }
                    .font(.body.weight(.bold))
                }
            }
        }
    }

    // MARK: - Вспомогательная логика

    private var totalCompletedSets: Int {
        exercises.flatMap(\.sets).filter(\.isDone).count
    }

    private var totalTonnage: Double {
        exercises.flatMap(\.sets)
            .filter(\.isDone)
            .reduce(0.0) { $0 + ($1.weight * Double($1.reps)) }
    }

    private var density: Double {
        let mins = max(1.0, Double(elapsedSeconds) / 60.0)
        return totalTonnage / mins
    }

    private func handleFinishTapped() {
        if totalCompletedSets == 0 {
            showingZeroSetsAlert = true
        } else {
            showingSummary = true
        }
    }

    private func saveAndFinishWorkout() {
        // Записываем выполненные подходы в SwiftData профиль
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

        // Помечаем тренировочный день завершённым
        if !workout.isCompleted {
            profile.toggleCompleted(week: workout.week, day: workout.day.number)
        }

        UINotificationFeedbackGenerator().notificationOccurred(.success)
        dismiss()
    }

    private func toggleSetDone(exIdx: Int, sIdx: Int) {
        exercises[exIdx].sets[sIdx].isDone.toggle()
        let isDone = exercises[exIdx].sets[sIdx].isDone

        if isDone {
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            let currentSet = exercises[exIdx].sets[sIdx]
            let ex = exercises[exIdx]

            profile.recordSet(
                week: workout.week,
                day: workout.day.number,
                exercise: ex.prescription,
                index: currentSet.index,
                reps: currentSet.reps,
                weight: currentSet.weight
            )

            // Автоматический запуск таймера отдыха
            restTimer.start(TimeInterval(ex.restSeconds))
        } else {
            let currentSet = exercises[exIdx].sets[sIdx]
            let ex = exercises[exIdx]
            profile.clearSetEntry(
                week: workout.week,
                day: workout.day.number,
                exercise: ex.prescription,
                index: currentSet.index
            )
        }
    }

    private func addSet(to exIdx: Int) {
        let existingSets = exercises[exIdx].sets
        let lastWeight = existingSets.last?.weight ?? 50.0
        let lastReps = existingSets.last?.reps ?? 10
        let newIndex = existingSets.count

        let newSet = ActiveSetState(
            index: newIndex,
            previousText: previousPerformance(for: exercises[exIdx].prescription, setIndex: newIndex),
            weight: lastWeight,
            reps: lastReps,
            isDone: false
        )
        exercises[exIdx].sets.append(newSet)
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    private func adjustWeight(exIdx: Int, sIdx: Int, delta: Double) {
        exercises[exIdx].sets[sIdx].weight = max(0, exercises[exIdx].sets[sIdx].weight + delta)
    }

    private func adjustReps(exIdx: Int, sIdx: Int, delta: Int) {
        exercises[exIdx].sets[sIdx].reps = max(1, exercises[exIdx].sets[sIdx].reps + delta)
    }

    private func setRestDuration(_ seconds: Int) {
        if let idx = customRestTargetExerciseIndex {
            exercises[idx].restSeconds = seconds
        }
    }

    private func setupInitialExercises() {
        let prescs = profile.exercises(for: workout)
        exercises = prescs.map { presc in
            let defaultRest = presc.benchSession != nil ? 180 : profile.defaultRestSeconds
            let plannedWeight = extractPlannedWeight(for: presc)
            let plannedReps = extractPlannedReps(for: presc)
            let setsCount = max(presc.sets, 3)

            let sets = (0..<setsCount).map { sIdx in
                let logged = profile.setEntry(
                    week: workout.week,
                    day: workout.day.number,
                    exercise: presc,
                    index: sIdx
                )
                let isDone = sIdx < profile.completedSets(
                    week: workout.week,
                    day: workout.day.number,
                    exercise: presc
                )

                return ActiveSetState(
                    index: sIdx,
                    previousText: previousPerformance(for: presc, setIndex: sIdx),
                    weight: logged?.weight ?? plannedWeight,
                    reps: logged?.reps ?? plannedReps,
                    isDone: isDone
                )
            }

            return ActiveExerciseState(
                prescription: presc,
                restSeconds: defaultRest,
                sets: sets
            )
        }
    }

    private func previousPerformance(for presc: ExercisePrescription, setIndex: Int) -> String {
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

    private func extractPlannedWeight(for presc: ExercisePrescription) -> Double {
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

    private func startStopwatch() {
        timerCancellable = Timer.publish(every: 1, on: .main, in: .common)
            .autoconnect()
            .sink { _ in
                elapsedSeconds += 1
            }
    }

    private func stopStopwatch() {
        timerCancellable?.cancel()
        timerCancellable = nil
    }

    private func formatTimer(_ seconds: Int) -> String {
        let mins = seconds / 60
        let secs = seconds % 60
        return String(format: "%02d:%02d", mins, secs)
    }

    private func formatRestTime(_ seconds: Int) -> String {
        let mins = seconds / 60
        let secs = seconds % 60
        return String(format: "%02d:%02d", mins, secs)
    }

    private func formatDuration(_ seconds: Int) -> String {
        let hours = seconds / 3600
        let mins = (seconds % 3600) / 60
        if hours > 0 {
            return "\(hours)ч \(mins)м"
        } else {
            return "\(mins) мин"
        }
    }
}
