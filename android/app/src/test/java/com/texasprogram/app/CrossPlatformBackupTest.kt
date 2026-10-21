package com.texasprogram.app

import com.texasprogram.app.data.BackupService
import com.texasprogram.app.model.LiftOutcome
import com.texasprogram.app.model.MainLift
import com.texasprogram.app.model.PlanEditKind
import com.texasprogram.app.model.SplitTemplate
import com.texasprogram.app.model.TrainingProgramKind
import com.texasprogram.app.service.EuthanasiaLevel
import com.texasprogram.app.service.FullBodyLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/// Копия с iPhone открывается на Android целиком и без потерь, и наоборот.
/// Раньше одно поле с подписью вместо имени константы («Средний» вместо
/// «MEDIUM») или дата вместо номера дня роняли разбор всей копии.
class CrossPlatformBackupTest {
    private fun day(iso: String) = Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    private fun profiles(json: String) =
        BackupService.parse(json).profiles.map(BackupService::profile).associateBy { it.name }

    @Test
    fun backupFromIphoneOpensOnAndroid() {
        val restored = profiles(FROM_IOS)
        assertEquals(4, restored.size)

        val texas = restored.getValue("Техас")
        assertEquals(TrainingProgramKind.TEXAS, texas.programKind)
        assertEquals(1, texas.liftReports.size)
        assertEquals(MainLift.BENCH, texas.liftReports[0].lift)
        assertEquals(LiftOutcome.GRIND, texas.liftReports[0].outcome)
        assertEquals(day("2026-10-14T18:30:00Z"), texas.liftReports[0].epochDay)
        assertEquals(1, texas.skipped.size)
        assertEquals(4, texas.skipped[0].week)
        assertTrue(texas.skipped[0].holdsProgression)
        assertEquals(day("2026-10-15T09:00:00Z"), texas.skipped[0].epochDay)
        assertEquals(PlanEditKind.REPLACE, texas.planEdits.single().kind)
        assertEquals("Жим лёжа", texas.planEdits.single().targetKey)
        assertEquals(5, texas.setLog.getValue("1-1|Приседания|0").reps)
        assertEquals("Пояс с третьего подхода", texas.workoutNotes["1-1"])
        assertEquals(day("2026-10-01T21:00:00Z"), texas.bodyWeightLog.single().epochDay)
        assertEquals(1.25, texas.weightSteps.getValue("Жим лёжа"), 0.0)
        assertTrue(texas.autoregulationEnabled)
        assertEquals(listOf(2, 4, 6), texas.scheduleWeekdays)
        assertEquals("8/10", texas.completionLog.single().effort)

        assertEquals(FullBodyLevel.UNDER_YEAR, restored.getValue("Фулбади").fullBodyLevel)
        assertEquals(EuthanasiaLevel.MEDIUM, restored.getValue("Эвтаназия").euthanasiaInput?.level)
        val custom = restored.getValue("Своя").customProgram!!
        assertEquals(SplitTemplate.UPPER_LOWER, custom.template)
        assertEquals(5.0, custom.days.single().exercises.single().weeklyIncrement, 0.0)

        // Подходы и правки «Эвтаназии» переходят на здешние ключи и находят свои упражнения.
        val euthanasia = restored.getValue("Эвтаназия")
        assertEquals(87.5, euthanasia.setLog.getValue("1-1|test-max-PULL|0").weight, 0.0)
        assertEquals(5, euthanasia.setLog.getValue("2-3|main-PRESS-0|2").reps)
        assertEquals("main-SQUAT-1", euthanasia.planEdits.single { it.kind == PlanEditKind.HIDE }.targetKey)
        val firstDay = euthanasia.workoutPlan.weeks.first().days.first().exercises.map { it.key }
        assertEquals(listOf("test-time-PULL", "test-max-PULL"), firstDay.take(2))
    }

    @Test
    fun euthanasiaKeysFromIphone() {
        assertEquals("1-1|main-PULL-0|2", BackupService.exerciseKey("1-1|main-Тяга-0|2"))
        assertEquals("density-PRESS", BackupService.exerciseKey("density-Жим"))
        assertEquals("3-2|test-time-SQUAT|0", BackupService.exerciseKey("3-2|test-time-Присед|0"))
        assertEquals("1-1|test-max-PULL|0", BackupService.exerciseKey("1-1|test-max-PULL|0"))
        assertEquals("1-1|Приседания|0", BackupService.exerciseKey("1-1|Приседания|0"))
        assertEquals("main-Становая-0", BackupService.exerciseKey("main-Становая-0"))
    }

    @Test
    fun ownBackupStillOpens() {
        val restored = profiles(FROM_ANDROID)
        assertEquals(4, restored.size)
        val texas = restored.getValue("Техас")
        assertEquals(20740L, texas.liftReports.single().epochDay)
        assertEquals(20741L, texas.skipped.single().epochDay)
        assertEquals(FullBodyLevel.UNDER_YEAR, restored.getValue("Фулбади").fullBodyLevel)
        assertEquals(EuthanasiaLevel.MEDIUM, restored.getValue("Эвтаназия").euthanasiaInput?.level)
        assertEquals(setOf("1-1|test-max-PULL|0", "2-3|main-PRESS-0|2"), restored.getValue("Эвтаназия").setLog.keys)
        assertEquals(SplitTemplate.UPPER_LOWER, restored.getValue("Своя").customProgram!!.template)
    }

    /// Новая копия несёт и номер дня, и дату — её прочтёт и старая, и новая версия iOS.
    @Test
    fun exportCarriesBothDateForms() {
        val texas = profiles(FROM_ANDROID).getValue("Техас")
        val json = BackupService.export(listOf(texas))
        val snapshot = BackupService.parse(json).profiles.single()
        assertEquals(20740L, snapshot.liftReports.single().epochDay)
        assertTrue(snapshot.liftReports.single().date!!.endsWith("Z"))
        assertEquals(20741L, snapshot.skipped.single().epochDay)
        assertTrue(snapshot.skipped.single().date!!.endsWith("Z"))
        assertEquals(day(snapshot.liftReports.single().date!!), 20740L)
    }

    private companion object {
        const val FROM_IOS = """{
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
}"""

        const val FROM_ANDROID = """{
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
}"""
    }
}
