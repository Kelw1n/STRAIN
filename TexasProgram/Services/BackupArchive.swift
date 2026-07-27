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
}

struct CompletionSnapshot: Codable, Equatable, Sendable {
    var key: String
    var epochDay: Int
    var week: Int
    var day: Int
    var squat: Double?
    var bench: Double?
    var deadlift: Double?
}

/// Корень файла резервной копии.
struct BackupArchive: Codable, Equatable, Sendable {
    /// Версия формата: если поля поменяются, старые файлы можно будет опознать.
    var version: Int = 1
    var exportedAt: Date = .now
    var profiles: [ProfileSnapshot]
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

    static func export(_ profiles: [ProgramProfile]) throws -> Data {
        try encoder.encode(BackupArchive(profiles: profiles.map(snapshot)))
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
            programKind: profile.programKind == .texas ? "TEXAS" : "UPPER_LOWER",
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
                    deadlift: $0.deadlift
                )
            },
            planEdits: profile.planEdits
        )
    }

    // MARK: - Загрузка

    static func archive(from data: Data) throws -> BackupArchive {
        try decoder.decode(BackupArchive.self, from: data)
    }

    /// Восстанавливает профили в хранилище, заменяя существующие с тем же идентификатором.
    @discardableResult
    static func restore(_ archive: BackupArchive, into context: ModelContext, existing: [ProgramProfile]) -> Int {
        for snapshot in archive.profiles {
            if let match = existing.first(where: { $0.profileID.uuidString == snapshot.id }) {
                context.delete(match)
            }
            context.insert(profile(from: snapshot))
        }
        try? context.save()
        return archive.profiles.count
    }

    private static func profile(from snapshot: ProfileSnapshot) -> ProgramProfile {
        let result = ProgramProfile(input: .demo, name: snapshot.name)
        result.profileID = UUID(uuidString: snapshot.id) ?? UUID()
        result.createdAt = date(snapshot.createdAtEpochDay)
        result.programKind = snapshot.programKind == "UPPER_LOWER" ? .upperLower : .texas
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
                deadlift: $0.deadlift
            )
        }
        result.planEdits = snapshot.planEdits ?? []
        return result
    }

    // MARK: - Даты

    /// Дни от 1970-01-01 в UTC: то же представление, что в Android-версии.
    private static func epochDay(_ date: Date) -> Int {
        Int(floor(date.timeIntervalSince1970 / 86_400))
    }

    private static func date(_ epochDay: Int) -> Date {
        Date(timeIntervalSince1970: Double(epochDay) * 86_400)
    }
}
