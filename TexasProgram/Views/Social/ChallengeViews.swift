import SwiftUI
import UIKit

// MARK: - Челленджи с друзьями

extension ChallengeKind {
    var color: Color {
        switch self {
        case .workouts: return Theme.accent
        case .volume: return Color(red: 0.2, green: 0.56, blue: 0.98)
        case .streak: return Theme.success
        case .strength: return Color(red: 0.98, green: 0.72, blue: 0.16)
        }
    }

    /// Как считается счёт — под таблицей челленджа.
    var rules: String {
        switch self {
        case .workouts: return "Каждая отмеченная тренировка за время челленджа — одно очко."
        case .volume: return "Складываются килограммы всех рабочих подходов: вес × повторения."
        case .streak: return "Неделя засчитывается, если выполнена цель недели. Первая неделя без цели — серия остановилась. При равенстве выше тот, у кого больше тренировок."
        case .strength: return "Сравнивается лучший расчётный разовый максимум в каждом движении до старта и за время челленджа. Прибавка делится на вес тела — поэтому запиши вес в «Замерах веса»."
        }
    }
}

enum ChallengePlace {
    static func color(_ place: Int?) -> Color {
        switch place {
        // На светлом фоне золото и серебро темнее: яркие на белом не видны.
        case 1: return Color.themed(light: Color(red: 0.86, green: 0.62, blue: 0.0), dark: Color(red: 1, green: 0.8, blue: 0.25))
        case 2: return Color.themed(light: Color(white: 0.55), dark: Color(white: 0.8))
        case 3: return Color(red: 0.85, green: 0.55, blue: 0.3)
        default: return Color.ink.opacity(0.5)
        }
    }

    static func ordinal(_ place: Int) -> String { "\(place)-е место" }
}

private func caption(_ text: String) -> some View {
    Text(text.uppercased())
        .font(.system(size: 12, weight: .bold))
        .tracking(1.2)
        .foregroundStyle(Color.ink.opacity(0.45))
}

/// Челленджи над рейтингом недели: приглашения, идущие и законченные.
struct ChallengesSection: View {
    @State private var creating = false
    @State private var opened: SocialChallenge?
    @State private var showAllFinished = false

    private var store: ChallengeStore { .shared }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                caption("Челленджи")
                Spacer()
                Button {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    creating = true
                } label: {
                    Label("Новый", systemImage: "plus")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Theme.accent)
                }
                .buttonStyle(.pressable)
            }

            if let error = store.error, store.challenges.isEmpty {
                SocialErrorCard(message: error) { Task { await store.refresh() } }
            } else if !store.loaded {
                ProgressView().tint(.ink).frame(maxWidth: .infinity).padding(12)
            } else if store.challenges.isEmpty {
                emptyCard
            }

            ForEach(store.invites) { challenge in
                ChallengeInviteCard(challenge: challenge)
            }
            ForEach(store.running) { challenge in
                ChallengeCard(challenge: challenge) { opened = challenge }
            }
            let finished = store.finished
            ForEach(showAllFinished ? finished : Array(finished.prefix(2))) { challenge in
                ChallengeCard(challenge: challenge) { opened = challenge }
            }
            if finished.count > 2 && !showAllFinished {
                Button("Все законченные · \(finished.count)") { withAnimation(Motion.snappy) { showAllFinished = true } }
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.accent)
            }
        }
        .task { await store.refresh() }
        .sheet(isPresented: $creating) { NewChallengeSheet() }
        .sheet(item: $opened) { ChallengeDetailSheet(id: $0.id) }
    }

    private var emptyCard: some View {
        Button { creating = true } label: {
            HStack(spacing: 14) {
                Image(systemName: "flag.checkered")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundStyle(Theme.accent)
                    .frame(width: 46, height: 46)
                    .background(Theme.accent.opacity(0.15), in: Circle())
                VStack(alignment: .leading, spacing: 3) {
                    Text("Позови друзей помериться")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(.ink)
                    Text("Тренировки, тоннаж, недели без пропусков или рост силы — от недели до месяца.")
                        .font(.system(size: 13))
                        .foregroundStyle(Color.ink.opacity(0.5))
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            .padding(16)
            .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
        .buttonStyle(.pressable)
    }
}

// MARK: - Карточки

private struct KindIcon: View {
    let kind: ChallengeKind?
    var size: CGFloat = 42

    var body: some View {
        let color = kind?.color ?? Theme.accent
        Image(systemName: kind?.symbol ?? "flag.checkered")
            .font(.system(size: size * 0.42, weight: .bold))
            .foregroundStyle(color)
            .frame(width: size, height: size)
            .background(color.opacity(0.16), in: Circle())
    }
}

/// Сколько времени челленджа прошло.
private struct TimeBar: View {
    let challenge: SocialChallenge

    var body: some View {
        let now = Date().timeIntervalSince1970 * 1000
        let fraction = challenge.endsAt > challenge.startsAt
            ? min(1, max(0, (now - challenge.startsAt) / (challenge.endsAt - challenge.startsAt))) : 1
        GeometryReader { proxy in
            ZStack(alignment: .leading) {
                Capsule().fill(Color.ink.opacity(0.08))
                Capsule().fill(challenge.kind?.color ?? Theme.accent)
                    .frame(width: max(4, proxy.size.width * fraction))
            }
        }
        .frame(height: 4)
    }
}

struct ChallengeCard: View {
    let challenge: SocialChallenge
    var onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 12) {
                    KindIcon(kind: challenge.kind)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(challenge.title)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(.ink)
                        Text(challenge.timeLine())
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(Color.ink.opacity(0.5))
                    }
                    Spacer(minLength: 8)
                    if let place = challenge.me?.place {
                        VStack(spacing: 0) {
                            Text("\(place)")
                                .font(.system(size: 24, weight: .black, design: Theme.fontDesign).monospacedDigit())
                                .foregroundStyle(ChallengePlace.color(place))
                            Text("место")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundStyle(Color.ink.opacity(0.45))
                        }
                    }
                }
                if !challenge.isFinished { TimeBar(challenge: challenge) }
                VStack(spacing: 6) {
                    ForEach(challenge.joined.prefix(3), id: \.user.id) { member in
                        HStack(spacing: 8) {
                            Text("\(member.place ?? 0)")
                                .font(.system(size: 13, weight: .heavy).monospacedDigit())
                                .foregroundStyle(ChallengePlace.color(member.place))
                                .frame(width: 18)
                            AvatarView(imageId: member.user.avatar, username: member.user.username, size: 22)
                            Text(member.user.isMe ? "Ты" : "@\(member.user.username)")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(member.user.isMe ? Theme.accent : .ink)
                                .lineLimit(1)
                            Spacer(minLength: 6)
                            Text(member.detail.isEmpty ? "—" : member.detail)
                                .font(.system(size: 13, weight: .bold).monospacedDigit())
                                .foregroundStyle(Color.ink.opacity(0.8))
                                .lineLimit(1)
                        }
                    }
                }
            }
            .padding(16)
            .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
        .buttonStyle(PressableStyle(scale: 0.985))
    }
}

/// Приглашение: кто зовёт, во что и на сколько — принять или отказаться.
struct ChallengeInviteCard: View {
    let challenge: SocialChallenge
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                KindIcon(kind: challenge.kind)
                VStack(alignment: .leading, spacing: 2) {
                    Text("@\(challenge.owner?.username ?? "друг") зовёт в челлендж")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Color.ink.opacity(0.6))
                        .lineLimit(1)
                    Text("\(challenge.title) · \(challenge.weeks) нед.")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.ink)
                }
                Spacer(minLength: 0)
            }
            Text(challenge.kind?.subtitle ?? "")
                .font(.system(size: 13))
                .foregroundStyle(Color.ink.opacity(0.5))
            if let error {
                Text(error).font(.system(size: 12)).foregroundStyle(Theme.record)
            }
            HStack(spacing: 10) {
                Button { Task { await answer("decline") } } label: {
                    Text("Отказаться")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(.ink)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(Color.tone(0.18), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .buttonStyle(.pressable)
                Button { Task { await answer("join") } } label: {
                    Text("Принять")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(.canvas)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(Theme.accent, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .buttonStyle(.pressable)
            }
            .disabled(busy)
            .opacity(busy ? 0.6 : 1)
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(Theme.surface(.dark))
                .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(Theme.accent.opacity(0.4), lineWidth: 1))
        )
    }

    private func answer(_ action: String) async {
        busy = true
        defer { busy = false }
        do {
            let updated = try await SocialService.shared.challenge(challenge.id, action: action)
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            withAnimation(Motion.snappy) {
                if action == "join" { ChallengeStore.shared.replace(updated) } else { ChallengeStore.shared.remove(challenge.id) }
            }
            if action == "join" { await ChallengeStore.shared.syncAgain() }
        } catch {
            self.error = error.socialMessage
        }
    }
}

// MARK: - Челлендж целиком

struct ChallengeDetailSheet: View {
    let id: String
    @Environment(\.dismiss) private var dismiss
    @State private var confirmLeave = false
    @State private var shareImage: ShareImage?
    @State private var error: String?

    private var challenge: SocialChallenge? { ChallengeStore.shared.challenges.first { $0.id == id } }

    var body: some View {
        NavigationStack {
            ScrollView {
                if let challenge {
                    VStack(alignment: .leading, spacing: 14) {
                        hero(challenge)
                        if challenge.isFinished, let mine = challenge.me, let place = mine.place {
                            result(challenge, mine: mine, place: place)
                        }
                        table(challenge)
                        if !challenge.invited.isEmpty {
                            Text("Ждём ответа: " + challenge.invited.map { "@\($0.user.username)" }.joined(separator: ", "))
                                .font(.system(size: 13))
                                .foregroundStyle(Color.ink.opacity(0.5))
                        }
                        Text(challenge.kind?.rules ?? "")
                            .font(.system(size: 12))
                            .foregroundStyle(Color.ink.opacity(0.4))
                            .fixedSize(horizontal: false, vertical: true)
                        if let error {
                            Text(error).font(.system(size: 13)).foregroundStyle(Theme.record)
                        }
                        if !challenge.isFinished && challenge.myStatus == "joined" {
                            Button("Выйти из челленджа", role: .destructive) { confirmLeave = true }
                                .font(.system(size: 15, weight: .semibold))
                                .frame(maxWidth: .infinity)
                                .padding(.top, 8)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                    .padding(.bottom, 32)
                } else {
                    Text("Челлендж больше не доступен")
                        .foregroundStyle(Color.ink.opacity(0.5))
                        .padding(40)
                }
            }
            .scrollIndicators(.hidden)
            .background(Color.canvas.ignoresSafeArea())
            .navigationTitle("Челлендж")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Готово") { dismiss() }
                        .font(.body.weight(.bold))
                        .foregroundStyle(Theme.accent)
                }
            }
            .refreshable { await ChallengeStore.shared.syncAgain() }
            .confirmationDialog("Выйти из челленджа?", isPresented: $confirmLeave, titleVisibility: .visible) {
                Button("Выйти", role: .destructive) { Task { await leave() } }
            } message: {
                Text("Счёт пропадёт из таблицы, вернуться не получится.")
            }
            .sheet(item: $shareImage) { ShareSheet(items: [$0.image]) }
        }
    }

    private func hero(_ challenge: SocialChallenge) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                KindIcon(kind: challenge.kind, size: 52)
                VStack(alignment: .leading, spacing: 3) {
                    Text(challenge.title)
                        .font(.system(size: 22, weight: .heavy, design: Theme.fontDesign))
                        .foregroundStyle(.ink)
                    Text("\(challenge.weeks) нед. · \(RuDate.shortDayMonth(challenge.start)) – \(RuDate.shortDayMonth(challenge.end.addingTimeInterval(-1)))")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Color.ink.opacity(0.5))
                }
            }
            Text(challenge.kind?.subtitle ?? "")
                .font(.system(size: 14))
                .foregroundStyle(Color.ink.opacity(0.7))
            if !challenge.isFinished { TimeBar(challenge: challenge) }
            Text(challenge.timeLine())
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(challenge.kind?.color ?? Theme.accent)
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private func result(_ challenge: SocialChallenge, mine: SocialChallenge.Member, place: Int) -> some View {
        let xp = ChallengeScoring.xp(place: place, participants: challenge.joined.count, score: mine.score)
        return VStack(spacing: 10) {
            Image(systemName: place <= 3 ? "trophy.fill" : "flag.checkered")
                .font(.system(size: 34, weight: .bold))
                .foregroundStyle(ChallengePlace.color(place))
            Text(place == 1 ? "Победа!" : ChallengePlace.ordinal(place))
                .font(.system(size: 26, weight: .black, design: Theme.fontDesign))
                .foregroundStyle(.ink)
            Text("из \(challenge.joined.count) · \(mine.detail)")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Color.ink.opacity(0.6))
            if xp > 0 {
                Text(challenge.isFinal() ? "+\(xp) опыта" : "+\(xp) опыта — когда места станут окончательными")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Theme.accent)
                    .multilineTextAlignment(.center)
            }
            Button {
                if let image = ChallengeShareCard.render(challenge) { shareImage = ShareImage(image: image) }
            } label: {
                Label("Поделиться", systemImage: "square.and.arrow.up")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(.ink)
                    .frame(maxWidth: .infinity, minHeight: 46)
                    .background(Color.tone(0.18), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
            .buttonStyle(.pressable)
        }
        .padding(18)
        .frame(maxWidth: .infinity)
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private func table(_ challenge: SocialChallenge) -> some View {
        VStack(spacing: 0) {
            ForEach(challenge.joined, id: \.user.id) { member in
                HStack(spacing: 12) {
                    Text("\(member.place ?? 0)")
                        .font(.system(size: 16, weight: .black, design: Theme.fontDesign).monospacedDigit())
                        .foregroundStyle(ChallengePlace.color(member.place))
                        .frame(width: 26)
                    AvatarView(imageId: member.user.avatar, username: member.user.username, size: 38)
                    HStack(spacing: 6) {
                        Text(member.user.isMe ? "Ты" : "@\(member.user.username)")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundStyle(.ink)
                            .lineLimit(1)
                        LevelChip(level: member.user.level)
                    }
                    Spacer(minLength: 8)
                    Text(member.detail.isEmpty ? "—" : member.detail)
                        .font(.system(size: 14, weight: .heavy, design: Theme.fontDesign).monospacedDigit())
                        .foregroundStyle(.ink)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(member.user.isMe ? Theme.accent.opacity(0.1) : Color.clear)
                if member.user.id != challenge.joined.last?.user.id {
                    Divider().overlay(Color.ink.opacity(0.06)).padding(.leading, 90)
                }
            }
        }
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private func leave() async {
        do {
            _ = try await SocialService.shared.challenge(id, action: "leave")
            ChallengeStore.shared.remove(id)
            dismiss()
        } catch {
            self.error = error.socialMessage
        }
    }
}

// MARK: - Новый челлендж

struct NewChallengeSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var kind: ChallengeKind = .workouts
    @State private var weeks = 2
    @State private var friends: [SocialUserShort] = []
    @State private var picked: Set<String> = []
    @State private var loading = true
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    caption("Во что меряемся")
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                        ForEach(ChallengeKind.allCases) { item in kindTile(item) }
                    }
                    Text(kind.rules)
                        .font(.system(size: 12))
                        .foregroundStyle(Color.ink.opacity(0.45))
                        .fixedSize(horizontal: false, vertical: true)

                    caption("Сколько")
                    Picker("Недели", selection: $weeks) {
                        ForEach(1...4, id: \.self) { Text("\($0) нед.").tag($0) }
                    }
                    .pickerStyle(.segmented)

                    caption("Кого позвать")
                    friendsList

                    if let error {
                        Text(error).font(.system(size: 13)).foregroundStyle(Theme.record)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 110)
            }
            .scrollIndicators(.hidden)
            .background(Color.canvas.ignoresSafeArea())
            .safeAreaInset(edge: .bottom) { startButton }
            .navigationTitle("Новый челлендж")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Отмена") { dismiss() }.foregroundStyle(.ink)
                }
            }
            .task { await loadFriends() }
        }
    }

    private func kindTile(_ item: ChallengeKind) -> some View {
        let selected = kind == item
        return Button {
            UISelectionFeedbackGenerator().selectionChanged()
            withAnimation(Motion.snappy) { kind = item }
        } label: {
            VStack(alignment: .leading, spacing: 8) {
                Image(systemName: item.symbol)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(item.color)
                Text(item.title)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(.ink)
                Text(item.subtitle)
                    .font(.system(size: 11))
                    .foregroundStyle(Color.ink.opacity(0.5))
                    .lineLimit(3)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 0)
            }
            .padding(12)
            .frame(maxWidth: .infinity, minHeight: 128, alignment: .topLeading)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(selected ? item.color.opacity(0.16) : Theme.surface(.dark))
                    .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(selected ? item.color : Color.clear, lineWidth: 1.5))
            )
        }
        .buttonStyle(.pressable)
    }

    @ViewBuilder
    private var friendsList: some View {
        if loading {
            ProgressView().tint(.ink).frame(maxWidth: .infinity).padding(16)
        } else if friends.isEmpty {
            Text("Подпишись на друзей во вкладке «Люди» — звать можно тех, на кого подписан.")
                .font(.system(size: 14))
                .foregroundStyle(Color.ink.opacity(0.5))
        } else {
            VStack(spacing: 0) {
                ForEach(friends) { friend in
                    Button {
                        UISelectionFeedbackGenerator().selectionChanged()
                        if picked.contains(friend.username) { picked.remove(friend.username) } else { picked.insert(friend.username) }
                    } label: {
                        HStack(spacing: 12) {
                            AvatarView(imageId: friend.avatar, username: friend.username, size: 38)
                            Text("@\(friend.username)")
                                .font(.system(size: 15, weight: .bold))
                                .foregroundStyle(.ink)
                                .lineLimit(1)
                            LevelChip(level: friend.level)
                            Spacer()
                            Image(systemName: picked.contains(friend.username) ? "checkmark.circle.fill" : "circle")
                                .font(.system(size: 22))
                                .foregroundStyle(picked.contains(friend.username) ? Theme.accent : Color.ink.opacity(0.3))
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 9)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(PressableStyle(scale: 0.985))
                }
            }
            .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
    }

    private var startButton: some View {
        Button { Task { await start() } } label: {
            Text(picked.isEmpty ? "Выбери друзей" : "Позвать \(picked.count) и начать")
                .font(.system(size: 17, weight: .bold))
                .foregroundStyle(.canvas)
                .frame(maxWidth: .infinity, minHeight: 54)
                .background(Theme.accent, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.pressable)
        .disabled(picked.isEmpty || busy)
        .opacity(picked.isEmpty || busy ? 0.5 : 1)
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
        .background(Color.black.opacity(0.001))
    }

    private func loadFriends() async {
        defer { loading = false }
        guard let me = SocialService.shared.me else { return }
        friends = (try? await SocialService.shared.following(of: me.username)) ?? []
    }

    private func start() async {
        busy = true
        defer { busy = false }
        do {
            let created = try await SocialService.shared.createChallenge(kind: kind, weeks: weeks, invite: picked.sorted())
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            ChallengeStore.shared.replace(created)
            dismiss()
            await ChallengeStore.shared.syncAgain()
        } catch {
            self.error = error.socialMessage
        }
    }
}

// MARK: - Победители недель

struct WeekWinnersSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var winners: [SocialWeekWinner] = []
    @State private var loading = true
    @State private var error: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let error, winners.isEmpty {
                        SocialErrorCard(message: error) { Task { await load() } }
                    } else if loading {
                        ProgressView().tint(.ink).frame(maxWidth: .infinity).padding(20)
                    } else if winners.isEmpty {
                        Text("Пока никто не выигрывал неделю. Победитель — у кого больше тренировок, при равенстве — больше тоннаж.")
                            .font(.system(size: 14))
                            .foregroundStyle(Color.ink.opacity(0.5))
                    } else {
                        tally
                        VStack(spacing: 0) {
                            ForEach(winners) { winner in
                                HStack(spacing: 12) {
                                    Image(systemName: "crown.fill")
                                        .font(.system(size: 14))
                                        .foregroundStyle(ChallengePlace.color(1))
                                    AvatarView(imageId: winner.user.avatar, username: winner.user.username, size: 34)
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(winner.user.isMe ? "Ты" : "@\(winner.user.username)")
                                            .font(.system(size: 15, weight: .bold))
                                            .foregroundStyle(.ink)
                                        Text(Self.weekTitle(winner.week))
                                            .font(.system(size: 12))
                                            .foregroundStyle(Color.ink.opacity(0.45))
                                    }
                                    Spacer()
                                    Text("\(winner.workouts) трен.")
                                        .font(.system(size: 14, weight: .heavy).monospacedDigit())
                                        .foregroundStyle(.ink)
                                }
                                .padding(.horizontal, 14)
                                .padding(.vertical, 10)
                            }
                        }
                        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 32)
            }
            .background(Color.canvas.ignoresSafeArea())
            .navigationTitle("Победители недель")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Готово") { dismiss() }
                        .font(.body.weight(.bold))
                        .foregroundStyle(Theme.accent)
                }
            }
            .task { await load() }
        }
    }

    private struct Tally: Identifiable {
        let user: SocialUserShort
        let count: Int
        var id: String { user.id }
    }

    /// Кто сколько недель выиграл — сверху самые частые победители.
    private var tallies: [Tally] {
        var counts: [String: Tally] = [:]
        for winner in winners {
            let previous = counts[winner.user.id]?.count ?? 0
            counts[winner.user.id] = Tally(user: winner.user, count: previous + 1)
        }
        return counts.values.sorted { left, right in
            if left.count != right.count { return left.count > right.count }
            return left.user.username < right.user.username
        }
    }

    private var tally: some View {
        ScrollView(.horizontal) {
            HStack(spacing: 10) {
                ForEach(tallies) { item in
                    TallyTile(user: item.user, count: item.count)
                }
            }
        }
        .scrollIndicators(.hidden)
    }

    private struct TallyTile: View {
        let user: SocialUserShort
        let count: Int

        var body: some View {
            VStack(spacing: 6) {
                AvatarView(imageId: user.avatar, username: user.username, size: 44)
                Text(user.isMe ? "Ты" : "@\(user.username)")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(.ink)
                    .lineLimit(1)
                Text("👑 \(count)")
                    .font(.system(size: 13, weight: .heavy))
                    .foregroundStyle(ChallengePlace.color(1))
            }
            .frame(width: 84)
            .padding(.vertical, 12)
            .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
    }

    /// «2026-W42» → «12 – 18 окт.».
    static func weekTitle(_ key: String, calendar: Calendar = WeeklyStatsBuilder.calendar) -> String {
        let parts = key.split(separator: "-")
        guard parts.count == 2, let year = Int(parts[0]), let week = Int(parts[1].dropFirst()),
              let start = calendar.date(from: DateComponents(weekday: 2, weekOfYear: week, yearForWeekOfYear: year)),
              let end = calendar.date(byAdding: .day, value: 6, to: start) else { return key }
        return "\(RuDate.shortDayMonth(start, calendar: calendar)) – \(RuDate.shortDayMonth(end, calendar: calendar))"
    }

    private func load() async {
        loading = true
        defer { loading = false }
        do {
            winners = try await SocialService.shared.leaderboardHistory()
            error = nil
        } catch {
            self.error = error.socialMessage
        }
    }
}

// MARK: - Карточка для сторис

/// Итог челленджа картинкой для сторис.
struct ChallengeShareCard: View {
    static let size = CGSize(width: 360, height: 640)
    let challenge: SocialChallenge

    var body: some View {
        darkContent.alwaysDark()
    }

    @ViewBuilder
    private var darkContent: some View {
        let place = challenge.me?.place ?? 0
        VStack(alignment: .leading, spacing: 18) {
            HStack {
                Text("STRAIN")
                    .font(.system(size: 18, weight: .black, design: Theme.fontDesign))
                    .tracking(2)
                Spacer()
                Text("ЧЕЛЛЕНДЖ")
                    .font(.system(size: 11, weight: .heavy))
                    .tracking(1.4)
                    .foregroundStyle(Color.ink.opacity(0.8))
            }
            Spacer(minLength: 0)
            Image(systemName: place <= 3 ? "trophy.fill" : "flag.checkered")
                .font(.system(size: 76, weight: .bold))
                .foregroundStyle(ChallengePlace.color(place))
                .frame(maxWidth: .infinity)
            VStack(alignment: .leading, spacing: 4) {
                Text(challenge.title)
                    .font(.system(size: 20, weight: .heavy))
                    .foregroundStyle(Color.ink.opacity(0.8))
                Text(place == 1 ? "Победа" : ChallengePlace.ordinal(place))
                    .font(.system(size: 48, weight: .black, design: Theme.fontDesign))
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
                Text("из \(challenge.joined.count) · \(challenge.weeks) нед. · \(challenge.me?.detail ?? "")")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Color.ink.opacity(0.75))
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            VStack(spacing: 8) {
                ForEach(challenge.joined.prefix(3), id: \.user.id) { member in
                    HStack {
                        Text("\(member.place ?? 0)")
                            .font(.system(size: 15, weight: .black).monospacedDigit())
                            .foregroundStyle(ChallengePlace.color(member.place))
                            .frame(width: 20, alignment: .leading)
                        Text("@\(member.user.username)")
                            .font(.system(size: 15, weight: .bold))
                            .lineLimit(1)
                        Spacer()
                        Text(member.detail)
                            .font(.system(size: 15, weight: .heavy).monospacedDigit())
                    }
                }
            }
            .padding(14)
            .background(Color.black.opacity(0.22), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            Spacer(minLength: 0)
        }
        .foregroundStyle(.ink)
        .padding(26)
        .frame(width: Self.size.width, height: Self.size.height, alignment: .topLeading)
        .background(
            LinearGradient(colors: TrainingRecapView.palette(3), startPoint: .topLeading, endPoint: .bottomTrailing)
        )
    }

    @MainActor
    static func render(_ challenge: SocialChallenge) -> UIImage? {
        let renderer = ImageRenderer(content: ChallengeShareCard(challenge: challenge))
        renderer.scale = 3
        renderer.isOpaque = true
        return renderer.uiImage
    }
}
