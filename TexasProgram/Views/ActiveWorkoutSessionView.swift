import SwiftUI

// MARK: - Экран активной тренировки

struct ActiveWorkoutSessionView: View {
    @Environment(RestTimer.self) private var restTimer
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Bindable var profile: ProgramProfile
    let workout: ScheduledWorkout

    @Bindable var sessionManager = WorkoutSessionManager.shared
    @State private var reorder = ExerciseReorder()

    var body: some View {
        NavigationStack {
            ZStack(alignment: .bottom) {
                ScrollViewReader { proxy in
                    ScrollView {
                        VStack(spacing: 16) {
                            // Совместная тренировка: что сейчас делает напарник.
                            PartnerStrip()
                            exerciseList
                        }
                            .padding(.horizontal, 16)
                            .padding(.top, 12)
                            .padding(.bottom, restTimer.isRunning ? 200 : 60)
                    }
                    .scrollIndicators(.hidden)
                    .scrollDismissesKeyboard(.interactively)
                    // Пока упражнение тащат, лента не должна уезжать из-под пальца.
                    .scrollDisabled(reorder.isActive)
                    .onChange(of: reorder.draggingID) { _, id in
                        guard let id else { return }
                        withAnimation(Motion.sheet) { proxy.scrollTo(id, anchor: .center) }
                    }
                }
                .background(Color.black.ignoresSafeArea())

                // Карточка отдыха в стиле REBRN
                if restTimer.isRunning {
                    RebrnRestTimerCard()
                        .padding(.bottom, 16)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }

                if sessionManager.showingZeroSetsAlert {
                    zeroSetsAlertOverlay
                        .transition(.opacity.combined(with: .scale(scale: 0.95)))
                }
            }
            .overlay(alignment: .top) { recordBanner }
            .animation(Motion.sheet, value: restTimer.isRunning)
            .animation(Motion.sheet, value: sessionManager.showingZeroSetsAlert)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { toolbar }
            .sheet(isPresented: $sessionManager.showingSummary) {
                WorkoutCompletionSummaryView(profile: profile, workout: workout)
            }
            .sheet(isPresented: $sessionManager.showingNoteSheet) {
                workoutNoteSheet
            }
            .sheet(item: $sessionManager.artTarget) { target in
                ExerciseArtSheet(name: target.name)
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

    // MARK: - Лента упражнений

    /// Обычный `VStack`, не ленивый: при перестановке карточкам нужны настоящие
    /// места, а упражнений в тренировке — единицы.
    private var exerciseList: some View {
        VStack(spacing: reorder.isActive ? ExerciseReorder.spacing : 16) {
            ForEach(Array(sessionManager.exercises.enumerated()), id: \.element.id) { index, exercise in
                let dragged = reorder.draggingID == exercise.id
                ExerciseSessionCard(
                    exercise: exercise,
                    index: index,
                    count: sessionManager.exercises.count,
                    profile: profile,
                    compact: reorder.isActive,
                    lifted: dragged,
                    reorder: reorder
                )
                .id(exercise.id)
                .offset(y: reorder.offset(for: exercise.id, index: index))
                .scaleEffect(dragged && !reduceMotion ? 1.03 : 1)
                .shadow(color: .black.opacity(dragged ? 0.6 : 0), radius: 18, y: 10)
                .zIndex(dragged ? 1 : 0)
            }
        }
    }

    // MARK: - Шапка

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
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
                .buttonStyle(.pressable)
                .accessibilityLabel("Свернуть тренировку")

                SessionClock(size: 26)
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
                .buttonStyle(.pressable)
            }
        }
    }

    // MARK: - Плашка рекорда

    @ViewBuilder
    private var recordBanner: some View {
        if let record = sessionManager.lastRecord {
            HStack(spacing: 10) {
                Image(systemName: "trophy.fill")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(.black)
                    .frame(width: 30, height: 30)
                    .background(Theme.accent, in: Circle())
                VStack(alignment: .leading, spacing: 1) {
                    Text("Новый рекорд")
                        .font(.system(size: 14, weight: .heavy, design: Theme.fontDesign))
                        .foregroundStyle(.white)
                    Text("\(record.exercise) · \(WeightFormat.kilogramsPrecise(record.weight))")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(.white.opacity(0.65))
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(Color(white: 0.12), in: Capsule())
            .overlay(Capsule().strokeBorder(Theme.accent.opacity(0.6), lineWidth: 1))
            .shadow(color: Theme.accent.opacity(0.35), radius: 16, y: 6)
            .padding(.horizontal, 24)
            .padding(.top, 4)
            .id(record.id)
            .transition(.move(edge: .top).combined(with: .opacity).combined(with: .scale(scale: 0.9, anchor: .top)))
            .task(id: record.id) {
                try? await Task.sleep(for: .seconds(2.6))
                withAnimation(Motion.sheet) {
                    if sessionManager.lastRecord?.id == record.id { sessionManager.lastRecord = nil }
                }
            }
            .onTapGesture {
                withAnimation(Motion.sheet) { sessionManager.lastRecord = nil }
            }
        }
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
            .background(Color.black.ignoresSafeArea())
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
}

// MARK: - Перестановка упражнений

/// Перетаскивание упражнения пальцем.
///
/// Долгое нажатие на шапку сворачивает все карточки до строк одной высоты:
/// так место, куда встанет упражнение, считается делением, а не обмером
/// карточек разной высоты. Пока палец ведёт, массив не трогаем — соседи
/// расступаются смещением; на отпускании строка доезжает до места,
/// и только тогда порядок меняется без анимации — картинка при этом
/// не дёргается, потому что всё уже стоит там, где окажется.
@Observable
final class ExerciseReorder {
    static let rowHeight: CGFloat = 76
    static let spacing: CGFloat = 10
    private var step: CGFloat { Self.rowHeight + Self.spacing }

    private(set) var draggingID: String?
    private(set) var startIndex = 0
    private(set) var target = 0
    private(set) var translation: CGFloat = 0
    @ObservationIgnored private var ending = false

    var isActive: Bool { draggingID != nil }

    func begin(id: String, index: Int) {
        guard draggingID == nil, !ending else { return }
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
        withAnimation(Motion.sheet) {
            draggingID = id
            startIndex = index
            target = index
            translation = 0
        }
    }

    func update(translation value: CGFloat, count: Int) {
        guard draggingID != nil, !ending else { return }
        translation = value
        let next = min(max(startIndex + Int((value / step).rounded()), 0), max(count - 1, 0))
        if next != target {
            UISelectionFeedbackGenerator().selectionChanged()
            withAnimation(Motion.reorder) { target = next }
        }
    }

    func end() {
        guard draggingID != nil, !ending else { return }
        ending = true
        let from = startIndex, to = target
        withAnimation(Motion.reorder) {
            translation = CGFloat(to - from) * step
        } completion: {
            var still = Transaction()
            still.disablesAnimations = true
            withTransaction(still) {
                WorkoutSessionManager.shared.moveExercise(from: from, to: to)
                self.startIndex = to
                self.target = to
                self.translation = 0
            }
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            withAnimation(Motion.sheet) { self.draggingID = nil }
            self.ending = false
        }
    }

    /// Смещение карточки: схваченная едет за пальцем, соседи уступают место.
    func offset(for id: String, index: Int) -> CGFloat {
        guard let draggingID else { return 0 }
        if id == draggingID { return translation }
        if startIndex < target, index > startIndex, index <= target { return -step }
        if target < startIndex, index >= target, index < startIndex { return step }
        return 0
    }
}

// MARK: - Карточка упражнения

/// Карточка отдельным вью со значениями на входе: SwiftUI перерисует только ту,
/// чьё упражнение изменилось, а не все сразу на каждую введённую цифру.
private struct ExerciseSessionCard: View {
    let exercise: ActiveExerciseState
    let index: Int
    let count: Int
    let profile: ProgramProfile
    let compact: Bool
    let lifted: Bool
    let reorder: ExerciseReorder

    @GestureState private var holding = false

    private var manager: WorkoutSessionManager { .shared }
    private var doneCount: Int { exercise.sets.filter(\.isDone).count }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            header
            if !compact {
                setsTable
                    .transition(.opacity.combined(with: .scale(scale: 0.98, anchor: .top)))
            }
        }
        .padding(compact ? 12 : 16)
        .frame(height: compact ? ExerciseReorder.rowHeight : nil, alignment: .top)
        .background(Color(white: lifted ? 0.12 : 0.086), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .strokeBorder(Theme.accent.opacity(lifted ? 0.7 : 0), lineWidth: 1.5)
        }
        .onChange(of: holding) { _, isHolding in
            // Жест отменила система (звонок, уведомление) — onEnded не придёт.
            if !isHolding { reorder.end() }
        }
    }

    // MARK: Шапка

    private var header: some View {
        HStack(spacing: 12) {
            HStack(spacing: 12) {
                Image(systemName: "line.3.horizontal")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Color.white.opacity(lifted ? 0.9 : 0.3))
                    .frame(width: 18)

                ExerciseThumb(name: exercise.prescription.name, size: compact ? 48 : 52)

                VStack(alignment: .leading, spacing: 3) {
                    Text(exercise.prescription.name)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.white)
                        .lineLimit(compact ? 1 : 2)
                        .minimumScaleFactor(0.85)
                    if !compact {
                        // Отдых — под названием, как в REBRN: справа он отнимал место,
                        // и «Приседания» рвались посреди слова.
                        restChip
                            .transition(.opacity)
                    }
                    if compact {
                        Text("\(doneCount) из \(RuPlural.sets(exercise.sets.count))")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(Color.white.opacity(0.45))
                            .transition(.opacity)
                    }
                }
                Spacer(minLength: 4)
            }
            .contentShape(Rectangle())
            .gesture(reorderGesture)
            .accessibilityElement(children: .combine)
            .accessibilityHint("Удерживайте, чтобы переставить")
            .accessibilityActions {
                if index > 0 {
                    Button("Переместить выше") { withAnimation(Motion.reorder) { manager.moveExercise(from: index, to: index - 1) } }
                }
                if index < count - 1 {
                    Button("Переместить ниже") { withAnimation(Motion.reorder) { manager.moveExercise(from: index, to: index + 1) } }
                }
            }

            if !compact {
                menu
                    .transition(.opacity)
            }
        }
    }

    /// Удержание 0,3 с, потом ведение. Короткое касание и прокрутка проходят мимо.
    private var reorderGesture: some Gesture {
        LongPressGesture(minimumDuration: 0.3)
            .sequenced(before: DragGesture(minimumDistance: 0, coordinateSpace: .global))
            .updating($holding) { value, state, _ in
                if case .second(true, _) = value { state = true }
            }
            .onChanged { value in
                guard case .second(true, let drag) = value else { return }
                if !reorder.isActive { reorder.begin(id: exercise.id, index: index) }
                if let drag { reorder.update(translation: drag.translation.height, count: count) }
            }
            .onEnded { _ in reorder.end() }
    }

    private var restChip: some View {
        Button {
            manager.customRestTargetExerciseIndex = index
        } label: {
            HStack(spacing: 5) {
                Image(systemName: "timer")
                    .font(.system(size: 12, weight: .bold))
                Text(String(format: "%02d:%02d", exercise.restSeconds / 60, exercise.restSeconds % 60))
                    .font(.system(size: 13, weight: .bold, design: Theme.fontDesign))
                    .monospacedDigit()
            }
            .foregroundStyle(Color.white.opacity(0.85))
            .padding(.horizontal, 9)
            .padding(.vertical, 4)
            .background(Color.white.opacity(0.10), in: Capsule())
        }
        .buttonStyle(.pressable)
        .accessibilityLabel("Отдых \(exercise.restSeconds / 60) минут \(exercise.restSeconds % 60) секунд")
    }

    private var menu: some View {
        Menu {
            Button {
                withAnimation(Motion.reorder) { manager.addSet(to: index, profile: profile) }
            } label: {
                Label("Добавить подход", systemImage: "plus")
            }
            Button {
                manager.artTarget = ExerciseArtTarget(name: exercise.prescription.name)
            } label: {
                Label("Картинка упражнения", systemImage: "photo")
            }
            // Шаг прибавки этого движения: у кого есть блины 1,25 — 2,5 кг,
            // у кого нет — 5; на блоке бывает и 1 кг.
            Menu {
                Picker("Шаг прибавки", selection: Binding(
                    get: { profile.weightStep(for: exercise.prescription.name) },
                    set: { step in
                        withAnimation(Motion.snappy) { manager.changeWeightStep(step, for: index, profile: profile) }
                    }
                )) {
                    ForEach(ProgramProfile.weightStepOptions, id: \.self) { option in
                        Text(ProgramProfile.weightStepTitle(option)).tag(option)
                    }
                }
            } label: {
                Label("Шаг прибавки: \(WeightFormat.kilogramsPrecise(profile.weightStep(for: exercise.prescription.name)))", systemImage: "scalemass")
            }
            if index > 0 {
                Button {
                    withAnimation(Motion.reorder) { manager.moveExercise(from: index, to: index - 1) }
                } label: {
                    Label("Переместить выше", systemImage: "arrow.up")
                }
            }
            if index < count - 1 {
                Button {
                    withAnimation(Motion.reorder) { manager.moveExercise(from: index, to: index + 1) }
                } label: {
                    Label("Переместить ниже", systemImage: "arrow.down")
                }
            }
        } label: {
            Image(systemName: "ellipsis")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Color.white.opacity(0.5))
                .frame(width: 30, height: 30)
                .contentShape(Rectangle())
        }
    }

    // MARK: Подходы

    private var setsTable: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                columnTitle("#").frame(width: 38)
                columnTitle("БЫЛО").frame(width: 62)
                // Поля ввода — в килограммах, как и вся арифметика программы.
                columnTitle("КГ").frame(maxWidth: .infinity)
                columnTitle("ПОВТ").frame(maxWidth: .infinity)
                Color.clear.frame(width: 40, height: 1)
            }

            ForEach(Array(exercise.sets.enumerated()), id: \.element.id) { sIdx, set in
                SetRowView(
                    set: set,
                    position: sIdx,
                    exerciseIndex: index,
                    isCurrent: !set.isDone && exercise.sets.prefix(sIdx).allSatisfy(\.isDone),
                    canDelete: exercise.sets.count > 1,
                    profile: profile
                )
                .transition(.asymmetric(
                    insertion: .opacity.combined(with: .move(edge: .top)),
                    removal: .opacity.combined(with: .scale(scale: 0.9))
                ))
            }

            Button {
                withAnimation(Motion.reorder) { manager.addSet(to: index, profile: profile) }
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
            .buttonStyle(.pressable)
            .padding(.top, 4)
        }
    }

    private func columnTitle(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 11, weight: .bold))
            .foregroundStyle(Color.white.opacity(0.4))
    }
}

// MARK: - Строка подхода

private struct SetRowView: View {
    let set: ActiveSetState
    let position: Int
    let exerciseIndex: Int
    let isCurrent: Bool
    let canDelete: Bool
    let profile: ProgramProfile

    @Environment(RestTimer.self) private var restTimer
    private var manager: WorkoutSessionManager { .shared }

    var body: some View {
        HStack(spacing: 8) {
            numberMenu

            // «БЫЛО»: нажал — подставилось то, что было в прошлый раз.
            Button {
                withAnimation(Motion.snappy) { manager.applyPrevious(exIdx: exerciseIndex, sIdx: position, profile: profile) }
            } label: {
                Text(set.previousText)
                    .font(.system(size: 12, weight: .medium, design: Theme.fontDesign))
                    .foregroundStyle(Color.white.opacity(set.previous == nil ? 0.25 : 0.45))
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                    .frame(width: 62, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.pressable)
            .disabled(set.previous == nil)
            .accessibilityLabel(set.previous == nil ? "Прошлого раза нет" : "Подставить прошлый раз: \(set.previousText)")

            SetNumberField(
                value: set.weight,
                isDecimal: true,
                isDone: set.isDone,
                accessibilityName: "Вес, подход \(position + 1)"
            ) { value in
                manager.setWeight(value, exIdx: exerciseIndex, sIdx: position, profile: profile)
            }

            SetNumberField(
                value: Double(set.reps),
                isDecimal: false,
                isDone: set.isDone,
                accessibilityName: "Повторы, подход \(position + 1)"
            ) { value in
                manager.setReps(Int(value), exIdx: exerciseIndex, sIdx: position, profile: profile)
            }

            checkButton
        }
    }

    /// Номер подхода: оранжевая черта у текущего, кубок у рекорда.
    /// Нажатие — меню подхода.
    private var numberMenu: some View {
        Menu {
            if set.previous != nil {
                Button {
                    withAnimation(Motion.snappy) { manager.applyPrevious(exIdx: exerciseIndex, sIdx: position, profile: profile) }
                } label: {
                    Label("Как в прошлый раз", systemImage: "arrow.uturn.backward")
                }
            }
            if canDelete {
                Button(role: .destructive) {
                    withAnimation(Motion.reorder) { manager.deleteSet(exIdx: exerciseIndex, sIdx: position, profile: profile) }
                } label: {
                    Label("Удалить подход", systemImage: "trash")
                }
            }
        } label: {
            Text("\(position + 1)")
                .font(.system(size: 15, weight: .bold, design: Theme.fontDesign))
                .monospacedDigit()
                .foregroundStyle(set.isRecord ? .black : .white)
                .frame(width: 34, height: 34)
                .background(set.isRecord ? AnyShapeStyle(Theme.accent) : AnyShapeStyle(Color(white: 0.15)), in: Circle())
                .overlay(alignment: .topTrailing) {
                    if set.isRecord {
                        Image(systemName: "trophy.fill")
                            .font(.system(size: 9, weight: .bold))
                            .foregroundStyle(.white)
                            .padding(3)
                            .background(Color.black, in: Circle())
                            .offset(x: 5, y: -5)
                            .transition(.scale.combined(with: .opacity))
                    }
                }
                .frame(width: 38, height: 44)
                .overlay(alignment: .leading) {
                    Capsule()
                        .fill(Theme.accent)
                        .frame(width: 3, height: 22)
                        .offset(x: -16)
                        .opacity(isCurrent ? 1 : 0)
                }
                .contentShape(Rectangle())
        }
        .animation(Motion.bouncy, value: set.isRecord)
        .animation(Motion.snappy, value: isCurrent)
        .accessibilityLabel("Подход \(position + 1)")
    }

    private var checkButton: some View {
        Button {
            withAnimation(Motion.bouncy) {
                manager.toggleSetDone(exIdx: exerciseIndex, sIdx: position, profile: profile, restTimer: restTimer)
            }
        } label: {
            ZStack {
                Circle()
                    .strokeBorder(Color.white.opacity(0.35), lineWidth: 2)
                    .opacity(set.isDone ? 0 : 1)
                Circle()
                    .fill(Theme.accent)
                    .scaleEffect(set.isDone ? 1 : 0.4)
                    .opacity(set.isDone ? 1 : 0)
                Image(systemName: "checkmark")
                    .font(.system(size: 14, weight: .heavy))
                    .foregroundStyle(.white)
                    .scaleEffect(set.isDone ? 1 : 0.4)
                    .opacity(set.isDone ? 1 : 0)
            }
            .frame(width: 30, height: 30)
            .frame(width: 40, height: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.pressable)
        .accessibilityLabel(set.isDone ? "Подход \(position + 1) выполнен" : "Отметить подход \(position + 1)")
    }
}

// MARK: - Секундомер

/// Секундомер тренировки. Тикает сам по себе через `TimelineView` — остальной
/// экран раз в секунду не перерисовывается.
struct SessionClock: View {
    var size: CGFloat = 26
    var color: Color = .white

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            let seconds = WorkoutSessionManager.shared.elapsed(at: context.date)
            Text(Self.format(seconds))
                .font(.system(size: size, weight: .heavy, design: Theme.fontDesign))
                .monospacedDigit()
                .foregroundStyle(color)
                .contentTransition(.numericText(countsDown: false))
                .animation(.snappy(duration: 0.25), value: seconds)
                .accessibilityLabel("Время тренировки \(Self.format(seconds))")
        }
    }

    static func format(_ seconds: Int) -> String {
        let hours = seconds / 3600, mins = (seconds % 3600) / 60, secs = seconds % 60
        return hours > 0 ? String(format: "%d:%02d:%02d", hours, mins, secs) : String(format: "%02d:%02d", mins, secs)
    }
}

// MARK: - Свернутая панель активной тренировки

struct MinimizedWorkoutBar: View {
    @Bindable var sessionManager = WorkoutSessionManager.shared
    @Bindable var profile: ProgramProfile
    @State private var pulse = false

    var body: some View {
        if let workout = sessionManager.activeWorkout {
            Button {
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                sessionManager.expand()
            } label: {
                HStack(spacing: 12) {
                    // Пульсирующая точка «идёт»
                    ZStack {
                        Circle()
                            .fill(Color.green.opacity(0.35))
                            .frame(width: 22, height: 22)
                            .scaleEffect(pulse ? 1.15 : 0.8)
                            .opacity(pulse ? 0.2 : 0.8)
                        Circle()
                            .fill(Color.green)
                            .frame(width: 8, height: 8)
                    }
                    .onAppear {
                        withAnimation(.easeInOut(duration: 1.1).repeatForever(autoreverses: true)) { pulse = true }
                    }

                    VStack(alignment: .leading, spacing: 2) {
                        Text(workout.fullTitle)
                            .font(.system(size: 13, weight: .bold, design: Theme.fontDesign))
                            .foregroundStyle(.white)
                            .lineLimit(1)

                        Text("\(sessionManager.totalCompletedSets) из \(sessionManager.totalSetsCount) \(RuPlural.word(sessionManager.totalSetsCount, "подхода", "подходов", "подходов"))")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundStyle(Color.white.opacity(0.55))
                            .contentTransition(.numericText())
                    }

                    Spacer(minLength: 8)

                    HStack(spacing: 5) {
                        Image(systemName: "timer")
                            .font(.system(size: 11, weight: .semibold))
                        SessionClock(size: 13, color: Theme.accent)
                    }
                    .foregroundStyle(Theme.accent)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Theme.accent.opacity(0.15), in: Capsule())

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
            .buttonStyle(.pressable)
        }
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
