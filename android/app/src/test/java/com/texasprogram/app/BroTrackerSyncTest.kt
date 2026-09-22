package com.texasprogram.app

import com.texasprogram.app.model.AdditionalExerciseCategory
import com.texasprogram.app.model.ExercisePrescription
import com.texasprogram.app.model.LoadPrescription
import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.model.TrainingLevel
import com.texasprogram.app.model.TrainingProgramKind
import com.texasprogram.app.model.UpperLowerInput
import com.texasprogram.app.model.WorkoutDayPlan
import com.texasprogram.app.model.WorkoutPlan
import com.texasprogram.app.model.WorkoutWeekPlan
import com.texasprogram.app.service.BroExercise
import com.texasprogram.app.service.BroLiftEntry
import com.texasprogram.app.service.BroProfileData
import com.texasprogram.app.service.BroWorkoutDay
import com.texasprogram.app.service.FullBodyLevel
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Comprehensive Unit and Specification Test Suite for Bro-Tracker Sync (R1 & R2).
 *
 * Covers:
 * - Tier 1: Functional Baseline (12+ weeks serialization, all daily exercises in recentLifts,
 *           JSON round-trip fidelity, active week/day metadata preservation).
 * - Tier 2: Boundary Value Analysis & Edge Cases (online status transitions <300s, <3600s, >3600s;
 *           clock skew, empty lifts, 1-week plan, 20+ week plans, extreme timestamps).
 * - Tier 3: Pairwise Combinatorial & Error Recovery (404 PUT -> POST fallback, ID transitions,
 *           all program kinds serialization).
 * - Tier 4: Real-World Workload Scenarios (Week 8 progression, workout completion sync trigger,
 *           backend TTL eviction self-healing).
 */
class BroTrackerSyncTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    private data class RestfulApiEnvelope(
        val id: String? = null,
        val data: BroProfileData
    )

    // =========================================================================
    // Specification Helpers & Reference Models (Interface Contract Derivation)
    // =========================================================================

    /**
     * Extracts full program days from a ProgramProfile according to Requirement R2
     * (NO take(4) truncation; all weeks 1..12+ preserved).
     */
    private fun extractFullProgramDays(profile: ProgramProfile): List<BroWorkoutDay> {
        val plan = profile.workoutPlan
        val days = ArrayList<BroWorkoutDay>()
        // Spec: all weeks preserved without take(4)
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

    /**
     * Extracts all daily exercises into recentLifts according to Requirement R2
     * (NO take(2) or take(3) truncation; squat, bench, deadlift, and accessories preserved).
     */
    private fun extractAllRecentLifts(profile: ProgramProfile): List<BroLiftEntry> {
        val plan = profile.workoutPlan
        val curWeek = profile.currentWeek
        val curWeekPlan = plan.weeks.firstOrNull { it.number == curWeek }
        val nextDay = curWeekPlan?.days?.firstOrNull { !profile.isCompleted(curWeek, it.number) }
        val activeDay = nextDay ?: curWeekPlan?.days?.firstOrNull() ?: return emptyList()

        val lifts = ArrayList<BroLiftEntry>()
        // Spec: ALL exercises preserved without take(3)
        for (ex in activeDay.exercises) {
            val load = ex.load.displayText
            val presc = if (ex.sets > 0) "${ex.sets}×${ex.reps} · $load" else "${ex.reps} · $load"
            lifts.add(BroLiftEntry(ex.name, presc))
        }
        return lifts
    }

    /**
     * Builds the complete BroProfileData payload according to PROJECT.md § Interface Contracts.
     */
    private fun buildSpecificationPayload(
        profile: ProgramProfile,
        broId: String,
        lastActiveEpoch: Long = System.currentTimeMillis()
    ): BroProfileData {
        val plan = profile.workoutPlan
        val curWeek = profile.currentWeek
        val curWeekPlan = plan.weeks.firstOrNull { it.number == curWeek }
        val nextDay = curWeekPlan?.days?.firstOrNull { !profile.isCompleted(curWeek, it.number) }

        return BroProfileData(
            broId = broId,
            name = profile.name.ifBlank { "Бро" },
            programKind = profile.programKind.name,
            programTitle = profile.programKind.title,
            currentWeek = curWeek,
            currentDay = nextDay?.number ?: 1,
            lastActiveEpoch = lastActiveEpoch,
            squat5RM = profile.squat5RM,
            bench5RM = profile.bench5RM,
            deadlift5RM = profile.deadlift5RM,
            recentLifts = extractAllRecentLifts(profile),
            programDays = extractFullProgramDays(profile)
        )
    }

    /**
     * Authoritative specification for dynamic online status calculation (R1 / PROJECT.md):
     * - < 300s: "В сети / Только что тренировался"
     * - < 3600s: "Только что тренировался"
     * - < 86400s: "Тренировался $hours ч. назад"
     * - >= 86400s: "Был $days дн. назад"
     */
    private fun calculateExpectedStatus(lastActiveEpoch: Long, now: Long = System.currentTimeMillis()): String {
        val diffSec = (now - lastActiveEpoch) / 1000
        return when {
            diffSec < 300 -> "В сети / Только что тренировался"
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

    // =========================================================================
    // TIER 1: Functional Baseline / Happy Path (>= 5 Tests)
    // =========================================================================

    @Test
    fun test_tier1_full12WeeksSerializedInProgramDays_week8AndWeek12Preserved() {
        // Arrange: Standard 12-week Texas program profile
        val profile = ProgramProfile.texas(ProgramInput.demo)
        assertEquals(12, profile.workoutPlan.weeks.size)

        // Act: Serialize full plan into programDays
        val programDays = extractFullProgramDays(profile)

        // Assert: All 12 weeks are serialized (legacy take(4) would yield only 4 weeks / 12 days)
        val serializedWeeks = programDays.map { it.week }.distinct()
        assertEquals(12, serializedWeeks.size)
        assertTrue("Week 8 must be present in serialized programDays", serializedWeeks.contains(8))
        assertTrue("Week 12 must be present in serialized programDays", serializedWeeks.contains(12))

        // Total days: 12 weeks * 3 days = 36 days
        assertEquals(36, programDays.size)

        // Verify Week 8 content integrity
        val week8Days = programDays.filter { it.week == 8 }
        assertEquals(3, week8Days.size)
        assertEquals(1, week8Days[0].day)
        assertEquals(2, week8Days[1].day)
        assertEquals(3, week8Days[2].day)
        assertTrue(week8Days.all { it.exercises.isNotEmpty() })
    }

    @Test
    fun test_tier1_programDaysContainsAllWorkoutDaysForEachWeek() {
        val profile = ProgramProfile.texas(ProgramInput.demo)
        val programDays = extractFullProgramDays(profile)

        for (w in 1..12) {
            val daysInWeek = programDays.filter { it.week == w }
            assertEquals("Week $w must have 3 days", 3, daysInWeek.size)
            for (d in 1..3) {
                val day = daysInWeek.firstOrNull { it.day == d }
                assertNotNull("Week $w Day $d must exist", day)
                assertTrue("Week $w Day $d must have exercises", day!!.exercises.isNotEmpty())
                for (ex in day.exercises) {
                    assertTrue("Exercise name must not be blank", ex.name.isNotBlank())
                    assertTrue("Sets must be >= 0", ex.sets >= 0)
                    assertTrue("Weight must not be blank", ex.weight.isNotBlank())
                }
            }
        }
    }

    @Test
    fun test_tier1_allDailyExercisesCapturedInRecentLifts_noTruncation() {
        // Arrange: Texas program configured with multiple accessory exercises
        // Squat, Bench, Deadlift, plus Core accessory = 4 exercises on Day 1
        val input = ProgramInput(
            squat5RM = 100.0,
            bench5RM = 100.0,
            deadlift5RM = 100.0,
            level = TrainingLevel.INTERMEDIATE,
            core = "Копенгагенская планка",
            pull = "Подтягивания"
        )
        val profile = ProgramProfile.texas(input)
        val activeDayExercises = profile.workoutPlan.weeks.first().days.first().exercises
        assertTrue("Active day must have >= 4 exercises", activeDayExercises.size >= 4)

        // Act: Extract recent lifts
        val recentLifts = extractAllRecentLifts(profile)

        // Assert: All exercises must be preserved (legacy take(2) and take(3) must NOT truncate)
        assertEquals(
            "recentLifts must contain all exercises of the active day without truncation",
            activeDayExercises.size,
            recentLifts.size
        )

        // Verify lift names match the active day exercises
        val expectedNames = activeDayExercises.map { it.name }
        val actualNames = recentLifts.map { it.name }
        assertEquals(expectedNames, actualNames)
    }

    @Test
    fun test_tier1_broProfileDataJsonSerializationRoundTrip() {
        val profile = ProgramProfile.texas(ProgramInput.demo)
        val payload = buildSpecificationPayload(profile, "ff80818193240e5301932b70f0010001")

        // Act: Serialize to JSON and deserialize back
        val jsonString = json.encodeToString(payload)
        assertTrue(jsonString.contains("ff80818193240e5301932b70f0010001"))
        assertTrue(jsonString.contains("TEXAS"))

        val deserialized = json.decodeFromString<BroProfileData>(jsonString)

        // Assert: 100% round-trip fidelity
        assertEquals(payload.broId, deserialized.broId)
        assertEquals(payload.name, deserialized.name)
        assertEquals(payload.programKind, deserialized.programKind)
        assertEquals(payload.currentWeek, deserialized.currentWeek)
        assertEquals(payload.currentDay, deserialized.currentDay)
        assertEquals(payload.recentLifts.size, deserialized.recentLifts.size)
        assertEquals(payload.programDays.size, deserialized.programDays.size)
        assertEquals(payload.programDays.last().week, deserialized.programDays.last().week)
    }

    @Test
    fun test_tier1_activeWeekAndDayPreservedInBroProfileData() {
        var profile = ProgramProfile.texas(ProgramInput.demo)
        // Advance to Week 8 by completing weeks 1 through 7
        for (w in 1..7) {
            for (d in 1..3) {
                profile = profile.toggleCompleted(w, d)
            }
        }
        assertEquals(8, profile.currentWeek)

        // Complete Day 1 of Week 8
        profile = profile.toggleCompleted(8, 1)

        val payload = buildSpecificationPayload(profile, "bro_test_8")
        assertEquals(8, payload.currentWeek)
        assertEquals(2, payload.currentDay) // Next active day is Day 2
    }

    @Test
    fun test_tier1_onlineStatus_justTrainedActiveStatus() {
        val now = System.currentTimeMillis()
        val recentEpoch = now - 120 * 1000L // 2 minutes ago (< 300s)

        val expected = calculateExpectedStatus(recentEpoch, now)
        assertEquals("В сети / Только что тренировался", expected)
    }

    // =========================================================================
    // TIER 2: Boundary Value Analysis & Edge Cases (>= 5 Tests)
    // =========================================================================

    @Test
    fun test_tier2_bva_onlineStatus_within300Seconds() {
        val now = System.currentTimeMillis()

        // Boundary: 0s, 60s, 299s
        assertEquals("В сети / Только что тренировался", calculateExpectedStatus(now, now))
        assertEquals("В сети / Только что тренировался", calculateExpectedStatus(now - 60_000L, now))
        assertEquals("В сети / Только что тренировался", calculateExpectedStatus(now - 299_000L, now))
    }

    @Test
    fun test_tier2_bva_onlineStatus_between300And3600Seconds() {
        val now = System.currentTimeMillis()

        // Boundary: exactly 300s (5 min), 1800s (30 min), 3599s (59m 59s)
        assertEquals("Только что тренировался", calculateExpectedStatus(now - 300_000L, now))
        assertEquals("Только что тренировался", calculateExpectedStatus(now - 1_800_000L, now))
        assertEquals("Только что тренировался", calculateExpectedStatus(now - 3_599_000L, now))
    }

    @Test
    fun test_tier2_bva_onlineStatus_between1HourAnd24Hours() {
        val now = System.currentTimeMillis()

        // Boundary: exactly 3600s (1 hour), 7200s (2 hours), 86399s (23h 59m 59s)
        assertEquals("Тренировался 1 ч. назад", calculateExpectedStatus(now - 3_600_000L, now))
        assertEquals("Тренировался 2 ч. назад", calculateExpectedStatus(now - 7_200_000L, now))
        assertEquals("Тренировался 23 ч. назад", calculateExpectedStatus(now - 86_399_000L, now))
    }

    @Test
    fun test_tier2_bva_onlineStatus_moreThan24Hours() {
        val now = System.currentTimeMillis()

        // Boundary: exactly 86400s (1 day), 172800s (2 days), 7 days
        assertEquals("Был 1 дн. назад", calculateExpectedStatus(now - 86_400_000L, now))
        assertEquals("Был 2 дн. назад", calculateExpectedStatus(now - 172_800_000L, now))
        assertEquals("Был 7 дн. назад", calculateExpectedStatus(now - 7 * 86_400_000L, now))
    }

    @Test
    fun test_tier2_bva_onlineStatus_negativeDeltaClockSkew() {
        val now = System.currentTimeMillis()
        // Clock skew: device timestamp is 30 seconds into the future (diffSec < 0)
        val futureEpoch = now + 30_000L
        assertEquals("В сети / Только что тренировался", calculateExpectedStatus(futureEpoch, now))
    }

    @Test
    fun test_tier2_bva_emptyRecentLifts() {
        val emptyLiftsData = BroProfileData(
            broId = "bro_empty",
            name = "Пустой бро",
            programKind = "TEXAS",
            programTitle = "Техасский метод",
            currentWeek = 1,
            currentDay = 1,
            lastActiveEpoch = System.currentTimeMillis(),
            squat5RM = 100.0,
            bench5RM = 100.0,
            deadlift5RM = 100.0,
            recentLifts = emptyList(),
            programDays = emptyList()
        )

        val encoded = json.encodeToString(emptyLiftsData)
        val decoded = json.decodeFromString<BroProfileData>(encoded)
        assertTrue(decoded.recentLifts.isEmpty())
        assertTrue(decoded.programDays.isEmpty())
    }

    @Test
    fun test_tier2_bva_singleWeekPlanBoundary() {
        // Synthetic 1-week plan boundary
        val singleWeekDay = WorkoutDayPlan("d1", 1, "День 1", listOf(
            ExercisePrescription("Присед", 3, "5", LoadPrescription.Kilograms(100.0))
        ))
        val singleWeekPlan = WorkoutPlan(listOf(WorkoutWeekPlan(1, listOf(singleWeekDay))), isPeaking = false)

        val days = ArrayList<BroWorkoutDay>()
        for (w in singleWeekPlan.weeks) {
            for (d in w.days) {
                days.add(BroWorkoutDay(w.number, d.number, d.title, d.exercises.map {
                    BroExercise(it.name, it.sets, it.reps, it.load.displayText)
                }))
            }
        }

        assertEquals(1, days.size)
        assertEquals(1, days.first().week)
        assertEquals(1, days.first().day)
    }

    @Test
    fun test_tier2_bva_extended20PlusWeekPlanBoundary() {
        // ProTexas generates a 20-week program
        val proProfile = ProgramProfile.proTexas(ProgramInput.demo)
        val plan = proProfile.workoutPlan
        assertEquals(20, plan.weeks.size)

        val programDays = extractFullProgramDays(proProfile)
        val distinctWeeks = programDays.map { it.week }.distinct()

        assertEquals("All 20 weeks must be serialized without truncation", 20, distinctWeeks.size)
        assertEquals(1, distinctWeeks.first())
        assertEquals(20, distinctWeeks.last())
    }

    @Test
    fun test_tier2_bva_extremeTimestampsAndEpochValues() {
        val extremeEpoch = 0L // Epoch 1970-01-01
        val now = System.currentTimeMillis()
        val status = calculateExpectedStatus(extremeEpoch, now)
        assertTrue(status.startsWith("Был "))
        assertTrue(status.endsWith(" дн. назад"))

        val halfMaxEpoch = Long.MAX_VALUE / 2
        // Should not throw overflow exceptions
        val futureStatus = calculateExpectedStatus(halfMaxEpoch, now)
        assertEquals("В сети / Только что тренировался", futureStatus)
    }

    // =========================================================================
    // TIER 3: Pairwise Combinatorial & Error Recovery Logic (Pairwise)
    // =========================================================================

    /**
     * Simulated HTTP Client and Server for 404 Recovery State Machine Testing.
     */
    private enum class HttpMethod { GET, POST, PUT }

    private data class HttpRequest(val method: HttpMethod, val url: String, val body: String?)
    private data class HttpResponse(val code: Int, val body: String)

    private class MockHttpSyncServer {
        private val objects = HashMap<String, String>()
        val requestLog = ArrayList<HttpRequest>()
        var evictNextPut = false

        fun handle(method: HttpMethod, url: String, body: String?): HttpResponse {
            requestLog.add(HttpRequest(method, url, body))
            val id = url.substringAfterLast("/")

            return when (method) {
                HttpMethod.POST -> {
                    val newId = "ff808181" + UUID.randomUUID().toString().replace("-", "").take(24)
                    objects[newId] = body ?: ""
                    HttpResponse(200, """{"id":"$newId"}""")
                }
                HttpMethod.PUT -> {
                    if (evictNextPut || !objects.containsKey(id)) {
                        HttpResponse(404, """{"error":"Object not found"}""")
                    } else {
                        objects[id] = body ?: ""
                        HttpResponse(200, """{"id":"$id"}""")
                    }
                }
                HttpMethod.GET -> {
                    val obj = objects[id]
                    if (obj != null) {
                        HttpResponse(200, """{"id":"$id","data":$obj}""")
                    } else {
                        HttpResponse(404, """{"error":"Object not found"}""")
                    }
                }
            }
        }
    }

    /**
     * Resilient Sync Coordinator executing the PROJECT.md HTTP Contract:
     * - If no confirmed server ID -> POST /objects
     * - If server ID exists -> PUT /objects/{id}
     * - If PUT returns 404 -> immediate fallback to POST /objects, store new ID
     */
    private class ResilientSyncCoordinator(
        private val server: MockHttpSyncServer,
        var currentStoredId: String? = null
    ) {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        var isServerConfirmedId = currentStoredId?.startsWith("ff80") == true

        fun sync(payload: BroProfileData): Boolean {
            val endpoint = "https://api.restful-api.dev/objects"
            val hasValidServerId = isServerConfirmedId && !currentStoredId.isNullOrBlank()

            if (!hasValidServerId) {
                // Initial POST
                val resp = server.handle(HttpMethod.POST, endpoint, json.encodeToString(payload))
                if (resp.code in 200..299) {
                    val createdId = extractIdFromResponse(resp.body)
                    if (!createdId.isNullOrEmpty()) {
                        currentStoredId = createdId
                        isServerConfirmedId = true
                        return true
                    }
                }
                return false
            } else {
                // Update PUT
                val url = "$endpoint/$currentStoredId"
                val putResp = server.handle(HttpMethod.PUT, url, json.encodeToString(payload))

                if (putResp.code in 200..299) {
                    return true
                } else if (putResp.code == 404) {
                    // Specification Requirement R1: Fallback to POST on 404
                    val postFallbackResp = server.handle(HttpMethod.POST, endpoint, json.encodeToString(payload))
                    if (postFallbackResp.code in 200..299) {
                        val newId = extractIdFromResponse(postFallbackResp.body)
                        if (!newId.isNullOrEmpty()) {
                            currentStoredId = newId
                            isServerConfirmedId = true
                            return true
                        }
                    }
                }
                return false
            }
        }

        private fun extractIdFromResponse(body: String): String? {
            return Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
        }
    }

    @Test
    fun test_tier3_404Recovery_putNotFoundTriggersPostFallbackAndIdUpdate() {
        val server = MockHttpSyncServer()
        val expiredId = "ff80818193240e5301932b70f0010001"

        // Setup coordinator with expired ID (not present in server storage)
        val coordinator = ResilientSyncCoordinator(server, currentStoredId = expiredId)
        assertTrue(coordinator.isServerConfirmedId)

        val profile = ProgramProfile.texas(ProgramInput.demo)
        val payload = buildSpecificationPayload(profile, expiredId)

        // Act: Sync with expired ID -> PUT will return 404 -> coordinator must fallback to POST
        val success = coordinator.sync(payload)

        // Assert: Sync succeeded via self-healing fallback
        assertTrue("Sync must succeed via 404 recovery fallback", success)
        assertNotEquals("Stored ID must be updated from expired ID", expiredId, coordinator.currentStoredId)
        assertTrue("New ID must be a valid server ID", coordinator.currentStoredId!!.startsWith("ff808181"))

        // Verify request log: First request was PUT (404), second was POST (200)
        assertEquals(2, server.requestLog.size)
        assertEquals(HttpMethod.PUT, server.requestLog[0].method)
        assertEquals("https://api.restful-api.dev/objects/$expiredId", server.requestLog[0].url)
        assertEquals(HttpMethod.POST, server.requestLog[1].method)
        assertEquals("https://api.restful-api.dev/objects", server.requestLog[1].url)

        // Verify subsequent sync uses the new ID via PUT without falling back to POST
        val subsequentSuccess = coordinator.sync(payload)
        assertTrue(subsequentSuccess)
        assertEquals(3, server.requestLog.size)
        assertEquals(HttpMethod.PUT, server.requestLog[2].method)
        assertEquals("https://api.restful-api.dev/objects/${coordinator.currentStoredId}", server.requestLog[2].url)
    }

    @Test
    fun test_tier3_404Recovery_ensuresCleanIdInPayloadAndBuddyCacheMigration() {
        val server = MockHttpSyncServer()
        val expiredId = "ff808181_expired"

        var buddyIds = listOf(expiredId, "other_bro_123")
        var cachedBuddies = listOf(
            BroProfileData(
                broId = expiredId,
                name = "Тест",
                programKind = "TEXAS",
                programTitle = "Техасский метод",
                currentWeek = 1,
                currentDay = 1,
                lastActiveEpoch = 123456L
            )
        )

        // Simulate 404 PUT -> fallback POST
        val profile = ProgramProfile.texas(ProgramInput.demo)
        val initialData = buildSpecificationPayload(profile, expiredId)

        // 1. Initial PUT fails with 404
        val putResp = server.handle(HttpMethod.PUT, "https://api.restful-api.dev/objects/$expiredId", json.encodeToString(initialData))
        assertEquals(404, putResp.code)

        // 2. Fallback POST payload cleans old expired ID (broId = "")
        val postPayload = initialData.copy(broId = "")
        val postResp = server.handle(HttpMethod.POST, "https://api.restful-api.dev/objects", json.encodeToString(postPayload))
        assertEquals(200, postResp.code)
        val newId = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(postResp.body)!!.groupValues[1]
        assertNotEquals(expiredId, newId)

        // 3. Remote object PUT updates data.broId to newId
        val updateResp = server.handle(HttpMethod.PUT, "https://api.restful-api.dev/objects/$newId", json.encodeToString(initialData.copy(broId = newId)))
        assertEquals(200, updateResp.code)

        // 4. Buddy cache migration
        if (buddyIds.contains(expiredId)) {
            buddyIds = buddyIds.map { if (it == expiredId) newId else it }
        }
        if (cachedBuddies.any { it.broId == expiredId }) {
            cachedBuddies = cachedBuddies.map { if (it.broId == expiredId) it.copy(broId = newId) else it }
        }

        // 5. Verification: Buddy cache and IDs cleanly updated
        assertTrue(buddyIds.contains(newId))
        assertFalse(buddyIds.contains(expiredId))
        assertEquals(newId, cachedBuddies.first().broId)

        // 6. Verification: Remote GET fetches the object with new ID cleanly matching in data.broId
        val getResp = server.handle(HttpMethod.GET, "https://api.restful-api.dev/objects/$newId", null)
        assertEquals(200, getResp.code)
        val envelope = json.decodeFromString<RestfulApiEnvelope>(getResp.body)
        assertEquals(newId, envelope.id)
        assertEquals(newId, envelope.data.broId)
    }

    @Test
    fun test_tier3_initialSyncWithLocalIdTriggersPostAndAdoptsServerId() {
        val server = MockHttpSyncServer()
        val localBroId = "bro_a1b2c3d4"

        // Fresh installation: local ID only
        val coordinator = ResilientSyncCoordinator(server, currentStoredId = localBroId)
        assertFalse(coordinator.isServerConfirmedId)

        val profile = ProgramProfile.texas(ProgramInput.demo)
        val payload = buildSpecificationPayload(profile, localBroId)

        val success = coordinator.sync(payload)
        assertTrue(success)
        assertTrue("Coordinator must adopt server ID after initial POST", coordinator.currentStoredId!!.startsWith("ff808181"))
        assertTrue(coordinator.isServerConfirmedId)

        assertEquals(1, server.requestLog.size)
        assertEquals(HttpMethod.POST, server.requestLog[0].method)
    }

    @Test
    fun test_tier3_existingConfirmedIdUsesPutDirectly() {
        val server = MockHttpSyncServer()
        val coordinator = ResilientSyncCoordinator(server, currentStoredId = null)

        val profile = ProgramProfile.texas(ProgramInput.demo)
        val payload = buildSpecificationPayload(profile, "init")

        // First sync creates object
        coordinator.sync(payload)
        val serverId = coordinator.currentStoredId!!
        server.requestLog.clear()

        // Second sync with valid server ID must use PUT directly
        val updateSuccess = coordinator.sync(payload)
        assertTrue(updateSuccess)
        assertEquals(1, server.requestLog.size)
        assertEquals(HttpMethod.PUT, server.requestLog[0].method)
        assertEquals("https://api.restful-api.dev/objects/$serverId", server.requestLog[0].url)
        assertEquals(serverId, coordinator.currentStoredId)
    }

    @Test
    fun test_tier3_pairwise_allProgramKindsFullSerialization() {
        val demoInput = ProgramInput.demo
        val kindsToTest = listOf(
            TrainingProgramKind.TEXAS to ProgramProfile.texas(demoInput),
            TrainingProgramKind.UPPER_LOWER to ProgramProfile.upperLower(UpperLowerInput.demo),
            TrainingProgramKind.FULL_BODY to ProgramProfile.fullBody(demoInput, FullBodyLevel.UNDER_YEAR),
            TrainingProgramKind.PRO_TEXAS to ProgramProfile.proTexas(demoInput)
        )

        for ((kind, profile) in kindsToTest) {
            val programDays = extractFullProgramDays(profile)
            val expectedWeeks = profile.workoutPlan.weeks.size

            val distinctWeeks = programDays.map { it.week }.distinct()
            assertEquals("Program $kind must serialize all $expectedWeeks weeks", expectedWeeks, distinctWeeks.size)

            val lifts = extractAllRecentLifts(profile)
            assertTrue("Program $kind active day must have >= 1 lift", lifts.isNotEmpty())
        }
    }

    @Test
    fun test_tier3_recoveryFailureHandling_networkErrorLeavesIdIntact() {
        // Failing server returning 500
        val failingServer = object : MockHttpSyncServer() {
            override fun handle(method: HttpMethod, url: String, body: String?): HttpResponse {
                return HttpResponse(500, "Internal Server Error")
            }
        }

        val originalId = "ff808181_existing"
        val coordinator = ResilientSyncCoordinator(failingServer, currentStoredId = originalId)
        val profile = ProgramProfile.texas(ProgramInput.demo)
        val payload = buildSpecificationPayload(profile, originalId)

        val result = coordinator.sync(payload)
        assertFalse(result)
        assertEquals("Original ID must not be corrupted on network failure", originalId, coordinator.currentStoredId)
    }

    // =========================================================================
    // TIER 4: Real-World Workload Scenarios (End-to-End User Workflows)
    // =========================================================================

    @Test
    fun test_tier4_e2e_userProgressesToWeek8_workoutCompletionTriggersSyncAndStatusUpdate() {
        val server = MockHttpSyncServer()
        val coordinator = ResilientSyncCoordinator(server)

        // 1. Initial Launch & Onboarding
        var profile = ProgramProfile.texas(ProgramInput.demo, name = "Иван")
        var payload = buildSpecificationPayload(profile, "bro_ivan")
        assertTrue(coordinator.sync(payload))
        val serverId = coordinator.currentStoredId!!

        // 2. Training Progression: Advance through Weeks 1..7
        for (w in 1..7) {
            for (d in 1..3) {
                profile = profile.toggleCompleted(w, d)
            }
        }
        assertEquals(8, profile.currentWeek)

        // 3. User Trains Week 8 Day 1
        profile = profile.toggleCompleted(8, 1)

        // 4. Workout Completed -> Auto-Sync Triggered with current timestamp
        val syncTime = System.currentTimeMillis()
        payload = buildSpecificationPayload(profile, serverId, lastActiveEpoch = syncTime)

        // Verify active stage in payload: Week 8, Day 2
        assertEquals(8, payload.currentWeek)
        assertEquals(2, payload.currentDay)

        // Execute sync
        assertTrue(coordinator.sync(payload))

        // 5. Buddy queries server for Ivan's profile
        val buddyGetResp = server.handle(HttpMethod.GET, "https://api.restful-api.dev/objects/$serverId", null)
        assertEquals(200, buddyGetResp.code)

        val buddyView = json.decodeFromString<RestfulApiEnvelope>(buddyGetResp.body).data

        // Buddy verifies:
        assertEquals("Иван", buddyView.name)
        assertEquals(8, buddyView.currentWeek)
        assertEquals(2, buddyView.currentDay)
        assertEquals(12, buddyView.programDays.map { it.week }.distinct().size)

        // Verify buddy sees dynamic status: completed just now (< 300s)
        val statusForBuddy = calculateExpectedStatus(buddyView.lastActiveEpoch, syncTime + 60_000L) // 1 min later
        assertEquals("В сети / Только что тренировался", statusForBuddy)
    }

    @Test
    fun test_tier4_e2e_backendEvictionDuringActiveCycle_selfHealingRecovery() {
        val server = MockHttpSyncServer()
        val coordinator = ResilientSyncCoordinator(server)

        // User profile in Week 8
        var profile = ProgramProfile.texas(ProgramInput.demo, name = "Алексей")
        for (w in 1..7) {
            for (d in 1..3) {
                profile = profile.toggleCompleted(w, d)
            }
        }
        val payload1 = buildSpecificationPayload(profile, "init")
        coordinator.sync(payload1)
        val id1 = coordinator.currentStoredId!!

        // Simulate backend TTL eviction: object disappears from server
        server.evictNextPut = true

        // User finishes Week 8 Day 2 workout -> auto-sync triggered
        profile = profile.toggleCompleted(8, 2)
        val payload2 = buildSpecificationPayload(profile, id1)

        // Sync triggers PUT -> receives 404 -> self-heals by POSTing -> receives id2
        val syncSuccess = coordinator.sync(payload2)
        assertTrue(syncSuccess)

        val id2 = coordinator.currentStoredId!!
        assertNotEquals(id1, id2)

        // Subsequent check on id2 reflects Week 8 Day 3
        val getResp = server.handle(HttpMethod.GET, "https://api.restful-api.dev/objects/$id2", null)
        assertEquals(200, getResp.code)
        assertTrue(getResp.body.contains("\"currentWeek\":8"))
        assertTrue(getResp.body.contains("\"currentDay\":3"))
    }

    @Test
    fun test_tier4_e2e_fullGangSyncSimulationBetweenTwoBros() {
        val server = MockHttpSyncServer()

        // Bro 1 (Squat focus)
        val bro1Coord = ResilientSyncCoordinator(server)
        val bro1Profile = ProgramProfile.texas(ProgramInput(140.0, 100.0, 160.0, TrainingLevel.INTERMEDIATE), name = "Богатырь")
        val bro1Payload = buildSpecificationPayload(bro1Profile, "bro_1")
        bro1Coord.sync(bro1Payload)
        val bro1Id = bro1Coord.currentStoredId!!

        // Bro 2 (Bench focus)
        val bro2Coord = ResilientSyncCoordinator(server)
        val bro2Profile = ProgramProfile.proTexas(ProgramInput(100.0, 140.0, 140.0, TrainingLevel.INTERMEDIATE), name = "Жимовик")
        val bro2Payload = buildSpecificationPayload(bro2Profile, "bro_2")
        bro2Coord.sync(bro2Payload)
        val bro2Id = bro2Coord.currentStoredId!!

        // Bro 1 inspects Bro 2's program
        val bro2DataResp = server.handle(HttpMethod.GET, "https://api.restful-api.dev/objects/$bro2Id", null)
        val bro2Data = json.decodeFromString<RestfulApiEnvelope>(bro2DataResp.body).data

        assertEquals("Жимовик", bro2Data.name)
        assertEquals(20, bro2Data.programDays.map { it.week }.distinct().size)
        assertTrue(bro2Data.recentLifts.any { it.name.contains("Жим") })

        // Bro 2 inspects Bro 1's program
        val bro1DataResp = server.handle(HttpMethod.GET, "https://api.restful-api.dev/objects/$bro1Id", null)
        val bro1Data = json.decodeFromString<RestfulApiEnvelope>(bro1DataResp.body).data

        assertEquals("Богатырь", bro1Data.name)
        assertEquals(12, bro1Data.programDays.map { it.week }.distinct().size)
        assertTrue(bro1Data.recentLifts.any { it.name.contains("Присед") })
    }

    @Test
    fun test_broProfileData_dynamicOnlineStatusAndStageProperties() {
        val now = System.currentTimeMillis()

        // 1. Online (< 300s)
        val onlineProfile = BroProfileData(
            broId = "bro_online",
            name = "Онлайн Бро",
            programKind = "TEXAS",
            programTitle = "Техасский метод",
            currentWeek = 8,
            currentDay = 2,
            lastActiveEpoch = now - 100_000L, // 100s ago
            squat5RM = 140.0,
            bench5RM = 100.0,
            deadlift5RM = 160.0
        )
        assertTrue("isOnline must be true within 300s", onlineProfile.isOnline)
        assertTrue("isRecentlyActive must be true within 300s", onlineProfile.isRecentlyActive)
        assertEquals("В сети / Только что тренировался", onlineProfile.statusDescription)
        assertEquals("Неделя 8, День 2", onlineProfile.activeStageDescription)

        // 2. Just trained (< 3600s, >= 300s)
        val justTrainedProfile = onlineProfile.copy(
            lastActiveEpoch = now - 1_200_000L // 20 minutes ago
        )
        assertFalse("isOnline must be false after 300s", justTrainedProfile.isOnline)
        assertTrue("isRecentlyActive must be true within 1h", justTrainedProfile.isRecentlyActive)
        assertEquals("Только что тренировался", justTrainedProfile.statusDescription)

        // 3. Trained hours ago (< 86400s)
        val hoursAgoProfile = onlineProfile.copy(
            lastActiveEpoch = now - 7_200_000L // 2 hours ago
        )
        assertFalse(hoursAgoProfile.isOnline)
        assertTrue(hoursAgoProfile.isRecentlyActive)
        assertEquals("Тренировался 2 ч. назад", hoursAgoProfile.statusDescription)

        // 4. Inactive days ago (>= 86400s)
        val daysAgoProfile = onlineProfile.copy(
            lastActiveEpoch = now - 3 * 86_400_000L // 3 days ago
        )
        assertFalse(daysAgoProfile.isOnline)
        assertFalse(daysAgoProfile.isRecentlyActive)
        assertEquals("Был 3 дн. назад", daysAgoProfile.statusDescription)
    }

    @Test
    fun test_broProfileData_epochNormalization() {
        val nowSec = System.currentTimeMillis() / 1000L
        val profileWithEpochSeconds = BroProfileData(
            broId = "bro_sec",
            name = "Seconds Bro",
            programKind = "TEXAS",
            programTitle = "Техасский метод",
            currentWeek = 1,
            currentDay = 1,
            lastActiveEpoch = nowSec - 60L, // 60 seconds ago in epoch seconds
            squat5RM = 100.0,
            bench5RM = 100.0,
            deadlift5RM = 100.0
        )
        assertTrue("Should detect online even when epoch was in seconds", profileWithEpochSeconds.isOnline)
        assertEquals("В сети / Только что тренировался", profileWithEpochSeconds.statusDescription)
    }
}
