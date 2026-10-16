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
const PAGE = 20;

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
CREATE INDEX IF NOT EXISTS messages_pair ON messages (LEAST(sender, recipient), GREATEST(sender, recipient), created_at DESC);
CREATE INDEX IF NOT EXISTS messages_unread ON messages (recipient) WHERE read_at IS NULL;
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

    async function userView(row, me) {
        const [counts] = await q(
            `SELECT
                (SELECT count(*) FROM posts WHERE user_id = $1)::int AS workouts,
                (SELECT count(*) FROM follows WHERE followee = $1)::int AS followers,
                (SELECT count(*) FROM follows WHERE follower = $1)::int AS following,
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
            workouts: counts.workouts,
            followers: counts.followers,
            following: counts.following,
            isFollowing: counts.is_following,
            isMe,
            createdAt: new Date(row.created_at).getTime()
        };
    }

    const shortUser = (row) => ({ id: row.id, username: row.username, avatar: row.avatar_id, bio: row.bio });

    async function userByName(username) {
        const rows = await q('SELECT * FROM users WHERE username = $1', [String(username || '').toLowerCase()]);
        if (!rows[0]) throw new HttpError(404, 'Пользователь не найден');
        return rows[0];
    }

    // MARK: Пост в ответе

    const POST_SELECT = `
        SELECT p.*, u.username, u.avatar_id,
            (SELECT count(*) FROM likes l WHERE l.post_id = p.id)::int AS like_count,
            (SELECT count(*) FROM comments c WHERE c.post_id = p.id)::int AS comment_count,
            EXISTS (SELECT 1 FROM likes l WHERE l.post_id = p.id AND l.user_id = $1) AS liked
        FROM posts p JOIN users u ON u.id = p.user_id`;

    const postView = (row) => ({
        id: row.id,
        author: { id: row.user_id, username: row.username, avatar: row.avatar_id },
        createdAt: new Date(row.created_at).getTime(),
        title: row.title,
        description: row.description,
        durationSeconds: row.duration_seconds,
        volumeKg: row.volume_kg,
        sets: row.sets,
        effort: row.effort,
        photos: row.photos,
        workout: row.workout,
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
        const inserted = await q(
            `INSERT INTO posts (id, user_id, client_id, title, description, duration_seconds, volume_kg, sets, effort, photos, workout)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
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
                JSON.stringify(jsonLimited(body.workout, 64_000, {}))
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

    const messageView = (row, me) => ({
        id: row.id,
        text: row.text,
        fromMe: row.sender === me.id,
        createdAt: new Date(row.created_at).getTime(),
        read: row.read_at !== null
    });

    async function unreadCount(me) {
        const [row] = await q('SELECT count(*)::int AS count FROM messages WHERE recipient = $1 AND read_at IS NULL', [me.id]);
        return row.count;
    }

    // Переписки: с кем, последнее сообщение и сколько не прочитано.
    router.get('/chats', wrap(async (req, res) => {
        const me = await requireViewer(req);
        const rows = await q(
            `SELECT DISTINCT ON (other) other, m.*
             FROM (SELECT *, CASE WHEN sender = $1 THEN recipient ELSE sender END AS other
                   FROM messages WHERE sender = $1 OR recipient = $1) m
             ORDER BY other, created_at DESC`,
            [me.id]
        );
        if (rows.length === 0) return res.json({ chats: [], unread: 0 });
        const others = rows.map((row) => row.other);
        const users = new Map((await q('SELECT * FROM users WHERE id = ANY($1)', [others])).map((u) => [u.id, u]));
        const unread = new Map((await q(
            `SELECT sender, count(*)::int AS count FROM messages
             WHERE recipient = $1 AND read_at IS NULL GROUP BY sender`,
            [me.id]
        )).map((row) => [row.sender, row.count]));
        const chats = rows
            .filter((row) => users.has(row.other))
            .sort((a, b) => b.created_at - a.created_at)
            .map((row) => ({
                user: shortUser(users.get(row.other)),
                last: messageView(row, me),
                unread: unread.get(row.other) || 0
            }));
        res.json({ chats, unread: chats.reduce((sum, chat) => sum + chat.unread, 0) });
    }));

    router.get('/chats/unread', wrap(async (req, res) => {
        const me = await requireViewer(req);
        res.json({ unread: await unreadCount(me) });
    }));

    // Сообщения переписки, новые первыми; открытая переписка помечается прочитанной.
    router.get('/chats/:username/messages', wrap(async (req, res) => {
        const me = await requireViewer(req);
        const other = await userByName(req.params.username);
        const before = Number(req.query.before);
        const after = Number(req.query.after);
        const limit = Math.min(100, Math.max(1, Number(req.query.limit) || 50));
        const params = [me.id, other.id, limit];
        // Время в ответе — в миллисекундах, а в базе — в микросекундах: сравниваем
        // по миллисекундам, иначе «новее, чем последнее» вернуло бы его же.
        let range = '';
        if (Number.isFinite(after) && after > 0) {
            params.push(new Date(after));
            range = `AND date_trunc('milliseconds', created_at) > $${params.length}`;
        } else if (Number.isFinite(before) && before > 0) {
            params.push(new Date(before));
            range = `AND date_trunc('milliseconds', created_at) < $${params.length}`;
        }
        const rows = await q(
            `SELECT * FROM messages
             WHERE ((sender = $1 AND recipient = $2) OR (sender = $2 AND recipient = $1)) ${range}
             ORDER BY created_at DESC LIMIT $3`,
            params
        );
        await q('UPDATE messages SET read_at = now() WHERE sender = $1 AND recipient = $2 AND read_at IS NULL', [other.id, me.id]);
        const messages = rows.map((row) => messageView(row, me));
        res.json({
            user: shortUser(other),
            messages,
            next: rows.length === limit ? messages[messages.length - 1].createdAt : null
        });
    }));

    router.post('/chats/:username/messages', wrap(async (req, res) => {
        const me = await requireViewer(req);
        messageLimit(me.id);
        const other = await userByName(req.params.username);
        if (other.id === me.id) throw new HttpError(400, 'Нельзя написать самому себе');
        const body = text(req.body && req.body.text, 2000);
        if (!body) throw new HttpError(400, 'Пустое сообщение');
        const clientId = text(req.body && req.body.clientId, 64) || null;
        const inserted = await q(
            `INSERT INTO messages (id, sender, recipient, client_id, text) VALUES ($1, $2, $3, $4, $5)
             ON CONFLICT (sender, client_id) DO NOTHING RETURNING *`,
            [newId(), me.id, other.id, clientId, body]
        );
        // Повтор отправки после обрыва связи возвращает уже созданное сообщение.
        const row = inserted[0] || (await q('SELECT * FROM messages WHERE sender = $1 AND client_id = $2', [me.id, clientId]))[0];
        res.status(inserted[0] ? 201 : 200).json(messageView(row, me));
    }));

    router.delete('/messages/:id', wrap(async (req, res) => {
        const me = await requireViewer(req);
        const rows = await q('DELETE FROM messages WHERE id = $1 AND sender = $2 RETURNING id', [req.params.id, me.id]);
        if (!rows[0]) throw new HttpError(404, 'Сообщение не найдено');
        res.json({ success: true });
    }));

    router.use((error, req, res, next) => {
        if (error && error.type === 'entity.too.large') return res.status(413).json({ error: 'Файл слишком большой' });
        next(error);
    });

    return { router, init, pool: () => pool };
}

module.exports = { createSocial, USERNAME };
