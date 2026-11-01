import XCTest
@testable import TexasProgram

/// Трекер добавок: серия, дни «с тренировкой», отметки за прошлые дни,
/// напоминания и формат файла. Те же случаи — в SupplementsTest на Android.
@MainActor
final class SupplementTests: XCTestCase {
    private var calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/Moscow")!
        return calendar
    }()

    private func day(_ d: Int, _ hour: Int = 10) -> Date {
        calendar.date(from: DateComponents(year: 2026, month: 3, day: d, hour: hour))!
    }

    private func start(_ d: Int) -> Date { calendar.startOfDay(for: day(d)) }

    private let creatineID = UUID(uuidString: "00000000-0000-0000-0000-00000000000A")!
    private let proteinID = UUID(uuidString: "00000000-0000-0000-0000-00000000000B")!

    private func data(taken: [(UUID, Int)]) -> SupplementData {
        SupplementData(
            items: [
                Supplement(id: creatineID, name: "Креатин", dose: "5 г", slot: .morning, reminder: SupplementReminder(hour: 9, minute: 0), created: day(1)),
                Supplement(id: proteinID, name: "Протеин", dose: "30 г", slot: .workout, reminder: nil, created: day(1))
            ],
            taken: taken.map { SupplementIntake(supplementID: $0.0, date: day($0.1)) }
        )
    }

    func testDayStateCountsWorkoutOnlyOnTrainingDays() {
        let data = data(taken: [(creatineID, 5)])
        let rest = SupplementTracker.state(on: day(5), data: data, trainedDays: [], calendar: calendar)
        XCTAssertEqual(rest.required, 1)
        XCTAssertTrue(rest.isComplete)

        let training = SupplementTracker.state(on: day(5), data: data, trainedDays: [start(5)], calendar: calendar)
        XCTAssertEqual(training.required, 2)
        XCTAssertEqual(training.taken, 1)
        XCTAssertFalse(training.isComplete)
        XCTAssertEqual(training.share, 0.5)
    }

    func testStreakSkipsUnfinishedTodayAndStopsOnMiss() {
        // 2, 3, 4 — всё, 5 — тренировка без протеина, 6, 7 — всё, 8 (сегодня) — ещё нет.
        let data = data(taken: [(creatineID, 2), (creatineID, 3), (creatineID, 4), (creatineID, 5),
                                (creatineID, 6), (creatineID, 7)])
        let trained: Set<Date> = [start(5)]
        XCTAssertEqual(SupplementTracker.streak(data: data, trainedDays: trained, today: day(8), calendar: calendar), 2)
        // Без тренировки 5-го рвать нечему: серия до первого дня с пропуском — 1-го.
        XCTAssertEqual(SupplementTracker.streak(data: data, trainedDays: [], today: day(8), calendar: calendar), 6)
    }

    func testStreakCountsTodayWhenDone() {
        let data = data(taken: [(creatineID, 7), (creatineID, 8)])
        XCTAssertEqual(SupplementTracker.streak(data: data, trainedDays: [], today: day(8, 20), calendar: calendar), 2)
        XCTAssertEqual(SupplementTracker.streak(data: SupplementData(), trainedDays: [], today: day(8), calendar: calendar), 0)
    }

    func testDaysBeforeCreationAreNotRequired() {
        var data = data(taken: [])
        data.items[0].created = day(10)
        XCTAssertEqual(SupplementTracker.state(on: day(9), data: data, trainedDays: [], calendar: calendar).required, 0)
        XCTAssertEqual(SupplementTracker.state(on: day(10), data: data, trainedDays: [], calendar: calendar).required, 1)
    }

    func testToggleTodayAndPastDay() {
        var data = data(taken: [])
        let item = data.items[0]
        SupplementTracker.toggle(item, on: start(8), in: &data, now: day(8, 7), calendar: calendar)
        XCTAssertEqual(data.taken.last?.date, day(8, 7))
        SupplementTracker.toggle(item, on: start(6), in: &data, now: day(8, 7), calendar: calendar)
        XCTAssertEqual(data.taken.last?.date, day(6, 12))
        XCTAssertTrue(SupplementTracker.isTaken(item, on: day(6, 23), in: data, calendar: calendar))
        SupplementTracker.toggle(item, on: start(8), in: &data, now: day(8, 9), calendar: calendar)
        XCTAssertFalse(SupplementTracker.isTaken(item, on: day(8), in: data, calendar: calendar))
        XCTAssertEqual(data.taken.count, 1)
    }

    func testRecentWeekEndsToday() {
        let recent = SupplementTracker.recent(7, data: data(taken: [(creatineID, 8)]), trainedDays: [], today: day(8), calendar: calendar)
        XCTAssertEqual(recent.count, 7)
        XCTAssertEqual(recent.first?.day, start(2))
        XCTAssertEqual(recent.last?.day, start(8))
        XCTAssertTrue(recent.last?.isComplete ?? false)
    }

    func testOrderBySlot() {
        let items = [
            Supplement(name: "Магний", slot: .evening),
            Supplement(name: "Протеин", slot: .workout),
            Supplement(name: "Креатин", slot: .morning),
            Supplement(name: "Омега", slot: .morning)
        ]
        XCTAssertEqual(SupplementTracker.ordered(items).map(\.name), ["Креатин", "Омега", "Магний", "Протеин"])
    }

    func testPrunedDropsOldIntakes() {
        var data = data(taken: [(creatineID, 8)])
        data.taken.append(SupplementIntake(supplementID: creatineID, date: calendar.date(byAdding: .day, value: -500, to: day(8))!))
        XCTAssertEqual(SupplementTracker.pruned(data, now: day(8), calendar: calendar).taken.count, 1)
    }

    // MARK: Напоминания

    func testReminderSlotsGroupByTime() {
        let data = SupplementData(items: [
            Supplement(name: "Креатин", dose: "5 г", reminder: SupplementReminder(hour: 9, minute: 0)),
            Supplement(name: "Магний", dose: "", slot: .evening, reminder: SupplementReminder(hour: 22, minute: 30)),
            Supplement(name: "Омега-3", dose: "2 капсулы", reminder: SupplementReminder(hour: 9, minute: 0)),
            Supplement(name: "Цинк")
        ])
        let slots = SupplementReminders.slots(data)
        XCTAssertEqual(slots.map(\.id), ["0900", "2230"])
        XCTAssertEqual(SupplementReminders.body(slots[0].items), "Креатин 5 г · Омега-3 2 капсулы")
        XCTAssertEqual(SupplementReminders.body(slots[1].items), "Магний")
    }

    func testReminderRepeatsUntilTakenToday() {
        let open = SupplementReminders.plan(data(taken: []), now: day(8, 7), calendar: calendar)
        XCTAssertEqual(open.map(\.identifier), ["strain-supplement-0900"])
        XCTAssertTrue(open[0].repeats)
        XCTAssertEqual(open[0].components.hour, 9)

        let done = SupplementReminders.plan(data(taken: [(creatineID, 8)]), now: day(8, 7), calendar: calendar)
        XCTAssertEqual(done.count, SupplementReminders.skipDays)
        XCTAssertFalse(done.contains { $0.repeats })
        XCTAssertEqual(done.first?.components.day, 9)
        XCTAssertEqual(done.first?.components.hour, 9)
        XCTAssertEqual(done.last?.components.day, 15)
    }

    // MARK: Файл и копия

    func testOldNutritionFileWithoutSupplementsOpens() throws {
        let old = #"{"entries":[]}"#
        let decoded = try JSONDecoder().decode(NutritionData.self, from: Data(old.utf8))
        XCTAssertNil(decoded.supplements)
    }

    /// Так питание с добавками пишет Android (копия — ISO 8601 без долей секунды).
    func testAndroidBackupJSON() throws {
        let json = """
        {"entries":[],"plan":null,"supplements":{"items":[{"id":"0B5B7C2E-6A43-4C79-9D8B-3F0E3C1D2A11","name":"Креатин","dose":"5 г","slot":"morning","reminder":{"hour":9,"minute":0},"created":"2026-03-01T07:00:00Z"},{"id":"1C5B7C2E-6A43-4C79-9D8B-3F0E3C1D2A12","name":"Протеин","dose":"30 г","slot":"workout","reminder":null,"created":"2026-03-01T07:00:00Z"}],"taken":[{"supplementID":"0B5B7C2E-6A43-4C79-9D8B-3F0E3C1D2A11","date":"2026-03-02T06:15:00Z"}]}}
        """
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let data = try decoder.decode(NutritionData.self, from: Data(json.utf8))
        let supplements = try XCTUnwrap(data.supplements)
        XCTAssertEqual(supplements.items.map(\.name), ["Креатин", "Протеин"])
        XCTAssertEqual(supplements.items[0].reminder, SupplementReminder(hour: 9, minute: 0))
        XCTAssertNil(supplements.items[1].reminder)
        XCTAssertEqual(supplements.items[1].slot, .workout)
        XCTAssertEqual(supplements.taken.first?.supplementID, supplements.items[0].id)

        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        let again = try decoder.decode(NutritionData.self, from: encoder.encode(data))
        XCTAssertEqual(again, data)
    }

    func testStoreKeepsSupplements() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let profile = UUID()
        let store = NutritionStore(directory: directory)
        store.open(profile)
        let item = Supplement(name: "Креатин", dose: "5 г")
        store.saveSupplement(item)
        store.toggleSupplement(item, on: Date())
        XCTAssertEqual(NutritionStore.read(profile, in: directory)?.supplements?.taken.count, 1)

        var renamed = item
        renamed.name = "Креатин моногидрат"
        store.saveSupplement(renamed)
        XCTAssertEqual(store.supplements.items.map(\.name), ["Креатин моногидрат"])

        store.deleteSupplement(renamed)
        XCTAssertNil(NutritionStore.read(profile, in: directory)?.supplements)
    }
}
