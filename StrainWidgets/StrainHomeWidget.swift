import SwiftUI
import WidgetKit

private enum HomePalette {
    static let accent = Color(red: 0.16, green: 0.84, blue: 0.76)
    static let accentDeep = Color(red: 0.35, green: 0.47, blue: 0.98)
    static let record = Color(red: 1.00, green: 0.42, blue: 0.52)

    static var gradient: LinearGradient {
        LinearGradient(colors: [accent, accentDeep], startPoint: .topLeading, endPoint: .bottomTrailing)
    }
}

/// Виджет со следующей тренировкой: домашний экран и экран блокировки.
struct StrainHomeWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "StrainHomeWidget", provider: StrainProvider()) { entry in
            StrainWidgetView(snapshot: entry.snapshot)
        }
        .configurationDisplayName("STRAIN")
        .description("Следующая тренировка и номер жима.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryCircular, .accessoryRectangular])
    }
}

private struct StrainEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot?
}

private struct StrainProvider: TimelineProvider {
    func placeholder(in context: Context) -> StrainEntry {
        StrainEntry(date: .now, snapshot: nil)
    }

    func getSnapshot(in context: Context, completion: @escaping (StrainEntry) -> Void) {
        completion(StrainEntry(date: .now, snapshot: WidgetStore.load()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<StrainEntry>) -> Void) {
        // Данные меняются только когда приложение их перезаписывает и само просит
        // обновиться, поэтому расписание редкое — раз в час, чтобы освежать «Завтра».
        let entry = StrainEntry(date: .now, snapshot: WidgetStore.load())
        completion(Timeline(entries: [entry], policy: .after(.now.addingTimeInterval(3600))))
    }
}

private struct StrainWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let snapshot: WidgetSnapshot?

    var body: some View {
        switch family {
        case .accessoryCircular: circular
        case .accessoryRectangular: rectangular
        case .systemMedium: medium
        default: small
        }
    }

    // MARK: - Домашний экран

    private var small: some View {
        VStack(alignment: .leading, spacing: 6) {
            header
            Spacer(minLength: 0)
            if let snapshot {
                Text(snapshot.weekText)
                    .font(.system(size: 15, weight: .bold, design: .rounded))
                Text(snapshot.title)
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                if let session = snapshot.benchSession {
                    Text("Жим №\(session)")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(HomePalette.record)
                }
            } else {
                placeholderText
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .containerBackground(for: .widget) { Color.black }
    }

    private var medium: some View {
        HStack(spacing: 14) {
            VStack(alignment: .leading, spacing: 6) {
                header
                Spacer(minLength: 0)
                if let snapshot {
                    Text(snapshot.weekText)
                        .font(.system(size: 20, weight: .bold, design: .rounded))
                    Text(snapshot.title)
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                } else {
                    placeholderText
                }
            }
            if let snapshot {
                VStack(alignment: .trailing, spacing: 6) {
                    if let session = snapshot.benchSession {
                        Text("Жим №\(session)")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(HomePalette.record)
                        if let top = snapshot.benchTopWeight {
                            Text("до \(top) кг")
                                .font(.system(size: 11))
                                .foregroundStyle(.secondary)
                        }
                    }
                    Spacer(minLength: 0)
                    Text("\(snapshot.doneDays) / \(snapshot.totalDays)")
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .foregroundStyle(HomePalette.accent)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .containerBackground(for: .widget) { Color.black }
    }

    private var header: some View {
        HStack(spacing: 6) {
            Image(systemName: snapshot?.isRestToday == true ? "moon.zzz.fill" : "figure.strengthtraining.traditional")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(HomePalette.accent)
            Text(snapshot.map { "\($0.relative) · \($0.dateText)" } ?? "STRAIN")
                .font(.system(size: 10, weight: .bold))
                .foregroundStyle(HomePalette.accent)
                .lineLimit(1)
        }
    }

    private var placeholderText: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text("STRAIN")
                .font(.system(size: 14, weight: .black))
                .tracking(1.2)
            Text("Открой приложение, чтобы виджет наполнился")
                .font(.system(size: 9))
                .foregroundStyle(.secondary)
        }
    }

    // MARK: - Экран блокировки

    private var circular: some View {
        ZStack {
            AccessoryWidgetBackground()
            if let snapshot {
                VStack(spacing: 0) {
                    Image(systemName: "figure.strengthtraining.traditional")
                        .font(.system(size: 11, weight: .bold))
                    Text(snapshot.compactLine)
                        .font(.system(size: 11, weight: .bold, design: .rounded))
                        .minimumScaleFactor(0.7)
                }
            } else {
                Image(systemName: "figure.strengthtraining.traditional")
            }
        }
        .containerBackground(for: .widget) { Color.clear }
    }

    private var rectangular: some View {
        VStack(alignment: .leading, spacing: 1) {
            if let snapshot {
                Text("\(snapshot.relative) · \(snapshot.dateText)")
                    .font(.system(size: 11, weight: .bold))
                Text(snapshot.weekText)
                    .font(.system(size: 13, weight: .semibold))
                Text(snapshot.title)
                    .font(.system(size: 11))
                    .lineLimit(1)
            } else {
                Text("STRAIN").font(.system(size: 13, weight: .bold))
                Text("Открой приложение").font(.system(size: 11))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .containerBackground(for: .widget) { Color.clear }
    }
}
