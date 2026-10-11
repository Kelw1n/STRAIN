import SwiftUI
import Charts

struct ActivityProgressView: View {
    @Bindable var profile: ProgramProfile
    let onStartWorkout: () -> Void

    @State private var selectedPeriod = "3 месяца"
    @State private var showingWeightSheet = false
    @State private var showingMuscleSheet = false

    private let periods = ["1 месяц", "3 месяца", "6 месяцев", "Всё время"]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    // Карточка 1: Время, объём и тренировки
                    volumeAndWorkoutsCard

                    // Сетка: Замеры и фото + Группы мышц
                    HStack(spacing: 12) {
                        bodyWeightSummaryCard
                        muscleGroupsRadarCard
                    }

                    // Карточка: Сила и Рекорды
                    strengthRecordsCard

                    Spacer(minLength: 90)
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
            }
            .scrollIndicators(.hidden)
            .background(Color.black.ignoresSafeArea())
            .navigationTitle("Активность")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        ForEach(periods, id: \.self) { p in
                            Button(p) { selectedPeriod = p }
                        }
                    } label: {
                        HStack(spacing: 4) {
                            Text(selectedPeriod)
                                .font(.system(size: 14, weight: .bold))
                                .foregroundStyle(Color.white.opacity(0.9))
                            Image(systemName: "chevron.down")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundStyle(Color.white.opacity(0.5))
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(Color(white: 0.14), in: Capsule())
                    }
                }
            }
            .sheet(isPresented: $showingWeightSheet) {
                NavigationStack {
                    BodyWeightCard(profile: profile)
                        .padding()
                        .background(Color.black.ignoresSafeArea())
                        .navigationTitle("Замеры веса")
                        .navigationBarTitleDisplayMode(.inline)
                        .toolbar {
                            ToolbarItem(placement: .topBarTrailing) {
                                Button("Готово") { showingWeightSheet = false }
                            }
                        }
                }
            }
            .sheet(isPresented: $showingMuscleSheet) {
                MuscleGroupsDetailSheet(profile: profile)
            }
        }
        .preferredColorScheme(.dark)
    }

    // MARK: - Карточка «Время, объём и тренировки»

    private var volumeAndWorkoutsCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            VStack(alignment: .leading, spacing: 3) {
                Text("Время, объём и тренировки")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(.white)
                Text(profile.completionLog.isEmpty
                     ? "Появятся после первой тренировки"
                     : "\(profile.completionLog.count) тренировок за всё время")
                    .font(.system(size: 12))
                    .foregroundStyle(Color.white.opacity(0.45))
            }

            // Бар-чарт с объёмом (показываем до 6 последних, чтобы даты не наползали друг на друга)
            let records = profile.completionLog.suffix(6)
            if !records.isEmpty {
                Chart {
                    ForEach(Array(records.enumerated()), id: \.offset) { idx, record in
                        let squat = record.actualSquat ?? record.squat ?? 0
                        let bench = record.actualBench ?? record.bench ?? 0
                        let dead = record.actualDeadlift ?? record.deadlift ?? 0
                        let total = (squat + bench + dead) * 5

                        BarMark(
                            x: .value("Дата", RuDate.shortDayMonth(record.date)),
                            y: .value("Объём", total)
                        )
                        .foregroundStyle(Theme.accentGradient)
                        .cornerRadius(6)
                    }
                }
                .frame(height: 140)
                .chartYAxis(.hidden)
                .chartXAxis {
                    AxisMarks { _ in
                        AxisValueLabel()
                            .font(.system(size: 10, weight: .semibold, design: Theme.fontDesign))
                            .foregroundStyle(Color.white.opacity(0.45))
                    }
                }
                .padding(.vertical, 4)
            } else {
                // Заглушка чарта в виде элегантных столбцов
                HStack(alignment: .bottom, spacing: 8) {
                    ForEach([0.4, 0.6, 0.5, 0.8, 0.65, 0.55, 0.75, 0.9, 0.6], id: \.self) { ratio in
                        RoundedRectangle(cornerRadius: 6, style: .continuous)
                            .fill(Color(white: 0.16))
                            .frame(height: 110 * ratio)
                            .frame(maxWidth: .infinity)
                    }
                }
                .frame(height: 120)
                .padding(.vertical, 6)
            }

            // Кнопка «НАЧАТЬ ТРЕНИРОВКУ» внутри карточки
            Button {
                UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
                onStartWorkout()
            } label: {
                Text("НАЧАТЬ ТРЕНИРОВКУ")
                    .font(.system(size: 15, weight: .black, design: Theme.fontDesign))
                    .tracking(1.0)
                    .foregroundStyle(.black)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 15)
                    .background(Color.white, in: Capsule())
            }
            .buttonStyle(.plain)
            .padding(.top, 4)
        }
        .padding(18)
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    // MARK: - Карточка «Замеры и фото»

    private var bodyWeightSummaryCard: some View {
        Button {
            showingWeightSheet = true
        } label: {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("Замеры и фото")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(.white)
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Color.white.opacity(0.3))
                }

                // Индикатор веса / график
                ZStack {
                    Line()
                        .stroke(style: StrokeStyle(lineWidth: 1.5, dash: [4, 4]))
                        .foregroundStyle(Color.white.opacity(0.18))
                        .frame(height: 2)

                    Circle()
                        .fill(Theme.accent)
                        .frame(width: 8, height: 8)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                }
                .frame(height: 44)

                VStack(alignment: .leading, spacing: 2) {
                    if let weight = profile.latestBodyWeight {
                        Text(WeightFormat.kilogramsPrecise(weight))
                            .font(.system(size: 20, weight: .bold, design: Theme.fontDesign))
                            .foregroundStyle(.white)
                        Text("Текущий вес")
                            .font(.system(size: 11))
                            .foregroundStyle(Color.white.opacity(0.45))
                    } else {
                        Text("— кг")
                            .font(.system(size: 20, weight: .bold, design: Theme.fontDesign))
                            .foregroundStyle(.white)
                        Text("Нужен первый замер")
                            .font(.system(size: 11))
                            .foregroundStyle(Color.white.opacity(0.45))
                    }
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    // MARK: - Карточка «Группы мышц» (Радарная диаграмма)

    private var muscleGroupsRadarCard: some View {
        let muscleData = MuscleGroupAnalytics.calculate(for: profile)
        return Button {
            showingMuscleSheet = true
        } label: {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("Группы мышц")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(.white)
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Color.white.opacity(0.3))
                }

                // Радарная диаграмма (Spider web) с динамическими данными
                ZStack {
                    RadarWebShape(levels: 3)
                        .stroke(Color.white.opacity(0.12), lineWidth: 1)

                    RadarDataShape(values: muscleData.radarValues)
                        .fill(Theme.accent.opacity(0.25))

                    RadarDataShape(values: muscleData.radarValues)
                        .stroke(Theme.accent, lineWidth: 1.6)
                }
                .frame(height: 56)
                .padding(.vertical, 4)

                Text("Баланс нагрузки")
                    .font(.system(size: 11))
                    .foregroundStyle(Color.white.opacity(0.45))
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    // MARK: - Карточка «Сила» (Рекорды)

    private var strengthRecordsCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text("Сила")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(.white)
                Spacer()
                HStack(spacing: 4) {
                    Image(systemName: "medal.fill")
                        .font(.system(size: 13))
                        .foregroundStyle(Color.yellow)
                    Text("Рекорды")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(Color.white.opacity(0.85))
                }
            }

            VStack(spacing: 10) {
                recordRow(
                    name: "Приседания",
                    weight: profile.squat5RM,
                    icon: "figure.strengthtraining.functional"
                )
                recordRow(
                    name: "Жим лёжа",
                    weight: profile.bench5RM,
                    icon: "figure.strengthtraining.traditional"
                )
                recordRow(
                    name: "Становая тяга",
                    weight: profile.deadlift5RM,
                    icon: "figure.stand"
                )
            }
        }
        .padding(18)
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private func recordRow(name: String, weight: Double, icon: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Theme.accent)
                .frame(width: 32, height: 32)
                .background(Color.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 8, style: .continuous))

            Text(name)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.white)

            Spacer()

            Text(WeightFormat.kilogramsPrecise(weight))
                .font(.system(size: 15, weight: .bold, design: Theme.fontDesign))
                .monospacedDigit()
                .foregroundStyle(Theme.accentGradient)
        }
    }
}

// MARK: - Экран детализации «Группы мышц»

struct MuscleGroupsDetailSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Bindable var profile: ProgramProfile

    var body: some View {
        let data = MuscleGroupAnalytics.calculate(for: profile)

        return NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    // Большая радарная диаграмма
                    CardView(padding: 20) {
                        VStack(spacing: 16) {
                            Text("Баланс распределения нагрузки")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundStyle(.white)

                            ZStack {
                                RadarWebShape(levels: 4)
                                    .stroke(Color.white.opacity(0.12), lineWidth: 1)

                                RadarDataShape(values: data.radarValues)
                                    .fill(Theme.accent.opacity(0.25))

                                RadarDataShape(values: data.radarValues)
                                    .stroke(Theme.accent, lineWidth: 2)
                            }
                            .frame(height: 160)
                            .padding(.vertical, 8)

                            Text(data.subtitle)
                                .font(.system(size: 12))
                                .foregroundStyle(Color.white.opacity(0.6))
                                .multilineTextAlignment(.center)
                        }
                        .frame(maxWidth: .infinity)
                    }

                    // Список групп мышц
                    VStack(spacing: 12) {
                        ForEach(data.items) { group in
                            CardView(padding: 16, spacing: 10) {
                                HStack(spacing: 12) {
                                    Image(systemName: group.category.icon)
                                        .font(.system(size: 16, weight: .bold))
                                        .foregroundStyle(Theme.accent)
                                        .frame(width: 36, height: 36)
                                        .background(Theme.accent.opacity(0.14), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(group.category.rawValue)
                                            .font(.system(size: 15, weight: .bold))
                                            .foregroundStyle(.white)
                                        Text(group.exercises)
                                            .font(.system(size: 11))
                                            .foregroundStyle(Color.white.opacity(0.45))
                                            .lineLimit(1)
                                    }

                                    Spacer(minLength: 8)

                                    VStack(alignment: .trailing, spacing: 2) {
                                        Text("\(group.percentage)%")
                                            .font(.system(size: 15, weight: .bold, design: Theme.fontDesign))
                                            .foregroundStyle(Theme.accent)
                                        Text("\(group.sets) подх.")
                                            .font(.system(size: 11))
                                            .foregroundStyle(Color.white.opacity(0.45))
                                    }
                                }

                                // Полоса прогресса
                                GeometryReader { barProxy in
                                    ZStack(alignment: .leading) {
                                        Capsule()
                                            .fill(Color.white.opacity(0.08))
                                            .frame(height: 6)

                                        Capsule()
                                            .fill(Theme.accentGradient)
                                            .frame(width: max(6, barProxy.size.width * CGFloat(group.percentage) / 100.0), height: 6)
                                    }
                                }
                                .frame(height: 6)
                            }
                        }
                    }

                    Spacer(minLength: 30)
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
            }
            .background(Color.black.ignoresSafeArea())
            .navigationTitle("Группы мышц")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Готово") { dismiss() }
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(Theme.accent)
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}

// MARK: - Аналитика и расчёт баланса групп мышц

enum MuscleCategory: String, CaseIterable, Identifiable {
    case chest = "Грудь (Пекторальные)"
    case back = "Спина (Широчайшие и трапеции)"
    case legs = "Ноги (Квадрицепсы и бицепс бедра)"
    case shoulders = "Плечи (Дельты)"
    case arms = "Руки (Бицепс и трицепс)"

    var id: String { rawValue }

    var icon: String {
        switch self {
        case .chest: return "figure.strengthtraining.traditional"
        case .back: return "figure.rower"
        case .legs: return "figure.run"
        case .shoulders: return "figure.boxing"
        case .arms: return "dumbbell.fill"
        }
    }

    static func categorize(exerciseName: String) -> MuscleCategory {
        let name = exerciseName.lowercased()

        // Ноги
        if name.contains("присед") || name.contains("ног") || name.contains("выпад") ||
           name.contains("квадрицепс") || name.contains("бицепс бедр") || name.contains("голен") ||
           name.contains("икр") || name.contains("гакк") || name.contains("румынск") {
            return .legs
        }

        // Плечи (важно проверять жим стоя / армейский жим до общего жима)
        if name.contains("армейск") || name.contains("жим стоя") || name.contains("жим сидя") ||
           name.contains("махи") || name.contains("дельт") || name.contains("плеч") ||
           name.contains("к подбородку") {
            return .shoulders
        }

        // Руки
        if name.contains("бицепс") || name.contains("трицепс") || name.contains("француз") ||
           name.contains("молот") || name.contains("сгибан") || name.contains("разгибан") ||
           name.contains("брусь") {
            return .arms
        }

        // Спина
        if name.contains("тяг") || name.contains("станов") || name.contains("подтягиван") ||
           name.contains("спин") || name.contains("широчайш") || name.contains("трапеци") ||
           name.contains("блок") || name.contains("гиперэкстенз") || name.contains("пулловер") {
            return .back
        }

        // Грудь
        if name.contains("жим") || name.contains("груд") || name.contains("наклон") ||
           name.contains("отжиман") || name.contains("пекторал") || name.contains("кроссовер") ||
           name.contains("разводк") || name.contains("бабочк") {
            return .chest
        }

        return .chest
    }
}

struct MuscleGroupDisplayItem: Identifiable {
    var id: String { category.id }
    let category: MuscleCategory
    let sets: Int
    let percentage: Int
    let exercises: String
    let radarValue: CGFloat
}

struct MuscleGroupAnalytics {
    static func calculate(for profile: ProgramProfile) -> (items: [MuscleGroupDisplayItem], radarValues: [CGFloat], subtitle: String) {
        var groupSets: [MuscleCategory: Int] = [:]
        var groupExercises: [MuscleCategory: Set<String>] = [:]

        for cat in MuscleCategory.allCases {
            groupSets[cat] = 0
            groupExercises[cat] = []
        }

        // 1. Проверяем фактически выполненные подходы из setLog
        var idToName: [String: String] = [:]
        for week in profile.workoutPlan.weeks {
            for day in week.days {
                for ex in day.exercises {
                    idToName[ex.id] = ex.name
                }
            }
        }
        for edit in profile.planEdits {
            if let targetID = edit.targetID {
                idToName[targetID] = edit.name
            }
        }

        var loggedSetsCount = 0
        for (key, _) in profile.setLog {
            let parts = key.split(separator: "|")
            guard parts.count >= 2 else { continue }
            let exId = String(parts[1])
            let exName = idToName[exId] ?? exId
            let cat = MuscleCategory.categorize(exerciseName: exName)
            groupSets[cat, default: 0] += 1
            groupExercises[cat, default: []].insert(exName)
            loggedSetsCount += 1
        }

        let isBasedOnHistory = loggedSetsCount >= 3

        // 2. Если выполненных подходов мало или нет — считаем нагрузку по текущему плану тренировок
        if !isBasedOnHistory {
            for cat in MuscleCategory.allCases {
                groupSets[cat] = 0
                groupExercises[cat] = []
            }

            for week in profile.workoutPlan.weeks {
                for day in week.days {
                    let exercises = profile.exercises(day: day, benchSession: nil)
                    for ex in exercises {
                        let cat = MuscleCategory.categorize(exerciseName: ex.name)
                        groupSets[cat, default: 0] += max(ex.sets, 1)
                        groupExercises[cat, default: []].insert(ex.name)
                    }
                }
            }
        }

        let totalSets = groupSets.values.reduce(0, +)
        let maxSets = max(1, groupSets.values.max() ?? 1)

        let items: [MuscleGroupDisplayItem] = MuscleCategory.allCases.map { cat in
            let sets = groupSets[cat] ?? 0
            let pct = totalSets > 0 ? Int(round(Double(sets) / Double(totalSets) * 100)) : 0
            let exList = (groupExercises[cat] ?? []).sorted()
            let exString = exList.isEmpty ? "Базовые движения" : exList.prefix(3).joined(separator: ", ")
            let radarVal = max(0.18, CGFloat(sets) / CGFloat(maxSets))

            return MuscleGroupDisplayItem(
                category: cat,
                sets: sets,
                percentage: pct,
                exercises: exString,
                radarValue: radarVal
            )
        }
        .sorted { $0.sets > $1.sets }

        let radarValues = MuscleCategory.allCases.map { cat -> CGFloat in
            let sets = groupSets[cat] ?? 0
            return max(0.2, CGFloat(sets) / CGFloat(maxSets))
        }

        let subtitle = isBasedOnHistory
            ? "На основе \(loggedSetsCount) выполненных подходов в вашей истории."
            : "На основе распределения упражнений в текущей программе."

        return (items, radarValues, subtitle)
    }
}

// MARK: - Вспомогательные формы для Радарной диаграммы

struct Line: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 0, y: rect.midY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.midY))
        return path
    }
}

struct RadarWebShape: Shape {
    let levels: Int

    func path(in rect: CGRect) -> Path {
        var path = Path()
        let center = CGPoint(x: rect.midX, y: rect.midY)
        let radius = min(rect.width, rect.height) / 2
        let sides = 6
        let angleStep = (2 * CGFloat.pi) / CGFloat(sides)

        // Осевые линии
        for i in 0..<sides {
            let angle = CGFloat(i) * angleStep - CGFloat.pi / 2
            let point = CGPoint(
                x: center.x + radius * cos(angle),
                y: center.y + radius * sin(angle)
            )
            path.move(to: center)
            path.addLine(to: point)
        }

        // Концентрические сетки паутины
        for level in 1...levels {
            let currentRadius = radius * (CGFloat(level) / CGFloat(levels))
            for i in 0..<sides {
                let angle = CGFloat(i) * angleStep - CGFloat.pi / 2
                let point = CGPoint(
                    x: center.x + currentRadius * cos(angle),
                    y: center.y + currentRadius * sin(angle)
                )
                if i == 0 {
                    path.move(to: point)
                } else {
                    path.addLine(to: point)
                }
            }
            path.closeSubpath()
        }

        return path
    }
}

struct RadarDataShape: Shape {
    let values: [CGFloat] // Значения от 0.0 до 1.0

    func path(in rect: CGRect) -> Path {
        var path = Path()
        guard !values.isEmpty else { return path }

        let center = CGPoint(x: rect.midX, y: rect.midY)
        let radius = min(rect.width, rect.height) / 2
        let sides = values.count
        let angleStep = (2 * CGFloat.pi) / CGFloat(sides)

        for i in 0..<sides {
            let val = min(1.0, max(0.1, values[i]))
            let angle = CGFloat(i) * angleStep - CGFloat.pi / 2
            let currentRadius = radius * val
            let point = CGPoint(
                x: center.x + currentRadius * cos(angle),
                y: center.y + currentRadius * sin(angle)
            )

            if i == 0 {
                path.move(to: point)
            } else {
                path.addLine(to: point)
            }
        }
        path.closeSubpath()
        return path
    }
}
