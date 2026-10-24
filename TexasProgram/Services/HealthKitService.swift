import Foundation
import HealthKit

/// Сервис взаимодействия с Apple Health (HealthKit).
///
/// Безопасен для бесплатных аккаунтов разработчика: если HealthKit недоступен
/// или выключен в настройках, сервис не выбрасывает исключений и тихо завершает операции.
@Observable
public final class HealthKitService {
    public static let shared = HealthKitService()

    private let healthStore = HKHealthStore()

    /// Включена ли интеграция пользователем в настройках приложения.
    public var isEnabled: Bool {
        get { UserDefaults.standard.bool(forKey: "healthkit_sync_enabled") }
        set { UserDefaults.standard.set(newValue, forKey: "healthkit_sync_enabled") }
    }

    /// Есть ли «Здоровье» на устройстве (на iPhone есть, на iPad до iPadOS 17 — нет).
    public var deviceSupportsHealth: Bool {
        HKHealthStore.isHealthDataAvailable()
    }

    /// Можно ли пользоваться «Здоровьем» в этой сборке. Бесплатный Apple ID
    /// при установке через сайдлоад не даёт права на HealthKit: запрос прав
    /// молча падает, и переключатель с кнопкой веса ничего не делали.
    public var isAvailable: Bool {
        deviceSupportsHealth && Self.buildHasHealthEntitlement
    }

    /// Право берём из профиля подписи в пакете приложения. Его кладёт любая
    /// установка не из App Store; в App Store профиля нет, и право выдаёт Apple.
    static let buildHasHealthEntitlement: Bool = {
        guard let url = Bundle.main.url(forResource: "embedded", withExtension: "mobileprovision"),
              let data = try? Data(contentsOf: url) else { return true }
        return provisionGrantsHealth(data)
    }()

    /// Профиль — подписанный контейнер, внутри которого лежит обычный plist.
    static func provisionGrantsHealth(_ provision: Data) -> Bool {
        guard let start = provision.range(of: Data("<?xml".utf8)),
              let end = provision.range(of: Data("</plist>".utf8), in: start.lowerBound..<provision.endIndex),
              let plist = try? PropertyListSerialization.propertyList(
                  from: provision.subdata(in: start.lowerBound..<end.upperBound), format: nil
              ) as? [String: Any],
              let entitlements = plist["Entitlements"] as? [String: Any]
        else { return false }
        return entitlements["com.apple.developer.healthkit"] as? Bool == true
    }

    private init() {}

    /// Запрос прав на чтение веса тела и сохранение тренировок.
    public func requestAuthorization() async -> Bool {
        guard isAvailable else { return false }

        guard let bodyMassType = HKQuantityType.quantityType(forIdentifier: .bodyMass) else { return false }
        let workoutType = HKObjectType.workoutType()

        let typesToShare: Set<HKSampleType> = [workoutType]
        let typesToRead: Set<HKObjectType> = [bodyMassType, workoutType]

        do {
            try await healthStore.requestAuthorization(toShare: typesToShare, read: typesToRead)
            return true
        } catch {
            return false
        }
    }

    /// Сохранение силовой тренировки в Apple Здоровье.
    public func saveWorkout(
        startDate: Date,
        endDate: Date = Date(),
        title: String,
        totalTonnageKg: Double = 0
    ) async -> Bool {
        guard isEnabled, isAvailable else { return false }

        let duration = max(60, endDate.timeIntervalSince(startDate))
        // Примерный ориентир для силовой тренировки: ~5-6 ккал/мин в зависимости от интенсивности
        let estimatedCalories = (duration / 60.0) * 5.5
        let energyBurned = HKQuantity(unit: .kilocalorie(), doubleValue: estimatedCalories)

        var metadata: [String: Any] = [
            HKMetadataKeyWorkoutBrandName: "STRAIN",
            HKMetadataKeyIndoorWorkout: true
        ]
        if totalTonnageKg > 0 {
            metadata["TotalTonnageKg"] = totalTonnageKg
        }

        let workout = HKWorkout(
            activityType: .traditionalStrengthTraining,
            start: startDate,
            end: endDate,
            duration: duration,
            totalEnergyBurned: energyBurned,
            totalDistance: nil,
            metadata: metadata
        )

        do {
            try await healthStore.save(workout)
            return true
        } catch {
            return false
        }
    }

    /// Получение последнего зафиксированного веса тела (в кг) из Apple Здоровья.
    public func fetchLatestBodyWeight() async -> Double? {
        guard isAvailable else { return nil }

        let weightType = HKQuantityType(.bodyMass)
        let sortDescriptor = NSSortDescriptor(key: HKSampleSortIdentifierEndDate, ascending: false)

        return await withCheckedContinuation { continuation in
            let query = HKSampleQuery(
                sampleType: weightType,
                predicate: nil,
                limit: 1,
                sortDescriptors: [sortDescriptor]
            ) { _, samples, _ in
                guard let sample = samples?.first as? HKQuantitySample else {
                    continuation.resume(returning: nil)
                    return
                }
                let weightKg = sample.quantity.doubleValue(for: .gramUnit(with: .kilo))
                continuation.resume(returning: (weightKg * 10).rounded() / 10)
            }
            healthStore.execute(query)
        }
    }
}
