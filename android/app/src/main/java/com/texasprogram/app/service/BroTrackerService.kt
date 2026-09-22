package com.texasprogram.app.service

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.texasprogram.app.model.LoadPrescription
import com.texasprogram.app.model.ProgramProfile
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

@Serializable
data class BroLiftEntry(
    val name: String,
    val prescription: String
)

@Serializable
data class BroExercise(
    val name: String,
    val sets: Int,
    val reps: String,
    val weight: String
)

@Serializable
data class BroWorkoutDay(
    val week: Int,
    val day: Int,
    val title: String,
    val exercises: List<BroExercise>
)

@Serializable
data class BroProfileData(
    val broId: String,
    val name: String,
    val programKind: String,
    val programTitle: String,
    val currentWeek: Int,
    val currentDay: Int,
    val lastActiveEpoch: Long,
    val squat5RM: Double,
    val bench5RM: Double,
    val deadlift5RM: Double,
    val recentLifts: List<BroLiftEntry> = emptyList(),
    val programDays: List<BroWorkoutDay> = emptyList(),
    val rawProgramJson: String? = null
) {
    val statusDescription: String
        get() {
            val diffSec = (System.currentTimeMillis() - lastActiveEpoch) / 1000
            return when {
                diffSec < 3600 -> "Только что тренировался"
                diffSec < 86400 -> {
                    val hours = maxOf(1, diffSec / 3600)
                    "Тренировался $hours ч. назад"
                }
                else -> {
                    val days = diffSec / 86400
                    "Был $days дн. назад"
                }
            }
        }

    val isRecentlyActive: Boolean
        get() {
            val diffSec = (System.currentTimeMillis() - lastActiveEpoch) / 1000
            return diffSec < 86400 * 2
        }
}

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
        prefs.edit().putString(KEY_MY_ID, newId).apply()
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
        val day = 1
        val sq = profile.squat5RM.toInt()
        val bp = profile.bench5RM.toInt()
        val dl = profile.deadlift5RM.toInt()

        return "strain://bro?id=$myBroId&name=$encodedName&kind=$kind&w=$week&d=$day&sq=$sq&bp=$bp&dl=$dl"
    }

    suspend fun syncMyProfile(profile: ProgramProfile) = withContext(Dispatchers.IO) {
        isSyncing = true
        try {
            val plan = profile.workoutPlan
            val curWeek = profile.currentWeek
            val curWeekPlan = plan.weeks.firstOrNull { it.number == curWeek }
            val nextDay = curWeekPlan?.days?.firstOrNull { !profile.isCompleted(curWeek, it.number) }

            val lifts = ArrayList<BroLiftEntry>()
            val activeDay = nextDay ?: curWeekPlan?.days?.firstOrNull()
            if (activeDay != null) {
                for (ex in activeDay.exercises.take(3)) {
                    val load = ex.load.displayText
                    val presc = if (ex.sets > 0) "${ex.sets}×${ex.reps} · $load" else "${ex.reps} · $load"
                    lifts.add(BroLiftEntry(ex.name, presc))
                }
            }

            val programDays = ArrayList<BroWorkoutDay>()
            for (week in plan.weeks.take(4)) {
                for (day in week.days) {
                    val exs = day.exercises.map {
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
                currentWeek = profile.currentWeek,
                currentDay = nextDay?.number ?: 1,
                lastActiveEpoch = System.currentTimeMillis(),
                squat5RM = profile.squat5RM,
                bench5RM = profile.bench5RM,
                deadlift5RM = profile.deadlift5RM,
                recentLifts = lifts,
                programDays = programDays
            )

            val endpoint = "https://api.restful-api.dev/objects"
            val isNew = !myBroId.startsWith("ff80")
            val urlString = if (isNew) endpoint else "$endpoint/$myBroId"
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = if (isNew) "POST" else "PUT"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            val apiObj = RestfulApiObject(
                id = if (isNew) null else myBroId,
                name = "STRAIN_BRO",
                data = myData
            )
            val body = json.encodeToString(apiObj)

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(body)
                it.flush()
            }

            val code = conn.responseCode
            if (code in 200..299) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val resp = try { json.decodeFromString<CreateResponse>(responseText) } catch (_: Exception) { null }
                if (!resp?.id.isNullOrEmpty()) {
                    myBroId = resp!!.id!!
                    prefs.edit().putString(KEY_MY_ID, myBroId).apply()
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
                    programDays = generatePreviewDays(kind, squat, bench, deadlift)
                )

                val newIds = if (buddyIds.contains(id)) buddyIds else buddyIds + id
                saveBuddyIds(newIds)

                val updatedBuddies = listOf(buddy) + buddies.filterNot { it.broId == id }
                saveCachedBuddies(updatedBuddies)

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
        deadlift: Double
    ): List<BroWorkoutDay> {
        val dummy = ProgramProfile(
            name = "Preview",
            programKind = kind,
            squat5RM = if (squat > 0) squat else 100.0,
            bench5RM = if (bench > 0) bench else 100.0,
            deadlift5RM = if (deadlift > 0) deadlift else 100.0,
            level = com.texasprogram.app.model.TrainingLevel.BEGINNER
        )
        val plan = dummy.workoutPlan
        val days = ArrayList<BroWorkoutDay>()
        for (week in plan.weeks.take(4)) {
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
        saveCachedBuddies(updated)
    }

    suspend fun refreshBuddies() = withContext(Dispatchers.IO) {
        if (buddyIds.isEmpty()) return@withContext
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

    private fun fetchBuddy(id: String): BroProfileData? {
        return try {
            val url = URL("https://api.restful-api.dev/objects/$id")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            if (conn.responseCode in 200..299) {
                val raw = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = json.decodeFromString<RestfulApiObject>(raw)
                obj.data
            } else null
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val KEY_MY_ID = "my_bro_id"
        private const val KEY_BUDDY_IDS = "buddy_ids"
        private const val KEY_CACHED_BUDDIES = "cached_buddies"
    }
}
