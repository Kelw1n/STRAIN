import Charts
import SwiftUI

/// Весь путь одного движения по текущему циклу.
///
/// Записанные подходы копились ради этого экрана: без него они лежат по дням
/// и увидеть, что присед за десять недель прибавил двадцать кило, нельзя.
struct ExerciseHistoryView: View {
    @Bindable var profile: ProgramProfile
    let name: String

    private var points: [ExerciseHistoryPoint] { profile.history(forExerciseNamed: name) }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 16) {
                if points.isEmpty {
                    empty.appearIn(0)
                } else {
                    summary.appearIn(0)
                    chart.appearIn(1).softScroll()
                    ForEach(Array(points.enumerated()), id: \.element.id) { index, point in
                        row(point).appearIn(index + 2).softScroll()
                    }
                }
            }
            .padding(.horizontal)
            .padding(.bottom, 28)
        }
        .scrollIndicators(.hidden)
        .screenBackground()
        .navigationTitle(name)
        .navigationBarTitleDisplayMode(.inline)
    }

    private var empty: some View {
        CardView {
            Text("Здесь пока пусто")
                .font(.headline)
            Text("Нажми на счётчик подходов справа в карточке упражнения — или удерживай кружок. Ещё быстрее: «Всё прошло по плану» под таймером отдыха закрывает день плановыми весами.")
                .font(.footnote).foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    /// Первый и последний рабочий вес: главный ответ этого экрана.
    private var summary: some View {
        let first = points.first?.best ?? 0
        let last = points.last?.best ?? 0
        let delta = last - first
        return HighlightCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("ЛУЧШИЙ ПОДХОД")
                    .font(.caption2.weight(.bold)).tracking(1.3)
                    .foregroundStyle(Theme.accentGradient)
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text(WeightFormat.kilogramsPrecise(last))
                        .font(.system(size: 38, weight: .bold, design: .rounded))
                        .contentTransition(.numericText())
                    if delta != 0 {
                        Text((delta > 0 ? "+" : "") + WeightFormat.kilogramsPrecise(delta))
                            .font(.subheadline.weight(.bold).monospacedDigit())
                            .foregroundStyle(delta > 0 ? Theme.success : Theme.record)
                    }
                }
                Text(delta > 0
                     ? "было \(WeightFormat.kilogramsPrecise(first)) — за \(points.count) \(workoutWord(points.count))"
                     : "записано \(points.count) \(workoutWord(points.count))")
                    .font(.caption).foregroundStyle(.secondary)

                HStack(spacing: 18) {
                    stat("Всего подходов", "\(points.reduce(0) { $0 + $1.entries.count })")
                    stat("Повторов", "\(points.reduce(0) { $0 + $1.totalReps })")
                    stat("Тоннаж", WeightFormat.tonnage(points.reduce(0) { $0 + $1.tonnage }))
                }
                .padding(.top, 2)
            }
        }
    }

    private func stat(_ title: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value).font(.subheadline.weight(.bold).monospacedDigit())
            Text(title).font(.caption2).foregroundStyle(.secondary)
        }
    }

    /// План линией, факт точками: видно и то, что назначено, и то, что вышло.
    private var chart: some View {
        CardView {
            Text("Вес по неделям").font(.title3.weight(.bold))
            Chart {
                ForEach(points) { point in
                    if let planned = point.planned {
                        LineMark(
                            x: .value("Неделя", point.week),
                            y: .value("План", planned),
                            series: .value("Ряд", "план")
                        )
                        .foregroundStyle(Color.secondary.opacity(0.55))
                        .lineStyle(StrokeStyle(lineWidth: 2, dash: [4, 3]))
                    }
                    PointMark(
                        x: .value("Неделя", point.week),
                        y: .value("Факт", point.best)
                    )
                    .foregroundStyle(Theme.accentGradient)
                    .symbolSize(70)
                }
            }
            .chartYAxis {
                AxisMarks(position: .leading) { value in
                    AxisGridLine().foregroundStyle(Color.primary.opacity(0.08))
                    AxisValueLabel {
                        if let weight = value.as(Double.self) {
                            Text(WeightFormat.kilogramsPrecise(weight)).font(.caption2)
                        }
                    }
                }
            }
            .chartXAxis {
                AxisMarks(values: Array(Set(points.map(\.week))).sorted()) { value in
                    AxisValueLabel {
                        if let week = value.as(Int.self) { Text("\(week)").font(.caption2) }
                    }
                }
            }
            .frame(height: 190)
            HStack(spacing: 14) {
                legend(color: AnyShapeStyle(Theme.accentGradient), text: "факт")
                legend(color: AnyShapeStyle(Color.secondary.opacity(0.55)), text: "план")
            }
        }
    }

    private func legend(color: AnyShapeStyle, text: String) -> some View {
        HStack(spacing: 5) {
            Circle().fill(color).frame(width: 7, height: 7)
            Text(text).font(.caption2).foregroundStyle(.secondary)
        }
    }

    private func row(_ point: ExerciseHistoryPoint) -> some View {
        CardView(padding: 14, spacing: 8) {
            HStack {
                Text("Неделя \(point.week), день \(point.day)")
                    .font(.subheadline.weight(.semibold))
                Spacer(minLength: 8)
                Text(WeightFormat.kilogramsPrecise(point.best))
                    .font(.subheadline.weight(.bold).monospacedDigit())
                    .foregroundStyle(Theme.accentGradient)
            }
            Text(point.entries.map { "\($0.reps)×\(WeightFormat.kilogramsPrecise($0.weight))" }.joined(separator: "   "))
                .font(.footnote.monospacedDigit())
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
            if let planned = point.planned, planned != point.best {
                Text("по плану было \(WeightFormat.kilogramsPrecise(planned))")
                    .font(.caption2)
                    .foregroundStyle(point.best < planned ? Theme.warning : Theme.success)
            }
        }
    }

    private func workoutWord(_ count: Int) -> String {
        let last = count % 10, hundred = count % 100
        if hundred >= 11 && hundred <= 14 { return "тренировок" }
        if last == 1 { return "тренировку" }
        if last >= 2 && last <= 4 { return "тренировки" }
        return "тренировок"
    }
}

/// Список движений, по которым есть записи, — вход в историю с экрана прогресса.
struct ExerciseHistoryList: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Bindable var profile: ProgramProfile

    @State private var confirming = false
    @State private var filledMessage: String?

    var body: some View {
        let names = profile.loggedExerciseNames
        let pending = profile.backfillableWorkouts
        ScrollView {
            LazyVStack(spacing: 12) {
                if pending > 0 {
                    backfillCard(pending).appearIn(0)
                }
                if let filledMessage {
                    Text(filledMessage)
                        .font(.footnote)
                        .foregroundStyle(Theme.success)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 4)
                }
                if names.isEmpty {
                    CardView {
                        Text("Записей пока нет").font(.headline)
                        Text("Нажми на счётчик подходов в карточке упражнения и впиши, сколько получилось. Как появятся записи, движения соберутся сюда.")
                            .font(.footnote).foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .appearIn(0)
                }
                ForEach(Array(names.enumerated()), id: \.element) { index, name in
                    NavigationLink {
                        ExerciseHistoryView(profile: profile, name: name)
                    } label: {
                        CardView(padding: 15, spacing: 6) {
                            HStack {
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(name).font(.subheadline.weight(.semibold))
                                        .foregroundStyle(.primary)
                                    Text(subtitle(for: name)).font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer(minLength: 8)
                                Image(systemName: "chevron.right")
                                    .font(.footnote.weight(.bold)).foregroundStyle(.secondary)
                            }
                        }
                    }
                    .buttonStyle(.pressable)
                    .appearIn(index)
                    .softScroll()
                }
            }
            .padding(.horizontal)
            .padding(.bottom, 28)
        }
        .scrollIndicators(.hidden)
        .screenBackground()
        .navigationTitle("История")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func subtitle(for name: String) -> String {
        let points = profile.history(forExerciseNamed: name)
        guard let best = points.map(\.best).max() else { return "нет записей" }
        return "\(points.count) трен. · лучший \(WeightFormat.kilogramsPrecise(best))"
    }

    /// Предложение достроить историю по уже проставленным отметкам.
    ///
    /// Показываем только когда есть что достраивать, и спрашиваем: это всё-таки
    /// запись данных за человека, пусть и по его же отметкам.
    private func backfillCard(_ pending: Int) -> some View {
        HighlightCard {
            VStack(alignment: .leading, spacing: 10) {
                Label("Есть отмеченные тренировки", systemImage: "clock.arrow.circlepath")
                    .font(.subheadline.weight(.bold))
                Text("\(pending) \(workoutWord(pending)) отмечено, но веса в них не записаны. Можно проставить плановые — история и тоннаж посчитаются задним числом.")
                    .font(.footnote).foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                Button {
                    confirming = true
                } label: {
                    Text("Заполнить по отметкам")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 11)
                        .background(Theme.accentGradient, in: RoundedRectangle(cornerRadius: 13, style: .continuous))
                }
                .buttonStyle(.pressable)
            }
        }
        .confirmationDialog("Заполнить историю плановыми весами?",
                            isPresented: $confirming, titleVisibility: .visible) {
            Button("Заполнить") {
                let filled = profile.backfillHistoryFromMarks()
                withAnimation(Motion.maybe(Motion.card, reduce: reduceMotion)) {
                    filledMessage = "Заполнено \(filled) \(workoutWord(filled)). Что помнишь иначе — поправь через счётчик подходов."
                }
            }
            Button("Отмена", role: .cancel) {}
        } message: {
            Text("Возьмутся веса из плана этих недель. Там, где ты уже вводил свои числа, ничего не изменится. Упражнения без веса в плане — по РПЕ и на количество — пропустятся.")
        }
    }

    private func workoutWord(_ count: Int) -> String {
        let last = count % 10, hundred = count % 100
        if hundred >= 11 && hundred <= 14 { return "тренировок" }
        if last == 1 { return "тренировка" }
        if last >= 2 && last <= 4 { return "тренировки" }
        return "тренировок"
    }
}
