import SwiftUI
import UIKit

// MARK: - Карточка входа в итоги

/// Карточка в «Активности»: итоги этого и прошлого месяца, этого и прошлого года.
struct TrainingRecapEntryCard: View {
    @Bindable var profile: ProgramProfile
    @State private var opened: TrainingRecap?

    var body: some View {
        let periods = TrainingRecap.periods(profile.completionLog)
        if !periods.isEmpty {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    Image(systemName: "sparkles")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Theme.accent)
                    Text("ИТОГИ")
                        .font(.system(size: 12, weight: .bold))
                        .tracking(1.2)
                        .foregroundStyle(Color.white.opacity(0.55))
                }
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(Array(periods.enumerated()), id: \.element.id) { index, period in
                            Button {
                                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                opened = TrainingRecap.build(profile, period: period)
                            } label: {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(period.isYear ? "Год" : "Месяц")
                                        .font(.system(size: 11, weight: .bold))
                                        .foregroundStyle(Color.white.opacity(0.75))
                                    Text(period.title)
                                        .font(.system(size: 16, weight: .heavy, design: Theme.fontDesign))
                                        .foregroundStyle(.white)
                                        .lineLimit(1)
                                }
                                .padding(.horizontal, 14)
                                .padding(.vertical, 12)
                                .frame(minWidth: 128, alignment: .leading)
                                .background(
                                    LinearGradient(colors: TrainingRecapView.palette(index), startPoint: .topLeading, endPoint: .bottomTrailing),
                                    in: RoundedRectangle(cornerRadius: 18, style: .continuous)
                                )
                            }
                            .buttonStyle(.pressable)
                        }
                    }
                }
            }
            .padding(16)
            .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .fullScreenCover(item: $opened) { recap in
                TrainingRecapView(recap: recap)
            }
        }
    }
}

extension TrainingRecap: Identifiable {
    var id: String { period.id }
}

// MARK: - Итоги историями

/// Итоги историями, как Spotify Wrapped: слайды сами листаются, нажатие справа —
/// дальше, слева — назад, удержание — пауза. Последний слайд — карточка для сторис.
struct TrainingRecapView: View {
    let recap: TrainingRecap

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var page = 0
    /// Время слайда — в классе, а не в состоянии экрана: полоски читают его
    /// каждый кадр, а сам слайд при этом не пересчитывается.
    @State private var clock = RecapClock()
    @State private var forward = true
    @State private var pressStart: Date?
    @State private var shareImage: ShareImage?

    private static let slideSeconds: Double = 6

    enum Slide: Hashable {
        case intro, time, volume, records, favorite, streak, summary
    }

    private var slides: [Slide] {
        var result: [Slide] = [.intro, .time, .volume]
        if recap.records > 0 || recap.heaviest != nil { result.append(.records) }
        if recap.favorite != nil { result.append(.favorite) }
        if recap.activeWeeks > 0 { result.append(.streak) }
        result.append(.summary)
        return result
    }

    /// Цвета фона слайдов: от акцентного к тёплым и обратно.
    static func palette(_ index: Int) -> [Color] {
        let sets: [[Color]] = [
            [Color(red: 0.36, green: 0.22, blue: 0.95), Color(red: 0.08, green: 0.06, blue: 0.22)],
            [Color(red: 0.98, green: 0.36, blue: 0.24), Color(red: 0.30, green: 0.05, blue: 0.10)],
            [Color(red: 0.10, green: 0.70, blue: 0.55), Color(red: 0.02, green: 0.16, blue: 0.16)],
            [Color(red: 0.98, green: 0.72, blue: 0.16), Color(red: 0.36, green: 0.14, blue: 0.02)],
            [Color(red: 0.90, green: 0.20, blue: 0.62), Color(red: 0.20, green: 0.03, blue: 0.20)],
            [Color(red: 0.20, green: 0.56, blue: 0.98), Color(red: 0.03, green: 0.08, blue: 0.26)],
            [Color(red: 0.55, green: 0.55, blue: 0.60), Color(red: 0.06, green: 0.06, blue: 0.08)]
        ]
        return sets[index % sets.count]
    }

    var body: some View {
        let slide = slides[min(page, slides.count - 1)]
        ZStack {
            LinearGradient(colors: Self.palette(page), startPoint: .topLeading, endPoint: .bottomTrailing)
                .ignoresSafeArea()
                .animation(.easeInOut(duration: 0.6), value: page)

            content(slide)
                .id(slide)
                .transition(.asymmetric(
                    insertion: .move(edge: forward ? .trailing : .leading).combined(with: .opacity),
                    removal: .opacity
                ))
                .padding(.horizontal, 26)
                .padding(.top, 70)
                .padding(.bottom, 40)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)

            // Короткое нажатие слева — назад, справа — дальше; удержание — пауза;
            // смахнуть вниз — закрыть. Под кнопками — не мешает им.
            GeometryReader { proxy in
                Color.clear
                    .contentShape(Rectangle())
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { _ in
                                if pressStart == nil {
                                    pressStart = Date()
                                    clock.pause()
                                }
                            }
                            .onEnded { value in
                                let held = Date().timeIntervalSince(pressStart ?? Date())
                                pressStart = nil
                                clock.resume()
                                if value.translation.height > 120 {
                                    dismiss()
                                } else if held < 0.35 && abs(value.translation.width) < 20 && abs(value.translation.height) < 20 {
                                    go(value.location.x < proxy.size.width / 3 ? -1 : 1)
                                }
                            }
                    )
            }
            .allowsHitTesting(slide != .summary)

            VStack(spacing: 12) {
                // Каждый кадр перерисовываются только полоски.
                TimelineView(.animation(paused: slide == .summary)) { context in
                    HStack(spacing: 4) {
                        ForEach(slides.indices, id: \.self) { index in
                            GeometryReader { proxy in
                                Capsule()
                                    .fill(Color.white.opacity(0.3))
                                    .overlay(alignment: .leading) {
                                        Capsule()
                                            .fill(Color.white)
                                            .frame(width: proxy.size.width * fill(index, now: context.date))
                                    }
                            }
                            .frame(height: 3)
                        }
                    }
                }
                HStack {
                    Text(recap.period.title.uppercased())
                        .font(.system(size: 12, weight: .heavy))
                        .tracking(1.2)
                        .foregroundStyle(Color.white.opacity(0.8))
                    Spacer()
                    Button { dismiss() } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(width: 36, height: 36)
                            .background(Color.black.opacity(0.25), in: Circle())
                    }
                    .buttonStyle(.pressable)
                    .accessibilityLabel("Закрыть")
                }
            }
            .padding(.horizontal, 14)
            .padding(.top, 8)
            .frame(maxHeight: .infinity, alignment: .top)
        }
        .preferredColorScheme(.dark)
        .statusBarHidden()
        .sheet(item: $shareImage) { item in
            ShareSheet(items: [item.image])
                .presentationDetents([.medium, .large])
        }
        .task(id: page) { await run() }
    }

    private func fill(_ index: Int, now: Date) -> Double {
        if index < page { return 1 }
        if index > page { return 0 }
        if slides[page] == .summary { return 1 }
        return min(1, clock.elapsed(now) / Self.slideSeconds)
    }

    /// Слайд листается сам, когда истекло его время; удержание его останавливает.
    /// На последнем стоим.
    private func run() async {
        clock.restart()
        guard slides[page] != .summary else { return }
        while !Task.isCancelled {
            try? await Task.sleep(nanoseconds: 100_000_000)
            if clock.elapsed() >= Self.slideSeconds { break }
        }
        if !Task.isCancelled { go(1) }
    }

    private func go(_ step: Int) {
        let next = page + step
        guard next >= 0 else {
            clock.restart()
            return
        }
        guard next < slides.count else { return }
        UISelectionFeedbackGenerator().selectionChanged()
        forward = step > 0
        withAnimation(reduceMotion ? .easeOut(duration: 0.2) : Motion.card) { page = next }
    }

    // MARK: Слайды

    @ViewBuilder
    private func content(_ slide: Slide) -> some View {
        switch slide {
        case .intro:
            VStack(alignment: .leading, spacing: 18) {
                Spacer()
                Text("Твой \(recap.period.name)")
                    .font(.system(size: 44, weight: .black, design: Theme.fontDesign))
                    .foregroundStyle(.white)
                    .minimumScaleFactor(0.6)
                    .lineLimit(2)
                HStack(alignment: .firstTextBaseline, spacing: 10) {
                    CountUpText(value: Double(recap.workouts)) { "\(Int($0.rounded()))" }
                        .font(.system(size: 96, weight: .black, design: Theme.fontDesign))
                    Text(RuPlural.word(recap.workouts, "тренировка", "тренировки", "тренировок"))
                        .font(.system(size: 24, weight: .bold))
                }
                .foregroundStyle(.white)
                if recap.previousWorkouts > 0 {
                    Text(changeLine(now: recap.workouts, before: recap.previousWorkouts, noun: "тренировок"))
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(Color.white.opacity(0.85))
                }
                Spacer()
                Label("Нажми, чтобы листать", systemImage: "hand.tap.fill")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Color.white.opacity(0.7))
            }
        case .time:
            VStack(alignment: .leading, spacing: 16) {
                Spacer()
                Text("Время в зале")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundStyle(Color.white.opacity(0.85))
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    CountUpText(value: Double(recap.hours)) { "\(Int($0.rounded()))" }
                        .font(.system(size: 88, weight: .black, design: Theme.fontDesign))
                    Text("ч")
                        .font(.system(size: 30, weight: .heavy))
                    CountUpText(value: Double(recap.minutes)) { "\(Int($0.rounded()))" }
                        .font(.system(size: 56, weight: .black, design: Theme.fontDesign))
                    Text("мин")
                        .font(.system(size: 24, weight: .heavy))
                }
                .foregroundStyle(.white)
                .minimumScaleFactor(0.6)
                .lineLimit(1)
                Text("\(RuPlural.sets(recap.sets)) и \(SocialFormat.grouped(Double(recap.reps))) \(RuPlural.word(recap.reps, "повтор", "повтора", "повторов")) \(recap.period.inPhrase)")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Color.white.opacity(0.85))
                RecapBars(values: recap.buckets, labels: barLabels)
                    .frame(height: 150)
                    .padding(.top, 12)
                Spacer()
            }
        case .volume:
            VStack(alignment: .leading, spacing: 16) {
                Spacer()
                Text("Поднято \(recap.period.inPhrase)")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundStyle(Color.white.opacity(0.85))
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    CountUpText(value: recap.volumeKg / 1000) { String(format: "%.1f", $0).replacingOccurrences(of: ".", with: ",") }
                        .font(.system(size: 96, weight: .black, design: Theme.fontDesign))
                    Text("т")
                        .font(.system(size: 36, weight: .heavy))
                }
                .foregroundStyle(.white)
                .minimumScaleFactor(0.5)
                .lineLimit(1)
                if let comparison = recap.volumeComparison {
                    Text("Это \(comparison) 🐘")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(.white)
                }
                if let change = recap.volumeChange {
                    Text(change >= 0 ? "На \(change)% больше, чем \(previousPhrase)" : "На \(-change)% меньше, чем \(previousPhrase)")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(Color.white.opacity(0.85))
                }
                Spacer()
            }
        case .records:
            VStack(alignment: .leading, spacing: 16) {
                Spacer()
                Image(systemName: "trophy.fill")
                    .font(.system(size: 54, weight: .bold))
                    .foregroundStyle(.white)
                    .symbolEffect(.bounce, value: page)
                if recap.records > 0 {
                    HStack(alignment: .firstTextBaseline, spacing: 10) {
                        CountUpText(value: Double(recap.records)) { "\(Int($0.rounded()))" }
                            .font(.system(size: 96, weight: .black, design: Theme.fontDesign))
                        Text(RuPlural.word(recap.records, "рекорд", "рекорда", "рекордов"))
                            .font(.system(size: 26, weight: .bold))
                    }
                    .foregroundStyle(.white)
                } else {
                    Text("Рекордов не было — значит, всё впереди")
                        .font(.system(size: 26, weight: .heavy))
                        .foregroundStyle(.white)
                }
                if let heaviest = recap.heaviest {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Самый тяжёлый подход")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(Color.white.opacity(0.8))
                        Text("\(heaviest.name) · \(WeightFormat.kilogramsPrecise(heaviest.bestWeight)) × \(heaviest.bestReps)")
                            .font(.system(size: 24, weight: .heavy, design: Theme.fontDesign))
                            .foregroundStyle(.white)
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.black.opacity(0.22), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                }
                Spacer()
            }
        case .favorite:
            VStack(alignment: .leading, spacing: 16) {
                Spacer()
                Text("Любимое упражнение")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundStyle(Color.white.opacity(0.85))
                if let favorite = recap.favorite {
                    HStack(spacing: 16) {
                        ExerciseThumb(name: favorite.name, size: 74)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(favorite.name)
                                .font(.system(size: 30, weight: .black, design: Theme.fontDesign))
                                .foregroundStyle(.white)
                                .lineLimit(2)
                                .minimumScaleFactor(0.7)
                            Text("\(favorite.sessions) \(RuPlural.word(favorite.sessions, "тренировка", "тренировки", "тренировок"))")
                                .font(.system(size: 17, weight: .semibold))
                                .foregroundStyle(Color.white.opacity(0.85))
                        }
                    }
                }
                if !recap.topByVolume.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("БОЛЬШЕ ВСЕГО ПОДНЯТО")
                            .font(.system(size: 12, weight: .heavy))
                            .tracking(1.1)
                            .foregroundStyle(Color.white.opacity(0.7))
                        ForEach(Array(recap.topByVolume.enumerated()), id: \.offset) { index, stat in
                            HStack {
                                Text("\(index + 1)")
                                    .font(.system(size: 18, weight: .black, design: Theme.fontDesign))
                                    .frame(width: 24, alignment: .leading)
                                Text(stat.name)
                                    .font(.system(size: 17, weight: .bold))
                                    .lineLimit(1)
                                Spacer()
                                Text("\(SocialFormat.grouped(stat.volumeKg)) кг")
                                    .font(.system(size: 16, weight: .heavy, design: Theme.fontDesign))
                                    .monospacedDigit()
                            }
                            .foregroundStyle(.white)
                        }
                    }
                    .padding(16)
                    .background(Color.black.opacity(0.22), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                }
                Spacer()
            }
        case .streak:
            VStack(alignment: .leading, spacing: 16) {
                Spacer()
                Image(systemName: "flame.fill")
                    .font(.system(size: 54, weight: .bold))
                    .foregroundStyle(.white)
                    .symbolEffect(.bounce, value: page)
                HStack(alignment: .firstTextBaseline, spacing: 10) {
                    CountUpText(value: Double(recap.longestWeekStreak)) { "\(Int($0.rounded()))" }
                        .font(.system(size: 96, weight: .black, design: Theme.fontDesign))
                    Text(RuPlural.word(recap.longestWeekStreak, "неделя", "недели", "недель") + " подряд")
                        .font(.system(size: 24, weight: .bold))
                }
                .foregroundStyle(.white)
                .minimumScaleFactor(0.6)
                .lineLimit(1)
                Text("Самая длинная серия: каждую неделю — хотя бы одна тренировка. Всего недель с тренировками — \(recap.activeWeeks).")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Color.white.opacity(0.85))
                    .fixedSize(horizontal: false, vertical: true)
                if let weekday = recap.busiestWeekday {
                    Text("Чаще всего ты тренировался(-ась) по \(Self.weekdayDative[weekday])")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(.white)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer()
            }
        case .summary:
            VStack(spacing: 18) {
                Spacer(minLength: 0)
                TrainingRecapCard(recap: recap)
                    .scaleEffect(0.82)
                    .frame(width: TrainingRecapCard.size.width * 0.82, height: TrainingRecapCard.size.height * 0.82)
                    .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                    .shadow(color: .black.opacity(0.4), radius: 24, y: 10)
                Button {
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    if let image = TrainingRecapCard.render(recap) { shareImage = ShareImage(image: image) }
                } label: {
                    Label("Поделиться в сторис", systemImage: "square.and.arrow.up")
                        .font(.system(size: 16, weight: .heavy))
                        .foregroundStyle(.black)
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                        .background(Color.white, in: Capsule())
                }
                .buttonStyle(.pressable)
                Button("Сначала") { go(-page) }
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color.white.opacity(0.85))
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity)
        }
    }

    private var barLabels: [String] {
        if recap.period.isYear { return ["я", "ф", "м", "а", "м", "и", "и", "а", "с", "о", "н", "д"] }
        return recap.buckets.indices.map { "\($0 + 1) нед" }
    }

    private var previousPhrase: String {
        switch recap.period.previous {
        case .month(_, let month): return "в " + TrainingRecap.monthPrepositional[month]
        case .year(let year): return "в \(year) году"
        }
    }

    private func changeLine(now: Int, before: Int, noun: String) -> String {
        if now == before { return "Столько же, сколько \(previousPhrase)" }
        let difference = abs(now - before)
        let word = RuPlural.word(difference, "тренировку", "тренировки", "тренировок")
        return now > before ? "На \(difference) \(word) больше, чем \(previousPhrase)\u{00A0}💪" : "На \(difference) \(word) меньше, чем \(previousPhrase)"
    }

    /// «по понедельникам» — индекс как у `Calendar`: 1 — воскресенье.
    static let weekdayDative = ["", "воскресеньям", "понедельникам", "вторникам", "средам", "четвергам", "пятницам", "субботам"]
}

/// Время слайда с учётом пауз.
final class RecapClock {
    private var start = Date()
    private var pausedAt: Date?
    private var pausedTotal: TimeInterval = 0

    func restart() {
        start = Date()
        pausedAt = nil
        pausedTotal = 0
    }

    func pause() {
        if pausedAt == nil { pausedAt = Date() }
    }

    func resume() {
        guard let pausedAt else { return }
        pausedTotal += Date().timeIntervalSince(pausedAt)
        self.pausedAt = nil
    }

    func elapsed(_ now: Date = Date()) -> TimeInterval {
        max(0, (pausedAt ?? now).timeIntervalSince(start) - pausedTotal)
    }
}

/// Число, которое при появлении набегает от нуля.
struct CountUpText: View {
    let value: Double
    let format: (Double) -> String
    @State private var shown: Double = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(value: Double, format: @escaping (Double) -> String) {
        self.value = value
        self.format = format
    }

    var body: some View {
        Text(format(shown))
            .contentTransition(.numericText(value: shown))
            .monospacedDigit()
            .onAppear {
                if reduceMotion {
                    shown = value
                } else {
                    withAnimation(.spring(duration: 1.3, bounce: 0.15).delay(0.15)) { shown = value }
                }
            }
            .accessibilityLabel(format(value))
    }
}

/// Столбики по неделям месяца или месяцам года: вырастают снизу по очереди.
struct RecapBars: View {
    let values: [Int]
    let labels: [String]
    @State private var grown = false

    var body: some View {
        let top = max(values.max() ?? 0, 1)
        HStack(alignment: .bottom, spacing: values.count > 6 ? 6 : 12) {
            ForEach(values.indices, id: \.self) { index in
                VStack(spacing: 6) {
                    if values[index] > 0 {
                        Text("\(values[index])")
                            .font(.system(size: 12, weight: .heavy))
                            .foregroundStyle(.white)
                    }
                    GeometryReader { proxy in
                        VStack {
                            Spacer(minLength: 0)
                            RoundedRectangle(cornerRadius: 5, style: .continuous)
                                .fill(Color.white.opacity(values[index] > 0 ? 0.95 : 0.2))
                                .frame(height: grown ? max(4, proxy.size.height * CGFloat(values[index]) / CGFloat(top)) : 4)
                        }
                    }
                    Text(labels.indices.contains(index) ? labels[index] : "")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(Color.white.opacity(0.7))
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                }
                .animation(.spring(duration: 0.7, bounce: 0.25).delay(0.2 + Double(index) * 0.05), value: grown)
            }
        }
        .onAppear { grown = true }
    }
}

// MARK: - Картинка для сторис

/// Итоги одной картинкой 9:16 — последний слайд и то, что уходит в сторис.
struct TrainingRecapCard: View {
    static let size = CGSize(width: 360, height: 640)
    let recap: TrainingRecap

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack {
                Text("STRAIN")
                    .font(.system(size: 18, weight: .black, design: Theme.fontDesign))
                    .tracking(2)
                Spacer()
                Text(recap.period.isYear ? "ИТОГИ ГОДА" : "ИТОГИ МЕСЯЦА")
                    .font(.system(size: 11, weight: .heavy))
                    .tracking(1.2)
                    .foregroundStyle(Color.white.opacity(0.75))
            }
            Text(recap.period.title)
                .font(.system(size: 38, weight: .black, design: Theme.fontDesign))
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                tile("\(recap.workouts)", RuPlural.word(recap.workouts, "тренировка", "тренировки", "тренировок"))
                tile("\(recap.hours) ч \(recap.minutes) м", "в зале")
                tile(String(format: "%.1f т", recap.volumeKg / 1000).replacingOccurrences(of: ".", with: ","), "поднято")
                // Без рекордов ноль не показываем — показываем, сколько подходов сделано.
                if recap.records > 0 {
                    tile("\(recap.records)", RuPlural.word(recap.records, "рекорд", "рекорда", "рекордов"))
                } else {
                    tile("\(recap.sets)", RuPlural.word(recap.sets, "подход", "подхода", "подходов"))
                }
            }
            if let favorite = recap.favorite {
                row("Любимое", favorite.name)
            }
            if let heaviest = recap.heaviest {
                row("Тяжелее всего", "\(heaviest.name) · \(WeightFormat.kilogramsPrecise(heaviest.bestWeight)) × \(heaviest.bestReps)")
            }
            if recap.longestWeekStreak > 0 {
                row("Серия", "\(recap.longestWeekStreak) \(RuPlural.word(recap.longestWeekStreak, "неделя", "недели", "недель")) подряд")
            }
            Spacer(minLength: 0)
        }
        .foregroundStyle(.white)
        .padding(26)
        .frame(width: Self.size.width, height: Self.size.height, alignment: .topLeading)
        .background(
            LinearGradient(colors: TrainingRecapView.palette(0), startPoint: .topLeading, endPoint: .bottomTrailing)
        )
    }

    private func tile(_ value: String, _ title: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value)
                .font(.system(size: 26, weight: .black, design: Theme.fontDesign))
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            Text(title)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Color.white.opacity(0.75))
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.black.opacity(0.22), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func row(_ title: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title.uppercased())
                .font(.system(size: 11, weight: .heavy))
                .tracking(1.1)
                .foregroundStyle(Color.white.opacity(0.7))
            Text(value)
                .font(.system(size: 18, weight: .heavy))
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
    }

    @MainActor
    static func render(_ recap: TrainingRecap) -> UIImage? {
        let renderer = ImageRenderer(content: TrainingRecapCard(recap: recap))
        renderer.scale = 3
        renderer.isOpaque = true
        return renderer.uiImage
    }
}
