# Сервер STRAIN: лента тренировок

Профили, подписки, посты с фото и тренировкой, лайки и комментарии. Код —
`server.js` (старые маршруты бро-трекера) и `social.js` (лента, `/v2/...`).

Данные ленты хранятся в Postgres. Диск бесплатного Render стирается при каждом
засыпании сервера, поэтому база нужна отдельная — бесплатный **Neon** подходит.

## Настройка за 10 минут

### 1. База Neon (бесплатно)

1. Открой https://neon.tech → **Sign up** → войди через GitHub.
2. **Create project**: имя `strain`, регион **AWS Europe Central (Frankfurt)**.
3. На странице проекта нажми **Connect** и скопируй строку подключения —
   она начинается с `postgresql://` и заканчивается на `?sslmode=require`.
   Это пароль от базы: никому не пересылай и не клади в репозиторий.

### 2. Render

1. Открой https://dashboard.render.com → свой сервис STRAIN.
2. **Environment** → **Add Environment Variable**:
   - Key: `DATABASE_URL`
   - Value: строка из Neon
3. **Settings → Build & Deploy** проверь:
   - Root Directory: `server`
   - Build Command: `npm install`
   - Start Command: `npm start`
   - Branch: ветка, где лежит этот код (`main` после слияния).
4. **Manual Deploy → Deploy latest commit**.

### 3. Проверка

Открой `https://<твой-сервер>.onrender.com/health` — в ответе должно быть
`"social": true`. Таблицы база создаёт сама при первом запуске.

## Бесплатные лимиты

- Neon: 0,5 ГБ. Фото сжимаются на телефоне до ~200 КБ — это тысячи постов.
- Render: сервер засыпает через 15 минут без запросов. Workflow
  `.github/workflows/keepalive.yml` пингует его раз в 10 минут (работает из `main`).

## Проверка кода

```
cd server && npm install
TEST_DATABASE_URL=postgres://user:pass@localhost:5432/strain_test npm test
```

В GitHub Actions то же самое делает `.github/workflows/server.yml` на временном Postgres.
