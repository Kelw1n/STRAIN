import SwiftUI
import WidgetKit

/// Небольшой виджет на домашний экран.
///
/// Помимо пользы он служит индикатором: виджет видно в галерее только если
/// расширение реально установлено и подписано. Пустая Live Activity и
/// вырезанное при подписи расширение снаружи неотличимы — этот виджет их разводит.
struct StrainHomeWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "StrainHomeWidget", provider: StrainProvider()) { _ in
            StrainWidgetView()
        }
        .configurationDisplayName("STRAIN")
        .description("Быстрый переход к тренировке дня.")
        .supportedFamilies([.systemSmall])
    }
}

private struct StrainEntry: TimelineEntry {
    let date: Date
}

private struct StrainProvider: TimelineProvider {
    func placeholder(in context: Context) -> StrainEntry { StrainEntry(date: .now) }

    func getSnapshot(in context: Context, completion: @escaping (StrainEntry) -> Void) {
        completion(StrainEntry(date: .now))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<StrainEntry>) -> Void) {
        // Данные приложения виджету пока недоступны — для этого нужна App Group.
        // Обновляемся раз в час, чтобы не жечь бюджет обновлений.
        let entry = StrainEntry(date: .now)
        completion(Timeline(entries: [entry], policy: .after(.now.addingTimeInterval(3600))))
    }
}

private struct StrainWidgetView: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: "figure.strengthtraining.traditional")
                .font(.system(size: 22, weight: .bold))
                .foregroundStyle(Color(red: 0.16, green: 0.84, blue: 0.76))
            Spacer(minLength: 0)
            Text("STRAIN")
                .font(.system(size: 13, weight: .black))
                .tracking(1.4)
            Text("Открыть тренировку")
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .containerBackground(for: .widget) {
            Color.black
        }
    }
}
