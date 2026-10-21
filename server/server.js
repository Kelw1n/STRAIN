const express = require('express');
const cors = require('cors');

const { createSocial } = require('./social');

const app = express();
const PORT = process.env.PORT || 10000;

// Render стоит за прокси: без этого у всех запросов один и тот же IP,
// и ограничение частоты регистраций срабатывало бы на всех сразу.
app.set('trust proxy', 1);

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
social.init().then(
    (ok) => console.log(ok ? 'Social: database ready' : 'Social: DATABASE_URL not set, /v2 disabled'),
    (error) => console.error('Social: database init failed:', error.message)
);

// Start server
app.listen(PORT, '0.0.0.0', () => {
    console.log(`STRAIN Backend running on port ${PORT}`);
    console.log(`Health check: http://localhost:${PORT}/health`);
});
