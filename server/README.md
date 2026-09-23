# 🚀 STRAIN Backend (Бро-трекер и Чат)

Лёгкий, быстрый и надёжный бэкенд для приложения STRAIN.

- **Синхронизация профилей**: сохраняет любую кастомную программу (любое количество недель, все подсобные упражнения, реальные веса).
- **Прямой чат**: сообщения сохраняются в каналы (`chat_<id1>_<id2>`), не затираются, поддерживают текст, результаты подходов и фотографии.
- **Heartbeat**: быстрый пинг статуса «В сети».

---

## ⚡ Бесплатный деплой за 2 минуты

### Вариант 1: Koyeb (Рекомендуется — 100% бесплатно, НЕ засыпает)

1. Зарегистрируйся на [koyeb.com](https://www.koyeb.com/) (через GitHub).
2. Нажми **Create App** -> **GitHub**.
3. Выбери репозиторий `STRAIN` (или свой форк).
4. Укажи параметры:
   - **Root directory**: `/server`
   - **Builder**: `Dockerfile` (или `Node.js`)
   - **Port**: `3000`
5. Нажми **Deploy**.
6. Через 1 минуту ты получишь публичную ссылку вида:
   `https://strain-backend-yourname.koyeb.app`

---

### Вариант 2: Render.com (Бесплатно)

1. Зарегистрируйся на [render.com](https://render.com/) (через GitHub).
2. Нажми **New +** -> **Web Service**.
3. Подключи репозиторий `STRAIN`.
4. Настройки:
   - **Root Directory**: `server`
   - **Environment**: `Node`
   - **Build Command**: `npm install`
   - **Start Command**: `npm start`
5. Нажми **Create Web Service**.
6. Получишь ссылку вида:
   `https://strain-backend.onrender.com`

---

### Проверка работоспособности:
Открой в браузере: `https://твой-сервер/health`
Должен вернуть: `{"status":"ok","service":"strain-backend",...}`
