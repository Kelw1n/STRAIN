import SwiftUI

/// Вопрос после тяжёлого дня: как прошёл рабочий подход по каждому движению.
///
/// Спрашиваем только здесь. Объёмный день тоже умеет тормозить, но второй
/// вопрос за неделю превращает приложение в анкету, а решение всё равно
/// принимается по тяжёлому.
struct AutoregulationCard: View {
    @Bindable var profile: ProgramProfile
    let week: Int
    let day: WorkoutDayPlan

    /// Движения этого дня, у которых есть конкретный вес.
    private var lifts: [(lift: MainLift, weight: Double)] {
        day.exercises.compactMap { exercise in
            guard let lift = MainLift(rawValue: exercise.name),
                  case .kilograms(let value) = exercise.load else { return nil }
            return (lift, value)
        }
    }

    /// Тяжёлый день — последний в неделе.
    private var isIntensityDay: Bool {
        profile.workoutPlan.weeks.first { $0.number == week }?.days.last?.number == day.number
    }

    var body: some View {
        if profile.autoregulationEnabled, profile.supportsAutoregulation, isIntensityDay, !lifts.isEmpty {
            CardView(padding: 16, spacing: 14) {
                HStack(spacing: 8) {
                    Image(systemName: "dial.medium").font(.footnote.weight(.bold)).foregroundStyle(Theme.accent)
                    Text("Как прошёл тяжёлый подход").font(.subheadline.weight(.semibold))
                }

                ForEach(lifts, id: \.lift) { item in
                    VStack(alignment: .leading, spacing: 7) {
                        HStack {
                            Text(item.lift.rawValue).font(.footnote.weight(.semibold))
                            Spacer(minLength: 6)
                            Text(WeightFormat.kilogramsPrecise(item.weight))
                                .font(.footnote.weight(.bold).monospacedDigit())
                                .foregroundStyle(Theme.accentGradient)
                        }
                        Picker(item.lift.rawValue, selection: binding(for: item.lift)) {
                            ForEach(LiftOutcome.allCases) { Text($0.rawValue).tag(Optional($0)) }
                        }
                        .pickerStyle(.segmented)
                        if let outcome = profile.report(lift: item.lift, week: week)?.outcome {
                            Text(hint(for: outcome, lift: item.lift, weight: item.weight))
                                .font(.caption2)
                                .foregroundStyle(outcome.isFailure ? Theme.warning : .secondary)
                        }
                    }
                }

                Text("Ответ меняет вес только этого движения на следующую неделю. Без ответа неделя идёт с обычной прибавкой.")
                    .font(.caption2).foregroundStyle(.tertiary)
            }
        }
    }

    private func binding(for lift: MainLift) -> Binding<LiftOutcome?> {
        Binding(
            get: { profile.report(lift: lift, week: week)?.outcome },
            set: { value in
                guard let value else { return profile.clearReport(lift: lift, week: week) }
                profile.setReport(lift: lift, week: week, outcome: value)
            }
        )
    }

    /// Показываем сразу, во что превратится вес: решение должно быть видимым.
    private func hint(for outcome: LiftOutcome, lift: MainLift, weight: Double) -> String {
        let next = ProgramCalculator.roundToPlate(outcome.next(after: weight, isUpper: lift.isUpper))
        if next == weight { return outcome.subtitle + " · вес остаётся " + WeightFormat.kilogramsPrecise(next) }
        return outcome.subtitle + " · дальше " + WeightFormat.kilogramsPrecise(next)
    }
}

/// Предупреждение о застое: две неудачи подряд означают, что линейный рост кончился.
struct StallNotice: View {
    @Bindable var profile: ProgramProfile

    var body: some View {
        let stalled = profile.stalledLifts
        if !stalled.isEmpty {
            CardView(padding: 16, spacing: 8) {
                Label("Прогресс встал", systemImage: "exclamationmark.triangle.fill")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(Theme.warning)
                Text(stalled.map(\.rawValue).joined(separator: ", ") + " — два раза подряд не добрал повторения. Линейная прибавка здесь кончилась: имеет смысл начать новый цикл с пересчитанных максимумов.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

/// Заметка к тренировке — свободный текст для себя.
struct WorkoutNoteCard: View {
    @Bindable var profile: ProgramProfile
    let week: Int
    let day: Int

    @State private var text = ""
    @State private var loaded = false

    var body: some View {
        CardView(padding: 16, spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "note.text").font(.footnote.weight(.bold)).foregroundStyle(Theme.accent)
                Text("Заметка").font(.subheadline.weight(.semibold))
            }
            TextField("Как прошло, что болело, как спал", text: $text, axis: .vertical)
                .lineLimit(2...6)
                .font(.footnote)
                .onChange(of: text) { _, value in
                    profile.setNote(value, week: week, day: day)
                }
            Text("Через месяц по этой строке будет понятно, почему неделя вышла такой.")
                .font(.caption2).foregroundStyle(.tertiary)
        }
        .onAppear {
            // Только при первом появлении: иначе набранное затиралось бы
            // при каждом обновлении экрана.
            guard !loaded else { return }
            loaded = true
            text = profile.note(week: week, day: day)
        }
    }
}
