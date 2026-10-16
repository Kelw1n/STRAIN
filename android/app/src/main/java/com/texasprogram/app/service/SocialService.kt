package com.texasprogram.app.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.exifinterface.media.ExifInterface
import com.texasprogram.app.model.AchievementCalculator
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.SocialActivityPage
import com.texasprogram.app.model.SocialBadge
import com.texasprogram.app.model.SocialChatList
import com.texasprogram.app.model.SocialChatPage
import com.texasprogram.app.model.SocialComment
import com.texasprogram.app.model.SocialFeedPage
import com.texasprogram.app.model.SocialFormat
import com.texasprogram.app.model.SocialLeaderboardEntry
import com.texasprogram.app.model.SocialLive
import com.texasprogram.app.model.SocialLiveFriend
import com.texasprogram.app.model.SocialMessage
import com.texasprogram.app.model.SocialPost
import com.texasprogram.app.model.SocialPostDraft
import com.texasprogram.app.model.SocialProgramBuilder
import com.texasprogram.app.model.SocialProgramDay
import com.texasprogram.app.model.SocialUser
import com.texasprogram.app.model.SocialUserShort
import com.texasprogram.app.model.SocialWeekStats
import com.texasprogram.app.model.SocialWorkout
import com.texasprogram.app.model.WeeklyStatsBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/// Ошибка ленты с понятным текстом: показываем её человеку как есть.
class SocialException(message: String, val status: Int = 0) : Exception(message)

/// Лента STRAIN: профиль, подписки, посты, лайки и комментарии.
///
/// Состояние — в Compose-переменных: экраны перерисовываются сами, когда
/// меняется профиль или очередь публикаций. Сеть — на фоновом потоке.
object SocialService {
    const val DEFAULT_SERVER = "https://strain-feed.onrender.com"
    private const val PREFS = "social"

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var syncJob: Job? = null

    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    var token by mutableStateOf<String?>(null)
        private set
    var me by mutableStateOf<SocialUser?>(null)
        private set
    /// Посты в очереди на отправку — их видно в ленте до того, как они уйдут.
    var outbox by mutableStateOf<List<SocialPostDraft>>(emptyList())
        private set
    var outboxError by mutableStateOf<String?>(null)
        private set
    var isFlushing by mutableStateOf(false)
        private set
    /// Меняется после каждой публикации или удаления: ленты по нему перезагружаются.
    var feedVersion by mutableIntStateOf(0)
        private set
    /// Непрочитанные личные сообщения — точка на вкладке «Друзья».
    var unreadMessages by mutableIntStateOf(0)
        private set
    /// Новые лайки, комментарии и подписчики — точка на колокольчике и вкладке «Главное».
    var unseenActivity by mutableIntStateOf(0)
        private set
    /// Подписки, которые тренируются прямо сейчас.
    var liveFriends by mutableStateOf<List<SocialLiveFriend>>(emptyList())
        private set
    /// Переписка прячет панель вкладок: внизу экрана живёт поле ввода.
    var hidesTabBar by mutableStateOf(false)
    /// Видно ли друзьям, что идёт тренировка.
    var sharesLive by mutableStateOf(true)
        private set

    /// Последняя загруженная лента: при переходе между вкладками она видна сразу,
    /// а свежая подгружается без пустого экрана со спиннером.
    val feedCache = mutableMapOf<FeedScope, SocialFeedPage>()

    /// Профили и комментарии, которые уже открывали. Экран под открытым поверх
    /// создаётся заново при возврате «Назад» — с кэшем он сразу показывает
    /// прошлое содержимое, а не пустой экран со спиннером.
    val userCache = mutableMapOf<String, Pair<SocialUser, SocialFeedPage>>()
    val commentCache = mutableMapOf<String, List<SocialComment>>()

    val isSignedIn: Boolean get() = token != null

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        token = prefs().getString("token", null)
        sharesLive = prefs().getBoolean("share_live", true)
        outbox = OutboxStore.loadAll()
    }

    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val serverUrl: String
        get() = if (::appContext.isInitialized) prefs().getString("server", null) ?: DEFAULT_SERVER else DEFAULT_SERVER

    fun imageUrl(id: String?): String? = id?.takeIf { it.isNotBlank() }?.let { "$serverUrl/v2/images/$it" }

    // MARK: Запросы

    private suspend fun call(
        method: String,
        path: String,
        body: String? = null,
        bytes: ByteArray? = null,
        contentType: String? = null,
        requiresAuth: Boolean = false,
        useToken: String? = token
    ): String = withContext(Dispatchers.IO) {
        if (requiresAuth && useToken == null) throw SocialException("Сначала создай профиль STRAIN во вкладке «Профиль».", 401)
        val connection = try {
            URL(serverUrl + "/v2" + path).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw SocialException("Неверный адрес сервера")
        }
        try {
            connection.requestMethod = method
            // Спящий бесплатный сервер просыпается до минуты.
            connection.connectTimeout = 75_000
            connection.readTimeout = 75_000
            connection.setRequestProperty("Accept", "application/json")
            useToken?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            val payload = body?.toByteArray(Charsets.UTF_8) ?: bytes
            if (payload != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", if (body != null) "application/json; charset=utf-8" else contentType ?: "application/octet-stream")
                connection.setFixedLengthStreamingMode(payload.size)
                connection.outputStream.use { it.write(payload) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching {
                    json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.content
                }.getOrNull() ?: if (status == 503) "Сервер ленты ещё не настроен или перезапускается." else "Ошибка сервера ($status)"
                throw SocialException(message, status)
            }
            text
        } catch (e: SocialException) {
            throw e
        } catch (e: IOException) {
            throw SocialException("Нет связи с сервером. Если ленту давно не открывали, сервер просыпается — попробуй через полминуты.")
        } catch (e: RuntimeException) {
            // Сбой внутри HttpURLConnection не должен ронять приложение.
            throw SocialException("Не удалось связаться с сервером")
        } finally {
            connection.disconnect()
        }
    }

    private inline fun <reified T> decode(text: String): T = try {
        json.decodeFromString<T>(text)
    } catch (e: Exception) {
        throw SocialException("Сервер ответил непонятно — возможно, приложение устарело.")
    }

    private fun q(value: String) = URLEncoder.encode(value, "UTF-8")

    // MARK: Профиль

    @Serializable
    private data class RegisterResponse(val token: String, val user: SocialUser)

    suspend fun register(username: String) {
        val name = SocialFormat.normalizedUsername(username)
        if (!SocialFormat.isValidUsername(name)) throw SocialException("Юзернейм: 3–24 символа — латиница, цифры, точка и подчёркивание")
        val body = buildJsonObject { put("username", name) }.toString()
        val response = decode<RegisterResponse>(call("POST", "/register", body = body, useToken = null))
        storeToken(response.token)
        me = response.user
    }

    /// Вход кодом с другого телефона или из резервной копии.
    suspend fun signIn(code: String) {
        val cleaned = code.trim().lowercase()
        if (!Regex("^[a-f0-9]{64}$").matches(cleaned)) throw SocialException("Код входа — 64 символа из цифр и латинских букв a–f")
        val user = decode<SocialUser>(call("GET", "/me", useToken = cleaned))
        storeToken(cleaned)
        me = user
    }

    /// Код из копии подхватываем молча, только если своего профиля ещё нет.
    fun adoptBackupToken(code: String?) {
        if (token != null || code.isNullOrBlank()) return
        scope.launch { runCatching { signIn(code) } }
    }

    fun signOut() {
        storeToken(null)
        me = null
        unreadMessages = 0
        unseenActivity = 0
        liveFriends = emptyList()
        lastLive = null
    }

    suspend fun deleteAccount() {
        call("DELETE", "/me", requiresAuth = true)
        // Неотправленные посты удалённого профиля не должны уйти от имени следующего.
        withContext(Dispatchers.IO) { outbox.forEach { OutboxStore.remove(it.clientId) } }
        outbox = emptyList()
        signOut()
    }

    private fun storeToken(value: String?) {
        token = value
        prefs().edit().apply { if (value == null) remove("token") else putString("token", value) }.apply()
        prefs().edit().remove("sync_fingerprint").apply()
        feedCache.clear()
        userCache.clear()
        commentCache.clear()
        feedVersion++
    }

    suspend fun refreshMe() {
        if (token == null) return
        try {
            me = decode<SocialUser>(call("GET", "/me"))
        } catch (e: SocialException) {
            if (e.status == 401) signOut()
        }
    }

    /// Правка профиля: в JSON уходят только заданные поля. `clearHeight`
    /// отправляет null — рост стёрт, а не «не трогать».
    data class ProfilePatch(
        val username: String? = null,
        val bio: String? = null,
        val heightCm: Int? = null,
        val clearHeight: Boolean = false,
        val weightKg: Double? = null,
        val showBody: Boolean? = null,
        val avatar: String? = null,
        val cover: String? = null,
        val programs: List<SocialProgramDay>? = null,
        val achievements: List<SocialBadge>? = null,
        val stats: SocialWeekStats? = null
    ) {
        fun toJson(): String {
            val map = mutableMapOf<String, JsonElement>()
            username?.let { map["username"] = JsonPrimitive(it) }
            bio?.let { map["bio"] = JsonPrimitive(it) }
            if (clearHeight) map["heightCm"] = JsonNull else heightCm?.let { map["heightCm"] = JsonPrimitive(it) }
            weightKg?.let { map["weightKg"] = JsonPrimitive(it) }
            showBody?.let { map["showBody"] = JsonPrimitive(it) }
            avatar?.let { map["avatar"] = JsonPrimitive(it) }
            cover?.let { map["cover"] = JsonPrimitive(it) }
            programs?.let { map["programs"] = json.encodeToJsonElement(kotlinx.serialization.builtins.ListSerializer(SocialProgramDay.serializer()), it) }
            achievements?.let { map["achievements"] = json.encodeToJsonElement(kotlinx.serialization.builtins.ListSerializer(SocialBadge.serializer()), it) }
            stats?.let { map["stats"] = json.encodeToJsonElement(SocialWeekStats.serializer(), it) }
            return JsonObject(map).toString()
        }
    }

    suspend fun update(patch: ProfilePatch): SocialUser {
        val user = decode<SocialUser>(call("PATCH", "/me", body = patch.toJson(), requiresAuth = true))
        me = user
        return user
    }

    /// Программа, медали, вес и сводка недели — в профиль. Шлём, только если что-то поменялось.
    fun scheduleSync(profile: ProgramProfile) {
        if (token == null || !::appContext.isInitialized) return
        syncJob?.cancel()
        syncJob = scope.launch {
            delay(3_000)
            val patch = withContext(Dispatchers.Default) {
                ProfilePatch(
                    weightKg = profile.latestBodyWeight,
                    programs = SocialProgramBuilder.days(profile),
                    achievements = AchievementCalculator.badges(AchievementCalculator.progress(profile)),
                    stats = WeeklyStatsBuilder.stats(profile)
                )
            }
            val fingerprint = patch.toJson()
            if (fingerprint == prefs().getString("sync_fingerprint", null)) return@launch
            runCatching { update(patch) }.onSuccess {
                prefs().edit().putString("sync_fingerprint", fingerprint).apply()
            }
        }
    }

    // MARK: Люди

    suspend fun user(username: String): SocialUser = decode(call("GET", "/users/${q(username)}"))

    suspend fun searchUsers(query: String): List<SocialUserShort> = decode(call("GET", "/users?q=${q(query)}"))

    suspend fun followers(username: String): List<SocialUserShort> = decode(call("GET", "/users/${q(username)}/followers"))

    suspend fun following(username: String): List<SocialUserShort> = decode(call("GET", "/users/${q(username)}/following"))

    suspend fun setFollowing(username: String, follow: Boolean): SocialUser {
        val user = decode<SocialUser>(call(if (follow) "POST" else "DELETE", "/users/${q(username)}/follow", requiresAuth = true))
        refreshMe()
        return user
    }

    // MARK: Посты

    enum class FeedScope(val title: String, val query: String) { ALL("Все", "all"), FOLLOWING("Подписки", "following") }

    suspend fun feed(scope: FeedScope, before: Double? = null): SocialFeedPage {
        val tail = before?.let { "&before=${it.toLong()}" }.orEmpty()
        return decode(call("GET", "/feed?scope=${scope.query}$tail"))
    }

    suspend fun posts(username: String, before: Double? = null): SocialFeedPage {
        val tail = before?.let { "?before=${it.toLong()}" }.orEmpty()
        return decode(call("GET", "/users/${q(username)}/posts$tail"))
    }

    suspend fun post(id: String): SocialPost = decode(call("GET", "/posts/$id"))

    suspend fun setLiked(postId: String, liked: Boolean): SocialPost =
        decode(call(if (liked) "POST" else "DELETE", "/posts/$postId/like", requiresAuth = true))

    suspend fun deletePost(postId: String) {
        call("DELETE", "/posts/$postId", requiresAuth = true)
        feedVersion++
        refreshMe()
    }

    suspend fun comments(postId: String): List<SocialComment> = decode(call("GET", "/posts/$postId/comments"))

    /// `replyTo` — комментарий, на который отвечаем: ответ встанет в его ветку.
    suspend fun addComment(postId: String, text: String, replyTo: String? = null): SocialComment {
        val body = buildJsonObject {
            put("text", text)
            if (replyTo != null) put("replyTo", replyTo)
        }.toString()
        return decode(call("POST", "/posts/$postId/comments", body = body, requiresAuth = true))
    }

    // MARK: Личные сообщения

    suspend fun chats(): SocialChatList {
        val list = decode<SocialChatList>(call("GET", "/chats", requiresAuth = true))
        unreadMessages = list.unread
        return list
    }

    /// Сколько непрочитанных сообщений и новой активности — для точек на вкладках.
    suspend fun refreshUnread() {
        if (token == null) {
            unreadMessages = 0
            unseenActivity = 0
            return
        }
        runCatching { decode<JsonObject>(call("GET", "/badges")) }
            .onSuccess {
                unreadMessages = it["messages"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                unseenActivity = it["activity"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            }
    }

    /// Сообщения переписки, новые первыми. `after` — только пришедшие позже,
    /// `since` — всё, что появилось или поменялось с `serverTime` прошлого ответа.
    suspend fun messages(username: String, before: Double? = null, after: Double? = null, since: Double? = null): SocialChatPage {
        val query = listOfNotNull(
            before?.let { "before=${it.toLong()}" },
            after?.let { "after=${it.toLong()}" },
            since?.let { "since=${it.toLong()}" }
        )
        val tail = if (query.isEmpty()) "" else "?" + query.joinToString("&")
        return decode(call("GET", "/chats/${q(username)}/messages$tail", requiresAuth = true))
    }

    /// `image` — id фото, уже загруженного через `uploadImage`; `replyTo` — сообщение, на которое отвечаем.
    suspend fun sendMessage(
        username: String,
        text: String,
        clientId: String = newClientId(),
        image: String? = null,
        replyTo: String? = null
    ): SocialMessage {
        val body = buildJsonObject {
            put("text", text)
            put("clientId", clientId)
            if (image != null) put("image", image)
            if (replyTo != null) put("replyTo", replyTo)
        }.toString()
        return decode(call("POST", "/chats/${q(username)}/messages", body = body, requiresAuth = true))
    }

    /// Реакция на сообщение; та же ещё раз — снимает её.
    suspend fun react(messageId: String, emoji: String): SocialMessage {
        val body = buildJsonObject { put("emoji", emoji) }.toString()
        return decode(call("POST", "/messages/$messageId/react", body = body, requiresAuth = true))
    }

    private val typingSentAt = mutableMapOf<String, Long>()

    /// «Печатает…» у собеседника: не чаще раза в три секунды.
    fun sendTyping(username: String) {
        if (token == null) return
        val now = System.currentTimeMillis()
        if (now - (typingSentAt[username] ?: 0L) < 3_000) return
        typingSentAt[username] = now
        scope.launch { runCatching { call("POST", "/chats/${q(username)}/typing", requiresAuth = true) } }
    }

    // MARK: Активность

    suspend fun activity(): SocialActivityPage {
        val page = decode<SocialActivityPage>(call("GET", "/activity", requiresAuth = true))
        unseenActivity = page.unseen
        return page
    }

    /// Всё в списке активности просмотрено — точка гаснет.
    suspend fun markActivitySeen() {
        if (token == null) return
        unseenActivity = 0
        runCatching { call("POST", "/activity/seen", requiresAuth = true) }
    }

    // MARK: Тренируется сейчас

    suspend fun refreshLive(): List<SocialLiveFriend> {
        if (token == null) {
            liveFriends = emptyList()
            return liveFriends
        }
        runCatching { decode<List<SocialLiveFriend>>(call("GET", "/live", requiresAuth = true)) }
            .onSuccess { liveFriends = it }
        return liveFriends
    }

    private var lastLive: Pair<SocialLive, Long>? = null
    private var liveJob: Job? = null

    /// Идущая тренировка — друзьям. Одинаковое не шлём чаще раза в пять минут:
    /// этого хватает, чтобы статус на сервере не протух.
    fun reportLive(live: SocialLive?) {
        if (token == null) return
        if (live == null || !sharesLive) {
            if (lastLive != null) scope.launch { clearLive() }
            return
        }
        val last = lastLive
        val now = System.currentTimeMillis()
        if (last != null && last.first == live && now - last.second < 300_000) return
        lastLive = live to now
        liveJob?.cancel()
        liveJob = scope.launch {
            // Несколько отметок подряд — один запрос.
            delay(1_500)
            runCatching { call("PUT", "/me/live", body = json.encodeToString(SocialLive.serializer(), live), requiresAuth = true) }
        }
    }

    suspend fun clearLive() {
        liveJob?.cancel()
        lastLive = null
        if (token == null) return
        runCatching { call("DELETE", "/me/live", requiresAuth = true) }
    }

    fun updateSharesLive(value: Boolean) {
        sharesLive = value
        if (::appContext.isInitialized) prefs().edit().putBoolean("share_live", value).apply()
        if (!value) scope.launch { clearLive() }
    }

    // MARK: Рейтинг недели

    suspend fun leaderboard(): List<SocialLeaderboardEntry> = decode(call("GET", "/leaderboard", requiresAuth = true))

    suspend fun deleteMessage(id: String) {
        call("DELETE", "/messages/$id", requiresAuth = true)
    }

    suspend fun deleteComment(id: String) {
        call("DELETE", "/comments/$id", requiresAuth = true)
    }

    // MARK: Фото

    @Serializable
    private data class ImageResponse(val id: String)

    suspend fun uploadImage(jpeg: ByteArray): String =
        decode<ImageResponse>(call("POST", "/images", bytes = jpeg, contentType = "image/jpeg", requiresAuth = true)).id

    suspend fun uploadImage(context: Context, uri: Uri, maxSide: Int = 1280): String {
        val jpeg = withContext(Dispatchers.IO) { jpeg(context, uri, maxSide) } ?: throw SocialException("Не удалось открыть фото")
        return uploadImage(jpeg)
    }

    /// Фото уменьшается до 1280 px по длинной стороне и поворачивается по EXIF:
    /// так пост грузится быстро, а в бесплатную базу помещаются тысячи тренировок.
    fun jpeg(context: Context, uri: Uri, maxSide: Int = 1280, quality: Int = 72): ByteArray? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val scale = minOf(1f, maxSide.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0f) postRotate(rotation)
        }
        val bitmap = if (scale < 1f || rotation != 0f) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } else decoded
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
    }.getOrNull()

    // MARK: Публикация

    /// Кладёт пост в очередь на телефоне и сразу пробует отправить.
    /// Нет связи — пост дождётся следующего открытия приложения.
    fun enqueue(context: Context, draft: SocialPostDraft, photos: List<Uri>) {
        scope.launch {
            val files = withContext(Dispatchers.IO) { photos.take(5).mapNotNull { jpeg(context, it) } }
            val saved = draft.copy(photoCount = files.size, uploadedPhotos = List(files.size) { null })
            // Нет места на диске — пост не сохранится, но и приложение не упадёт.
            val stored = withContext(Dispatchers.IO) { runCatching { OutboxStore.save(saved, files) }.isSuccess }
            if (!stored) {
                outboxError = "Не удалось сохранить тренировку для ленты — мало места на телефоне"
                return@launch
            }
            outbox = OutboxStore.loadAll()
            flushOutbox()
        }
    }

    fun discard(draft: SocialPostDraft) {
        OutboxStore.remove(draft.clientId)
        outbox = OutboxStore.loadAll()
    }

    @Serializable
    private data class PostBody(
        val clientId: String,
        val title: String,
        val description: String,
        val durationSeconds: Int,
        val volumeKg: Double,
        val sets: Int,
        val effort: Int? = null,
        val photos: List<String>,
        val workout: SocialWorkout
    )

    fun flushOutbox() {
        if (!::appContext.isInitialized || token == null || isFlushing || outbox.isEmpty()) return
        isFlushing = true
        outboxError = null
        scope.launch {
            try {
                for (pending in outbox) {
                    var draft = pending
                    if (draft.uploadedPhotos.size != draft.photoCount) {
                        draft = draft.copy(uploadedPhotos = List(draft.photoCount) { null })
                    }
                    try {
                        for (index in 0 until draft.photoCount) {
                            if (draft.uploadedPhotos.getOrNull(index) != null) continue
                            val data = withContext(Dispatchers.IO) { OutboxStore.photo(draft.clientId, index) } ?: continue
                            val id = uploadImage(data)
                            draft = draft.copy(uploadedPhotos = draft.uploadedPhotos.toMutableList().also { it[index] = id })
                            withContext(Dispatchers.IO) { OutboxStore.update(draft) }
                        }
                        val body = PostBody(
                            clientId = draft.clientId,
                            title = draft.title,
                            description = draft.description,
                            durationSeconds = draft.durationSeconds,
                            volumeKg = draft.volumeKg,
                            sets = draft.sets,
                            effort = draft.effort,
                            photos = draft.uploadedPhotos.filterNotNull(),
                            workout = draft.workout
                        )
                        call("POST", "/posts", body = json.encodeToString(body), requiresAuth = true)
                        withContext(Dispatchers.IO) { OutboxStore.remove(draft.clientId) }
                        outbox = OutboxStore.loadAll()
                        feedVersion++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Любая ошибка — пост остаётся в очереди. Раньше всё, кроме
                        // ошибки сервера, роняло приложение.
                        outboxError = (e as? SocialException)?.message ?: "Не удалось отправить тренировку"
                        break
                    }
                }
                refreshMe()
            } finally {
                isFlushing = false
            }
        }
    }

    fun newClientId(): String = UUID.randomUUID().toString().lowercase()

    // MARK: Очередь на диске

    /// Каждый неотправленный пост — папка с draft.json и фото photo-N.jpg.
    private object OutboxStore {
        private val root: File get() = File(appContext.filesDir, "social_outbox")
        private fun folder(id: String) = File(root, id)

        fun save(draft: SocialPostDraft, photos: List<ByteArray>) {
            val dir = folder(draft.clientId).apply { mkdirs() }
            photos.forEachIndexed { index, data -> File(dir, "photo-$index.jpg").writeBytes(data) }
            update(draft)
        }

        fun update(draft: SocialPostDraft) {
            val dir = folder(draft.clientId).apply { mkdirs() }
            val temp = File(dir, "draft.json.tmp")
            temp.writeText(json.encodeToString(draft))
            temp.renameTo(File(dir, "draft.json"))
        }

        fun photo(id: String, index: Int): ByteArray? = File(folder(id), "photo-$index.jpg").takeIf { it.exists() }?.readBytes()

        fun remove(id: String) {
            folder(id).deleteRecursively()
        }

        fun loadAll(): List<SocialPostDraft> =
            (root.listFiles() ?: emptyArray())
                .mapNotNull { dir ->
                    runCatching { json.decodeFromString<SocialPostDraft>(File(dir, "draft.json").readText()) }.getOrNull()
                }
                .sortedBy { it.createdAtMillis }
    }
}
