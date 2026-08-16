import Foundation

/// Выгрузка записанных подходов таблицей.
///
/// Резервная копия в JSON нужна, чтобы перенести всё на другой телефон, — глазами
/// её не почитаешь. Здесь наоборот: файл, который открывается в Excel и годится,
/// чтобы посчитать своё.
enum CSVExport {

    static let fileName = "strain-log.csv"

    /// Разделитель — точка с запятой, а не запятая.
    ///
    /// В русской локали Excel запятая занята под десятичный знак, и файл с ней
    /// открывается одной колонкой. Вес пишем тоже с запятой — так его сразу
    /// видно числом, а не текстом.
    private static let separator = ";"

    static func text(for profiles: [ProgramProfile]) -> String {
        var rows = ["Профиль;Программа;Цикл;Неделя;День;Упражнение;Подход;Повторы;Вес;Тоннаж"]

        for profile in profiles {
            for week in profile.workoutPlan.weeks {
                for day in week.days {
                    for exercise in day.exercises {
                        for item in profile.entries(week: week.number, day: day.number, exercise: exercise) {
                            rows.append([
                                escape(profile.name),
                                escape(profile.programKind.rawValue),
                                "\(profile.cycleNumber)",
                                "\(week.number)",
                                "\(day.number)",
                                escape(exercise.name),
                                "\(item.index + 1)",
                                "\(item.entry.reps)",
                                number(item.entry.weight),
                                number(item.entry.tonnage)
                            ].joined(separator: separator))
                        }
                    }
                }
            }
        }

        return rows.joined(separator: "\r\n") + "\r\n"
    }

    static func writeTemporaryFile(_ profiles: [ProgramProfile]) throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
        // Без метки кодировки Excel на Windows читает кириллицу как мусор.
        var data = Data([0xEF, 0xBB, 0xBF])
        data.append(Data(text(for: profiles).utf8))
        try data.write(to: url, options: .atomic)
        return url
    }

    private static func number(_ value: Double) -> String {
        let text = value == value.rounded() ? String(Int(value)) : String(format: "%.2f", value)
        return text.replacingOccurrences(of: ".", with: ",")
    }

    /// Точка с запятой и кавычки внутри названия сломали бы разбор.
    private static func escape(_ text: String) -> String {
        guard text.contains(separator) || text.contains("\"") || text.contains("\n") else { return text }
        return "\"" + text.replacingOccurrences(of: "\"", with: "\"\"") + "\""
    }
}
