import SwiftUI
import Combine

// MARK: - Экран активной тренировки

struct ActiveWorkoutSessionView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(RestTimer.self) private var restTimer
    @Bindable var profile: ProgramProfile
    let workout: ScheduledWorkout

    @Bindable var sessionManager = WorkoutSessionManager.shared

    var body: some View {
        NavigationStack {
            ZStack(alignment: .bottom) {
                // Основной контент тренировки
                ScrollView {
                    LazyVStack(spacing: 16) {
                        ForEach(Array(sessionManager.exercises.indices), id: \.self) { exIndex in
                            exerciseCard(at: exIndex)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 12)
                    .padding(.bottom, restTimer.isRunning ? 200 : 60)
                }
                .scrollIndicators(.hidden)
                .background(Color(red: 0.05, green: 0.05, blue: 0.06).ignoresSafeArea())

                // Карточка отдыха в стиле REBRN
                if restTimer.isRunning {
                    RebrnRestTimerCard()
                        .padding(.bottom, 16)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }

                // Всплывающее окно подтверждения при 0 отмеченных подходах или отмене
                if sessionManager.showingZeroSetsAlert {
                    zeroSetsAlertOverlay
                        .transition(.opacity.combined(with: .scale(scale: 0.95)))
                }
            }
            .animation(.spring(response: 0.35, dampingFraction: 0.8), value: restTimer.isRunning)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        sessionManager.minimize()
                    } label: {
                        Image(systemName: "chevron.down")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(.white)
                            .padding(8)
                            .background(Color.white.opacity(0.12), in: Circle())
                    }
                    .accessibilityLabel("Свернуть тренировку")
                }

                ToolbarItem(placement: .principal) {
                    HStack(spacing: 8) {
                        Text(formatTimer(sessionManager.elapsedSeconds))
                            .font(.system(size: 20, weight: .bold, design: .rounded))
                            .monospacedDigit()
                            .foregroundStyle(.white)
                    }
                }

                ToolbarItem(placement: .topBarTrailing) {
                    HStack(spacing: 10) {
                        Menu {
                            Button {
                                sessionManager.showingNoteSheet = true
                            } label: {
                                Label("Заметка к тренировке", systemImage: "note.text")
                            }

                            Button(role: .destructive) {
                                sessionManager.showingZeroSetsAlert = true
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
            .sheet(isPresented: $sessionManager.showingSummary) {
                WorkoutCompletionSummaryView(profile: profile, workout: workout)
            }
            .sheet(isPresented: $sessionManager.showingNoteSheet) {
                workoutNoteSheet
            }
            .confirmationDialog("Время отдыха", isPresented: Binding(
                get: { sessionManager.customRestTargetExerciseIndex != nil },
                set: { if !$0 { sessionManager.customRestTargetExerciseIndex = nil } }
            )) {
                Button("1 мин (01:00)") { sessionManager.setRestDuration(60) }
                Button("1.5 мин (01:30)") { sessionManager.setRestDuration(90) }
                Button("2 мин (02:00)") { sessionManager.setRestDuration(120) }
                Button("2.5 мин (02:30)") { sessionManager.setRestDuration(150) }
                Button("3 мин (03:00)") { sessionManager.setRestDuration(180) }
                Button("4 мин (04:00)") { sessionManager.setRestDuration(240) }
            }
        }
        .preferredColorScheme(.dark)
    }

    // MARK: - Карточка упражнения

    @ViewBuilder
    private func exerciseCard(at exIndex: Int) -> some View {
        if exIndex < sessionManager.exercises.count {
            let exerciseState = sessionManager.exercises[exIndex]

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
                        sessionManager.customRestTargetExerciseIndex = exIndex
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
                            sessionManager.addSet(to: exIndex, profile: profile)
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
                        sessionManager.addSet(to: exIndex, profile: profile)
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
    }

    // MARK: - Строка подхода

    @ViewBuilder
    private func setRow(exerciseIndex exIdx: Int, setIndex sIdx: Int) -> some View {
        if exIdx < sessionManager.exercises.count && sIdx < sessionManager.exercises[exIdx].sets.count {
            let currentSet = sessionManager.exercises[exIdx].sets[sIdx]
            let isCurrent = !currentSet.isDone && sessionManager.exercises[exIdx].sets.prefix(sIdx).allSatisfy(\.isDone)

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
                    Button("+5 кг") { sessionManager.adjustWeight(exIdx: exIdx, sIdx: sIdx, delta: 5) }
                    Button("+2.5 кг") { sessionManager.adjustWeight(exIdx: exIdx, sIdx: sIdx, delta: 2.5) }
                    Button("+1 кг") { sessionManager.adjustWeight(exIdx: exIdx, sIdx: sIdx, delta: 1) }
                    Divider()
                    Button("-1 кг") { sessionManager.adjustWeight(exIdx: exIdx, sIdx: sIdx, delta: -1) }
                    Button("-2.5 кг") { sessionManager.adjustWeight(exIdx: exIdx, sIdx: sIdx, delta: -2.5) }
                    Button("-5 кг") { sessionManager.adjustWeight(exIdx: exIdx, sIdx: sIdx, delta: -5) }
                } label: {
                    Text(WeightFormat.kilogramsPrecise(currentSet.weight))
                        .font(.system(size: 15, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(Color(white: 0.16), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                }

                // «ПОВТ»
                Menu {
                    Button("+1 повт") { sessionManager.adjustReps(exIdx: exIdx, sIdx: sIdx, delta: 1) }
                    Button("+2 повт") { sessionManager.adjustReps(exIdx: exIdx, sIdx: sIdx, delta: 2) }
                    Divider()
                    Button("-1 повт") { sessionManager.adjustReps(exIdx: exIdx, sIdx: sIdx, delta: -1) }
                    Button("-2 повт") { sessionManager.adjustReps(exIdx: exIdx, sIdx: sIdx, delta: -2) }
                } label: {
                    Text("\(currentSet.reps)")
                        .font(.system(size: 15, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(Color(white: 0.16), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                }

                // Чекбокс выполнения подхода (в стиле REBRN)
                Button {
                    sessionManager.toggleSetDone(exIdx: exIdx, sIdx: sIdx, profile: profile, restTimer: restTimer)
                } label: {
                    ZStack {
                        Circle()
                            .fill(currentSet.isDone ? Color.cyan : Color.clear)
                            .strokeBorder(currentSet.isDone ? Color.cyan : Color.white.opacity(0.3), lineWidth: 2)
                            .frame(width: 28, height: 28)

                        if currentSet.isDone {
                            Image(systemName: "checkmark")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundStyle(.black)
                        }
                    }
                    .frame(width: 38, height: 38)
                }
                .buttonStyle(.plain)
            }
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

    // MARK: - Окно «Ни одного подхода не отмечено» / Отмена тренировки

    private var zeroSetsAlertOverlay: some View {
        ZStack {
            Color.black.opacity(0.78)
                .ignoresSafeArea()
                .onTapGesture {
                    sessionManager.showingZeroSetsAlert = false
                }

            VStack(spacing: 18) {
                // Красная мусорка
                Image(systemName: "trash")
                    .font(.system(size: 44, weight: .semibold))
                    .foregroundStyle(Color.red)
                    .padding(.top, 8)

                Text(sessionManager.totalCompletedSets == 0 ? "Ни одного подхода не отмечено" : "Отменить тренировку?")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)

                Text("Тренировка удалится вместе с введёнными значениями и счётчиком времени.")
                    .font(.system(size: 14))
                    .foregroundStyle(Color.white.opacity(0.65))
                    .multilineTextAlignment(.center)
                    .lineSpacing(3)
                    .padding(.horizontal, 10)

                // Слайдер «>>> УДАЛИТЬ»
                SlideToConfirmView(title: "УДАЛИТЬ") {
                    sessionManager.discardWorkout(profile: profile)
                }
                .padding(.top, 8)

                // Кнопка возврата
                Button {
                    sessionManager.showingZeroSetsAlert = false
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


    // MARK: - Заметка

    private var workoutNoteSheet: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                TextEditor(text: $sessionManager.noteText)
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
                        if !sessionManager.noteText.isEmpty {
                            profile.setNote(
                                sessionManager.noteText,
                                week: workout.week,
                                day: workout.day.number
                            )
                        }
                        sessionManager.showingNoteSheet = false
                    }
                    .font(.body.weight(.bold))
                }
            }
        }
    }

    // MARK: - Вспомогательная логика

    private func handleFinishTapped() {
        if sessionManager.totalCompletedSets == 0 {
            sessionManager.showingZeroSetsAlert = true
        } else {
            sessionManager.showingSummary = true
        }
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

// MARK: - Свернутая панель активной тренировки (Liquid Glass Mini-Player)

struct MinimizedWorkoutBar: View {
    @Bindable var sessionManager = WorkoutSessionManager.shared
    @Bindable var profile: ProgramProfile

    var body: some View {
        guard let workout = sessionManager.activeWorkout else { return AnyView(EmptyView()) }

        return AnyView(
            Button {
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                sessionManager.expand()
            } label: {
                HStack(spacing: 12) {
                    // Пульсирующая зеленая точка
                    ZStack {
                        Circle()
                            .fill(Color.green.opacity(0.35))
                            .frame(width: 22, height: 22)
                        Circle()
                            .fill(Color.green)
                            .frame(width: 8, height: 8)
                    }

                    VStack(alignment: .leading, spacing: 2) {
                        Text(workout.fullTitle)
                            .font(.system(size: 13, weight: .bold, design: .rounded))
                            .foregroundStyle(.white)
                            .lineLimit(1)

                        Text("\(sessionManager.totalCompletedSets) из \(sessionManager.totalSetsCount) подходов")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundStyle(Color.white.opacity(0.55))
                    }

                    Spacer(minLength: 8)

                    // Таймер
                    HStack(spacing: 5) {
                        Image(systemName: "timer")
                            .font(.system(size: 11, weight: .semibold))
                        Text(formatTimer(sessionManager.elapsedSeconds))
                            .font(.system(size: 13, weight: .bold, design: .rounded))
                            .monospacedDigit()
                    }
                    .foregroundStyle(Theme.accent)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Theme.accent.opacity(0.15), in: Capsule())

                    // Кнопка развернуть
                    Image(systemName: "chevron.up")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(Color.white.opacity(0.7))
                        .padding(6)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background {
                    Capsule(style: .continuous)
                        .fill(.ultraThinMaterial)
                        .overlay {
                            Capsule(style: .continuous)
                                .fill(Color(red: 0.10, green: 0.10, blue: 0.12).opacity(0.88))
                        }
                        .overlay {
                            Capsule(style: .continuous)
                                .strokeBorder(
                                    LinearGradient(
                                        colors: [Color.white.opacity(0.28), Color.white.opacity(0.06)],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    ),
                                    lineWidth: 1
                                )
                        }
                        .shadow(color: Color.black.opacity(0.55), radius: 12, y: 4)
                }
                .padding(.horizontal, 16)
            }
            .buttonStyle(.plain)
        )
    }

    private func formatTimer(_ seconds: Int) -> String {
        let mins = seconds / 60
        let secs = seconds % 60
        return String(format: "%02d:%02d", mins, secs)
    }
}
