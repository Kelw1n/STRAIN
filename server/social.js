// Лента STRAIN: профили, подписки, посты с тренировкой и фото, лайки, комментарии.
//
// Данные живут в Postgres (бесплатный Neon): диск бесплатного Render стирается
// при каждом засыпании сервера, поэтому на нём ничего не храним. Адрес базы —
// в переменной окружения DATABASE_URL. Нет её — раздел отвечает 503, а старые
// маршруты бро-трекера продолжают работать.

const crypto = require('crypto');
const express = require('express');

const USERNAME = /^[a-z0-9_.]{3,24}$/;
const RESERVED = new Set(['admin', 'strain', 'support', 'root', 'api', 'me', 'help', 'official']);
const MAX_IMAGE_BYTES = 3 * 1024 * 1024;
// Голосовое — до минуты: AAC 32 кбит/с даёт ~240 КБ, берём с запасом.
const MAX_VOICE_BYTES = 1.5 * 1024 * 1024;
const MAX_VOICE_MS = 62 * 1000;
const PAGE = 20;
// «Тренируется сейчас» гаснет само, если телефон давно не присылал статус.
const LIVE_TTL_MS = 20 * 60 * 1000;
// «Печатает…» держится несколько секунд после последнего сигнала.
const TYPING_TTL_MS = 6000;
// Насколько раньше начинать следующее «что изменилось».
const SINCE_OVERLAP_MS = 2000;
const REACTIONS = new Set(['🔥', '💪', '👍', '❤️', '😂', '😮']);
// Приглашение на совместную тренировку ждёт ответа два часа, а сама
// совместная тренировка длится не дольше шести.
const INVITE_TTL_MS = 2 * 60 * 60 * 1000;
const TOGETHER_TTL_MS = 6 * 60 * 60 * 1000;
// Виды челленджей: больше тренировок, больше тоннаж, недели без пропусков, рост силы.
const CHALLENGE_KINDS = new Set(['workouts', 'volume', 'streak', 'strength']);
const DAY_MS = 24 * 60 * 60 * 1000;
// Счёт ещё сутки после конца: тренировку последнего дня могли отметить без сети.
const CHALLENGE_GRACE_MS = DAY_MS;
const WEEK_KEY = /^\d{4}-W\d{2}$/;

const SCHEMA = `
CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    username TEXT UNIQUE NOT NULL,
    token_hash TEXT UNIQUE NOT NULL,
    bio TEXT NOT NULL DEFAULT '',
    height_cm INT,
    weight_kg REAL,
    show_body BOOLEAN NOT NULL DEFAULT TRUE,
    avatar_id TEXT,
    cover_id TEXT,
    programs JSONB NOT NULL DEFAULT '[]',
    achievements JSONB NOT NULL DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS follows (
    follower TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    followee TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (follower, followee)
);
CREATE INDEX IF NOT EXISTS follows_followee ON follows(followee);
CREATE TABLE IF NOT EXISTS images (
    id TEXT PRIMARY KEY,
    owner TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    mime TEXT NOT NULL,
    data BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Голосовые сообщения: звук AAC (m4a), длительность и «волна» — громкость
-- по кусочкам строкой шестнадцатеричных цифр, чтобы пузырь рисовался сразу.
CREATE TABLE IF NOT EXISTS voices (
    id TEXT PRIMARY KEY,
    owner TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    mime TEXT NOT NULL,
    duration_ms INTEGER NOT NULL,
    waveform TEXT NOT NULL DEFAULT '',
    data BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS posts (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_id TEXT NOT NULL,
    title TEXT NOT NULL DEFAULT '',
    description TEXT NOT NULL DEFAULT '',
    duration_seconds INT NOT NULL DEFAULT 0,
    volume_kg REAL NOT NULL DEFAULT 0,
    sets INT NOT NULL DEFAULT 0,
    effort INT,
    photos JSONB NOT NULL DEFAULT '[]',
    workout JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, client_id)
);
CREATE INDEX IF NOT EXISTS posts_created ON posts(created_at DESC);
CREATE INDEX IF NOT EXISTS posts_user ON posts(user_id, created_at DESC);
CREATE TABLE IF NOT EXISTS likes (
    post_id TEXT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id)
);
CREATE TABLE IF NOT EXISTS comments (
    id TEXT PRIMARY KEY,
    post_id TEXT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS comments_post ON comments(post_id, created_at);
-- Ответ под комментарием: reply_to — корневой комментарий ветки, reply_to_user —
-- кому отвечают (в ветке можно отвечать и на ответы).
ALTER TABLE comments ADD COLUMN IF NOT EXISTS reply_to TEXT REFERENCES comments(id) ON DELETE CASCADE;
ALTER TABLE comments ADD COLUMN IF NOT EXISTS reply_to_user TEXT REFERENCES users(id) ON DELETE SET NULL;
-- Личные сообщения: переписка двух людей — все сообщения между ними.
CREATE TABLE IF NOT EXISTS messages (
    id TEXT PRIMARY KEY,
    sender TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recipient TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_id TEXT,
    text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at TIMESTAMPTZ,
    UNIQUE (sender, client_id)
);
-- Фото в сообщении: id из images, хозяин фото — отправитель.
ALTER TABLE messages ADD COLUMN IF NOT EXISTS image TEXT;
-- Голосовое в сообщении: id из voices, хозяин — отправитель.
ALTER TABLE messages ADD COLUMN IF NOT EXISTS voice TEXT;
-- Ответ на сообщение, реакции {userId: emoji} и время последней правки:
-- открытая переписка подтягивает всё, что поменялось после прошлого запроса.
ALTER TABLE messages ADD COLUMN IF NOT EXISTS reply_to TEXT REFERENCES messages(id) ON DELETE SET NULL;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS reactions JSONB NOT NULL DEFAULT '{}';
ALTER TABLE messages ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE messages ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS messages_updated ON messages (updated_at);
-- «Тренируется сейчас», недельная сводка для рейтинга и когда последний раз
-- открывали «Активность».
ALTER TABLE users ADD COLUMN IF NOT EXISTS live JSONB;
ALTER TABLE users ADD COLUMN IF NOT EXISTS live_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS stats JSONB NOT NULL DEFAULT '{}';
ALTER TABLE users ADD COLUMN IF NOT EXISTS activity_seen_at TIMESTAMPTZ NOT NULL DEFAULT now();
CREATE INDEX IF NOT EXISTS comments_reply_user ON comments (reply_to_user);
CREATE INDEX IF NOT EXISTS messages_pair ON messages (LEAST(sender, recipient), GREATEST(sender, recipient), created_at DESC);
CREATE INDEX IF NOT EXISTS messages_unread ON messages (recipient) WHERE read_at IS NULL;
-- Совместная тренировка: приглашение, ответ и кто уже закончил.
CREATE TABLE IF NOT EXISTS together (
    id TEXT PRIMARY KEY,
    inviter TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    invitee TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'pending',
    inviter_done BOOLEAN NOT NULL DEFAULT FALSE,
    invitee_done BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS together_inviter ON together (inviter, created_at DESC);
CREATE INDEX IF NOT EXISTS together_invitee ON together (invitee, created_at DESC);
-- Приглашение видно в переписке карточкой, а пост — «вместе с @другом».
ALTER TABLE messages ADD COLUMN IF NOT EXISTS together TEXT REFERENCES together(id) ON DELETE SET NULL;
ALTER TABLE posts ADD COLUMN IF NOT EXISTS partner TEXT REFERENCES users(id) ON DELETE SET NULL;
-- Правка текста, пересылка (от кого изначально), «прослушано» у голосового
-- и тренировка карточкой прямо в переписке.
ALTER TABLE messages ADD COLUMN IF NOT EXISTS edited_at TIMESTAMPTZ;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS forwarded BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS forward_from TEXT REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS heard_at TIMESTAMPTZ;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS workout JSONB;
CREATE INDEX IF NOT EXISTS messages_reply ON messages (reply_to) WHERE reply_to IS NOT NULL;
-- Сводки прошлых недель: по ним корона победителю недели и история побед.
CREATE TABLE IF NOT EXISTS weekly_stats (
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    week TEXT NOT NULL,
    workouts INT NOT NULL DEFAULT 0,
    volume_kg REAL NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, week)
);
-- Челленджи с друзьями: вид, сроки и участники со счётом, который считают
-- сами приложения — вся история тренировок живёт на телефоне.
CREATE TABLE IF NOT EXISTS challenges (
    id TEXT PRIMARY KEY,
    owner TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind TEXT NOT NULL,
    weeks INT NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS challenge_members (
    challenge_id TEXT NOT NULL REFERENCES challenges(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'invited',
    score DOUBLE PRECISION NOT NULL DEFAULT 0,
    detail TEXT NOT NULL DEFAULT '',
    invited_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (challenge_id, user_id)
);
CREATE INDEX IF NOT EXISTS challenge_members_user ON challenge_members (user_id, status);
`;

const newId = () => crypto.randomBytes(12).toString('hex');
const hashToken = (token) => crypto.createHash('sha256').update(String(token)).digest('hex');

class HttpError extends Error {
    constructor(status, message) {
        super(message);
        this.status = status;
    }
}

// Ограничитель частоты в памяти: на одном сервере этого хватает, а сброс
// при перезапуске не страшен — он защищает от случайных циклов и спама.
function limiter(limit, windowMs) {
    const hits = new Map();
    return (key) => {
        const now = Date.now();
        const entry = hits.get(key);
        if (!entry || now - entry.start > windowMs) {
            hits.set(key, { start: now, count: 1 });
            return;
        }
        entry.count += 1;
        if (entry.count > limit) throw new HttpError(429, 'Слишком часто. Попробуй через несколько минут.');
    };
}

function text(value, max) {
    if (value === undefined || value === null) return '';
    return String(value).replace(/\u0000/g, '').trim().slice(0, max);
}

function number(value, min, max) {
    if (value === undefined || value === null || value === '') return null;
    const parsed = Number(value);
    if (!Number.isFinite(parsed)) return null;
    return Math.min(max, Math.max(min, parsed));
}

function imageIds(value) {
    if (!Array.isArray(value)) return [];
    return value.filter((id) => typeof id === 'string' && /^[a-f0-9]{24}$/.test(id)).slice(0, 5);
}

function jsonLimited(value, maxBytes, fallback) {
    if (value === undefined || value === null) return fallback;
    const raw = JSON.stringify(value);
    if (raw.length > maxBytes) throw new HttpError(413, 'Слишком много данных');
    return value;
}

// Тренировка карточкой в сообщении — объект с упражнениями, не больше 32 КБ.
function chatWorkout(value) {
    if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
    return jsonLimited(value, 32_000, null);
}

function createSocial({ databaseUrl, pool: injectedPool } = {}) {
    const router = express.Router();
    let pool = injectedPool || null;
    let ready = null;

    if (!pool && databaseUrl) {
        const { Pool } = require('pg');
        pool = new Pool({ connectionString: databaseUrl, max: 5, idleTimeoutMillis: 30_000 });
        pool.on('error', (error) => console.error('Postgres pool error:', error.message));
    }

    async function init() {
        if (!pool) return false;
        if (!ready) {
            ready = pool.query(SCHEMA).then(() => true).catch((error) => {
                ready = null;
                throw error;
            });
        }
        return ready;
    }

    const q = async (sql, params) => (await pool.query(sql, params)).rows;

    const registerLimit = limiter(10, 60 * 60 * 1000);
    const postLimit = limiter(40, 60 * 60 * 1000);
    const commentLimit = limiter(150, 60 * 60 * 1000);
    const messageLimit = limiter(400, 60 * 60 * 1000);
    const imageLimit = limiter(80, 60 * 60 * 1000);
    const voiceLimit = limiter(120, 60 * 60 * 1000);

    // Все маршруты раздела ждут готовую базу.
    router.use(async (req, res, next) => {
        if (!pool) return res.status(503).json({ error: 'База ленты не подключена (нет DATABASE_URL)' });
        try {
            await init();
            next();
        } catch (error) {
            console.error('Schema init failed:', error.message);
            res.status(503).json({ error: 'База ленты недоступна, попробуй позже' });
        }
    });

    async function viewer(req) {
        const header = req.get('authorization') || '';
        const match = header.match(/^Bearer\s+([a-f0-9]{64})$/i);
        if (!match) return null;
        const rows = await q('UPDATE users SET last_seen = now() WHERE token_hash = $1 RETURNING *', [hashToken(match[1])]);
        return rows[0] || null;
    }

    async function requireViewer(req) {
        const me = await viewer(req);
        if (!me) throw new HttpError(401, 'Нужно войти в профиль STRAIN');
        return me;
    }

    const wrap = (handler) => (req, res) => {
        handler(req, res).catch((error) => {
            if (error instanceof HttpError) return res.status(error.status).json({ error: error.message });
            if (error && error.code === '23505') return res.status(409).json({ error: 'Такой юзернейм уже занят' });
            console.error(req.method, req.originalUrl, error);
            res.status(500).json({ error: 'Ошибка сервера' });
        });
    };

    // MARK: Профиль в ответе

    // Победы в челленджах: законченные, где было хотя бы двое и счёт первый.
    const CHALLENGE_WINS_SQL = `
        SELECT count(*) FROM challenge_members m JOIN challenges c ON c.id = m.challenge_id
        WHERE m.user_id = $1 AND m.status = 'joined' AND m.score > 0 AND c.ends_at < now()
        AND (SELECT count(*) FROM challenge_members o WHERE o.challenge_id = c.id AND o.status = 'joined') >= 2
        AND NOT EXISTS (SELECT 1 FROM challenge_members o WHERE o.challenge_id = c.id AND o.status = 'joined' AND o.score > m.score)`;

    async function userView(row, me) {
        const [counts] = await q(
            `SELECT
                (SELECT count(*) FROM posts WHERE user_id = $1)::int AS workouts,
                (SELECT count(*) FROM follows WHERE followee = $1)::int AS followers,
                (SELECT count(*) FROM follows WHERE follower = $1)::int AS following,
                (${CHALLENGE_WINS_SQL})::int AS challenge_wins,
                EXISTS (SELECT 1 FROM follows WHERE follower = $2 AND followee = $1) AS is_following`,
            [row.id, me ? me.id : '']
        );
        const isMe = !!me && me.id === row.id;
        return {
            id: row.id,
            username: row.username,
            bio: row.bio,
            heightCm: row.show_body || isMe ? row.height_cm : null,
            weightKg: row.show_body || isMe ? row.weight_kg : null,
            showBody: row.show_body,
            avatar: row.avatar_id,
            cover: row.cover_id,
            programs: row.programs,
            achievements: row.achievements,
            level: levelOf(row.stats && row.stats.level),
            challengeWins: counts.challenge_wins,
            workouts: counts.workouts,
            followers: counts.followers,
            following: counts.following,
            isFollowing: counts.is_following,
            isMe,
            live: liveView(row),
            lastSeen: new Date(row.last_seen).getTime(),
            createdAt: new Date(row.created_at).getTime()
        };
    }

    // Статус тренировки — только свежий: приложение могли закрыть, не завершив её.
    const freshLive = (row) => (row.live && row.live_at && Date.now() - new Date(row.live_at).getTime() < LIVE_TTL_MS
        ? { ...row.live, updatedAt: new Date(row.live_at).getTime() }
        : null);
    // «Только напарнику» — когда человек скрыл тренировку, но тренируется вместе с другом.
    const liveView = (row) => {
        const live = freshLive(row);
        if (!live || live.partnerOnly) return null;
        delete live.partnerOnly;
        return live;
    };

    // Уровень присылает само приложение в сводке недели: опыт считается по всей
    // истории тренировок, которая живёт на телефоне.
    const levelOf = (value) => {
        const level = Math.floor(Number(value));
        return Number.isFinite(level) && level >= 1 && level <= 50 ? level : null;
    };

    const shortUser = (row) => ({
        id: row.id,
        username: row.username,
        avatar: row.avatar_id,
        bio: row.bio,
        level: levelOf(row.stats && row.stats.level)
    });

    async function userByName(username) {
        const rows = await q('SELECT * FROM users WHERE username = $1', [String(username || '').toLowerCase()]);
        if (!rows[0]) throw new HttpError(404, 'Пользователь не найден');
        return rows[0];
    }

    // MARK: Пост в ответе

    const POST_SELECT = `
        SELECT p.*, u.username, u.avatar_id, u.stats->'level' AS author_level,
            (SELECT count(*) FROM likes l WHERE l.post_id = p.id)::int AS like_count,
            (SELECT count(*) FROM comments c WHERE c.post_id = p.id)::int AS comment_count,
            EXISTS (SELECT 1 FROM likes l WHERE l.post_id = p.id AND l.user_id = $1) AS liked
        , pu.username AS partner_username, pu.avatar_id AS partner_avatar
        FROM posts p JOIN users u ON u.id = p.user_id LEFT JOIN users pu ON pu.id = p.partner`;

    const postView = (row) => ({
        id: row.id,
        author: { id: row.user_id, username: row.username, avatar: row.avatar_id, level: levelOf(row.author_level) },
        createdAt: new Date(row.created_at).getTime(),
        title: row.title,
        description: row.description,
        durationSeconds: row.duration_seconds,
        volumeKg: row.volume_kg,
        sets: row.sets,
        effort: row.effort,
        photos: row.photos,
        workout: row.workout,
        partner: row.partner && row.partner_username
            ? { id: row.partner, username: row.partner_username, avatar: row.partner_avatar }
            : null,
        likes: row.like_count,
        comments: row.comment_count,
        liked: row.liked
    });

    function page(req) {
        const before = Number(req.query.before);
        const limit = Math.min(50, Math.max(1, Number(req.query.limit) || PAGE));
        return { before: Number.isFinite(before) && before > 0 ? new Date(before) : new Date(Date.now() + 60_000), limit };
    }

    function pageResult(rows, limit) {
        const posts = rows.map(postView);
        return { posts, next: posts.length === limit ? posts[posts.length - 1].createdAt : null };
    }

    // MARK: Регистрация и вход

    router.post('/register', wrap(async (req, res) => {
        registerLimit(req.ip);
        const username = text(req.body && req.body.username, 24).toLowerCase();
        if (!USERNAME.test(username)) {
            throw new HttpError(400, 'Юзернейм: 3–24 символа — латиница, цифры, точка и подчёркивание');
        }
        if (RESERVED.has(username)) throw new HttpError(409, 'Такой юзернейм уже занят');
        const token = crypto.randomBytes(32).toString('hex');
        const rows = await q(
            'INSERT INTO users (id, username, token_hash) VALUES ($1, $2, $3) RETURNING *',
            [newId(), username, hashToken(token)]
        );
        res.status(201).json({ token, user: await userView(rows[0], rows[0]) });
    }));

    router.get('/me', wrap(async (req, res) => {
        const me = await requireViewer(req);
        res.json(await userView(me, me));
    }));

    router.patch('/me', wrap(async (req, res) => {
        const me = await requireViewer(req);
        const body = req.body || {};
        const sets = [];
        const params = [];
        const set = (column, value) => {
            params.push(value);
            sets.push(`${column} = $${params.length}`);
        };
        if (body.username !== undefined) {
            const username = text(body.username, 24).toLowerCase();
            if (!USERNAME.test(username)) throw new HttpError(400, 'Юзернейм: 3–24 символа — латиница, цифры, точка и подчёркивание');
            if (RESERVED.has(username)) throw new HttpError(409, 'Такой юзернейм уже занят');
            set('username', username);
        }
        if (body.bio !== undefined) set('bio', text(body.bio, 160));
        if (body.heightCm !== undefined) set('height_cm', number(body.heightCm, 100, 250));
        if (body.weightKg !== undefined) set('weight_kg', number(body.weightKg, 20, 400));
        if (body.showBody !== undefined) set('show_body', !!body.showBody);
        if (body.avatar !== undefined) set('avatar_id', imageIds([body.avatar])[0] || null);
        if (body.cover !== undefined) set('cover_id', imageIds([body.cover])[0] || null);
        if (body.programs !== undefined) set('programs', JSON.stringify(jsonLimited(body.programs, 64_000, [])));
        if (body.achievements !== undefined) set('achievements', JSON.stringify(jsonLimited(body.achievements, 16_000, [])));
        if (body.stats !== undefined) set('stats', JSON.stringify(jsonLimited(body.stats, 4_000, {})));
        await rememberWeek(me, body.stats);
        if (sets.length === 0) return res.json(await userView(me, me));
        params.push(me.id);
        const rows = await q(`UPDATE users SET ${sets.join(', ')} WHERE id = $${params.length} RETURNING *`, params);
        res.json(await userView(rows[0], rows[0]));
    }));

    // Удаление профиля целиком: посты, фото, лайки и комментарии уходят каскадом.
    router.delete('/me', wrap(async (req, res) => {
        const me = await requireViewer(req);
        await q('DELETE FROM users WHERE id = $1', [me.id]);
        res.json({ success: true });
    }));

    // MARK: Люди

    router.get('/users', wrap(async (req, res) => {
        const me = await viewer(req);
        const query = text(req.query.q, 24).toLowerCase().replace(/^@/, '');
        const rows = query
            ? await q(
                `SELECT * FROM users WHERE username LIKE $1 ESCAPE '\\'
                 ORDER BY (username LIKE $2 ESCAPE '\\') DESC, last_seen DESC LIMIT 30`,
                ['%' + query.replace(/[\\%_]/g, '\\$&') + '%', query.replace(/[\\%_]/g, '\\$&') + '%']
            )
            : await q('SELECT * FROM users WHERE id <> $1 ORDER BY last_seen DESC LIMIT 30', [me ? me.id : '']);
        const following = me
            ? new Set((await q('SELECT followee FROM follows WHERE follower = $1', [me.id])).map((r) => r.followee))
            : new Set();
        res.json(rows.map((row) => ({ ...shortUser(row), isFollowing: following.has(row.id), isMe: !!me && me.id === row.id })));
    }));

    router.get('/users/:username', wrap(async (req, res) => {
        const me = await viewer(req);
        res.json(await userView(await userByName(req.params.username), me));
    }));

    router.get('/users/:username/posts', wrap(async (req, res) => {
        const me = await viewer(req);
        const user = await userByName(req.params.username);
        const { before, limit } = page(req);
        const rows = await q(
            `${POST_SELECT} WHERE p.user_id = $2 AND p.created_at < $3 ORDER BY p.created_at DESC LIMIT $4`,
            [me ? me.id : '', user.id, before, limit]
        );
        res.json(pageResult(rows, limit));
    }));

    async function people(req, res, sql) {
        const me = await viewer(req);
        const user = await userByName(req.params.username);
        const rows = await q(sql, [user.id]);
        const following = me
            ? new Set((await q('SELECT followee FROM follows WHERE follower = $1', [me.id])).map((r) => r.followee))
            : new Set();
        res.json(rows.map((row) => ({ ...shortUser(row), isFollowing: following.has(row.id), isMe: !!me && me.id === row.id })));
    }

    router.get('/users/:username/followers', wrap((req, res) => people(req, res,
        'SELECT u.* FROM follows f JOIN users u ON u.id = f.follower WHERE f.followee = $1 ORDER BY f.created_at DESC LIMIT 200')));

    router.get('/users/:username/following', wrap((req, res) => people(req, res,
        'SELECT u.* FROM follows f JOIN users u ON u.id = f.followee WHERE f.follower = $1 ORDER BY f.created_at DESC LIMIT 200')));

    router.post('/users/:username/follow', wrap(async (req, res) => {
        const me = await requireViewer(req);
        const user = await userByName(req.params.username);
        if (user.id === me.id) throw new HttpError(400, 'Нельзя подписаться на себя');
        await q('INSERT INTO follows (follower, followee) VALUES ($1, $2) ON CONFLICT DO NOTHING', [me.id, user.id]);
        res.json(await userView(user, me));
    }));

    router.delete('/users/:username/follow', wrap(async (req, res) => {
        const me = await requireViewer(req);
        const user = await userByName(req.params.username);
        await q('DELETE FROM follows WHERE follower = $1 AND followee = $2', [me.id, user.id]);
        res.json(await userView(user, me));
    }));

    // MARK: Фото

    router.post('/images', express.raw({ type: ['image/jpeg', 'image/png', 'image/heic', 'image/webp'], limit: MAX_IMAGE_BYTES }), wrap(async (req, res) => {
        const me = await requireViewer(req);
        imageLimit(me.id);
        if (!Buffer.isBuffer(req.body) || req.body.length === 0) throw new HttpError(400, 'Пустое фото');
        const id = newId();
        await q('INSERT INTO images (id, owner, mime, data) VALUES ($1, $2, $3, $4)', [id, me.id, req.get('content-type'), req.body]);
        res.status(201).json({ id });
    }));

    router.get('/images/:id', wrap(async (req, res) => {
        if (!/^[a-f0-9]{24}$/.test(req.params.id)) throw new HttpError(404, 'Нет такого фото');
        const rows = await q('SELECT mime, data FROM images WHERE id = $1', [req.params.id]);
        if (!rows[0]) throw new HttpError(404, 'Нет такого фото');
        // Фото по одному адресу никогда не меняется — телефон кэширует его навсегда.
        res.set('Cache-Control', 'public, max-age=31536000, immutable');
        res.type(rows[0].mime).send(rows[0].data);
    }));

    // MARK: Голосовые

    // Длительность и волна — в адресе: тело запроса целиком занято звуком.
    router.post('/voices', express.raw({ type: ['audio/mp4', 'audio/m4a', 'audio/x-m4a', 'audio/aac'], limit: MAX_VOICE_BYTES }), wrap(async (req, res) => {
        const me = await requireViewer(req);
        voiceLimit(me.id);
        if (!Buffer.isBuffer(req.body) || req.body.length === 0) throw new HttpError(400, 'Пустое голосовое');
        const durationMs = Math.round(Number(req.query.durationMs));
        if (!Number.isFinite(durationMs) || durationMs < 300) throw new HttpError(400, 'Слишком короткое голосовое');
        if (durationMs > MAX_VOICE_MS) throw new HttpError(400, 'Голосовое — не длиннее минуты');
        const waveform = /^[0-9a-f]{0,64}$/.test(String(req.query.waveform || '')) ? String(req.query.waveform || '') : '';
        const id = newId();
        await q(
            'INSERT INTO voices (id, owner, mime, duration_ms, waveform, data) VALUES ($1, $2, $3, $4, $5, $6)',
            [id, me.id, req.get('content-type'), durationMs, waveform, req.body]
        );
        res.status(201).json({ id, durationMs, waveform });
    }));

    router.get('/voices/:id', wrap(async (req, res) => {
        if (!/^[a-f0-9]{24}$/.test(req.params.id)) throw new HttpError(404, 'Нет такого голосового');
        const rows = await q('SELECT mime, data FROM voices WHERE id = $1', [req.params.id]);
        if (!rows[0]) throw new HttpError(404, 'Нет такого голосового');
        // Звук по одному адресу никогда не меняется — телефон кэширует его навсегда.
        res.set('Cache-Control', 'public, max-age=31536000, immutable');
        res.type(rows[0].mime).send(rows[0].data);
    }));

    // MARK: Посты

    router.post('/posts', wrap(async (req, res) => {
        const me = await requireViewer(req);
        postLimit(me.id);
        const body = req.body || {};
        const clientId = text(body.clientId, 64) || newId();
        const photos = imageIds(body.photos);
        if (photos.length > 0) {
            const owned = await q('SELECT id FROM images WHERE owner = $1 AND id = ANY($2)', [me.id, photos]);
            if (owned.length !== photos.length) throw new HttpError(400, 'Фото не найдено');
        }
        const effort = number(body.effort, 1, 10);
        // Совместная тренировка — пост «вместе с»; напарник отмечается закончившим.
        let partner = null;
        if (body.together) {
            const session = (await q(
                `SELECT * FROM together WHERE id = $1 AND (inviter = $2 OR invitee = $2)
                 AND status IN ('accepted', 'ended') AND responded_at > $3`,
                [String(body.together), me.id, new Date(Date.now() - 2 * TOGETHER_TTL_MS)]
            ))[0];
            if (session) {
                partner = session.inviter === me.id ? session.invitee : session.inviter;
                await markDone(session, me);
            }
        }
        const inserted = await q(
            `INSERT INTO posts (id, user_id, client_id, title, description, duration_seconds, volume_kg, sets, effort, photos, workout, partner)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)
             ON CONFLICT (user_id, client_id) DO NOTHING RETURNING id`,
            [
                newId(), me.id, clientId,
                text(body.title, 80) || 'Тренировка',
                text(body.description, 1000),
                Math.round(number(body.durationSeconds, 0, 86_400) || 0),
                number(body.volumeKg, 0, 1_000_000) || 0,
                Math.round(number(body.sets, 0, 1000) || 0),
                effort === null ? null : Math.round(effort),
                JSON.stringify(photos),
                JSON.stringify(jsonLimited(body.workout, 64_000, {})),
                partner
            ]
        );
        // Повтор той же публикации (обрыв связи после отправки) возвращает уже созданный пост.
        const id = inserted[0]
            ? inserted[0].id
            : (await q('SELECT id FROM posts WHERE user_id = $1 AND client_id = $2', [me.id, clientId]))[0].id;
        const rows = await q(`${POST_SELECT} WHERE p.id = $2`, [me.id, id]);
        res.status(inserted[0] ? 201 : 200).json(postView(rows[0]));
    }));

    router.get('/posts/:id', wrap(async (req, res) => {
        const me = await viewer(req);
        const rows = await q(`${POST_SELECT} WHERE p.id = $2`, [me ? me.id : '', req.params.id]);
        if (!rows[0]) throw new HttpError(404, 'Пост удалён');
        res.json(postView(rows[0]));
    }));

    router.delete('/posts/:id', wrap(async (req, res) => {
        const me = await requireViewer(req);
        const rows = await q('DELETE FROM posts WHERE id = $1 AND user_id = $2 RETURNING photos', [req.params.id, me.id]);
        if (!rows[0]) throw new HttpError(404, 'Пост не найден');
        const photos = imageIds(rows[0].photos);
        if (photos.length > 0) await q('DELETE FROM images WHERE owner = $1 AND id = ANY($2)', [me.id, photos]);
        res.json({ success: true });
    }));

    router.get('/feed', wrap(async (req, res) => {
        const me = await viewer(req);
        const { before, limit } = page(req);
        const following = req.query.scope === 'following';
        if (following && !me) throw new HttpError(401, 'Нужно войти в профиль STRAIN');
        const rows = following
            ? await q(
                `${POST_SELECT} WHERE (p.user_id = $1 OR p.user_id IN (SELECT followee FROM follows WHERE follower = $1))
                 AND p.created_at < $2 ORDER BY p.created_at DESC LIMIT $3`,
                [me.id, before, limit]
            )
            : await q(`${POST_SELECT} WHERE p.created_at < $2 ORDER BY p.created_at DESC LIMIT $3`, [me ? me.id : '', before, limit]);
        res.json(pageResult(rows, limit));
    }));

    router.post('/posts/:id/like', wrap(async (req, res) => {
        const me = await requireViewer(req);
        await q('INSERT INTO likes (post_id, user_id) SELECT id, $2 FROM posts WHERE id = $1 ON CONFLICT DO NOTHING', [req.params.id, me.id]);
        const rows = await q(`${POST_SELECT} WHERE p.id = $2`, [me.id, req.params.id]);
        if (!rows[0]) throw new HttpError(404, 'Пост удалён');
        res.json(postView(rows[0]));
    }));

    router.delete('/posts/:id/like', wrap(async (req, res) => {
        const me = await requireViewer(req);
        await q('DELETE FROM likes WHERE post_id = $1 AND user_id = $2', [req.params.id, me.id]);
        const rows = await q(`${POST_SELECT} WHERE p.id = $2`, [me.id, req.params.id]);
        if (!rows[0]) throw new HttpError(404, 'Пост удалён');
        res.json(postView(rows[0]));
    }));

    // MARK: Комментарии

    const commentView = (row) => ({
        id: row.id,
        postId: row.post_id,
        author: { id: row.user_id, username: row.username, avatar: row.avatar_id },
        text: row.text,
        replyTo: row.reply_to || null,
        replyToUsername: row.reply_to_username || null,
        createdAt: new Date(row.created_at).getTime()
    });

    const COMMENT_SELECT = `
        SELECT c.*, u.username, u.avatar_id, r.username AS reply_to_username
        FROM comments c JOIN users u ON u.id = c.user_id LEFT JOIN users r ON r.id = c.reply_to_user`;

    router.get('/posts/:id/comments', wrap(async (req, res) => {
        const rows = await q(`${COMMENT_SELECT} WHERE c.post_id = $1 ORDER BY c.created_at ASC LIMIT 500`, [req.params.id]);
        res.json(rows.map(commentView));
    }));

    // replyTo — комментарий, на который отвечают. Ответ встаёт в ветку его
    // корневого комментария: ветки в одну глубину, как в Instagram.
    router.post('/posts/:id/comments', wrap(async (req, res) => {
        const me = await requireViewer(req);
        commentLimit(me.id);
        const body = text(req.body && req.body.text, 500);
        if (!body) throw new HttpError(400, 'Пустой комментарий');
        let root = null;
        let replyUser = null;
        const replyTo = req.body && req.body.replyTo;
        if (replyTo) {
            const target = (await q('SELECT id, reply_to, user_id FROM comments WHERE id = $1 AND post_id = $2', [String(replyTo), req.params.id]))[0];
            if (!target) throw new HttpError(404, 'Комментарий удалён');
            root = target.reply_to || target.id;
            replyUser = target.user_id;
        }
        const rows = await q(
            `INSERT INTO comments (id, post_id, user_id, text, reply_to, reply_to_user)
             SELECT $1, id, $3, $4, $5, $6 FROM posts WHERE id = $2 RETURNING id`,
            [newId(), req.params.id, me.id, body, root, replyUser]
        );
        if (!rows[0]) throw new HttpError(404, 'Пост удалён');
        const created = await q(`${COMMENT_SELECT} WHERE c.id = $1`, [rows[0].id]);
        res.status(201).json(commentView(created[0]));
    }));

    // Комментарий удаляет его автор или хозяин поста.
    router.delete('/comments/:id', wrap(async (req, res) => {
        const me = await requireViewer(req);
        const rows = await q(
            `DELETE FROM comments c USING posts p WHERE c.id = $1 AND p.id = c.post_id
             AND (c.user_id = $2 OR p.user_id = $2) RETURNING c.id`,
            [req.params.id, me.id]
        );
        if (!rows[0]) throw new HttpError(404, 'Комментарий не найден');
        res.json({ success: true });
    }));

    // MARK: Личные сообщения

    // Реакции в ответе: какие, сколько и стоит ли среди них своя.
    function reactionsView(reactions, me) {
        const counts = new Map();
        for (const [user, emoji] of Object.entries(reactions || {})) {
            const entry = counts.get(emoji) || { emoji, count: 0, mine: false };
            entry.count += 1;
            if (user === me.id) entry.mine = true;
            counts.set(emoji, entry);
        }
        return [...counts.values()];
    }

    const messageView = (row, me) => (row.deleted_at
        ? { id: row.id, deleted: true, fromMe: row.sender === me.id, createdAt: new Date(row.created_at).getTime(), text: '' }
        : {
            id: row.id,
            // Свой clientId — чтобы приложение узнало сообщение из своей очереди
            // неотправленных, даже если ответ на отправку потерялся.
            clientId: row.sender === me.id ? row.client_id || null : null,
            text: row.text,
            image: row.image || null,
            voice: row.voice
                ? { id: row.voice, durationMs: Number(row.v_duration) || 0, waveform: row.v_waveform || '' }
                : null,
            workout: row.workout || null,
            fromMe: row.sender === me.id,
            createdAt: new Date(row.created_at).getTime(),
            read: row.read_at !== null,
            heard: !!row.heard_at,
            edited: !!row.edited_at,
            forwarded: row.forwarded ? { username: row.forward_username || null } : null,
            reactions: reactionsView(row.reactions, me),
            invite: row.together && row.t_status
                ? {
                    id: row.together,
                    status: togetherStatus({
                        status: row.t_status, created_at: row.t_created, responded_at: row.t_responded,
                        inviter_done: row.t_inviter_done, invitee_done: row.t_invitee_done
                    }),
                    fromMe: row.sender === me.id
                }
                : null,
            replyTo: row.reply_id
                ? {
                    id: row.reply_id,
                    text: row.reply_deleted ? '' : row.reply_text,
                    image: row.reply_deleted ? null : row.reply_image,
                    voice: !row.reply_deleted && !!row.reply_voice,
                    fromMe: row.reply_sender === me.id
                }
                : null
        });

    const MESSAGE_SELECT = `
        SELECT m.*, r.id AS reply_id, r.text AS reply_text, r.image AS reply_image, r.voice AS reply_voice,
               r.sender AS reply_sender, r.deleted_at IS NOT NULL AS reply_deleted,
               t.status AS t_status, t.created_at AS t_created, t.responded_at AS t_responded,
               t.inviter_done AS t_inviter_done, t.invitee_done AS t_invitee_done,
               v.duration_ms AS v_duration, v.waveform AS v_waveform, f.username AS forward_username
        FROM messages m LEFT JOIN messages r ON r.id = m.reply_to LEFT JOIN together t ON t.id = m.together
        LEFT JOIN voices v ON v.id = m.voice LEFT JOIN users f ON f.id = m.forward_from`;

    // Фото и звук сообщения могли переслать дальше — удаляем их, только когда
    // на них больше не ссылается ни одно сообщение, профиль или пост.
    const dropImageIfUnused = (id) => q(
        `DELETE FROM images WHERE id = $1
         AND NOT EXISTS (SELECT 1 FROM messages WHERE image = $1)
         AND NOT EXISTS (SELECT 1 FROM users WHERE avatar_id = $1 OR cover_id = $1)
         AND NOT EXISTS (SELECT 1 FROM posts WHERE photos ? $1)`,
        [id]
    );
    const dropVoiceIfUnused = (id) => q('DELETE FROM voices WHERE id = $1 AND NOT EXISTS (SELECT 1 FROM messages WHERE voice = $1)', [id]);
    // Ответы на изменённое или удалённое сообщение: их цитата обновится у обоих.
    const touchReplies = (id) => q('UPDATE messages SET updated_at = now() WHERE reply_to = $1', [id]);

    async function unreadCount(me) {
        const [row] = await q('SELECT count(*)::int AS count FROM messages WHERE recipient = $1 AND read_at IS NULL AND deleted_at IS NULL', [me.id]);
        return row.count;
    }

    // Переписки: с кем, последнее сообщение и сколько не прочитано.
    router.get('/chats', wrap(async (req, res) => {
        const me = await requireViewer(req);
        const rows = await q(
            `SELECT DISTINCT ON (other) other, m.*
             FROM (SELECT *, CASE WHEN sender = $1 THEN recipient ELSE sender END AS other
                   FROM messages WHERE (sender = $1 OR recipient = $1) AND deleted_at IS NULL) m
             ORDER BY other, created_at DESC`,
            [me.id]
        );
        if (rows.length === 0) return res.json({ chats: [], unread: 0 });
        const others = rows.map((row) => row.other);
        const users = new Map((await q('SELECT * FROM users WHERE id = ANY($1)', [others])).map((u) => [u.id, u]));
        const unread = new Map((await q(
            `SELECT sender, count(*)::int AS count FROM messages
             WHERE recipient = $1 AND read_at IS NULL AND deleted_at IS NULL GROUP BY sender`,
            [me.id]
        )).map((row) => [row.sender, row.count]));
        const chats = rows
            .filter((row) => users.has(row.other))
            .sort((a, b) => b.created_at - a.created_at)
            .map((row) => ({
                user: shortUser(users.get(row.other)),
                last: messageView(row, me),
                unread: unread.get(row.other) || 0,
                live: liveView(users.get(row.other))
            }));
        res.json({ chats, unread: chats.reduce((sum, chat) => sum + chat.unread, 0) });
    }));

    router.get('/chats/unread', wrap(async (req, res) => {
        const me = await requireViewer(req);
        res.json({ unread: await unreadCount(me) });
    }));

    // Кто сейчас печатает кому: в памяти сервера, этого хватает на несколько секунд.
    const typing = new Map();

    router.post('/chats/:username/typing', wrap(async (req, res) => {
        const me = await requireViewer(req);
        const other = await userByName(req.params.username);
        typing.set(`${me.id}:${other.id}`, Date.now());
        if (typing.size > 5000) {
            const now = Date.now();
            for (const [key, at] of typing) if (now - at > TYPING_TTL_MS) typing.delete(key);
        }
        res.json({ success: true });
    }));

    // Сообщения переписки, новые первыми; открытая переписка помечается прочитанной.
    // `since` — всё, что появилось или поменялось (реакции, прочтение, удаление)
    // начиная с `serverTime` прошлого ответа.
    router.get('/chats/:username/messages', wrap(async (req, res) => {
        const me = await requireViewer(req);
        const other = await userByName(req.params.username);
        const serverTime = Date.now();
        const before = Number(req.query.before);
        const after = Number(req.query.after);
        const since = Number(req.query.since);
        const limit = Math.min(100, Math.max(1, Number(req.query.limit) || 50));
        const params = [me.id, other.id, limit];
        // Время в ответе — в миллисекундах, а в базе — в микросекундах: сравниваем
        // по миллисекундам, иначе «новее, чем последнее» вернуло бы его же.
        let range = 'AND m.deleted_at IS NULL';
        if (Number.isFinite(since) && since > 0) {
            params.push(new Date(since));
            range = `AND date_trunc('milliseconds', m.updated_at) >= $${params.length}`;
        } else if (Number.isFinite(after) && after > 0) {
            params.push(new Date(after));
            range = `AND m.deleted_at IS NULL AND date_trunc('milliseconds', m.created_at) > $${params.length}`;
        } else if (Number.isFinite(before) && before > 0) {
            params.push(new Date(before));
            range = `AND m.deleted_at IS NULL AND date_trunc('milliseconds', m.created_at) < $${params.length}`;
        }
        const rows = await q(
            `${MESSAGE_SELECT}
             WHERE ((m.sender = $1 AND m.recipient = $2) OR (m.sender = $2 AND m.recipient = $1)) ${range}
             ORDER BY m.created_at DESC LIMIT $3`,
            params
        );
        await q(
            `UPDATE messages SET read_at = now(), updated_at = now()
             WHERE sender = $1 AND recipient = $2 AND read_at IS NULL AND deleted_at IS NULL`,
            [other.id, me.id]
        );
        const messages = rows.map((row) => messageView(row, me));
        const typedAt = typing.get(`${other.id}:${me.id}`) || 0;
        res.json({
            user: shortUser(other),
            messages,
            next: !since && rows.length === limit ? messages[messages.length - 1].createdAt : null,
            // С запасом: часы базы и сервера могут чуть расходиться, а повтор
            // уже полученного приложение просто заменит тем же.
            serverTime: serverTime - SINCE_OVERLAP_MS,
            typing: serverTime - typedAt < TYPING_TTL_MS,
            lastSeen: new Date(other.last_seen).getTime(),
            live: liveView(other)
        });
    }));

    router.post('/chats/:username/messages', wrap(async (req, res) => {
        const me = await requireViewer(req);
        messageLimit(me.id);
        const other = await userByName(req.params.username);
        if (other.id === me.id) throw new HttpError(400, 'Нельзя написать самому себе');
        let body = text(req.body && req.body.text, 2000);
        // Фото и голосовое — только свои, загруженные через /images и /voices.
        let image = imageIds([req.body && req.body.image])[0] || null;
        let voice = imageIds([req.body && req.body.voice])[0] || null;
        let workout = chatWorkout(req.body && req.body.workout);
        // Пересылка: содержимое берём из сообщения, которое видно отправителю,
        // а «от кого» — от автора самого первого сообщения цепочки.
        let forwardFrom = null;
        const forward = req.body && req.body.forward ? String(req.body.forward) : null;
        if (forward) {
            const source = (await q(
                'SELECT * FROM messages WHERE id = $1 AND deleted_at IS NULL AND (sender = $2 OR recipient = $2)',
                [forward, me.id]
            ))[0];
            if (!source) throw new HttpError(404, 'Сообщение удалено');
            if (source.together) throw new HttpError(400, 'Приглашение нельзя переслать');
            ({ text: body, image, voice, workout } = source);
            forwardFrom = source.forwarded ? source.forward_from : source.sender;
        }
        if (!body && !image && !voice && !workout) throw new HttpError(400, 'Пустое сообщение');
        if (!forward && image && !(await q('SELECT 1 FROM images WHERE id = $1 AND owner = $2', [image, me.id]))[0]) {
            throw new HttpError(400, 'Фото не найдено');
        }
        if (!forward && voice && !(await q('SELECT 1 FROM voices WHERE id = $1 AND owner = $2', [voice, me.id]))[0]) {
            throw new HttpError(400, 'Голосовое не найдено');
        }
        // Ответить можно только на сообщение из этой же переписки.
        let replyTo = null;
        if (!forward && req.body && req.body.replyTo) {
            const target = (await q(
                `SELECT id FROM messages WHERE id = $1 AND deleted_at IS NULL
                 AND ((sender = $2 AND recipient = $3) OR (sender = $3 AND recipient = $2))`,
                [String(req.body.replyTo), me.id, other.id]
            ))[0];
            if (!target) throw new HttpError(404, 'Сообщение удалено');
            replyTo = target.id;
        }
        const clientId = text(req.body && req.body.clientId, 64) || null;
        const inserted = await q(
            `INSERT INTO messages (id, sender, recipient, client_id, text, image, voice, reply_to, workout, forwarded, forward_from)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
             ON CONFLICT (sender, client_id) DO NOTHING RETURNING id`,
            [
                newId(), me.id, other.id, clientId, body, image, voice, replyTo,
                workout ? JSON.stringify(workout) : null, !!forward, forwardFrom
            ]
        );
        typing.delete(`${me.id}:${other.id}`);
        // Повтор отправки после обрыва связи возвращает уже созданное сообщение.
        const id = inserted[0] ? inserted[0].id : (await q('SELECT id FROM messages WHERE sender = $1 AND client_id = $2', [me.id, clientId]))[0].id;
        const row = (await q(`${MESSAGE_SELECT} WHERE m.id = $1`, [id]))[0];
        res.status(inserted[0] ? 201 : 200).json(messageView(row, me));
    }));

    // Реакция на сообщение: одна от человека, повтор той же — снимает её.
    router.post('/messages/:id/react', wrap(async (req, res) => {
        const me = await requireViewer(req);
        const emoji = req.body && req.body.emoji ? String(req.body.emoji) : null;
        if (emoji && !REACTIONS.has(emoji)) throw new HttpError(400, 'Такой реакции нет');
        const row = (await q(
            'SELECT * FROM messages WHERE id = $1 AND (sender = $2 OR recipient = $2) AND deleted_at IS NULL',
            [req.params.id, me.id]
        ))[0];
        if (!row) throw new HttpError(404, 'Сообщение не найдено');
        const reactions = { ...(row.reactions || {}) };
        if (!emoji || reactions[me.id] === emoji) delete reactions[me.id];
        else reactions[me.id] = emoji;
        await q('UPDATE messages SET reactions = $2, updated_at = now() WHERE id = $1', [row.id, JSON.stringify(reactions)]);
        const updated = (await q(`${MESSAGE_SELECT} WHERE m.id = $1`, [row.id]))[0];
        res.json(messageView(updated, me));
    }));

    // Правка своего сообщения: текст или подпись к фото. Голосовое, приглашение,
    // тренировку и пересланное не правят — там нечего или нельзя менять.
    router.patch('/messages/:id', wrap(async (req, res) => {
        const me = await requireViewer(req);
        messageLimit(me.id);
        const own = (await q('SELECT * FROM messages WHERE id = $1 AND sender = $2 AND deleted_at IS NULL', [req.params.id, me.id]))[0];
        if (!own) throw new HttpError(404, 'Сообщение не найдено');
        if (own.voice || own.together || own.workout || own.forwarded) throw new HttpError(400, 'Это сообщение нельзя изменить');
        const body = text(req.body && req.body.text, 2000);
        if (!body && !own.image) throw new HttpError(400, 'Пустое сообщение — лучше удали его');
        if (body !== own.text) {
            await q('UPDATE messages SET text = $2, edited_at = now(), updated_at = now() WHERE id = $1', [own.id, body]);
            await touchReplies(own.id);
        }
        const row = (await q(`${MESSAGE_SELECT} WHERE m.id = $1`, [own.id]))[0];
        res.json(messageView(row, me));
    }));

    // Голосовое прослушано получателем — гаснет точка у обоих.
    router.post('/messages/:id/heard', wrap(async (req, res) => {
        const me = await requireViewer(req);
        await q(
            `UPDATE messages SET heard_at = now(), updated_at = now()
             WHERE id = $1 AND recipient = $2 AND voice IS NOT NULL AND heard_at IS NULL AND deleted_at IS NULL`,
            [req.params.id, me.id]
        );
        res.json({ success: true });
    }));

    // Удаление своего сообщения: у собеседника оно тоже пропадёт при следующем обновлении.
    router.delete('/messages/:id', wrap(async (req, res) => {
        const me = await requireViewer(req);
        const own = (await q('SELECT id, image, voice FROM messages WHERE id = $1 AND sender = $2 AND deleted_at IS NULL', [req.params.id, me.id]))[0];
        if (!own) throw new HttpError(404, 'Сообщение не найдено');
        await q(
            `UPDATE messages SET deleted_at = now(), updated_at = now(), text = '', image = NULL, voice = NULL,
             workout = NULL, reactions = '{}' WHERE id = $1`,
            [own.id]
        );
        await touchReplies(own.id);
        if (own.image) await dropImageIfUnused(own.image);
        if (own.voice) await dropVoiceIfUnused(own.voice);
        res.json({ success: true });
    }));

    // MARK: Тренируется сейчас

    // Статус идущей тренировки: что за тренировка, какое упражнение и сколько подходов.
    router.put('/me/live', wrap(async (req, res) => {
        const me = await requireViewer(req);
        const body = req.body || {};
        const live = {
            title: text(body.title, 80),
            exercise: text(body.exercise, 80),
            done: Math.round(number(body.done, 0, 100) || 0),
            total: Math.round(number(body.total, 0, 100) || 0),
            setsDone: Math.round(number(body.setsDone, 0, 500) || 0),
            setsTotal: Math.round(number(body.setsTotal, 0, 500) || 0),
            startedAt: number(body.startedAt, 0, 9e15) || Date.now(),
            partnerOnly: !!body.partnerOnly
        };
        await q('UPDATE users SET live = $2, live_at = now() WHERE id = $1', [me.id, JSON.stringify(live)]);
        res.json({ success: true });
    }));

    router.delete('/me/live', wrap(async (req, res) => {
        const me = await requireViewer(req);
        await q('UPDATE users SET live = NULL, live_at = NULL WHERE id = $1', [me.id]);
        res.json({ success: true });
    }));

    // Кто из подписок тренируется прямо сейчас.
    router.get('/live', wrap(async (req, res) => {
        const me = await requireViewer(req);
        const rows = await q(
            `SELECT u.* FROM follows f JOIN users u ON u.id = f.followee
             WHERE f.follower = $1 AND u.live IS NOT NULL AND u.live_at > $2
             ORDER BY u.live_at DESC LIMIT 50`,
            [me.id, new Date(Date.now() - LIVE_TTL_MS)]
        );
        res.json(rows.map((row) => ({ user: shortUser(row), live: liveView(row) })).filter((item) => item.live));
    }));

    // MARK: Рейтинг недели

    // Свой профиль и подписки со сводкой недели, которую присылают сами приложения:
    // в ней все тренировки, а не только выложенные в ленту.
    router.get('/leaderboard', wrap(async (req, res) => {
        const me = await requireViewer(req);
        const rows = await q(
            `SELECT u.* FROM users u
             WHERE u.id = $1 OR u.id IN (SELECT followee FROM follows WHERE follower = $1)`,
            [me.id]
        );
        // Корона — победителю прошлой недели, рядом — сколько недель выигрывал.
        const winners = await weekWinners(rows.map((row) => row.id), isoWeek(new Date()), 52);
        const lastWeek = isoWeek(new Date(Date.now() - 7 * DAY_MS));
        const wins = new Map();
        for (const w of winners) wins.set(w.user_id, (wins.get(w.user_id) || 0) + 1);
        res.json(rows.map((row) => ({
            user: { ...shortUser(row), isMe: row.id === me.id },
            stats: row.stats || {},
            wins: wins.get(row.id) || 0,
            crown: winners.some((w) => w.week === lastWeek && w.user_id === row.id)
        })));
    }));

    // MARK: Активность

    const ACTIVITY_SQL = `
        SELECT 'like' AS kind, l.created_at, l.user_id AS actor, p.id AS post_id, p.title AS post_title, NULL AS text
        FROM likes l JOIN posts p ON p.id = l.post_id WHERE p.user_id = $1 AND l.user_id <> $1
        UNION ALL
        SELECT 'comment', c.created_at, c.user_id, p.id, p.title, c.text
        FROM comments c JOIN posts p ON p.id = c.post_id
        WHERE p.user_id = $1 AND c.user_id <> $1 AND (c.reply_to_user IS NULL OR c.reply_to_user <> $1)
        UNION ALL
        SELECT 'reply', c.created_at, c.user_id, p.id, p.title, c.text
        FROM comments c JOIN posts p ON p.id = c.post_id WHERE c.reply_to_user = $1 AND c.user_id <> $1
        UNION ALL
        SELECT 'follow', f.created_at, f.follower, NULL, NULL, NULL FROM follows f WHERE f.followee = $1`;

    async function unseenActivity(me) {
        const [row] = await q(`SELECT count(*)::int AS count FROM (${ACTIVITY_SQL}) a WHERE a.created_at > $2`, [me.id, me.activity_seen_at]);
        return row.count;
    }

    // Лайки, комментарии, ответы и новые подписчики — новые первыми.
    router.get('/activity', wrap(async (req, res) => {
        const me = await requireViewer(req);
        const rows = await q(`SELECT * FROM (${ACTIVITY_SQL}) a ORDER BY a.created_at DESC LIMIT 80`, [me.id]);
        const actors = new Map((await q('SELECT * FROM users WHERE id = ANY($1)', [[...new Set(rows.map((r) => r.actor))]])).map((u) => [u.id, u]));
        const seen = new Date(me.activity_seen_at).getTime();
        res.json({
            items: rows.filter((row) => actors.has(row.actor)).map((row) => ({
                kind: row.kind,
                actor: shortUser(actors.get(row.actor)),
                postId: row.post_id,
                postTitle: row.post_title,
                text: row.text,
                createdAt: new Date(row.created_at).getTime(),
                unseen: new Date(row.created_at).getTime() > seen
            })),
            unseen: await unseenActivity(me)
        });
    }));

    router.post('/activity/seen', wrap(async (req, res) => {
        const me = await requireViewer(req);
        await q('UPDATE users SET activity_seen_at = now() WHERE id = $1', [me.id]);
        res.json({ success: true });
    }));

    // Точки на вкладках: непрочитанные сообщения и новая активность одним запросом.
    router.get('/badges', wrap(async (req, res) => {
        const me = await requireViewer(req);
        res.json({
            messages: await unreadCount(me),
            activity: await unseenActivity(me),
            invites: await pendingInvites(me),
            challenges: await pendingChallenges(me)
        });
    }));

    // MARK: Совместная тренировка

    // Статус с учётом времени: неотвеченное приглашение протухает, совместная
    // тренировка заканчивается, когда оба закончили или прошло шесть часов.
    function togetherStatus(row) {
        const now = Date.now();
        if (row.status === 'pending' && now - new Date(row.created_at).getTime() > INVITE_TTL_MS) return 'expired';
        if (row.status === 'accepted'
            && ((row.inviter_done && row.invitee_done) || now - new Date(row.responded_at).getTime() > TOGETHER_TTL_MS)) return 'ended';
        return row.status;
    }

    const togetherView = (row, me, partner) => ({
        id: row.id,
        status: togetherStatus(row),
        fromMe: row.inviter === me.id,
        partner: shortUser(partner),
        createdAt: new Date(row.created_at).getTime(),
        meDone: row.inviter === me.id ? row.inviter_done : row.invitee_done,
        partnerDone: row.inviter === me.id ? row.invitee_done : row.inviter_done
    });

    async function pendingInvites(me) {
        const [row] = await q(
            "SELECT count(*)::int AS count FROM together WHERE invitee = $1 AND status = 'pending' AND created_at > $2",
            [me.id, new Date(Date.now() - INVITE_TTL_MS)]
        );
        return row.count;
    }

    // Карточка приглашения в переписке подтянет новый статус при обновлении.
    const touchInvite = (id) => q('UPDATE messages SET updated_at = now() WHERE together = $1', [id]);

    async function markDone(session, me) {
        const column = session.inviter === me.id ? 'inviter_done' : 'invitee_done';
        await q(`UPDATE together SET ${column} = TRUE WHERE id = $1`, [session.id]);
        await touchInvite(session.id);
    }

    async function sessionFor(req, me) {
        const row = (await q('SELECT * FROM together WHERE id = $1 AND (inviter = $2 OR invitee = $2)', [req.params.id, me.id]))[0];
        if (!row) throw new HttpError(404, 'Приглашение не найдено');
        return row;
    }

    // Позвать друга: приглашение уходит и карточкой в переписку.
    router.post('/users/:username/together', wrap(async (req, res) => {
        const me = await requireViewer(req);
        messageLimit(me.id);
        const other = await userByName(req.params.username);
        if (other.id === me.id) throw new HttpError(400, 'Нельзя позвать самого себя');
        // Уже звали друг друга и ответа ещё нет — второе приглашение не нужно.
        const existing = (await q(
            `SELECT * FROM together WHERE status = 'pending' AND created_at > $3
             AND ((inviter = $1 AND invitee = $2) OR (inviter = $2 AND invitee = $1))
             ORDER BY created_at DESC LIMIT 1`,
            [me.id, other.id, new Date(Date.now() - INVITE_TTL_MS)]
        ))[0];
        if (existing) return res.json(togetherView(existing, me, other));
        const id = newId();
        const [row] = await q('INSERT INTO together (id, inviter, invitee) VALUES ($1, $2, $3) RETURNING *', [id, me.id, other.id]);
        await q(
            'INSERT INTO messages (id, sender, recipient, text, together) VALUES ($1, $2, $3, $4, $5)',
            [newId(), me.id, other.id, 'Го тренироваться вместе 💪', id]
        );
        res.status(201).json(togetherView(row, me, other));
    }));

    // Входящие приглашения, свои ждущие ответа и идущая совместная тренировка
    // с тем, что сейчас делает напарник.
    router.get('/together', wrap(async (req, res) => {
        const me = await requireViewer(req);
        const rows = await q(
            `SELECT * FROM together WHERE (inviter = $1 OR invitee = $1)
             AND ((status = 'pending' AND created_at > $2) OR (status = 'accepted' AND responded_at > $3))
             ORDER BY created_at DESC LIMIT 20`,
            [me.id, new Date(Date.now() - INVITE_TTL_MS), new Date(Date.now() - TOGETHER_TTL_MS)]
        );
        const ids = [...new Set(rows.map((row) => (row.inviter === me.id ? row.invitee : row.inviter)))];
        const users = new Map((await q('SELECT * FROM users WHERE id = ANY($1)', [ids])).map((u) => [u.id, u]));
        const views = rows
            .filter((row) => users.has(row.inviter === me.id ? row.invitee : row.inviter))
            .map((row) => ({ row, view: togetherView(row, me, users.get(row.inviter === me.id ? row.invitee : row.inviter)) }));
        const activeEntry = views.find(({ view }) => view.status === 'accepted' && !view.meDone);
        let active = null;
        if (activeEntry) {
            const partner = users.get(activeEntry.row.inviter === me.id ? activeEntry.row.invitee : activeEntry.row.inviter);
            const live = freshLive(partner);
            if (live) delete live.partnerOnly;
            active = { ...activeEntry.view, partnerLive: live };
        }
        res.json({
            incoming: views.filter(({ view }) => view.status === 'pending' && !view.fromMe).map(({ view }) => view),
            outgoing: views.filter(({ view }) => view.status === 'pending' && view.fromMe).map(({ view }) => view),
            active
        });
    }));

    router.post('/together/:id/accept', wrap(async (req, res) => {
        const me = await requireViewer(req);
        const row = await sessionFor(req, me);
        if (row.invitee !== me.id) throw new HttpError(403, 'Принять может только приглашённый');
        if (togetherStatus(row) !== 'pending') throw new HttpError(409, 'Приглашение уже неактуально');
        // Одна совместная тренировка за раз: прошлые у обоих заканчиваются.
        await q(
            `UPDATE together SET status = 'ended' WHERE status = 'accepted' AND id <> $2
             AND (inviter = ANY($1) OR invitee = ANY($1))`,
            [[row.inviter, row.invitee], row.id]
        );
        const [updated] = await q("UPDATE together SET status = 'accepted', responded_at = now() WHERE id = $1 RETURNING *", [row.id]);
        await touchInvite(row.id);
        const inviter = (await q('SELECT * FROM users WHERE id = $1', [row.inviter]))[0];
        res.json(togetherView(updated, me, inviter));
    }));

    // Отказ, отмена своего приглашения или выход из совместной тренировки.
    router.post('/together/:id/:action(decline|cancel|leave|done)', wrap(async (req, res) => {
        const me = await requireViewer(req);
        const row = await sessionFor(req, me);
        const status = togetherStatus(row);
        const action = req.params.action;
        if (action === 'decline') {
            if (row.invitee !== me.id || status !== 'pending') throw new HttpError(409, 'Приглашение уже неактуально');
            await q("UPDATE together SET status = 'declined', responded_at = now() WHERE id = $1", [row.id]);
        } else if (action === 'cancel') {
            if (row.inviter !== me.id || status !== 'pending') throw new HttpError(409, 'Приглашение уже неактуально');
            await q("UPDATE together SET status = 'cancelled', responded_at = now() WHERE id = $1", [row.id]);
        } else if (action === 'leave') {
            if (status === 'accepted') await q("UPDATE together SET status = 'ended' WHERE id = $1", [row.id]);
        } else if (status === 'accepted') {
            await markDone(row, me);
        }
        await touchInvite(row.id);
        res.json({ success: true });
    }));

    // MARK: Корона недели

    // Сводка недели из приложения ложится и в историю: по ней видно,
    // кто выиграл прошлые недели.
    async function rememberWeek(me, stats) {
        if (!stats || typeof stats !== 'object' || !WEEK_KEY.test(String(stats.week || ''))) return;
        await q(
            `INSERT INTO weekly_stats (user_id, week, workouts, volume_kg) VALUES ($1, $2, $3, $4)
             ON CONFLICT (user_id, week) DO UPDATE SET workouts = $3, volume_kg = $4, updated_at = now()`,
            [me.id, String(stats.week), number(stats.workouts, 0, 100) || 0, number(stats.volumeKg, 0, 10_000_000) || 0]
        );
    }

    // Победитель каждой прошлой недели среди своих: больше тренировок,
    // при равенстве — больше тоннаж.
    async function weekWinners(ids, before, limit) {
        return q(
            `SELECT week, user_id, workouts, volume_kg FROM (
                SELECT *, row_number() OVER (PARTITION BY week ORDER BY workouts DESC, volume_kg DESC, user_id) AS place
                FROM weekly_stats WHERE user_id = ANY($1) AND week < $2 AND workouts > 0
             ) ranked WHERE place = 1 ORDER BY week DESC LIMIT $3`,
            [ids, before, limit]
        );
    }

    // Неделя по ISO, как её считают приложения: «2026-W43».
    function isoWeek(date) {
        const day = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
        const weekday = day.getUTCDay() || 7;
        day.setUTCDate(day.getUTCDate() + 4 - weekday);
        const yearStart = new Date(Date.UTC(day.getUTCFullYear(), 0, 1));
        const week = Math.ceil(((day - yearStart) / DAY_MS + 1) / 7);
        return `${day.getUTCFullYear()}-W${String(week).padStart(2, '0')}`;
    }

    async function circle(me) {
        return [me.id, ...(await q('SELECT followee FROM follows WHERE follower = $1', [me.id])).map((r) => r.followee)];
    }

    // Кто выигрывал недели среди своих — новые первыми.
    router.get('/leaderboard/history', wrap(async (req, res) => {
        const me = await requireViewer(req);
        const winners = await weekWinners(await circle(me), isoWeek(new Date()), 26);
        const users = new Map((await q('SELECT * FROM users WHERE id = ANY($1)', [[...new Set(winners.map((w) => w.user_id))]])).map((u) => [u.id, u]));
        res.json(winners.filter((w) => users.has(w.user_id)).map((w) => ({
            week: w.week,
            user: { ...shortUser(users.get(w.user_id)), isMe: w.user_id === me.id },
            workouts: w.workouts,
            volumeKg: w.volume_kg
        })));
    }));

    // MARK: Челленджи

    function challengeStatus(row, now = Date.now()) {
        if (now < new Date(row.starts_at).getTime()) return 'upcoming';
        return now < new Date(row.ends_at).getTime() ? 'active' : 'finished';
    }

    // Места по счёту: равный счёт — одно место.
    function ranked(members) {
        const joined = members.filter((m) => m.status === 'joined').sort((a, b) => b.score - a.score || a.username.localeCompare(b.username));
        let place = 0;
        let previous = null;
        joined.forEach((m, index) => {
            if (previous === null || m.score < previous) place = index + 1;
            previous = m.score;
            m.place = place;
        });
        return joined.concat(members.filter((m) => m.status === 'invited'));
    }

    async function challengeViews(rows, me) {
        if (rows.length === 0) return [];
        const members = await q(
            `SELECT m.*, u.id, u.username, u.avatar_id, u.bio, u.stats FROM challenge_members m JOIN users u ON u.id = m.user_id
             WHERE m.challenge_id = ANY($1) AND m.status IN ('joined', 'invited')`,
            [rows.map((r) => r.id)]
        );
        const owners = new Map((await q('SELECT * FROM users WHERE id = ANY($1)', [[...new Set(rows.map((r) => r.owner))]])).map((u) => [u.id, u]));
        const mine = new Map((await q('SELECT challenge_id, status FROM challenge_members WHERE user_id = $1 AND challenge_id = ANY($2)', [me.id, rows.map((r) => r.id)])).map((m) => [m.challenge_id, m.status]));
        return rows.map((row) => {
            const list = ranked(members.filter((m) => m.challenge_id === row.id).map((m) => ({
                user: { ...shortUser(m), isMe: m.user_id === me.id },
                username: m.username,
                status: m.status,
                score: m.score,
                detail: m.detail,
                place: null
            })));
            const status = challengeStatus(row);
            return {
                id: row.id,
                kind: row.kind,
                weeks: row.weeks,
                startsAt: new Date(row.starts_at).getTime(),
                endsAt: new Date(row.ends_at).getTime(),
                status,
                owner: owners.has(row.owner) ? shortUser(owners.get(row.owner)) : null,
                myStatus: mine.get(row.id) || null,
                members: list.map(({ username, ...member }) => member)
            };
        });
    }

    async function challengeFor(req, me) {
        const row = (await q(
            `SELECT c.* FROM challenges c JOIN challenge_members m ON m.challenge_id = c.id
             WHERE c.id = $1 AND m.user_id = $2`,
            [req.params.id, me.id]
        ))[0];
        if (!row) throw new HttpError(404, 'Челлендж не найден');
        return row;
    }

    async function pendingChallenges(me) {
        const [row] = await q(
            `SELECT count(*)::int AS count FROM challenge_members m JOIN challenges c ON c.id = m.challenge_id
             WHERE m.user_id = $1 AND m.status = 'invited' AND c.ends_at > now()`,
            [me.id]
        );
        return row.count;
    }

    // Новый челлендж: вид, на сколько недель и кого позвать. Начало присылает
    // приложение — полночь сегодняшнего дня по времени телефона.
    router.post('/challenges', wrap(async (req, res) => {
        const me = await requireViewer(req);
        messageLimit(me.id);
        const body = req.body || {};
        const kind = String(body.kind || '');
        if (!CHALLENGE_KINDS.has(kind)) throw new HttpError(400, 'Неизвестный вид челленджа');
        const weeks = Number(body.weeks);
        if (!Number.isInteger(weeks) || weeks < 1 || weeks > 4) throw new HttpError(400, 'Челлендж — от одной до четырёх недель');
        const names = [...new Set((Array.isArray(body.invite) ? body.invite : []).map((n) => text(n, 24).toLowerCase()).filter(Boolean))].slice(0, 20);
        // Звать можно только своих: на кого подписан или кто подписан на тебя.
        const invited = names.length ? await q(
            `SELECT * FROM users WHERE username = ANY($1) AND id <> $2
             AND (id IN (SELECT followee FROM follows WHERE follower = $2) OR id IN (SELECT follower FROM follows WHERE followee = $2))`,
            [names, me.id]
        ) : [];
        if (invited.length === 0) throw new HttpError(400, 'Позови хотя бы одного друга');
        const requested = Number(body.startsAt);
        const now = Date.now();
        const start = Number.isFinite(requested) && requested > now - 2 * DAY_MS && requested < now + 7 * DAY_MS ? requested : now;
        const id = newId();
        const [row] = await q(
            'INSERT INTO challenges (id, owner, kind, weeks, starts_at, ends_at) VALUES ($1, $2, $3, $4, $5, $6) RETURNING *',
            [id, me.id, kind, weeks, new Date(start), new Date(start + weeks * 7 * DAY_MS)]
        );
        await q("INSERT INTO challenge_members (challenge_id, user_id, status) VALUES ($1, $2, 'joined')", [id, me.id]);
        for (const user of invited) {
            await q('INSERT INTO challenge_members (challenge_id, user_id) VALUES ($1, $2) ON CONFLICT DO NOTHING', [id, user.id]);
        }
        res.status(201).json((await challengeViews([row], me))[0]);
    }));

    // Свои челленджи: идущие, приглашения и последние законченные.
    router.get('/challenges', wrap(async (req, res) => {
        const me = await requireViewer(req);
        const rows = await q(
            `SELECT c.* FROM challenges c JOIN challenge_members m ON m.challenge_id = c.id
             WHERE m.user_id = $1 AND (m.status = 'joined' OR (m.status = 'invited' AND c.ends_at > now()))
             AND c.ends_at > now() - interval '120 days'
             ORDER BY c.ends_at DESC LIMIT 40`,
            [me.id]
        );
        res.json(await challengeViews(rows, me));
    }));

    router.post('/challenges/:id/:action(join|decline|leave)', wrap(async (req, res) => {
        const me = await requireViewer(req);
        const row = await challengeFor(req, me);
        const action = req.params.action;
        if (action === 'join') {
            if (challengeStatus(row) === 'finished') throw new HttpError(409, 'Челлендж уже закончился');
            await q("UPDATE challenge_members SET status = 'joined', updated_at = now() WHERE challenge_id = $1 AND user_id = $2 AND status = 'invited'", [row.id, me.id]);
        } else if (action === 'decline') {
            await q("UPDATE challenge_members SET status = 'declined', updated_at = now() WHERE challenge_id = $1 AND user_id = $2 AND status = 'invited'", [row.id, me.id]);
        } else {
            if (challengeStatus(row) === 'finished') throw new HttpError(409, 'Челлендж уже закончился');
            await q("UPDATE challenge_members SET status = 'left', updated_at = now() WHERE challenge_id = $1 AND user_id = $2", [row.id, me.id]);
        }
        const fresh = (await q('SELECT * FROM challenges WHERE id = $1', [row.id]))[0];
        res.json((await challengeViews([fresh], me))[0]);
    }));

    // Счёт после тренировки: приложение присылает его сразу по всем своим
    // идущим челленджам. Закончившийся принимает счёт ещё сутки.
    router.put('/challenges/scores', wrap(async (req, res) => {
        const me = await requireViewer(req);
        const scores = Array.isArray(req.body && req.body.scores) ? req.body.scores.slice(0, 40) : [];
        let updated = 0;
        for (const item of scores) {
            const score = number(item && item.score, 0, 1e9);
            if (score === null || !item.id) continue;
            const rows = await q(
                `UPDATE challenge_members m SET score = $3, detail = $4, updated_at = now()
                 FROM challenges c WHERE c.id = m.challenge_id AND m.challenge_id = $1 AND m.user_id = $2
                 AND m.status = 'joined' AND c.starts_at <= now() AND c.ends_at > $5 RETURNING m.challenge_id`,
                [String(item.id), me.id, score, text(item.detail, 60), new Date(Date.now() - CHALLENGE_GRACE_MS)]
            );
            updated += rows.length;
        }
        res.json({ updated });
    }));

    router.use((error, req, res, next) => {
        if (error && error.type === 'entity.too.large') return res.status(413).json({ error: 'Файл слишком большой' });
        next(error);
    });

    return { router, init, pool: () => pool };
}

module.exports = { createSocial, USERNAME };
