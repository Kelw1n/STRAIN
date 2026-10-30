const express = require('express');
const cors = require('cors');

const { createSocial } = require('./social');

const app = express();
const PORT = process.env.PORT || 10000;

// Render стоит за прокси: без этого у всех запросов один и тот же IP,
// и ограничение частоты регистраций срабатывало бы на всех сразу.
app.set('trust proxy', 1);
app.disable('x-powered-by');

// Enable CORS for mobile apps and web
app.use(cors());

// Тела запросов — JSON. Самое крупное поле API — 64 КБ (программы, тренировка),
// фото и голосовые идут отдельно, без JSON. Мегабайта хватает с запасом.
app.use(express.json({ limit: '1mb' }));

// -----------------------------------------------------------------------------
// Health Check
// -----------------------------------------------------------------------------
app.get('/health', (req, res) => {
    res.json({
        status: 'ok',
        service: 'strain-backend',
        timestamp: Date.now(),
        social: !!process.env.DATABASE_URL,
        // Номер набора функций: проверка после выкладки ждёт именно его,
        // а не ответа ещё работающей старой версии.
        api: 6
    });
});

app.get('/', (req, res) => {
    res.json({
        name: 'STRAIN Backend API',
        version: '2.0.0',
        endpoints: { social: '/v2', health: '/health' }
    });
});

// Старый API бро-трекера (/api/profile, /api/chat) убран: приложения давно
// работают через /v2, а он без входа принимал от кого угодно до 25 МБ
// и складывал на диск сервера.

// -----------------------------------------------------------------------------
// Лента, профили и комментарии (Postgres)
// -----------------------------------------------------------------------------
const social = createSocial({ databaseUrl: process.env.DATABASE_URL });
app.use('/v2', social.router);

// Неизвестный адрес и ошибки разбора тела — тоже JSON: приложения ждут {error},
// а стандартный ответ Express — HTML-страница, да ещё со стеком вызовов, если
// NODE_ENV не production (на Render его никто не выставляет).
app.use((req, res) => res.status(404).json({ error: 'Нет такого адреса' }));

// eslint-disable-next-line no-unused-vars
app.use((error, req, res, next) => {
    const status = Number(error && (error.status || error.statusCode)) || 500;
    if (status >= 500) console.error(req.method, req.originalUrl, error);
    if (res.headersSent) return res.end();
    const message = error && error.type === 'entity.too.large' ? 'Слишком большой запрос'
        : error && error.type === 'entity.parse.failed' ? 'Тело запроса — не JSON'
            : status >= 500 ? 'Ошибка сервера' : 'Неверный запрос';
    res.status(status >= 400 && status < 600 ? status : 500).json({ error: message });
});

if (require.main === module) {
    social.init().then(
        (ok) => console.log(ok ? 'Social: database ready' : 'Social: DATABASE_URL not set, /v2 disabled'),
        (error) => console.error('Social: database init failed:', error.message)
    );

    app.listen(PORT, '0.0.0.0', () => {
        console.log(`STRAIN Backend running on port ${PORT}`);
        console.log(`Health check: http://localhost:${PORT}/health`);
    });
}

module.exports = { app, social };
