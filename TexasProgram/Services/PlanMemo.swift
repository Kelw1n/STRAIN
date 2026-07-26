import Foundation

/// Кэш последнего результата расчёта.
///
/// Планы тренировок раньше пересобирались при каждом обращении к `workoutPlan`,
/// то есть по несколько раз за один проход `body`. Здесь результат переиспользуется,
/// пока не изменились входные максимумы.
final class PlanMemo<Key: Equatable, Value> {
    private var key: Key?
    private var value: Value?

    func resolve(_ newKey: Key, build: (Key) -> Value) -> Value {
        if let key, let value, key == newKey { return value }
        let built = build(newKey)
        key = newKey
        value = built
        return built
    }
}
