import Foundation

// MARK: - Лента STRAIN: то, что приходит с сервера и уходит на него
//
// Поля совпадают с ответами `server/social.js` и с Android-версией: сервер
// хранит тренировку и программы как есть, поэтому имена ключей — общий договор.
// Всё необязательное декодируется мягко: старая или чужая версия приложения
// могла не прислать поле, и пост из-за этого не должен пропадать из ленты.

struct SocialAuthor: Codable, Hashable, Sendable {
    let id: String
    let username: String
    var avatar: String?
}

struct SocialUserShort: Codable, Hashable, Identifiable, Sendable {
    let id: String
    let username: String
    var avatar: String?
    var bio: String
    var isFollowing: Bool
    var isMe: Bool

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        username = try c.decode(String.self, forKey: .username)
        avatar = try c.decodeIfPresent(String.self, forKey: .avatar)
        bio = try c.decodeIfPresent(String.self, forKey: .bio) ?? ""
        isFollowing = try c.decodeIfPresent(Bool.self, forKey: .isFollowing) ?? false
        isMe = try c.decodeIfPresent(Bool.self, forKey: .isMe) ?? false
    }
}

/// Медаль на показ: какое достижение и на каком уровне.
struct SocialBadge: Codable, Hashable, Sendable {
    let id: String
    let level: Int
}

/// Тренировочный день программы в профиле: название, группы мышц и упражнения.
struct SocialProgramDay: Codable, Hashable, Identifiable, Sendable {
    struct Exercise: Codable, Hashable, Sendable {
        let name: String
        let sets: Int
        let reps: String
        let load: String
    }

    var id: String { title + "\(exercises.count)" }
    let title: String
    let groups: [String]
    let exercises: [Exercise]

    var setCount: Int { exercises.reduce(0) { $0 + $1.sets } }

    init(title: String, groups: [String], exercises: [Exercise]) {
        self.title = title
        self.groups = groups
        self.exercises = exercises
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        title = try c.decodeIfPresent(String.self, forKey: .title) ?? "Тренировка"
        groups = try c.decodeIfPresent([String].self, forKey: .groups) ?? []
        exercises = try c.decodeIfPresent([Exercise].self, forKey: .exercises) ?? []
    }
}

struct SocialUser: Codable, Hashable, Identifiable, Sendable {
    let id: String
    var username: String
    var bio: String
    var heightCm: Int?
    var weightKg: Double?
    var showBody: Bool
    var avatar: String?
    var cover: String?
    var programs: [SocialProgramDay]
    var achievements: [SocialBadge]
    var workouts: Int
    var followers: Int
    var following: Int
    var isFollowing: Bool
    var isMe: Bool
    /// Идёт тренировка — что делает прямо сейчас.
    var live: SocialLive?
    /// Когда последний раз заходил — для «был(а) N минут назад».
    var lastSeen: Double?

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        username = try c.decode(String.self, forKey: .username)
        live = try? c.decodeIfPresent(SocialLive.self, forKey: .live)
        lastSeen = try? c.decodeIfPresent(Double.self, forKey: .lastSeen)
        bio = try c.decodeIfPresent(String.self, forKey: .bio) ?? ""
        heightCm = try c.decodeIfPresent(Int.self, forKey: .heightCm)
        weightKg = try c.decodeIfPresent(Double.self, forKey: .weightKg)
        showBody = try c.decodeIfPresent(Bool.self, forKey: .showBody) ?? true
        avatar = try c.decodeIfPresent(String.self, forKey: .avatar)
        cover = try c.decodeIfPresent(String.self, forKey: .cover)
        programs = (try? c.decodeIfPresent([SocialProgramDay].self, forKey: .programs)) ?? []
        achievements = (try? c.decodeIfPresent([SocialBadge].self, forKey: .achievements)) ?? []
        workouts = try c.decodeIfPresent(Int.self, forKey: .workouts) ?? 0
        followers = try c.decodeIfPresent(Int.self, forKey: .followers) ?? 0
        following = try c.decodeIfPresent(Int.self, forKey: .following) ?? 0
        isFollowing = try c.decodeIfPresent(Bool.self, forKey: .isFollowing) ?? false
        isMe = try c.decodeIfPresent(Bool.self, forKey: .isMe) ?? false
    }

    /// «178 см · 75 кг» — то, что видно под юзернеймом.
    var bodyLine: String? {
        var parts: [String] = []
        if let heightCm { parts.append("\(heightCm) см") }
        if let weightKg { parts.append("\(WeightFormat.plain(weightKg)) кг") }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }
}

// MARK: - Тренировка внутри поста

struct SocialWorkout: Codable, Hashable, Sendable {
    struct SetLine: Codable, Hashable, Sendable {
        let reps: Int
        let weight: Double
        var record: Bool

        init(reps: Int, weight: Double, record: Bool = false) {
            self.reps = reps
            self.weight = weight
            self.record = record
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            reps = try c.decodeIfPresent(Int.self, forKey: .reps) ?? 0
            weight = try c.decodeIfPresent(Double.self, forKey: .weight) ?? 0
            record = try c.decodeIfPresent(Bool.self, forKey: .record) ?? false
        }

        /// «80 кг × 5», а без веса — «12 повт.».
        var text: String {
            weight > 0 ? "\(WeightFormat.kilogramsPrecise(weight)) × \(reps)" : "\(reps) повт."
        }
    }

    struct Exercise: Codable, Hashable, Sendable, Identifiable {
        var id: String { name }
        let name: String
        let sets: [SetLine]

        init(name: String, sets: [SetLine]) {
            self.name = name
            self.sets = sets
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            name = try c.decodeIfPresent(String.self, forKey: .name) ?? "Упражнение"
            sets = (try? c.decodeIfPresent([SetLine].self, forKey: .sets)) ?? []
        }

        /// Лучший подход — по нему пост коротко рассказывает об упражнении.
        var best: SetLine? { sets.max { ($0.weight, $0.reps) < ($1.weight, $1.reps) } }
        var hasRecord: Bool { sets.contains(where: \.record) }
    }

    var program: String?
    var exercises: [Exercise]

    init(program: String?, exercises: [Exercise]) {
        self.program = program
        self.exercises = exercises
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        program = try c.decodeIfPresent(String.self, forKey: .program)
        exercises = (try? c.decodeIfPresent([Exercise].self, forKey: .exercises)) ?? []
    }

    static let empty = SocialWorkout(program: nil, exercises: [])
}

struct SocialPost: Codable, Hashable, Identifiable, Sendable {
    let id: String
    let author: SocialAuthor
    let createdAt: Double
    let title: String
    let description: String
    let durationSeconds: Int
    let volumeKg: Double
    let sets: Int
    let effort: Int?
    let photos: [String]
    let workout: SocialWorkout
    var likes: Int
    var comments: Int
    var liked: Bool
    /// С кем тренировались вместе.
    var partner: SocialAuthor?

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        author = try c.decode(SocialAuthor.self, forKey: .author)
        partner = try? c.decodeIfPresent(SocialAuthor.self, forKey: .partner)
        createdAt = try c.decodeIfPresent(Double.self, forKey: .createdAt) ?? 0
        title = try c.decodeIfPresent(String.self, forKey: .title) ?? "Тренировка"
        description = try c.decodeIfPresent(String.self, forKey: .description) ?? ""
        durationSeconds = try c.decodeIfPresent(Int.self, forKey: .durationSeconds) ?? 0
        volumeKg = try c.decodeIfPresent(Double.self, forKey: .volumeKg) ?? 0
        sets = try c.decodeIfPresent(Int.self, forKey: .sets) ?? 0
        effort = try c.decodeIfPresent(Int.self, forKey: .effort)
        photos = (try? c.decodeIfPresent([String].self, forKey: .photos)) ?? []
        workout = (try? c.decodeIfPresent(SocialWorkout.self, forKey: .workout)) ?? .empty
        likes = try c.decodeIfPresent(Int.self, forKey: .likes) ?? 0
        comments = try c.decodeIfPresent(Int.self, forKey: .comments) ?? 0
        liked = try c.decodeIfPresent(Bool.self, forKey: .liked) ?? false
    }

    var date: Date { Date(timeIntervalSince1970: createdAt / 1000) }

    /// Плотность — сколько килограммов в минуту: видно, насколько плотно шла тренировка.
    var density: Double {
        guard durationSeconds >= 60 else { return 0 }
        return volumeKg / (Double(durationSeconds) / 60)
    }
}

struct SocialFeedPage: Codable, Sendable {
    let posts: [SocialPost]
    let next: Double?
}

struct SocialComment: Codable, Hashable, Identifiable, Sendable {
    let id: String
    let postId: String
    let author: SocialAuthor
    let text: String
    let createdAt: Double
    /// Корневой комментарий ветки, если это ответ.
    var replyTo: String?
    /// Кому отвечают — «@имя» перед текстом ответа.
    var replyToUsername: String?

    var date: Date { Date(timeIntervalSince1970: createdAt / 1000) }
    var isReply: Bool { replyTo != nil }

    /// Корневые комментарии по порядку, под каждым — его ответы.
    static func threads(_ comments: [SocialComment]) -> [SocialCommentThread] {
        let ids = Set(comments.map(\.id))
        var replies: [String: [SocialComment]] = [:]
        var roots: [SocialComment] = []
        for comment in comments {
            // Ответ на удалённый корень показываем как обычный комментарий.
            if let root = comment.replyTo, ids.contains(root) {
                replies[root, default: []].append(comment)
            } else {
                roots.append(comment)
            }
        }
        return roots.map { SocialCommentThread(root: $0, replies: replies[$0.id] ?? []) }
    }
}

/// Комментарий и ответы под ним.
struct SocialCommentThread: Identifiable, Hashable, Sendable {
    let root: SocialComment
    let replies: [SocialComment]
    var id: String { root.id }
}

// MARK: - Личные сообщения

struct SocialMessage: Codable, Hashable, Identifiable, Sendable {
    let id: String
    let text: String
    /// Фото в сообщении — id картинки на сервере.
    var image: String?
    let fromMe: Bool
    let createdAt: Double
    var read: Bool
    /// Удалено отправителем — приходит при обновлении переписки, чтобы пропасть и здесь.
    var deleted: Bool = false
    var reactions: [SocialReaction] = []
    /// На какое сообщение это ответ.
    var replyTo: SocialMessageQuote?
    /// Приглашение на совместную тренировку — карточкой в переписке.
    var invite: SocialInvite?

    var date: Date { Date(timeIntervalSince1970: createdAt / 1000) }
    var isLocal: Bool { id.hasPrefix("local-") }
    /// Подпись в списке переписок: фото без текста — «Фото».
    var preview: String { text.isEmpty && image != nil ? "📷 Фото" : text }

    init(id: String, text: String, image: String? = nil, fromMe: Bool, createdAt: Double, read: Bool = false,
         reactions: [SocialReaction] = [], replyTo: SocialMessageQuote? = nil) {
        self.id = id
        self.text = text
        self.image = image
        self.fromMe = fromMe
        self.createdAt = createdAt
        self.read = read
        self.reactions = reactions
        self.replyTo = replyTo
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        text = try c.decodeIfPresent(String.self, forKey: .text) ?? ""
        image = try c.decodeIfPresent(String.self, forKey: .image)
        deleted = try c.decodeIfPresent(Bool.self, forKey: .deleted) ?? false
        reactions = (try? c.decodeIfPresent([SocialReaction].self, forKey: .reactions)) ?? []
        replyTo = try? c.decodeIfPresent(SocialMessageQuote.self, forKey: .replyTo)
        invite = try? c.decodeIfPresent(SocialInvite.self, forKey: .invite)
        fromMe = try c.decodeIfPresent(Bool.self, forKey: .fromMe) ?? false
        createdAt = try c.decodeIfPresent(Double.self, forKey: .createdAt) ?? 0
        read = try c.decodeIfPresent(Bool.self, forKey: .read) ?? false
    }
}

extension SocialMessage {
    /// Переписка после обновления с сервера: новое добавлено, изменённое
    /// (реакции, прочтение) заменено, удалённое убрано. Старые — сверху.
    static func merged(_ current: [SocialMessage], with incoming: [SocialMessage]) -> [SocialMessage] {
        var byId: [String: SocialMessage] = [:]
        for message in current { byId[message.id] = message }
        for message in incoming {
            if message.deleted { byId[message.id] = nil } else { byId[message.id] = message }
        }
        return byId.values.sorted { $0.createdAt == $1.createdAt ? $0.id < $1.id : $0.createdAt < $1.createdAt }
    }
}

struct SocialReaction: Codable, Hashable, Sendable {
    let emoji: String
    var count: Int
    var mine: Bool

    /// Реакции, которые можно поставить, — те же, что принимает сервер.
    static let choices = ["🔥", "💪", "👍", "❤️", "😂", "😮"]

    /// Своя реакция поставлена или снята — сразу, не дожидаясь сервера.
    static func toggled(_ reactions: [SocialReaction], _ emoji: String) -> [SocialReaction] {
        let had = reactions.first { $0.mine }?.emoji
        var result = reactions.compactMap { item -> SocialReaction? in
            guard item.mine else { return item }
            var copy = item
            copy.count -= 1
            copy.mine = false
            return copy.count > 0 ? copy : nil
        }
        guard had != emoji else { return result }
        if let index = result.firstIndex(where: { $0.emoji == emoji }) {
            result[index].count += 1
            result[index].mine = true
        } else {
            result.append(SocialReaction(emoji: emoji, count: 1, mine: true))
        }
        return result
    }
}

/// Цитата сообщения, на которое ответили.
struct SocialMessageQuote: Codable, Hashable, Sendable {
    let id: String
    let text: String
    var image: String?
    let fromMe: Bool

    init(id: String, text: String, image: String?, fromMe: Bool) {
        self.id = id
        self.text = text
        self.image = image
        self.fromMe = fromMe
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        text = try c.decodeIfPresent(String.self, forKey: .text) ?? ""
        image = try c.decodeIfPresent(String.self, forKey: .image)
        fromMe = try c.decodeIfPresent(Bool.self, forKey: .fromMe) ?? false
    }

    var preview: String { text.isEmpty ? (image != nil ? "📷 Фото" : "Сообщение удалено") : text }
}

/// Идущая тренировка друга: что за день, какое упражнение и сколько подходов.
struct SocialLive: Codable, Hashable, Sendable {
    var title: String = ""
    var exercise: String = ""
    var done: Int = 0
    var total: Int = 0
    var setsDone: Int = 0
    var setsTotal: Int = 0
    var startedAt: Double = 0
    var updatedAt: Double?
    /// Тренировка скрыта от подписчиков, но видна напарнику по совместной.
    var partnerOnly: Bool?

    init(title: String, exercise: String, done: Int, total: Int, setsDone: Int, setsTotal: Int, startedAt: Double) {
        self.title = title
        self.exercise = exercise
        self.done = done
        self.total = total
        self.setsDone = setsDone
        self.setsTotal = setsTotal
        self.startedAt = startedAt
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        title = try c.decodeIfPresent(String.self, forKey: .title) ?? ""
        exercise = try c.decodeIfPresent(String.self, forKey: .exercise) ?? ""
        done = try c.decodeIfPresent(Int.self, forKey: .done) ?? 0
        total = try c.decodeIfPresent(Int.self, forKey: .total) ?? 0
        setsDone = try c.decodeIfPresent(Int.self, forKey: .setsDone) ?? 0
        setsTotal = try c.decodeIfPresent(Int.self, forKey: .setsTotal) ?? 0
        startedAt = try c.decodeIfPresent(Double.self, forKey: .startedAt) ?? 0
        updatedAt = try c.decodeIfPresent(Double.self, forKey: .updatedAt)
    }

    /// «Присед · 3/5».
    var line: String {
        guard !exercise.isEmpty else { return title }
        return total > 0 ? "\(exercise) · \(done)/\(total)" : exercise
    }

    var startDate: Date { Date(timeIntervalSince1970: startedAt / 1000) }
}

// MARK: - Совместная тренировка

/// Приглашение в переписке: статус меняется у обоих.
struct SocialInvite: Codable, Hashable, Sendable {
    let id: String
    var status: String
    let fromMe: Bool

    var isPending: Bool { status == "pending" }

    /// Что написать на карточке, когда ответ уже есть.
    var statusText: String {
        switch status {
        case "pending": return fromMe ? "Ждём ответа" : "Зовёт тренироваться вместе"
        case "accepted": return "Тренируетесь вместе"
        case "declined": return fromMe ? "Отказался(-ась)" : "Ты отказался(-ась)"
        case "cancelled": return "Приглашение отменено"
        case "expired": return "Приглашение истекло"
        default: return "Совместная тренировка закончилась"
        }
    }
}

struct SocialTogether: Codable, Hashable, Identifiable, Sendable {
    let id: String
    var status: String
    let fromMe: Bool
    let partner: SocialUserShort
    var createdAt: Double = 0
    var meDone: Bool = false
    var partnerDone: Bool = false
    /// Что сейчас делает напарник — только у идущей совместной тренировки.
    var partnerLive: SocialLive?

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        status = try c.decodeIfPresent(String.self, forKey: .status) ?? "pending"
        fromMe = try c.decodeIfPresent(Bool.self, forKey: .fromMe) ?? false
        partner = try c.decode(SocialUserShort.self, forKey: .partner)
        createdAt = try c.decodeIfPresent(Double.self, forKey: .createdAt) ?? 0
        meDone = try c.decodeIfPresent(Bool.self, forKey: .meDone) ?? false
        partnerDone = try c.decodeIfPresent(Bool.self, forKey: .partnerDone) ?? false
        partnerLive = try? c.decodeIfPresent(SocialLive.self, forKey: .partnerLive)
    }
}

struct SocialTogetherState: Codable, Hashable, Sendable {
    var incoming: [SocialTogether] = []
    var outgoing: [SocialTogether] = []
    var active: SocialTogether?

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        incoming = (try? c.decodeIfPresent([SocialTogether].self, forKey: .incoming)) ?? []
        outgoing = (try? c.decodeIfPresent([SocialTogether].self, forKey: .outgoing)) ?? []
        active = try? c.decodeIfPresent(SocialTogether.self, forKey: .active)
    }
}

struct SocialLiveFriend: Codable, Hashable, Identifiable, Sendable {
    let user: SocialUserShort
    let live: SocialLive
    var id: String { user.id }
}

/// Сводка недели для рейтинга: её присылает само приложение — в ней все
/// тренировки, а не только выложенные в ленту.
struct SocialWeekStats: Codable, Hashable, Sendable {
    var week: String = ""
    var workouts: Int = 0
    var volumeKg: Double = 0
    var records: Int = 0
    var streakWeeks: Int = 0

    init(week: String, workouts: Int, volumeKg: Double, records: Int, streakWeeks: Int) {
        self.week = week
        self.workouts = workouts
        self.volumeKg = volumeKg
        self.records = records
        self.streakWeeks = streakWeeks
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        week = try c.decodeIfPresent(String.self, forKey: .week) ?? ""
        workouts = try c.decodeIfPresent(Int.self, forKey: .workouts) ?? 0
        volumeKg = try c.decodeIfPresent(Double.self, forKey: .volumeKg) ?? 0
        records = try c.decodeIfPresent(Int.self, forKey: .records) ?? 0
        streakWeeks = try c.decodeIfPresent(Int.self, forKey: .streakWeeks) ?? 0
    }
}

struct SocialLeaderboardEntry: Codable, Hashable, Identifiable, Sendable {
    let user: SocialUserShort
    let stats: SocialWeekStats
    var id: String { user.id }
}

// MARK: - Активность

/// Кто что сделал: лайк, комментарий, ответ или подписка.
struct SocialActivityItem: Codable, Hashable, Identifiable, Sendable {
    enum Kind: String, Codable, Sendable { case like, comment, reply, follow }

    let kind: Kind
    let actor: SocialUserShort
    var postId: String?
    var postTitle: String?
    var text: String?
    let createdAt: Double
    var unseen: Bool

    var id: String { "\(kind.rawValue)-\(actor.id)-\(postId ?? "")-\(Int(createdAt))" }
    var date: Date { Date(timeIntervalSince1970: createdAt / 1000) }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        kind = (try? c.decode(Kind.self, forKey: .kind)) ?? .like
        actor = try c.decode(SocialUserShort.self, forKey: .actor)
        postId = try c.decodeIfPresent(String.self, forKey: .postId)
        postTitle = try c.decodeIfPresent(String.self, forKey: .postTitle)
        text = try c.decodeIfPresent(String.self, forKey: .text)
        createdAt = try c.decodeIfPresent(Double.self, forKey: .createdAt) ?? 0
        unseen = try c.decodeIfPresent(Bool.self, forKey: .unseen) ?? false
    }

    /// «поставил(а) лайк тренировке «День 1»».
    var action: String {
        switch kind {
        case .like: return "поставил(а) лайк тренировке «\(postTitle ?? "")»"
        case .comment: return "прокомментировал(а) «\(postTitle ?? "")»: \(text ?? "")"
        case .reply: return "ответил(а) тебе: \(text ?? "")"
        case .follow: return "подписался(-ась) на тебя"
        }
    }
}

struct SocialActivityPage: Codable, Sendable {
    let items: [SocialActivityItem]
    let unseen: Int
}

/// Переписка в списке: с кем, последнее сообщение и сколько не прочитано.
struct SocialChat: Codable, Hashable, Identifiable, Sendable {
    let user: SocialUserShort
    let last: SocialMessage
    var unread: Int
    var live: SocialLive?

    var id: String { user.id }
}

struct SocialChatList: Codable, Sendable {
    let chats: [SocialChat]
    let unread: Int
}

struct SocialChatPage: Codable, Sendable {
    let user: SocialUserShort
    let messages: [SocialMessage]
    let next: Double?
    /// Время сервера на момент ответа: с него начинается следующее обновление.
    var serverTime: Double?
    var typing: Bool?
    var lastSeen: Double?
    var live: SocialLive?
}

/// Пост, который ещё не ушёл на сервер: лежит на телефоне вместе с фото
/// и отправляется, как только появится связь.
struct SocialPostDraft: Codable, Hashable, Identifiable, Sendable {
    var id: String { clientId }
    let clientId: String
    let createdAt: Date
    var title: String
    var description: String
    var durationSeconds: Int
    var volumeKg: Double
    var sets: Int
    var effort: Int?
    var workout: SocialWorkout
    var photoCount: Int
    /// Уже загруженные фото: после обрыва связи их не грузим второй раз.
    var uploadedPhotos: [String?]
    /// Совместная тренировка — пост выйдет «вместе с» напарником.
    var together: String?
}

// MARK: - Подписи

enum SocialFormat {
    /// «только что», «5 минут назад», «вчера», «12 окт».
    static func ago(_ date: Date, now: Date = Date(), calendar: Calendar = .current) -> String {
        let seconds = max(0, now.timeIntervalSince(date))
        if seconds < 60 { return "только что" }
        let minutes = Int(seconds / 60)
        if minutes < 60 { return "\(minutes) \(RuPlural.word(minutes, "минуту", "минуты", "минут")) назад" }
        let hours = minutes / 60
        if hours < 24 && calendar.isDate(date, inSameDayAs: now) {
            return "\(hours) \(RuPlural.word(hours, "час", "часа", "часов")) назад"
        }
        if let yesterday = calendar.date(byAdding: .day, value: -1, to: now), calendar.isDate(date, inSameDayAs: yesterday) {
            return "вчера"
        }
        let days = calendar.dateComponents([.day], from: calendar.startOfDay(for: date), to: calendar.startOfDay(for: now)).day ?? 0
        if days < 7 { return "\(days) \(RuPlural.word(days, "день", "дня", "дней")) назад" }
        return RuDate.shortDayMonth(date, calendar: calendar)
    }

    /// «в сети» или «был(а) 5 минут назад».
    static func lastSeen(_ date: Date, now: Date = Date(), calendar: Calendar = .current) -> String {
        now.timeIntervalSince(date) < 120 ? "в сети" : "был(а) " + ago(date, now: now, calendar: calendar)
    }

    /// «1ч 48м», «45м».
    static func duration(_ seconds: Int) -> String {
        let hours = seconds / 3600
        let minutes = (seconds % 3600) / 60
        return hours > 0 ? "\(hours)ч \(minutes)м" : "\(max(minutes, seconds > 0 ? 1 : 0))м"
    }

    /// «1 470» — с пробелом между тысячами, как пишут вес по-русски.
    static func grouped(_ value: Double) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.groupingSeparator = "\u{2009}"
        formatter.maximumFractionDigits = 0
        return formatter.string(from: NSNumber(value: value.rounded())) ?? "\(Int(value.rounded()))"
    }

    /// Юзернейм так, как его примет сервер: строчные латинские буквы, цифры, точка и подчёркивание.
    static func normalizedUsername(_ raw: String) -> String {
        raw.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "@", with: "")
            .lowercased()
    }

    static func isValidUsername(_ value: String) -> Bool {
        value.range(of: "^[a-z0-9_.]{3,24}$", options: .regularExpression) != nil
    }
}
