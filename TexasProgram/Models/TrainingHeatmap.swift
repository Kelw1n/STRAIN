import Foundation

// MARK: - Карта тренировок за год

/// Год тренировок клеточками, как на GitHub: строка цифр 0–4 по дням, последняя —
/// день `end`. 0 — тренировки не было, 1–4 — насколько тяжёлым был день.
struct SocialHeatmap: Codable, Hashable, Sendable {
    /// День последней цифры, «2026-10-28» по времени телефона.
    let end: String
    let days: String
}

enum TrainingHeatmap {
    /// 53 недели: последняя неделя может быть неполной, первая — с понедельника.
    static let length = 371

    /// Тяжесть дня — по тоннажу, от своих же обычных тренировок: день на уровне
    /// девяти десятых самых тяжёлых — ярче всего. Без записанных подходов — бледно.
    static func build(log: [CompletionRecord], sets: [String: SetEntry], today: Date = Date(),
                      calendar: Calendar = .current) -> SocialHeatmap {
        var tonnage: [String: Double] = [:]
        for (key, entry) in sets {
            guard let bar = key.firstIndex(of: "|") else { continue }
            tonnage[String(key[..<bar]), default: 0] += entry.tonnage
        }
        let end = calendar.startOfDay(for: today)
        var latest: [String: Date] = [:]
        for record in log { latest[record.key] = max(latest[record.key] ?? record.date, record.date) }
        var perDay = [Double](repeating: -1, count: length)
        for (key, date) in latest {
            guard let back = calendar.dateComponents([.day], from: calendar.startOfDay(for: date), to: end).day,
                  back >= 0, back < length else { continue }
            let index = length - 1 - back
            perDay[index] = max(perDay[index], 0) + (tonnage[key] ?? 0)
        }
        let worked = perDay.filter { $0 > 0 }.sorted()
        let reference = worked.isEmpty ? 0 : worked[Int(Double(worked.count - 1) * 0.9)]
        let digits = perDay.map { value -> Character in
            if value < 0 { return "0" }
            guard value > 0, reference > 0 else { return "1" }
            let level = min(4, max(1, Int((4 * value / reference).rounded(.up))))
            return Character(String(level))
        }
        return SocialHeatmap(end: dayString(end, calendar: calendar), days: String(digits))
    }

    static func build(for profile: ProgramProfile, today: Date = Date()) -> SocialHeatmap {
        build(log: profile.completionLog, sets: profile.setLog, today: today)
    }

    /// Уровни на сегодня: карта друга могла прийти несколько дней назад —
    /// недостающие дни в конце пустые, лишние в начале отрезаем.
    static func levels(_ heatmap: SocialHeatmap, today: Date = Date(), calendar: Calendar = .current) -> [Int] {
        var levels = heatmap.days.map { Int(String($0)) ?? 0 }.map { min(4, max(0, $0)) }
        if let end = date(heatmap.end, calendar: calendar),
           let gap = calendar.dateComponents([.day], from: end, to: calendar.startOfDay(for: today)).day {
            if gap > 0 { levels += [Int](repeating: 0, count: min(gap, length)) }
            if gap < 0 { levels = Array(levels.dropLast(min(-gap, levels.count))) }
        }
        if levels.count < length { levels = [Int](repeating: 0, count: length - levels.count) + levels }
        return Array(levels.suffix(length))
    }

    /// Сколько клеточек рисовать: полные 52 недели и текущая — с понедельника по сегодня.
    static func visibleCount(today: Date = Date(), calendar: Calendar = .current) -> Int {
        let weekday = calendar.component(.weekday, from: today)
        let fromMonday = (weekday + 5) % 7
        return 52 * 7 + fromMonday + 1
    }

    /// Тренировок за последние 365 дней.
    static func workouts(_ levels: [Int]) -> Int {
        levels.suffix(365).filter { $0 > 0 }.count
    }

    private static func formatter(_ calendar: Calendar) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.calendar = calendar
        formatter.timeZone = calendar.timeZone
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }

    static func dayString(_ date: Date, calendar: Calendar = .current) -> String {
        formatter(calendar).string(from: date)
    }

    static func date(_ string: String, calendar: Calendar = .current) -> Date? {
        formatter(calendar).date(from: string).map { calendar.startOfDay(for: $0) }
    }
}
