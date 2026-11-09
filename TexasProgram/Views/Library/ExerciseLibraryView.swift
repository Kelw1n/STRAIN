import SwiftUI
import UIKit

// MARK: - Библиотека упражнений

/// «Упражнения», как в REBRN: поиск, фильтры «Мышцы» и «Инвентарь», разделы
/// по группам мышц. В режиме выбора упражнения отмечаются галочкой, внизу
/// кнопка «Дальше · N»; «i» открывает технику.
struct ExerciseLibraryView: View {
    @Bindable var profile: ProgramProfile
    /// Выбор для шаблона или идущей тренировки. Нет — просто библиотека.
    var onPick: (([LibraryExercise]) -> Void)?
    var pickTitle = "Дальше"

    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var group: String?
    @State private var equipment: String?
    @State private var picked: [LibraryExercise] = []
    @State private var detail: LibraryExercise?
    @State private var creating = false
    @FocusState private var searching: Bool

    private var isPicking: Bool { onPick != nil }

    var body: some View {
        let items = ExerciseLibrary.filter(profile.exerciseLibrary, query: query, group: group, equipment: equipment)
        let sections = ExerciseLibrary.sections(items, group: group)
        NavigationStack {
            ZStack(alignment: .bottom) {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 0, pinnedViews: []) {
                        searchField
                            .padding(.bottom, 10)
                        filters
                            .padding(.bottom, 14)
                        if sections.isEmpty {
                            empty
                        }
                        ForEach(sections, id: \.group.id) { section in
                            Text(section.group.title.uppercased())
                                .font(.system(size: 12, weight: .bold))
                                .tracking(1.2)
                                .foregroundStyle(Color.ink.opacity(0.45))
                                .padding(.top, 12)
                                .padding(.bottom, 4)
                            ForEach(Array(section.items.enumerated()), id: \.element.id) { index, item in
                                row(item)
                                if index < section.items.count - 1 {
                                    Divider().overlay(Color.ink.opacity(0.06)).padding(.leading, 84)
                                }
                            }
                        }
                        Spacer(minLength: isPicking ? 110 : 40)
                    }
                    .padding(.horizontal, 16)
                }
                .scrollIndicators(.hidden)
                .scrollDismissesKeyboard(.interactively)
                .screenBackground()

                if isPicking && !picked.isEmpty {
                    Button {
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                        onPick?(picked)
                        dismiss()
                    } label: {
                        Text("\(pickTitle.uppercased()) · \(picked.count)")
                            .font(.system(size: 16, weight: .black, design: Theme.fontDesign))
                            .tracking(1.2)
                            .foregroundStyle(.canvas)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 18)
                            .background(Color.ink, in: Capsule())
                    }
                    .buttonStyle(.pressable)
                    .padding(.horizontal, 18)
                    .padding(.bottom, 12)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .animation(Motion.snappy, value: picked.isEmpty)
            .navigationTitle(L("Упражнения"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(isPicking ? L("Отмена") : L("Готово")) { dismiss() }
                        .foregroundStyle(Color.ink.opacity(0.7))
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { creating = true } label: {
                        Image(systemName: "plus")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundStyle(.ink)
                    }
                    .accessibilityLabel(L("Создать упражнение"))
                }
            }
        }
        .sheet(item: $detail) { item in
            ExerciseDetailView(profile: profile, exercise: item)
        }
        .sheet(isPresented: $creating) {
            CustomExerciseSheet { exercise in
                profile.saveCustomExercise(exercise)
                if isPicking { picked.append(exercise) }
            }
        }
    }

    private var searchField: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Color.ink.opacity(0.45))
            TextField(L("Поиск упражнения"), text: $query)
                .focused($searching)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(.ink)
                .submitLabel(.search)
            if !query.isEmpty {
                Button { query = "" } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(Color.ink.opacity(0.35))
                }
                .accessibilityLabel(L("Очистить"))
            }
        }
        .padding(.horizontal, 14)
        .frame(height: 46)
        .background(Color.tone(0.11), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private var filters: some View {
        HStack(spacing: 10) {
            filterMenu(title: "Мышцы", value: group, options: ExerciseLibrary.groups) { group = $0 }
            filterMenu(title: "Инвентарь", value: equipment, options: ExerciseLibrary.equipment) { equipment = $0 }
        }
    }

    private func filterMenu(title: String, value: String?, options: [ExerciseLibrary.Option], set: @escaping (String?) -> Void) -> some View {
        Menu {
            Button {
                set(nil)
            } label: {
                if value == nil { Label(L("Все"), systemImage: "checkmark") } else { Text(L("Все")) }
            }
            ForEach(options) { option in
                Button {
                    UISelectionFeedbackGenerator().selectionChanged()
                    set(option.id)
                } label: {
                    if value == option.id { Label(option.title, systemImage: "checkmark") } else { Text(option.title) }
                }
            }
        } label: {
            HStack {
                Text(options.first { $0.id == value }?.title ?? title)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(value == nil ? Color.ink.opacity(0.85) : Theme.accent)
                    .lineLimit(1)
                Spacer(minLength: 4)
                Image(systemName: "chevron.down")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Color.ink.opacity(0.5))
            }
            .padding(.horizontal, 14)
            .frame(height: 42)
            .background(Color.tone(0.11), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(value == nil ? Color.clear : Theme.accent.opacity(0.6), lineWidth: 1))
        }
    }

    private var empty: some View {
        VStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 28, weight: .semibold))
                .foregroundStyle(Color.ink.opacity(0.3))
            Text(L("Ничего не нашлось"))
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(.ink)
            Button(L("Создать «\(query.isEmpty ? "своё" : query)»")) { creating = true }
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Theme.accent)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 40)
    }

    private func row(_ item: LibraryExercise) -> some View {
        let selected = picked.contains { $0.id == item.id }
        return HStack(spacing: 12) {
            Button {
                if isPicking {
                    UISelectionFeedbackGenerator().selectionChanged()
                    withAnimation(Motion.snappy) {
                        if selected { picked.removeAll { $0.id == item.id } } else { picked.append(item) }
                    }
                } else {
                    detail = item
                }
            } label: {
                HStack(spacing: 12) {
                    ExerciseThumb(name: item.name, size: 60)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(item.name)
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(.ink)
                            .multilineTextAlignment(.leading)
                            .lineLimit(2)
                        Text(ExerciseLibrary.groupTitle(item.group) + (item.isCustom ? L(" · своё") : ""))
                            .font(.system(size: 12))
                            .foregroundStyle(Color.ink.opacity(0.45))
                    }
                    Spacer(minLength: 6)
                    if isPicking {
                        ZStack {
                            Circle().strokeBorder(Color.ink.opacity(selected ? 0 : 0.25), lineWidth: 2)
                                .background(Circle().fill(selected ? Theme.accent : Color.clear))
                            if selected {
                                Image(systemName: "checkmark")
                                    .font(.system(size: 12, weight: .black))
                                    .foregroundStyle(.canvas)
                            }
                        }
                        .frame(width: 26, height: 26)
                    } else {
                        Image(systemName: "chevron.right")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(Color.ink.opacity(0.3))
                    }
                }
                .padding(.vertical, 8)
                .contentShape(Rectangle())
            }
            .buttonStyle(.pressable)
            if isPicking {
                Button { detail = item } label: {
                    Image(systemName: "info.circle")
                        .font(.system(size: 19, weight: .regular))
                        .foregroundStyle(Color.ink.opacity(0.5))
                        .frame(width: 32, height: 44)
                }
                .accessibilityLabel(L("Техника: \(item.name)"))
            }
        }
        .contextMenu {
            if item.isCustom {
                Button(role: .destructive) {
                    profile.deleteCustomExercise(item.id)
                    picked.removeAll { $0.id == item.id }
                } label: {
                    Label(L("Удалить своё упражнение"), systemImage: "trash")
                }
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

// MARK: - Техника упражнения

/// Карточка упражнения: картинка, инвентарь, мышцы, как выполнять и прогресс.
struct ExerciseDetailView: View {
    @Bindable var profile: ProgramProfile
    let exercise: LibraryExercise

    @Environment(\.dismiss) private var dismiss
    @State private var tab = 0

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Picker("", selection: $tab) {
                        Text(L("Техника")).tag(0)
                        Text(L("Прогресс")).tag(1)
                    }
                    .pickerStyle(.segmented)

                    HStack {
                        Spacer()
                        ExerciseThumb(name: exercise.name, size: 220)
                        Spacer()
                    }
                    .padding(.vertical, 6)

                    if tab == 0 { technique } else { progress }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 32)
            }
            .scrollIndicators(.hidden)
            .screenBackground()
            .navigationTitle(exercise.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(L("Готово")) { dismiss() }.fontWeight(.bold)
                }
            }
        }
    }

    private var technique: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(spacing: 8) {
                chip(ExerciseLibrary.equipmentTitle(exercise.equipment), systemImage: "dumbbell.fill")
                chip(ExerciseLibrary.groupTitle(exercise.group), systemImage: "figure.strengthtraining.traditional")
            }
            VStack(alignment: .leading, spacing: 10) {
                caption("Мышцы")
                FlowMuscles(primary: exercise.group, secondary: exercise.secondary)
            }
            if !exercise.steps.isEmpty {
                VStack(alignment: .leading, spacing: 0) {
                    caption("Как выполнять").padding(.bottom, 6)
                    ForEach(Array(exercise.steps.enumerated()), id: \.offset) { index, step in
                        HStack(alignment: .firstTextBaseline, spacing: 16) {
                            Text("\(index + 1)")
                                .font(.system(size: 18, weight: .heavy, design: Theme.fontDesign))
                                .foregroundStyle(Theme.accent)
                                .frame(width: 18, alignment: .leading)
                            Text(step)
                                .font(.system(size: 16))
                                .foregroundStyle(.ink)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .padding(.vertical, 12)
                        if index < exercise.steps.count - 1 {
                            Divider().overlay(Color.ink.opacity(0.08))
                        }
                    }
                }
            } else if exercise.isCustom {
                Text(L("Своё упражнение — техники здесь нет."))
                    .font(.system(size: 14))
                    .foregroundStyle(Color.ink.opacity(0.5))
            }
        }
    }

    private var progress: some View {
        let points = profile.history(forExerciseNamed: exercise.name)
        return VStack(alignment: .leading, spacing: 14) {
            if points.isEmpty {
                Text(L("Ещё не делал. Сделаешь в тренировке — здесь появятся лучший вес и история."))
                    .font(.system(size: 15))
                    .foregroundStyle(Color.ink.opacity(0.6))
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                HStack(spacing: 10) {
                    stat("Лучший вес", WeightFormat.kilogramsPrecise(points.map(\.best).max() ?? 0))
                    stat("Тренировок", "\(points.count)")
                    stat("Прошлый раз", WeightFormat.kilogramsPrecise(points.last?.best ?? 0))
                }
                NavigationLink {
                    ExerciseHistoryView(profile: profile, name: exercise.name)
                } label: {
                    HStack {
                        Text(L("Вся история"))
                            .font(.system(size: 15, weight: .bold))
                            .foregroundStyle(.ink)
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(Color.ink.opacity(0.3))
                    }
                    .padding(16)
                    .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                .buttonStyle(.pressable)
            }
        }
    }

    private func stat(_ title: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.system(size: 12))
                .foregroundStyle(Color.ink.opacity(0.5))
            Text(value)
                .font(.system(size: 17, weight: .heavy, design: Theme.fontDesign))
                .foregroundStyle(.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func chip(_ text: String, systemImage: String) -> some View {
        HStack(spacing: 6) {
            Image(systemName: systemImage).font(.system(size: 12, weight: .bold))
            Text(text).font(.system(size: 14, weight: .bold))
        }
        .foregroundStyle(.ink)
        .padding(.horizontal, 12)
        .frame(height: 34)
        .background(Color.tone(0.12), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    private func caption(_ text: String) -> some View {
        Text(text.uppercased())
            .font(.system(size: 12, weight: .bold))
            .tracking(1.2)
            .foregroundStyle(Color.ink.opacity(0.45))
    }
}

/// Мышцы чипами: главная — залитая акцентом, остальные — обводкой.
private struct FlowMuscles: View {
    let primary: String
    let secondary: [String]

    var body: some View {
        ChipFlow(spacing: 8) {
            muscle(primary, main: true)
            ForEach(secondary, id: \.self) { muscle($0, main: false) }
        }
    }

    private func muscle(_ id: String, main: Bool) -> some View {
        Text(ExerciseLibrary.groupTitle(id))
            .font(.system(size: 14, weight: .bold))
            .foregroundStyle(main ? Color.white : Color.ink.opacity(0.85))
            .padding(.horizontal, 14)
            .frame(height: 34)
            .background(main ? Theme.accent : Color.clear, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).strokeBorder(main ? Color.clear : Color.ink.opacity(0.25), lineWidth: 1))
    }
}

// MARK: - Своё упражнение

struct CustomExerciseSheet: View {
    let onSave: (LibraryExercise) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var group = "chest"
    @State private var equipment = "barbell"

    private var trimmed: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }

    var body: some View {
        NavigationStack {
            Form {
                Section(L("Название")) {
                    TextField(L("Например, жим Ларсена"), text: $name)
                }
                Section(L("Мышцы")) {
                    Picker(L("Группа"), selection: $group) {
                        ForEach(ExerciseLibrary.groups) { Text($0.title).tag($0.id) }
                    }
                }
                Section(L("Инвентарь")) {
                    Picker(L("Инвентарь"), selection: $equipment) {
                        ForEach(ExerciseLibrary.equipment) { Text($0.title).tag($0.id) }
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .screenBackground()
            .navigationTitle(L("Создать упражнение"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button(L("Отмена")) { dismiss() } }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(L("Сохранить")) {
                        UINotificationFeedbackGenerator().notificationOccurred(.success)
                        onSave(.custom(name: trimmed, group: group, equipment: equipment))
                        dismiss()
                    }
                    .fontWeight(.bold)
                    .disabled(trimmed.isEmpty)
                }
            }
        }
    }
}
