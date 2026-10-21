import SwiftData
import XCTest
@testable import TexasProgram

/// Копия с Android открывается на iPhone целиком и без потерь, и наоборот.
/// Раньше одно поле с именем константы вместо подписи («MEDIUM» вместо
/// «Средний») или номер дня вместо даты роняли разбор всей копии.
@MainActor
final class CrossPlatformBackupTests: XCTestCase {
    private var container: ModelContainer?

    private func restore(_ json: String) throws -> [String: ProgramProfile] {
        let archive = try BackupService.archive(from: Data(json.utf8))
        let container = try ModelContainer(for: ProgramProfile.self, configurations: ModelConfiguration(isStoredInMemoryOnly: true))
        self.container = container
        let context = ModelContext(container)
        BackupService.restore(archive, into: context, existing: [])
        let profiles = try context.fetch(FetchDescriptor<ProgramProfile>())
        return Dictionary(uniqueKeysWithValues: profiles.map { ($0.name, $0) })
    }

    func testBackupFromAndroidOpensOnIphone() throws {
        let restored = try restore(Self.fromAndroid)
        XCTAssertEqual(restored.count, 4)

        let texas = try XCTUnwrap(restored["Техас"])
        XCTAssertEqual(texas.programKind, .texas)
        XCTAssertEqual(texas.liftReports.count, 1)
        XCTAssertEqual(texas.liftReports.first?.lift, .bench)
        XCTAssertEqual(texas.liftReports.first?.outcome, .grind)
        XCTAssertEqual(texas.liftReports.first.map { BackupService.epochDay($0.date) }, 20740)
        XCTAssertEqual(texas.skipped.count, 1)
        XCTAssertEqual(texas.skipped.first?.week, 4)
        XCTAssertEqual(texas.skipped.first?.holdsProgression, true)
        XCTAssertEqual(texas.skipped.first.map { BackupService.epochDay($0.date) }, 20741)
        XCTAssertEqual(texas.planEdits.first?.kind, .replace)
        XCTAssertEqual(texas.planEdits.first?.targetID, "Жим лёжа")
        XCTAssertEqual(texas.setLog["1-1|Приседания|0"]?.reps, 5)
        XCTAssertEqual(texas.workoutNotes["1-1"], "Пояс с третьего подхода")
        XCTAssertEqual(texas.bodyWeightLog.count, 1)
        XCTAssertEqual(texas.weightSteps["Жим лёжа"], 1.25)
        XCTAssertTrue(texas.autoregulationEnabled)
        XCTAssertEqual(texas.scheduleWeekdays, [2, 4, 6])
        XCTAssertEqual(texas.completionLog.first?.effort, "8/10")

        XCTAssertEqual(restored["Фулбади"]?.fullBodyLevel, .underYear)
        XCTAssertEqual(restored["Эвтаназия"]?.euthanasiaInput?.level, .medium)
        XCTAssertEqual(restored["Своя"]?.customProgram?.template, .upperLower)
        XCTAssertEqual(restored["Своя"]?.customProgram?.days.first?.exercises.first?.weeklyIncrement, 5)

        // Подходы и правки «Эвтаназии» переходят на здешние ключи и находят свои упражнения.
        let euthanasia = try XCTUnwrap(restored["Эвтаназия"])
        XCTAssertEqual(euthanasia.setLog["1-1|test-max-Тяга|0"]?.weight, 87.5)
        XCTAssertEqual(euthanasia.setLog["2-3|main-Жим-0|2"]?.reps, 5)
        XCTAssertEqual(euthanasia.planEdits.first { $0.kind == .hide }?.targetID, "main-Присед-1")
        let firstDay = euthanasia.workoutPlan.weeks.first?.days.first?.exercises.map(\.id) ?? []
        XCTAssertEqual(Array(firstDay.prefix(2)), ["test-time-Тяга", "test-max-Тяга"])
    }

    func testOwnBackupStillOpens() throws {
        let restored = try restore(Self.fromIOS)
        XCTAssertEqual(restored.count, 4)
        let texas = try XCTUnwrap(restored["Техас"])
        XCTAssertEqual(texas.liftReports.first?.outcome, .grind)
        XCTAssertEqual(texas.skipped.first?.week, 4)
        XCTAssertEqual(restored["Фулбади"]?.fullBodyLevel, .underYear)
        XCTAssertEqual(restored["Эвтаназия"]?.euthanasiaInput?.level, .medium)
        XCTAssertEqual(restored["Эвтаназия"].map { Set($0.setLog.keys) }, ["1-1|test-max-Тяга|0", "2-3|main-Жим-0|2"])
        XCTAssertEqual(restored["Своя"]?.customProgram?.template, .upperLower)
    }

    func testEuthanasiaKeysFromAndroid() {
        XCTAssertEqual(BackupEnum.exerciseKey("1-1|main-PULL-0|2"), "1-1|main-Тяга-0|2")
        XCTAssertEqual(BackupEnum.exerciseKey("density-PRESS"), "density-Жим")
        XCTAssertEqual(BackupEnum.exerciseKey("3-2|test-time-SQUAT|0"), "3-2|test-time-Присед|0")
        XCTAssertEqual(BackupEnum.exerciseKey("1-1|test-max-Тяга|0"), "1-1|test-max-Тяга|0")
        XCTAssertEqual(BackupEnum.exerciseKey("1-1|Приседания|0"), "1-1|Приседания|0")
        XCTAssertEqual(BackupEnum.exerciseKey("main-Становая-0"), "main-Становая-0")
    }

    /// Новая копия несёт и дату, и номер дня — её прочтёт и старая, и новая версия Android.
    func testExportCarriesBothDateForms() throws {
        let texas = try XCTUnwrap(try restore(Self.fromIOS)["Техас"])
        let snapshot = try XCTUnwrap(BackupService.archive(from: BackupService.export([texas], socialToken: nil)).profiles.first)
        let report = try XCTUnwrap(snapshot.liftReports?.first)
        XCTAssertNotNil(report.date)
        XCTAssertEqual(report.epochDay, report.date.map(BackupService.epochDay))
        let skip = try XCTUnwrap(snapshot.skipped?.first)
        XCTAssertNotNil(skip.date)
        XCTAssertEqual(skip.epochDay, skip.date.map(BackupService.epochDay))
    }

    func testAndroidCodes() {
        XCTAssertEqual(BackupEnum.androidCode("fullBodyEOD"), "FULL_BODY_EOD")
        XCTAssertEqual(BackupEnum.androidCode("pushPullLegs"), "PUSH_PULL_LEGS")
        XCTAssertEqual(BackupEnum.androidCode("underYear"), "UNDER_YEAR")
        XCTAssertEqual(BackupEnum.value(SplitTemplate.self, "FULL_BODY_EOD"), .fullBodyEOD)
        XCTAssertEqual(BackupEnum.value(LiftOutcome.self, "MISSED"), .missed)
        XCTAssertEqual(BackupEnum.value(LiftOutcome.self, "Легко"), .easy)
    }

    static let fromIOS = #"""
{
  "exportedAt": "2026-10-21T12:00:00Z",
  "profiles": [
    {
      "autoregulationEnabled": true,
      "bench5RM": 97.5,
      "bodyWeightLog": [
        {
          "date": "2026-10-01T21:00:00Z",
          "weight": 82.5
        }
      ],
      "completedBenchSessions": [],
      "completedDayKeys": [
        "1-1",
        "1-2"
      ],
      "completionLog": [
        {
          "day": 1,
          "durationSeconds": 3720,
          "effort": "8/10",
          "epochDay": 20731,
          "key": "1-1",
          "squat": 142.5,
          "week": 1
        }
      ],
      "core": "Копенгагенская планка",
      "createdAtEpochDay": 20724,
      "cycleNumber": 1,
      "cycleStartedEpochDay": 20724,
      "deadlift5RM": 187.5,
      "defaultWeightStep": 2.5,
      "id": "8B2F6C1E-1111-4C3A-9D2E-000000000001",
      "keepScreenOn": true,
      "lastCompletionEpochDay": 20731,
      "level": "INTERMEDIATE",
      "liftReports": [
        {
          "date": "2026-10-14T18:30:00Z",
          "key": "3",
          "lift": "Жим лёжа",
          "outcome": "На пределе"
        }
      ],
      "name": "Техас",
      "peakingActive": false,
      "planEdits": [
        {
          "day": 2,
          "id": "custom-1",
          "isPeaking": false,
          "kilograms": 40,
          "kind": "replace",
          "name": "Жим гантелей",
          "reps": "8–12",
          "sets": 3,
          "targetID": "Жим лёжа",
          "week": 3
        }
      ],
      "programKind": "TEXAS",
      "scheduleMode": "WEEKDAY",
      "scheduleWeekdays": [
        2,
        4,
        6
      ],
      "setLog": {
        "1-1|Приседания|0": {
          "reps": 5,
          "weight": 142.5
        }
      },
      "skipped": [
        {
          "date": "2026-10-15T09:00:00Z",
          "day": 2,
          "holdsProgression": true,
          "id": "s1",
          "isPeaking": false,
          "week": 4
        }
      ],
      "squat5RM": 142.5,
      "stallDismissed": {},
      "weightSteps": {
        "Жим лёжа": 1.25
      },
      "workoutNotes": {
        "1-1": "Пояс с третьего подхода"
      }
    },
    {
      "bench5RM": 80,
      "completedBenchSessions": [],
      "completedDayKeys": [],
      "completionLog": [],
      "createdAtEpochDay": 20724,
      "cycleStartedEpochDay": 20724,
      "deadlift5RM": 120,
      "fullBodyLevel": "Меньше года",
      "id": "8B2F6C1E-1111-4C3A-9D2E-000000000002",
      "level": "BEGINNER",
      "name": "Фулбади",
      "peakingActive": false,
      "programKind": "FULL_BODY",
      "scheduleMode": "QUEUE",
      "scheduleWeekdays": [],
      "squat5RM": 100
    },
    {
      "bench5RM": 80,
      "completedBenchSessions": [],
      "completedDayKeys": [],
      "completionLog": [],
      "createdAtEpochDay": 20724,
      "cycleStartedEpochDay": 20724,
      "deadlift5RM": 120,
      "euthanasiaInput": {
        "level": "Средний",
        "press": {
          "exercise": "Жим классический",
          "oneRepMax": 72.5,
          "testMinutes": 31
        },
        "pull": {
          "exercise": "Тяга в наклоне",
          "oneRepMax": 87.5,
          "testMinutes": 23
        },
        "squat": {
          "exercise": "Присед классический",
          "oneRepMax": 132.5,
          "testMinutes": 27.5
        }
      },
      "id": "8B2F6C1E-1111-4C3A-9D2E-000000000003",
      "level": "BEGINNER",
      "name": "Эвтаназия",
      "peakingActive": false,
      "programKind": "EUTHANASIA",
      "scheduleMode": "QUEUE",
      "scheduleWeekdays": [],
      "planEdits": [
        {
          "day": 1,
          "id": "order-1",
          "isPeaking": false,
          "kind": "order",
          "name": "",
          "order": ["test-time-Тяга", "test-max-Тяга"],
          "reps": "8–12",
          "sets": 3
        },
        {
          "day": 3,
          "id": "hide-1",
          "isPeaking": false,
          "kind": "hide",
          "name": "",
          "reps": "8–12",
          "sets": 3,
          "targetID": "main-Присед-1",
          "week": 2
        }
      ],
      "setLog": {
        "1-1|test-max-Тяга|0": {
          "reps": 1,
          "weight": 87.5
        },
        "2-3|main-Жим-0|2": {
          "reps": 5,
          "weight": 55
        }
      },
      "squat5RM": 100
    },
    {
      "bench5RM": 80,
      "completedBenchSessions": [],
      "completedDayKeys": [],
      "completionLog": [],
      "createdAtEpochDay": 20724,
      "customProgram": {
        "days": [
          {
            "exercises": [
              {
                "id": "own-a",
                "kilograms": 60,
                "name": "Тяга",
                "reps": "6",
                "sets": 4,
                "weeklyIncrement": 5
              }
            ],
            "id": "d1",
            "number": 1,
            "title": "ВЕРХ ТЯЖЁЛЫЙ"
          }
        ],
        "name": "Моя",
        "template": "Верх / Низ",
        "weeks": 3
      },
      "cycleStartedEpochDay": 20724,
      "deadlift5RM": 120,
      "id": "8B2F6C1E-1111-4C3A-9D2E-000000000004",
      "level": "BEGINNER",
      "name": "Своя",
      "peakingActive": false,
      "programKind": "CUSTOM",
      "scheduleMode": "QUEUE",
      "scheduleWeekdays": [],
      "squat5RM": 100
    }
  ],
  "version": 1
}
"""#

    static let fromAndroid = #"""
{
  "exportedAt": "2026-10-21T12:00:00Z",
  "profiles": [
    {
      "arms": null,
      "autoregulationEnabled": true,
      "back": null,
      "bench5RM": 97.5,
      "bodyWeightLog": [
        {
          "date": "2026-10-01T21:00:00Z",
          "weight": 82.5
        }
      ],
      "completedBenchSessions": [],
      "completedDayKeys": [
        "1-1",
        "1-2"
      ],
      "completionLog": [
        {
          "day": 1,
          "durationSeconds": 3720,
          "effort": "8/10",
          "epochDay": 20731,
          "key": "1-1",
          "squat": 142.5,
          "week": 1
        }
      ],
      "core": "Копенгагенская планка",
      "createdAtEpochDay": 20724,
      "customProgram": null,
      "cycleNumber": 1,
      "cycleStartedEpochDay": 20724,
      "deadlift5RM": 187.5,
      "defaultWeightStep": 2.5,
      "euthanasiaInput": null,
      "fullBodyLevel": "ABOUT_YEAR",
      "id": "8B2F6C1E-1111-4C3A-9D2E-000000000001",
      "keepScreenOn": true,
      "lastCompletionEpochDay": 20731,
      "level": "INTERMEDIATE",
      "liftReports": [
        {
          "epochDay": 20740,
          "key": "3",
          "lift": "BENCH",
          "outcome": "GRIND"
        }
      ],
      "name": "Техас",
      "peakBench5RM": null,
      "peakDeadlift5RM": null,
      "peakSquat5RM": null,
      "peakingActive": false,
      "planEdits": [
        {
          "day": 2,
          "id": "custom-1",
          "isPeaking": false,
          "kilograms": 40,
          "kind": "replace",
          "name": "Жим гантелей",
          "reps": "8–12",
          "sets": 3,
          "targetID": "Жим лёжа",
          "week": 3
        }
      ],
      "press": null,
      "programKind": "TEXAS",
      "pull": null,
      "scheduleMode": "WEEKDAY",
      "scheduleWeekdays": [
        2,
        4,
        6
      ],
      "setLog": {
        "1-1|Приседания|0": {
          "reps": 5,
          "weight": 142.5
        }
      },
      "skipped": [
        {
          "day": 2,
          "epochDay": 20741,
          "holdsProgression": true,
          "id": "s1",
          "isPeaking": false,
          "week": 4
        }
      ],
      "squat5RM": 142.5,
      "stallDismissed": {},
      "weightSteps": {
        "Жим лёжа": 1.25
      },
      "workoutNotes": {
        "1-1": "Пояс с третьего подхода"
      }
    },
    {
      "arms": null,
      "back": null,
      "bench5RM": 80,
      "completedBenchSessions": [],
      "completedDayKeys": [],
      "completionLog": [],
      "core": null,
      "createdAtEpochDay": 20724,
      "customProgram": null,
      "cycleStartedEpochDay": 20724,
      "deadlift5RM": 120,
      "euthanasiaInput": null,
      "fullBodyLevel": "UNDER_YEAR",
      "id": "8B2F6C1E-1111-4C3A-9D2E-000000000002",
      "lastCompletionEpochDay": null,
      "level": "BEGINNER",
      "name": "Фулбади",
      "peakBench5RM": null,
      "peakDeadlift5RM": null,
      "peakSquat5RM": null,
      "peakingActive": false,
      "press": null,
      "programKind": "FULL_BODY",
      "pull": null,
      "scheduleMode": "QUEUE",
      "scheduleWeekdays": [],
      "squat5RM": 100
    },
    {
      "arms": null,
      "back": null,
      "bench5RM": 80,
      "completedBenchSessions": [],
      "completedDayKeys": [],
      "completionLog": [],
      "core": null,
      "createdAtEpochDay": 20724,
      "customProgram": null,
      "cycleStartedEpochDay": 20724,
      "deadlift5RM": 120,
      "euthanasiaInput": {
        "level": "MEDIUM",
        "press": {
          "exercise": "Жим классический",
          "oneRepMax": 72.5,
          "testMinutes": 31
        },
        "pull": {
          "exercise": "Тяга в наклоне",
          "oneRepMax": 87.5,
          "testMinutes": 23
        },
        "squat": {
          "exercise": "Присед классический",
          "oneRepMax": 132.5,
          "testMinutes": 27.5
        }
      },
      "fullBodyLevel": "ABOUT_YEAR",
      "id": "8B2F6C1E-1111-4C3A-9D2E-000000000003",
      "lastCompletionEpochDay": null,
      "level": "BEGINNER",
      "name": "Эвтаназия",
      "peakBench5RM": null,
      "peakDeadlift5RM": null,
      "peakSquat5RM": null,
      "peakingActive": false,
      "press": null,
      "programKind": "EUTHANASIA",
      "pull": null,
      "scheduleMode": "QUEUE",
      "scheduleWeekdays": [],
      "planEdits": [
        {
          "day": 1,
          "id": "order-1",
          "isPeaking": false,
          "kind": "order",
          "name": "",
          "order": ["test-time-PULL", "test-max-PULL"],
          "reps": "8–12",
          "sets": 3
        },
        {
          "day": 3,
          "id": "hide-1",
          "isPeaking": false,
          "kind": "hide",
          "name": "",
          "reps": "8–12",
          "sets": 3,
          "targetID": "main-SQUAT-1",
          "week": 2
        }
      ],
      "setLog": {
        "1-1|test-max-PULL|0": {
          "reps": 1,
          "weight": 87.5
        },
        "2-3|main-PRESS-0|2": {
          "reps": 5,
          "weight": 55
        }
      },
      "squat5RM": 100
    },
    {
      "arms": null,
      "back": null,
      "bench5RM": 80,
      "completedBenchSessions": [],
      "completedDayKeys": [],
      "completionLog": [],
      "core": null,
      "createdAtEpochDay": 20724,
      "customProgram": {
        "days": [
          {
            "exercises": [
              {
                "id": "own-a",
                "kilograms": 60,
                "name": "Тяга",
                "reps": "6",
                "sets": 4,
                "weeklyIncrement": 5
              }
            ],
            "id": "d1",
            "number": 1,
            "title": "ВЕРХ ТЯЖЁЛЫЙ"
          }
        ],
        "name": "Моя",
        "template": "UPPER_LOWER",
        "weeks": 3
      },
      "cycleStartedEpochDay": 20724,
      "deadlift5RM": 120,
      "euthanasiaInput": null,
      "fullBodyLevel": "ABOUT_YEAR",
      "id": "8B2F6C1E-1111-4C3A-9D2E-000000000004",
      "lastCompletionEpochDay": null,
      "level": "BEGINNER",
      "name": "Своя",
      "peakBench5RM": null,
      "peakDeadlift5RM": null,
      "peakSquat5RM": null,
      "peakingActive": false,
      "press": null,
      "programKind": "CUSTOM",
      "pull": null,
      "scheduleMode": "QUEUE",
      "scheduleWeekdays": [],
      "squat5RM": 100
    }
  ],
  "socialToken": null,
  "version": 1
}
"""#
}
