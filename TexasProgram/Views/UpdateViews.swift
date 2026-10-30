import SwiftUI

/// Плашка сверху экрана: вышла новая версия. «Скачать» отдаёт новый IPA, его
/// ставят через Sideloadly, как и раньше; «Что нового» со стрелкой раскрывает
/// изменения всех версий новее установленной; «×» — напомнить при следующем запуске.
/// Только что обновились — вместо неё один раз «STRAIN обновлён» и что нового.
struct AppUpdateBanner: View {
    var updater = AppUpdater.shared
    var startExpanded = false

    var body: some View {
        if let release = updater.available, !updater.dismissed {
            UpdateCard(
                icon: "arrow.down.circle.fill",
                title: "Вышло обновление \(release.versionName)",
                changes: updater.pending.isEmpty ? release.changes(since: AppUpdater.installedCode) : updater.pending,
                fallback: release.notes,
                startExpanded: startExpanded,
                hint: "Скачается STRAIN.ipa — поставь его через Sideloadly, как обычно. Тренировки останутся на месте.",
                action: ("Скачать", { UIApplication.shared.open(AppUpdater.ipaURL) }),
                onClose: { withAnimation(Motion.sheet) { updater.dismiss() } }
            )
            .transition(.move(edge: .top).combined(with: .opacity))
        } else if let newest = updater.whatsNew.first {
            UpdateCard(
                icon: "sparkles",
                title: "STRAIN обновлён до \(newest.versionName)",
                changes: updater.whatsNew,
                fallback: newest.notes,
                startExpanded: startExpanded,
                hint: nil,
                action: ("Понятно", { withAnimation(Motion.sheet) { updater.dismissWhatsNew() } }),
                onClose: nil
            )
            .transition(.move(edge: .top).combined(with: .opacity))
        }
    }
}

private struct UpdateCard: View {
    let icon: String
    let title: String
    let changes: [AppUpdater.Change]
    let fallback: String
    let startExpanded: Bool
    let hint: String?
    let action: (String, () -> Void)
    let onClose: (() -> Void)?

    @State private var expanded: Bool?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var isExpanded: Bool { expanded ?? startExpanded }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            header
            HStack(spacing: 8) {
                if !changes.isEmpty { toggle } else { Spacer() }
                Button(action: action.1) {
                    Text(action.0)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(.black)
                        .padding(.horizontal, 16)
                        .frame(height: 36)
                        .background(Color.white, in: Capsule())
                }
                .buttonStyle(.pressable)
            }
            if isExpanded {
                VStack(alignment: .leading, spacing: 8) {
                    ChangesList(changes: changes)
                    if let hint {
                        Text(hint)
                            .font(.system(size: 12))
                            .foregroundStyle(Color.white.opacity(0.5))
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .padding(.leading, 12)
        .padding(.trailing, 10)
        .padding(.vertical, 10)
        .bannerSurface(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .padding(.horizontal, 12)
    }

    private var header: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(.black)
                .frame(width: 40, height: 40)
                .background(Theme.accent, in: Circle())
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                Text(AppUpdater.summary(changes, fallback: fallback))
                    .font(.system(size: 13))
                    .foregroundStyle(Color.white.opacity(0.65))
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 4)
            if let onClose {
                Button(action: onClose) {
                    Image(systemName: "xmark")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: 32, height: 32)
                        .background(Color.white.opacity(0.12), in: Circle())
                }
                .accessibilityLabel("Позже")
            }
        }
    }

    /// «Что нового» со стрелкой, которая поворачивается вниз-вверх.
    private var toggle: some View {
        Button {
            withAnimation(Motion.maybe(Motion.sheet, reduce: reduceMotion)) { expanded = !isExpanded }
        } label: {
            HStack(spacing: 4) {
                Text("Что нового")
                    .font(.system(size: 14, weight: .semibold))
                Image(systemName: "chevron.down")
                    .font(.system(size: 12, weight: .bold))
                    .rotationEffect(.degrees(isExpanded ? 180 : 0))
            }
            .foregroundStyle(Color.white.opacity(0.85))
            .frame(maxWidth: .infinity, minHeight: 36, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(isExpanded ? "Скрыть, что нового" : "Показать, что нового")
    }
}

/// Изменения версий: номер и дата, под ними пункты.
struct ChangesList: View {
    let changes: [AppUpdater.Change]

    /// Короткий список — целиком, длинный — прокручивается в рамке 280 точек.
    var body: some View {
        Group {
            if changes.count + changes.reduce(0, { $0 + max($1.points.count, 1) }) <= 8 {
                list
            } else {
                ScrollView { list }
                    .frame(height: 280)
            }
        }
        .background(Color.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private var list: some View {
            VStack(alignment: .leading, spacing: 12) {
                ForEach(changes) { change in
                    VStack(alignment: .leading, spacing: 4) {
                        Text([change.versionName.isEmpty ? nil : change.versionName, AppUpdater.dayText(change.date)]
                            .compactMap { $0 }.joined(separator: " · "))
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(Color.white.opacity(0.5))
                        let points = change.points
                        if points.isEmpty {
                            Text("Исправления и улучшения").font(.system(size: 14)).foregroundStyle(.white)
                        }
                        ForEach(points.indices, id: \.self) { index in
                            HStack(alignment: .firstTextBaseline, spacing: 8) {
                                Text("•").font(.system(size: 14, weight: .bold)).foregroundStyle(Theme.accent)
                                Text(points[index])
                                    .font(.system(size: 14))
                                    .foregroundStyle(.white)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .padding(12)
    }
}

/// Строка в настройках: какая версия стоит и есть ли новее.
struct AppVersionRow: View {
    var updater = AppUpdater.shared
    @State private var checking = false

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("Версия \(AppUpdater.installedName)")
                Text(status)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            if checking {
                ProgressView()
            } else if updater.available != nil {
                Button("Скачать") { UIApplication.shared.open(AppUpdater.ipaURL) }
                    .buttonStyle(.borderedProminent)
            } else {
                Button("Проверить") {
                    checking = true
                    Task {
                        await updater.check(force: true)
                        checking = false
                    }
                }
            }
        }
    }

    private var status: String {
        if let release = updater.available { return "Вышла \(release.versionName)" }
        return updater.hasChecked ? "Это последняя версия" : "Обновления проверяются сами"
    }
}
