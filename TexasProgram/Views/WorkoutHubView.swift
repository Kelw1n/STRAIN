import SwiftUI

struct WorkoutHubView: View {
    @Bindable var profile: ProgramProfile
    let onStartWorkout: (ScheduledWorkout) -> Void
    var onOpenBench: ((Int) -> Void)?

    @State private var showingGoalSheet = false
    @State private var showingStreak = false
    @State private var showingProgramCatalog = false
    @State private var showingCustomizing = false

    var body: some View {
        // Расписание — один раз за проход: каждое обращение к profile.schedule
        // заново раскладывает весь план по датам.
        let schedule = profile.schedule
        let focus = schedule.focus
        return NavigationStack {
            ZStack(alignment: .bottom) {
                ScrollView {
                    VStack(spacing: 16) {
                        // Карточка 1: Серия активностей — по нажатию календарь месяца
                        Button {
                            UIImpactFeedbackGenerator(style: .light).impactOccurred()
                            showingStreak = true
                        } label: {
                            activityStreakCard
                        }
                        .buttonStyle(.pressable)

                        // Карточки 2 и 3: Цель и Программы
                        HStack(spacing: 12) {
                            // Карточка «Цель»
                            Button {
                                showingGoalSheet = true
                            } label: {
                                goalCard(schedule: schedule, focus: focus)
                            }
                            .buttonStyle(.pressable)

                            // Карточка «Программы»
                            Button {
                                showingProgramCatalog = true
                            } label: {
                                programsCard
                            }
                            .buttonStyle(.pressable)
                        }

                        // Карточка сегодняшней программы
                        if let focus {
                            todaysWorkoutPreview(workout: focus)
                        } else {
                            restDayCard
                        }

                        Spacer(minLength: 130) // Место под кнопку «НАЧАТЬ ТРЕНИРОВКУ» и Liquid Glass Dock
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 12)
                }
                .scrollIndicators(.hidden)
                .screenBackground()

                // Плавающая кнопка «НАЧАТЬ ТРЕНИРОВКУ» / «ПРОДОЛЖИТЬ»
                if let focus {
                    let isRunning = WorkoutSessionManager.shared.isRunning
                    VStack(spacing: 0) {
                        Button {
                            UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
                            if isRunning {
                                WorkoutSessionManager.shared.expand()
                            } else {
                                onStartWorkout(focus)
                            }
                        } label: {
                            HStack(spacing: 8) {
                                if isRunning {
                                    Circle()
                                        .fill(Color.green)
                                        .frame(width: 8, height: 8)
                                    Text("ПРОДОЛЖИТЬ ТРЕНИРОВКУ")
                                } else {
                                    Text("НАЧАТЬ ТРЕНИРОВКУ")
                                }
                            }
                            .font(.system(size: 16, weight: .black, design: Theme.fontDesign))
                            .tracking(1.2)
                            .foregroundStyle(.black)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 18)
                            .background(Color.white, in: Capsule())
                            .shadow(color: Color.white.opacity(0.18), radius: 14, y: 4)
                            .contentTransition(.opacity)
                        }
                        .buttonStyle(.pressable)
                        .animation(Motion.snappy, value: isRunning)
                        .padding(.horizontal, 18)
                        .padding(.bottom, 74) // Зазор для Liquid Glass Dock
                    }
                }
            }
            .navigationTitle("Тренировка")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showingGoalSheet = true
                    } label: {
                        Image(systemName: "figure.run.circle")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundStyle(.white)
                    }
                }
            }
            .sheet(isPresented: $showingGoalSheet) {
                GoalSettingsSheet(profile: profile)
            }
            .sheet(isPresented: $showingStreak) {
                StreakCalendarSheet(profile: profile)
            }
            .sheet(isPresented: $showingProgramCatalog, onDismiss: {
                WorkoutSessionManager.shared.representAfterSheet()
            }) {
                PlanView(profile: profile, onOpenBench: onOpenBench)
            }
            // Тренировку начали из дня в «Программах» — лист закрываем, иначе
            // полноэкранная тренировка под ним так и не покажется.
            .onChange(of: WorkoutSessionManager.shared.isPresented) { _, presented in
                if presented { showingProgramCatalog = false }
            }
            .onChange(of: WorkoutSessionManager.shared.showingReplaceConfirm) { _, showing in
                if showing { showingProgramCatalog = false }
            }
        }
        .preferredColorScheme(.dark)
    }

    // MARK: - Карточка «Серия активностей» (Скриншот 4)

    private var activityStreakCard: some View {
        // Серия по календарю, как в листе: неделя с тренировкой засчитана.
        let streak = (current: profile.calendarStreak().weeks, best: 0)
        return VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text("Серия активностей")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(.white)
                Spacer()
                Text("Этот месяц")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Color.white.opacity(0.45))
                Image(systemName: "chevron.right")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(Color.white.opacity(0.3))
            }

            HStack(alignment: .center, spacing: 20) {
                // Иконка огня и недели
                VStack(spacing: 2) {
                    Image(systemName: "flame.fill")
                        .font(.system(size: 38))
                        .foregroundStyle(streak.current > 0 ? AnyShapeStyle(Theme.accent) : AnyShapeStyle(Color.white.opacity(0.2)))

                    Text("\(streak.current)")
                        .font(.system(size: 20, weight: .black, design: Theme.fontDesign))
                        .foregroundStyle(.white)

                    Text(streakWord(streak.current))
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(Color.white.opacity(0.45))
                }
                .frame(width: 80)

                // Сетка точек месяца (4 недели по 7 дней)
                monthDotsMatrix
            }

            Text(streak.current > 0 ? "Серия активна! Продолжай в том же духе." : "Запиши тренировку, чтобы начать серию")
                .font(.system(size: 12))
                .foregroundStyle(Color.white.opacity(0.45))
        }
        .padding(18)
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    /// Календарь текущего месяца, как у REBRN: столбцы — дни недели с понедельника,
    /// строки — недели. Закрытые дни залиты акцентом, прошедшие — серым,
    /// будущие тренировочные обведены акцентом, сегодня подчёркнуто.
    private var monthDotsMatrix: some View {
        let calendar = WorkoutScheduler.trainingCalendar()
        let today = calendar.startOfDay(for: Date())
        let done = Set(profile.completionLog.map { calendar.startOfDay(for: $0.date) })
        let trainingWeekdays = Set(profile.weekdays)
        let rows = Self.monthRows(containing: today, calendar: calendar)

        return VStack(spacing: 7) {
            ForEach(rows.indices, id: \.self) { row in
                HStack(spacing: 7) {
                    ForEach(0..<7, id: \.self) { column in
                        MonthDot(
                            date: rows[row][column],
                            today: today,
                            isDone: rows[row][column].map { done.contains($0) } ?? false,
                            isTrainingDay: rows[row][column].map {
                                trainingWeekdays.contains(calendar.component(.weekday, from: $0))
                            } ?? false
                        )
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .trailing)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Тренировок в этом месяце: \(rows.joined().compactMap { $0 }.filter { done.contains($0) }.count)")
    }

    /// Дни месяца по неделям; пустые клетки до первого и после последнего числа — `nil`.
    static func monthRows(containing date: Date, calendar: Calendar) -> [[Date?]] {
        guard let month = calendar.dateInterval(of: .month, for: date),
              let days = calendar.range(of: .day, in: .month, for: date) else { return [] }
        let firstWeekday = calendar.component(.weekday, from: month.start)
        let leading = (firstWeekday - calendar.firstWeekday + 7) % 7
        var cells: [Date?] = Array(repeating: nil, count: leading)
        for offset in 0..<days.count {
            cells.append(calendar.date(byAdding: .day, value: offset, to: month.start))
        }
        while cells.count % 7 != 0 { cells.append(nil) }
        return stride(from: 0, to: cells.count, by: 7).map { Array(cells[$0..<$0 + 7]) }
    }

    private func streakWord(_ count: Int) -> String {
        let tens = count % 100
        if (11...14).contains(tens) { return "недель" }
        switch count % 10 {
        case 1: return "неделя"
        case 2...4: return "недели"
        default: return "недель"
        }
    }

    // MARK: - Карточки «Цель» и «Программы»

    private func goalCard(schedule: WorkoutSchedule, focus: ScheduledWorkout?) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Цель")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(.white)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(Color.white.opacity(0.3))
            }

            // Как у REBRN: кольцо из стольких частей, сколько тренировок в неделе;
            // каждая сделанная закрашивает одну. С понедельника — снова с нуля.
            let goal = profile.weekGoal()
            ZStack {
                SegmentedRing(total: goal.target, filled: goal.done)
                    .frame(width: 48, height: 48)

                Image(systemName: goal.done >= goal.target ? "checkmark" : "calendar")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(goal.done >= goal.target ? Theme.accent : Color.white.opacity(0.85))
                    .contentTransition(.symbolEffect(.replace))
            }
            .padding(.vertical, 2)
            .animation(Motion.snappy, value: goal.done)

            VStack(alignment: .leading, spacing: 2) {
                Text(goal.done >= goal.target ? "Цель недели есть" : "На этой неделе")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                Text("\(min(goal.done, 99)) из \(goal.target) \(RuPlural.word(goal.target, "тренировки", "тренировок", "тренировок"))")
                    .font(.system(size: 12))
                    .foregroundStyle(Color.white.opacity(0.45))
                    .lineLimit(1)
                    .contentTransition(.numericText())
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private var programsCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Программы")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(.white)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(Color.white.opacity(0.3))
            }

            ZStack {
                Circle()
                    .fill(Color.white.opacity(0.12))
                    .frame(width: 44, height: 44)

                Image(systemName: "square.stack.3d.up.fill")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(Color.white.opacity(0.85))
            }
            .padding(.vertical, 4)

            VStack(alignment: .leading, spacing: 2) {
                Text(profile.programKind.rawValue)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                Text("Свои и каталог")
                    .font(.system(size: 12))
                    .foregroundStyle(Color.white.opacity(0.45))
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    // MARK: - Превью сегодняшней тренировки

    private func todaysWorkoutPreview(workout: ScheduledWorkout) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text("СЕГОДНЯШНИЙ ПЛАН")
                        .font(.system(size: 11, weight: .bold))
                        .tracking(1.2)
                        .foregroundStyle(Theme.accent)
                    Text(workout.fullTitle)
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(.white)
                }
                Spacer()
            }

            let exercises = profile.exercises(for: workout)
            VStack(spacing: 8) {
                ForEach(exercises) { ex in
                    HStack(spacing: 12) {
                        Image(systemName: "dumbbell.fill")
                            .font(.system(size: 13))
                            .foregroundStyle(Theme.accent)
                            .frame(width: 24)

                        Text(ex.name)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(.white)
                            .lineLimit(1)

                        Spacer()

                        Text(ex.sets > 0 ? "\(ex.sets) × \(ex.reps)" : ex.reps)
                            .font(.system(size: 13, weight: .bold, design: Theme.fontDesign))
                            .foregroundStyle(Color.white.opacity(0.65))
                    }
                    .padding(.vertical, 4)
                }
            }
        }
        .padding(18)
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private var restDayCard: some View {
        VStack(spacing: 12) {
            Image(systemName: "moon.zzz.fill")
                .font(.system(size: 32))
                .foregroundStyle(Theme.accent)
            Text("Сегодня отдых")
                .font(.system(size: 17, weight: .bold))
                .foregroundStyle(.white)
            Text("Восстановление — неотъемлемая часть прогресса.")
                .font(.system(size: 13))
                .foregroundStyle(Color.white.opacity(0.5))
                .multilineTextAlignment(.center)
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
}

/// Кольцо цели недели: по части на тренировку, сделанные — акцентом.
struct SegmentedRing: View {
    let total: Int
    let filled: Int
    var lineWidth: CGFloat = 4

    var body: some View {
        let count = max(total, 1)
        // Зазор между частями — в долях окружности; у одной части зазора нет.
        let gap = count > 1 ? 0.035 : 0
        ZStack {
            ForEach(0..<count, id: \.self) { index in
                let start = Double(index) / Double(count) + gap / 2
                let end = Double(index + 1) / Double(count) - gap / 2
                Circle()
                    .trim(from: start, to: max(start, end))
                    .stroke(index < filled ? Theme.accent : Color.white.opacity(0.14),
                            style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                    .rotationEffect(.degrees(-90))
            }
        }
        .padding(lineWidth / 2)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Сделано \(min(filled, count)) из \(count)")
    }
}

/// Клетка календаря серии.
private struct MonthDot: View {
    let date: Date?
    let today: Date
    let isDone: Bool
    let isTrainingDay: Bool

    var body: some View {
        ZStack {
            if let date {
                if isDone {
                    Circle().fill(Theme.accent)
                } else if date < today {
                    Circle().fill(Color.white.opacity(0.16))
                } else {
                    Circle().strokeBorder(
                        isTrainingDay ? Theme.accent : Color.white.opacity(0.18),
                        lineWidth: 1.6
                    )
                }
            }
        }
        .frame(width: 11, height: 11)
        .overlay(alignment: .bottom) {
            if date == today {
                Capsule()
                    .fill(Color.white)
                    .frame(width: 8, height: 2)
                    .offset(y: 5)
            }
        }
    }
}
