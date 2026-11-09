import SwiftUI
import UIKit

// MARK: - Мои тренировки

/// «Мои тренировки» как «Выбрать программу» в REBRN: вкладки «Мои»,
/// «Каталог» и «Сообщество». Любую можно начать в любой день — программа
/// при этом не двигается, а подходы идут в историю, рекорды и серию.
struct TemplatesView: View {
    @Bindable var profile: ProgramProfile
    /// Начать тренировку: лист закрывается, тренировка открывается на весь экран.
    let onStart: (WorkoutTemplate) -> Void

    enum Tab: String, CaseIterable, Identifiable {
        case mine = "Мои", catalog = "Каталог", community = "Сообщество"
        var id: String { rawValue }
    }

    @Environment(\.dismiss) private var dismiss
    @State private var tab: Tab = .mine
    @State private var query = ""
    @State private var editing: EditorTarget?
    @State private var preview: PreviewTarget?
    @State private var community: [CommunityProgram] = []
    @State private var loading = false
    @State private var communityError: String?
    @State private var deleting: WorkoutTemplate?

    struct EditorTarget: Identifiable {
        let id = UUID()
        let template: WorkoutTemplate
        let isNew: Bool
    }

    struct PreviewTarget: Identifiable {
        let id = UUID()
        let template: WorkoutTemplate
        let author: String?
        let isOwn: Bool
    }

    private var mine: [WorkoutTemplate] {
        filtered(profile.workoutTemplates.sorted { $0.createdAt > $1.createdAt })
    }

    private func filtered(_ list: [WorkoutTemplate]) -> [WorkoutTemplate] {
        let key = ExerciseLibrary.normalized(query)
        guard !key.isEmpty else { return list }
        return list.filter { ExerciseLibrary.normalized($0.name).contains(key) }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 12) {
                    tabs
                    searchField
                    switch tab {
                    case .mine: mineList
                    case .catalog: catalogList
                    case .community: communityList
                    }
                    Spacer(minLength: 40)
                }
                .padding(.horizontal, 16)
                .padding(.top, 4)
            }
            .scrollIndicators(.hidden)
            .scrollDismissesKeyboard(.interactively)
            .screenBackground()
            .navigationTitle(L("Мои тренировки"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(L("Закрыть")) { dismiss() }.foregroundStyle(Color.ink.opacity(0.7))
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        editing = EditorTarget(template: WorkoutTemplate(name: ""), isNew: true)
                    } label: {
                        Image(systemName: "plus").font(.system(size: 15, weight: .bold)).foregroundStyle(.ink)
                    }
                    .accessibilityLabel(L("Создать тренировку"))
                }
            }
            .task(id: tab) { if tab == .community { await loadCommunity() } }
            .onChange(of: query) { _, _ in
                guard tab == .community else { return }
                Task { await loadCommunity() }
            }
        }
        .sheet(item: $editing) { target in
            TemplateEditorView(profile: profile, original: target.template, isNew: target.isNew) { saved in
                profile.saveTemplate(saved)
                tab = .mine
            }
        }
        .sheet(item: $preview) { target in
            TemplatePreviewView(
                profile: profile,
                template: target.template,
                author: target.author,
                isOwn: target.isOwn,
                onStart: { start(target.template) },
                onCopy: {
                    profile.saveTemplate(target.template.copy())
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                    tab = .mine
                },
                onEdit: { editing = EditorTarget(template: target.template, isNew: false) }
            )
        }
        .confirmationDialog(L("Удалить «\(deleting?.name ?? "")»?"), isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }), titleVisibility: .visible) {
            Button(L("Удалить"), role: .destructive) {
                if let deleting { withAnimation(Motion.snappy) { profile.deleteTemplate(deleting.id) } }
                deleting = nil
            }
        } message: {
            Text(L("Прошлые тренировки по ней останутся в истории."))
        }
    }

    private func start(_ template: WorkoutTemplate) {
        UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
        preview = nil
        dismiss()
        onStart(template)
    }

    // MARK: Вкладки и поиск

    private var tabs: some View {
        HStack(spacing: 4) {
            ForEach(Tab.allCases) { item in
                Button {
                    UISelectionFeedbackGenerator().selectionChanged()
                    withAnimation(Motion.snappy) { tab = item }
                } label: {
                    Text(item.rawValue)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(tab == item ? Color.ink : Color.ink.opacity(0.5))
                        .frame(maxWidth: .infinity)
                        .frame(height: 36)
                        .background(tab == item ? Color.tone(0.2) : Color.clear, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(4)
        .background(Color.tone(0.1), in: RoundedRectangle(cornerRadius: 13, style: .continuous))
    }

    private var searchField: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Color.ink.opacity(0.45))
            TextField(tab == .community ? L("Поиск программы") : L("Поиск тренировки"), text: $query)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(.ink)
        }
        .padding(.horizontal, 14)
        .frame(height: 44)
        .background(Color.tone(0.11), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    // MARK: Мои

    @ViewBuilder
    private var mineList: some View {
        if profile.workoutTemplates.isEmpty {
            VStack(spacing: 12) {
                Image(systemName: "square.stack.3d.up")
                    .font(.system(size: 30, weight: .semibold))
                    .foregroundStyle(Theme.accent)
                Text(L("Своих тренировок пока нет"))
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(.ink)
                Text(L("Собери тренировку из упражнений библиотеки или возьми готовую в «Каталоге». Программа при этом не меняется."))
                    .font(.system(size: 14))
                    .foregroundStyle(Color.ink.opacity(0.55))
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                NutritionButton(title: "Создать тренировку", systemImage: "plus") {
                    editing = EditorTarget(template: WorkoutTemplate(name: ""), isNew: true)
                }
                NutritionButton(title: "Начать пустую", systemImage: "play.fill", primary: false) {
                    start(WorkoutTemplate(name: "Тренировка"))
                }
            }
            .padding(20)
            .frame(maxWidth: .infinity)
            .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        } else {
            ForEach(mine) { template in
                row(template.name, subtitle: template.summary, badge: template.isShared ? "в профиле" : nil) {
                    preview = PreviewTarget(template: template, author: nil, isOwn: true)
                } menu: {
                    Button { start(template) } label: { Label(L("Начать"), systemImage: "play.fill") }
                    Button { editing = EditorTarget(template: template, isNew: false) } label: { Label(L("Изменить"), systemImage: "pencil") }
                    Button {
                        profile.saveTemplate(template.copy(named: template.name + " (копия)"))
                    } label: { Label(L("Дублировать"), systemImage: "plus.square.on.square") }
                    Button {
                        var shared = template
                        shared.isShared.toggle()
                        profile.saveTemplate(shared)
                    } label: {
                        Label(template.isShared ? L("Убрать из профиля") : L("Показывать в профиле"),
                              systemImage: template.isShared ? "eye.slash" : "person.crop.circle.badge.checkmark")
                    }
                    Button(role: .destructive) { deleting = template } label: { Label(L("Удалить"), systemImage: "trash") }
                }
            }
            Button { start(WorkoutTemplate(name: "Тренировка")) } label: {
                HStack(spacing: 8) {
                    Image(systemName: "play.fill").font(.system(size: 13, weight: .bold))
                    Text(L("Пустая тренировка")).font(.system(size: 15, weight: .bold))
                }
                .foregroundStyle(.ink)
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .background(Color.tone(0.14), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
            .buttonStyle(.pressable)
            .padding(.top, 4)
        }
    }

    // MARK: Каталог

    private var catalogList: some View {
        ForEach(filtered(TemplateCatalog.all)) { template in
            row(template.name, subtitle: template.summary + " · " + template.groups().prefix(3).joined(separator: ", ")) {
                preview = PreviewTarget(template: template, author: nil, isOwn: false)
            } menu: {
                Button { start(template) } label: { Label(L("Начать"), systemImage: "play.fill") }
                Button {
                    profile.saveTemplate(template.copy())
                    tab = .mine
                } label: { Label(L("Сохранить себе"), systemImage: "square.and.arrow.down") }
            }
        }
    }

    // MARK: Сообщество

    @ViewBuilder
    private var communityList: some View {
        if SocialService.shared.token == nil {
            message("Создай профиль STRAIN во вкладке «Профиль», чтобы видеть программы других людей.")
        } else if loading && community.isEmpty {
            ProgressView().padding(.top, 30)
        } else if let communityError, community.isEmpty {
            message(communityError)
        } else if community.isEmpty {
            message("Пока никто не показал свои тренировки. Покажи свою: «…» → «Показывать в профиле».")
        } else {
            ForEach(community) { item in
                let template = item.program.template
                row(item.program.title, subtitle: "@\(item.author.username) · \(template.summary)",
                    badge: item.program.isTemplate ? nil : "программа") {
                    preview = PreviewTarget(template: template, author: item.author.username, isOwn: false)
                } menu: {
                    Button { start(template) } label: { Label(L("Начать"), systemImage: "play.fill") }
                    Button {
                        profile.saveTemplate(template.copy())
                        tab = .mine
                    } label: { Label(L("Сохранить себе"), systemImage: "square.and.arrow.down") }
                }
            }
        }
    }

    private func loadCommunity() async {
        guard SocialService.shared.token != nil else { return }
        loading = true
        defer { loading = false }
        do {
            community = try await SocialService.shared.communityPrograms(query: query)
            communityError = nil
        } catch is CancellationError {
        } catch {
            communityError = (error as? SocialError)?.message ?? "Не удалось загрузить"
        }
    }

    private func message(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 14))
            .foregroundStyle(Color.ink.opacity(0.55))
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 30)
    }

    // MARK: Строка

    private func row<Menu: View>(_ title: String, subtitle: String, badge: String? = nil,
                                 open: @escaping () -> Void, @ViewBuilder menu: () -> Menu) -> some View {
        HStack(spacing: 8) {
            Button(action: open) {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(title.isEmpty ? L("Без названия") : title)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(.ink)
                            .lineLimit(1)
                        if let badge {
                            Text(badge)
                                .font(.system(size: 10, weight: .bold))
                                .foregroundStyle(Theme.accent)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Theme.accent.opacity(0.14), in: Capsule())
                        }
                    }
                    Text(subtitle)
                        .font(.system(size: 12))
                        .foregroundStyle(Color.ink.opacity(0.45))
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.pressable)
            SwiftUI.Menu {
                menu()
            } label: {
                Image(systemName: "ellipsis")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(Color.ink.opacity(0.6))
                    .frame(width: 36, height: 36)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel(L("Действия: \(title)"))
        }
        .padding(.leading, 16)
        .padding(.trailing, 8)
        .padding(.vertical, 12)
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

// MARK: - Просмотр тренировки

struct TemplatePreviewView: View {
    @Bindable var profile: ProgramProfile
    let template: WorkoutTemplate
    var author: String?
    let isOwn: Bool
    let onStart: () -> Void
    let onCopy: () -> Void
    let onEdit: () -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(template.name.isEmpty ? L("Без названия") : template.name)
                            .font(.system(size: 26, weight: .heavy, design: Theme.fontDesign))
                            .foregroundStyle(.ink)
                        Text([author.map { "@\($0)" }, template.summary].compactMap { $0 }.joined(separator: " · "))
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Color.ink.opacity(0.5))
                    }
                    VStack(spacing: 0) {
                        ForEach(Array(template.exercises.enumerated()), id: \.offset) { index, exercise in
                            HStack(spacing: 12) {
                                ExerciseThumb(name: exercise.name, size: 48)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(exercise.name)
                                        .font(.system(size: 15, weight: .semibold))
                                        .foregroundStyle(.ink)
                                        .lineLimit(2)
                                    Text(detail(exercise))
                                        .font(.system(size: 12))
                                        .foregroundStyle(Color.ink.opacity(0.45))
                                }
                                Spacer()
                            }
                            .padding(.vertical, 8)
                            if index < template.exercises.count - 1 {
                                Divider().overlay(Color.ink.opacity(0.06))
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 6)
                    .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 18, style: .continuous))

                    NutritionButton(title: "Начать тренировку", systemImage: "play.fill") {
                        dismiss()
                        onStart()
                    }
                    if isOwn {
                        NutritionButton(title: "Изменить", systemImage: "pencil", primary: false) {
                            dismiss()
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { onEdit() }
                        }
                    } else {
                        NutritionButton(title: "Сохранить себе", systemImage: "square.and.arrow.down", primary: false) {
                            onCopy()
                            dismiss()
                        }
                    }
                }
                .padding(16)
            }
            .scrollIndicators(.hidden)
            .screenBackground()
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) { Button(L("Готово")) { dismiss() }.fontWeight(.bold) }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func detail(_ exercise: TemplateExercise) -> String {
        let reps = exercise.sets.first?.reps ?? 0
        let weight = exercise.sets.first(where: { $0.weight > 0 })?.weight
        var parts = ["\(exercise.sets.count) × \(reps)"]
        if let weight { parts.append(WeightFormat.kilogramsPrecise(weight)) }
        parts.append("отдых " + RestFormat.text(exercise.restSeconds))
        return parts.joined(separator: " · ")
    }
}

/// «1:30», «2 мин», «45 с» — отдых между подходами.
enum RestFormat {
    static let options = [30, 45, 60, 90, 120, 150, 180, 240, 300]

    static func text(_ seconds: Int) -> String {
        if seconds < 60 { return "\(seconds) с" }
        if seconds % 60 == 0 { return "\(seconds / 60) мин" }
        return String(format: "%d:%02d", seconds / 60, seconds % 60)
    }
}
