import SwiftData
import SwiftUI
import UIKit

/// Что копируем к себе: готовая программа из чужой тренировки или дней.
struct ProgramCopyRequest: Identifiable {
    let id = UUID()
    let program: CustomProgram
    let author: String
}

/// Копия открывается в конструкторе «Своей программы»: можно поправить веса,
/// повторы и число недель. Сохранение — новый профиль, текущий не трогаем.
struct CopyProgramSheet: View {
    let request: ProgramCopyRequest

    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @AppStorage("activeProfileID") private var activeProfileID = ""
    @State private var saved: ProgramProfile?

    var body: some View {
        CustomProgramBuilderView(
            initial: request.program,
            title: "Копия @\(request.author)",
            saveTitle: "Сохранить как новый профиль",
            onBack: { dismiss() },
            onSave: save
        )
        .alert("Программа сохранена", isPresented: Binding(get: { saved != nil }, set: { if !$0 { saved = nil } })) {
            Button("Перейти на неё") {
                if let saved { activeProfileID = saved.profileID.uuidString }
                dismiss()
            }
            Button("Остаться", role: .cancel) { dismiss() }
        } message: {
            Text("Это новый профиль «\(saved?.name ?? "")». Переключаться между профилями можно в настройках.")
        }
    }

    private func save(_ program: CustomProgram) {
        let profile = ProgramProfile(customProgram: program, name: "@\(request.author)")
        modelContext.insert(profile)
        try? modelContext.save()
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        saved = profile
    }
}

/// Кнопка «Скопировать к себе» с выбором: вся программа или один день.
struct CopyProgramButton: View {
    let days: [SocialProgramDay]
    let author: String
    /// Тренировка из поста — копируется как была, с весами подходов.
    var workout: (title: String, workout: SocialWorkout)?
    /// Дни программы автора подгружаются при нажатии, если их не передали.
    var loadsAuthorProgram = false

    @State private var loadedDays: [SocialProgramDay]?
    @State private var choosing = false
    @State private var loading = false
    @State private var request: ProgramCopyRequest?

    private var allDays: [SocialProgramDay] { loadedDays ?? days }

    var body: some View {
        Button(action: open) {
            HStack(spacing: 8) {
                if loading {
                    ProgressView().tint(.canvas)
                } else {
                    Image(systemName: "square.and.arrow.down.on.square")
                        .font(.system(size: 15, weight: .bold))
                }
                Text("Скопировать к себе")
                    .font(.system(size: 15, weight: .bold))
            }
            .foregroundStyle(.canvas)
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .background(Color.ink, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.pressable)
        .disabled(loading)
        .confirmationDialog("Что скопировать?", isPresented: $choosing, titleVisibility: .visible) {
            if let workout {
                Button("Эту тренировку с весами") {
                    request = ProgramCopyRequest(
                        program: ProgramCopy.program(workout: workout.workout, title: workout.title, name: workout.title),
                        author: author
                    )
                }
            }
            if allDays.count > 1 {
                Button("Всю программу (\(allDays.count) \(RuPlural.word(allDays.count, "день", "дня", "дней")))") {
                    request = ProgramCopyRequest(program: ProgramCopy.program(days: allDays, name: "Программа @\(author)"), author: author)
                }
            }
            ForEach(Array(allDays.enumerated()), id: \.offset) { _, day in
                Button("Только «\(day.title)»") {
                    request = ProgramCopyRequest(program: ProgramCopy.program(days: [day], name: day.title), author: author)
                }
            }
            Button("Отмена", role: .cancel) {}
        } message: {
            Text("Копия станет «Своей программой» в новом профиле — веса и повторы можно поправить перед сохранением.")
        }
        .sheet(item: $request) { request in
            CopyProgramSheet(request: request)
        }
    }

    private func open() {
        guard loadsAuthorProgram, loadedDays == nil else {
            choosing = true
            return
        }
        loading = true
        Task {
            loadedDays = (try? await SocialService.shared.user(author).programs) ?? []
            loading = false
            choosing = true
        }
    }
}
