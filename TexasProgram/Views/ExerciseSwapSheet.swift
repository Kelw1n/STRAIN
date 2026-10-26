import SwiftUI
import UIKit

/// «Тренажёр занят»: похожие упражнения на ту же мышцу с весом по истории.
struct ExerciseSwapSheet: View {
    @Bindable var profile: ProgramProfile
    let workout: ScheduledWorkout
    let index: Int

    @Environment(\.dismiss) private var dismiss
    @State private var scope: SwapScope = .today
    @State private var custom = ""
    @State private var done: [ExerciseSwap.Suggestion] = []
    @State private var similar: [ExerciseSwap.Suggestion] = []
    @FocusState private var typing: Bool

    private var manager: WorkoutSessionManager { .shared }
    private var exercise: ActiveExerciseState? {
        manager.exercises.indices.contains(index) ? manager.exercises[index] : nil
    }
    private var currentWeight: Double {
        guard let exercise else { return 0 }
        return exercise.sets.first(where: { !$0.isDone })?.weight ?? exercise.sets.first?.weight ?? 0
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                if let exercise {
                    VStack(alignment: .leading, spacing: 16) {
                        header(exercise)
                        if !done.isEmpty {
                            section("Уже делал", done)
                        }
                        if !similar.isEmpty {
                            section(done.isEmpty ? "Похожие" : "Ещё похожие", similar)
                        }
                        customField
                        scopePicker(exercise)
                        let marked = exercise.sets.filter(\.isDone).count
                        if marked > 0 {
                            Text("Отмеченные подходы этого упражнения (\(marked)) снимутся — их место займёт новое.")
                                .font(.system(size: 12))
                                .foregroundStyle(Theme.warning)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                    .padding(.bottom, 32)
                }
            }
            .scrollDismissesKeyboard(.interactively)
            .background(Color.black.ignoresSafeArea())
            .navigationTitle("Заменить упражнение")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Отмена") { dismiss() }.foregroundStyle(.white)
                }
            }
            .task { load() }
        }
        .preferredColorScheme(.dark)
    }

    private func header(_ exercise: ActiveExerciseState) -> some View {
        HStack(spacing: 12) {
            ExerciseThumb(name: exercise.prescription.name, size: 52)
            VStack(alignment: .leading, spacing: 3) {
                Text("Вместо «\(exercise.prescription.name)»")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(.white)
                    .lineLimit(2)
                Text("\(RuPlural.sets(exercise.sets.count)) по \(exercise.prescription.reps) — схема остаётся")
                    .font(.system(size: 13))
                    .foregroundStyle(Color.white.opacity(0.5))
            }
        }
    }

    private func section(_ title: String, _ items: [ExerciseSwap.Suggestion]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title.uppercased())
                .font(.system(size: 12, weight: .bold))
                .tracking(1.2)
                .foregroundStyle(Color.white.opacity(0.45))
            VStack(spacing: 0) {
                ForEach(items) { item in
                    Button { choose(item) } label: {
                        HStack(spacing: 12) {
                            ExerciseThumb(name: item.name, size: 42)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(item.name)
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundStyle(.white)
                                    .lineLimit(2)
                                Text(item.detail)
                                    .font(.system(size: 12))
                                    .foregroundStyle(item.source == .history ? Theme.accent : Color.white.opacity(0.5))
                                    .lineLimit(2)
                            }
                            Spacer(minLength: 8)
                            Image(systemName: "arrow.triangle.2.circlepath")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundStyle(Color.white.opacity(0.35))
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(PressableStyle(scale: 0.985))
                    if item.id != items.last?.id {
                        Divider().overlay(Color.white.opacity(0.06)).padding(.leading, 68)
                    }
                }
            }
            .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
    }

    private var customField: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("СВОЁ УПРАЖНЕНИЕ")
                .font(.system(size: 12, weight: .bold))
                .tracking(1.2)
                .foregroundStyle(Color.white.opacity(0.45))
            HStack(spacing: 8) {
                TextField("Название", text: $custom)
                    .focused($typing)
                    .submitLabel(.done)
                    .onSubmit(chooseCustom)
                    .font(.system(size: 16))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 14)
                    .frame(height: 46)
                    .background(Color(white: 0.13), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                Button("Заменить", action: chooseCustom)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(.black)
                    .padding(.horizontal, 14)
                    .frame(height: 46)
                    .background(Theme.accent, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .opacity(trimmedCustom.isEmpty ? 0.4 : 1)
                    .disabled(trimmedCustom.isEmpty)
            }
        }
    }

    @ViewBuilder
    private func scopePicker(_ exercise: ActiveExerciseState) -> some View {
        if profile.isProgramLift(exercise.prescription) {
            Text("Это основное движение программы — его меняем только на сегодня, в следующий раз оно вернётся.")
                .font(.system(size: 12))
                .foregroundStyle(Color.white.opacity(0.45))
                .fixedSize(horizontal: false, vertical: true)
        } else {
            VStack(alignment: .leading, spacing: 8) {
                Picker("Насколько", selection: $scope) {
                    ForEach(SwapScope.allCases) { Text($0.rawValue).tag($0) }
                }
                .pickerStyle(.segmented)
                Text(scope.explanation)
                    .font(.system(size: 12))
                    .foregroundStyle(Color.white.opacity(0.45))
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var trimmedCustom: String { custom.trimmingCharacters(in: .whitespacesAndNewlines) }

    private func load() {
        guard let exercise else { return }
        let found = ExerciseSwap.suggestions(
            replacing: exercise.prescription.name,
            currentWeight: currentWeight,
            profile: profile,
            week: workout.week,
            day: workout.day.number
        )
        done = found.done
        similar = found.similar
    }

    private func chooseCustom() {
        guard let exercise, !trimmedCustom.isEmpty else { return }
        choose(ExerciseSwap.suggestion(
            named: String(trimmedCustom.prefix(60)),
            currentName: exercise.prescription.name,
            currentWeight: currentWeight,
            profile: profile,
            week: workout.week,
            day: workout.day.number
        ))
    }

    private func choose(_ suggestion: ExerciseSwap.Suggestion) {
        withAnimation(Motion.snappy) {
            manager.swapExercise(exIdx: index, to: suggestion, scope: scope, profile: profile)
        }
        dismiss()
    }
}
