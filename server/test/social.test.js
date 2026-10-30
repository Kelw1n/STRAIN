// Проверка ленты на настоящем Postgres. Без TEST_DATABASE_URL тесты пропускаются:
//   TEST_DATABASE_URL=postgres://user:pass@localhost/strain_test npm test
const test = require('node:test');
const assert = require('node:assert');
const express = require('express');
const { createSocial } = require('../social');

const url = process.env.TEST_DATABASE_URL;

// Неделя по ISO, как её считают приложения: «2026-W43».
const isoWeek = (date) => {
    const day = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
    day.setUTCDate(day.getUTCDate() + 4 - (day.getUTCDay() || 7));
    const week = Math.ceil(((day - new Date(Date.UTC(day.getUTCFullYear(), 0, 1))) / 864e5 + 1) / 7);
    return `${day.getUTCFullYear()}-W${String(week).padStart(2, '0')}`;
};

test('лента, профили, подписки, фото и комментарии', { skip: !url && 'нет TEST_DATABASE_URL' }, async (t) => {
    const social = createSocial({ databaseUrl: url });
    await social.pool().query('DROP TABLE IF EXISTS challenge_members, challenges, weekly_stats, messages, together, comments, likes, posts, images, voices, follows, users CASCADE');
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

    await t.test('ответы под комментарием собираются в одну ветку', async () => {
        const root = (await call('POST', `/posts/${postId}/comments`, { token: friend.token, body: { text: 'Какой вес на присед?' } })).data;
        assert.equal(root.replyTo, null);
        const answer = await call('POST', `/posts/${postId}/comments`, { token: me.token, body: { text: '140 на 5', replyTo: root.id } });
        assert.equal(answer.status, 201);
        assert.equal(answer.data.replyTo, root.id);
        assert.equal(answer.data.replyToUsername, 'semobsessed');
        // Ответ на ответ встаёт в ту же ветку, но адресован автору ответа.
        const again = (await call('POST', `/posts/${postId}/comments`, { token: friend.token, body: { text: 'Красава', replyTo: answer.data.id } })).data;
        assert.equal(again.replyTo, root.id);
        assert.equal(again.replyToUsername, 'tim');
        assert.equal((await call('POST', `/posts/${postId}/comments`, { token: me.token, body: { text: 'x', replyTo: 'f'.repeat(24) } })).status, 404);
        const list = (await call('GET', `/posts/${postId}/comments`)).data;
        assert.deepEqual(list.map((c) => c.text), ['Какой вес на присед?', '140 на 5', 'Красава']);
        assert.equal((await call('GET', `/posts/${postId}`)).data.comments, 3);
        // Удалили корень — уходит вся ветка.
        assert.equal((await call('DELETE', `/comments/${root.id}`, { token: friend.token })).status, 200);
        assert.equal((await call('GET', `/posts/${postId}/comments`)).data.length, 0);
    });

    await t.test('личные сообщения', async () => {
        assert.equal((await call('GET', '/chats')).status, 401);
        assert.equal((await call('POST', '/chats/tim/messages', { token: me.token, body: { text: 'я' } })).status, 400, 'себе нельзя');
        assert.equal((await call('POST', '/chats/semobsessed/messages', { token: me.token, body: { text: '   ' } })).status, 400);
        const first = await call('POST', '/chats/semobsessed/messages', { token: me.token, body: { text: 'Го в зал?', clientId: 'm1' } });
        assert.equal(first.status, 201);
        assert.equal(first.data.fromMe, true);
        const repeat = await call('POST', '/chats/semobsessed/messages', { token: me.token, body: { text: 'Го в зал?', clientId: 'm1' } });
        assert.equal(repeat.status, 200, 'повтор отправки не дублирует');
        assert.equal(repeat.data.id, first.data.id);
        await call('POST', '/chats/tim/messages', { token: friend.token, body: { text: 'Давай в 7' } });
        await call('POST', '/chats/tim/messages', { token: friend.token, body: { text: 'Возьми пояс' } });

        assert.equal((await call('GET', '/chats/unread', { token: me.token })).data.unread, 2);
        const chats = (await call('GET', '/chats', { token: me.token })).data;
        assert.equal(chats.unread, 2);
        assert.equal(chats.chats.length, 1);
        assert.equal(chats.chats[0].user.username, 'semobsessed');
        assert.equal(chats.chats[0].last.text, 'Возьми пояс');
        assert.equal(chats.chats[0].last.fromMe, false);

        const thread = (await call('GET', '/chats/semobsessed/messages', { token: me.token })).data;
        assert.deepEqual(thread.messages.map((m) => m.text), ['Возьми пояс', 'Давай в 7', 'Го в зал?'], 'новые первыми');
        assert.equal((await call('GET', '/chats/unread', { token: me.token })).data.unread, 0, 'открыли — прочитано');
        const newer = (await call('GET', `/chats/semobsessed/messages?after=${thread.messages[1].createdAt}`, { token: me.token })).data;
        assert.deepEqual(newer.messages.map((m) => m.text), ['Возьми пояс']);
        // Свои сообщения не помечаются прочитанными, пока их не открыл собеседник.
        assert.equal((await call('GET', '/chats/unread', { token: friend.token })).data.unread, 1);

        // Фото в сообщении: только своё.
        const photo = (await call('POST', '/images', { token: me.token, raw: Buffer.from([0xff, 0xd8, 0xff, 0xd9]) })).data.id;
        const foreign = (await call('POST', '/images', { token: friend.token, raw: Buffer.from([0xff, 0xd8, 0xff, 0xd9]) })).data.id;
        assert.equal((await call('POST', '/chats/semobsessed/messages', { token: me.token, body: { image: foreign } })).status, 400);
        const withPhoto = await call('POST', '/chats/semobsessed/messages', { token: me.token, body: { image: photo } });
        assert.equal(withPhoto.status, 201);
        assert.equal(withPhoto.data.image, photo);
        assert.equal(withPhoto.data.text, '');
        assert.equal((await call('GET', '/chats', { token: friend.token })).data.chats[0].last.image, photo);
        assert.equal((await call('DELETE', `/messages/${withPhoto.data.id}`, { token: me.token })).status, 200);
        assert.equal((await call('GET', `/images/${photo}`)).status, 404, 'с сообщением удаляется и фото');

        // Голосовое: только своё, с длительностью и волной; удаляется вместе с сообщением.
        const sound = Buffer.from('....ftypM4A voice', 'latin1');
        assert.equal((await call('POST', '/voices?durationMs=4200&waveform=0f3a', { raw: sound, type: 'audio/mp4' })).status, 401);
        assert.equal((await call('POST', '/voices?durationMs=120', { token: me.token, raw: sound, type: 'audio/mp4' })).status, 400, 'слишком короткое');
        assert.equal((await call('POST', '/voices?durationMs=90000', { token: me.token, raw: sound, type: 'audio/mp4' })).status, 400, 'дольше минуты');
        const voice = (await call('POST', '/voices?durationMs=4200&waveform=0f3a', { token: me.token, raw: sound, type: 'audio/mp4' })).data;
        assert.equal(voice.durationMs, 4200);
        const foreignVoice = (await call('POST', '/voices?durationMs=1000', { token: friend.token, raw: sound, type: 'audio/mp4' })).data.id;
        assert.equal((await call('POST', '/chats/semobsessed/messages', { token: me.token, body: { voice: foreignVoice } })).status, 400);
        const spoken = await call('POST', '/chats/semobsessed/messages', { token: me.token, body: { voice: voice.id } });
        assert.equal(spoken.status, 201);
        assert.deepEqual(spoken.data.voice, { id: voice.id, durationMs: 4200, waveform: '0f3a' });
        const back = await call('GET', `/voices/${voice.id}`);
        assert.equal(back.headers.get('content-type'), 'audio/mp4');
        assert.ok(Buffer.compare(back.data, sound) === 0, 'звук приходит как был');
        const answer = await call('POST', '/chats/tim/messages', { token: friend.token, body: { text: 'Услышал', replyTo: spoken.data.id } });
        assert.equal(answer.data.replyTo.voice, true, 'в цитате видно, что это голосовое');
        assert.equal((await call('GET', '/chats', { token: friend.token })).data.chats[0].last.text, 'Услышал');
        assert.equal((await call('DELETE', `/messages/${spoken.data.id}`, { token: me.token })).status, 200);
        assert.equal((await call('GET', `/voices/${voice.id}`)).status, 404, 'с сообщением удаляется и звук');
        await call('DELETE', `/messages/${answer.data.id}`, { token: friend.token });

        assert.equal((await call('DELETE', `/messages/${thread.messages[0].id}`, { token: me.token })).status, 404, 'чужое не удалить');
        assert.equal((await call('DELETE', `/messages/${first.data.id}`, { token: me.token })).status, 200);
        assert.equal((await call('GET', '/chats/tim/messages', { token: friend.token })).data.messages.length, 2);
    });

    await t.test('ответ на сообщение, реакции, «печатает» и обновления переписки', async () => {
        const question = (await call('POST', '/chats/semobsessed/messages', { token: me.token, body: { text: 'Сколько жмёшь?' } })).data;
        const start = (await call('GET', '/chats/tim/messages', { token: friend.token })).data.serverTime;
        assert.ok(start > 0);
        const answer = await call('POST', '/chats/tim/messages', { token: friend.token, body: { text: '120', replyTo: question.id } });
        assert.equal(answer.status, 201);
        assert.equal(answer.data.replyTo.id, question.id);
        assert.equal(answer.data.replyTo.text, 'Сколько жмёшь?');
        assert.equal(answer.data.replyTo.fromMe, false);
        assert.equal((await call('POST', '/chats/tim/messages', { token: friend.token, body: { text: 'x', replyTo: 'f'.repeat(24) } })).status, 404);

        assert.equal((await call('POST', `/messages/${answer.data.id}/react`, { token: me.token, body: { emoji: '🍕' } })).status, 400);
        const reacted = await call('POST', `/messages/${answer.data.id}/react`, { token: me.token, body: { emoji: '🔥' } });
        assert.deepEqual(reacted.data.reactions, [{ emoji: '🔥', count: 1, mine: true }]);
        const again = await call('POST', `/messages/${answer.data.id}/react`, { token: me.token, body: { emoji: '🔥' } });
        assert.deepEqual(again.data.reactions, [], 'повтор снимает реакцию');
        await call('POST', `/messages/${answer.data.id}/react`, { token: me.token, body: { emoji: '💪' } });

        // Собеседник видит и новое сообщение, и реакцию на своё.
        const changes = (await call('GET', `/chats/tim/messages?since=${start}`, { token: friend.token })).data;
        const mine = changes.messages.find((m) => m.id === answer.data.id);
        assert.deepEqual(mine.reactions, [{ emoji: '💪', count: 1, mine: false }]);

        assert.equal((await call('POST', '/chats/tim/typing', { token: friend.token })).status, 200);
        const view = (await call('GET', '/chats/semobsessed/messages', { token: me.token })).data;
        assert.equal(view.typing, true);
        assert.ok(view.lastSeen > 0);

        // Удалённое приходит собеседнику как удалённое.
        const before = view.serverTime;
        await call('DELETE', `/messages/${question.id}`, { token: me.token });
        const gone = (await call('GET', `/chats/tim/messages?since=${before}`, { token: friend.token })).data;
        assert.equal(gone.messages.find((m) => m.id === question.id).deleted, true);
        assert.equal((await call('GET', '/chats/tim/messages', { token: friend.token })).data.messages.some((m) => m.id === question.id), false);
    });

    await t.test('правка, пересылка, «прослушано» и тренировка в переписке', async () => {
        // Свой clientId приходит обратно — по нему приложение узнаёт сообщение из очереди.
        const draft = (await call('POST', '/chats/semobsessed/messages', { token: me.token, body: { text: 'Жму 100', clientId: 'q1' } })).data;
        assert.equal(draft.clientId, 'q1');
        assert.equal(draft.edited, false);
        const seenByFriend = (await call('GET', '/chats/tim/messages', { token: friend.token })).data;
        assert.equal(seenByFriend.messages.find((m) => m.id === draft.id).clientId, null, 'чужой clientId не виден');
        const reply = (await call('POST', '/chats/tim/messages', { token: friend.token, body: { text: 'Ого', replyTo: draft.id } })).data;

        const since = seenByFriend.serverTime;
        assert.equal((await call('PATCH', `/messages/${draft.id}`, { token: friend.token, body: { text: 'x' } })).status, 404, 'чужое не правится');
        assert.equal((await call('PATCH', `/messages/${draft.id}`, { token: me.token, body: { text: '  ' } })).status, 400, 'пустым не сделать');
        const edited = await call('PATCH', `/messages/${draft.id}`, { token: me.token, body: { text: 'Жму 105' } });
        assert.equal(edited.status, 200);
        assert.equal(edited.data.text, 'Жму 105');
        assert.equal(edited.data.edited, true);
        const changes = (await call('GET', `/chats/tim/messages?since=${since}`, { token: friend.token })).data.messages;
        assert.equal(changes.find((m) => m.id === draft.id).text, 'Жму 105', 'собеседник видит правку');
        assert.equal(changes.find((m) => m.id === reply.id).replyTo.text, 'Жму 105', 'и цитата в ответе обновилась');

        // Пересылка фото: копия — у другого человека, «от кого» — автор первого сообщения.
        const third = (await call('POST', '/register', { body: { username: 'thirdguy' } })).data;
        const photo = (await call('POST', '/images', { token: me.token, raw: Buffer.from([0xff, 0xd8, 0xff, 0xd9]) })).data.id;
        const original = (await call('POST', '/chats/semobsessed/messages', { token: me.token, body: { text: 'Форма', image: photo } })).data;
        assert.equal((await call('POST', '/chats/semobsessed/messages', { token: third.token, body: { forward: original.id } })).status, 404, 'чужую переписку не переслать');
        const forwarded = await call('POST', '/chats/thirdguy/messages', { token: friend.token, body: { forward: original.id, clientId: 'f1' } });
        assert.equal(forwarded.status, 201);
        assert.equal(forwarded.data.text, 'Форма');
        assert.equal(forwarded.data.image, photo);
        assert.deepEqual(forwarded.data.forwarded, { username: 'tim' });
        const again = await call('POST', '/chats/tim/messages', { token: third.token, body: { forward: forwarded.data.id } });
        assert.deepEqual(again.data.forwarded, { username: 'tim' }, 'пересланное дальше — всё ещё от автора');
        assert.equal((await call('PATCH', `/messages/${forwarded.data.id}`, { token: friend.token, body: { text: 'y' } })).status, 400, 'пересланное не правится');
        await call('DELETE', `/messages/${original.id}`, { token: me.token });
        assert.equal((await call('GET', `/images/${photo}`)).status, 200, 'фото живо, пока его пересланная копия на месте');
        await call('DELETE', `/messages/${forwarded.data.id}`, { token: friend.token });
        await call('DELETE', `/messages/${again.data.id}`, { token: third.token });
        assert.equal((await call('GET', `/images/${photo}`)).status, 404, 'последняя копия ушла — и фото удалено');

        // Голосовое прослушано: отмечает только получатель.
        const sound = Buffer.from('....ftypM4A heard', 'latin1');
        const voice = (await call('POST', '/voices?durationMs=2000', { token: me.token, raw: sound, type: 'audio/mp4' })).data;
        const spoken = (await call('POST', '/chats/semobsessed/messages', { token: me.token, body: { voice: voice.id } })).data;
        assert.equal(spoken.heard, false);
        await call('POST', `/messages/${spoken.id}/heard`, { token: me.token });
        assert.equal((await call('GET', '/chats/semobsessed/messages', { token: me.token })).data.messages.find((m) => m.id === spoken.id).heard, false, 'сам себе не слушатель');
        await call('POST', `/messages/${spoken.id}/heard`, { token: friend.token });
        assert.equal((await call('GET', '/chats/semobsessed/messages', { token: me.token })).data.messages.find((m) => m.id === spoken.id).heard, true);
        assert.equal((await call('PATCH', `/messages/${spoken.id}`, { token: me.token, body: { text: 'z' } })).status, 400, 'голосовое не правится');

        // Тренировка карточкой: без текста и фото — тоже сообщение.
        const card = { title: 'День 1', durationSeconds: 3600, volumeKg: 5200, sets: 15, workout: { exercises: [{ name: 'Присед', sets: [{ reps: 5, weight: 100, record: true }] }] } };
        const sent = await call('POST', '/chats/semobsessed/messages', { token: me.token, body: { workout: card } });
        assert.equal(sent.status, 201);
        assert.deepEqual(sent.data.workout, card);
        assert.equal((await call('POST', '/chats/semobsessed/messages', { token: me.token, body: { workout: { huge: 'x'.repeat(40_000) } } })).status, 413);
        const shared = await call('POST', '/chats/thirdguy/messages', { token: friend.token, body: { forward: sent.data.id } });
        assert.deepEqual(shared.data.workout, card, 'тренировку можно переслать дальше');
    });

    await t.test('тренируется сейчас и рейтинг недели', async () => {
        assert.equal((await call('PUT', '/me/live', { token: me.token, body: { title: 'День 1', exercise: 'Присед', done: 3, total: 5 } })).status, 200);
        const live = (await call('GET', '/live', { token: friend.token })).data;
        assert.equal(live.length, 1, 'semobsessed подписан на tim');
        assert.equal(live[0].user.username, 'tim');
        assert.equal(live[0].live.exercise, 'Присед');
        assert.equal(live[0].live.done, 3);
        assert.equal((await call('GET', '/users/tim', { token: friend.token })).data.live.total, 5);
        assert.equal((await call('GET', '/live', { token: me.token })).data.length, 0, 'на кого не подписан — не видно');
        await call('DELETE', '/me/live', { token: me.token });
        assert.equal((await call('GET', '/live', { token: friend.token })).data.length, 0);

        await call('PATCH', '/me', { token: friend.token, body: { stats: { week: '2026-W42', workouts: 3, volumeKg: 12000 } } });
        await call('PATCH', '/me', { token: me.token, body: { stats: { week: '2026-W42', workouts: 2, volumeKg: 9000 } } });
        const board = (await call('GET', '/leaderboard', { token: friend.token })).data;
        assert.deepEqual(board.map((r) => r.user.username).sort(), ['semobsessed', 'tim']);
        assert.equal(board.find((r) => r.user.isMe).stats.workouts, 3);

        // Уровень из сводки — рядом с ником в профиле, рейтинге и ленте.
        await call('PATCH', '/me', { token: me.token, body: { stats: { week: '2026-W42', workouts: 2, level: 7, xp: 2200 } } });
        const records = [{ name: 'Жим лёжа', weight: 100, reps: 5, estimate: 116.5, date: 1761500000000, lift: 'bench' }];
        await call('PATCH', '/me', { token: me.token, body: { records } });
        assert.deepEqual((await call('GET', '/users/tim', { token: friend.token })).data.records, records, 'рекорды видят друзья');
        await call('PATCH', '/me', { token: me.token, body: { records: { name: 'не список' } } });
        assert.deepEqual((await call('GET', '/users/tim', { token: friend.token })).data.records, [], 'не список — пусто');
        await call('PATCH', '/me', { token: me.token, body: { heatmap: { end: '2026-10-28', days: '0012034' } } });
        assert.deepEqual((await call('GET', '/users/tim', { token: friend.token })).data.heatmap, { end: '2026-10-28', days: '0012034' });
        await call('PATCH', '/me', { token: me.token, body: { heatmap: { end: 'вчера', days: '9' } } });
        assert.equal((await call('GET', '/users/tim', { token: friend.token })).data.heatmap, null, 'мусор вместо карты не показываем');
        const leveledUser = (await call('GET', '/users/tim', { token: friend.token })).data;
        assert.equal(leveledUser.level, 7);
        assert.equal(leveledUser.xp, 2200, 'опыт — для шкалы до следующего уровня');
        const ranked = (await call('GET', '/leaderboard', { token: friend.token })).data;
        assert.equal(ranked.find((r) => r.user.username === 'tim').user.level, 7);
        assert.equal(ranked.find((r) => r.user.username === 'semobsessed').user.level, null, 'старое приложение уровень не шлёт');
        const leveled = (await call('POST', '/posts', { token: me.token, body: { clientId: 'lvl', title: 'С уровнем' } })).data;
        assert.equal(leveled.author.level, 7);
        await call('PATCH', '/me', { token: me.token, body: { stats: { level: 'много' } } });
        const junk = (await call('GET', '/users/tim', { token: friend.token })).data;
        assert.equal(junk.level, null, 'мусор вместо уровня не показываем');
        assert.equal(junk.xp, null);
    });

    await t.test('корона победителю прошлой недели и история побед', async () => {
        const last = isoWeek(new Date(Date.now() - 7 * 864e5));
        // Прошлые недели легли в историю, пока шли: приложения шлют сводку только за идущую.
        const remember = (user, week, workouts, volumeKg) => social.pool().query(
            `INSERT INTO weekly_stats (user_id, week, workouts, volume_kg) VALUES ($1, $2, $3, $4)
             ON CONFLICT (user_id, week) DO UPDATE SET workouts = $3, volume_kg = $4`,
            [user.user.id, week, workouts, volumeKg]
        );
        await remember(friend, '2025-W01', 3, 100);
        await remember(friend, last, 4, 10000);
        await remember(me, last, 4, 12000);
        await call('PATCH', '/me', { token: me.token, body: { stats: { week: isoWeek(new Date()), workouts: 9, volumeKg: 99999 } } });
        const current = await social.pool().query('SELECT workouts FROM weekly_stats WHERE user_id = $1 AND week = $2', [me.user.id, isoWeek(new Date())]);
        assert.equal(current.rows[0].workouts, 9, 'идущая неделя пишется в историю');
        // Задним числом историю не переписать: ни прошлую неделю с уже выданной короной, ни старые.
        assert.equal((await call('PATCH', '/me', { token: friend.token, body: { stats: { week: last, workouts: 50, volumeKg: 1 } } })).status, 200);
        await call('PATCH', '/me', { token: friend.token, body: { stats: { week: '2025-W02', workouts: 50, volumeKg: 1 } } });
        const board = (await call('GET', '/leaderboard', { token: friend.token })).data;
        const tim = board.find((r) => r.user.username === 'tim');
        const sem = board.find((r) => r.user.username === 'semobsessed');
        assert.equal(tim.crown, true, 'тренировок поровну — решает тоннаж');
        assert.equal(sem.crown, false);
        assert.ok(sem.wins >= 1, 'старую неделю выиграл semobsessed');
        assert.ok(tim.wins >= 1);
        const history = (await call('GET', '/leaderboard/history', { token: friend.token })).data;
        assert.equal(history[0].week, last, 'идущая неделя ещё не выиграна');
        assert.equal(history[0].user.username, 'tim');
        assert.equal(history.at(-1).week, '2025-W01');
        assert.equal(history.some((w) => w.week === '2025-W02'), false, 'старую неделю не дописать');
    });

    await t.test('данные для чужих приложений приводятся к нужным типам', async () => {
        // Android декодирует строго: одно поле не того типа — и вся лента не открывается.
        const junk = await call('POST', '/posts', {
            token: me.token,
            body: {
                clientId: 'junk-1',
                title: 'Мусор',
                workout: {
                    program: 7,
                    extra: { keep: true },
                    exercises: [{ name: 5, sets: [{ reps: '5', weight: 'тяжело', record: 1 }, 'x', { reps: 5.4, weight: 80 }] }, 'мусор', null]
                }
            }
        });
        assert.equal(junk.status, 201);
        assert.deepEqual(junk.data.workout, { program: '7', extra: { keep: true }, exercises: [{ name: '5', sets: [{}, { reps: 5, weight: 80 }] }] });
        const stringy = await call('POST', '/posts', { token: me.token, body: { clientId: 'junk-2', workout: 'строка' } });
        assert.deepEqual(stringy.data.workout, {});
        // Мусор, который уже лежит в базе, тоже не уходит в ленту.
        await social.pool().query(`UPDATE posts SET workout = '{"exercises": [{"name": "Присед", "sets": [{"reps": "много"}]}]}' WHERE id = $1`, [stringy.data.id]);
        const feed = (await call('GET', '/feed')).data.posts;
        assert.deepEqual(feed.find((p) => p.id === stringy.data.id).workout, { exercises: [{ name: 'Присед', sets: [{}] }] });

        // Профиль: программа, медаль и рекорд без обязательного поля выпадают, числа — числа.
        await call('PATCH', '/me', {
            token: me.token,
            body: {
                programs: [{ title: 'День', groups: ['Ноги', 3, {}], exercises: [{ name: 'Присед', sets: 5, reps: 5, load: '100 кг' }, { name: 'Жим' }] }, 'x'],
                achievements: [{ id: 'path', level: 2 }, { id: 3 }, null],
                records: [{ name: 'Присед', weight: 150, reps: 3 }, { name: 'Жим' }, 'x', { name: 'Тяга', weight: '200', reps: 1 }]
            }
        });
        const profile = (await call('GET', '/users/tim', { token: friend.token })).data;
        assert.deepEqual(profile.programs, [{ title: 'День', groups: ['Ноги', '3'], exercises: [{ name: 'Присед', sets: 5, reps: '5', load: '100 кг' }] }]);
        assert.deepEqual(profile.achievements, [{ id: 'path', level: 2 }]);
        assert.deepEqual(profile.records, [{ name: 'Присед', weight: 150, reps: 3 }]);

        // Сводка недели: мусор в одном профиле не ломает рейтинг всем, кто на него подписан.
        await social.pool().query(`UPDATE users SET stats = '{"week": 5, "workouts": "много", "volumeKg": null, "level": 3.6, "records": 1e12}' WHERE username = 'tim'`);
        const board = (await call('GET', '/leaderboard', { token: friend.token })).data;
        assert.deepEqual(board.find((r) => r.user.username === 'tim').stats, { week: '5', level: 4, records: 100000 });
        await call('PATCH', '/me', { token: me.token, body: { stats: [1, 2] } });
        assert.deepEqual((await call('GET', '/leaderboard', { token: friend.token })).data.find((r) => r.user.username === 'tim').stats, {});

        // Тренировка карточкой в переписке.
        const card = await call('POST', '/chats/semobsessed/messages', { token: me.token, body: { workout: { title: 5, sets: 'много', workout: { exercises: 'x' } } } });
        assert.equal(card.status, 201);
        assert.deepEqual(card.data.workout, { title: '5', workout: {} });

        for (const post of [junk.data, stringy.data]) await call('DELETE', `/posts/${post.id}`, { token: me.token });
    });

    await t.test('кривые параметры и дробные числа — не 500', async () => {
        for (const path of ['/feed?limit=1.5', '/feed?before=1e20', '/feed?before=abc&limit=-3', '/users/tim/posts?before=99999999999999999']) {
            assert.equal((await call('GET', path)).status, 200, path);
        }
        for (const query of ['since=1e20', 'after=9e15', 'before=1e300', 'limit=2.5']) {
            assert.equal((await call('GET', `/chats/semobsessed/messages?${query}`, { token: me.token })).status, 200, query);
        }
        const tall = await call('PATCH', '/me', { token: me.token, body: { heightCm: 178.6 } });
        assert.equal(tall.status, 200);
        assert.equal(tall.data.heightCm, 179);
        const week = isoWeek(new Date());
        assert.equal((await call('PATCH', '/me', { token: me.token, body: { stats: { week, workouts: 2.5, volumeKg: 100 } } })).status, 200);
    });

    await t.test('аватарка — только своё фото, удаление поста её не трогает', async () => {
        const jpeg = Buffer.from([0xff, 0xd8, 0xff, 0xd9]);
        const foreign = (await call('POST', '/images', { token: friend.token, raw: jpeg })).data.id;
        assert.equal((await call('PATCH', '/me', { token: me.token, body: { avatar: foreign } })).data.avatar, null, 'чужое фото на аватарку не поставить');
        const own = (await call('POST', '/images', { token: me.token, raw: jpeg, type: 'image/jpeg; charset=binary' })).data.id;
        assert.equal((await call('PATCH', '/me', { token: me.token, body: { avatar: own } })).data.avatar, own);
        const post = (await call('POST', '/posts', { token: me.token, body: { clientId: 'avatar-post', photos: [own] } })).data;
        assert.equal((await call('DELETE', `/posts/${post.id}`, { token: me.token })).status, 200);
        const kept = await call('GET', `/images/${own}`);
        assert.equal(kept.status, 200, 'фото с аватарки живо после удаления поста');
        assert.equal(kept.headers.get('content-type'), 'image/jpeg');
        assert.equal(kept.headers.get('x-content-type-options'), 'nosniff');
        await call('PATCH', '/me', { token: me.token, body: { avatar: null } });
    });

    await t.test('реакции двоих в одну секунду не теряются', async () => {
        for (let attempt = 0; attempt < 5; attempt += 1) {
            const message = (await call('POST', '/chats/semobsessed/messages', { token: me.token, body: { text: `Реагируйте ${attempt}` } })).data;
            await Promise.all([
                call('POST', `/messages/${message.id}/react`, { token: me.token, body: { emoji: '🔥' } }),
                call('POST', `/messages/${message.id}/react`, { token: friend.token, body: { emoji: '💪' } })
            ]);
            const seen = (await call('GET', '/chats/tim/messages', { token: friend.token })).data.messages.find((m) => m.id === message.id);
            assert.deepEqual(seen.reactions.map((r) => r.emoji).sort(), ['💪', '🔥'].sort());
        }
    });

    await t.test('челленджи: приглашение, счёт, места и победа в профиле', async () => {
        const stranger = (await call('POST', '/register', { body: { username: 'stranger.one' } })).data;
        assert.equal((await call('POST', '/challenges', { token: me.token, body: { kind: 'workouts', weeks: 1, invite: ['stranger.one'] } })).status, 400, 'незнакомого не позвать');
        assert.equal((await call('POST', '/challenges', { token: me.token, body: { kind: 'jump', weeks: 2, invite: ['semobsessed'] } })).status, 400);
        assert.equal((await call('POST', '/challenges', { token: me.token, body: { kind: 'workouts', weeks: 2, invite: [] } })).status, 400);
        assert.equal((await call('POST', '/challenges', { token: me.token, body: { kind: 'workouts', weeks: 9, invite: ['semobsessed'] } })).status, 400);
        const created = await call('POST', '/challenges', {
            token: me.token,
            body: { kind: 'workouts', weeks: 2, invite: ['SemObsessed', 'nobody.here', 'tim'], startsAt: Date.now() - 1000 }
        });
        assert.equal(created.status, 201);
        const id = created.data.id;
        assert.equal(created.data.status, 'active');
        assert.equal(created.data.endsAt - created.data.startsAt, 14 * 864e5);
        assert.deepEqual(created.data.members.map((m) => [m.user.username, m.status]), [['tim', 'joined'], ['semobsessed', 'invited']]);
        assert.equal((await call('GET', '/badges', { token: friend.token })).data.challenges, 1);
        assert.equal((await call('POST', `/challenges/${id}/join`, { token: stranger.token })).status, 404, 'чужой челлендж не виден');

        const early = await call('PUT', '/challenges/scores', { token: friend.token, body: { scores: [{ id, score: 9 }] } });
        assert.equal(early.data.updated, 0, 'до вступления счёт не принимается');
        assert.equal((await call('POST', `/challenges/${id}/join`, { token: friend.token })).data.myStatus, 'joined');
        assert.equal((await call('GET', '/badges', { token: friend.token })).data.challenges, 0);
        // Один челлендж дважды в одной отправке — засчитывается последний счёт.
        assert.equal((await call('PUT', '/challenges/scores', { token: friend.token, body: { scores: [{ id, score: 1 }, { id, score: 3, detail: '3 тренировки' }] } })).data.updated, 1);
        await call('PUT', '/challenges/scores', { token: me.token, body: { scores: [{ id, score: 2, detail: '2 тренировки' }, { id: 'nope', score: 5 }] } });
        const live = (await call('GET', '/challenges', { token: me.token })).data.find((c) => c.id === id);
        assert.deepEqual(live.members.map((m) => [m.user.username, m.place, m.detail]), [['semobsessed', 1, '3 тренировки'], ['tim', 2, '2 тренировки']]);
        assert.equal(live.owner.username, 'tim');

        // Конец и сутки на досчёт прошли — счёт закрыт, победа в профиле.
        await social.pool().query("UPDATE challenges SET starts_at = now() - interval '15 days', ends_at = now() - interval '25 hours' WHERE id = $1", [id]);
        assert.equal((await call('PUT', '/challenges/scores', { token: me.token, body: { scores: [{ id, score: 10 }] } })).data.updated, 0);
        const finished = (await call('GET', '/challenges', { token: me.token })).data.find((c) => c.id === id);
        assert.equal(finished.status, 'finished');
        assert.equal(finished.members[0].user.username, 'semobsessed');
        assert.equal((await call('GET', '/users/semobsessed', { token: me.token })).data.challengeWins, 1);
        assert.equal((await call('GET', '/users/tim', { token: me.token })).data.challengeWins, 0);
        assert.equal((await call('POST', `/challenges/${id}/leave`, { token: me.token })).status, 409);

        // Создатель удалил аккаунт — у остальных челлендж остаётся.
        await call('POST', '/users/tim/follow', { token: stranger.token });
        const orphan = (await call('POST', '/challenges', { token: stranger.token, body: { kind: 'volume', weeks: 1, invite: ['tim'] } })).data;
        await call('POST', `/challenges/${orphan.id}/join`, { token: me.token });
        assert.equal((await call('DELETE', '/me', { token: stranger.token })).status, 200);
        const kept = (await call('GET', '/challenges', { token: me.token })).data.find((c) => c.id === orphan.id);
        assert.ok(kept, 'челлендж не удалился');
        assert.equal(kept.owner, null);
        assert.deepEqual(kept.members.map((m) => m.user.username), ['tim']);
    });

    await t.test('активность: лайки, комментарии, ответы и подписчики', async () => {
        const post = (await call('POST', '/posts', { token: me.token, body: { clientId: 'act', title: 'Для активности' } })).data;
        await call('POST', `/posts/${post.id}/like`, { token: friend.token });
        const comment = (await call('POST', `/posts/${post.id}/comments`, { token: friend.token, body: { text: 'Огонь' } })).data;
        await call('POST', `/posts/${post.id}/like`, { token: me.token });
        const badges = (await call('GET', '/badges', { token: me.token })).data;
        assert.ok(badges.activity >= 3, 'лайк, комментарий и подписка');
        const activity = (await call('GET', '/activity', { token: me.token })).data;
        const kinds = activity.items.map((i) => i.kind);
        assert.ok(kinds.includes('like') && kinds.includes('comment') && kinds.includes('follow'));
        assert.ok(activity.items.every((i) => i.actor.username === 'semobsessed'), 'свои лайки в активность не попадают');
        assert.equal(activity.items.find((i) => i.kind === 'comment').text, 'Огонь');

        // Ответ мне на чужом посте — тоже активность.
        const theirs = (await call('POST', '/posts', { token: friend.token, body: { clientId: 'act2', title: 'Чужой' } })).data;
        const mineComment = (await call('POST', `/posts/${theirs.id}/comments`, { token: me.token, body: { text: 'Сколько?' } })).data;
        await call('POST', `/posts/${theirs.id}/comments`, { token: friend.token, body: { text: '150', replyTo: mineComment.id } });
        const withReply = (await call('GET', '/activity', { token: me.token })).data;
        assert.equal(withReply.items[0].kind, 'reply');
        assert.equal(withReply.items[0].postId, theirs.id);

        assert.equal((await call('POST', '/activity/seen', { token: me.token })).status, 200);
        assert.equal((await call('GET', '/badges', { token: me.token })).data.activity, 0);
        assert.ok(comment.id);
    });

    await t.test('совместная тренировка по приглашению', async () => {
        const invite = await call('POST', '/users/semobsessed/together', { token: me.token });
        assert.equal(invite.status, 201);
        assert.equal(invite.data.status, 'pending');
        assert.equal((await call('POST', '/users/semobsessed/together', { token: me.token })).data.id, invite.data.id, 'второе приглашение не плодится');
        assert.equal((await call('GET', '/badges', { token: friend.token })).data.invites, 1);

        // Приглашение — карточкой в переписке.
        const chat = (await call('GET', '/chats/tim/messages', { token: friend.token })).data;
        const card = chat.messages.find((m) => m.invite);
        assert.equal(card.invite.id, invite.data.id);
        assert.equal(card.invite.status, 'pending');

        const incoming = (await call('GET', '/together', { token: friend.token })).data;
        assert.equal(incoming.incoming[0].partner.username, 'tim');
        assert.equal((await call('POST', `/together/${invite.data.id}/accept`, { token: me.token })).status, 403, 'принимает только приглашённый');
        const accepted = await call('POST', `/together/${invite.data.id}/accept`, { token: friend.token });
        assert.equal(accepted.data.status, 'accepted');

        // Напарник видит тренировку, даже если она скрыта от подписчиков.
        await call('PUT', '/me/live', { token: me.token, body: { title: 'День 1', exercise: 'Жим', done: 2, total: 4, partnerOnly: true } });
        const active = (await call('GET', '/together', { token: friend.token })).data.active;
        assert.equal(active.partner.username, 'tim');
        assert.equal(active.partnerLive.exercise, 'Жим');
        assert.equal(active.partnerLive.partnerOnly, undefined);
        assert.equal((await call('GET', '/users/tim', { token: friend.token })).data.live, null, 'в профиле скрытая тренировка не видна');

        // Пост «вместе с»: у автора — напарник, сам он отмечен закончившим.
        const post = (await call('POST', '/posts', { token: me.token, body: { clientId: 'together1', title: 'Вдвоём', together: invite.data.id } })).data;
        assert.equal(post.partner.username, 'semobsessed');
        assert.equal((await call('GET', '/together', { token: me.token })).data.active, null, 'для закончившего совместная тренировка кончилась');
        assert.ok((await call('GET', '/together', { token: friend.token })).data.active, 'у напарника ещё идёт');
        await call('POST', `/together/${invite.data.id}/done`, { token: friend.token });
        const updated = (await call('GET', `/chats/tim/messages?since=${chat.serverTime}`, { token: friend.token })).data;
        assert.equal(updated.messages.find((m) => m.invite).invite.status, 'ended');

        // Отказ и отмена.
        const second = (await call('POST', '/users/tim/together', { token: friend.token })).data;
        assert.equal((await call('POST', `/together/${second.id}/decline`, { token: me.token })).status, 200);
        assert.equal((await call('GET', '/together', { token: me.token })).data.incoming.length, 0);
        const third = (await call('POST', '/users/tim/together', { token: friend.token })).data;
        assert.equal((await call('POST', `/together/${third.id}/cancel`, { token: friend.token })).status, 200);
        assert.equal((await call('POST', `/together/${third.id}/accept`, { token: me.token })).status, 409);
        await call('DELETE', '/me/live', { token: me.token });
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
