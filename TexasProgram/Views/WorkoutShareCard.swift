import SwiftUI
import UIKit

/// Что попадает на карточку тренировки. Собирается из активной тренировки
/// до «Дальше»: после завершения сессия очищается.
struct WorkoutShareSummary {
    struct Line: Identifiable {
        let id: String
        let name: String
        let doneSets: Int
        /// Лучший подход: «100 кг × 5».
        let best: String
        let isRecord: Bool
    }

    let title: String
    let date: Date
    let durationSeconds: Int
    let tonnage: Double
    let sets: Int
    let records: Int
    let lines: [Line]

    @MainActor
    static func current(title: String, durationSeconds: Int, manager: WorkoutSessionManager = .shared) -> WorkoutShareSummary {
        let lines: [Line] = manager.exercises.compactMap { exercise in
            let done = exercise.sets.filter(\.isDone)
            guard let top = done.max(by: { ($0.weight, $0.reps) < ($1.weight, $1.reps) }) else { return nil }
            return Line(
                id: exercise.id,
                name: exercise.prescription.name,
                doneSets: done.count,
                best: "\(WeightFormat.kilogramsPrecise(top.weight)) × \(top.reps)",
                isRecord: done.contains(where: \.isRecord)
            )
        }
        return WorkoutShareSummary(
            title: title,
            date: Date(),
            durationSeconds: durationSeconds,
            tonnage: manager.totalTonnage,
            sets: manager.totalCompletedSets,
            records: manager.exercises.reduce(0) { $0 + $1.sets.filter(\.isRecord).count },
            lines: lines
        )
    }
}

/// Карточка тренировки 9:16 для сторис. Рисуется в 360×640 pt и выгружается
/// в 1080×1920 — ровно размер сторис, ничего не обрежется.
struct WorkoutShareCard: View {
    static let size = CGSize(width: 360, height: 640)
    private static let maxLines = 5

    let summary: WorkoutShareSummary
    var photo: UIImage?

    var body: some View {
        ZStack {
            background

            VStack(alignment: .leading, spacing: 16) {
                header
                Spacer(minLength: 0)

                Text(summary.title.uppercased())
                    .font(.system(size: 26, weight: .black, design: Theme.fontDesign))
                    .foregroundStyle(.white)
                    .lineLimit(2)
                    .minimumScaleFactor(0.7)

                if summary.records > 0 {
                    HStack(spacing: 6) {
                        Image(systemName: "trophy.fill")
                            .font(.system(size: 12, weight: .bold))
                        Text("\(summary.records) \(RuPlural.word(summary.records, "рекорд", "рекорда", "рекордов"))")
                            .font(.system(size: 13, weight: .heavy))
                    }
                    .foregroundStyle(.black)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(Theme.accent, in: Capsule())
                }

                stats
                exerciseList
            }
            .padding(26)
        }
        .frame(width: Self.size.width, height: Self.size.height)
        .clipped()
        .environment(\.colorScheme, .dark)
    }

    // MARK: Фон

    @ViewBuilder
    private var background: some View {
        if let photo {
            // Своё фото из итогов — фоном, снизу затемнено под текст.
            Image(uiImage: photo)
                .resizable()
                .scaledToFill()
                .frame(width: Self.size.width, height: Self.size.height)
                .clipped()
                .overlay {
                    LinearGradient(
                        stops: [
                            .init(color: .black.opacity(0.35), location: 0),
                            .init(color: .black.opacity(0.15), location: 0.3),
                            .init(color: .black.opacity(0.85), location: 0.62),
                            .init(color: .black.opacity(0.95), location: 1)
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                }
        } else {
            Color.black
                .overlay(alignment: .topLeading) {
                    RadialGradient(
                        colors: [Theme.accent.opacity(0.45), .clear],
                        center: .topLeading,
                        startRadius: 0,
                        endRadius: 420
                    )
                }
        }
    }

    // MARK: Части

    private var header: some View {
        HStack(alignment: .center) {
            if let mark = UIImage(named: "splash_mark") {
                Image(uiImage: mark)
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .frame(height: 20)
                    .foregroundStyle(Theme.accent)
            }
            Spacer()
            Text(RuDate.dayMonth(summary.date))
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(.white.opacity(0.75))
        }
    }

    private var stats: some View {
        HStack(spacing: 0) {
            stat(SessionClock.format(summary.durationSeconds), "время")
            stat(WeightFormat.tonnage(summary.tonnage), "тоннаж")
            stat("\(summary.sets)", RuPlural.word(summary.sets, "подход", "подхода", "подходов"))
        }
        .padding(.vertical, 14)
        .background(Color.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(Color.white.opacity(0.1), lineWidth: 1))
    }

    private func stat(_ value: String, _ title: String) -> some View {
        VStack(spacing: 3) {
            Text(value)
                .font(.system(size: 20, weight: .heavy, design: Theme.fontDesign))
                .monospacedDigit()
                .foregroundStyle(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            Text(title)
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(.white.opacity(0.55))
        }
        .frame(maxWidth: .infinity)
    }

    private var exerciseList: some View {
        VStack(alignment: .leading, spacing: 10) {
            ForEach(summary.lines.prefix(Self.maxLines)) { line in
                HStack(spacing: 10) {
                    ExerciseThumb(name: line.name, size: 34)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(line.name)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(.white)
                            .lineLimit(1)
                        Text(RuPlural.sets(line.doneSets))
                            .font(.system(size: 11, weight: .medium))
                            .foregroundStyle(.white.opacity(0.5))
                    }
                    Spacer(minLength: 6)
                    if line.isRecord {
                        Image(systemName: "trophy.fill")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(Theme.accent)
                    }
                    Text(line.best)
                        .font(.system(size: 13, weight: .bold, design: Theme.fontDesign))
                        .monospacedDigit()
                        .foregroundStyle(.white.opacity(0.9))
                }
            }
            if summary.lines.count > Self.maxLines {
                let rest = summary.lines.count - Self.maxLines
                Text("и ещё \(RuPlural.exercises(rest))")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(.white.opacity(0.5))
            }
        }
    }

    // MARK: Картинка

    /// 1080×1920 PNG во временной папке: так лист «Поделиться» предлагает
    /// и сторис, и «Сохранить изображение».
    @MainActor
    static func render(_ summary: WorkoutShareSummary, photo: UIImage?) -> UIImage? {
        let renderer = ImageRenderer(content: WorkoutShareCard(summary: summary, photo: photo))
        renderer.scale = 3
        renderer.isOpaque = true
        return renderer.uiImage
    }
}
