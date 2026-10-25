import PhotosUI
import SwiftUI
import UIKit

// MARK: - Вкладка «Профиль»

/// Свой профиль. Настройки приложения — за шестерёнкой справа вверху.
struct ProfileTabView: View {
    @Bindable var profile: ProgramProfile
    var onSettings: () -> Void

    @State private var path = NavigationPath()
    @State private var posts: [SocialPost] = []
    @State private var next: Double?
    @State private var loadingPosts = false
    @State private var postsError: String?
    @State private var showingEdit = false

    private var social: SocialService { .shared }

    var body: some View {
        NavigationStack(path: $path) {
            Group {
                if let me = social.me {
                    ScrollView {
                        let progress = AchievementCalculator.progress(for: profile)
                        ProfileContent(
                            user: me,
                            badges: AchievementCalculator.badges(from: progress),
                            achievedCount: progress.filter { $0.level > 0 }.count,
                            programs: SocialProgramBuilder.days(for: profile),
                            posts: posts,
                            loadingPosts: loadingPosts,
                            postsError: postsError,
                            path: $path,
                            primaryAction: {
                                Button { showingEdit = true } label: {
                                    Text("Редактировать")
                                        .font(.system(size: 16, weight: .bold))
                                        .foregroundStyle(.white)
                                        .frame(maxWidth: .infinity)
                                        .frame(height: 50)
                                        .background(Color(white: 0.13), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                                }
                                .buttonStyle(.pressable)
                            },
                            onAchievements: { path.append(ProfileRoute.achievements) },
                            onLoadMore: { Task { await loadMore(me.username) } },
                            onPostChange: replace
                        )
                        .tabScrollAnchor(.profile)
                    }
                    .refreshable {
                        await social.refreshMe()
                        await reloadPosts()
                    }
                    .task(id: "\(me.username)-\(social.feedVersion)") { await reloadPosts() }
                } else if social.isSignedIn {
                    ProgressView().tint(.white)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .task { await social.refreshMe() }
                } else {
                    SignUpView()
                }
            }
            .scrollIndicators(.hidden)
            .background(Color.black.ignoresSafeArea())
            .toolbar {
                if let me = social.me {
                    ToolbarItem(placement: .topBarTrailing) {
                        ShareLink(item: "Я тренируюсь в STRAIN — найди меня: @\(me.username)") {
                            Image(systemName: "square.and.arrow.up")
                                .font(.system(size: 17, weight: .semibold))
                                .foregroundStyle(.white)
                        }
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: onSettings) {
                        Image(systemName: "gearshape")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundStyle(.white)
                    }
                    .accessibilityLabel("Настройки")
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(for: FeedRoute.self) { route in
                route.destination(path: $path) { id, value in replace(id, value) }
            }
            .navigationDestination(for: ProfileRoute.self) { route in
                switch route {
                case .achievements:
                    AchievementsView(progress: AchievementCalculator.progress(for: profile))
                }
            }
            .sheet(isPresented: $showingEdit) {
                EditProfileView(profile: profile)
            }
        }
        .preferredColorScheme(.dark)
        .onAppear { social.scheduleSync(from: profile) }
        .onTabReselect(.profile) {
            if path.isEmpty { TabReselection.shared.scrollToTop(.profile) } else { path = NavigationPath() }
        }
    }

    private func replace(_ id: String, _ value: SocialPost?) {
        guard let index = posts.firstIndex(where: { $0.id == id }) else { return }
        if let value { posts[index] = value } else { posts.remove(at: index) }
    }

    private func reloadPosts() async {
        guard let me = social.me else { return }
        loadingPosts = true
        defer { loadingPosts = false }
        do {
            let page = try await social.posts(of: me.username)
            posts = page.posts
            next = page.next
            postsError = nil
        } catch {
            postsError = error.socialMessage
        }
    }

    private func loadMore(_ username: String) async {
        guard let next, !loadingPosts else { return }
        loadingPosts = true
        defer { loadingPosts = false }
        if let page = try? await social.posts(of: username, before: next) {
            let known = Set(posts.map(\.id))
            posts.append(contentsOf: page.posts.filter { !known.contains($0.id) })
            self.next = page.next
        }
    }
}

enum ProfileRoute: Hashable {
    case achievements
}

// MARK: - Чужой профиль

struct UserProfileView: View {
    let username: String
    @Binding var path: NavigationPath

    @State private var user: SocialUser?
    @State private var posts: [SocialPost] = []
    @State private var next: Double?
    @State private var loading = true
    @State private var error: String?
    @State private var busy = false

    var body: some View {
        ScrollView {
            if let user {
                ProfileContent(
                    user: user,
                    badges: user.achievements,
                    achievedCount: user.achievements.count,
                    programs: user.programs,
                    posts: posts,
                    loadingPosts: loading,
                    postsError: nil,
                    path: $path,
                    primaryAction: {
                        if !user.isMe && SocialService.shared.isSignedIn {
                            VStack(spacing: 10) {
                                HStack(spacing: 10) {
                                    FollowButton(isFollowing: user.isFollowing) { toggleFollow(user) }
                                        .disabled(busy)
                                    Button { path.append(FeedRoute.chat(user.username)) } label: {
                                        Text("Написать")
                                            .font(.system(size: 16, weight: .bold))
                                            .foregroundStyle(.white)
                                            .frame(maxWidth: .infinity)
                                            .frame(height: 50)
                                            .background(Color(white: 0.16), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                                    }
                                    .buttonStyle(.pressable)
                                }
                                // Приглашение уходит карточкой в переписку — туда и переходим.
                                InviteButton(username: user.username) { path.append(FeedRoute.chat(user.username)) }
                            }
                        }
                    },
                    onAchievements: { path.append(FeedRoute.badges(user.achievements)) },
                    onLoadMore: { Task { await loadMore() } },
                    onPostChange: { id, value in
                        guard let index = posts.firstIndex(where: { $0.id == id }) else { return }
                        if let value { posts[index] = value } else { posts.remove(at: index) }
                    }
                )
            } else if let error {
                SocialErrorCard(message: error) { Task { await reload() } }.padding(16)
            } else {
                ProgressView().tint(.white).padding(40)
            }
        }
        .refreshable { await reload() }
        .background(Color.black.ignoresSafeArea())
        .navigationTitle("@\(username)")
        .navigationBarTitleDisplayMode(.inline)
        .task { await reload() }
        .preferredColorScheme(.dark)
    }

    private func reload() async {
        loading = true
        defer { loading = false }
        do {
            async let loadedUser = SocialService.shared.user(username)
            async let page = SocialService.shared.posts(of: username)
            user = try await loadedUser
            let loadedPage = try await page
            posts = loadedPage.posts
            next = loadedPage.next
            error = nil
        } catch {
            self.error = error.socialMessage
        }
    }

    private func loadMore() async {
        guard let next, !loading else { return }
        loading = true
        defer { loading = false }
        if let page = try? await SocialService.shared.posts(of: username, before: next) {
            let known = Set(posts.map(\.id))
            posts.append(contentsOf: page.posts.filter { !known.contains($0.id) })
            self.next = page.next
        }
    }

    private func toggleFollow(_ current: SocialUser) {
        busy = true
        var optimistic = current
        optimistic.isFollowing.toggle()
        optimistic.followers += optimistic.isFollowing ? 1 : -1
        withAnimation(Motion.snappy) { user = optimistic }
        Task {
            defer { busy = false }
            do {
                user = try await SocialService.shared.setFollowing(username, optimistic.isFollowing)
            } catch {
                user = current
            }
        }
    }
}

// MARK: - Содержимое профиля

struct ProfileContent<Action: View>: View {
    let user: SocialUser
    let badges: [SocialBadge]
    let achievedCount: Int
    let programs: [SocialProgramDay]
    let posts: [SocialPost]
    let loadingPosts: Bool
    let postsError: String?
    @Binding var path: NavigationPath
    @ViewBuilder var primaryAction: () -> Action
    var onAchievements: () -> Void
    var onLoadMore: () -> Void
    var onPostChange: (String, SocialPost?) -> Void

    @State private var tab = 0

    var body: some View {
        VStack(spacing: 18) {
            header
            HStack {
                SocialStat(value: "\(user.workouts)", title: "Тренировки")
                Button { path.append(FeedRoute.followers(user.username)) } label: {
                    SocialStat(value: "\(user.followers)", title: "Подписчики")
                }
                .buttonStyle(.pressable)
                Button { path.append(FeedRoute.following(user.username)) } label: {
                    SocialStat(value: "\(user.following)", title: "Подписки")
                }
                .buttonStyle(.pressable)
            }
            primaryAction()
            achievementsCard
            Picker("", selection: $tab) {
                Text("Тренировки").tag(0)
                Text("Программы").tag(1)
            }
            .pickerStyle(.segmented)
            if tab == 0 { postsList } else { programsList }
            Spacer(minLength: 110)
        }
        .padding(.horizontal, 16)
    }

    private var header: some View {
        VStack(spacing: 10) {
            ZStack(alignment: .bottom) {
                ProfileCover(imageId: user.cover)
                    .frame(height: 170)
                    .frame(maxWidth: .infinity)
                    .clipped()
                    .padding(.horizontal, -16)
                AvatarView(imageId: user.avatar, username: user.username, size: 118)
                    .overlay(Circle().strokeBorder(Color.black, lineWidth: 4))
                    .offset(y: 58)
            }
            .padding(.bottom, 58)

            Text("@\(user.username)")
                .font(.system(size: 28, weight: .heavy, design: Theme.fontDesign))
                .foregroundStyle(.white)
            LevelChip(level: user.level, large: true)
            if user.challengeWins > 0 {
                Label("\(user.challengeWins) \(RuPlural.word(user.challengeWins, "победа", "победы", "побед")) в челленджах", systemImage: "trophy.fill")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(ChallengePlace.color(1))
            }
            if let live = user.live {
                LiveBadge(live: live)
            }
            if let body = user.bodyLine {
                Text(body)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color.white.opacity(0.55))
            }
            if !user.bio.isEmpty {
                Text(user.bio)
                    .font(.system(size: 15))
                    .foregroundStyle(Color.white.opacity(0.75))
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var achievementsCard: some View {
        Button(action: onAchievements) {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text("ДОСТИЖЕНИЯ")
                        .font(.system(size: 13, weight: .bold))
                        .tracking(1.4)
                        .foregroundStyle(Color.white.opacity(0.5))
                    Spacer()
                    Text("Все \(achievedCount)")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Color.white.opacity(0.6))
                    Image(systemName: "chevron.right")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(Color.white.opacity(0.4))
                }
                if badges.isEmpty {
                    Text("Первая медаль — за первую тренировку")
                        .font(.system(size: 14))
                        .foregroundStyle(Color.white.opacity(0.5))
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding(.vertical, 8)
                } else {
                    HStack(spacing: 18) {
                        ForEach(badges, id: \.id) { badge in
                            VStack(spacing: 8) {
                                MedalView(kind: AchievementKind(rawValue: badge.id), level: badge.level, size: 62)
                                Text(AchievementKind(rawValue: badge.id)?.title ?? badge.id)
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(Color.white.opacity(0.65))
                                    .lineLimit(1)
                            }
                            .frame(maxWidth: .infinity)
                        }
                    }
                }
            }
            .padding(18)
            .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        }
        .buttonStyle(.pressable)
    }

    @ViewBuilder
    private var postsList: some View {
        if let postsError, posts.isEmpty {
            SocialErrorCard(message: postsError)
        } else if posts.isEmpty && !loadingPosts {
            Text(user.isMe ? "Опубликуй тренировку в конце занятия — она появится здесь." : "Тренировок пока нет")
                .font(.system(size: 14))
                .foregroundStyle(Color.white.opacity(0.5))
                .multilineTextAlignment(.center)
                .padding(.vertical, 20)
        }
        LazyVStack(spacing: 0) {
            ForEach(posts) { post in
                PostCardView(
                    post: post,
                    onOpen: { path.append(FeedRoute.post(post)) },
                    onOpenAuthor: {},
                    onChange: { onPostChange(post.id, $0) },
                    showsWorkout: true
                )
                Divider().overlay(Color.white.opacity(0.07))
                    .onAppear { if post.id == posts.last?.id { onLoadMore() } }
            }
        }
        if loadingPosts && posts.isEmpty {
            ProgressView().tint(.white).padding(20)
        }
    }

    @ViewBuilder
    private var programsList: some View {
        if programs.isEmpty {
            Text("Программа не выбрана")
                .font(.system(size: 14))
                .foregroundStyle(Color.white.opacity(0.5))
                .padding(.vertical, 20)
        }
        VStack(spacing: 12) {
            ForEach(Array(programs.enumerated()), id: \.offset) { _, day in
                Button {
                    path.append(FeedRoute.program(day, all: programs, author: user.isMe ? nil : user.username))
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(day.title)
                                .font(.system(size: 19, weight: .heavy, design: Theme.fontDesign))
                                .foregroundStyle(.white)
                                .multilineTextAlignment(.leading)
                            if !day.groups.isEmpty {
                                Text(day.groups.joined(separator: " · "))
                                    .font(.system(size: 15, weight: .medium))
                                    .foregroundStyle(Color.white.opacity(0.6))
                            }
                            Text("\(day.exercises.count) \(RuPlural.word(day.exercises.count, "упражнение", "упражнения", "упражнений")) · \(day.setCount) \(RuPlural.word(day.setCount, "подход", "подхода", "подходов"))")
                                .font(.system(size: 14))
                                .foregroundStyle(Color.white.opacity(0.4))
                        }
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(Color.white.opacity(0.35))
                    }
                    .padding(18)
                    .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
                }
                .buttonStyle(.pressable)
            }
        }
    }
}

/// Обложка профиля: своё фото или фирменный узор STRAIN.
///
/// Фото и узор лежат в `overlay` поверх фона: так обложка всегда ровно той
/// ширины, которую ей дали. Раньше узор из длинных надписей задавал ширину
/// сам и растягивал весь экран профиля за края телефона.
struct ProfileCover: View {
    let imageId: String?

    var body: some View {
        Color.black
            .overlay {
                if imageId != nil {
                    RemoteImage(id: imageId)
                } else {
                    pattern
                }
            }
            .overlay {
                LinearGradient(colors: [.clear, .black.opacity(0.85)], startPoint: .center, endPoint: .bottom)
            }
            .clipped()
    }

    private var pattern: some View {
        Color.clear.overlay(alignment: .topTrailing) {
            VStack(alignment: .trailing, spacing: -14) {
                ForEach(0..<4, id: \.self) { row in
                    Text("STRAINSTRAIN")
                        .font(.system(size: 64, weight: .black, design: .default))
                        .italic()
                        .foregroundStyle(
                            LinearGradient(
                                colors: [Theme.accent.opacity(0.85 - Double(row) * 0.2), Theme.accentDeep.opacity(0.4 - Double(row) * 0.08)],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .lineLimit(1)
                        .fixedSize()
                        .offset(x: CGFloat(row) * 26 + 90)
                }
            }
            .mask(LinearGradient(colors: [.black, .black.opacity(0.15)], startPoint: .top, endPoint: .bottom))
        }
    }
}

// MARK: - Регистрация

struct SignUpView: View {
    @State private var username = ""
    @State private var code = ""
    @State private var showingCode = false
    @State private var busy = false
    @State private var error: String?

    private var normalized: String { SocialFormat.normalizedUsername(username) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                ProfileCover(imageId: nil)
                    .frame(height: 150)
                    .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
                    .overlay(alignment: .bottomLeading) {
                        Text("Профиль STRAIN")
                            .font(.system(size: 30, weight: .heavy, design: Theme.fontDesign))
                            .foregroundStyle(.white)
                            .padding(18)
                    }

                Text("Публикуй тренировки с фото, подписывайся на друзей, ставь лайки и пиши комментарии. Свои веса и программа видны в профиле.")
                    .font(.system(size: 15))
                    .foregroundStyle(Color.white.opacity(0.65))
                    .fixedSize(horizontal: false, vertical: true)

                VStack(alignment: .leading, spacing: 8) {
                    Text("ЮЗЕРНЕЙМ")
                        .font(.system(size: 12, weight: .bold))
                        .tracking(1.2)
                        .foregroundStyle(Color.white.opacity(0.45))
                    HStack(spacing: 4) {
                        Text("@").font(.system(size: 18, weight: .bold)).foregroundStyle(Color.white.opacity(0.4))
                        TextField("kelw1n", text: $username)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .font(.system(size: 18, weight: .bold))
                            .foregroundStyle(.white)
                            .submitLabel(.done)
                            .onSubmit(register)
                    }
                    .padding(.horizontal, 16)
                    .frame(height: 54)
                    .background(Color(white: 0.12), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    Text("Латиница, цифры, точка и подчёркивание, от 3 до 24 символов. Поменять можно потом.")
                        .font(.system(size: 12))
                        .foregroundStyle(Color.white.opacity(0.45))
                }

                Button(action: register) {
                    HStack {
                        if busy { ProgressView().tint(.black) }
                        Text("Создать профиль")
                    }
                    .font(.system(size: 17, weight: .heavy, design: Theme.fontDesign))
                    .foregroundStyle(.black)
                    .frame(maxWidth: .infinity)
                    .frame(height: 54)
                    .background(SocialFormat.isValidUsername(normalized) ? Color.white : Color(white: 0.4), in: Capsule())
                }
                .buttonStyle(.pressable)
                .disabled(busy || !SocialFormat.isValidUsername(normalized))

                if let error {
                    Text(error)
                        .font(.system(size: 14))
                        .foregroundStyle(Theme.warning)
                        .fixedSize(horizontal: false, vertical: true)
                }

                DisclosureGroup(isExpanded: $showingCode) {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Код входа есть в настройках профиля на старом телефоне («Редактировать» → «Код входа»). Он же лежит в резервной копии STRAIN.")
                            .font(.system(size: 13))
                            .foregroundStyle(Color.white.opacity(0.55))
                            .fixedSize(horizontal: false, vertical: true)
                        TextField("Код входа", text: $code)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .font(.system(size: 14, design: .monospaced))
                            .padding(12)
                            .background(Color(white: 0.12), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        Button("Войти по коду", action: signIn)
                            .font(.system(size: 15, weight: .bold))
                            .foregroundStyle(Theme.accent)
                            .disabled(busy || code.isEmpty)
                    }
                    .padding(.top, 10)
                } label: {
                    Text("У меня уже есть профиль")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(.white)
                }
                .tint(.white)
                Spacer(minLength: 110)
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
        }
        .scrollDismissesKeyboard(.interactively)
    }

    private func register() {
        guard SocialFormat.isValidUsername(normalized), !busy else { return }
        busy = true
        error = nil
        Task {
            defer { busy = false }
            do {
                try await SocialService.shared.register(username: normalized)
                UINotificationFeedbackGenerator().notificationOccurred(.success)
            } catch {
                self.error = error.socialMessage
            }
        }
    }

    private func signIn() {
        busy = true
        error = nil
        Task {
            defer { busy = false }
            do {
                try await SocialService.shared.signIn(code: code)
                UINotificationFeedbackGenerator().notificationOccurred(.success)
            } catch {
                self.error = error.socialMessage
            }
        }
    }
}

// MARK: - Редактирование

struct EditProfileView: View {
    @Bindable var profile: ProgramProfile
    @Environment(\.dismiss) private var dismiss

    @State private var username = ""
    @State private var bio = ""
    @State private var height = ""
    @State private var showBody = true
    @State private var saving = false
    @State private var uploading: String?
    @State private var error: String?
    @State private var avatarItem: PhotosPickerItem?
    @State private var coverItem: PhotosPickerItem?
    @State private var showingCamera = false
    @State private var showingAvatarGallery = false
    @State private var showingCode = false
    @State private var confirmSignOut = false
    @State private var confirmDelete = false

    private var social: SocialService { .shared }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 18) {
                    covers
                    fields
                    account
                    if let error {
                        Text(error).font(.system(size: 14)).foregroundStyle(Theme.warning)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 30)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(Color.black.ignoresSafeArea())
            .navigationTitle("Профиль")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Отмена") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(action: save) {
                        if saving { ProgressView() } else { Text("Готово").bold() }
                    }
                    .disabled(saving || uploading != nil)
                }
            }
            .onAppear(perform: fill)
            .photosPicker(isPresented: $showingAvatarGallery, selection: $avatarItem, matching: .images)
            .onChange(of: avatarItem) { _, item in upload(item, kind: .avatar) }
            .onChange(of: coverItem) { _, item in upload(item, kind: .cover) }
            .cameraSheet(isPresented: $showingCamera) { image in upload(image, kind: .avatar) }
            .confirmationDialog("Выйти из профиля на этом телефоне?", isPresented: $confirmSignOut, titleVisibility: .visible) {
                Button("Выйти", role: .destructive) {
                    social.signOut()
                    dismiss()
                }
            } message: {
                Text("Профиль останется на сервере. Вернуться можно по коду входа — сохрани его перед выходом.")
            }
            .confirmationDialog("Удалить профиль навсегда?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Удалить профиль и все посты", role: .destructive) {
                    Task {
                        do {
                            try await social.deleteAccount()
                            dismiss()
                        } catch {
                            self.error = error.socialMessage
                        }
                    }
                }
            } message: {
                Text("Пропадут все посты, фото, лайки и комментарии. Тренировки в приложении останутся.")
            }
        }
        .preferredColorScheme(.dark)
    }

    private var covers: some View {
        ZStack(alignment: .bottom) {
            ProfileCover(imageId: social.me?.cover)
                .frame(height: 160)
                .frame(maxWidth: .infinity)
                .clipped()
                .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                // Тонкая рамка: чёрная обложка на чёрном фоне иначе сливается с экраном.
                .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).strokeBorder(Color.white.opacity(0.08), lineWidth: 1))
                .overlay { if uploading == "cover" { ProgressView().tint(.white) } }
                .overlay(alignment: .topTrailing) {
                    PhotosPicker(selection: $coverItem, matching: .images) {
                        Label("Изменить обложку", systemImage: "photo")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(.ultraThinMaterial, in: Capsule())
                    }
                    .padding(10)
                }
            Menu {
                Button { showingCamera = true } label: { Label("Снять фото", systemImage: "camera") }
                // Не PhotosPicker прямо в меню: оттуда он на iOS не открывается.
                Button { showingAvatarGallery = true } label: {
                    Label("Выбрать из галереи", systemImage: "photo.on.rectangle")
                }
                if social.me?.avatar != nil {
                    Button(role: .destructive) { clearAvatar() } label: { Label("Убрать фото", systemImage: "trash") }
                }
            } label: {
                ZStack(alignment: .bottomTrailing) {
                    AvatarView(imageId: social.me?.avatar, username: social.me?.username ?? "s", size: 104)
                        .overlay(Circle().strokeBorder(Color.black, lineWidth: 4))
                        .overlay {
                            if uploading == "avatar" { ProgressView().tint(.white) }
                        }
                    Image(systemName: "camera.fill")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: 36, height: 36)
                        .background(Color(white: 0.15), in: Circle())
                        .overlay(Circle().strokeBorder(Color.black, lineWidth: 3))
                }
            }
            .offset(y: 50)
        }
        .padding(.bottom, 50)
    }

    private var fields: some View {
        VStack(spacing: 0) {
            row(icon: "at", title: "Юзернейм") {
                TextField("юзернейм", text: $username)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .multilineTextAlignment(.trailing)
            }
            divider
            row(icon: "text.alignleft", title: "О себе") {
                TextField("Не заполнено", text: $bio, axis: .vertical)
                    .lineLimit(1...4)
                    .multilineTextAlignment(.trailing)
            }
            divider
            row(icon: "ruler", title: "Рост, см") {
                TextField("—", text: $height)
                    .keyboardType(.numberPad)
                    .multilineTextAlignment(.trailing)
            }
            divider
            row(icon: "scalemass", title: "Вес") {
                Text(profile.latestBodyWeight.map { WeightFormat.kilogramsPrecise($0) } ?? "из «Замеров веса»")
                    .foregroundStyle(Color.white.opacity(0.55))
            }
            divider
            Toggle(isOn: $showBody) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Показывать рост и вес")
                        .font(.system(size: 16, weight: .semibold))
                    Text("В шапке профиля. Сила к весу в достижениях видна всегда.")
                        .font(.system(size: 12))
                        .foregroundStyle(Color.white.opacity(0.45))
                }
            }
            .tint(Theme.accent)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            divider
            Toggle(isOn: Binding(get: { social.sharesLive }, set: { social.sharesLive = $0 })) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Показывать, что я тренируюсь")
                        .font(.system(size: 16, weight: .semibold))
                    Text("Подписчики увидят упражнение и подходы, пока идёт тренировка.")
                        .font(.system(size: 12))
                        .foregroundStyle(Color.white.opacity(0.45))
                }
            }
            .tint(Theme.accent)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private var account: some View {
        VStack(spacing: 0) {
            Button { withAnimation(Motion.snappy) { showingCode.toggle() } } label: {
                HStack {
                    Image(systemName: "key.fill").foregroundStyle(Theme.accent).frame(width: 26)
                    Text("Код входа на другом телефоне").font(.system(size: 16, weight: .semibold)).foregroundStyle(.white)
                    Spacer()
                    Image(systemName: showingCode ? "chevron.up" : "chevron.down")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(Color.white.opacity(0.4))
                }
                .padding(16)
            }
            .buttonStyle(PressableStyle(scale: 0.985))
            if showingCode, let token = social.token {
                VStack(alignment: .leading, spacing: 10) {
                    Text(token)
                        .font(.system(size: 13, design: .monospaced))
                        .foregroundStyle(.white)
                        .textSelection(.enabled)
                    Button {
                        UIPasteboard.general.string = token
                        UINotificationFeedbackGenerator().notificationOccurred(.success)
                    } label: {
                        Label("Скопировать", systemImage: "doc.on.doc")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(Theme.accent)
                    }
                    Text("С этим кодом в профиль может войти кто угодно — не публикуй его. Он также сохраняется в резервной копии.")
                        .font(.system(size: 12))
                        .foregroundStyle(Color.white.opacity(0.45))
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 14)
            }
            divider
            Button { confirmSignOut = true } label: {
                Text("Выйти из профиля").font(.system(size: 16, weight: .semibold)).foregroundStyle(.white)
                    .frame(maxWidth: .infinity, alignment: .leading).padding(16)
            }
            .buttonStyle(PressableStyle(scale: 0.985))
            divider
            Button { confirmDelete = true } label: {
                Text("Удалить профиль").font(.system(size: 16, weight: .semibold)).foregroundStyle(Theme.record)
                    .frame(maxWidth: .infinity, alignment: .leading).padding(16)
            }
            .buttonStyle(PressableStyle(scale: 0.985))
        }
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private var divider: some View {
        Divider().overlay(Color.white.opacity(0.06)).padding(.leading, 56)
    }

    private func row<Content: View>(icon: String, title: String, @ViewBuilder content: () -> Content) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Color.white.opacity(0.6))
                .frame(width: 26)
            Text(title).font(.system(size: 16, weight: .semibold))
            Spacer(minLength: 12)
            content().font(.system(size: 16)).foregroundStyle(.white)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
    }

    private func fill() {
        guard let me = social.me else { return }
        username = me.username
        bio = me.bio
        height = me.heightCm.map(String.init) ?? ""
        showBody = me.showBody
    }

    private enum ImageKind { case avatar, cover }

    private func upload(_ item: PhotosPickerItem?, kind: ImageKind) {
        guard let item else { return }
        Task {
            guard let data = try? await item.loadTransferable(type: Data.self), let image = UIImage(data: data) else {
                error = "Не удалось открыть фото"
                return
            }
            upload(image, kind: kind)
        }
    }

    private func upload(_ image: UIImage, kind: ImageKind) {
        uploading = kind == .avatar ? "avatar" : "cover"
        error = nil
        Task {
            defer {
                uploading = nil
                avatarItem = nil
                coverItem = nil
            }
            do {
                let id = try await social.uploadImage(image, maxSide: kind == .avatar ? 600 : 1280)
                var patch = SocialService.ProfilePatch()
                if kind == .avatar { patch.avatar = id } else { patch.cover = id }
                try await social.update(patch)
            } catch {
                self.error = error.socialMessage
            }
        }
    }

    private func clearAvatar() {
        Task {
            var patch = SocialService.ProfilePatch()
            patch.avatar = ""
            do {
                try await social.update(patch)
            } catch {
                self.error = error.socialMessage
            }
        }
    }

    private func save() {
        let name = SocialFormat.normalizedUsername(username)
        guard SocialFormat.isValidUsername(name) else {
            error = "Юзернейм: 3–24 символа — латиница, цифры, точка и подчёркивание"
            return
        }
        let heightText = height.trimmingCharacters(in: .whitespaces)
        guard heightText.isEmpty || Int(heightText).map({ (100...250).contains($0) }) == true else {
            error = "Рост — от 100 до 250 см или пусто"
            return
        }
        saving = true
        error = nil
        Task {
            defer { saving = false }
            var patch = SocialService.ProfilePatch()
            if name != social.me?.username { patch.username = name }
            patch.bio = bio.trimmingCharacters(in: .whitespacesAndNewlines)
            if let value = Int(height.trimmingCharacters(in: .whitespaces)), (100...250).contains(value) {
                patch.heightCm = value
            } else if height.trimmingCharacters(in: .whitespaces).isEmpty {
                patch.clearHeight = true
            }
            patch.showBody = showBody
            patch.weightKg = profile.latestBodyWeight
            do {
                try await social.update(patch)
                UINotificationFeedbackGenerator().notificationOccurred(.success)
                dismiss()
            } catch {
                self.error = error.socialMessage
            }
        }
    }
}

// MARK: - Достижения

struct AchievementsView: View {
    let progress: [AchievementProgress]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text("\(progress.filter { $0.level > 0 }.count)")
                        .font(.system(size: 52, weight: .heavy, design: Theme.fontDesign))
                        .foregroundStyle(.white)
                    Text("из \(progress.count)")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(Color.white.opacity(0.5))
                    Spacer()
                    Text("ВЗЯТО")
                        .font(.system(size: 13, weight: .bold))
                        .tracking(1.6)
                        .foregroundStyle(Color.white.opacity(0.4))
                }
                .padding(20)
                .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))

                ForEach(AchievementGroup.allCases) { group in
                    let items = progress.filter { $0.kind.group == group }
                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            Text(group.rawValue)
                                .font(.system(size: 13, weight: .bold))
                                .tracking(1.4)
                                .foregroundStyle(Color.white.opacity(0.5))
                            Spacer()
                            Text("\(items.filter { $0.level > 0 }.count)/\(items.count)")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundStyle(Color.white.opacity(0.5))
                        }
                        VStack(spacing: 0) {
                            ForEach(items) { item in
                                AchievementRow(item: item)
                                if item.id != items.last?.id {
                                    Divider().overlay(Color.white.opacity(0.06)).padding(.leading, 92)
                                }
                            }
                        }
                        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
                    }
                }
                Spacer(minLength: 120)
            }
            .padding(.horizontal, 16)
        }
        .background(Color.black.ignoresSafeArea())
        .navigationTitle("Достижения")
        .navigationBarTitleDisplayMode(.inline)
        .preferredColorScheme(.dark)
    }
}

struct AchievementRow: View {
    let item: AchievementProgress

    var body: some View {
        HStack(spacing: 16) {
            MedalView(kind: item.kind, level: item.level, size: 60)
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text(item.kind.title)
                        .font(.system(size: 18, weight: .heavy, design: Theme.fontDesign))
                        .foregroundStyle(item.level > 0 ? .white : Color.white.opacity(0.6))
                    Spacer()
                    Text(item.level > 0 ? "\(item.level)/\(item.kind.thresholds.count)" : "—")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Color.white.opacity(0.7))
                }
                LevelTrack(levels: item.kind.thresholds.count, reached: item.level, fraction: item.segmentFraction)
                Text(item.caption)
                    .font(.system(size: 13))
                    .foregroundStyle(Color.white.opacity(0.45))
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
    }
}

/// Шкала уровней: точки-пороги и отрезки между ними; текущий отрезок заполнен частично.
struct LevelTrack: View {
    let levels: Int
    let reached: Int
    let fraction: Double

    var body: some View {
        GeometryReader { proxy in
            let width = proxy.size.width
            let step = width / CGFloat(levels)
            ZStack(alignment: .leading) {
                Capsule().fill(Color.white.opacity(0.1)).frame(height: 4)
                Capsule()
                    .fill(Theme.accent)
                    .frame(width: max(0, step * (CGFloat(reached) + (reached < levels ? CGFloat(fraction) : 0))), height: 4)
                ForEach(1...levels, id: \.self) { index in
                    Circle()
                        .fill(index <= reached ? Theme.accent : Color(white: 0.25))
                        .frame(width: index == reached ? 12 : 8, height: index == reached ? 12 : 8)
                        .position(x: step * CGFloat(index) - (index == levels ? 6 : 0), y: 6)
                }
            }
        }
        .frame(height: 12)
    }
}

/// Медали чужого профиля: уровни без подробностей — сами цифры видны только хозяину.
struct BadgeListView: View {
    let badges: [SocialBadge]

    var body: some View {
        ScrollView {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 100), spacing: 16)], spacing: 20) {
                ForEach(badges, id: \.id) { badge in
                    VStack(spacing: 8) {
                        MedalView(kind: AchievementKind(rawValue: badge.id), level: badge.level, size: 76)
                        Text(AchievementKind(rawValue: badge.id)?.title ?? badge.id)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(.white)
                        Text("Уровень \(badge.level)")
                            .font(.system(size: 12))
                            .foregroundStyle(Color.white.opacity(0.5))
                    }
                }
            }
            .padding(16)
            if badges.isEmpty {
                Text("Медалей пока нет").foregroundStyle(Color.white.opacity(0.5)).padding(30)
            }
            Spacer(minLength: 120)
        }
        .background(Color.black.ignoresSafeArea())
        .navigationTitle("Достижения")
        .navigationBarTitleDisplayMode(.inline)
        .preferredColorScheme(.dark)
    }
}

// MARK: - День программы

struct ProgramDayView: View {
    let day: SocialProgramDay
    var allDays: [SocialProgramDay] = []
    /// Чья программа; `nil` — своя, копировать нечего.
    var author: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if !day.groups.isEmpty {
                    Text(day.groups.joined(separator: " · "))
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Color.white.opacity(0.6))
                }
                VStack(spacing: 0) {
                    ForEach(Array(day.exercises.enumerated()), id: \.offset) { index, exercise in
                        HStack(spacing: 12) {
                            ExerciseThumb(name: exercise.name, size: 44)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(exercise.name)
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundStyle(.white)
                                Text("\(exercise.sets) × \(exercise.reps)")
                                    .font(.system(size: 13))
                                    .foregroundStyle(Color.white.opacity(0.5))
                            }
                            Spacer()
                            Text(exercise.load)
                                .font(.system(size: 14, weight: .bold, design: Theme.fontDesign))
                                .foregroundStyle(.white)
                                .multilineTextAlignment(.trailing)
                        }
                        .padding(14)
                        if index < day.exercises.count - 1 {
                            Divider().overlay(Color.white.opacity(0.06)).padding(.leading, 70)
                        }
                    }
                }
                .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))

                if let author {
                    CopyProgramButton(days: allDays.isEmpty ? [day] : allDays, author: author)
                        .padding(.top, 6)
                }
            }
            .padding(16)
            .padding(.bottom, 110)
        }
        .background(Color.black.ignoresSafeArea())
        .navigationTitle(day.title)
        .navigationBarTitleDisplayMode(.inline)
        .preferredColorScheme(.dark)
    }
}
