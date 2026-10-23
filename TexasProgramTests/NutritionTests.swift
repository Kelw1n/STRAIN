import SwiftData
import XCTest
@testable import TexasProgram

/// Метод Лимара на цифрах с его доски, разбор ответа ИИ и хранение дневника.
@MainActor
final class NutritionTests: XCTestCase {
    private var calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/Moscow")!
        return calendar
    }()

    /// Понедельник, 19 октября 2026 — первый день недели наблюдения.
    private var start: Date { calendar.date(from: DateComponents(year: 2026, month: 10, day: 19))! }

    private func day(_ offset: Int, hour: Int = 13) -> Date {
        calendar.date(byAdding: DateComponents(day: offset, hour: hour), to: start)!
    }

    private func meals(_ kcal: [Double]) -> [FoodEntry] {
        kcal.enumerated().map { index, value in
            FoodEntry(date: day(index), name: "День \(index + 1)", nutrients: Nutrients(kcal: value, protein: 150, fat: 90, carbs: 300))
        }
    }

    private func weights(_ values: [Double], from offset: Int = 0) -> [BodyWeightEntry] {
        values.enumerated().map { index, value in
            BodyWeightEntry(date: calendar.startOfDay(for: day(offset + index)), weight: value)
        }
    }

    // MARK: Калории

    func testKcalFromMacrosAndSteps() {
        XCTAssertEqual(Nutrients.kcal(protein: 10, fat: 10, carbs: 10), 170)
        let norm = Nutrients(kcal: 3000, protein: 150, fat: 90, carbs: 300)
        XCTAssertEqual(LimarMethod.startingNorm(for: .bulk, maintenance: norm), Nutrients(kcal: 3120, protein: 150, fat: 90, carbs: 330))
        XCTAssertEqual(LimarMethod.startingNorm(for: .cut, maintenance: norm), Nutrients(kcal: 2883, protein: 150, fat: 77, carbs: 300))
        XCTAssertEqual(LimarMethod.startingNorm(for: .hold, maintenance: norm), norm)
        XCTAssertEqual(Nutrients(kcal: 2996, protein: 150.4, fat: 89.6, carbs: 300).rounded, Nutrients(kcal: 3000, protein: 150, fat: 90, carbs: 300))
    }

    // MARK: Неделя наблюдения

    /// Ровно пример Лимара: на доске «среднее 3200», но по его же цифрам выходит 3000.
    func testLimarBoardExample() {
        let plan = NutritionPlan(observationStart: start)
        let entries = meals([3000, 2700, 2800, 2900, 3100, 3200, 3300])
        let scale = weights([100, 101, 100, 102, 101, 103, 101])
        let state = LimarMethod.observation(plan, entries: entries, weights: scale, today: day(7), calendar: calendar)
        guard case let .done(week, maintenance, shift) = state else { return XCTFail("\(state)") }
        XCTAssertEqual(maintenance.kcal, 3000)
        XCTAssertEqual(week.foodDays, 7)
        XCTAssertEqual(week.weight ?? 0, 708.0 / 7, accuracy: 0.001)
        // Последние три замера на 1,3 кг выше первых — это предупреждение, а не запрет.
        XCTAssertEqual(shift ?? 0, 4.0 / 3, accuracy: 0.001)
    }

    func testStableWeightHasNoWarning() {
        let plan = NutritionPlan(observationStart: start)
        let state = LimarMethod.observation(plan, entries: meals(Array(repeating: 2500, count: 7)),
                                            weights: weights([80, 80.3, 79.9, 80.2, 80, 80.1, 80]), today: day(8), calendar: calendar)
        guard case let .done(_, maintenance, shift) = state else { return XCTFail("\(state)") }
        XCTAssertEqual(maintenance.kcal, 2500)
        XCTAssertNil(shift)
    }

    func testObservationCountsDaysAndNeedsFive() {
        let plan = NutritionPlan(observationStart: start)
        let collecting = LimarMethod.observation(plan, entries: meals([2000, 2100]), weights: weights([80, 80]), today: day(2), calendar: calendar)
        guard case let .collecting(dayNumber, week) = collecting else { return XCTFail("\(collecting)") }
        XCTAssertEqual(dayNumber, 3)
        XCTAssertEqual(week.foodDays, 2)

        let sparse = LimarMethod.observation(plan, entries: meals([2000, 2100, 2200, 2300]), weights: weights([80, 80, 80, 80, 80, 80]),
                                             today: day(7), calendar: calendar)
        guard case let .notEnough(thin) = sparse else { return XCTFail("\(sparse)") }
        XCTAssertEqual(thin.foodDays, 4)
    }

    /// Еда за день складывается, среднее — по дням, а не по записям.
    func testAverageIsPerDayNotPerEntry() {
        var entries = meals([2000, 2000, 2000, 2000, 2000])
        entries.append(FoodEntry(date: day(0, hour: 20), name: "Ужин", nutrients: Nutrients(kcal: 1000)))
        let week = LimarMethod.week(starting: start, entries: entries, weights: [], calendar: calendar)
        XCTAssertEqual(week.foodDays, 5)
        XCTAssertEqual(week.intake?.kcal ?? 0, 2200, accuracy: 0.001)
    }

    // MARK: Недельная проверка

    private func plan(_ goal: NutritionGoal, fat: Double = 90) -> NutritionPlan {
        var plan = NutritionPlan(observationStart: start)
        plan.goal = goal
        plan.target = Nutrients(kcal: 3000, protein: 150, fat: fat, carbs: 300)
        plan.stepStart = day(7)
        return plan
    }

    func testBulkAddsCarbsUntilWeightGrows() throws {
        let flat = weights(Array(repeating: 80, count: 7)) + weights(Array(repeating: 80.1, count: 7), from: 7)
        let check = try XCTUnwrap(LimarMethod.check(plan(.bulk), weights: flat, today: day(14), calendar: calendar))
        XCTAssertEqual(check.advice, .addCarbs)
        XCTAssertEqual(check.proposed.carbs, 330)
        XCTAssertEqual(check.proposed.kcal, 3120)

        let growing = weights(Array(repeating: 80, count: 7)) + weights(Array(repeating: 80.4, count: 7), from: 7)
        XCTAssertEqual(LimarMethod.check(plan(.bulk), weights: growing, today: day(14), calendar: calendar)?.advice, .keep)
    }

    func testCutRemovesFatAndWarnsWhenLow() throws {
        let flat = weights(Array(repeating: 80, count: 7)) + weights(Array(repeating: 79.9, count: 7), from: 7)
        let check = try XCTUnwrap(LimarMethod.check(plan(.cut), weights: flat, today: day(14), calendar: calendar))
        XCTAssertEqual(check.advice, .cutFat)
        XCTAssertEqual(check.proposed.fat, 77)
        XCTAssertFalse(check.lowFat)

        // 55 − 13 = 42 г при весе 80 кг — меньше 48 г (0,6 г на кило).
        XCTAssertEqual(LimarMethod.check(plan(.cut, fat: 55), weights: flat, today: day(14), calendar: calendar)?.lowFat, true)

        let dropping = weights(Array(repeating: 80, count: 7)) + weights(Array(repeating: 79.5, count: 7), from: 7)
        XCTAssertEqual(LimarMethod.check(plan(.cut), weights: dropping, today: day(14), calendar: calendar)?.advice, .keep)
    }

    func testHoldCorrectsBothWays() {
        let up = weights(Array(repeating: 80, count: 7)) + weights(Array(repeating: 80.3, count: 7), from: 7)
        XCTAssertEqual(LimarMethod.check(plan(.hold), weights: up, today: day(14), calendar: calendar)?.advice, .removeCarbs)
        let down = weights(Array(repeating: 80, count: 7)) + weights(Array(repeating: 79.7, count: 7), from: 7)
        XCTAssertEqual(LimarMethod.check(plan(.hold), weights: down, today: day(14), calendar: calendar)?.advice, .addCarbs)
    }

    func testCheckWaitsForWeekAndEnoughWeights() throws {
        let scale = weights(Array(repeating: 80, count: 14))
        XCTAssertNil(LimarMethod.check(plan(.bulk), weights: scale, today: day(13), calendar: calendar))
        let sparse = weights(Array(repeating: 80, count: 7)) + weights([80, 80, 80], from: 7)
        let check = try XCTUnwrap(LimarMethod.check(plan(.bulk), weights: sparse, today: day(14), calendar: calendar))
        XCTAssertNil(check.change)
        XCTAssertEqual(check.advice, .keep)
    }

    // MARK: Ответ ИИ

    func testParsesButterflyFormatAndRecountsTotal() throws {
        let meal = try FoodRecognizer.parse("""
        {"title": "Курица с рисом", "items": [
          {"name": "Куриная грудка", "portion_g": 150, "calories": 248, "protein_g": 46.5, "fat_g": 5.4, "carbs_g": 0},
          {"name": "Рис", "portion_g": 200, "calories": 260, "protein_g": 5.4, "fat_g": 0.6, "carbs_g": 57}
        ], "total": {"calories": 9999}, "confidence": "Высокая", "comment": "оценка по виду"}
        """)
        XCTAssertEqual(meal.title, "Курица с рисом")
        XCTAssertEqual(meal.items.count, 2)
        XCTAssertEqual(meal.items[0].grams, 150)
        XCTAssertEqual(meal.total.kcal, 508)
        XCTAssertEqual(meal.total.carbs, 57, accuracy: 0.001)
        XCTAssertEqual(meal.confidence, "высокая")
    }

    func testParsesLooseAnswers() throws {
        let fenced = try FoodRecognizer.parse("""
        ```json
        {"foods": [{"food": "Йогурт «Чудо»", "amount": "примерно 125 г", "kcal": "110,5", "protein": "4", "fat": 3, "carbs": 15}]}
        ```
        """)
        XCTAssertEqual(fenced.items.first?.name, "Йогурт «Чудо»")
        XCTAssertEqual(fenced.items.first?.grams, 125)
        XCTAssertEqual(fenced.items.first?.nutrients.kcal, 111)
        XCTAssertEqual(fenced.confidence, "оценка по фото")

        // Калорий нет — считаем по Б/Ж/У.
        let noKcal = try FoodRecognizer.parse(#"[{"name": "Орехи", "protein_g": 10, "fat_g": 20, "carbs_g": 5}]"#)
        XCTAssertEqual(noKcal.items.first?.nutrients.kcal, 240)
        XCTAssertEqual(noKcal.title, "Орехи")
    }

    func testRejectsEmptyAndGarbage() {
        XCTAssertThrowsError(try FoodRecognizer.parse(#"{"items": []}"#)) { error in
            XCTAssertEqual(error as? FoodRecognizer.Failure, .nothingFound)
        }
        XCTAssertThrowsError(try FoodRecognizer.parse("не знаю, что это")) { error in
            XCTAssertEqual(error as? FoodRecognizer.Failure, .unreadable)
        }
        XCTAssertNil(FoodRecognizer.number("-", limit: 100))
        XCTAssertNil(FoodRecognizer.number(50_000, limit: 20_000))
    }

    func testGeminiResponseSkipsThoughts() throws {
        let data = Data(#"{"candidates": [{"content": {"parts": [{"text": "думаю…", "thought": true}, {"text": "{\"items\": []}"}]}}]}"#.utf8)
        XCTAssertEqual(try FoodRecognizer.responseText(data), #"{"items": []}"#)
        XCTAssertThrowsError(try FoodRecognizer.responseText(Data(#"{"candidates": []}"#.utf8)))
    }

    func testCorrectionPromptCarriesEditedItems() {
        let meal = RecognizedMeal(title: "Каша", items: [RecognizedFood(name: "Овсянка", grams: 200, nutrients: Nutrients(kcal: 300))],
                                  confidence: "средняя", comment: "")
        let prompt = FoodRecognizer.correctionPrompt(meal, note: "там 250 г")
        XCTAssertTrue(prompt.contains("Овсянка"))
        XCTAssertTrue(prompt.contains("там 250 г"))
        XCTAssertTrue(prompt.contains("\"portion_g\" : 200"))
    }

    // MARK: Хранение

    func testStoreKeepsDiaryPerProfile() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let first = UUID()
        let store = NutritionStore(directory: directory)
        store.open(first)
        store.add(meals([2000]))
        store.startObservation(on: start, calendar: calendar)
        store.choose(.bulk, maintenance: Nutrients(kcal: 2500, protein: 150, fat: 80, carbs: 280), today: day(7), calendar: calendar)

        let reopened = NutritionStore(directory: directory)
        reopened.open(first)
        XCTAssertEqual(reopened.data.entries.count, 1)
        XCTAssertEqual(reopened.data.plan?.goal, .bulk)
        XCTAssertEqual(reopened.data.plan?.target?.carbs, 310)
        XCTAssertEqual(reopened.data.plan?.history.count, 1)

        reopened.open(UUID())
        XCTAssertTrue(reopened.data.entries.isEmpty)
    }

    /// Питание едет в копии с iPhone; копия без него питание на телефоне не трогает.
    func testBackupCarriesNutrition() throws {
        let profile = ProgramProfile(input: .demo)
        let id = profile.profileID
        defer { NutritionStore.delete(id) }
        var diary = NutritionData(entries: meals([2100, 2200]))
        diary.plan = NutritionPlan(observationStart: start)
        NutritionStore.write(diary, for: id)

        let archive = try BackupService.archive(from: try BackupService.export([profile], socialToken: nil))
        XCTAssertEqual(archive.profiles.first?.nutrition?.entries.count, 2)

        NutritionStore.delete(id)
        let container = try ModelContainer(for: ProgramProfile.self, configurations: ModelConfiguration(isStoredInMemoryOnly: true))
        BackupService.restore(archive, into: ModelContext(container), existing: [])
        XCTAssertEqual(NutritionStore.read(id)?.entries.count, 2)
        XCTAssertEqual(NutritionStore.read(id)?.plan?.observationStart, start)

        var withoutNutrition = archive
        withoutNutrition.profiles[0].nutrition = nil
        BackupService.restore(withoutNutrition, into: ModelContext(container), existing: [])
        XCTAssertEqual(NutritionStore.read(id)?.entries.count, 2)
    }
}
