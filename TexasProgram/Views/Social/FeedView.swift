import SwiftUI

/// Вкладка «Главное»: тренировки всех, кто пользуется STRAIN, или только подписок.
struct FeedView: View {
    var onFindPeople: () -> Void
    var onCreateProfile: () -> Void

    @State private var scope: SocialService.FeedScope = .all
    @State private var posts: [SocialPost] = []
    @State private var next: Double?
    @State private var loading = false
    @State private var loadingMore = false
    @State private var error: String?
    @State private var path = NavigationPath()
    @State private var confirmDiscard = false

    private var social: SocialService { .shared }

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    if !social.isSignedIn {
                        signUpTeaser.padding(.bottom, 8)
                    }
                    outbox
                    if let error, posts.isEmpty {
                        SocialErrorCard(message: error) { Task { await reload() } }
                            .padding(.top, 20)
                    } else if posts.isEmpty && !loading {
                        empty.padding(.top, 30)
                    }
                    ForEach(posts) { post in
                        PostCardView(
                            post: post,
                            onOpen: { path.append(FeedRoute.post(post)) },
                            onOpenAuthor: { path.append(FeedRoute.user(post.author.username)) },
                            onChange: { replace(post.id, with: $0) }
                        )
                        Divider().overlay(Color.white.opacity(0.07))
                            .onAppear { if post.id == posts.last?.id { Task { await loadMore() } } }
                    }
                    if loadingMore || (loading && posts.isEmpty) {
                        ProgressView().tint(.white).frame(maxWidth: .infinity).padding(24)
                    }
                    Spacer(minLength: 110)
                }
                .padding(.horizontal, 16)
            }
            .scrollIndicators(.hidden)
            .refreshable { await reload() }
            .background(Color.black.ignoresSafeArea())
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { scopeMenu }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: onFindPeople) {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundStyle(.white)
                    }
                    .accessibilityLabel("Найти людей")
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(for: FeedRoute.self) { route in
                route.destination(path: $path) { id, value in replace(id, with: value) }
            }
        }
        .preferredColorScheme(.dark)
        .task(id: "\(scope.rawValue)-\(social.feedVersion)") { await reload() }
        .onChange(of: scope) { _, value in
            // Другая лента — сразу показываем её прошлую версию, если она была.
            posts = social.feedCache[value]?.posts ?? []
            next = social.feedCache[value]?.next
        }
        .onChange(of: posts) { _, value in
            social.feedCache[scope] = SocialFeedPage(posts: value, next: next)
        }
    }

    private var scopeMenu: some View {
        Menu {
            Picker("Лента", selection: $scope) {
                ForEach(SocialService.FeedScope.allCases) { item in
                    Text(item == .all ? "Все в STRAIN" : "Мои подписки").tag(item)
                }
            }
        } label: {
            HStack(spacing: 6) {
                Text(scope == .all ? "Главное" : "Подписки")
                    .font(.system(size: 30, weight: .heavy, design: Theme.fontDesign))
                    .foregroundStyle(.white)
                Image(systemName: "chevron.down")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(Color.white.opacity(0.5))
            }
            .fixedSize()
        }
        .disabled(!social.isSignedIn)
    }

    @ViewBuilder
    private var outbox: some View {
        if !social.outbox.isEmpty {
            HStack(spacing: 12) {
                if social.isFlushing {
                    ProgressView().tint(.white)
                } else {
                    Image(systemName: "icloud.and.arrow.up")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(Theme.accent)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(social.isFlushing ? "Публикую тренировку…" : "Тренировка ждёт отправки")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(.white)
                    if let message = social.outboxError, !social.isFlushing {
                        Text(message)
                            .font(.system(size: 12))
                            .foregroundStyle(Color.white.opacity(0.55))
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                Spacer()
                if !social.isFlushing {
                    VStack(alignment: .trailing, spacing: 8) {
                        Button("Отправить") { social.flushOutbox() }
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(Theme.accent)
                        // Сервер отказал — пост не застрянет в очереди навсегда.
                        if social.outboxError != nil {
                            Button("Удалить") { confirmDiscard = true }
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(Color.white.opacity(0.5))
                        }
                    }
                }
            }
            .padding(16)
            .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .padding(.top, 8)
            .confirmationDialog("Не публиковать тренировку?", isPresented: $confirmDiscard, titleVisibility: .visible) {
                Button("Убрать из очереди", role: .destructive) {
                    for draft in social.outbox { social.discard(draft) }
                }
                Button("Отмена", role: .cancel) {}
            } message: {
                Text("Пост не уйдёт в ленту. Сама тренировка останется в приложении.")
            }
        }
    }

    private var signUpTeaser: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Делись тренировками")
                .font(.system(size: 20, weight: .heavy, design: Theme.fontDesign))
                .foregroundStyle(.white)
            Text("Создай профиль: в конце тренировки сделаешь фото — и оно появится здесь вместе с весами и подходами. Друзья поставят лайк или напишут комментарий.")
                .font(.system(size: 14))
                .foregroundStyle(Color.white.opacity(0.6))
                .fixedSize(horizontal: false, vertical: true)
            Button(action: onCreateProfile) {
                Text("Создать профиль")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(.black)
                    .padding(.horizontal, 18)
                    .frame(height: 44)
                    .background(Color.white, in: Capsule())
            }
            .buttonStyle(.pressable)
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .padding(.top, 8)
    }

    private var empty: some View {
        VStack(spacing: 10) {
            Image(systemName: scope == .all ? "figure.strengthtraining.traditional" : "person.2")
                .font(.system(size: 34, weight: .semibold))
                .foregroundStyle(Color.white.opacity(0.35))
            Text(scope == .all ? "В ленте пока пусто" : "Здесь появятся тренировки тех, на кого ты подписан")
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
            Text(scope == .all
                 ? "Закончи тренировку и опубликуй её — будешь первым."
                 : "Найди друзей по юзернейму и подпишись.")
                .font(.system(size: 14))
                .foregroundStyle(Color.white.opacity(0.55))
                .multilineTextAlignment(.center)
            if scope == .following {
                Button("Найти людей", action: onFindPeople)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(Theme.accent)
                    .padding(.top, 4)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(24)
    }

    private func replace(_ id: String, with value: SocialPost?) {
        guard let index = posts.firstIndex(where: { $0.id == id }) else { return }
        if let value { posts[index] = value } else { _ = withAnimation(Motion.snappy) { posts.remove(at: index) } }
    }

    private func reload() async {
        // Без профиля подписок нет: смена ленты сама перезапустит загрузку.
        if !social.isSignedIn && scope == .following { scope = .all; return }
        if posts.isEmpty, let cached = social.feedCache[scope] {
            posts = cached.posts
            next = cached.next
        }
        loading = true
        defer { loading = false }
        social.flushOutbox()
        do {
            let page = try await social.feed(scope)
            next = page.next
            posts = page.posts
            error = nil
        } catch {
            self.error = error.socialMessage
        }
    }

    private func loadMore() async {
        guard let next, !loadingMore else { return }
        loadingMore = true
        defer { loadingMore = false }
        if let page = try? await social.feed(scope, before: next) {
            let known = Set(posts.map(\.id))
            self.next = page.next
            posts.append(contentsOf: page.posts.filter { !known.contains($0.id) })
        }
    }
}

/// Куда ведут нажатия в ленте и профилях: пост или человек.
enum FeedRoute: Hashable {
    case post(SocialPost)
    case user(String)
    case followers(String)
    case following(String)
    case program(SocialProgramDay)
    case badges([SocialBadge])

    @MainActor @ViewBuilder
    func destination(path: Binding<NavigationPath>, onPostChange: @escaping (String, SocialPost?) -> Void) -> some View {
        switch self {
        case .post(let post):
            PostDetailView(
                post: post,
                onChange: { onPostChange(post.id, $0) },
                openAuthor: { path.wrappedValue.append(FeedRoute.user($0)) }
            )
        case .user(let username):
            UserProfileView(username: username, path: path)
        case .followers(let username):
            PeopleListView(title: "Подписчики", load: { try await SocialService.shared.followers(of: username) }, path: path)
        case .following(let username):
            PeopleListView(title: "Подписки", load: { try await SocialService.shared.following(of: username) }, path: path)
        case .program(let day):
            ProgramDayView(day: day)
        case .badges(let badges):
            BadgeListView(badges: badges)
        }
    }
}
