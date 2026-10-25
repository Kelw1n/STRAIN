import SwiftUI
import UIKit

// MARK: - Опыт за только что законченную тренировку

/// Сколько опыта принесла тренировка. Показываем, когда экран тренировки уже
/// закрылся: поверх него праздновать нечего — его сейчас уберут.
// Без привязки к главному потоку в типе: событие шлёт менеджер тренировки,
// он тоже живёт на главном потоке, но не помечен им.
@Observable
final class ProgressionEvents {
    static let shared = ProgressionEvents()

    struct Gain: Identifiable, Equatable {
        let id = UUID()
        let before: Int
        let after: Int
    }

    var pending: Gain?

    func celebrate(before: Int, after: Int) {
        guard after > before else { return }
        pending = Gain(before: before, after: after)
    }
}

// MARK: - Кольцо и шкала

/// Номер уровня в кольце: заполнение — путь до следующего.
struct LevelRing: View {
    let level: Int
    let fraction: Double
    var size: CGFloat = 64
    var lineWidth: CGFloat = 6

    var body: some View {
        ZStack {
            Circle()
                .stroke(Color.white.opacity(0.1), lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: max(0.001, min(1, fraction)))
                .stroke(Theme.accentGradient, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
            VStack(spacing: -2) {
                Text("\(level)")
                    .font(.system(size: size * 0.36, weight: .black, design: Theme.fontDesign))
                    .monospacedDigit()
                    .contentTransition(.numericText())
                    .foregroundStyle(.white)
                Text("УР.")
                    .font(.system(size: max(9, size * 0.13), weight: .heavy))
                    .foregroundStyle(Color.white.opacity(0.5))
            }
        }
        .frame(width: size, height: size)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Уровень \(level)")
    }
}

private struct XPBar: View {
    let fraction: Double
    var height: CGFloat = 6

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .leading) {
                Capsule().fill(Color.white.opacity(0.1))
                Capsule()
                    .fill(Theme.accentGradient)
                    .frame(width: max(height, proxy.size.width * min(1, max(0, fraction))))
            }
        }
        .frame(height: height)
    }
}

private extension Progression.MonthTier {
    var color: Color {
        switch self {
        case .bronze: return Color(red: 0.85, green: 0.55, blue: 0.30)
        case .silver: return Color(white: 0.80)
        case .gold: return Color(red: 1.0, green: 0.80, blue: 0.25)
        }
    }

    var genitive: String {
        switch self {
        case .bronze: return "бронзы"
        case .silver: return "серебра"
        case .gold: return "золота"
        }
    }
}

private enum ProgressionText {
    static func experience(_ value: Int) -> String {
        "\(value.formatted(.number.grouping(.automatic).locale(Locale(identifier: "ru_RU")))) опыта"
    }

    static func toNext(_ level: Progression.Level) -> String {
        guard let left = level.toNext else { return "Все уровни взяты" }
        return "до \(level.level + 1)-го уровня — \(experience(left))"
    }

    /// «Октябрь: 5 тренировок · до бронзы 3».
    static func month(_ count: Int, next: Progression.MonthTier?, now: Date = Date()) -> String {
        let month = Progression.monthNames[Calendar.current.component(.month, from: now)]
        let done = "\(month): \(count) \(RuPlural.word(count, "тренировка", "тренировки", "тренировок"))"
        guard let next else { return done + " · золото месяца" }
        return done + " · до \(next.genitive) \(next.workouts - count)"
    }
}

// MARK: - Уровень у ника

/// Плашка рядом с ником в ленте, у друзей и в рейтинге. Нет уровня — старая
/// версия приложения его не присылала — ничего не рисуем.
struct LevelChip: View {
    let level: Int?
    var large = false

    var body: some View {
        if let level {
            Text(large ? "Уровень \(level) · \(Progression.title(for: level))" : "ур. \(level)")
                .font(.system(size: large ? 13 : 10, weight: .heavy, design: .rounded))
                .monospacedDigit()
                .foregroundStyle(Theme.accent)
                .padding(.horizontal, large ? 10 : 5)
                .padding(.vertical, large ? 4 : 2)
                .background(Theme.accent.opacity(0.16), in: Capsule())
                .fixedSize()
                .accessibilityLabel("Уровень \(level)")
        }
    }
}

// MARK: - Карточка уровня

/// Уровень в «Активности»: звание, шкала до следующего и награда месяца.
struct LevelCard: View {
    @Bindable var profile: ProgramProfile
    @State private var showing = false

    var body: some View {
        let level = Progression.level(for: profile)
        let month = Progression.currentMonth(log: profile.completionLog)
        Button {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            showing = true
        } label: {
            HStack(spacing: 14) {
                LevelRing(level: level.level, fraction: level.fraction)
                VStack(alignment: .leading, spacing: 6) {
                    Text(level.title)
                        .font(.system(size: 20, weight: .heavy, design: Theme.fontDesign))
                        .foregroundStyle(.white)
                    XPBar(fraction: level.fraction)
                    Text(ProgressionText.toNext(level))
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Color.white.opacity(0.5))
                    Text(ProgressionText.month(month.workouts, next: month.next))
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(month.next == nil ? Progression.MonthTier.gold.color : Theme.accent)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Color.white.opacity(0.3))
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        }
        .buttonStyle(.pressable)
        .accessibilityLabel("Уровень \(level.level), \(level.title). \(ProgressionText.toNext(level))")
        .sheet(isPresented: $showing) { LevelSheet(profile: profile) }
    }
}

// MARK: - Экран уровня

/// Всё про уровень: за что опыт, награды месяцев и лестница званий.
struct LevelSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Bindable var profile: ProgramProfile

    private struct TitleRange: Hashable {
        let levels: String
        let title: String
        let from: Int
    }

    private static let titleRanges: [TitleRange] = [
        TitleRange(levels: "1–4", title: "Новичок", from: 1), TitleRange(levels: "5–9", title: "Любитель", from: 5),
        TitleRange(levels: "10–14", title: "Атлет", from: 10), TitleRange(levels: "15–19", title: "Силач", from: 15),
        TitleRange(levels: "20–24", title: "Ветеран зала", from: 20), TitleRange(levels: "25–29", title: "Мастер", from: 25),
        TitleRange(levels: "30–34", title: "Железный", from: 30), TitleRange(levels: "35–39", title: "Титан", from: 35),
        TitleRange(levels: "40–44", title: "Чемпион", from: 40), TitleRange(levels: "45–49", title: "Икона зала", from: 45),
        TitleRange(levels: "50", title: "Легенда", from: 50)
    ]

    var body: some View {
        let level = Progression.level(for: profile)
        let month = Progression.currentMonth(log: profile.completionLog)
        let awards = Progression.monthAwards(log: profile.completionLog)
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    hero(level)
                    rules
                    monthCard(month, awards: awards)
                    titles(level)
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 32)
            }
            .scrollIndicators(.hidden)
            .background(Color.black.ignoresSafeArea())
            .navigationTitle("Уровень")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Готово") { dismiss() }
                        .font(.body.weight(.bold))
                        .foregroundStyle(Theme.accent)
                }
            }
        }
        .preferredColorScheme(.dark)
    }

    private func hero(_ level: Progression.Level) -> some View {
        VStack(spacing: 12) {
            LevelRing(level: level.level, fraction: level.fraction, size: 120, lineWidth: 10)
            Text(level.title)
                .font(.system(size: 26, weight: .heavy, design: Theme.fontDesign))
                .foregroundStyle(.white)
            Text(ProgressionText.experience(level.xp))
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Color.white.opacity(0.6))
            XPBar(fraction: level.fraction, height: 8)
                .padding(.horizontal, 24)
            Text(ProgressionText.toNext(level))
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Color.white.opacity(0.45))
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 20)
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private var rules: some View {
        VStack(alignment: .leading, spacing: 10) {
            caption("За что опыт")
            rule("Тренировка", "+\(Progression.workoutXP)")
            rule("Рабочий подход", "+\(Progression.setXP)")
            rule("Личный рекорд", "+\(Progression.recordXP)")
            rule("Цель недели: \(Progression.weeklyGoal(for: profile)) \(RuPlural.word(Progression.weeklyGoal(for: profile), "тренировка", "тренировки", "тренировок"))", "+\(Progression.weekGoalXP)")
            rule("Серия недель подряд", "до +50%")
            rule("Место в челлендже", "до +300")
            Text("Каждая следующая неделя с тренировкой даёт +10% ко всему опыту недели. Пропустил неделю — серия начинается заново, опыт остаётся.")
                .font(.system(size: 12))
                .foregroundStyle(Color.white.opacity(0.4))
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private func monthCard(_ month: (workouts: Int, next: Progression.MonthTier?), awards: [Progression.MonthAward]) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            caption("Награды месяцев")
            Text(ProgressionText.month(month.workouts, next: month.next))
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(.white)
            HStack(spacing: 8) {
                ForEach([Progression.MonthTier.bronze, .silver, .gold], id: \.rawValue) { tier in
                    let reached = month.workouts >= tier.workouts
                    VStack(spacing: 4) {
                        Image(systemName: "medal.fill")
                            .font(.system(size: 20))
                            .foregroundStyle(reached ? tier.color : Color.white.opacity(0.18))
                        Text(tier.title)
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(reached ? .white : Color.white.opacity(0.45))
                        Text("\(tier.workouts) трен.")
                            .font(.system(size: 11))
                            .foregroundStyle(Color.white.opacity(0.4))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(Color(white: 0.12), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
            }
            Text("Награду месяца можно взять только в этом месяце — потом она остаётся в коллекции.")
                .font(.system(size: 12))
                .foregroundStyle(Color.white.opacity(0.4))
                .fixedSize(horizontal: false, vertical: true)
            if !awards.isEmpty {
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
                    ForEach(awards) { award in
                        HStack(spacing: 8) {
                            Image(systemName: "medal.fill")
                                .font(.system(size: 18))
                                .foregroundStyle(award.tier.color)
                            VStack(alignment: .leading, spacing: 1) {
                                Text(award.title)
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundStyle(.white)
                                    .lineLimit(1)
                                    .minimumScaleFactor(0.8)
                                Text("\(award.tier.title) · \(award.workouts) трен.")
                                    .font(.system(size: 11))
                                    .foregroundStyle(Color.white.opacity(0.5))
                            }
                            Spacer(minLength: 0)
                        }
                        .padding(10)
                        .background(Color(white: 0.12), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }
                }
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private func titles(_ level: Progression.Level) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            caption("Звания")
                .padding(.bottom, 6)
            ForEach(Self.titleRanges, id: \.from) { item in
                let current = Progression.title(for: level.level) == item.title
                let reached = level.level >= item.from
                HStack {
                    Text(item.title)
                        .font(.system(size: 15, weight: current ? .heavy : .semibold))
                        .foregroundStyle(current ? Theme.accent : (reached ? .white : Color.white.opacity(0.4)))
                    Spacer()
                    Text("ур. \(item.levels)")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Color.white.opacity(0.4))
                    if reached {
                        Image(systemName: current ? "location.fill" : "checkmark")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(current ? Theme.accent : Theme.success)
                            .frame(width: 18)
                    } else {
                        Color.clear.frame(width: 18, height: 1)
                    }
                }
                .padding(.vertical, 6)
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private func caption(_ text: String) -> some View {
        Text(text.uppercased())
            .font(.system(size: 12, weight: .bold))
            .tracking(1.2)
            .foregroundStyle(Color.white.opacity(0.45))
    }

    private func rule(_ title: String, _ value: String) -> some View {
        HStack {
            Text(title)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(.white)
            Spacer()
            Text(value)
                .font(.system(size: 15, weight: .heavy, design: Theme.fontDesign))
                .foregroundStyle(Theme.accent)
        }
    }
}

// MARK: - Новый уровень

/// После тренировки: сколько опыта пришло, шкала доезжает, а на новом уровне —
/// вспышка, звание и карточка для сторис.
struct LevelUpOverlay: View {
    let gain: ProgressionEvents.Gain
    let onClose: () -> Void

    @State private var barLevel: Int
    @State private var barFraction: Double
    @State private var shownXP = 0
    @State private var reachedLevel: Int?
    @State private var shareImage: ShareImage?

    init(gain: ProgressionEvents.Gain, onClose: @escaping () -> Void) {
        self.gain = gain
        self.onClose = onClose
        let start = Progression.level(forXP: gain.before)
        _barLevel = State(initialValue: start.level)
        _barFraction = State(initialValue: start.fraction)
    }

    private var end: Progression.Level { Progression.level(forXP: gain.after) }

    var body: some View {
        ZStack {
            Color.black.opacity(0.62)
                .ignoresSafeArea()
                .onTapGesture(perform: onClose)
            VStack(spacing: 16) {
                ZStack {
                    if reachedLevel != nil { ConfettiBurst() }
                    LevelRing(level: barLevel, fraction: barFraction, size: 120, lineWidth: 10)
                }
                .frame(height: 140)
                Text("+\(shownXP) опыта")
                    .font(.system(size: 32, weight: .black, design: Theme.fontDesign))
                    .monospacedDigit()
                    .contentTransition(.numericText())
                    .foregroundStyle(.white)
                if let reachedLevel {
                    VStack(spacing: 4) {
                        Text("НОВЫЙ УРОВЕНЬ")
                            .font(.system(size: 12, weight: .heavy))
                            .tracking(1.4)
                            .foregroundStyle(Theme.accent)
                        Text("\(reachedLevel) · \(Progression.title(for: reachedLevel))")
                            .font(.system(size: 24, weight: .heavy, design: Theme.fontDesign))
                            .foregroundStyle(.white)
                    }
                    .transition(.scale(scale: 0.8).combined(with: .opacity))
                } else {
                    Text(ProgressionText.toNext(end))
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Color.white.opacity(0.55))
                }
                HStack(spacing: 10) {
                    if reachedLevel != nil {
                        Button {
                            if let image = LevelShareCard.render(end) { shareImage = ShareImage(image: image) }
                        } label: {
                            Label("Поделиться", systemImage: "square.and.arrow.up")
                                .font(.system(size: 15, weight: .bold))
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: 50)
                                .background(Color(white: 0.18), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        }
                        .buttonStyle(.pressable)
                    }
                    Button(action: onClose) {
                        Text("Отлично")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundStyle(.black)
                            .frame(maxWidth: .infinity)
                            .frame(height: 50)
                            .background(Theme.accent, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }
                    .buttonStyle(.pressable)
                }
            }
            .padding(24)
            .frame(maxWidth: 360)
            .background(Color(white: 0.09), in: RoundedRectangle(cornerRadius: 28, style: .continuous))
            .padding(.horizontal, 20)
        }
        .task { await play() }
        .sheet(item: $shareImage) { ShareSheet(items: [$0.image]) }
    }

    private func play() async {
        try? await Task.sleep(for: .milliseconds(250))
        withAnimation(.easeOut(duration: 0.8)) { shownXP = gain.after - gain.before }
        let start = Progression.level(forXP: gain.before)
        guard end.level > start.level else {
            withAnimation(.easeOut(duration: 0.9)) { barFraction = end.fraction }
            return
        }
        // Сначала шкала доходит до конца, потом — новый уровень и остаток опыта.
        withAnimation(.easeIn(duration: 0.6)) { barFraction = 1 }
        try? await Task.sleep(for: .milliseconds(650))
        var still = Transaction()
        still.disablesAnimations = true
        withTransaction(still) {
            barLevel = end.level
            barFraction = 0
        }
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        withAnimation(Motion.bouncy) { reachedLevel = end.level }
        withAnimation(.easeOut(duration: 0.8).delay(0.1)) { barFraction = end.fraction }
    }
}

/// Вспышка конфетти из-за кольца: разлетается и гаснет.
private struct ConfettiBurst: View {
    @State private var fired = false
    private let colors: [Color] = [Theme.accent, Theme.success, Theme.warning, .white, Color(red: 0.36, green: 0.22, blue: 0.95)]

    var body: some View {
        ZStack {
            ForEach(0..<20, id: \.self) { index in
                let angle = Double(index) / 20 * 2 * .pi + (index.isMultiple(of: 2) ? 0.12 : -0.08)
                let distance: CGFloat = index.isMultiple(of: 3) ? 130 : 100
                RoundedRectangle(cornerRadius: 2)
                    .fill(colors[index % colors.count])
                    .frame(width: 7, height: index.isMultiple(of: 2) ? 12 : 8)
                    .rotationEffect(.degrees(Double(index) * 37 + (fired ? 220 : 0)))
                    .offset(x: fired ? cos(angle) * distance : 0, y: fired ? sin(angle) * distance : 0)
                    .opacity(fired ? 0 : 1)
            }
        }
        .allowsHitTesting(false)
        .onAppear {
            withAnimation(.easeOut(duration: 1.2)) { fired = true }
        }
    }
}

// MARK: - Карточка для сторис

/// Новый уровень картинкой для сторис — в стиле итогов месяца.
struct LevelShareCard: View {
    static let size = CGSize(width: 360, height: 640)
    let level: Progression.Level

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack {
                Text("STRAIN")
                    .font(.system(size: 18, weight: .black, design: Theme.fontDesign))
                    .tracking(2)
                Spacer()
                Text("НОВЫЙ УРОВЕНЬ")
                    .font(.system(size: 11, weight: .heavy))
                    .tracking(1.4)
                    .foregroundStyle(Color.white.opacity(0.8))
            }
            Spacer(minLength: 0)
            LevelRing(level: level.level, fraction: 1, size: 170, lineWidth: 14)
                .frame(maxWidth: .infinity)
            VStack(alignment: .leading, spacing: 4) {
                Text("Уровень \(level.level)")
                    .font(.system(size: 20, weight: .heavy))
                    .foregroundStyle(Color.white.opacity(0.8))
                Text(level.title)
                    .font(.system(size: 44, weight: .black, design: Theme.fontDesign))
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
                Text(ProgressionText.experience(level.xp))
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Color.white.opacity(0.75))
            }
            Spacer(minLength: 0)
        }
        .foregroundStyle(.white)
        .padding(26)
        .frame(width: Self.size.width, height: Self.size.height, alignment: .topLeading)
        .background(
            LinearGradient(colors: TrainingRecapView.palette(1), startPoint: .topLeading, endPoint: .bottomTrailing)
        )
    }

    @MainActor
    static func render(_ level: Progression.Level) -> UIImage? {
        let renderer = ImageRenderer(content: LevelShareCard(level: level))
        renderer.scale = 3
        renderer.isOpaque = true
        return renderer.uiImage
    }
}
