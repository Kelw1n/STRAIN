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
                        // Идентичность по упражнению, а не по номеру — иначе перестановка
                        // перерисовала бы карточки на месте вместо того, чтобы их подвинуть.
                        ForEach(Array(sessionManager.exercises.enumerated()), id: \.element.id) { exIndex, _ in
                            exerciseCard(at: exIndex)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 12)
                    .padding(.bottom, restTimer.isRunning ? 200 : 60)
                }
                .scrollIndicators(.hidden)
                .scrollDismissesKeyboard(.interactively)
                .background(Color.black.ignoresSafeArea())

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
                // Как у REBRN: свернуть и крупный секундомер слева, действия справа.
                ToolbarItem(placement: .topBarLeading) {
                    HStack(spacing: 12) {
                        Button {
                            UIImpactFeedbackGenerator(style: .light).impactOccurred()
                            sessionManager.minimize()
                        } label: {
                            Image(systemName: "chevron.down")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundStyle(.white)
                                .frame(width: 36, height: 36)
                                .background(Color.white.opacity(0.12), in: Circle())
                        }
                        .accessibilityLabel("Свернуть тренировку")

                        Text(formatTimer(sessionManager.elapsedSeconds))
                            .font(.system(size: 26, weight: .heavy, design: Theme.fontDesign))
                            .monospacedDigit()
                            .foregroundStyle(.white)
                            .contentTransition(.numericText())
                            .accessibilityLabel("Время тренировки \(formatTimer(sessionManager.elapsedSeconds))")
                    }
                }

                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("Готово") {
                        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                    }
                    .font(.body.weight(.bold))
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
                                .frame(width: 36, height: 36)
                                .background(Color.white.opacity(0.12), in: Circle())
                        }

                        Button {
                            handleFinishTapped()
                        } label: {
                            Text("Завершить")
                                .font(.system(size: 15, weight: .bold, design: Theme.fontDesign))
                                .foregroundStyle(.black)
                                .padding(.horizontal, 18)
                                .frame(height: 36)
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
                                .font(.system(size: 13, weight: .bold, design: Theme.fontDesign))
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
                        // Порядок меняется только в этой тренировке, план остаётся прежним.
                        if exIndex > 0 {
                            Button {
                                withAnimation(Motion.snappy) { sessionManager.moveExercise(from: exIndex, by: -1) }
                            } label: {
                                Label("Переместить выше", systemImage: "arrow.up")
                            }
                        }
                        if exIndex < sessionManager.exercises.count - 1 {
                            Button {
                                withAnimation(Motion.snappy) { sessionManager.moveExercise(from: exIndex, by: 1) }
                            } label: {
                                Label("Переместить ниже", systemImage: "arrow.down")
                            }
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
                            .frame(width: 38, alignment: .center)

                        Text("БЫЛО")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(Color.white.opacity(0.4))
                            .frame(width: 62, alignment: .center)

                        Text("КГ")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(Color.white.opacity(0.4))
                            .frame(maxWidth: .infinity, alignment: .center)

                        Text("ПОВТ")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(Color.white.opacity(0.4))
                            .frame(maxWidth: .infinity, alignment: .center)

                        Text("")
                            .frame(width: 40)
                    }

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
            .background(Color(white: 0.086), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        }
    }

    // MARK: - Строка подхода

    @ViewBuilder
    private func setRow(exerciseIndex exIdx: Int, setIndex sIdx: Int) -> some View {
        if exIdx < sessionManager.exercises.count && sIdx < sessionManager.exercises[exIdx].sets.count {
            let currentSet = sessionManager.exercises[exIdx].sets[sIdx]
            let isCurrent = !currentSet.isDone && sessionManager.exercises[exIdx].sets.prefix(sIdx).allSatisfy(\.isDone)

            HStack(spacing: 8) {
                // Номер в тёмном кружке; текущий подход отмечен оранжевой чертой у края.
                Text("\(sIdx + 1)")
                    .font(.system(size: 15, weight: .bold, design: Theme.fontDesign))
                    .monospacedDigit()
                    .foregroundStyle(.white)
                    .frame(width: 34, height: 34)
                    .background(Color(white: 0.15), in: Circle())
                    .frame(width: 38)
                    .overlay(alignment: .leading) {
                        Capsule()
                            .fill(Theme.accent)
                            .frame(width: 3, height: 22)
                            .offset(x: -16)
                            .opacity(isCurrent ? 1 : 0)
                    }

                // «БЫЛО»
                Text(currentSet.previousText)
                    .font(.system(size: 12, weight: .medium, design: Theme.fontDesign))
                    .foregroundStyle(Color.white.opacity(0.4))
                    .frame(width: 62, alignment: .center)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)

                // «КГ» и «ПОВТ» вводятся с клавиатуры, как у REBRN.
                SetNumberField(
                    value: currentSet.weight,
                    isDecimal: true,
                    isDone: currentSet.isDone,
                    accessibilityName: "Вес, подход \(sIdx + 1)"
                ) { value in
                    sessionManager.setWeight(value, exIdx: exIdx, sIdx: sIdx, profile: profile)
                }

                SetNumberField(
                    value: Double(currentSet.reps),
                    isDecimal: false,
                    isDone: currentSet.isDone,
                    accessibilityName: "Повторы, подход \(sIdx + 1)"
                ) { value in
                    sessionManager.setReps(Int(value), exIdx: exIdx, sIdx: sIdx, profile: profile)
                }

                // Отметка выполнения: оранжевый кружок с галочкой.
                Button {
                    sessionManager.toggleSetDone(exIdx: exIdx, sIdx: sIdx, profile: profile, restTimer: restTimer)
                } label: {
                    ZStack {
                        Circle()
                            .strokeBorder(Color.white.opacity(0.35), lineWidth: 2)
                            .opacity(currentSet.isDone ? 0 : 1)
                        Circle()
                            .fill(Theme.accent)
                            .scaleEffect(currentSet.isDone ? 1 : 0.4)
                            .opacity(currentSet.isDone ? 1 : 0)
                        Image(systemName: "checkmark")
                            .font(.system(size: 14, weight: .heavy))
                            .foregroundStyle(.white)
                            .scaleEffect(currentSet.isDone ? 1 : 0.4)
                            .opacity(currentSet.isDone ? 1 : 0)
                    }
                    .frame(width: 30, height: 30)
                    .frame(width: 40, height: 44)
                    .contentShape(Rectangle())
                    .animation(.spring(response: 0.3, dampingFraction: 0.6), value: currentSet.isDone)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(currentSet.isDone ? "Подход \(sIdx + 1) выполнен" : "Отметить подход \(sIdx + 1)")
            }
        }
    }

    // MARK: - Иконка/превью движения

    private func exerciseThumbnail(for prescription: ExercisePrescription) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color(white: 0.85))
                .frame(width: 48, height: 48)

            Image(systemName: iconName(for: prescription.name))
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(Color(white: 0.35))
        }
    }

    private func iconName(for name: String) -> String {
        let lower = name.lowercased()
        if lower.contains("присед") { return "figure.strengthtraining.functional" }
        if lower.contains("жим") { return "figure.strengthtraining.traditional" }
        if lower.contains("подтяг") { return "figure.cross.training" }
        if lower.contains("тяг") { return "figure.stand" }
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
                        // Пустой текст — тоже ответ: заметку стёрли, её надо удалить.
                        profile.setNote(
                            sessionManager.noteText,
                            week: workout.week,
                            day: workout.day.number
                        )
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
                            .font(.system(size: 13, weight: .bold, design: Theme.fontDesign))
                            .foregroundStyle(.white)
                            .lineLimit(1)

                        Text("\(sessionManager.totalCompletedSets) из \(sessionManager.totalSetsCount) \(RuPlural.word(sessionManager.totalSetsCount, "подхода", "подходов", "подходов"))")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundStyle(Color.white.opacity(0.55))
                    }

                    Spacer(minLength: 8)

                    // Таймер
                    HStack(spacing: 5) {
                        Image(systemName: "timer")
                            .font(.system(size: 11, weight: .semibold))
                        Text(formatTimer(sessionManager.elapsedSeconds))
                            .font(.system(size: 13, weight: .bold, design: Theme.fontDesign))
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

// MARK: - Поле веса / повторов

/// Ячейка подхода с вводом с клавиатуры.
///
/// Текст живёт в своём состоянии, а не вычисляется из модели: иначе стёртое
/// поле сразу заполнялось бы старым числом и вписать новое было нельзя.
/// В модель уходит каждое число, которое удалось разобрать.
private struct SetNumberField: View {
    let value: Double
    let isDecimal: Bool
    let isDone: Bool
    let accessibilityName: String
    let onCommit: (Double) -> Void

    @State private var text = ""
    @FocusState private var focused: Bool

    private var shape: RoundedRectangle { RoundedRectangle(cornerRadius: 12, style: .continuous) }

    var body: some View {
        TextField("0", text: $text)
            .keyboardType(isDecimal ? .decimalPad : .numberPad)
            .multilineTextAlignment(.center)
            .font(.system(size: 17, weight: .bold, design: Theme.fontDesign))
            .monospacedDigit()
            // План — приглушённым, факт — белым, как у REBRN.
            .foregroundStyle(isDone || focused ? Color.white : Color.white.opacity(0.6))
            .focused($focused)
            .frame(maxWidth: .infinity, minHeight: 44)
            .background(Color(white: focused ? 0.19 : 0.14), in: shape)
            .overlay(shape.strokeBorder(Theme.accent, lineWidth: 1.5).opacity(focused ? 1 : 0))
            .animation(.easeOut(duration: 0.15), value: focused)
            .accessibilityLabel(accessibilityName)
            .onAppear { text = Self.format(value, decimal: isDecimal) }
            .onChange(of: value) { _, newValue in
                if !focused { text = Self.format(newValue, decimal: isDecimal) }
            }
            .onChange(of: text) { _, newText in
                if let parsed = Self.parse(newText, decimal: isDecimal), parsed != value { onCommit(parsed) }
            }
            .onChange(of: focused) { _, isFocused in
                // Ушли из поля пустым — показываем то, что в модели.
                if !isFocused { text = Self.format(value, decimal: isDecimal) }
            }
    }

    private static func format(_ value: Double, decimal: Bool) -> String {
        decimal ? WeightFormat.plain(value) : String(Int(value))
    }

    private static func parse(_ text: String, decimal: Bool) -> Double? {
        let cleaned = text.replacingOccurrences(of: ",", with: ".").trimmingCharacters(in: .whitespaces)
        guard !cleaned.isEmpty, let number = Double(cleaned), number.isFinite, number >= 0 else { return nil }
        return decimal ? min(number, 1000) : min(number.rounded(.down), 999)
    }
}
