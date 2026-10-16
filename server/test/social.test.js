// Проверка ленты на настоящем Postgres. Без TEST_DATABASE_URL тесты пропускаются:
//   TEST_DATABASE_URL=postgres://user:pass@localhost/strain_test npm test
const test = require('node:test');
const assert = require('node:assert');
const express = require('express');
const { createSocial } = require('../social');

const url = process.env.TEST_DATABASE_URL;

test('лента, профили, подписки, фото и комментарии', { skip: !url && 'нет TEST_DATABASE_URL' }, async (t) => {
    const social = createSocial({ databaseUrl: url });
    await social.pool().query('DROP TABLE IF EXISTS comments, likes, posts, images, follows, users CASCADE');
    await social.init();

    const app = express();
    app.use(express.json({ limit: '2mb' }));
    app.use('/v2', social.router);
    const server = app.listen(0);
    const base = `http://127.0.0.1:${server.address().port}/v2`;
    t.after(async () => {
        server.close();
        await social.pool().end();
    });

    const call = async (method, path, { token, body, raw, type } = {}) => {
        const headers = {};
        if (token) headers.authorization = `Bearer ${token}`;
        if (body !== undefined) headers['content-type'] = 'application/json';
        if (raw) headers['content-type'] = type || 'image/jpeg';
        const response = await fetch(base + path, {
            method,
            headers,
            body: raw || (body !== undefined ? JSON.stringify(body) : undefined)
        });
        const contentType = response.headers.get('content-type') || '';
        const data = contentType.includes('json') ? await response.json() : Buffer.from(await response.arrayBuffer());
        return { status: response.status, data, headers: response.headers };
    };

    await t.test('регистрация проверяет юзернейм и не даёт занять чужой', async () => {
        assert.equal((await call('POST', '/register', { body: { username: 'ab' } })).status, 400);
        assert.equal((await call('POST', '/register', { body: { username: 'Плохо имя' } })).status, 400);
        assert.equal((await call('POST', '/register', { body: { username: 'admin' } })).status, 409);
        const ok = await call('POST', '/register', { body: { username: 'Kelw1n' } });
        assert.equal(ok.status, 201);
        assert.equal(ok.data.user.username, 'kelw1n');
        assert.match(ok.data.token, /^[a-f0-9]{64}$/);
        assert.equal((await call('POST', '/register', { body: { username: 'kelw1n' } })).status, 409);
    });

    const me = (await call('POST', '/register', { body: { username: 'tim' } })).data;
    const friend = (await call('POST', '/register', { body: { username: 'semobsessed' } })).data;

    await t.test('без токена профиль не меняется', async () => {
        assert.equal((await call('GET', '/me')).status, 401);
        assert.equal((await call('PATCH', '/me', { body: { bio: 'x' } })).status, 401);
        assert.equal((await call('GET', '/me', { token: 'f'.repeat(64) })).status, 401);
    });

    await t.test('правка профиля и скрытие роста и веса', async () => {
        const updated = await call('PATCH', '/me', {
            token: me.token,
            body: { bio: 'Техас, 3 раза в неделю', heightCm: 178, weightKg: 75, showBody: false, programs: [{ title: 'День 1' }], achievements: [{ id: 'path', level: 1 }] }
        });
        assert.equal(updated.status, 200);
        assert.equal(updated.data.bio, 'Техас, 3 раза в неделю');
        assert.equal(updated.data.heightCm, 178);
        const seen = await call('GET', '/users/tim', { token: friend.token });
        assert.equal(seen.data.heightCm, null, 'чужим рост не виден, если скрыт');
        assert.equal(seen.data.weightKg, null);
        assert.deepEqual(seen.data.programs, [{ title: 'День 1' }]);
        assert.equal(seen.data.isMe, false);
        assert.equal((await call('PATCH', '/me', { token: me.token, body: { username: 'semobsessed' } })).status, 409);
    });

    await t.test('подписка и счётчики', async () => {
        const followed = await call('POST', '/users/tim/follow', { token: friend.token });
        assert.equal(followed.data.followers, 1);
        assert.equal(followed.data.isFollowing, true);
        assert.equal((await call('POST', '/users/tim/follow', { token: friend.token })).data.followers, 1, 'повтор не удваивает');
        assert.equal((await call('POST', '/users/tim/follow', { token: me.token })).status, 400);
        const followers = await call('GET', '/users/tim/followers', { token: me.token });
        assert.deepEqual(followers.data.map((u) => u.username), ['semobsessed']);
        const search = await call('GET', '/users?q=semo', { token: me.token });
        assert.equal(search.data[0].username, 'semobsessed');
    });

    let photoId;
    await t.test('фото загружается только с токеном и отдаётся с кэшем', async () => {
        const jpeg = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 1, 2, 3, 0xff, 0xd9]);
        assert.equal((await call('POST', '/images', { raw: jpeg })).status, 401);
        const uploaded = await call('POST', '/images', { token: me.token, raw: jpeg });
        assert.equal(uploaded.status, 201);
        photoId = uploaded.data.id;
        const fetched = await call('GET', `/images/${photoId}`);
        assert.equal(fetched.status, 200);
        assert.deepEqual(fetched.data, jpeg);
        assert.match(fetched.headers.get('cache-control'), /immutable/);
    });

    let postId;
    await t.test('пост с тренировкой, повтор публикации не дублирует', async () => {
        const body = {
            clientId: 'workout-1',
            title: 'Верх',
            description: 'Нормально было',
            durationSeconds: 6480,
            volumeKg: 1470,
            sets: 18,
            effort: 7,
            photos: [photoId],
            workout: { exercises: [{ name: 'Жим лёжа', sets: [{ reps: 5, weight: 80, done: true }] }] }
        };
        const created = await call('POST', '/posts', { token: me.token, body });
        assert.equal(created.status, 201);
        postId = created.data.id;
        assert.equal(created.data.author.username, 'tim');
        assert.deepEqual(created.data.photos, [photoId]);
        assert.equal(created.data.workout.exercises[0].sets[0].weight, 80);
        const again = await call('POST', '/posts', { token: me.token, body });
        assert.equal(again.status, 200);
        assert.equal(again.data.id, postId);
        const stolen = await call('POST', '/posts', { token: friend.token, body: { ...body, clientId: 'x' } });
        assert.equal(stolen.status, 400, 'чужое фото к своему посту не прикрепить');
    });

    await t.test('лента: все и подписки', async () => {
        const all = await call('GET', '/feed');
        assert.equal(all.data.posts.length, 1);
        const mine = await call('GET', '/feed?scope=following', { token: me.token });
        assert.equal(mine.data.posts.length, 1, 'свои посты видны в подписках');
        const stranger = (await call('POST', '/register', { body: { username: 'stranger' } })).data;
        const theirs = await call('GET', '/feed?scope=following', { token: stranger.token });
        assert.equal(theirs.data.posts.length, 0);
        const profilePosts = await call('GET', '/users/tim/posts');
        assert.equal(profilePosts.data.posts.length, 1);
        assert.equal((await call('GET', '/users/tim')).data.workouts, 1);
    });

    await t.test('лайки и комментарии', async () => {
        const liked = await call('POST', `/posts/${postId}/like`, { token: friend.token });
        assert.equal(liked.data.likes, 1);
        assert.equal(liked.data.liked, true);
        assert.equal((await call('POST', `/posts/${postId}/like`, { token: friend.token })).data.likes, 1);
        const unliked = await call('DELETE', `/posts/${postId}/like`, { token: friend.token });
        assert.equal(unliked.data.likes, 0);

        assert.equal((await call('POST', `/posts/${postId}/comments`, { token: friend.token, body: { text: '  ' } })).status, 400);
        const comment = await call('POST', `/posts/${postId}/comments`, { token: friend.token, body: { text: 'Мощно 💪' } });
        assert.equal(comment.status, 201);
        assert.equal(comment.data.author.username, 'semobsessed');
        const list = await call('GET', `/posts/${postId}/comments`);
        assert.deepEqual(list.data.map((c) => c.text), ['Мощно 💪']);
        assert.equal((await call('GET', `/posts/${postId}`)).data.comments, 1);

        const stranger = (await call('GET', '/users?q=stranger')).data[0];
        assert.ok(stranger);
        // Хозяин поста удаляет чужой комментарий под своим постом.
        assert.equal((await call('DELETE', `/comments/${comment.data.id}`, { token: me.token })).status, 200);
        assert.equal((await call('GET', `/posts/${postId}/comments`)).data.length, 0);
    });

    await t.test('удаление поста убирает и его фото', async () => {
        assert.equal((await call('DELETE', `/posts/${postId}`, { token: friend.token })).status, 404, 'чужой пост не удалить');
        assert.equal((await call('DELETE', `/posts/${postId}`, { token: me.token })).status, 200);
        assert.equal((await call('GET', `/posts/${postId}`)).status, 404);
        assert.equal((await call('GET', `/images/${photoId}`)).status, 404);
    });

    await t.test('удаление профиля', async () => {
        assert.equal((await call('DELETE', '/me', { token: friend.token })).status, 200);
        assert.equal((await call('GET', '/users/semobsessed')).status, 404);
        assert.equal((await call('GET', '/users/tim')).data.followers, 0);
    });
});

test('без базы раздел честно отвечает 503', async (t) => {
    const social = createSocial({});
    const app = express();
    app.use('/v2', social.router);
    const server = app.listen(0);
    t.after(() => server.close());
    const response = await fetch(`http://127.0.0.1:${server.address().port}/v2/feed`);
    assert.equal(response.status, 503);
});
