import Foundation
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

    var isSignedIn: Bool { token != nil }

    static let defaultServer = "https://strain-feed.onrender.com"

    var serverURL: String {
        // Свой ключ, а не «strain_backend_url» бро-трекера: там мог остаться старый адрес.
        UserDefaults.standard.string(forKey: "social_server_url") ?? Self.defaultServer
    }

    private let session: URLSession
    private var syncTask: Task<Void, Never>?

    private init() {
        let configuration = URLSessionConfiguration.default
        // Спящий бесплатный сервер просыпается до минуты — короче ждать нельзя.
        configuration.timeoutIntervalForRequest = 75
        configuration.waitsForConnectivity = false
        session = URLSession(configuration: configuration)
        token = SocialKeychain.load()
        outbox = SocialOutboxStore.loadAll()
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
        requiresAuth: Bool = false
    ) async throws -> T {
        guard var components = URLComponents(string: serverURL + "/v2" + path) else {
            throw SocialError(message: "Неверный адрес сервера")
        }
        if !query.isEmpty { components.queryItems = query }
        guard let url = components.url else { throw SocialError(message: "Неверный адрес сервера") }

        var request = URLRequest(url: url)
        request.httpMethod = method
        if let token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
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
            throw SocialError.offline
        }
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            let message = (try? JSONDecoder().decode(ErrorBody.self, from: body).error)
                ?? (status == 503 ? "Сервер ленты ещё не настроен или перезапускается." : "Ошибка сервера (\(status))")
            if status == 401 && token != nil && path == "/me" { signOut() }
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
        let previous = token
        token = cleaned
        do {
            me = try await request("GET", "/me")
            setToken(cleaned)
        } catch {
            token = previous
            throw error
        }
    }

    /// Код из копии подхватываем молча, только если своего профиля ещё нет.
    func adoptBackupToken(_ code: String?) {
        guard token == nil, let code, !code.isEmpty else { return }
        Task { try? await signIn(code: code) }
    }

    func signOut() {
        setToken(nil)
        me = nil
        feedVersion += 1
    }

    func deleteAccount() async throws {
        let _: Empty = try await request("DELETE", "/me", requiresAuth: true)
        signOut()
    }

    private func setToken(_ value: String?) {
        token = value
        SocialKeychain.save(value)
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

        enum CodingKeys: String, CodingKey {
            case username, bio, heightCm, weightKg, showBody, avatar, cover, programs, achievements
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
        }
    }

    @discardableResult
    func update(_ patch: ProfilePatch) async throws -> SocialUser {
        let user: SocialUser = try await request("PATCH", "/me", json: patch, requiresAuth: true)
        me = user
        return user
    }

    /// Программа, медали и вес из приложения — в профиль. Шлём, только если
    /// что-то поменялось, и не чаще раза в несколько секунд.
    func scheduleSync(from profile: ProgramProfile) {
        guard token != nil else { return }
        let patch = ProfilePatch(
            weightKg: profile.latestBodyWeight,
            programs: SocialProgramBuilder.days(for: profile),
            achievements: AchievementCalculator.badges(from: AchievementCalculator.progress(for: profile))
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

    func addComment(_ postId: String, text: String) async throws -> SocialComment {
        try await request("POST", "/posts/\(postId)/comments", json: ["text": text], requiresAuth: true)
    }

    func deleteComment(_ id: String) async throws {
        let _: Empty = try await request("DELETE", "/comments/\(id)", requiresAuth: true)
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
    }

    func flushOutbox() {
        guard token != nil, !isFlushing, !outbox.isEmpty else { return }
        isFlushing = true
        outboxError = nil
        Task {
            defer { isFlushing = false }
            for var draft in outbox {
                do {
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
                        workout: draft.workout
                    )
                    let _: SocialPost = try await request("POST", "/posts", json: body, requiresAuth: true)
                    SocialOutboxStore.remove(draft.clientId)
                    outbox = SocialOutboxStore.loadAll()
                    feedVersion += 1
                } catch {
                    outboxError = (error as? SocialError)?.message ?? error.localizedDescription
                    break
                }
            }
            await refreshMe()
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
