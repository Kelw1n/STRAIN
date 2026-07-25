import SwiftUI
import SwiftData

struct MainTabView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Bindable var profile: ProgramProfile
    @State private var selectedTab = 0
    @State private var showingSettings = false

    var body: some View {
        TabView(selection: $selectedTab) {
            TodayView(profile: profile, onSettings: { showingSettings = true }).tabItem { Label("Сегодня", systemImage: "calendar.badge.clock") }.tag(0)
            PlanView(profile: profile).tabItem { Label("План", systemImage: "list.bullet.rectangle") }.tag(1)
            ProgressScreen(profile: profile).tabItem { Label("Прогресс", systemImage: "chart.bar.xaxis") }.tag(2)
            InstructionsView().tabItem { Label("Инструкция", systemImage: "book.closed") }.tag(3)
        }
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.22), value: selectedTab)
        .sheet(isPresented: $showingSettings) { SettingsView(profile: profile) }
    }
}

struct TodayView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Bindable var profile: ProgramProfile
    let onSettings: () -> Void
    @State private var expandedDay: String?
    private var plan: WorkoutPlan { ProgramCalculator.generate(input: profile.input) }

    var next: (Int, WorkoutDayPlan)? {
        for week in plan.weeks { for day in week.days where !profile.isCompleted(week: week.number, day: day.number) { return (week.number, day) } }
        return nil
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                if let next {
                    VStack(alignment: .leading, spacing: 18) {
                        Text("Следующая тренировка").font(.subheadline.weight(.semibold)).foregroundStyle(.teal)
                        HStack(alignment: .firstTextBaseline) {
                            Text("Неделя \(next.0)").font(.system(size: 32, weight: .bold, design: .rounded))
                            Text(next.1.title).font(.title3.weight(.semibold)).foregroundStyle(.secondary)
                        }
                        ForEach(next.1.exercises) { exercise in ExerciseCard(exercise: exercise) }
                        CompleteButton(isCompleted: profile.isCompleted(week: next.0, day: next.1.number)) { withAnimation(reduceMotion ? nil : .spring) { profile.toggleCompleted(week: next.0, day: next.1.number) } }
                    }.padding()
                } else {
                    ContentUnavailableView("Цикл завершён", systemImage: "checkmark.seal", description: Text("Все 12 недель отмечены. Можно запустить пикирование из настроек."))
                }
            }
            .background(Color(uiColor: .systemGroupedBackground))
            .navigationTitle("Сегодня")
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button(action: onSettings) { Image(systemName: "slider.horizontal.3") }.accessibilityLabel("Настройки программы") } }
        }
    }
}

struct PlanView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Bindable var profile: ProgramProfile
    @State private var selectedWeek = 1
    @Namespace private var dayAnimation
    private var plan: WorkoutPlan { ProgramCalculator.generate(input: profile.input) }
    var body: some View {
        NavigationStack {
            ScrollView {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(plan.weeks) { week in
                            Button("\(week.number)") { withAnimation(reduceMotion ? nil : .snappy) { selectedWeek = week.number } }
                                .buttonStyle(.borderedProminent)
                                .tint(selectedWeek == week.number ? .teal : Color(uiColor: .tertiarySystemFill))
                                .foregroundStyle(selectedWeek == week.number ? .white : .primary)
                                .accessibilityLabel("Неделя \(week.number)")
                        }
                    }.padding(.horizontal)
                }
                if let week = plan.weeks.first(where: { $0.number == selectedWeek }) {
                    VStack(spacing: 14) {
                        ForEach(week.days) { day in
                            NavigationLink { DayDetailView(profile: profile, week: week.number, day: day, namespace: dayAnimation) } label: {
                                DaySummaryCard(profile: profile, week: week.number, day: day, namespace: dayAnimation)
                            }.buttonStyle(.plain)
                        }
                    }.padding()
                }
            }.background(Color(uiColor: .systemGroupedBackground)).navigationTitle("План")
        }
    }
}

struct DaySummaryCard: View {
    let profile: ProgramProfile; let week: Int; let day: WorkoutDayPlan; let namespace: Namespace.ID
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 5) { Text(day.title).font(.headline); Text("\(day.exercises.count) упражнений").font(.subheadline).foregroundStyle(.secondary) }
            Spacer()
            Image(systemName: profile.isCompleted(week: week, day: day.number) ? "checkmark.circle.fill" : "chevron.right")
                .foregroundStyle(profile.isCompleted(week: week, day: day.number) ? .green : .secondary).font(.title3)
        }.padding(18).background(.background, in: RoundedRectangle(cornerRadius: 20, style: .continuous)).matchedGeometryEffect(id: "day-\(day.id)", in: namespace)
    }
}

struct DayDetailView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Bindable var profile: ProgramProfile
    let week: Int; let day: WorkoutDayPlan; let namespace: Namespace.ID
    var body: some View {
        ScrollView { VStack(alignment: .leading, spacing: 14) { ForEach(day.exercises) { ExerciseCard(exercise: $0) }; CompleteButton(isCompleted: profile.isCompleted(week: week, day: day.number)) { withAnimation(reduceMotion ? nil : .spring) { profile.toggleCompleted(week: week, day: day.number) } } }.padding().matchedGeometryEffect(id: "day-\(day.id)", in: namespace) }
            .background(Color(uiColor: .systemGroupedBackground)).navigationTitle("Неделя \(week) · \(day.title)").navigationBarTitleDisplayMode(.inline)
    }
}

struct ExerciseCard: View {
    let exercise: ExercisePrescription
    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: exercise.isOptional ? "plus.circle" : "figure.strengthtraining.traditional").font(.title2).foregroundStyle(exercise.isOptional ? .orange : .teal)
            VStack(alignment: .leading, spacing: 6) {
                Text(exercise.name).font(.headline)
                Text("\(exercise.sets) подходов × \(exercise.reps)").font(.subheadline).foregroundStyle(.secondary)
            }
            Spacer()
            Text(exercise.load.displayText).font(.headline.monospacedDigit()).multilineTextAlignment(.trailing)
        }.padding(16).background(.background, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

struct CompleteButton: View {
    let isCompleted: Bool; let action: () -> Void
    var body: some View { Button(action: action) { Label(isCompleted ? "Выполнено" : "Отметить тренировку", systemImage: isCompleted ? "checkmark.circle.fill" : "circle").frame(maxWidth: .infinity) }.buttonStyle(.borderedProminent).tint(isCompleted ? .green : .teal).controlSize(.large) }
}
