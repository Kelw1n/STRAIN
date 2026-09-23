package com.texasprogram.app.service

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.texasprogram.app.model.BroChatMessage
import com.texasprogram.app.model.BroExercise
import com.texasprogram.app.model.BroLiftEntry
import com.texasprogram.app.model.BroMessageType
import com.texasprogram.app.model.BroProfileData
import com.texasprogram.app.model.BroWorkoutDay
import com.texasprogram.app.model.LoadPrescription
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.WorkoutSharePayload
import com.texasprogram.app.model.formatWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

typealias BroLiftEntry = com.texasprogram.app.model.BroLiftEntry
typealias BroExercise = com.texasprogram.app.model.BroExercise
typealias BroWorkoutDay = com.texasprogram.app.model.BroWorkoutDay
typealias BroProfileData = com.texasprogram.app.model.BroProfileData

@Serializable
private data class RestfulApiObject(
    val id: String? = null,
    val name: String,
    val data: BroProfileData
)

data class AddBuddyResult(
    val success: Boolean,
    val buddyName: String,
    val message: String
)

@Serializable
private data class CreateResponse(
    val id: String? = null
)

/// Сервис синхронизации «Бро-трекера» на Android.
class BroTrackerService(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("strain.bro_tracker", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    var myBroId by mutableStateOf(getOrCreateMyId())
        private set

    val backendBaseUrl: String
        get() = prefs.getString("strain_backend_url", "https://strain-y94r.onrender.com") ?: "https://strain-y94r.onrender.com"

    fun setBackendUrl(url: String) {
        val trimmed = url.trim().trimEnd('/')
        prefs.edit().putString("strain_backend_url", trimmed).apply()
    }

    suspend fun pingBackend(customUrl: String? = null): Triple<Boolean, Long, String> = withContext(Dispatchers.IO) {
        val base = (customUrl ?: backendBaseUrl).trim().trimEnd('/')
        val start = System.currentTimeMillis()
        try {
            val url = URL("$base/health")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.connect()
            val code = conn.responseCode
            val elapsed = System.currentTimeMillis() - start
            if (code == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                if (body.contains("strain-backend")) {
                    Triple(true, elapsed, "Сервер активен (strain-backend)")
                } else {
                    Triple(true, elapsed, "Сервер отвечает (HTTP 200)")
                }
            } else {
                Triple(false, elapsed, "HTTP $code")
            }
        } catch (e: Exception) {
            Triple(false, 0L, e.message ?: "Ошибка соединения")
        }
    }

    var buddyIds by mutableStateOf(loadBuddyIds())
        private set

    var buddies by mutableStateOf(loadCachedBuddies())
        private set

    var isSyncing by mutableStateOf(false)
        private set

    private fun getOrCreateMyId(): String {
        val saved = prefs.getString(KEY_MY_ID, null)
        if (!saved.isNullOrBlank()) return saved
        val newId = "bro_" + UUID.randomUUID().toString().take(8).lowercase()
        prefs.edit()
            .putString(KEY_MY_ID, newId)
            .putBoolean(KEY_HAS_SERVER_ID, false)
            .apply()
        return newId
    }

    private fun loadBuddyIds(): List<String> {
        val raw = prefs.getString(KEY_BUDDY_IDS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<String>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadCachedBuddies(): List<BroProfileData> {
        val raw = prefs.getString(KEY_CACHED_BUDDIES, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<BroProfileData>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveBuddyIds(ids: List<String>) {
        buddyIds = ids
        prefs.edit().putString(KEY_BUDDY_IDS, json.encodeToString(ids)).apply()
    }

    private fun saveCachedBuddies(list: List<BroProfileData>) {
        buddies = list
        prefs.edit().putString(KEY_CACHED_BUDDIES, json.encodeToString(list)).apply()
    }

    /// Генерирует полную QR-ссылку с данными профиля для мгновенного добавления офлайн
    fun buildQRLink(profile: ProgramProfile): String {
        val name = profile.name.ifBlank { "Бро" }
        val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
        val kind = profile.programKind.name
        val week = profile.currentWeek
        val plan = profile.workoutPlan
        val curWeekPlan = plan.weeks.firstOrNull { it.number == week }
        val nextDay = curWeekPlan?.days?.firstOrNull { !profile.isCompleted(week, it.number) }
        val day = nextDay?.number ?: 1
        val sq = profile.squat5RM.toInt()
        val bp = profile.bench5RM.toInt()
        val dl = profile.deadlift5RM.toInt()

        val sb = StringBuilder("strain://bro?id=$myBroId&name=$encodedName&kind=$kind&w=$week&d=$day&sq=$sq&bp=$bp&dl=$dl")
        val srvEncoded = java.net.URLEncoder.encode(backendBaseUrl, "UTF-8")
        sb.append("&srv=").append(srvEncoded)
        profile.back?.takeIf { it.isNotBlank() }?.let { sb.append("&back=").append(java.net.URLEncoder.encode(it, "UTF-8")) }
        profile.press?.takeIf { it.isNotBlank() }?.let { sb.append("&press=").append(java.net.URLEncoder.encode(it, "UTF-8")) }
        profile.pull?.takeIf { it.isNotBlank() }?.let { sb.append("&pull=").append(java.net.URLEncoder.encode(it, "UTF-8")) }
        profile.arms?.takeIf { it.isNotBlank() }?.let { sb.append("&arms=").append(java.net.URLEncoder.encode(it, "UTF-8")) }
        profile.core?.takeIf { it.isNotBlank() }?.let { sb.append("&core=").append(java.net.URLEncoder.encode(it, "UTF-8")) }
        return sb.toString()
    }

    suspend fun syncMyProfile(profile: ProgramProfile) = withContext(Dispatchers.IO) {
        isSyncing = true
        try {
            val plan = profile.workoutPlan
            val schedule = profile.schedule()
            val focus = schedule.focus
            val curWeek = focus?.week ?: profile.currentWeek
            val curWeekPlan = plan.weeks.firstOrNull { it.number == curWeek }
            val nextDay = curWeekPlan?.days?.firstOrNull { !profile.isCompleted(curWeek, it.number) }
            val curDay = focus?.day?.number ?: (nextDay?.number ?: 1)

            val lifts = ArrayList<BroLiftEntry>()
            if (focus != null) {
                val focusExercises = profile.exercises(focus)
                for (ex in focusExercises) {
                    val load = ex.load.displayText
                    val presc = if (ex.sets > 0) "${ex.sets}×${ex.reps} · $load" else "${ex.reps} · $load"
                    lifts.add(BroLiftEntry(ex.name, presc))
                }
            } else {
                val activeDay = curWeekPlan?.days?.firstOrNull()
                if (activeDay != null) {
                    val activeExercises = profile.exercises(activeDay, null)
                    for (ex in activeExercises) {
                        val load = ex.load.displayText
                        val presc = if (ex.sets > 0) "${ex.sets}×${ex.reps} · $load" else "${ex.reps} · $load"
                        lifts.add(BroLiftEntry(ex.name, presc))
                    }
                }
            }

            val benchMap = schedule.allPending.mapNotNull { sw ->
                sw.benchSession?.let { (sw.week to sw.day.number) to it }
            }.toMap()

            val programDays = ArrayList<BroWorkoutDay>()
            for (week in plan.weeks) {
                for (day in week.days) {
                    val benchSession = benchMap[week.number to day.number]
                    val resolvedExercises = profile.exercises(day, benchSession)
                    val exs = resolvedExercises.map {
                        BroExercise(it.name, it.sets, it.reps, it.load.displayText)
                    }
                    programDays.add(BroWorkoutDay(week.number, day.number, day.title, exs))
                }
            }

            val myData = BroProfileData(
                broId = myBroId,
                name = profile.name.ifBlank { "Бро" },
                programKind = profile.programKind.name,
                programTitle = profile.programKind.title,
                currentWeek = curWeek,
                currentDay = curDay,
                lastActiveEpoch = System.currentTimeMillis(),
                squat5RM = profile.squat5RM,
                bench5RM = profile.bench5RM,
                deadlift5RM = profile.deadlift5RM,
                recentLifts = lifts,
                programDays = programDays,
                recentChatMessages = loadOutbox()
            )

            // 1. Синхронизация с выделенным бэкендом STRAIN (без лимитов размера JSON)
            try {
                val bUrl = URL("$backendBaseUrl/api/profile/$myBroId")
                val bConn = (bUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("Accept", "application/json")
                    doOutput = true
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                val body = json.encodeToString(myData)
                OutputStreamWriter(bConn.outputStream, "UTF-8").use {
                    it.write(body)
                    it.flush()
                }
                bConn.responseCode
                bConn.disconnect()
            } catch (_: Exception) {}

            // 2. Fallback на api.restful-api.dev
            val endpoint = "https://api.restful-api.dev/objects"
            var hasServerId = prefs.getBoolean(KEY_HAS_SERVER_ID, false) &&
                myBroId.isNotBlank() && !myBroId.startsWith("bro_")
            val oldId = myBroId
            var is404 = false

            var synced = false

            if (hasServerId) {
                try {
                    val putUrl = URL("$endpoint/$myBroId")
                    val putConn = (putUrl.openConnection() as HttpURLConnection).apply {
                        requestMethod = "PUT"
                        setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                        setRequestProperty("Accept", "application/json")
                        doOutput = true
                        connectTimeout = 8000
                        readTimeout = 8000
                    }
                    val apiObj = RestfulApiObject(
                        id = myBroId,
                        name = "STRAIN_BRO",
                        data = myData
                    )
                    val body = json.encodeToString(apiObj)
                    OutputStreamWriter(putConn.outputStream, "UTF-8").use {
                        it.write(body)
                        it.flush()
                    }

                    val code = putConn.responseCode
                    if (code in 200..299) {
                        synced = true
                    } else if (code == 404) {
                        is404 = true
                    }
                    putConn.disconnect()
                } catch (_: Exception) {
                }

                if (is404) {
                    prefs.edit().putBoolean(KEY_HAS_SERVER_ID, false).apply()
                    hasServerId = false
                }
            }

            if (!synced && (!hasServerId || !prefs.getBoolean(KEY_HAS_SERVER_ID, false))) {
                try {
                    val postUrl = URL(endpoint)
                    val postConn = (postUrl.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                        setRequestProperty("Accept", "application/json")
                        doOutput = true
                        connectTimeout = 8000
                        readTimeout = 8000
                    }
                    val postPayloadData = if (is404) myData.copy(broId = "") else myData
                    val apiObj = RestfulApiObject(
                        id = null,
                        name = "STRAIN_BRO",
                        data = postPayloadData
                    )
                    val body = json.encodeToString(apiObj)
                    OutputStreamWriter(postConn.outputStream, "UTF-8").use {
                        it.write(body)
                        it.flush()
                    }

                    val code = postConn.responseCode
                    if (code in 200..299) {
                        val responseText = postConn.inputStream.bufferedReader().use { it.readText() }
                        val resp = try { json.decodeFromString<CreateResponse>(responseText) } catch (_: Exception) { null }
                        if (!resp?.id.isNullOrEmpty()) {
                            val newId = resp!!.id!!
                            myBroId = newId
                            prefs.edit()
                                .putString(KEY_MY_ID, myBroId)
                                .putBoolean(KEY_HAS_SERVER_ID, true)
                                .apply()
                            synced = true

                            // Cleanly eliminate any temporary ID mismatch in local buddy caches if oldId was present
                            if (oldId.isNotBlank() && oldId != newId) {
                                if (buddyIds.contains(oldId)) {
                                    val updatedIds = buddyIds.map { if (it == oldId) newId else it }
                                    saveBuddyIds(updatedIds)
                                }
                                if (buddies.any { it.broId == oldId }) {
                                    val updatedBuddies = buddies.map { if (it.broId == oldId) it.copy(broId = newId) else it }
                                    saveCachedBuddies(updatedBuddies)
                                }
                            }

                            // Update remote object with the new server ID inside data.broId so buddies receive the correct ID cleanly
                            try {
                                val putUrl = URL("$endpoint/$newId")
                                val putConn = (putUrl.openConnection() as HttpURLConnection).apply {
                                    requestMethod = "PUT"
                                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                                    setRequestProperty("Accept", "application/json")
                                    doOutput = true
                                    connectTimeout = 8000
                                    readTimeout = 8000
                                }
                                val updatedObj = RestfulApiObject(
                                    id = newId,
                                    name = "STRAIN_BRO",
                                    data = myData.copy(broId = newId)
                                )
                                val putBody = json.encodeToString(updatedObj)
                                OutputStreamWriter(putConn.outputStream, "UTF-8").use {
                                    it.write(putBody)
                                    it.flush()
                                }
                                putConn.responseCode
                                putConn.disconnect()
                            } catch (_: Exception) {
                            }
                        }
                    }
                    postConn.disconnect()
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        } finally {
            isSyncing = false
        }
    }

    suspend fun addBuddy(rawCode: String): AddBuddyResult = withContext(Dispatchers.IO) {
        val trimmed = rawCode.trim()
        if (trimmed.isEmpty()) return@withContext AddBuddyResult(false, "", "Код пуст")

        // 1. Проверяем URL-параметры (офлайн / прямой обмен)
        if (trimmed.contains("?") && (trimmed.contains("name=") || trimmed.contains("kind="))) {
            val urlToParse = if (trimmed.startsWith("strain://")) trimmed.replace("strain://", "https://strain.app/") else trimmed
            val uri = try { android.net.Uri.parse(urlToParse) } catch (_: Exception) { null }
            if (uri != null) {
                val id = uri.getQueryParameter("id") ?: ("bro_" + UUID.randomUUID().toString().take(8).lowercase())
                if (id == myBroId) {
                    return@withContext AddBuddyResult(false, "", "Это твой собственный QR-код!")
                }

                // Автоматически подхватываем сервер друга, если передан в QR-коде
                val srv = uri.getQueryParameter("srv")
                if (!srv.isNullOrBlank() && srv.startsWith("http")) {
                    setBackendUrl(srv)
                }

                val name = uri.getQueryParameter("name") ?: "Бро"
                val kindStr = uri.getQueryParameter("kind") ?: "TEXAS"
                val week = uri.getQueryParameter("w")?.toIntOrNull() ?: 1
                val day = uri.getQueryParameter("d")?.toIntOrNull() ?: 1
                val squat = uri.getQueryParameter("sq")?.toDoubleOrNull() ?: 100.0
                val bench = uri.getQueryParameter("bp")?.toDoubleOrNull() ?: 100.0
                val deadlift = uri.getQueryParameter("dl")?.toDoubleOrNull() ?: 100.0

                val kind = try {
                    com.texasprogram.app.model.TrainingProgramKind.valueOf(kindStr)
                } catch (_: Exception) {
                    com.texasprogram.app.model.TrainingProgramKind.TEXAS
                }

                val back = uri.getQueryParameter("back")
                val press = uri.getQueryParameter("press")
                val pull = uri.getQueryParameter("pull")
                val arms = uri.getQueryParameter("arms")
                val core = uri.getQueryParameter("core")

                val buddy = BroProfileData(
                    broId = id,
                    name = name,
                    programKind = kind.name,
                    programTitle = kind.title,
                    currentWeek = week,
                    currentDay = day,
                    lastActiveEpoch = System.currentTimeMillis(),
                    squat5RM = squat,
                    bench5RM = bench,
                    deadlift5RM = deadlift,
                    recentLifts = generatePreviewLifts(squat, bench, deadlift),
                    programDays = generatePreviewDays(kind, squat, bench, deadlift, back, press, pull, arms, core)
                )

                val newIds = if (buddyIds.contains(id)) buddyIds else buddyIds + id
                saveBuddyIds(newIds)

                val updatedBuddies = listOf(buddy) + buddies.filterNot { it.broId == id }
                saveCachedBuddies(updatedBuddies)

                // Сразу пытаемся обновить данные с сервера (полная программа, подсобные, актуальные веса)
                try { refreshBuddiesInternal() } catch (_: Exception) {}

                return@withContext AddBuddyResult(true, name, "Бро «$name» успешно добавлен в банду! 🤝")
            }
        }

        // 2. Если передан чистый ID или ссылка strain://bro/<id>
        var cleanId = trimmed
        if (cleanId.contains("/bro/")) {
            cleanId = cleanId.substringAfter("/bro/")
        } else if (cleanId.contains("bro=")) {
            cleanId = cleanId.substringAfter("bro=")
        }
        cleanId = cleanId.removePrefix("strain://").trim()

        if (cleanId.isEmpty()) {
            return@withContext AddBuddyResult(false, "", "Неверный формат ссылки или кода")
        }
        if (cleanId == myBroId) {
            return@withContext AddBuddyResult(false, "", "Это твой собственный код бро!")
        }

        val fetched = fetchBuddy(cleanId)
        if (fetched != null) {
            val newIds = if (buddyIds.contains(cleanId)) buddyIds else buddyIds + cleanId
            saveBuddyIds(newIds)

            val updatedBuddies = listOf(fetched) + buddies.filterNot { it.broId == cleanId }
            saveCachedBuddies(updatedBuddies)
            AddBuddyResult(true, fetched.name, "Бро «${fetched.name}» успешно добавлен в банду! 🤝")
        } else {
            AddBuddyResult(false, "", "Не удалось найти бро с ID «$cleanId». Проверь подключение к интернету.")
        }
    }

    private fun generatePreviewDays(
        kind: com.texasprogram.app.model.TrainingProgramKind,
        squat: Double,
        bench: Double,
        deadlift: Double,
        back: String? = null,
        press: String? = null,
        pull: String? = null,
        arms: String? = null,
        core: String? = null
    ): List<BroWorkoutDay> {
        val dummy = ProgramProfile(
            name = "Preview",
            programKind = kind,
            squat5RM = if (squat > 0) squat else 100.0,
            bench5RM = if (bench > 0) bench else 100.0,
            deadlift5RM = if (deadlift > 0) deadlift else 100.0,
            level = com.texasprogram.app.model.TrainingLevel.BEGINNER,
            back = back,
            press = press,
            pull = pull,
            arms = arms,
            core = core
        )
        val plan = dummy.workoutPlan
        val days = ArrayList<BroWorkoutDay>()
        for (week in plan.weeks) {
            for (day in week.days) {
                val exs = day.exercises.map {
                    BroExercise(it.name, it.sets, it.reps, it.load.displayText)
                }
                days.add(BroWorkoutDay(week.number, day.number, day.title, exs))
            }
        }
        return days
    }

    private fun generatePreviewLifts(squat: Double, bench: Double, deadlift: Double): List<BroLiftEntry> {
        return listOf(
            BroLiftEntry("Присед 5ПМ", "${squat.toInt()} кг"),
            BroLiftEntry("Жим 5ПМ", "${bench.toInt()} кг"),
            BroLiftEntry("Тяга 5ПМ", "${deadlift.toInt()} кг")
        )
    }

    fun removeBuddy(id: String) {
        val newIds = buddyIds.filterNot { it == id }
        saveBuddyIds(newIds)
        val updated = buddies.filterNot { it.broId == id }
        prefs.edit().remove("strain_buddy_alias_$id").apply()
        saveCachedBuddies(updated)
    }

    /// Позволяет локально переименовать бро (задать псевдоним)
    fun renameBuddy(id: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        val updated = buddies.map {
            if (it.broId == id) it.copy(name = trimmed) else it
        }
        buddies = updated
        prefs.edit().putString("strain_buddy_alias_$id", trimmed).apply()
        saveCachedBuddies(updated)
    }

    suspend fun refreshBuddies() = withContext(Dispatchers.IO) {
        refreshBuddiesInternal()
    }

    private fun refreshBuddiesInternal() {
        if (buddyIds.isEmpty()) return
        isSyncing = true
        try {
            val updated = ArrayList<BroProfileData>()
            for (id in buddyIds) {
                val fresh = fetchBuddy(id)
                if (fresh != null) {
                    updated.add(fresh)
                } else {
                    val cached = buddies.firstOrNull { it.broId == id }
                    if (cached != null) updated.add(cached)
                }
            }
            saveCachedBuddies(updated)
        } catch (_: Exception) {
        } finally {
            isSyncing = false
        }
    }

    private fun applyAliasIfPresent(buddy: BroProfileData, id: String): BroProfileData {
        val alias = prefs.getString("strain_buddy_alias_$id", null)
        return if (!alias.isNullOrBlank()) buddy.copy(name = alias) else buddy
    }

    private fun fetchBuddy(id: String): BroProfileData? {
        // 1. Попытка загрузить с выделенного бэкенда STRAIN
        try {
            val bUrl = URL("$backendBaseUrl/api/profile/$id")
            val bConn = (bUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
            }
            if (bConn.responseCode in 200..299) {
                val raw = bConn.inputStream.bufferedReader().use { it.readText() }
                val data = json.decodeFromString<BroProfileData>(raw)
                val serverId = if (data.broId.isNotBlank()) data.broId else id
                bConn.disconnect()
                return applyAliasIfPresent(data.copy(broId = serverId), id)
            }
            bConn.disconnect()
        } catch (_: Exception) {}

        // 2. Fallback на restful-api.dev
        return try {
            val url = URL("https://api.restful-api.dev/objects/$id")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            if (conn.responseCode in 200..299) {
                val raw = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = json.decodeFromString<RestfulApiObject>(raw)
                val data = obj.data
                if (data != null) {
                    val serverId = if (!obj.id.isNullOrEmpty()) obj.id!! else id
                    applyAliasIfPresent(data.copy(broId = serverId), id)
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    // MARK: - Chat Management

    fun channelId(buddyId: String): String {
        val ids = listOf(myBroId, buddyId).sorted()
        return "chat_${ids[0]}_${ids[1]}"
    }

    fun loadLocalMessages(channelId: String): List<BroChatMessage> {
        val raw = prefs.getString("strain_chat_$channelId", null) ?: return emptyList()
        return try {
            json.decodeFromString<List<BroChatMessage>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveLocalMessages(messages: List<BroChatMessage>, channelId: String) {
        try {
            val raw = json.encodeToString(messages)
            prefs.edit().putString("strain_chat_$channelId", raw).apply()
        } catch (_: Exception) {}
    }

    fun loadOutbox(): List<BroChatMessage> {
        val raw = prefs.getString(KEY_OUTBOX, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<BroChatMessage>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveOutbox(messages: List<BroChatMessage>) {
        try {
            val raw = json.encodeToString(messages)
            prefs.edit().putString(KEY_OUTBOX, raw).apply()
        } catch (_: Exception) {}
    }

    /// Загружает свежие сообщения из облачного канала выделенного бэкенда
    suspend fun fetchRemoteMessages(buddyId: String) = withContext(Dispatchers.IO) {
        val chId = channelId(buddyId)
        try {
            val url = URL("$backendBaseUrl/api/chat/$chId/messages")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }
            if (conn.responseCode in 200..299) {
                val raw = conn.inputStream.bufferedReader().use { it.readText() }
                val serverMsgs = json.decodeFromString<List<BroChatMessage>>(raw)
                val local = loadLocalMessages(chId).toMutableList()
                val existingIds = local.map { it.id }.toSet()
                var hasNew = false
                for (m in serverMsgs) {
                    if (m.id !in existingIds) {
                        local.add(m)
                        hasNew = true
                    }
                }
                if (hasNew) {
                    local.sortBy { it.timestamp }
                    saveLocalMessages(local, chId)
                }
            }
            conn.disconnect()
        } catch (_: Exception) {}
    }

    fun getMessages(buddyId: String): List<BroChatMessage> {
        val chId = channelId(buddyId)
        val local = loadLocalMessages(chId).toMutableList()
        val buddy = buddies.firstOrNull { it.broId == buddyId }
        if (buddy != null) {
            val buddyMsgs = buddy.recentChatMessages.filter { it.channelId == chId }
            val existingIds = local.map { it.id }.toSet()
            for (msg in buddyMsgs) {
                if (msg.id !in existingIds) {
                    local.add(msg)
                }
            }
            local.sortBy { it.timestamp }
            saveLocalMessages(local, chId)
        }
        return local
    }

    suspend fun sendMessage(
        toBuddyId: String,
        text: String,
        type: BroMessageType = BroMessageType.TEXT,
        workoutPayload: WorkoutSharePayload? = null,
        photoBase64: String? = null,
        profile: ProgramProfile? = null
    ): BroChatMessage = withContext(Dispatchers.IO) {
        val chId = channelId(toBuddyId)
        val senderName = profile?.name?.ifBlank { "Бро" } ?: "Бро"
        val msg = BroChatMessage(
            channelId = chId,
            senderId = myBroId,
            senderName = senderName,
            timestamp = System.currentTimeMillis(),
            text = text,
            type = type,
            workoutPayload = workoutPayload,
            photoBase64 = photoBase64
        )

        val current = loadLocalMessages(chId).toMutableList()
        current.add(msg)
        saveLocalMessages(current, chId)

        val outbox = loadOutbox().toMutableList()
        outbox.add(msg)
        if (outbox.size > 25) {
            while (outbox.size > 25) {
                outbox.removeAt(0)
            }
        }
        saveOutbox(outbox)

        // 1. Прямая отправка в облачный канал бэкенда
        try {
            val chatUrl = URL("$backendBaseUrl/api/chat/$chId/messages")
            val chatConn = (chatUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                connectTimeout = 8000
                readTimeout = 8000
            }
            val body = json.encodeToString(msg)
            OutputStreamWriter(chatConn.outputStream, "UTF-8").use {
                it.write(body)
                it.flush()
            }
            chatConn.responseCode
            chatConn.disconnect()
        } catch (_: Exception) {}

        // 2. Обновление профиля
        if (profile != null) {
            syncMyProfile(profile)
        }
        msg
    }

    companion object {
        private const val KEY_OUTBOX = "strain_my_outbox"
        private const val KEY_MY_ID = "my_bro_id"
        private const val KEY_BUDDY_IDS = "buddy_ids"
        private const val KEY_CACHED_BUDDIES = "cached_buddies"
        private const val KEY_HAS_SERVER_ID = "has_server_id"

        @Volatile
        private var instance: BroTrackerService? = null

        fun getInstance(context: Context): BroTrackerService {
            return instance ?: synchronized(this) {
                instance ?: BroTrackerService(context.applicationContext).also { instance = it }
            }
        }
    }
}
