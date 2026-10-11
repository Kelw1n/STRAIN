import SwiftUI

struct WorkoutHubView: View {
    @Bindable var profile: ProgramProfile
    let onStartWorkout: (ScheduledWorkout) -> Void
    var onOpenBench: ((Int) -> Void)?

    @State private var showingGoalSheet = false
    @State private var showingProgramCatalog = false
    @State private var showingCustomizing = false

    private var schedule: WorkoutSchedule { profile.schedule }
    private var focus: ScheduledWorkout? { schedule.focus }

    var body: some View {
        NavigationStack {
            ZStack(alignment: .bottom) {
                ScrollView {
                    VStack(spacing: 16) {
                        // Карточка 1: Серия активностей (Streak Grid)
                        activityStreakCard

                        // Карточки 2 и 3: Цель и Программы
                        HStack(spacing: 12) {
                            // Карточка «Цель»
                            Button {
                                showingGoalSheet = true
                            } label: {
                                goalCard
                            }
                            .buttonStyle(.plain)

                            // Карточка «Программы»
                            Button {
                                showingProgramCatalog = true
                            } label: {
                                programsCard
                            }
                            .buttonStyle(.plain)
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
                .background(Color(red: 0.05, green: 0.05, blue: 0.06).ignoresSafeArea())

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
                        }
                        .buttonStyle(.plain)
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
        let streak = profile.weekStreak
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
        .background(Color(white: 0.11), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private var monthDotsMatrix: some View {
        let completedSet = Set(profile.completionLog.map { Calendar.current.startOfDay(for: $0.date) })
        let today = Calendar.current.startOfDay(for: Date())

        return HStack(spacing: 7) {
            ForEach(0..<4, id: \.self) { weekOffset in
                VStack(spacing: 7) {
                    ForEach(0..<7, id: \.self) { dayOffset in
                        let daysAgo = (3 - weekOffset) * 7 + (6 - dayOffset)
                        let targetDate = Calendar.current.date(byAdding: .day, value: -daysAgo, to: today) ?? today
                        let isDone = completedSet.contains(targetDate)
                        let isToday = targetDate == today

                        Circle()
                            .fill(isDone ? Theme.accent : Color.clear)
                            .overlay(
                                Circle()
                                    .strokeBorder(
                                        isToday ? Color.white : (isDone ? Theme.accent : Color.white.opacity(0.18)),
                                        lineWidth: isToday ? 2 : 1.2
                                    )
                            )
                            .frame(width: 10, height: 10)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .trailing)
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

    private var goalCard: some View {
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

            ZStack {
                Circle()
                    .strokeBorder(Color.white.opacity(0.12), lineWidth: 3)
                    .frame(width: 44, height: 44)

                Image(systemName: "calendar")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(Color.white.opacity(0.85))
            }
            .padding(.vertical, 4)

            VStack(alignment: .leading, spacing: 2) {
                Text(focus.map { schedule.relativeTitle(for: $0) } ?? "Программа пройдена")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(.white)
                Text("Тренировка в \(String(format: "%02d:%02d", profile.reminderHour, profile.reminderMinute))")
                    .font(.system(size: 12))
                    .foregroundStyle(Color.white.opacity(0.45))
                    .lineLimit(1)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(white: 0.11), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
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
        .background(Color(white: 0.11), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
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
        .background(Color(white: 0.11), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
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
        .background(Color(white: 0.11), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
}
