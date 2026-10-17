import SwiftUI

// MARK: - Тренируется сейчас

/// Зелёная точка, которая расходится кругами, — «идёт прямо сейчас».
struct LivePulse: View {
    var size: CGFloat = 8
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var expanded = false

    var body: some View {
        Circle()
            .fill(Theme.success)
            .frame(width: size, height: size)
            .background(
                Circle()
                    .fill(Theme.success.opacity(0.4))
                    .scaleEffect(expanded ? 2.4 : 1)
                    .opacity(expanded ? 0 : 1)
            )
            .onAppear {
                guard !reduceMotion else { return }
                withAnimation(.easeOut(duration: 1.3).repeatForever(autoreverses: false)) { expanded = true }
            }
    }
}

/// «Тренируется · Присед · 3/5» — в шапке профиля и переписки.
struct LiveBadge: View {
    let live: SocialLive

    var body: some View {
        HStack(spacing: 7) {
            LivePulse()
            Text(live.line.isEmpty ? "Тренируется" : "Тренируется · \(live.line)")
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(Theme.success)
                .lineLimit(1)
        }
        .padding(.horizontal, 11)
        .padding(.vertical, 6)
        .background(Theme.success.opacity(0.14), in: Capsule())
    }
}

/// Полоса над лентой: кто из подписок сейчас в зале и на каком подходе.
struct LiveFriendsStrip: View {
    let friends: [SocialLiveFriend]
    var onOpen: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 7) {
                LivePulse(size: 7)
                Text("СЕЙЧАС ТРЕНИРУЮТСЯ")
                    .font(.system(size: 12, weight: .bold))
                    .tracking(1.2)
                    .foregroundStyle(Color.white.opacity(0.5))
            }
            ScrollView(.horizontal) {
                HStack(spacing: 10) {
                    ForEach(friends) { friend in
                        Button { onOpen(friend.user.username) } label: { card(friend) }
                            .buttonStyle(.pressable)
                    }
                }
            }
            .scrollIndicators(.hidden)
            .scrollClipDisabled()
        }
    }

    private func card(_ friend: SocialLiveFriend) -> some View {
        let live = friend.live
        return VStack(alignment: .leading, spacing: 9) {
            HStack(spacing: 9) {
                AvatarView(imageId: friend.user.avatar, username: friend.user.username, size: 34)
                    .overlay(Circle().strokeBorder(Theme.success, lineWidth: 2).padding(-3))
                VStack(alignment: .leading, spacing: 1) {
                    Text("@\(friend.user.username)")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                    // Минуты с начала тренировки тикают сами.
                    TimelineView(.periodic(from: .now, by: 30)) { context in
                        Text(SocialFormat.duration(Int(max(0, context.date.timeIntervalSince(live.startDate)))))
                            .font(.system(size: 12, weight: .semibold).monospacedDigit())
                            .foregroundStyle(Theme.success)
                    }
                }
            }
            Text(live.exercise.isEmpty ? live.title : live.exercise)
                .font(.system(size: 15, weight: .heavy, design: Theme.fontDesign))
                .foregroundStyle(.white)
                .lineLimit(1)
            if live.total > 0 {
                HStack(spacing: 8) {
                    SetDots(done: live.done, total: live.total)
                    Text("\(live.done)/\(live.total)")
                        .font(.system(size: 12, weight: .bold).monospacedDigit())
                        .foregroundStyle(Color.white.opacity(0.6))
                }
            }
            if !live.title.isEmpty && !live.exercise.isEmpty {
                Text(live.title)
                    .font(.system(size: 12))
                    .foregroundStyle(Color.white.opacity(0.45))
                    .lineLimit(1)
            }
        }
        .padding(12)
        .frame(width: 196, alignment: .leading)
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(Theme.success.opacity(0.25), lineWidth: 1))
    }
}

/// Подходы упражнения точками: сделанные — зелёные.
private struct SetDots: View {
    let done: Int
    let total: Int

    var body: some View {
        HStack(spacing: 4) {
            ForEach(0..<min(total, 10), id: \.self) { index in
                Capsule()
                    .fill(index < done ? Theme.success : Color.white.opacity(0.14))
                    .frame(width: total > 6 ? 8 : 14, height: 5)
            }
        }
    }
}

// MARK: - Активность

/// Колокольчик: кто поставил лайк, прокомментировал, ответил или подписался.
struct ActivityView: View {
    @Binding var path: NavigationPath

    @State private var items: [SocialActivityItem] = []
    @State private var loading = true
    @State private var error: String?

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                if let error, items.isEmpty {
                    SocialErrorCard(message: error) { Task { await reload() } }.padding(16)
                } else if items.isEmpty && !loading {
                    empty
                }
                ForEach(items) { item in
                    ActivityRow(
                        item: item,
                        onOpenActor: { path.append(FeedRoute.user(item.actor.username)) },
                        onOpen: { open(item) }
                    )
                    Divider().overlay(Color.white.opacity(0.06)).padding(.leading, 70)
                }
                if loading && items.isEmpty {
                    ProgressView().tint(.white).padding(24)
                }
                Spacer(minLength: 120)
            }
        }
        .refreshable { await reload() }
        .background(Color.black.ignoresSafeArea())
        .navigationTitle("Активность")
        .navigationBarTitleDisplayMode(.inline)
        .task { await reload() }
        .preferredColorScheme(.dark)
    }

    private var empty: some View {
        VStack(spacing: 10) {
            Image(systemName: "bell")
                .font(.system(size: 34, weight: .semibold))
                .foregroundStyle(Color.white.opacity(0.35))
            Text("Пока тихо")
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(.white)
            Text("Здесь появятся лайки, комментарии, ответы и новые подписчики.")
                .font(.system(size: 14))
                .foregroundStyle(Color.white.opacity(0.55))
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(30)
    }

    private func open(_ item: SocialActivityItem) {
        if item.kind == .follow {
            path.append(FeedRoute.user(item.actor.username))
        } else if let id = item.postId {
            path.append(FeedRoute.postId(id))
        }
    }

    private func reload() async {
        loading = true
        defer { loading = false }
        do {
            items = try await SocialService.shared.activity().items
            error = nil
            // Новые подсвечены в этом списке, а точка на колокольчике гаснет.
            await SocialService.shared.markActivitySeen()
        } catch {
            self.error = error.socialMessage
        }
    }
}

struct ActivityRow: View {
    let item: SocialActivityItem
    var onOpenActor: () -> Void
    var onOpen: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Button(action: onOpenActor) {
                AvatarView(imageId: item.actor.avatar, username: item.actor.username, size: 44)
                    .overlay(alignment: .bottomTrailing) { kindIcon.offset(x: 4, y: 4) }
            }
            .buttonStyle(.plain)

            Button(action: onOpen) {
                HStack(alignment: .top, spacing: 8) {
                    VStack(alignment: .leading, spacing: 3) {
                        line
                            .foregroundStyle(.white)
                            .lineLimit(3)
                            .multilineTextAlignment(.leading)
                            .fixedSize(horizontal: false, vertical: true)
                        Text(SocialFormat.ago(item.date))
                            .font(.system(size: 12))
                            .foregroundStyle(Color.white.opacity(0.4))
                    }
                    Spacer(minLength: 0)
                    if item.unseen {
                        Circle().fill(Theme.accent).frame(width: 9, height: 9).padding(.top, 5)
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(item.unseen ? Theme.accent.opacity(0.07) : Color.clear)
    }

    private var line: Text {
        Text("\(Text("@\(item.actor.username)").bold()) \(item.action)")
            .font(.system(size: 15))
    }

    private var kindIcon: some View {
        let (symbol, color): (String, Color) = {
            switch item.kind {
            case .like: return ("heart.fill", Color(red: 1, green: 0.27, blue: 0.36))
            case .comment: return ("bubble.left.fill", Theme.accent)
            case .reply: return ("arrowshape.turn.up.left.fill", Theme.accent)
            case .follow: return ("person.fill.badge.plus", Theme.success)
            }
        }()
        return Image(systemName: symbol)
            .font(.system(size: 10, weight: .bold))
            .foregroundStyle(.white)
            .frame(width: 21, height: 21)
            .background(color, in: Circle())
            .overlay(Circle().strokeBorder(Color.black, lineWidth: 2))
    }
}

/// Пост по ссылке из активности: сначала загружаем, потом показываем.
struct PostLoaderView: View {
    let id: String
    @Binding var path: NavigationPath

    @State private var post: SocialPost?
    @State private var error: String?

    var body: some View {
        Group {
            if let post {
                PostDetailView(post: post, openAuthor: { path.append(FeedRoute.user($0)) })
            } else if let error {
                SocialErrorCard(message: error) { Task { await load() } }
                    .padding(16)
                    .frame(maxHeight: .infinity, alignment: .top)
            } else {
                ProgressView().tint(.white)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black.ignoresSafeArea())
        .task { if post == nil { await load() } }
        .preferredColorScheme(.dark)
    }

    private func load() async {
        do {
            post = try await SocialService.shared.post(id)
            error = nil
        } catch {
            self.error = error.socialMessage
        }
    }
}

// MARK: - Рейтинг недели

/// Чем меряемся в рейтинге.
enum LeaderboardMetric: String, CaseIterable, Identifiable {
    case volume = "Тоннаж"
    case workouts = "Тренировки"
    case streak = "Серия"
    case records = "Рекорды"

    var id: String { rawValue }

    func value(_ stats: SocialWeekStats) -> Double {
        switch self {
        case .volume: return stats.volumeKg
        case .workouts: return Double(stats.workouts)
        case .streak: return Double(stats.streakWeeks)
        case .records: return Double(stats.records)
        }
    }

    func format(_ stats: SocialWeekStats) -> String {
        switch self {
        case .volume:
            return AchievementKind.iron.format(stats.volumeKg)
        case .workouts:
            return "\(stats.workouts) \(RuPlural.word(stats.workouts, "тренировка", "тренировки", "тренировок"))"
        case .streak:
            return "\(stats.streakWeeks) \(RuPlural.word(stats.streakWeeks, "неделя", "недели", "недель"))"
        case .records:
            return "\(stats.records) \(RuPlural.word(stats.records, "рекорд", "рекорда", "рекордов"))"
        }
    }

    /// Места: больше — выше; поровну — по тоннажу, потом по имени.
    static func ranked(_ entries: [SocialLeaderboardEntry], by metric: LeaderboardMetric, now: Date = Date()) -> [SocialLeaderboardEntry] {
        entries
            .map { SocialLeaderboardEntry(user: $0.user, stats: WeeklyStatsBuilder.current($0.stats, now: now)) }
            .sorted { a, b in
                let left = metric.value(a.stats), right = metric.value(b.stats)
                if left != right { return left > right }
                if a.stats.volumeKg != b.stats.volumeKg { return a.stats.volumeKg > b.stats.volumeKg }
                return a.user.username < b.user.username
            }
    }
}

/// Рейтинг недели среди подписок: тоннаж, тренировки, серия и рекорды.
struct LeaderboardView: View {
    var onOpen: (String) -> Void

    @State private var entries: [SocialLeaderboardEntry] = []
    @State private var metric: LeaderboardMetric = .volume
    @State private var loading = true
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            ScrollView(.horizontal) {
                HStack(spacing: 8) {
                    ForEach(LeaderboardMetric.allCases) { item in
                        Button { withAnimation(Motion.snappy) { metric = item } } label: {
                            Text(item.rawValue)
                                .font(.system(size: 14, weight: .bold))
                                .foregroundStyle(metric == item ? Color.black : Color.white)
                                .padding(.horizontal, 14)
                                .frame(height: 34)
                                .background(metric == item ? Color.white : Color(white: 0.14), in: Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .scrollIndicators(.hidden)

            Text(Self.weekTitle())
                .font(.system(size: 12, weight: .bold))
                .tracking(1.2)
                .foregroundStyle(Color.white.opacity(0.45))

            let ranked = LeaderboardMetric.ranked(entries, by: metric)
            if let error, entries.isEmpty {
                SocialErrorCard(message: error) { Task { await reload() } }
            } else if loading && entries.isEmpty {
                ProgressView().tint(.white).frame(maxWidth: .infinity).padding(20)
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(ranked.enumerated()), id: \.element.id) { index, entry in
                        LeaderboardRow(place: index + 1, entry: entry, metric: metric) { onOpen(entry.user.username) }
                        if entry.id != ranked.last?.id {
                            Divider().overlay(Color.white.opacity(0.06)).padding(.leading, 98)
                        }
                    }
                }
                .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                if entries.count < 2 {
                    Text("Подпишись на друзей — и меряйтесь тоннажем, тренировками и рекордами каждую неделю.")
                        .font(.system(size: 14))
                        .foregroundStyle(Color.white.opacity(0.5))
                }
            }
            Text("Считаются все тренировки в приложении, а не только опубликованные. Неделя — с понедельника.")
                .font(.system(size: 12))
                .foregroundStyle(Color.white.opacity(0.35))
        }
        .task { await reload() }
    }

    static func weekTitle(now: Date = Date()) -> String {
        let calendar = WeeklyStatsBuilder.calendar
        guard let week = calendar.dateInterval(of: .weekOfYear, for: now),
              let end = calendar.date(byAdding: .day, value: 6, to: week.start) else { return "ЭТА НЕДЕЛЯ" }
        return "НЕДЕЛЯ · \(RuDate.shortDayMonth(week.start, calendar: calendar)) – \(RuDate.shortDayMonth(end, calendar: calendar))".uppercased()
    }

    func reload() async {
        loading = true
        defer { loading = false }
        do {
            entries = try await SocialService.shared.leaderboard()
            error = nil
        } catch {
            self.error = error.socialMessage
        }
    }
}

struct LeaderboardRow: View {
    let place: Int
    let entry: SocialLeaderboardEntry
    let metric: LeaderboardMetric
    var onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            HStack(spacing: 12) {
                Text("\(place)")
                    .font(.system(size: 16, weight: .black, design: Theme.fontDesign).monospacedDigit())
                    .foregroundStyle(placeColor)
                    .frame(width: 30)
                AvatarView(imageId: entry.user.avatar, username: entry.user.username, size: 42)
                    .overlay {
                        if place <= 3 && metric.value(entry.stats) > 0 {
                            Circle().strokeBorder(placeColor, lineWidth: 2).padding(-3)
                        }
                    }
                VStack(alignment: .leading, spacing: 2) {
                    Text(entry.user.isMe ? "Ты" : "@\(entry.user.username)")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                    Text(secondary)
                        .font(.system(size: 12))
                        .foregroundStyle(Color.white.opacity(0.45))
                        .lineLimit(1)
                }
                Spacer(minLength: 8)
                Text(metric.format(entry.stats))
                    .font(.system(size: 15, weight: .heavy, design: Theme.fontDesign).monospacedDigit())
                    .foregroundStyle(metric.value(entry.stats) > 0 ? Color.white : Color.white.opacity(0.35))
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(entry.user.isMe ? Theme.accent.opacity(0.1) : Color.clear)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    /// Остальные цифры недели мелко — чтобы было видно, за счёт чего место.
    private var secondary: String {
        LeaderboardMetric.allCases.filter { $0 != metric }.map { $0.format(entry.stats) }.joined(separator: " · ")
    }

    private var placeColor: Color {
        guard metric.value(entry.stats) > 0 else { return Color.white.opacity(0.4) }
        switch place {
        case 1: return Color(red: 1, green: 0.8, blue: 0.25)
        case 2: return Color(white: 0.78)
        case 3: return Color(red: 0.85, green: 0.55, blue: 0.3)
        default: return Color.white.opacity(0.5)
        }
    }
}
