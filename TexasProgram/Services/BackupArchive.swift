import Foundation
import SwiftData

/// Снимок одного профиля для переноса между устройствами.
///
/// Формат намеренно плоский и совпадает по именам полей с Android-версией:
/// файл, выгруженный на iPhone, читается на Android и наоборот.
struct ProfileSnapshot: Codable, Equatable, Sendable {
    var id: String
    var name: String
    var createdAtEpochDay: Int
    var programKind: String
    var squat5RM: Double
    var bench5RM: Double
    var deadlift5RM: Double
    var level: String
    var pull: String?
    var arms: String?
    var core: String?
    var back: String?
    var press: String?
    var completedDayKeys: [String]
    var completedBenchSessions: [Int]
    var scheduleWeekdays: [Int]
    var scheduleMode: String
    var cycleStartedEpochDay: Int
    var lastCompletionEpochDay: Int?
    var peakingActive: Bool
    var peakSquat5RM: Double?
    var peakBench5RM: Double?
    var peakDeadlift5RM: Double?
    var completionLog: [CompletionSnapshot]
    /// Свои упражнения и правки дней. Необязательное поле: в копиях, снятых до
    /// появления правок, и в файлах с Android его нет, а синтезированный
    /// декодер Swift не подставляет значения по умолчанию за отсутствующий ключ.
    var planEdits: [PlanEdit]?
    /// Фактически выполненные подходы и откаты. Тоже необязательные: в копиях,
    /// снятых раньше, этих полей нет.
    var setLog: [String: SetEntry]?
    var skipped: [SkippedWorkout]?
    var keepScreenOn: Bool?
    /// Номер цикла и своя программа. Необязательные — в старых копиях их нет.
    var cycleNumber: Int?
    var customProgram: CustomProgram?
    var fullBodyLevel: String?
    var euthanasiaInput: EuthanasiaInput?
    var autoregulationEnabled: Bool?
    var liftReports: [LiftReport]?
    var workoutNotes: [String: String]?
    var bodyWeightLog: [BodyWeightEntry]?
    /// Шаг прибавки по упражнениям. Необязательное: в старых копиях его нет.
    var weightSteps: [String: Double]?
    var defaultWeightStep: Double?
    var stallDismissed: [String: Double]?
}

struct CompletionSnapshot: Codable, Equatable, Sendable {
    var key: String
    var epochDay: Int
    var week: Int
    var day: Int
    var squat: Double?
    var bench: Double?
    var deadlift: Double?
    /// Факт, время и усилие тренировки. Необязательные: в копиях, снятых
    /// раньше, их нет — без них история теряла бы итоги тренировок.
    var actualSquat: Double? = nil
    var actualBench: Double? = nil
    var actualDeadlift: Double? = nil
    var durationSeconds: Int? = nil
    var effort: String? = nil
}

/// Корень файла резервной копии.
struct BackupArchive: Codable, Equatable, Sendable {
    /// Версия формата: если поля поменяются, старые файлы можно будет опознать.
    var version: Int = 1
    var exportedAt: Date = .now
    var profiles: [ProfileSnapshot]
    /// Код входа в профиль ленты: после переустановки профиль возвращается
    /// вместе с данными. Необязательный — в старых копиях его нет.
    var socialToken: String? = nil

    init(version: Int = 1, exportedAt: Date = .now, profiles: [ProfileSnapshot], socialToken: String? = nil) {
        self.version = version
        self.exportedAt = exportedAt
        self.profiles = profiles
        self.socialToken = socialToken
    }

    /// Дата выгрузки — только для справки. Android-версия долго писала сюда
    /// пустую строку, и строгий разбор отвергал всю копию целиком.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        version = try c.decodeIfPresent(Int.self, forKey: .version) ?? 1
        exportedAt = (try? c.decodeIfPresent(Date.self, forKey: .exportedAt)) ?? .now
        profiles = try c.decode([ProfileSnapshot].self, forKey: .profiles)
        socialToken = try c.decodeIfPresent(String.self, forKey: .socialToken)
    }
}

enum BackupService {
    static let fileName = "strain-backup.json"

    private static var encoder: JSONEncoder {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        return encoder
    }

    private static var decoder: JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }

    // MARK: - Выгрузка

    static func export(_ profiles: [ProgramProfile], socialToken: String? = SocialKeychain.load()) throws -> Data {
        try encoder.encode(BackupArchive(profiles: profiles.map(snapshot), socialToken: socialToken))
    }

    /// Пишет копию во временный файл — его отдаёт системный лист «Поделиться».
    static func writeTemporaryFile(_ profiles: [ProgramProfile]) throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
        try export(profiles).write(to: url, options: .atomic)
        return url
    }

    private static func snapshot(_ profile: ProgramProfile) -> ProfileSnapshot {
        ProfileSnapshot(
            id: profile.profileID.uuidString,
            name: profile.name,
            createdAtEpochDay: epochDay(profile.createdAt),
            programKind: profile.programKind.backupCode,
            squat5RM: profile.squat5RM,
            bench5RM: profile.bench5RM,
            deadlift5RM: profile.deadlift5RM,
            level: profile.level == .beginner ? "BEGINNER" : "INTERMEDIATE",
            pull: profile.pull,
            arms: profile.arms,
            core: profile.core,
            back: profile.back,
            press: profile.press,
            completedDayKeys: profile.completedDayKeys,
            completedBenchSessions: profile.completedBenchSessions,
            scheduleWeekdays: profile.scheduleWeekdays,
            scheduleMode: profile.scheduleMode == .queue ? "QUEUE" : "WEEKDAY",
            cycleStartedEpochDay: epochDay(profile.cycleStartedAt),
            lastCompletionEpochDay: profile.lastCompletionDate.map(epochDay),
            peakingActive: profile.peakingActive,
            peakSquat5RM: profile.peakSquat5RM,
            peakBench5RM: profile.peakBench5RM,
            peakDeadlift5RM: profile.peakDeadlift5RM,
            completionLog: profile.completionLog.map {
                CompletionSnapshot(
                    key: $0.key,
                    epochDay: epochDay($0.date),
                    week: $0.week,
                    day: $0.day,
                    squat: $0.squat,
                    bench: $0.bench,
                    deadlift: $0.deadlift,
                    actualSquat: $0.actualSquat,
                    actualBench: $0.actualBench,
                    actualDeadlift: $0.actualDeadlift,
                    durationSeconds: $0.durationSeconds,
                    effort: $0.effort
                )
            },
            planEdits: profile.planEdits,
            setLog: profile.setLog,
            skipped: profile.skipped,
            keepScreenOn: profile.keepScreenOn,
            cycleNumber: profile.cycleNumber,
            customProgram: profile.customProgram,
            fullBodyLevel: profile.fullBodyLevelRaw,
            euthanasiaInput: profile.euthanasiaInput,
            autoregulationEnabled: profile.autoregulationEnabled,
            liftReports: profile.liftReports,
            workoutNotes: profile.workoutNotes,
            bodyWeightLog: profile.bodyWeightLog,
            weightSteps: profile.weightSteps,
            defaultWeightStep: profile.defaultWeightStep,
            stallDismissed: profile.stallDismissed
        )
    }

    // MARK: - Загрузка

    static func archive(from data: Data) throws -> BackupArchive {
        try decoder.decode(BackupArchive.self, from: data)
    }

    /// Восстанавливает профили в хранилище, заменяя существующие с тем же идентификатором.
    ///
    /// Профиль с тем же идентификатором переписывается на месте, а не удаляется:
    /// его в этот момент показывают открытые экраны, и обращение к удалённой
    /// модели SwiftData роняет приложение.
    @discardableResult
    static func restore(_ archive: BackupArchive, into context: ModelContext, existing: [ProgramProfile]) -> Int {
        for snapshot in archive.profiles {
            if let match = existing.first(where: { $0.profileID.uuidString == snapshot.id }) {
                fill(match, from: snapshot)
            } else {
                let result = ProgramProfile(input: .demo, name: snapshot.name)
                fill(result, from: snapshot)
                context.insert(result)
            }
        }
        try? context.save()
        return archive.profiles.count
    }

    private static func fill(_ result: ProgramProfile, from snapshot: ProfileSnapshot) {
        result.name = snapshot.name
        result.profileID = UUID(uuidString: snapshot.id) ?? result.profileID
        result.createdAt = date(snapshot.createdAtEpochDay)
        result.programKind = TrainingProgramKind(backupCode: snapshot.programKind)
        result.squat5RM = snapshot.squat5RM
        result.bench5RM = snapshot.bench5RM
        result.deadlift5RM = snapshot.deadlift5RM
        result.level = snapshot.level == "INTERMEDIATE" ? .intermediate : .beginner
        result.pull = snapshot.pull
        result.arms = snapshot.arms
        result.core = snapshot.core
        result.back = snapshot.back
        result.press = snapshot.press
        result.completedDayKeys = snapshot.completedDayKeys
        result.completedBenchSessions = snapshot.completedBenchSessions
        result.scheduleWeekdays = snapshot.scheduleWeekdays
        result.scheduleMode = snapshot.scheduleMode == "QUEUE" ? .queue : .weekday
        result.cycleStartedAt = date(snapshot.cycleStartedEpochDay)
        result.lastCompletionDate = snapshot.lastCompletionEpochDay.map(date)
        result.peakingActive = snapshot.peakingActive
        result.peakSquat5RM = snapshot.peakSquat5RM
        result.peakBench5RM = snapshot.peakBench5RM
        result.peakDeadlift5RM = snapshot.peakDeadlift5RM
        result.completionLog = snapshot.completionLog.map {
            CompletionRecord(
                key: $0.key,
                date: date($0.epochDay),
                week: $0.week,
                day: $0.day,
                squat: $0.squat,
                bench: $0.bench,
                deadlift: $0.deadlift,
                actualSquat: $0.actualSquat,
                actualBench: $0.actualBench,
                actualDeadlift: $0.actualDeadlift,
                durationSeconds: $0.durationSeconds,
                effort: $0.effort
            )
        }
        result.planEdits = snapshot.planEdits ?? []
        result.setLog = snapshot.setLog ?? [:]
        result.skipped = snapshot.skipped ?? []
        result.keepScreenOn = snapshot.keepScreenOn ?? true
        result.cycleNumber = snapshot.cycleNumber ?? 1
        result.customProgram = snapshot.customProgram
        result.fullBodyLevelRaw = snapshot.fullBodyLevel ?? FullBodyLevel.aboutYear.rawValue
        result.euthanasiaInput = snapshot.euthanasiaInput
        result.autoregulationEnabled = snapshot.autoregulationEnabled ?? false
        result.liftReports = snapshot.liftReports ?? []
        result.workoutNotes = snapshot.workoutNotes ?? [:]
        result.bodyWeightLog = snapshot.bodyWeightLog ?? []
        result.weightSteps = snapshot.weightSteps ?? [:]
        result.defaultWeightStep = snapshot.defaultWeightStep ?? AccessoryHint.step
        result.stallDismissed = snapshot.stallDismissed ?? [:]
    }

    // MARK: - Даты

    /// Номер дня от 1970-01-01 по местной дате — как `LocalDate.toEpochDay()`
    /// в Android-версии. Раньше считали по UTC, и тренировка в час ночи
    /// по Москве после переноса оказывалась во вчерашнем дне.
    static func epochDay(_ date: Date) -> Int {
        let parts = local.dateComponents([.year, .month, .day], from: date)
        guard let day = utc.date(from: parts) else { return Int(floor(date.timeIntervalSince1970 / 86_400)) }
        return Int((day.timeIntervalSince1970 / 86_400).rounded())
    }

    /// Полдень той же местной даты: от него далеко до границ суток в любом поясе.
    static func date(_ epochDay: Int) -> Date {
        let midnight = Date(timeIntervalSince1970: Double(epochDay) * 86_400)
        var parts = utc.dateComponents([.year, .month, .day], from: midnight)
        parts.hour = 12
        return local.date(from: parts) ?? midnight
    }

    private static var local: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .current
        return calendar
    }

    private static let utc: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC") ?? .gmt
        return calendar
    }()
}
