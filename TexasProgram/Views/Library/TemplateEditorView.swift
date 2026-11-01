import SwiftUI
import UIKit

/// Конструктор тренировки, как в REBRN: название, упражнения с отдыхом и
/// подходами «кг × повт», добавить и убрать подход, поменять порядок,
/// добавить из библиотеки. Закрыл с правками — «Выйти без сохранения?».
struct TemplateEditorView: View {
    @Bindable var profile: ProgramProfile
    let original: WorkoutTemplate
    let isNew: Bool
    let onSave: (WorkoutTemplate) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var draft: WorkoutTemplate
    @State private var picking = false
    @State private var confirmingExit = false
    @State private var info: LibraryExercise?

    init(profile: ProgramProfile, original: WorkoutTemplate, isNew: Bool, onSave: @escaping (WorkoutTemplate) -> Void) {
        self.profile = profile
        self.original = original
        self.isNew = isNew
        self.onSave = onSave
        _draft = State(initialValue: original)
    }

    private var trimmedName: String { draft.name.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var canSave: Bool { !trimmedName.isEmpty && !draft.exercises.isEmpty }
    private var changed: Bool { draft != original }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 12) {
                    TextField("Название тренировки", text: $draft.name)
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(.ink)
                        .padding(.horizontal, 16)
                        .frame(height: 56)
                        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 16, style: .continuous))

                    ForEach(Array(draft.exercises.enumerated()), id: \.element.id) { index, exercise in
                        exerciseCard(index: index, exercise: exercise)
                            .transition(.opacity.combined(with: .scale(scale: 0.97)))
                    }

                    Button { picking = true } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "plus").font(.system(size: 15, weight: .bold))
                            Text("Добавить упражнения").font(.system(size: 15, weight: .bold))
                        }
                        .foregroundStyle(draft.exercises.isEmpty ? Color.canvas : Color.ink)
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                        .background(draft.exercises.isEmpty ? Theme.accent : Color.tone(0.16),
                                    in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }
                    .buttonStyle(.pressable)
                    Spacer(minLength: 40)
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .animation(Motion.snappy, value: draft.exercises.map(\.id))
            }
            .scrollIndicators(.hidden)
            .scrollDismissesKeyboard(.interactively)
            .screenBackground()
            .navigationTitle(isNew ? "Новая тренировка" : "Тренировка")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Отмена") {
                        if changed { confirmingExit = true } else { dismiss() }
                    }
                    .foregroundStyle(Color.ink.opacity(0.7))
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Сохранить", action: save)
                        .font(.body.weight(.bold))
                        .foregroundStyle(canSave ? Theme.accent : Color.ink.opacity(0.3))
                        .disabled(!canSave)
                }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("Готово") {
                        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                    }
                }
            }
            .confirmationDialog("Выйти без сохранения?", isPresented: $confirmingExit, titleVisibility: .visible) {
                Button("Выйти", role: .destructive) { dismiss() }
                Button("Остаться", role: .cancel) {}
            } message: {
                Text("Изменения в тренировке пропадут.")
            }
        }
        .interactiveDismissDisabled(changed)
        .sheet(isPresented: $picking) {
            ExerciseLibraryView(profile: profile, onPick: { items in
                withAnimation(Motion.snappy) {
                    draft.exercises += items.map { item in
                        TemplateExercise(name: item.name, restSeconds: profile.defaultRestSeconds)
                    }
                }
            }, pickTitle: "Добавить")
        }
        .sheet(item: $info) { item in
            ExerciseDetailView(profile: profile, exercise: item)
        }
    }

    private func save() {
        guard canSave else { return }
        var result = draft
        result.name = trimmedName
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        onSave(result)
        dismiss()
    }

    // MARK: Упражнение

    private func exerciseCard(index: Int, exercise: TemplateExercise) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                Button {
                    info = ExerciseLibrary.find(named: exercise.name, custom: profile.customExercises)
                } label: {
                    ExerciseThumb(name: exercise.name, size: 48)
                }
                .buttonStyle(.plain)
                .disabled(ExerciseLibrary.find(named: exercise.name, custom: profile.customExercises) == nil)
                VStack(alignment: .leading, spacing: 4) {
                    Text(exercise.name)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.ink)
                        .lineLimit(2)
                    Menu {
                        ForEach(RestFormat.options, id: \.self) { seconds in
                            Button {
                                draft.exercises[index].restSeconds = seconds
                            } label: {
                                if seconds == exercise.restSeconds {
                                    Label(RestFormat.text(seconds), systemImage: "checkmark")
                                } else {
                                    Text(RestFormat.text(seconds))
                                }
                            }
                        }
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "timer").font(.system(size: 11, weight: .bold))
                            Text("Отдых " + RestFormat.text(exercise.restSeconds)).font(.system(size: 12, weight: .semibold))
                            Image(systemName: "chevron.down").font(.system(size: 9, weight: .bold))
                        }
                        .foregroundStyle(Theme.accent)
                    }
                }
                Spacer(minLength: 4)
                Menu {
                    if index > 0 {
                        Button { move(index, by: -1) } label: { Label("Выше", systemImage: "arrow.up") }
                    }
                    if index < draft.exercises.count - 1 {
                        Button { move(index, by: 1) } label: { Label("Ниже", systemImage: "arrow.down") }
                    }
                    Button(role: .destructive) {
                        _ = withAnimation(Motion.snappy) { draft.exercises.remove(at: index) }
                    } label: { Label("Убрать упражнение", systemImage: "trash") }
                } label: {
                    Image(systemName: "ellipsis")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(Color.ink.opacity(0.6))
                        .frame(width: 36, height: 36)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel("Действия: \(exercise.name)")
            }

            HStack {
                Text("ПОДХОД").frame(width: 64, alignment: .leading)
                Text("КГ").frame(maxWidth: .infinity)
                Text("ПОВТ").frame(maxWidth: .infinity)
                Color.clear.frame(width: 30)
            }
            .font(.system(size: 11, weight: .bold))
            .tracking(1)
            .foregroundStyle(Color.ink.opacity(0.4))

            ForEach(Array(exercise.sets.enumerated()), id: \.offset) { setIndex, _ in
                setRow(exercise: index, set: setIndex)
            }

            Button {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                let last = draft.exercises[index].sets.last ?? TemplateSet(weight: 0, reps: 10)
                withAnimation(Motion.snappy) { draft.exercises[index].sets.append(last) }
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "plus").font(.system(size: 12, weight: .bold))
                    Text("Подход").font(.system(size: 14, weight: .bold))
                }
                .foregroundStyle(.ink)
                .frame(maxWidth: .infinity)
                .frame(height: 38)
                .background(Color.tone(0.14), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            }
            .buttonStyle(.pressable)
        }
        .padding(14)
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private func setRow(exercise: Int, set: Int) -> some View {
        HStack {
            Text("\(set + 1)")
                .font(.system(size: 15, weight: .bold, design: Theme.fontDesign))
                .foregroundStyle(Color.ink.opacity(0.7))
                .frame(width: 64, alignment: .leading)
            TemplateNumberField(value: weightBinding(exercise, set), placeholder: "—", decimal: true)
            TemplateNumberField(value: repsBinding(exercise, set), placeholder: "0", decimal: false)
            Button {
                guard draft.exercises[exercise].sets.count > 1 else { return }
                UIImpactFeedbackGenerator(style: .rigid).impactOccurred()
                _ = withAnimation(Motion.snappy) { draft.exercises[exercise].sets.remove(at: set) }
            } label: {
                Image(systemName: "minus.circle.fill")
                    .font(.system(size: 18))
                    .foregroundStyle(Color.ink.opacity(draft.exercises[exercise].sets.count > 1 ? 0.35 : 0.1))
            }
            .frame(width: 30)
            .disabled(draft.exercises[exercise].sets.count <= 1)
            .accessibilityLabel("Убрать подход \(set + 1)")
        }
    }

    private func weightBinding(_ exercise: Int, _ set: Int) -> Binding<Double> {
        Binding(
            get: { draft.exercises.indices.contains(exercise) && draft.exercises[exercise].sets.indices.contains(set) ? draft.exercises[exercise].sets[set].weight : 0 },
            set: { value in
                guard draft.exercises.indices.contains(exercise), draft.exercises[exercise].sets.indices.contains(set) else { return }
                draft.exercises[exercise].sets[set].weight = value
            }
        )
    }

    private func repsBinding(_ exercise: Int, _ set: Int) -> Binding<Double> {
        Binding(
            get: { draft.exercises.indices.contains(exercise) && draft.exercises[exercise].sets.indices.contains(set) ? Double(draft.exercises[exercise].sets[set].reps) : 0 },
            set: { value in
                guard draft.exercises.indices.contains(exercise), draft.exercises[exercise].sets.indices.contains(set) else { return }
                draft.exercises[exercise].sets[set].reps = max(0, Int(value.rounded()))
            }
        )
    }

    private func move(_ index: Int, by offset: Int) {
        let target = index + offset
        guard draft.exercises.indices.contains(target) else { return }
        UISelectionFeedbackGenerator().selectionChanged()
        withAnimation(Motion.reorder) { draft.exercises.swapAt(index, target) }
    }
}

/// Число в ячейке подхода: пустое поле — ноль, запятая и точка — одно и то же.
private struct TemplateNumberField: View {
    @Binding var value: Double
    let placeholder: String
    let decimal: Bool

    @State private var text = ""
    @FocusState private var focused: Bool

    var body: some View {
        TextField(placeholder, text: $text)
            .keyboardType(decimal ? .decimalPad : .numberPad)
            .focused($focused)
            .multilineTextAlignment(.center)
            .font(.system(size: 17, weight: .bold, design: Theme.fontDesign))
            .foregroundStyle(.ink)
            .frame(maxWidth: .infinity)
            .frame(height: 40)
            .background(Color.tone(focused ? 0.17 : 0.13), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).strokeBorder(Theme.accent.opacity(focused ? 1 : 0), lineWidth: 1.5))
            .onAppear { text = format(value) }
            .onChange(of: value) { _, newValue in
                if parse(text) != newValue { text = format(newValue) }
            }
            .onChange(of: text) { _, newText in
                let parsed = parse(newText)
                if parsed != value { value = parsed }
            }
    }

    private func parse(_ text: String) -> Double {
        let value = Double(text.replacingOccurrences(of: ",", with: ".").trimmingCharacters(in: .whitespaces)) ?? 0
        return value.isFinite ? max(0, value) : 0
    }

    private func format(_ value: Double) -> String {
        guard value > 0 else { return "" }
        return decimal ? WeightFormat.plain(value) : String(Int(value))
    }
}
