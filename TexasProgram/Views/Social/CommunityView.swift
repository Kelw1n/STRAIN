import SwiftUI

/// Вкладка «Друзья»: поиск по юзернейму, люди, которые недавно тренировались,
/// рейтинг недели и личные сообщения.
struct CommunityView: View {
    var onCreateProfile: () -> Void

    private enum Part: Hashable { case people, rating, messages }

    @State private var section: Part = .people
    @State private var query = ""
    @State private var people: [SocialUserShort] = []
    @State private var loading = false
    @State private var error: String?
    @State private var path = NavigationPath()
    @State private var ratingVersion = 0

    private var social: SocialService { .shared }

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    if social.isSignedIn {
                        Picker("Раздел", selection: $section) {
                            Text("Люди").tag(Part.people)
                            Text(social.challengeInvites > 0 ? "Рейтинг · \(social.challengeInvites)" : "Рейтинг").tag(Part.rating)
                            Text(social.unreadMessages > 0 ? "Чаты · \(social.unreadMessages)" : "Чаты").tag(Part.messages)
                        }
                        .pickerStyle(.segmented)
                    }
                    if section == .messages && social.isSignedIn {
                        ChatListView { path.append(FeedRoute.chat($0)) }
                    } else if section == .rating && social.isSignedIn {
                        ChallengesSection()
                            .id(ratingVersion)
                        LeaderboardView { path.append(FeedRoute.user($0)) }
                            .id(ratingVersion)
                    } else {
                        peopleSection
                    }
                    Spacer(minLength: 110)
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .tabScrollAnchor(.community)
            }
            .scrollDismissesKeyboard(.interactively)
            .refreshable {
                if section == .people { await search() } else { await social.refreshUnread() }
                if section == .rating { ratingVersion += 1 }
            }
            .background(Color.black.ignoresSafeArea())
            .navigationTitle("Друзья")
            .navigationBarTitleDisplayMode(.large)
            .navigationDestination(for: FeedRoute.self) { route in
                route.destination(path: $path) { _, _ in }
            }
        }
        .preferredColorScheme(.dark)
        .onTabReselect(.community) {
            if path.isEmpty { TabReselection.shared.scrollToTop(.community) } else { path = NavigationPath() }
        }
        .task(id: query) {
            // Небольшая пауза: не дёргаем сервер на каждую букву.
            try? await Task.sleep(nanoseconds: query.isEmpty ? 0 : 350_000_000)
            guard !Task.isCancelled else { return }
            await search()
        }
    }

    @ViewBuilder
    private var peopleSection: some View {
        searchField
        if !social.isSignedIn {
            Button(action: onCreateProfile) {
                HStack {
                    Image(systemName: "person.crop.circle.badge.plus")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(Theme.accent)
                    Text("Создай профиль, чтобы подписываться на друзей")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(.white)
                        .multilineTextAlignment(.leading)
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(Color.white.opacity(0.35))
                }
                .padding(16)
                .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            }
            .buttonStyle(.pressable)
        }

        Text(query.isEmpty ? "НЕДАВНО ТРЕНИРОВАЛИСЬ" : "НАЙДЕНО")
            .font(.system(size: 12, weight: .bold))
            .tracking(1.2)
            .foregroundStyle(Color.white.opacity(0.45))
            .padding(.top, 6)

        if let error, people.isEmpty {
            SocialErrorCard(message: error) { Task { await search() } }
        } else if people.isEmpty && !loading {
            Text(query.isEmpty ? "Пока никого. Позови друзей в STRAIN — их юзернеймы появятся здесь." : "Никого с таким юзернеймом")
                .font(.system(size: 14))
                .foregroundStyle(Color.white.opacity(0.5))
                .padding(.vertical, 12)
        }

        VStack(spacing: 0) {
            ForEach(people) { person in
                PersonRow(person: person, onOpen: { path.append(FeedRoute.user(person.username)) }) { updated in
                    if let index = people.firstIndex(where: { $0.id == updated.id }) { people[index] = updated }
                }
                if person.id != people.last?.id {
                    Divider().overlay(Color.white.opacity(0.06)).padding(.leading, 64)
                }
            }
        }
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .opacity(people.isEmpty ? 0 : 1)

        if loading && people.isEmpty {
            ProgressView().tint(.white).frame(maxWidth: .infinity).padding(20)
        }
    }

    private var searchField: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(Color.white.opacity(0.45))
            TextField("Найти по юзернейму", text: $query)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .font(.system(size: 16))
                .foregroundStyle(.white)
            if !query.isEmpty {
                Button { query = "" } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(Color.white.opacity(0.4))
                }
            }
        }
        .padding(.horizontal, 14)
        .frame(height: 46)
        .background(Color(white: 0.12), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func search() async {
        loading = true
        defer { loading = false }
        do {
            people = try await social.searchUsers(SocialFormat.normalizedUsername(query)).filter { !$0.isMe }
            error = nil
        } catch {
            self.error = error.socialMessage
        }
    }
}

/// Человек в списке: аватар, юзернейм, о себе и кнопка подписки.
struct PersonRow: View {
    let person: SocialUserShort
    var onOpen: () -> Void
    var onChange: (SocialUserShort) -> Void

    @State private var busy = false

    var body: some View {
        HStack(spacing: 12) {
            Button(action: onOpen) {
                HStack(spacing: 12) {
                    AvatarView(imageId: person.avatar, username: person.username, size: 44)
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 6) {
                            Text("@\(person.username)")
                                .font(.system(size: 15, weight: .bold))
                                .foregroundStyle(.white)
                                .lineLimit(1)
                            LevelChip(level: person.level)
                        }
                        if !person.bio.isEmpty {
                            Text(person.bio)
                                .font(.system(size: 13))
                                .foregroundStyle(Color.white.opacity(0.5))
                                .lineLimit(1)
                        }
                    }
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(PressableStyle(scale: 0.985))

            if SocialService.shared.isSignedIn && !person.isMe {
                FollowButton(isFollowing: person.isFollowing, compact: true) { toggle() }
                    .disabled(busy)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
    }

    private func toggle() {
        busy = true
        var updated = person
        updated.isFollowing.toggle()
        onChange(updated)
        Task {
            defer { busy = false }
            do {
                _ = try await SocialService.shared.setFollowing(person.username, updated.isFollowing)
            } catch {
                onChange(person)
            }
        }
    }
}

/// Подписчики или подписки человека.
struct PeopleListView: View {
    let title: String
    let load: () async throws -> [SocialUserShort]
    @Binding var path: NavigationPath

    @State private var people: [SocialUserShort] = []
    @State private var loading = true
    @State private var error: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                if let error {
                    SocialErrorCard(message: error) { Task { await reload() } }.padding(16)
                } else if people.isEmpty && !loading {
                    Text("Пока никого")
                        .font(.system(size: 15))
                        .foregroundStyle(Color.white.opacity(0.5))
                        .padding(30)
                }
                ForEach(people) { person in
                    PersonRow(person: person, onOpen: { path.append(FeedRoute.user(person.username)) }) { updated in
                        if let index = people.firstIndex(where: { $0.id == updated.id }) { people[index] = updated }
                    }
                    Divider().overlay(Color.white.opacity(0.06)).padding(.leading, 64)
                }
                if loading { ProgressView().tint(.white).padding(24) }
                Spacer(minLength: 120)
            }
        }
        .background(Color.black.ignoresSafeArea())
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .task { await reload() }
        .preferredColorScheme(.dark)
    }

    private func reload() async {
        loading = true
        defer { loading = false }
        do {
            people = try await load()
            error = nil
        } catch {
            self.error = error.socialMessage
        }
    }
}
