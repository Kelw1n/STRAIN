// Ответы самого приложения Express — вне раздела /v2. Базы не нужно.
const test = require('node:test');
const assert = require('node:assert');

test('кривое тело запроса и неизвестный адрес — JSON без стека вызовов', async (t) => {
    delete process.env.DATABASE_URL;
    const { app } = require('../server');
    const server = app.listen(0);
    t.after(() => server.close());
    const base = `http://127.0.0.1:${server.address().port}`;

    const broken = await fetch(`${base}/v2/register`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: '{"username": ' });
    assert.equal(broken.status, 400);
    assert.match(broken.headers.get('content-type'), /json/);
    const error = await broken.json();
    assert.equal(typeof error.error, 'string');
    assert.equal(JSON.stringify(error).includes('node_modules'), false, 'без стека вызовов');

    const missing = await fetch(`${base}/nope`);
    assert.equal(missing.status, 404);
    assert.deepEqual(await missing.json(), { error: 'Нет такого адреса' });
    assert.equal(missing.headers.get('x-powered-by'), null);

    const health = await (await fetch(`${base}/health`)).json();
    assert.equal(health.api, 6, 'номер набора функций ждёт проверка после выкладки');
});
