import Foundation
import Network
import Observation
import Security
import UIKit

/// Ошибка ленты с понятным текстом: показываем её человеку как есть.
struct SocialError: LocalizedError, Equatable {
    let message: String
    var status: Int = 0

    var errorDescription: String? { message }

    static let offline = SocialError(
        message: "Нет связи с сервером. Если ленту давно не открывали, сервер просыпается — попробуй через полминуты."
    )
    static let signedOut = SocialError(message: "Сначала создай профиль STRAIN во вкладке «Профиль».", status: 401)

    /// Нет связи, сервер просыпается или занят — стоит попробовать ещё раз позже.
    var isRetryable: Bool { status == 0 || status == 429 || status >= 500 }
}

extension Error {
    /// Текст ошибки для человека. Отменённый запрос — ушли с экрана или набрали
    /// следующую букву поиска — не ошибка: для него `nil`, и плашка не мигает.
    var socialMessage: String? {
        if self is CancellationError { return nil }
        if (self as? URLError)?.code == .cancelled { return nil }
        return (self as? SocialError)?.message ?? localizedDescription
    }
}

// MARK: - Код входа

/// Код входа хранится в связке ключей: она переживает переустановку приложения
/// с тем же Apple ID. На другой телефон его переносят копией или вручную.
enum SocialKeychain {
    private static let service = "com.texasprogram.app.social"
    private static let account = "token"

    static func load() -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func save(_ token: String?) {
        let base: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(base as CFDictionary)
        guard let token, let data = token.data(using: .utf8) else { return }
        var add = base
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(add as CFDictionary, nil)
    }
}

// MARK: - Сервис

@Observable
@MainActor
final class SocialService {
    static let shared = SocialService()

    private(set) var token: String?
    private(set) var me: SocialUser?
    /// Посты в очереди на отправку — их видно в ленте до того, как они уйдут.
    private(set) var outbox: [SocialPostDraft] = []
    private(set) var outboxError: String?
    private(set) var isFlushing = false

    /// Меняется после каждой публикации или удаления: ленты по нему перезагружаются.
    private(set) var feedVersion = 0

    /// Непрочитанные личные сообщения — точка на вкладке «Друзья».
    private(set) var unreadMessages = 0
    /// Новые лайки, комментарии и подписчики — точка на колокольчике и вкладке «Главное».
    private(set) var unseenActivity = 0
    /// Приглашения в челленджи, на которые ещё не ответил.
    var challengeInvites = 0
    /// Подписки, которые тренируются прямо сейчас.
    private(set) var liveFriends: [SocialLiveFriend] = []
    /// Приглашения на совместную тренировку и идущая совместная тренировка.
    private(set) var together = SocialTogetherState()

    /// Сообщения, которые ещё не ушли: лежат на телефоне и отправляются сами.
    private(set) var pendingMessages: [ChatPending] = []
    /// Сервер отказал — такое сообщение само больше не уйдёт: его повторяют или удаляют.
    private(set) var pendingFailures: [String: String] = [:]
    /// Последняя попытка отправить упёрлась в отсутствие связи.
    private(set) var chatOffline = false

    /// Видно ли друзьям, что идёт тренировка.
    var sharesLive: Bool {
        get { access(keyPath: \.sharesLive); return UserDefaults.standard.object(forKey: "social_share_live") as? Bool ?? true }
        set {
            withMutation(keyPath: \.sharesLive) { UserDefaults.standard.set(newValue, forKey: "social_share_live") }
            if !newValue { Task { await clearLive() } }
        }
    }

    /// Последняя загруженная лента: при переходе между вкладками она видна сразу,
    /// а свежая подгружается без пустого экрана со спиннером.
    @ObservationIgnored var feedCache: [FeedScope: SocialFeedPage] = [:]

    var isSignedIn: Bool { token != nil }

    static let defaultServer = "https://strain-feed.onrender.com"

    var serverURL: String {
        // Свой ключ, а не «strain_backend_url» бро-трекера: там мог остаться старый адрес.
        UserDefaults.standard.string(forKey: "social_server_url") ?? Self.defaultServer
    }

    private let session: URLSession
    private var syncTask: Task<Void, Never>?
    /// Связь вернулась — неотправленное уходит само.
    @ObservationIgnored private let pathMonitor = NWPathMonitor()

    private init() {
        let configuration = URLSessionConfiguration.default
        // Спящий бесплатный сервер просыпается до минуты — короче ждать нельзя.
        configuration.timeoutIntervalForRequest = 75
        configuration.waitsForConnectivity = false
        session = URLSession(configuration: configuration)
        token = SocialKeychain.load()
        outbox = SocialOutboxStore.loadAll()
        pendingMessages = ChatOutboxStore.loadAll()
        pathMonitor.pathUpdateHandler = { [weak self] path in
            guard path.status == .satisfied else { return }
            Task { @MainActor in self?.flushOutbox() }
        }
        pathMonitor.start(queue: DispatchQueue(label: "strain.social.network"))
    }

    func imageURL(_ id: String?) -> URL? {
        guard let id, !id.isEmpty else { return nil }
        return URL(string: "\(serverURL)/v2/images/\(id)")
    }

    // MARK: Запросы

    private struct ErrorBody: Decodable { let error: String }
    private struct Empty: Decodable {}

    private func request<T: Decodable>(
        _ method: String,
        _ path: String,
        query: [URLQueryItem] = [],
        json: Encodable? = nil,
        data: Data? = nil,
        contentType: String? = nil,
        requiresAuth: Bool = false,
        token tokenOverride: String? = nil
    ) async throws -> T {
        guard var components = URLComponents(string: serverURL + "/v2" + path) else {
            throw SocialError(message: "Неверный адрес сервера")
        }
        if !query.isEmpty { components.queryItems = query }
        guard let url = components.url else { throw SocialError(message: "Неверный адрес сервера") }

        var request = URLRequest(url: url)
        request.httpMethod = method
        if let token = tokenOverride ?? token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        else if requiresAuth { throw SocialError.signedOut }
        if let json {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONEncoder().encode(AnyEncodable(json))
        } else if let data {
            request.setValue(contentType ?? "application/octet-stream", forHTTPHeaderField: "Content-Type")
            request.httpBody = data
        }

        let (body, response): (Data, URLResponse)
        do {
            (body, response) = try await session.data(for: request)
        } catch {
            if error is CancellationError || (error as? URLError)?.code == .cancelled { throw CancellationError() }
            throw SocialError.offline
        }
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            let message = (try? JSONDecoder().decode(ErrorBody.self, from: body).error)
                ?? (status == 503 ? "Сервер ленты ещё не настроен или перезапускается." : "Ошибка сервера (\(status))")
            // Профиль удалён на сервере — выходим. Но не при проверке чужого кода входа.
            if status == 401 && tokenOverride == nil && token != nil && path == "/me" { signOut() }
            throw SocialError(message: message, status: status)
        }
        if T.self == Empty.self { return Empty() as! T }
        do {
            return try JSONDecoder().decode(T.self, from: body)
        } catch {
            throw SocialError(message: "Сервер ответил непонятно — возможно, приложение устарело.")
        }
    }

    // MARK: Профиль

    private struct RegisterResponse: Decodable {
        let token: String
        let user: SocialUser
    }

    func register(username: String) async throws {
        let name = SocialFormat.normalizedUsername(username)
        guard SocialFormat.isValidUsername(name) else {
            throw SocialError(message: "Юзернейм: 3–24 символа — латиница, цифры, точка и подчёркивание")
        }
        let response: RegisterResponse = try await request("POST", "/register", json: ["username": name])
        setToken(response.token)
        me = response.user
    }

    /// Вход кодом с другого телефона или из резервной копии.
    func signIn(code: String) async throws {
        let cleaned = code.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard cleaned.range(of: "^[a-f0-9]{64}$", options: .regularExpression) != nil else {
            throw SocialError(message: "Код входа — 64 символа из цифр и латинских букв a–f")
        }
        // Код проверяем отдельно: неверный код не должен трогать текущий вход.
        let user: SocialUser = try await request("GET", "/me", token: cleaned)
        setToken(cleaned)
        me = user
    }

    /// Код из копии подхватываем молча, только если своего профиля ещё нет.
    func adoptBackupToken(_ code: String?) {
        guard token == nil, let code, !code.isEmpty else { return }
        Task { try? await signIn(code: code) }
    }

    func signOut() {
        // Неотправленные сообщения не должны уйти от имени следующего профиля.
        for item in pendingMessages { ChatOutboxStore.remove(item.clientId) }
        pendingMessages = []
        pendingFailures = [:]
        setToken(nil)
        me = nil
        unreadMessages = 0
        unseenActivity = 0
        challengeInvites = 0
        liveFriends = []
        together = SocialTogetherState()
        lastLive = nil
        feedVersion += 1
    }

    func deleteAccount() async throws {
        let _: Empty = try await request("DELETE", "/me", requiresAuth: true)
        // Неотправленные посты удалённого профиля не должны уйти от имени следующего.
        for draft in outbox { SocialOutboxStore.remove(draft.clientId) }
        outbox = []
        signOut()
    }

    private func setToken(_ value: String?) {
        token = value
        SocialKeychain.save(value)
        // Другой профиль — программу и медали в него надо отправить заново.
        UserDefaults.standard.removeObject(forKey: "social_sync_fingerprint")
        feedCache = [:]
        feedVersion += 1
    }

    func refreshMe() async {
        guard token != nil else { return }
        if let user: SocialUser = try? await request("GET", "/me") { me = user }
    }

    struct ProfilePatch: Encodable {
        var username: String?
        var bio: String?
        var heightCm: Int?
        var clearHeight = false
        var weightKg: Double?
        var showBody: Bool?
        var avatar: String?
        var cover: String?
        var programs: [SocialProgramDay]?
        var achievements: [SocialBadge]?
        var records: [SocialRecord]?
        var heatmap: SocialHeatmap?
        var stats: SocialWeekStats?

        enum CodingKeys: String, CodingKey {
            case username, bio, heightCm, weightKg, showBody, avatar, cover, programs, achievements, records, heatmap, stats
        }

        func encode(to encoder: Encoder) throws {
            var c = encoder.container(keyedBy: CodingKeys.self)
            try c.encodeIfPresent(username, forKey: .username)
            try c.encodeIfPresent(bio, forKey: .bio)
            if clearHeight { try c.encodeNil(forKey: .heightCm) } else { try c.encodeIfPresent(heightCm, forKey: .heightCm) }
            try c.encodeIfPresent(weightKg, forKey: .weightKg)
            try c.encodeIfPresent(showBody, forKey: .showBody)
            try c.encodeIfPresent(avatar, forKey: .avatar)
            try c.encodeIfPresent(cover, forKey: .cover)
            try c.encodeIfPresent(programs, forKey: .programs)
            try c.encodeIfPresent(achievements, forKey: .achievements)
            try c.encodeIfPresent(records, forKey: .records)
            try c.encodeIfPresent(heatmap, forKey: .heatmap)
            try c.encodeIfPresent(stats, forKey: .stats)
        }
    }

    @discardableResult
    func update(_ patch: ProfilePatch) async throws -> SocialUser {
        let user: SocialUser = try await request("PATCH", "/me", json: patch, requiresAuth: true)
        me = user
        return user
    }

    /// Программа, медали, вес и сводка недели из приложения — в профиль. Шлём, только если
    /// что-то поменялось, и не чаще раза в несколько секунд.
    func scheduleSync(from profile: ProgramProfile) {
        guard token != nil else { return }
        let patch = ProfilePatch(
            weightKg: profile.latestBodyWeight,
            programs: SocialProgramBuilder.days(for: profile),
            achievements: AchievementCalculator.badges(from: AchievementCalculator.progress(for: profile)),
            records: PersonalRecords.records(for: profile),
            heatmap: TrainingHeatmap.build(for: profile),
            stats: WeeklyStatsBuilder.stats(for: profile)
        )
        guard let data = try? JSONEncoder().encode(patch) else { return }
        let fingerprint = data.base64EncodedString()
        guard fingerprint != UserDefaults.standard.string(forKey: "social_sync_fingerprint") else { return }
        syncTask?.cancel()
        syncTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 3_000_000_000)
            guard let self, !Task.isCancelled else { return }
            if (try? await self.update(patch)) != nil {
                UserDefaults.standard.set(fingerprint, forKey: "social_sync_fingerprint")
            }
        }
    }

    // MARK: Люди

    func user(_ username: String) async throws -> SocialUser {
        try await request("GET", "/users/\(username)")
    }

    func searchUsers(_ query: String) async throws -> [SocialUserShort] {
        try await request("GET", "/users", query: [URLQueryItem(name: "q", value: query)])
    }

    func followers(of username: String) async throws -> [SocialUserShort] {
        try await request("GET", "/users/\(username)/followers")
    }

    func following(of username: String) async throws -> [SocialUserShort] {
        try await request("GET", "/users/\(username)/following")
    }

    func setFollowing(_ username: String, _ follow: Bool) async throws -> SocialUser {
        let user: SocialUser = try await request(follow ? "POST" : "DELETE", "/users/\(username)/follow", requiresAuth: true)
        await refreshMe()
        return user
    }

    // MARK: Посты

    enum FeedScope: String, CaseIterable, Identifiable {
        case all = "Все"
        case following = "Подписки"
        var id: String { rawValue }
    }

    func feed(_ scope: FeedScope, before: Double? = nil) async throws -> SocialFeedPage {
        var query = [URLQueryItem(name: "scope", value: scope == .all ? "all" : "following")]
        if let before { query.append(URLQueryItem(name: "before", value: String(Int(before)))) }
        return try await request("GET", "/feed", query: query)
    }

    func posts(of username: String, before: Double? = nil) async throws -> SocialFeedPage {
        let query = before.map { [URLQueryItem(name: "before", value: String(Int($0)))] } ?? []
        return try await request("GET", "/users/\(username)/posts", query: query)
    }

    func post(_ id: String) async throws -> SocialPost {
        try await request("GET", "/posts/\(id)")
    }

    func setLiked(_ postId: String, _ liked: Bool) async throws -> SocialPost {
        try await request(liked ? "POST" : "DELETE", "/posts/\(postId)/like", requiresAuth: true)
    }

    func deletePost(_ postId: String) async throws {
        let _: Empty = try await request("DELETE", "/posts/\(postId)", requiresAuth: true)
        feedVersion += 1
        await refreshMe()
    }

    func comments(_ postId: String) async throws -> [SocialComment] {
        try await request("GET", "/posts/\(postId)/comments")
    }

    private struct CommentBody: Encodable {
        let text: String
        let replyTo: String?
    }

    /// `replyTo` — комментарий, на который отвечаем: ответ встанет в его ветку.
    func addComment(_ postId: String, text: String, replyTo: String? = nil) async throws -> SocialComment {
        try await request("POST", "/posts/\(postId)/comments", json: CommentBody(text: text, replyTo: replyTo), requiresAuth: true)
    }

    func deleteComment(_ id: String) async throws {
        let _: Empty = try await request("DELETE", "/comments/\(id)", requiresAuth: true)
    }

    // MARK: Личные сообщения

    func chats() async throws -> SocialChatList {
        let list: SocialChatList = try await request("GET", "/chats", requiresAuth: true)
        unreadMessages = list.unread
        return list
    }

    /// Сколько непрочитанных — для точки на вкладке «Друзья».
    /// Сколько непрочитанных сообщений и новой активности — для точек на вкладках.
    func refreshUnread() async {
        guard token != nil else { unreadMessages = 0; unseenActivity = 0; return }
        struct Badges: Decodable { let messages: Int; let activity: Int; let invites: Int?; let challenges: Int? }
        if let value: Badges = try? await request("GET", "/badges") {
            unreadMessages = value.messages
            unseenActivity = value.activity
            challengeInvites = value.challenges ?? 0
            // Приглашения и совместную тренировку спрашиваем, только когда они есть.
            if (value.invites ?? 0) > 0 || together.active != nil || !together.outgoing.isEmpty || !together.incoming.isEmpty {
                await refreshTogether()
            }
        }
    }

    // MARK: Совместная тренировка

    func refreshTogether() async {
        guard token != nil else { together = SocialTogetherState(); return }
        if let value: SocialTogetherState = try? await request("GET", "/together", requiresAuth: true) { together = value }
    }

    /// Позвать друга: приглашение придёт ему и появится карточкой в переписке.
    @discardableResult
    func invite(_ username: String) async throws -> SocialTogether {
        let invite: SocialTogether = try await request("POST", "/users/\(username)/together", requiresAuth: true)
        await refreshTogether()
        return invite
    }

    func acceptTogether(_ id: String) async throws {
        let _: SocialTogether = try await request("POST", "/together/\(id)/accept", requiresAuth: true)
        await refreshTogether()
    }

    /// `decline`, `cancel`, `leave` или `done`.
    func respondTogether(_ id: String, _ action: String) async throws {
        let _: Empty = try await request("POST", "/together/\(id)/\(action)", requiresAuth: true)
        await refreshTogether()
    }

    /// Тренировка закончена без поста. Пост сам отмечает напарника закончившим,
    /// а без него совместная висела бы ещё шесть часов — и следующий пост
    /// вышел бы «вместе с» напарником.
    func finishTogether() {
        guard let id = together.active?.id else { return }
        Task { try? await respondTogether(id, "done") }
    }

    /// Сообщения переписки, новые первыми. `after` — только пришедшие позже,
    /// `since` — всё, что появилось или поменялось с `serverTime` прошлого ответа.
    func messages(with username: String, before: Double? = nil, after: Double? = nil, since: Double? = nil) async throws -> SocialChatPage {
        var query: [URLQueryItem] = []
        if let before { query.append(URLQueryItem(name: "before", value: String(Int(before)))) }
        if let after { query.append(URLQueryItem(name: "after", value: String(Int(after)))) }
        if let since { query.append(URLQueryItem(name: "since", value: String(Int(since)))) }
        return try await request("GET", "/chats/\(username)/messages", query: query, requiresAuth: true)
    }

    private struct MessageBody: Encodable {
        let text: String
        var image: String?
        var voice: String?
        var replyTo: String?
        let clientId: String
        var workout: SocialChatWorkout?
        var forward: String?
    }

    private struct EditBody: Encodable { let text: String }

    /// Новый текст своего сообщения или подписи к фото.
    func editMessage(_ id: String, text: String) async throws -> SocialMessage {
        try await request("PATCH", "/messages/\(id)", json: EditBody(text: text), requiresAuth: true)
    }

    /// Голосовое прослушано — точка гаснет и у отправителя.
    func markHeard(_ id: String) {
        guard token != nil else { return }
        Task { let _: Empty? = try? await request("POST", "/messages/\(id)/heard", requiresAuth: true) }
    }

    // MARK: Очередь сообщений

    /// Открытые переписки: ушедшее сообщение сразу встаёт на место своего пузыря.
    @ObservationIgnored private var sentHandlers: [String: (String, SocialMessage) -> Void] = [:]
    @ObservationIgnored private var flushingMessages = false
    @ObservationIgnored private var flushRequested = false
    /// Удалённые из очереди, пока уже летели на сервер: дошедшее удаляем и там.
    @ObservationIgnored private var discardedWhileSending: Set<String> = []
    @ObservationIgnored private var sendingClientId: String?

    func onMessageSent(in username: String, _ handler: ((String, SocialMessage) -> Void)?) {
        sentHandlers[username] = handler
    }

    func pending(for username: String) -> [ChatPending] {
        pendingMessages.filter { $0.username == username }
    }

    /// Сообщение — сначала на диск, потом в путь. Нет связи — дождётся её.
    func queue(_ item: ChatPending, photo: Data? = nil, voice: Data? = nil) {
        ChatOutboxStore.save(item, photo: photo, voice: voice)
        pendingMessages.append(item)
        flushMessages()
    }

    func retryPending(_ clientId: String) {
        pendingFailures[clientId] = nil
        flushMessages()
    }

    func discardPending(_ clientId: String) {
        if sendingClientId == clientId { discardedWhileSending.insert(clientId) }
        ChatOutboxStore.remove(clientId)
        pendingMessages.removeAll { $0.clientId == clientId }
        pendingFailures[clientId] = nil
    }

    /// Отправляет очередь по порядку. Нет связи — останавливается до следующего
    /// раза; сервер отказал — сообщение помечается и не мешает остальным.
    func flushMessages() {
        guard token != nil else { return }
        if flushingMessages {
            flushRequested = true
            return
        }
        guard pendingMessages.contains(where: { pendingFailures[$0.clientId] == nil }) else { return }
        flushingMessages = true
        Task {
            var stalled = false
            delivery: for item in pendingMessages.sorted(by: { $0.createdAt < $1.createdAt }) where pendingFailures[item.clientId] == nil {
                // Пока отправляли предыдущие, это сообщение могли удалить.
                guard pendingMessages.contains(where: { $0.clientId == item.clientId }) else { continue }
                sendingClientId = item.clientId
                defer { sendingClientId = nil }
                do {
                    let sent = try await deliver(item)
                    chatOffline = false
                    // Пока сообщение летело, его отменили — убираем и с сервера.
                    if discardedWhileSending.remove(item.clientId) != nil {
                        try? await deleteMessage(sent.id)
                        continue
                    }
                    ChatOutboxStore.remove(item.clientId)
                    pendingMessages.removeAll { $0.clientId == item.clientId }
                    sentHandlers[item.username]?(item.clientId, sent)
                } catch let error as SocialError where error.isRetryable {
                    chatOffline = true
                    stalled = true
                    break delivery
                } catch is CancellationError {
                    stalled = true
                    break delivery
                } catch {
                    if discardedWhileSending.remove(item.clientId) == nil {
                        pendingFailures[item.clientId] = error.socialMessage ?? "Не отправлено"
                    }
                }
            }
            flushingMessages = false
            let again = flushRequested && !stalled
            flushRequested = false
            if again { flushMessages() }
        }
    }

    private func savePending(_ item: ChatPending) {
        ChatOutboxStore.update(item)
        if let index = pendingMessages.firstIndex(where: { $0.clientId == item.clientId }) { pendingMessages[index] = item }
    }

    /// Фото и звук грузятся один раз, потом уходит само сообщение. Повтор с тем же
    /// clientId сервер не дублирует, поэтому оборванную отправку можно повторять.
    private func deliver(_ pending: ChatPending) async throws -> SocialMessage {
        var item = pending
        if item.hasPhotoFile && item.uploadedImage == nil {
            guard let data = ChatOutboxStore.photo(item.clientId) else { throw SocialError(message: "Фото потерялось — отправь его заново") }
            item.uploadedImage = try await uploadImage(data)
            savePending(item)
        }
        if item.hasVoiceFile, item.uploadedVoice == nil, let voice = item.voice {
            guard let data = ChatOutboxStore.voice(item.clientId) else { throw SocialError(message: "Запись потерялась — запиши заново") }
            item.uploadedVoice = try await uploadVoice(data, durationMs: voice.durationMs, waveform: voice.waveform).id
            savePending(item)
        }
        var body = MessageBody(text: item.text, replyTo: item.replyTo?.id, clientId: item.clientId, workout: item.workout, forward: item.forward)
        if item.forward == nil {
            body.image = item.hasPhotoFile ? item.uploadedImage : item.image
            body.voice = item.hasVoiceFile ? item.uploadedVoice : nil
        }
        do {
            return try await request("POST", "/chats/\(item.username)/messages", json: body, requiresAuth: true)
        } catch let error as SocialError where error.status == 404 && item.replyTo != nil && error.message == "Сообщение удалено" {
            // Сообщение, на которое отвечали, удалили, пока ждали связи, — уходим без цитаты.
            item.replyTo = nil
            savePending(item)
            return try await deliver(item)
        }
    }

    /// Звук голосового (AAC в m4a); длительность и волна — в адресе.
    func uploadVoice(_ data: Data, durationMs: Int, waveform: String) async throws -> SocialVoice {
        try await request(
            "POST", "/voices",
            query: [URLQueryItem(name: "durationMs", value: String(durationMs)), URLQueryItem(name: "waveform", value: waveform)],
            data: data,
            contentType: "audio/mp4",
            requiresAuth: true
        )
    }

    func voiceURL(_ id: String) -> URL? {
        URL(string: "\(serverURL)/v2/voices/\(id)")
    }

    private struct ReactBody: Encodable { let emoji: String }

    /// Реакция на сообщение; та же ещё раз — снимает её.
    func react(_ messageId: String, emoji: String) async throws -> SocialMessage {
        try await request("POST", "/messages/\(messageId)/react", json: ReactBody(emoji: emoji), requiresAuth: true)
    }

    @ObservationIgnored private var typingSentAt: [String: Date] = [:]

    /// «Печатает…» у собеседника: не чаще раза в три секунды.
    func sendTyping(to username: String) {
        guard token != nil else { return }
        let now = Date()
        if let last = typingSentAt[username], now.timeIntervalSince(last) < 3 { return }
        typingSentAt[username] = now
        Task { let _: Empty? = try? await request("POST", "/chats/\(username)/typing", requiresAuth: true) }
    }

    func deleteMessage(_ id: String) async throws {
        let _: Empty = try await request("DELETE", "/messages/\(id)", requiresAuth: true)
    }

    // MARK: Активность

    func activity() async throws -> SocialActivityPage {
        let page: SocialActivityPage = try await request("GET", "/activity", requiresAuth: true)
        unseenActivity = page.unseen
        return page
    }

    /// Всё в списке активности просмотрено — точка гаснет.
    func markActivitySeen() async {
        guard token != nil else { return }
        unseenActivity = 0
        let _: Empty? = try? await request("POST", "/activity/seen", requiresAuth: true)
    }

    // MARK: Тренируется сейчас

    @discardableResult
    func refreshLive() async -> [SocialLiveFriend] {
        guard token != nil else { liveFriends = []; return [] }
        if let friends: [SocialLiveFriend] = try? await request("GET", "/live", requiresAuth: true) { liveFriends = friends }
        return liveFriends
    }

    @ObservationIgnored private var lastLive: (live: SocialLive, sentAt: Date)?
    @ObservationIgnored private var liveTask: Task<Void, Never>?

    /// Идущая тренировка — друзьям. Одинаковое не шлём чаще раза в пять минут:
    /// этого хватает, чтобы статус на сервере не протух.
    func reportLive(_ live: SocialLive?) {
        guard token != nil else { return }
        // Скрытая тренировка всё равно видна напарнику по совместной.
        let partnerOnly = !sharesLive && together.active != nil
        guard var live, sharesLive || partnerOnly else {
            if lastLive != nil { Task { await clearLive() } }
            return
        }
        if partnerOnly { live.partnerOnly = true }
        if let last = lastLive, last.live == live, Date().timeIntervalSince(last.sentAt) < 300 { return }
        lastLive = (live, Date())
        liveTask?.cancel()
        liveTask = Task { [weak self] in
            // Несколько отметок подряд — один запрос.
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            guard let self, !Task.isCancelled else { return }
            let _: Empty? = try? await self.request("PUT", "/me/live", json: live, requiresAuth: true)
        }
    }

    func clearLive() async {
        liveTask?.cancel()
        lastLive = nil
        guard token != nil else { return }
        let _: Empty? = try? await request("DELETE", "/me/live", requiresAuth: true)
    }

    // MARK: Рейтинг недели

    func leaderboard() async throws -> [SocialLeaderboardEntry] {
        try await request("GET", "/leaderboard", requiresAuth: true)
    }

    /// Кто выигрывал прошлые недели среди своих — новые первыми.
    func leaderboardHistory() async throws -> [SocialWeekWinner] {
        try await request("GET", "/leaderboard/history", requiresAuth: true)
    }

    // MARK: Челленджи

    private struct NewChallenge: Encodable {
        let kind: String
        let weeks: Int
        let invite: [String]
        let startsAt: Double
    }

    private struct ChallengeScores: Encodable { let scores: [ChallengeScoreUpdate] }
    private struct ScoresResponse: Decodable { let updated: Int }

    func challenges() async throws -> [SocialChallenge] {
        try await request("GET", "/challenges", requiresAuth: true)
    }

    /// Начало — полночь сегодняшнего дня: тренировка, сделанная утром, уже в счёт.
    func createChallenge(kind: ChallengeKind, weeks: Int, invite: [String], now: Date = Date()) async throws -> SocialChallenge {
        let start = WeeklyStatsBuilder.calendar.startOfDay(for: now)
        return try await request(
            "POST", "/challenges",
            json: NewChallenge(kind: kind.rawValue, weeks: weeks, invite: invite, startsAt: (start.timeIntervalSince1970 * 1000).rounded()),
            requiresAuth: true
        )
    }

    func challenge(_ id: String, action: String) async throws -> SocialChallenge {
        try await request("POST", "/challenges/\(id)/\(action)", requiresAuth: true)
    }

    func postChallengeScores(_ scores: [ChallengeScoreUpdate]) async throws {
        let _: ScoresResponse = try await request("PUT", "/challenges/scores", json: ChallengeScores(scores: scores), requiresAuth: true)
    }

    // MARK: Фото

    private struct ImageResponse: Decodable { let id: String }

    /// Фото уменьшается до 1280 px по длинной стороне: так пост грузится быстро,
    /// а в бесплатную базу помещаются тысячи тренировок.
    func uploadImage(_ data: Data) async throws -> String {
        let response: ImageResponse = try await request("POST", "/images", data: data, contentType: "image/jpeg", requiresAuth: true)
        return response.id
    }

    func uploadImage(_ image: UIImage, maxSide: CGFloat = 1280) async throws -> String {
        guard let data = Self.jpeg(image, maxSide: maxSide) else { throw SocialError(message: "Не удалось подготовить фото") }
        return try await uploadImage(data)
    }

    nonisolated static func jpeg(_ image: UIImage, maxSide: CGFloat = 1280, quality: CGFloat = 0.72) -> Data? {
        let size = image.size
        let scale = min(1, maxSide / max(size.width, size.height, 1))
        let target = CGSize(width: (size.width * scale).rounded(), height: (size.height * scale).rounded())
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let resized = UIGraphicsImageRenderer(size: target, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: target))
        }
        return resized.jpegData(compressionQuality: quality)
    }

    // MARK: Публикация

    /// Кладёт пост в очередь на телефоне и сразу пробует отправить.
    /// Нет связи — пост дождётся следующего открытия приложения.
    func enqueue(_ draft: SocialPostDraft, photos: [UIImage]) {
        let files = photos.prefix(5).compactMap { Self.jpeg($0) }
        var saved = draft
        saved.photoCount = files.count
        saved.uploadedPhotos = Array(repeating: nil, count: files.count)
        SocialOutboxStore.save(saved, photos: files)
        outbox = SocialOutboxStore.loadAll()
        flushOutbox()
    }

    func discard(_ draft: SocialPostDraft) {
        SocialOutboxStore.remove(draft.clientId)
        outbox = SocialOutboxStore.loadAll()
    }

    private struct PostBody: Encodable {
        let clientId: String
        let title: String
        let description: String
        let durationSeconds: Int
        let volumeKg: Double
        let sets: Int
        let effort: Int?
        let photos: [String]
        let workout: SocialWorkout
        let together: String?
    }

    func flushOutbox() {
        flushMessages()
        guard token != nil, !isFlushing, !outbox.isEmpty else { return }
        isFlushing = true
        outboxError = nil
        Task {
            defer { isFlushing = false }
            for var draft in outbox {
                do {
                    if draft.uploadedPhotos.count != draft.photoCount {
                        draft.uploadedPhotos = Array(repeating: nil, count: draft.photoCount)
                    }
                    for index in 0..<draft.photoCount where draft.uploadedPhotos[index] == nil {
                        guard let data = SocialOutboxStore.photo(draft.clientId, index) else { continue }
                        draft.uploadedPhotos[index] = try await uploadImage(data)
                        SocialOutboxStore.update(draft)
                    }
                    let body = PostBody(
                        clientId: draft.clientId,
                        title: draft.title,
                        description: draft.description,
                        durationSeconds: draft.durationSeconds,
                        volumeKg: draft.volumeKg,
                        sets: draft.sets,
                        effort: draft.effort,
                        photos: draft.uploadedPhotos.compactMap { $0 },
                        workout: draft.workout,
                        together: draft.together
                    )
                    let _: SocialPost = try await request("POST", "/posts", json: body, requiresAuth: true)
                    SocialOutboxStore.remove(draft.clientId)
                    outbox = SocialOutboxStore.loadAll()
                    feedVersion += 1
                } catch {
                    outboxError = error.socialMessage
                    break
                }
            }
            await refreshMe()
            // Пост совместной тренировки отмечает её законченной.
            if together.active != nil { await refreshTogether() }
        }
    }
}

/// Обёртка, чтобы закодировать любое `Encodable` в один вызов `JSONEncoder`.
private struct AnyEncodable: Encodable {
    let value: Encodable
    init(_ value: Encodable) { self.value = value }
    func encode(to encoder: Encoder) throws { try value.encode(to: encoder) }
}

// MARK: - Очередь публикаций на диске

/// Каждый неотправленный пост — папка с `draft.json` и фото `photo-N.jpg`.
enum SocialOutboxStore {
    private static var root: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent("SocialOutbox", isDirectory: true)
    }

    private static func folder(_ id: String) -> URL { root.appendingPathComponent(id, isDirectory: true) }

    static func save(_ draft: SocialPostDraft, photos: [Data]) {
        let dir = folder(draft.clientId)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        for (index, data) in photos.enumerated() {
            try? data.write(to: dir.appendingPathComponent("photo-\(index).jpg"), options: .atomic)
        }
        update(draft)
    }

    static func update(_ draft: SocialPostDraft) {
        let dir = folder(draft.clientId)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        if let data = try? JSONEncoder().encode(draft) {
            try? data.write(to: dir.appendingPathComponent("draft.json"), options: .atomic)
        }
    }

    static func photo(_ id: String, _ index: Int) -> Data? {
        try? Data(contentsOf: folder(id).appendingPathComponent("photo-\(index).jpg"))
    }

    static func remove(_ id: String) {
        try? FileManager.default.removeItem(at: folder(id))
    }

    static func loadAll() -> [SocialPostDraft] {
        guard let items = try? FileManager.default.contentsOfDirectory(at: root, includingPropertiesForKeys: nil) else { return [] }
        return items.compactMap { dir in
            guard let data = try? Data(contentsOf: dir.appendingPathComponent("draft.json")) else { return nil }
            return try? JSONDecoder().decode(SocialPostDraft.self, from: data)
        }
        .sorted { $0.createdAt < $1.createdAt }
    }
}

// MARK: - Очередь сообщений на диске

/// Каждое неотправленное сообщение — папка с `pending.json`, рядом `photo.jpg`
/// или `voice.m4a`, если они ещё не загружены.
enum ChatOutboxStore {
    private static var root: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent("ChatOutbox", isDirectory: true)
    }

    private static func folder(_ id: String) -> URL { root.appendingPathComponent(id, isDirectory: true) }

    static func save(_ item: ChatPending, photo: Data?, voice: Data?) {
        let dir = folder(item.clientId)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        if let photo { try? photo.write(to: dir.appendingPathComponent("photo.jpg"), options: .atomic) }
        if let voice { try? voice.write(to: dir.appendingPathComponent("voice.m4a"), options: .atomic) }
        update(item)
    }

    static func update(_ item: ChatPending) {
        let dir = folder(item.clientId)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        if let data = try? JSONEncoder().encode(item) {
            try? data.write(to: dir.appendingPathComponent("pending.json"), options: .atomic)
        }
    }

    static func photo(_ id: String) -> Data? {
        try? Data(contentsOf: folder(id).appendingPathComponent("photo.jpg"))
    }

    static func voice(_ id: String) -> Data? {
        try? Data(contentsOf: folder(id).appendingPathComponent("voice.m4a"))
    }

    static func remove(_ id: String) {
        try? FileManager.default.removeItem(at: folder(id))
    }

    static func loadAll() -> [ChatPending] {
        guard let items = try? FileManager.default.contentsOfDirectory(at: root, includingPropertiesForKeys: nil) else { return [] }
        return items.compactMap { dir in
            guard let data = try? Data(contentsOf: dir.appendingPathComponent("pending.json")) else { return nil }
            return try? JSONDecoder().decode(ChatPending.self, from: data)
        }
        .sorted { $0.createdAt < $1.createdAt }
    }
}
