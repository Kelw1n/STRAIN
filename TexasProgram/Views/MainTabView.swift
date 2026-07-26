import SwiftUI
import SwiftData

enum AppTab: Hashable {
    case today, plan, bench, progress, guide
}

struct MainTabView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Bindable var profile: ProgramProfile
    @State private var selectedTab: AppTab = .today
    @State private var showingSettings = false
    @State private var benchFocus: Int?
    let onResetApplication: () -> Void

    private var isUpperLower: Bool { profile.programKind == .upperLower }

    /// Переход в раздел «Жим 14» на конкретную тренировку волны.
    private var benchHandler: ((Int) -> Void)? {
        guard isUpperLower else { return nil }
        return { session in
            benchFocus = session
            withAnimation(Motion.maybe(Motion.snappy, reduce: reduceMotion)) { selectedTab = .bench }
        }
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            TodayView(
                profile: profile,
                onSettings: { showingSettings = true },
                onOpenBench: benchHandler
            )
            .tabItem { Label("Сегодня", systemImage: "bolt.fill") }
            .tag(AppTab.today)

            PlanView(profile: profile, onOpenBench: benchHandler)
                .tabItem { Label("План", systemImage: "square.stack.3d.up.fill") }
                .tag(AppTab.plan)

            if isUpperLower {
                BenchWaveView(profile: profile, focused: $benchFocus)
                    .tabItem { Label("Жим 14", systemImage: "waveform.path.ecg") }
                    .tag(AppTab.bench)
            }

            ProgressScreen(profile: profile)
                .tabItem { Label("Прогресс", systemImage: "chart.bar.xaxis") }
                .tag(AppTab.progress)

            InstructionsView(programKind: profile.programKind)
                .tabItem { Label("Инструкция", systemImage: "book.closed.fill") }
                .tag(AppTab.guide)
        }
        .tint(Theme.accent)
        .sheet(isPresented: $showingSettings) {
            SettingsView(profile: profile, onResetApplication: onResetApplication)
        }
    }
}

// MARK: - Сегодня

struct TodayView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Bindable var profile: ProgramProfile
    let onSettings: () -> Void
    var onOpenBench: ((Int) -> Void)?

    private var overallProgress: Double {
        guard profile.totalDays > 0 else { return 0 }
        return Double(profile.completedDayCount) / Double(profile.totalDays)
    }

    var body: some View {
        // Расписание считается один раз за проход body.
        let schedule = profile.schedule
        let focus = schedule.focus
        return NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 16) {
                    if let focus {
                        if schedule.todayAlreadyDone {
                            statusCard(
                                icon: "checkmark.circle.fill",
                                gradient: Theme.successGradient,
                                title: "Сегодня уже отмечено",
                                subtitle: dateLine(for: schedule.referenceDate)
                            ).appearIn(0)
                        } else if schedule.isRestDay {
                            statusCard(
                                icon: "moon.zzz.fill",
                                gradient: Theme.deepGradient,
                                title: "Сегодня отдых",
                                subtitle: dateLine(for: schedule.referenceDate)
                            ).appearIn(0)
                        }

                        hero(workout: focus, schedule: schedule).appearIn(1)

                        if !schedule.overdue.isEmpty {
                            overdueCard(schedule.overdue).appearIn(2)
                        }

                        if let onOpenBench, let session = focus.day.exercises.compactMap(\.benchSession).first {
                            BenchTeaser(profile: profile, session: session, action: { onOpenBench(session) })
                                .appearIn(3)
                        }

                        ForEach(Array(focus.day.exercises.enumerated()), id: \.element.id) { index, exercise in
                            ExerciseCard(exercise: exercise, onOpenBench: onOpenBench)
                                .appearIn(index + 4)
                                .softScroll()
                        }

                        CompleteButton(isCompleted: focus.isCompleted) {
                            withAnimation(Motion.maybe(Motion.bouncy, reduce: reduceMotion)) {
                                profile.toggleCompleted(week: focus.week, day: focus.day.number)
                            }
                        }
                        .padding(.top, 4)
                        .appearIn(focus.day.exercises.count + 4)
                    } else {
                        finished.appearIn(0)
                    }
                }
                .padding(.horizontal)
                .padding(.bottom, 28)
            }
            .scrollIndicators(.hidden)
            .screenBackground()
            .navigationTitle("Сегодня")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: onSettings) {
                        Image(systemName: "slider.horizontal.3").font(.body.weight(.semibold))
                    }
                    .buttonStyle(.pressable)
                    .accessibilityLabel("Настройки программы")
                }
            }
        }
    }

    private func dateLine(for date: Date) -> String {
        let calendar = WorkoutScheduler.trainingCalendar()
        let weekday = calendar.component(.weekday, from: date)
        return RuDate.full(weekday: weekday).capitalized + ", " + RuDate.dayMonth(date, calendar: calendar)
    }

    private func statusCard(icon: String, gradient: LinearGradient, title: String, subtitle: String) -> some View {
        CardView(padding: 16) {
            HStack(spacing: 14) {
                Image(systemName: icon)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 42, height: 42)
                    .background(gradient, in: RoundedRectangle(cornerRadius: 13, style: .continuous))
                VStack(alignment: .leading, spacing: 3) {
                    Text(title).font(.subheadline.weight(.bold))
                    Text(subtitle).font(.caption).foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
            }
        }
    }

    private func overdueCard(_ items: [ScheduledWorkout]) -> some View {
        CardView(padding: 16, spacing: 10) {
            Label("Пропущено на этой неделе", systemImage: "exclamationmark.arrow.circlepath")
                .font(.subheadline.weight(.bold))
                .foregroundStyle(Theme.warning)
            ForEach(items) { item in
                NavigationLink {
                    DayDetailView(profile: profile, week: item.week, day: item.day, onOpenBench: onOpenBench)
                } label: {
                    HStack(spacing: 10) {
                        Text(item.shortWeekday.uppercased())
                            .font(.caption.weight(.black))
                            .foregroundStyle(Theme.warning)
                            .frame(width: 26, alignment: .leading)
                        Text(item.day.title)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.primary)
                            .lineLimit(1)
                        Spacer(minLength: 4)
                        Text(RuDate.dayMonth(item.date))
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                        Image(systemName: "chevron.right").font(.caption2.weight(.bold)).foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 8)
                    .padding(.horizontal, 10)
                    .background(Theme.warning.opacity(0.09), in: RoundedRectangle(cornerRadius: 11, style: .continuous))
                }
                .buttonStyle(.pressable)
            }
        }
    }

    private func hero(workout: ScheduledWorkout, schedule: WorkoutSchedule) -> some View {
        HighlightCard {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 8) {
                    Text((schedule.relativeTitle(for: workout) + " · " + RuDate.dayMonth(workout.date)).uppercased())
                        .font(.caption2.weight(.bold))
                        .tracking(1.3)
                        .foregroundStyle(Theme.accentGradient)
                    Text("Неделя \(workout.week)")
                        .font(.system(size: 34, weight: .bold, design: .rounded))
                        .contentTransition(.numericText())
                    Text(profile.fullTitle(forDay: workout.day))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 12)
                RingProgress(value: overallProgress, lineWidth: 9) {
                    VStack(spacing: 0) {
                        Text("\(profile.completedDayCount)")
                            .font(.system(size: 20, weight: .bold, design: .rounded))
                            .contentTransition(.numericText())
                        Text("из \(profile.totalDays)").font(.system(size: 10)).foregroundStyle(.secondary)
                    }
                }
                .frame(width: 76, height: 76)
            }
        }
    }

    private var finished: some View {
        CardView(padding: 28) {
            VStack(spacing: 14) {
                Image(systemName: "checkmark.seal.fill")
                    .font(.system(size: 54))
                    .foregroundStyle(Theme.successGradient)
                    .symbolEffect(.pulse)
                Text("Цикл завершён").font(.title2.weight(.bold))
                Text(profile.programKind == .texas
                     ? "Все 12 недель отмечены. Можно запустить пикирование из настроек."
                     : "Все 7 недель программы «Верх / Низ» отмечены.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
        }
        .padding(.top, 40)
    }
}

// MARK: - Промо-карточка волны жима

struct BenchTeaser: View {
    let profile: ProgramProfile
    let session: Int
    let action: () -> Void

    private var plan: BenchSessionPlan? {
        profile.benchWave.first { $0.id == session }
    }

    private var isDone: Bool { profile.isBenchCompleted(session) }

    var body: some View {
        Button(action: action) {
            CardView(padding: 16) {
                HStack(spacing: 14) {
                    Image(systemName: isDone ? "checkmark" : "waveform.path.ecg")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: 42, height: 42)
                        .background(isDone ? Theme.successGradient : (plan?.accent ?? Theme.accentGradient),
                                    in: RoundedRectangle(cornerRadius: 13, style: .continuous))
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Жим 14 · тренировка \(session) из \(UpperLowerCalculator.benchSessionCount)")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.primary)
                        if isDone {
                            Text("Уже отмечена в разделе «Жим 14»")
                                .font(.caption)
                                .foregroundStyle(Theme.success)
                        } else if let plan {
                            Text("Верх до \(plan.topWeightText) · \(plan.sets.count) подходов")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    Spacer(minLength: 4)
                    Image(systemName: "chevron.right").font(.footnote.weight(.bold)).foregroundStyle(.secondary)
                }
            }
        }
        .buttonStyle(.pressable)
    }
}

// MARK: - План

struct PlanView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Bindable var profile: ProgramProfile
    var onOpenBench: ((Int) -> Void)?

    @State private var selectedWeek = 1
    @Namespace private var weekPill

    private var plan: WorkoutPlan { profile.workoutPlan }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 18) {
                    weekSelector
                    if let week = plan.weeks.first(where: { $0.number == selectedWeek }) {
                        VStack(spacing: 14) {
                            ForEach(Array(week.days.enumerated()), id: \.element.id) { index, day in
                                NavigationLink {
                                    DayDetailView(profile: profile, week: week.number, day: day, onOpenBench: onOpenBench)
                                } label: {
                                    DaySummaryCard(profile: profile, week: week.number, day: day)
                                }
                                .buttonStyle(.pressable)
                                .appearIn(index)
                                .softScroll()
                            }
                        }
                        .padding(.horizontal)
                        .id(selectedWeek)
                        .transition(.asymmetric(
                            insertion: .opacity.combined(with: .offset(y: 24)),
                            removal: .opacity.combined(with: .scale(scale: 0.97))
                        ))
                    }
                }
                .padding(.bottom, 28)
            }
            .scrollIndicators(.hidden)
            .screenBackground()
            .navigationTitle("План")
        }
    }

    private var weekSelector: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 9) {
                    ForEach(plan.weeks) { week in
                        weekChip(week)
                            .id(week.number)
                    }
                }
                .padding(.horizontal)
                .padding(.vertical, 6)
            }
            .onChange(of: selectedWeek) { _, newValue in
                withAnimation(Motion.maybe(Motion.smooth, reduce: reduceMotion)) {
                    proxy.scrollTo(newValue, anchor: .center)
                }
            }
        }
    }

    private func weekChip(_ week: WorkoutWeekPlan) -> some View {
        let selected = selectedWeek == week.number
        let done = week.days.allSatisfy { profile.isCompleted(week: week.number, day: $0.number) }
        return Button {
            withAnimation(Motion.maybe(Motion.snappy, reduce: reduceMotion)) { selectedWeek = week.number }
        } label: {
            VStack(spacing: 3) {
                Text("\(week.number)").font(.system(size: 17, weight: .bold, design: .rounded))
                if done {
                    Image(systemName: "checkmark").font(.system(size: 8, weight: .black))
                } else {
                    Circle().fill(Color.clear).frame(height: 8)
                }
            }
            .frame(width: 48, height: 52)
            .foregroundStyle(selected ? Color.white : (done ? Theme.success : Color.primary))
            .background {
                ZStack {
                    Capsule().fill(Color.primary.opacity(0.06))
                    if selected {
                        Capsule()
                            .fill(Theme.accentGradient)
                            .matchedGeometryEffect(id: "weekPill", in: weekPill)
                    }
                }
            }
        }
        .buttonStyle(.pressable)
        .accessibilityLabel("Неделя \(week.number)")
    }
}

struct DaySummaryCard: View {
    let profile: ProgramProfile
    let week: Int
    let day: WorkoutDayPlan

    private var isDone: Bool { profile.isCompleted(week: week, day: day.number) }
    private var benchSession: Int? { day.exercises.compactMap(\.benchSession).first }

    var body: some View {
        CardView {
            HStack(spacing: 14) {
                ZStack {
                    Circle()
                        .fill(isDone ? AnyShapeStyle(Theme.successGradient) : AnyShapeStyle(Color.primary.opacity(0.07)))
                        .frame(width: 42, height: 42)
                    Image(systemName: isDone ? "checkmark" : "\(day.number).circle")
                        .font(.system(size: isDone ? 16 : 20, weight: .bold))
                        .foregroundStyle(isDone ? Color.white : Color.primary.opacity(0.6))
                        .symbolEffect(.bounce, value: isDone)
                }
                VStack(alignment: .leading, spacing: 5) {
                    Text(profile.fullTitle(forDay: day))
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(.primary)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                    HStack(spacing: 6) {
                        Text("\(day.exercises.count) упражнений").font(.caption).foregroundStyle(.secondary)
                        if let benchSession {
                            OutlineBadge(text: "Жим №\(benchSession)")
                        }
                    }
                }
                Spacer(minLength: 4)
                Image(systemName: "chevron.right").font(.footnote.weight(.bold)).foregroundStyle(.secondary)
            }
        }
    }
}

struct DayDetailView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Bindable var profile: ProgramProfile
    let week: Int
    let day: WorkoutDayPlan
    var onOpenBench: ((Int) -> Void)?

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 14) {
                ForEach(Array(day.exercises.enumerated()), id: \.element.id) { index, exercise in
                    ExerciseCard(exercise: exercise, onOpenBench: onOpenBench)
                        .appearIn(index)
                        .softScroll()
                }
                CompleteButton(isCompleted: profile.isCompleted(week: week, day: day.number)) {
                    withAnimation(Motion.maybe(Motion.bouncy, reduce: reduceMotion)) {
                        profile.toggleCompleted(week: week, day: day.number)
                    }
                }
                .padding(.top, 4)
                .appearIn(day.exercises.count)
            }
            .padding(.horizontal)
            .padding(.bottom, 28)
        }
        .scrollIndicators(.hidden)
        .screenBackground()
        .navigationTitle("Неделя \(week) · \(profile.shortWeekdayName(forDay: day.number).uppercased())")
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - Карточка упражнения

struct ExerciseCard: View {
    let exercise: ExercisePrescription
    var onOpenBench: ((Int) -> Void)?

    private var isBenchLink: Bool { exercise.benchSession != nil && onOpenBench != nil }

    var body: some View {
        if let session = exercise.benchSession, let onOpenBench {
            Button { onOpenBench(session) } label: { card }
                .buttonStyle(.pressable)
        } else {
            card
        }
    }

    private var iconGradient: LinearGradient {
        if exercise.benchSession != nil { return Theme.recordGradient }
        return exercise.isOptional
            ? LinearGradient(colors: [Theme.warning, Theme.record], startPoint: .topLeading, endPoint: .bottomTrailing)
            : Theme.accentGradient
    }

    private var symbol: String {
        if exercise.benchSession != nil { return "waveform.path.ecg" }
        return exercise.isOptional ? "plus" : "figure.strengthtraining.traditional"
    }

    private var card: some View {
        CardView(padding: 16, spacing: 10) {
            HStack(alignment: .top, spacing: 14) {
                Image(systemName: symbol)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 38, height: 38)
                    .background(iconGradient, in: RoundedRectangle(cornerRadius: 12, style: .continuous))

                VStack(alignment: .leading, spacing: 5) {
                    Text(exercise.name)
                        .font(.headline)
                        .foregroundStyle(.primary)
                        .fixedSize(horizontal: false, vertical: true)
                    Text(setsText)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Spacer(minLength: 6)

                if isBenchLink {
                    Image(systemName: "chevron.right").font(.footnote.weight(.bold)).foregroundStyle(.secondary)
                } else {
                    loadChip
                }
            }

            if isBenchLink {
                HStack(spacing: 8) {
                    Text(exercise.load.displayText)
                        .font(.footnote.weight(.semibold).monospacedDigit())
                        .foregroundStyle(Theme.accentGradient)
                        .fixedSize(horizontal: false, vertical: true)
                    Spacer(minLength: 0)
                }
            }
        }
        .accessibilityElement(children: .combine)
    }

    private var setsText: String {
        exercise.sets > 0 ? "\(exercise.sets) подходов × \(exercise.reps)" : exercise.reps
    }

    @ViewBuilder
    private var loadChip: some View {
        switch exercise.load {
        case .kilograms:
            Text(exercise.load.displayText)
                .font(.system(size: 17, weight: .bold, design: .rounded))
                .monospacedDigit()
                .foregroundStyle(Theme.accentGradient)
        case .testOneRepMax:
            TagBadge(text: "Тест 1ПМ", systemImage: "flame.fill", gradient: Theme.recordGradient)
        default:
            OutlineBadge(text: exercise.load.displayText, tint: Theme.warning)
        }
    }
}

// MARK: - Кнопка выполнения

struct CompleteButton: View {
    let isCompleted: Bool
    let action: () -> Void
    @State private var trigger = 0

    var body: some View {
        Button {
            trigger += 1
            action()
        } label: {
            HStack(spacing: 10) {
                Image(systemName: isCompleted ? "checkmark.circle.fill" : "circle")
                    .symbolEffect(.bounce, value: trigger)
                Text(isCompleted ? "Выполнено" : "Отметить тренировку")
            }
        }
        .buttonStyle(GradientButtonStyle(
            gradient: isCompleted ? Theme.successGradient : Theme.accentGradient,
            glow: isCompleted ? Theme.success : Theme.accent
        ))
        .sensoryFeedback(trigger: trigger) { _, _ in
            isCompleted ? SensoryFeedback.success : SensoryFeedback.impact(weight: .light)
        }
    }
}
