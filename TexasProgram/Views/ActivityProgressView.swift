import SwiftUI
import Charts

struct ActivityProgressView: View {
    @Bindable var profile: ProgramProfile
    let onStartWorkout: () -> Void

    @State private var selectedPeriod = "3 месяца"
    @State private var showingWeightSheet = false

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
            .background(Color(red: 0.05, green: 0.05, blue: 0.06).ignoresSafeArea())
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
                        .background(Color(red: 0.05, green: 0.05, blue: 0.06).ignoresSafeArea())
                        .navigationTitle("Замеры веса")
                        .navigationBarTitleDisplayMode(.inline)
                        .toolbar {
                            ToolbarItem(placement: .topBarTrailing) {
                                Button("Готово") { showingWeightSheet = false }
                            }
                        }
                }
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

            // Бар-чарт с объёмом
            let records = profile.completionLog.suffix(8)
            if !records.isEmpty {
                Chart {
                    ForEach(Array(records.enumerated()), id: \.offset) { idx, record in
                        let squat = record.actualSquat ?? record.squat ?? 0
                        let bench = record.actualBench ?? record.bench ?? 0
                        let dead = record.actualDeadlift ?? record.deadlift ?? 0
                        let total = (squat + bench + dead) * 5

                        BarMark(
                            x: .value("Дата", RuDate.dayMonth(record.date)),
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
                        AxisValueLabel().foregroundStyle(Color.white.opacity(0.4))
                    }
                }
                .padding(.vertical, 4)
            } else {
                // Заглушка чарта в виде элегантных столбцов (как в скриншоте 5)
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
                    .font(.system(size: 15, weight: .black, design: .rounded))
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
        .background(Color(white: 0.11), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
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
                        Text("\(WeightFormat.kilogramsPrecise(weight)) кг")
                            .font(.system(size: 20, weight: .bold, design: .rounded))
                            .foregroundStyle(.white)
                        Text("Текущий вес")
                            .font(.system(size: 11))
                            .foregroundStyle(Color.white.opacity(0.45))
                    } else {
                        Text("— кг")
                            .font(.system(size: 20, weight: .bold, design: .rounded))
                            .foregroundStyle(.white)
                        Text("Нужен первый замер")
                            .font(.system(size: 11))
                            .foregroundStyle(Color.white.opacity(0.45))
                    }
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(white: 0.11), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    // MARK: - Карточка «Группы мышц» (Радарная диаграмма)

    private var muscleGroupsRadarCard: some View {
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

            // Радарная диаграмма (Spider web)
            ZStack {
                RadarWebShape(levels: 3)
                    .stroke(Color.white.opacity(0.12), lineWidth: 1)

                RadarDataShape(values: [0.85, 0.7, 0.9, 0.6, 0.75, 0.5])
                    .fill(Theme.accent.opacity(0.25))

                RadarDataShape(values: [0.85, 0.7, 0.9, 0.6, 0.75, 0.5])
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
        .background(Color(white: 0.11), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
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
        .background(Color(white: 0.11), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
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

            Text("\(WeightFormat.kilogramsPrecise(weight)) кг")
                .font(.system(size: 15, weight: .bold, design: .rounded))
                .monospacedDigit()
                .foregroundStyle(Theme.accentGradient)
        }
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

        // Лучи из центра
        for i in 0..<sides {
            let angle = CGFloat(i) * angleStep - CGFloat.pi / 2
            let x = center.x + radius * cos(angle)
            let y = center.y + radius * sin(angle)
            path.move(to: center)
            path.addLine(to: CGPoint(x: x, y: y))
        }

        // Концентрические кольца-многоугольники
        for level in 1...levels {
            let currentRadius = radius * CGFloat(level) / CGFloat(levels)
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
