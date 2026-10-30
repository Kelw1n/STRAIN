package com.texasprogram.app

import com.texasprogram.app.data.BackupService
import com.texasprogram.app.data.NutritionStore
import com.texasprogram.app.model.BodyWeightEntry
import com.texasprogram.app.model.FoodEntry
import com.texasprogram.app.model.LimarMethod
import com.texasprogram.app.model.Nutrients
import com.texasprogram.app.model.NutritionData
import com.texasprogram.app.model.NutritionGoal
import com.texasprogram.app.model.NutritionPlan
import com.texasprogram.app.model.ProgramInput
import com.texasprogram.app.model.ProgramProfile
import com.texasprogram.app.service.FoodRecognizer
import com.texasprogram.app.service.RecognizedFood
import com.texasprogram.app.service.RecognizedMeal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/// Метод Лимара на цифрах с его доски, разбор ответа ИИ и хранение дневника —
/// те же проверки, что в iOS-версии, плюс копия питания между платформами.
class NutritionTest {
    private val zone: ZoneId = ZoneId.of("Europe/Moscow")

    /// Понедельник, 19 октября 2026 — первый день недели наблюдения.
    private val start: LocalDate = LocalDate.of(2026, 10, 19)
    private val startInstant: Instant = start.atStartOfDay(zone).toInstant()

    private fun day(offset: Int): LocalDate = start.plusDays(offset.toLong())

    private fun moment(offset: Int, hour: Int = 13): Instant = day(offset).atTime(hour, 0).atZone(zone).toInstant()

    private fun meals(kcal: List<Double>): List<FoodEntry> = kcal.mapIndexed { index, value ->
        FoodEntry(date = moment(index), name = "День ${index + 1}", nutrients = Nutrients(value, 150.0, 90.0, 300.0))
    }

    private fun weights(values: List<Double>, from: Int = 0): List<BodyWeightEntry> =
        values.mapIndexed { index, value -> BodyWeightEntry(day(from + index).toEpochDay(), value) }

    private fun repeat(value: Double, count: Int) = List(count) { value }

    // MARK: Калории

    @Test
    fun kcalFromMacrosAndSteps() {
        assertEquals(170.0, Nutrients.kcal(10.0, 10.0, 10.0), 0.0)
        val norm = Nutrients(3000.0, 150.0, 90.0, 300.0)
        assertEquals(Nutrients(3120.0, 150.0, 90.0, 330.0), LimarMethod.startingNorm(NutritionGoal.BULK, norm))
        assertEquals(Nutrients(2883.0, 150.0, 77.0, 300.0), LimarMethod.startingNorm(NutritionGoal.CUT, norm))
        assertEquals(norm, LimarMethod.startingNorm(NutritionGoal.HOLD, norm))
        assertEquals(Nutrients(3000.0, 150.0, 90.0, 300.0), Nutrients(2996.0, 150.4, 89.6, 300.0).rounded())
        // Половинки — вверх, как на iPhone, а не к чётному.
        assertEquals(Nutrients(2550.0, 3.0, 1.0, 0.0), Nutrients(2545.0, 2.5, 0.5, 0.0).rounded())
    }

    // MARK: Неделя наблюдения

    /// Ровно пример Лимара: на доске «среднее 3200», но по его же цифрам выходит 3000.
    @Test
    fun limarBoardExample() {
        val plan = NutritionPlan(observationStart = startInstant)
        val entries = meals(listOf(3000.0, 2700.0, 2800.0, 2900.0, 3100.0, 3200.0, 3300.0))
        val scale = weights(listOf(100.0, 101.0, 100.0, 102.0, 101.0, 103.0, 101.0))
        val state = LimarMethod.observation(plan, entries, scale, today = day(7), zone = zone)
        val done = state as? LimarMethod.Observation.Done ?: return fail("$state")
        assertEquals(3000.0, done.maintenance.kcal, 0.0)
        assertEquals(7, done.week.foodDays)
        assertEquals(708.0 / 7, done.week.weight ?: 0.0, 0.001)
        // Последние три замера на 1,3 кг выше первых — это предупреждение, а не запрет.
        assertEquals(4.0 / 3, done.shift ?: 0.0, 0.001)
    }

    @Test
    fun stableWeightHasNoWarning() {
        val plan = NutritionPlan(observationStart = startInstant)
        val state = LimarMethod.observation(
            plan, meals(repeat(2500.0, 7)), weights(listOf(80.0, 80.3, 79.9, 80.2, 80.0, 80.1, 80.0)),
            today = day(8), zone = zone
        )
        val done = state as? LimarMethod.Observation.Done ?: return fail("$state")
        assertEquals(2500.0, done.maintenance.kcal, 0.0)
        assertNull(done.shift)
    }

    @Test
    fun observationCountsDaysAndNeedsFive() {
        val plan = NutritionPlan(observationStart = startInstant)
        val collecting = LimarMethod.observation(plan, meals(listOf(2000.0, 2100.0)), weights(listOf(80.0, 80.0)), today = day(2), zone = zone)
        val running = collecting as? LimarMethod.Observation.Collecting ?: return fail("$collecting")
        assertEquals(3, running.day)
        assertEquals(2, running.week.foodDays)

        val sparse = LimarMethod.observation(
            plan, meals(listOf(2000.0, 2100.0, 2200.0, 2300.0)), weights(repeat(80.0, 6)), today = day(7), zone = zone
        )
        val thin = sparse as? LimarMethod.Observation.NotEnough ?: return fail("$sparse")
        assertEquals(4, thin.week.foodDays)
    }

    /// Еда за день складывается, среднее — по дням, а не по записям.
    @Test
    fun averageIsPerDayNotPerEntry() {
        val entries = meals(repeat(2000.0, 5)) + FoodEntry(date = moment(0, hour = 20), name = "Ужин", nutrients = Nutrients(kcal = 1000.0))
        val week = LimarMethod.week(start, entries, emptyList(), zone)
        assertEquals(5, week.foodDays)
        assertEquals(2200.0, week.intake?.kcal ?: 0.0, 0.001)
    }

    /// Запись в 23:30 по Москве — это ещё тот же день, хотя по Гринвичу уже другой.
    @Test
    fun dayIsLocal() {
        val late = FoodEntry(date = moment(0, hour = 23).plusSeconds(1800), name = "Кефир", nutrients = Nutrients(kcal = 120.0))
        assertEquals(1, LimarMethod.entries(listOf(late), day(0), zone).size)
        assertTrue(LimarMethod.entries(listOf(late), day(1), zone).isEmpty())
    }

    // MARK: Недельная проверка

    private fun plan(goal: NutritionGoal, fat: Double = 90.0) = NutritionPlan(
        observationStart = startInstant,
        goal = goal,
        target = Nutrients(3000.0, 150.0, fat, 300.0),
        stepStart = day(7).atStartOfDay(zone).toInstant()
    )

    @Test
    fun bulkAddsCarbsUntilWeightGrows() {
        val flat = weights(repeat(80.0, 7)) + weights(repeat(80.1, 7), from = 7)
        val check = LimarMethod.check(plan(NutritionGoal.BULK), flat, today = day(14), zone = zone)!!
        assertEquals(LimarMethod.Advice.ADD_CARBS, check.advice)
        assertEquals(330.0, check.proposed.carbs, 0.0)
        assertEquals(3120.0, check.proposed.kcal, 0.0)

        val growing = weights(repeat(80.0, 7)) + weights(repeat(80.4, 7), from = 7)
        assertEquals(LimarMethod.Advice.KEEP, LimarMethod.check(plan(NutritionGoal.BULK), growing, today = day(14), zone = zone)?.advice)
    }

    @Test
    fun cutRemovesFatAndWarnsWhenLow() {
        val flat = weights(repeat(80.0, 7)) + weights(repeat(79.9, 7), from = 7)
        val check = LimarMethod.check(plan(NutritionGoal.CUT), flat, today = day(14), zone = zone)!!
        assertEquals(LimarMethod.Advice.CUT_FAT, check.advice)
        assertEquals(77.0, check.proposed.fat, 0.0)
        assertFalse(check.lowFat)

        // 55 − 13 = 42 г при весе 80 кг — меньше 48 г (0,6 г на кило).
        assertEquals(true, LimarMethod.check(plan(NutritionGoal.CUT, fat = 55.0), flat, today = day(14), zone = zone)?.lowFat)

        val dropping = weights(repeat(80.0, 7)) + weights(repeat(79.5, 7), from = 7)
        assertEquals(LimarMethod.Advice.KEEP, LimarMethod.check(plan(NutritionGoal.CUT), dropping, today = day(14), zone = zone)?.advice)
    }

    @Test
    fun holdCorrectsBothWays() {
        val up = weights(repeat(80.0, 7)) + weights(repeat(80.3, 7), from = 7)
        assertEquals(LimarMethod.Advice.REMOVE_CARBS, LimarMethod.check(plan(NutritionGoal.HOLD), up, today = day(14), zone = zone)?.advice)
        val down = weights(repeat(80.0, 7)) + weights(repeat(79.7, 7), from = 7)
        assertEquals(LimarMethod.Advice.ADD_CARBS, LimarMethod.check(plan(NutritionGoal.HOLD), down, today = day(14), zone = zone)?.advice)
    }

    @Test
    fun checkWaitsForWeekAndEnoughWeights() {
        val scale = weights(repeat(80.0, 14))
        assertNull(LimarMethod.check(plan(NutritionGoal.BULK), scale, today = day(13), zone = zone))
        val sparse = weights(repeat(80.0, 7)) + weights(listOf(80.0, 80.0, 80.0), from = 7)
        val check = LimarMethod.check(plan(NutritionGoal.BULK), sparse, today = day(14), zone = zone)!!
        assertNull(check.change)
        assertEquals(LimarMethod.Advice.KEEP, check.advice)
    }

    // MARK: Ответ ИИ

    @Test
    fun parsesButterflyFormatAndRecountsTotal() {
        val meal = FoodRecognizer.parse(
            """
            {"title": "Курица с рисом", "items": [
              {"name": "Куриная грудка", "portion_g": 150, "calories": 248, "protein_g": 46.5, "fat_g": 5.4, "carbs_g": 0},
              {"name": "Рис", "portion_g": 200, "calories": 260, "protein_g": 5.4, "fat_g": 0.6, "carbs_g": 57}
            ], "total": {"calories": 9999}, "confidence": "Высокая", "comment": "оценка по виду"}
            """.trimIndent()
        )
        assertEquals("Курица с рисом", meal.title)
        assertEquals(2, meal.items.size)
        assertEquals(150.0, meal.items[0].grams!!, 0.0)
        assertEquals(508.0, meal.total.kcal, 0.0)
        assertEquals(57.0, meal.total.carbs, 0.001)
        assertEquals("высокая", meal.confidence)
        assertEquals("оценка по виду", meal.comment)
    }

    @Test
    fun parsesLooseAnswers() {
        val fenced = FoodRecognizer.parse(
            """
            ```json
            {"foods": [{"food": "Йогурт «Чудо»", "amount": "примерно 125 г", "kcal": "110,5", "protein": "4", "fat": 3, "carbs": 15}]}
            ```
            """.trimIndent()
        )
        assertEquals("Йогурт «Чудо»", fenced.items.first().name)
        assertEquals(125.0, fenced.items.first().grams!!, 0.0)
        assertEquals(111.0, fenced.items.first().nutrients.kcal, 0.0)
        assertEquals("оценка по фото", fenced.confidence)

        // Калорий нет — считаем по Б/Ж/У.
        val noKcal = FoodRecognizer.parse("""[{"name": "Орехи", "protein_g": 10, "fat_g": 20, "carbs_g": 5}]""")
        assertEquals(240.0, noKcal.items.first().nutrients.kcal, 0.0)
        assertEquals("Орехи", noKcal.title)

        // Текст вокруг JSON и «умные» кавычки.
        val chatty = FoodRecognizer.parse("Вот ответ: {“items”: [{“name”: “Банан”, “calories”: 105}]} Приятного аппетита!")
        assertEquals("Банан", chatty.items.single().name)
    }

    @Test
    fun rejectsEmptyAndGarbage() {
        assertEquals(FoodRecognizer.Failure.NothingFound, runCatching { FoodRecognizer.parse("""{"items": []}""") }.exceptionOrNull())
        assertEquals(FoodRecognizer.Failure.Unreadable, runCatching { FoodRecognizer.parse("не знаю, что это") }.exceptionOrNull())
        assertNull(FoodRecognizer.number(JsonPrimitive("-"), 100.0))
        assertNull(FoodRecognizer.number(JsonPrimitive(50_000), 20_000.0))
        assertNull(FoodRecognizer.number(JsonPrimitive(true), 100.0))
    }

    @Test
    fun geminiResponseSkipsThoughts() {
        val raw = """{"candidates": [{"content": {"parts": [{"text": "думаю…", "thought": true}, {"text": "{\"items\": []}"}]}}]}"""
        assertEquals("""{"items": []}""", FoodRecognizer.responseText(raw))
        assertEquals(FoodRecognizer.Failure.NothingFound, runCatching { FoodRecognizer.responseText("""{"candidates": []}""") }.exceptionOrNull())
    }

    /// Ошибки Google — те же слова, что на iPhone; пропавшая модель — повод взять следующую.
    @Test
    fun googleErrorsBecomeWords() {
        fun error(message: String) = """{"error": {"code": 400, "message": "$message"}}"""
        assertTrue(FoodRecognizer.isModelMissing(404, ""))
        assertTrue(FoodRecognizer.isModelMissing(400, error("models/gemini-x is not found for API version v1beta")))
        assertFalse(FoodRecognizer.isModelMissing(400, error("API key not valid. Please pass a valid API key.")))
        assertEquals(FoodRecognizer.Failure.Region, FoodRecognizer.failure(400, error("User location is not supported for the API use.")))
        assertEquals(FoodRecognizer.Failure.BadKey, FoodRecognizer.failure(400, error("API key not valid. Please pass a valid API key.")))
        assertEquals(FoodRecognizer.Failure.BadKey, FoodRecognizer.failure(403, ""))
        assertEquals(FoodRecognizer.Failure.Quota, FoodRecognizer.failure(429, ""))
        assertEquals(FoodRecognizer.Failure.Busy, FoodRecognizer.failure(503, ""))
        assertEquals(FoodRecognizer.Failure.Network("ошибка 418"), FoodRecognizer.failure(418, ""))
        assertNull(FoodRecognizer.failure(200, ""))
    }

    @Test
    fun correctionPromptCarriesEditedItems() {
        val meal = RecognizedMeal(
            title = "Каша",
            items = listOf(RecognizedFood(name = "Овсянка", grams = 200.0, nutrients = Nutrients(kcal = 300.0))),
            confidence = "средняя",
            comment = ""
        )
        val prompt = FoodRecognizer.correctionPrompt(meal, "там 250 г")
        assertTrue(prompt.contains("Овсянка"))
        assertTrue(prompt.contains("там 250 г"))
        assertTrue(prompt, prompt.contains("\"portion_g\": 200"))
        assertTrue(prompt.startsWith("Ты пересчитываешь уже сохранённый прием пищи STRAIN."))
    }

    // MARK: Хранение

    private fun temporaryDirectory(): File = Files.createTempDirectory("nutrition").toFile()

    @Test
    fun storeKeepsDiaryPerProfile() {
        val directory = temporaryDirectory()
        try {
            val first = "11111111-2222-3333-4444-555555555555"
            val store = NutritionStore(directory)
            store.open(first)
            store.add(meals(listOf(2000.0)))
            store.startObservation(start, zone)
            store.choose(NutritionGoal.BULK, Nutrients(2500.0, 150.0, 80.0, 280.0), today = day(7), zone = zone)

            val reopened = NutritionStore(directory)
            reopened.open(first)
            assertEquals(1, reopened.data.entries.size)
            assertEquals(NutritionGoal.BULK, reopened.data.plan?.goal)
            assertEquals(310.0, reopened.data.plan?.target?.carbs ?: 0.0, 0.0)
            assertEquals(1, reopened.data.plan?.history?.size)
            assertEquals(startInstant, reopened.data.plan?.observationStart)

            reopened.open("другой")
            assertTrue(reopened.data.entries.isEmpty())

            NutritionStore.delete(directory, first)
            assertNull(NutritionStore.read(directory, first))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun recentFoodsSkipRepeats() {
        val directory = temporaryDirectory()
        try {
            val store = NutritionStore(directory)
            store.open("p")
            store.add(
                listOf(
                    FoodEntry(date = moment(0), name = "Гречка", nutrients = Nutrients(kcal = 300.0)),
                    FoodEntry(date = moment(1), name = "гречка ", nutrients = Nutrients(kcal = 320.0)),
                    FoodEntry(date = moment(2), name = "Творог", nutrients = Nutrients(kcal = 200.0))
                )
            )
            assertEquals(listOf("Творог", "гречка "), store.recentFoods().map { it.name })
        } finally {
            directory.deleteRecursively()
        }
    }

    /// Питание едет в копии; копия без него питание на телефоне не трогает.
    @Test
    fun backupCarriesNutrition() {
        val directory = temporaryDirectory()
        try {
            val profile = ProgramProfile.texas(ProgramInput.demo)
            val diary = NutritionData(entries = meals(listOf(2100.0, 2200.0)), plan = NutritionPlan(observationStart = startInstant))
            NutritionStore.write(directory, profile.id, diary)

            val text = BackupService.export(listOf(profile)) { NutritionStore.read(directory, it) }
            val archive = BackupService.parse(text)
            assertEquals(2, archive.profiles.first().nutrition?.entries?.size)

            NutritionStore.delete(directory, profile.id)
            BackupService.restoreNutrition(archive, directory)
            assertEquals(2, NutritionStore.read(directory, profile.id)?.entries?.size)
            assertEquals(startInstant, NutritionStore.read(directory, profile.id)?.plan?.observationStart)

            val withoutNutrition = archive.copy(profiles = archive.profiles.map { it.copy(nutrition = null) })
            BackupService.restoreNutrition(withoutNutrition, directory)
            assertEquals(2, NutritionStore.read(directory, profile.id)?.entries?.size)
        } finally {
            directory.deleteRecursively()
        }
    }

    /// Питание из копии iPhone читается целиком; копия с Android пишет его так,
    /// как ждёт iOS: даты без долей секунды и все обязательные поля на месте.
    @Test
    fun nutritionMatchesIphoneBackup() {
        val archive = BackupService.parse(NUTRITION_FROM_IOS)
        val nutrition = archive.profiles.single().nutrition!!
        val entry = nutrition.entries.single()
        assertEquals("6F9619FF-8B86-D011-B42D-00C04FC964FF", entry.id)
        assertEquals(Instant.parse("2026-10-19T10:00:00Z"), entry.date)
        assertEquals(150.0, entry.grams!!, 0.0)
        assertTrue(entry.fromPhoto)
        assertEquals(46.5, entry.nutrients.protein, 0.0)
        val plan = nutrition.plan!!
        assertEquals(NutritionGoal.CUT, plan.goal)
        assertEquals(2883.0, plan.target!!.kcal, 0.0)
        assertEquals("Сушка", plan.history.single().note)
        assertEquals(Instant.parse("2026-10-18T21:00:00Z"), plan.observationStart)

        val profile = BackupService.profile(archive.profiles.single())
        val withMillis = nutrition.copy(
            entries = nutrition.entries + FoodEntry(date = Instant.parse("2026-10-20T08:15:30.123Z"), name = "Яблоко", nutrients = Nutrients(kcal = 52.0))
        )
        val exported = Json.parseToJsonElement(BackupService.export(listOf(profile)) { withMillis })
        val written = exported.jsonObject["profiles"]!!.jsonArray.single().jsonObject["nutrition"]!!.jsonObject
        val entries = written["entries"]!!.jsonArray.map { it.jsonObject }
        assertEquals("2026-10-20T08:15:30Z", entries.last()["date"]!!.jsonPrimitive.content)
        entries.forEach { row ->
            listOf("id", "date", "name", "nutrients", "fromPhoto").forEach { key -> assertNotNull(key, row[key]) }
            val nutrients = row["nutrients"] as JsonObject
            listOf("kcal", "protein", "fat", "carbs").forEach { key -> assertNotNull(key, nutrients[key]) }
        }
        val writtenPlan = written["plan"]!!.jsonObject
        assertEquals("cut", writtenPlan["goal"]!!.jsonPrimitive.content)
        assertEquals("2026-10-18T21:00:00Z", writtenPlan["observationStart"]!!.jsonPrimitive.content)
        assertNotNull(writtenPlan["history"])
    }

    private companion object {
        /// Профиль из копии iPhone — только поля, нужные для питания; остальное по умолчанию.
        const val NUTRITION_FROM_IOS = """
        {
          "version" : 1,
          "exportedAt" : "2026-10-27T09:00:00Z",
          "profiles" : [
            {
              "id" : "0B5A6F2E-7C1D-4E8F-9A3B-2C4D6E8F0A1B",
              "name" : "Питание",
              "createdAtEpochDay" : 20740,
              "programKind" : "TEXAS",
              "squat5RM" : 100,
              "bench5RM" : 80,
              "deadlift5RM" : 120,
              "level" : "BEGINNER",
              "cycleStartedEpochDay" : 20740,
              "nutrition" : {
                "entries" : [
                  {
                    "date" : "2026-10-19T10:00:00Z",
                    "fromPhoto" : true,
                    "grams" : 150,
                    "id" : "6F9619FF-8B86-D011-B42D-00C04FC964FF",
                    "name" : "Куриная грудка",
                    "nutrients" : { "carbs" : 0, "fat" : 5.4, "kcal" : 248, "protein" : 46.5 }
                  }
                ],
                "plan" : {
                  "goal" : "cut",
                  "history" : [
                    {
                      "date" : "2026-10-25T21:00:00Z",
                      "norm" : { "carbs" : 300, "fat" : 77, "kcal" : 2883, "protein" : 150 },
                      "note" : "Сушка"
                    }
                  ],
                  "maintenance" : { "carbs" : 300, "fat" : 90, "kcal" : 3000, "protein" : 150 },
                  "observationStart" : "2026-10-18T21:00:00Z",
                  "stepStart" : "2026-10-25T21:00:00Z",
                  "target" : { "carbs" : 300, "fat" : 77, "kcal" : 2883, "protein" : 150 }
                }
              }
            }
          ]
        }
        """
    }
}
