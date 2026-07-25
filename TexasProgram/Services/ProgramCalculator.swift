import Foundation

struct ProgramCalculator {
    static func roundToPlate(_ value: Double) -> Double {
        guard value.isFinite else { return 0 }
        let scaled = value / 2.5
        let rounded = scaled >= 0 ? floor(scaled + 0.5) : ceil(scaled - 0.5)
        return rounded * 2.5
    }

    static func generate(input: ProgramInput) -> WorkoutPlan {
        generateWeeks(input: input, peaking: false)
    }

    static func generatePeaking(input: ProgramInput) -> WorkoutPlan {
        generateWeeks(input: input, peaking: true)
    }

    private static func generateWeeks(input: ProgramInput, peaking: Bool) -> WorkoutPlan {
        let count = peaking ? (input.level == .beginner ? 3 : 4) : 12
        let weeks = (1...count).map { week in
            peaking ? peakWeek(week, input: input) : baseWeek(week, input: input)
        }
        return WorkoutPlan(weeks: weeks, isPeaking: peaking)
    }

    private static func baseWeek(_ week: Int, input: ProgramInput) -> WorkoutWeekPlan {
        let increment = Double(week - 1) * 2.5 - 10
        let squatDay3 = roundToPlate(input.squat5RM + increment)
        let benchDay3 = roundToPlate(input.bench5RM + increment)
        let deadDay3 = roundToPlate(input.deadlift5RM + increment)
        let squatDay1 = roundToPlate(squatDay3 * 0.9)
        let benchDay1 = roundToPlate(benchDay3 * 0.9)
        let squatDay2 = roundToPlate(squatDay1 * 0.8)
        let benchDay2 = roundToPlate(benchDay1 * 0.8)

        var d1 = [
            ExercisePrescription(name: "Приседания", sets: 5, reps: "5", load: .kilograms(squatDay1)),
            ExercisePrescription(name: "Жим лёжа", sets: 5, reps: "5", load: .kilograms(benchDay1))
        ]
        append(&d1, optional(input.pull, sets: 2, reps: "5", load: .rpe("RPE 8")))
        append(&d1, optional(input.arms, sets: 5, reps: "8–12", load: .rpe("RPE 8–9")))
        append(&d1, optional(input.core, sets: 3, reps: "5–8", load: .rpe("RPE 9")))
        var d2 = [
            ExercisePrescription(name: "Приседания", sets: 2, reps: "5", load: .kilograms(squatDay2)),
            ExercisePrescription(name: "Жим лёжа", sets: 3, reps: "5", load: .kilograms(benchDay2))
        ]
        append(&d2, optional(input.back, sets: 3, reps: "8–12", load: .rpe("RPE 8–9")))
        append(&d2, optional(input.press, sets: 2, reps: "5–8", load: .rpe("RPE 8")))
        var d3 = [
            ExercisePrescription(name: "Приседания", sets: 1, reps: "5", load: .kilograms(squatDay3)),
            ExercisePrescription(name: "Жим лёжа", sets: 1, reps: "5", load: .kilograms(benchDay3)),
            ExercisePrescription(name: "Становая тяга", sets: 1, reps: "5", load: .kilograms(deadDay3))
        ]
        append(&d3, optional(input.core, sets: 3, reps: "9–12", load: .rpe("RPE 9")))
        return WorkoutWeekPlan(id: week, number: week, days: [day(week, 1, d1), day(week, 2, d2), day(week, 3, d3)])
    }

    private static func peakWeek(_ week: Int, input: ProgramInput) -> WorkoutWeekPlan {
        let medium = input.level == .intermediate
        let increment = Double(week) * 2.5
        let squatTop = roundToPlate(input.squat5RM + increment)
        let benchTop = roundToPlate(input.bench5RM + increment)
        let deadTop = roundToPlate(input.deadlift5RM + increment)
        let d1: [ExercisePrescription]
        let d2: [ExercisePrescription]
        let d3: [ExercisePrescription]

        switch week {
        case 1:
            var firstDay = [
                ExercisePrescription(name: "Приседания", sets: medium ? 4 : 3, reps: medium ? "4" : "3", load: .kilograms(roundToPlate(squatTop * 0.9))),
                ExercisePrescription(name: "Жим лёжа", sets: medium ? 4 : 3, reps: medium ? "4" : "3", load: .kilograms(roundToPlate(benchTop * 0.9)))
            ]
            append(&firstDay, optional(input.pull, sets: medium ? 3 : 2, reps: medium ? "4" : "3", load: .rpe("RPE 8")))
            append(&firstDay, optional(input.arms, sets: 5, reps: medium ? "8–12" : "5–8", load: .rpe("RPE 8–9")))
            append(&firstDay, optional(input.core, sets: 3, reps: "5–8", load: .rpe("RPE 9")))
            d1 = firstDay
            d2 = peakDayTwo(input: input, squatDayOne: roundToPlate(squatTop * 0.9), benchDayOne: roundToPlate(benchTop * 0.9), week: week)
            d3 = peakMainDay(input: input, sets: 2, reps: "3", squat: .kilograms(squatTop), bench: .kilograms(benchTop), deadlift: .kilograms(deadTop), includeCore: true)
        case 2:
            var secondWeekFirstDay = [
                ExercisePrescription(name: "Приседания", sets: 3, reps: medium ? "3" : "2", load: .kilograms(roundToPlate(squatTop * 0.9))),
                ExercisePrescription(name: "Жим лёжа", sets: 3, reps: medium ? "3" : "2", load: .kilograms(roundToPlate(benchTop * 0.9)))
            ]
            append(&secondWeekFirstDay, optional(input.pull, sets: medium ? 3 : 2, reps: medium ? "3" : "2", load: .rpe("RPE 7")))
            if medium { append(&secondWeekFirstDay, optional(input.arms, sets: 5, reps: "5–8", load: .rpe("RPE 8–9"))); append(&secondWeekFirstDay, optional(input.core, sets: 3, reps: "5–8", load: .rpe("RPE 9"))) }
            d1 = secondWeekFirstDay
            d2 = peakDayTwo(input: input, squatDayOne: roundToPlate(squatTop * 0.9), benchDayOne: roundToPlate(benchTop * 0.9), week: week)
            d3 = peakMainDay(input: input, sets: 3, reps: medium ? "2" : "1", squat: .kilograms(squatTop), bench: .kilograms(benchTop), deadlift: .kilograms(deadTop), includeCore: medium)
        case 3:
            var thirdWeekFirstDay = [
                ExercisePrescription(name: "Приседания", sets: medium ? 3 : 2, reps: medium ? "2" : "1", load: .kilograms(medium ? roundToPlate(squatTop * 0.9) : squatTop)),
                ExercisePrescription(name: "Жим лёжа", sets: medium ? 3 : 2, reps: medium ? "2" : "1", load: .kilograms(medium ? roundToPlate(benchTop * 0.9) : benchTop))
            ]
            if medium { append(&thirdWeekFirstDay, optional(input.pull, sets: 3, reps: "2", load: .rpe("RPE 7"))) } else { thirdWeekFirstDay.append(ExercisePrescription(name: "Становая тяга", sets: 2, reps: "1", load: .kilograms(deadTop))) }
            d1 = thirdWeekFirstDay
            d2 = [
                ExercisePrescription(name: "Приседания", sets: 2, reps: medium ? "3" : "1", load: .kilograms(roundToPlate((medium ? roundToPlate(squatTop * 0.9) : squatTop) * 0.8))),
                ExercisePrescription(name: "Жим лёжа", sets: medium ? 3 : 2, reps: medium ? "3" : "1", load: .kilograms(roundToPlate((medium ? roundToPlate(benchTop * 0.9) : benchTop) * 0.8)))
            ]
            d3 = medium ? peakMainDay(input: input, sets: 3, reps: "2", squat: .kilograms(squatTop), bench: .kilograms(benchTop), deadlift: .kilograms(deadTop), includeCore: false) : peakMainDay(input: input, sets: 0, reps: "", squat: .testOneRepMax, bench: .testOneRepMax, deadlift: .testOneRepMax, includeCore: false)
        default:
            d1 = peakMainDay(input: input, sets: 2, reps: "1", squat: .kilograms(squatTop), bench: .kilograms(benchTop), deadlift: .kilograms(deadTop), includeCore: false)
            d2 = [
                ExercisePrescription(name: "Приседания", sets: 3, reps: "1", load: .kilograms(roundToPlate(squatTop * 0.8))),
                ExercisePrescription(name: "Жим лёжа", sets: 3, reps: "1", load: .kilograms(roundToPlate(benchTop * 0.8)))
            ]
            d3 = peakMainDay(input: input, sets: 0, reps: "", squat: .testOneRepMax, bench: .testOneRepMax, deadlift: .testOneRepMax, includeCore: false)
        }
        return WorkoutWeekPlan(id: week, number: week, days: [day(week, 1, d1), day(week, 2, d2), day(week, 3, d3)])
    }

    private static func peakDayTwo(input: ProgramInput, squatDayOne: Double, benchDayOne: Double, week: Int) -> [ExercisePrescription] {
        let medium = input.level == .intermediate
        var result = [
            ExercisePrescription(name: "Приседания", sets: 2, reps: week == 2 && medium ? "4" : "5", load: .kilograms(roundToPlate(squatDayOne * 0.8))),
            ExercisePrescription(name: "Жим лёжа", sets: medium ? 3 : 2, reps: week == 2 && medium ? "4" : "5", load: .kilograms(roundToPlate(benchDayOne * 0.8)))
        ]
        if week == 1, let back = optional(input.back, sets: 3, reps: medium ? "8–12" : "5–8", load: .rpe("RPE 8–9")) { result.append(back) }
        if let press = optional(input.press, sets: week == 2 && medium ? 3 : 2, reps: "5–8", load: .rpe(week == 2 ? "RPE 7" : "RPE 8")), week == 1 || medium { result.append(press) }
        return result
    }

    private static func peakMainDay(input: ProgramInput, sets: Int, reps: String, squat: LoadPrescription, bench: LoadPrescription, deadlift: LoadPrescription, includeCore: Bool) -> [ExercisePrescription] {
        var result = [
            ExercisePrescription(name: "Приседания", sets: sets, reps: reps, load: squat),
            ExercisePrescription(name: "Жим лёжа", sets: sets, reps: reps, load: bench),
            ExercisePrescription(name: "Становая тяга", sets: sets, reps: reps, load: deadlift)
        ]
        if includeCore, let core = optional(input.core, sets: 3, reps: "9–12", load: .rpe("RPE 9")) { result.append(core) }
        return result
    }

    private static func day(_ week: Int, _ number: Int, _ exercises: [ExercisePrescription]) -> WorkoutDayPlan {
        WorkoutDayPlan(id: "\(week)-\(number)", number: number, title: "ДЕНЬ \(number)", exercises: exercises)
    }

    private static func optional(_ name: String?, sets: Int, reps: String, load: LoadPrescription) -> ExercisePrescription? {
        guard let name, !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        return ExercisePrescription(name: name, sets: sets, reps: reps, load: load, isOptional: true)
    }

    private static func append(_ array: inout [ExercisePrescription], _ value: ExercisePrescription?) {
        if let value { array.append(value) }
    }
}
