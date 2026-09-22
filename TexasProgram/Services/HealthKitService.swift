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

    /// Доступен ли HealthKit на текущем устройстве (например, доступен на iPhone, но не на iPad до iPadOS 17).
    public var isAvailable: Bool {
        HKHealthStore.isHealthDataAvailable()
    }

    private init() {}

    /// Запрос прав на чтение веса тела и сохранение тренировок.
    public func requestAuthorization() async -> Bool {
        guard isAvailable else { return false }

        let typesToShare: Set<HKSampleType> = [
            HKObjectType.workoutType()
        ]

        let typesToRead: Set<HKObjectType> = [
            HKQuantityType(.bodyMass),
            HKObjectType.workoutType()
        ]

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
