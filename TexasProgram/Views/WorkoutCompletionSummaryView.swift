import SwiftUI
import PhotosUI

/// Оценка субъективного усилия тренировки в стиле REBRN
enum WorkoutEffort: String, CaseIterable, Identifiable {
    case easy = "ЛЕГКО"
    case normal = "НОРМАЛЬНО"
    case veryHard = "ОЧЕНЬ ТЯЖЕЛО"

    var id: String { rawValue }

    var color: Color {
        switch self {
        case .easy:
            return Color(red: 0.18, green: 0.84, blue: 0.45) // #2ED573
        case .normal:
            return Color(red: 1.0, green: 0.65, blue: 0.20) // #FFA533
        case .veryHard:
            return Color(red: 1.0, green: 0.28, blue: 0.34) // #FF4757
        }
    }

    var icon: String {
        switch self {
        case .easy:
            return "leaf.fill"
        case .normal:
            return "flame.fill"
        case .veryHard:
            return "bolt.heart.fill"
        }
    }
}

/// Экран подведения итогов тренировки «Твоя тренировка» в стиле REBRN
struct WorkoutCompletionSummaryView: View {
    @Environment(\.dismiss) private var dismiss
    @Bindable var profile: ProgramProfile
    let workout: ScheduledWorkout

    @Bindable var sessionManager = WorkoutSessionManager.shared

    @State private var workoutTitle: String = ""
    @State private var workoutDescription: String = ""
    @State private var selectedEffort: WorkoutEffort? = nil

    // Выбор фото/видео (до 5 штук)
    @State private var selectedPhotoItems: [PhotosPickerItem] = []
    @State private var selectedImages: [UIImage] = []

    // Редактирование времени тренировки
    @State private var durationSeconds: Int = 0
    @State private var showingDurationPicker: Bool = false
    @State private var pickerHours: Int = 1
    @State private var pickerMinutes: Int = 15

    var body: some View {
        NavigationStack {
            ZStack(alignment: .bottom) {
                ScrollView {
                    VStack(spacing: 20) {
                        // ФОТО И ВИДЕО (0/5)
                        photoVideoSection

                        // НАЗВАНИЕ И ОПИСАНИЕ ТРЕНИРОВКИ
                        titleAndDescriptionSection

                        // ВРЕМЯ
                        timeSection

                        // УСИЛИЕ
                        effortSection

                        // Зазор под нижнюю кнопку
                        Spacer(minLength: 90)
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 16)
                }
                .scrollIndicators(.hidden)
                .background(Color.black.ignoresSafeArea())

                // Нижняя большая белая кнопка «ДАЛЬШЕ»
                bottomActionButton
                    .padding(.horizontal, 16)
                    .padding(.bottom, 20)
                    .background(
                        LinearGradient(
                            colors: [
                                Color.black.opacity(0),
                                Color.black.opacity(0.95),
                                Color.black
                            ],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                        .frame(height: 110)
                        .ignoresSafeArea(),
                        alignment: .bottom
                    )
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        sessionManager.showingSummary = false
                    } label: {
                        Image(systemName: "arrow.left")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(width: 38, height: 38)
                            .background(Color.white.opacity(0.12), in: Circle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Назад")
                }

                ToolbarItem(placement: .principal) {
                    VStack(spacing: 2) {
                        Text("Твоя тренировка")
                            .font(.system(size: 17, weight: .bold))
                            .foregroundStyle(.white)

                        Text("Увидят подписчики в твоём профиле")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundStyle(Color.white.opacity(0.55))
                    }
                }
            }
            .sheet(isPresented: $showingDurationPicker) {
                durationPickerSheet
            }
        }
        .preferredColorScheme(.dark)
        .onAppear {
            workoutTitle = workout.fullTitle
            workoutDescription = sessionManager.noteText.isEmpty
                ? profile.note(week: workout.week, day: workout.day.number)
                : sessionManager.noteText
            durationSeconds = max(sessionManager.elapsedSeconds, 60)
            pickerHours = durationSeconds / 3600
            pickerMinutes = max(1, (durationSeconds % 3600) / 60)
        }
    }

    // MARK: - Секция Фото и Видео

    private var photoVideoSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("ФОТО И ВИДЕО (\(selectedImages.count)/5)")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(Color.white.opacity(0.45))
                .padding(.leading, 4)

            PhotosPicker(
                selection: $selectedPhotoItems,
                maxSelectionCount: 5,
                matching: .any(of: [.images, .videos])
            ) {
                VStack(spacing: 12) {
                    if selectedImages.isEmpty {
                        ZStack {
                            Circle()
                                .fill(Color.white.opacity(0.08))
                                .frame(width: 56, height: 56)

                            Image(systemName: "camera.fill")
                                .font(.system(size: 24, weight: .medium))
                                .foregroundStyle(Color.white.opacity(0.90))
                        }

                        VStack(spacing: 4) {
                            Text("Добавить фото и видео")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundStyle(.white)

                            Text("до 5 штук · видео до 30 секунд")
                                .font(.system(size: 13, weight: .regular))
                                .foregroundStyle(Color.white.opacity(0.50))
                        }
                    } else {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(Array(selectedImages.enumerated()), id: \.offset) { index, img in
                                    ZStack(alignment: .topTrailing) {
                                        Image(uiImage: img)
                                            .resizable()
                                            .scaledToFill()
                                            .frame(width: 80, height: 80)
                                            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))

                                        Button {
                                            if index < selectedImages.count {
                                                selectedImages.remove(at: index)
                                            }
                                            if index < selectedPhotoItems.count {
                                                selectedPhotoItems.remove(at: index)
                                            }
                                        } label: {
                                            Image(systemName: "xmark.circle.fill")
                                                .font(.system(size: 20))
                                                .foregroundStyle(Color.white, Color.black.opacity(0.7))
                                                .padding(4)
                                        }
                                    }
                                }

                                if selectedImages.count < 5 {
                                    VStack(spacing: 6) {
                                        Image(systemName: "plus")
                                            .font(.system(size: 20, weight: .bold))
                                            .foregroundStyle(.white.opacity(0.8))
                                        Text("Ещё")
                                            .font(.system(size: 11, weight: .medium))
                                            .foregroundStyle(.white.opacity(0.6))
                                    }
                                    .frame(width: 80, height: 80)
                                    .background(Color.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                                }
                            }
                            .padding(.vertical, 4)
                        }
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, selectedImages.isEmpty ? 28 : 16)
                .padding(.horizontal, 16)
                .background(Color(red: 0.10, green: 0.10, blue: 0.12))
                .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 22, style: .continuous)
                        .strokeBorder(Color.white.opacity(0.08), lineWidth: 1)
                )
            }
            .buttonStyle(.plain)
            .onChange(of: selectedPhotoItems) { _, newItems in
                Task {
                    var loaded: [UIImage] = []
                    for item in newItems {
                        if let data = try? await item.loadTransferable(type: Data.self),
                           let image = UIImage(data: data) {
                            loaded.append(image)
                        }
                    }
                    await MainActor.run {
                        selectedImages = loaded
                    }
                }
            }
        }
    }

    // MARK: - Секция Название и Описание

    private var titleAndDescriptionSection: some View {
        VStack(spacing: 0) {
            // Название тренировки
            HStack(spacing: 12) {
                TextField("Название тренировки", text: $workoutTitle)
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(.white)

                Image(systemName: "pencil")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Color.white.opacity(0.35))
            }
            .padding(16)

            Divider()
                .background(Color.white.opacity(0.08))

            // Описание
            HStack(alignment: .top, spacing: 12) {
                ZStack(alignment: .topLeading) {
                    if workoutDescription.isEmpty {
                        Text("Описание")
                            .font(.system(size: 15, weight: .regular))
                            .foregroundStyle(Color.white.opacity(0.35))
                            .padding(.top, 8)
                            .padding(.leading, 4)
                    }

                    TextEditor(text: $workoutDescription)
                        .font(.system(size: 15, weight: .regular))
                        .foregroundStyle(.white)
                        .frame(minHeight: 70)
                        .scrollContentBackground(.hidden)
                }

                Image(systemName: "pencil")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Color.white.opacity(0.35))
                    .padding(.top, 8)
            }
            .padding(16)
        }
        .background(Color(red: 0.10, green: 0.10, blue: 0.12))
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .strokeBorder(Color.white.opacity(0.08), lineWidth: 1)
        )
    }

    // MARK: - Секция Время

    private var timeSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("ВРЕМЯ")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(Color.white.opacity(0.45))
                .padding(.leading, 4)

            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(formatDuration(durationSeconds))
                        .font(.system(size: 26, weight: .black, design: Theme.fontDesign))
                        .foregroundStyle(.white)

                    Text("В приложении: \(formatDuration(sessionManager.elapsedSeconds))")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(Color.white.opacity(0.50))
                }

                Spacer()

                Button {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    showingDurationPicker = true
                } label: {
                    Text("Изменить")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(Theme.accent, in: Capsule())
                }
                .buttonStyle(.plain)
            }
            .padding(18)
            .background(Color(red: 0.10, green: 0.10, blue: 0.12))
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .strokeBorder(Color.white.opacity(0.08), lineWidth: 1)
            )
        }
    }

    // MARK: - Секция Усилие

    private var effortSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("УСИЛИЕ")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Color.white.opacity(0.45))

                Spacer()

                Text("Можно пропустить")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Color.white.opacity(0.45))
            }
            .padding(.horizontal, 4)

            HStack(spacing: 10) {
                ForEach(WorkoutEffort.allCases) { effort in
                    let isSelected = selectedEffort == effort

                    Button {
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                        if selectedEffort == effort {
                            selectedEffort = nil
                        } else {
                            selectedEffort = effort
                        }
                    } label: {
                        VStack(spacing: 8) {
                            Circle()
                                .fill(isSelected ? effort.color : effort.color.opacity(0.35))
                                .frame(width: 10, height: 10)

                            Text(effort.rawValue)
                                .font(.system(size: 13, weight: .bold))
                                .foregroundStyle(isSelected ? .white : Color.white.opacity(0.70))
                                .multilineTextAlignment(.center)
                                .lineLimit(1)
                                .minimumScaleFactor(0.75)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .padding(.horizontal, 6)
                        .background(
                            isSelected
                                ? effort.color.opacity(0.18)
                                : Color(red: 0.10, green: 0.10, blue: 0.12)
                        )
                        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 18, style: .continuous)
                                .strokeBorder(
                                    isSelected ? effort.color : Color.white.opacity(0.08),
                                    lineWidth: isSelected ? 2 : 1
                                )
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    // MARK: - Нижняя кнопка «ДАЛЬШЕ»

    private var bottomActionButton: some View {
        Button {
            handleComplete()
        } label: {
            Text("ДАЛЬШЕ")
                .font(.system(size: 16, weight: .heavy, design: Theme.fontDesign))
                .foregroundStyle(.black)
                .frame(maxWidth: .infinity)
                .frame(height: 54)
                .background(Color.white, in: Capsule())
                .shadow(color: Color.white.opacity(0.20), radius: 12, y: 3)
        }
        .buttonStyle(.plain)
    }

    // MARK: - Модальное окно выбора продолжительности

    private var durationPickerSheet: some View {
        NavigationStack {
            VStack(spacing: 20) {
                Text("Укажи фактическую длительность тренировки")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Color.white.opacity(0.70))
                    .multilineTextAlignment(.center)
                    .padding(.top, 16)

                HStack(spacing: 0) {
                    Picker("Часы", selection: $pickerHours) {
                        ForEach(0..<6) { h in
                            Text("\(h) ч").tag(h)
                        }
                    }
                    .pickerStyle(.wheel)
                    .frame(maxWidth: .infinity)

                    Picker("Минуты", selection: $pickerMinutes) {
                        ForEach(1..<60) { m in
                            Text("\(m) мин").tag(m)
                        }
                    }
                    .pickerStyle(.wheel)
                    .frame(maxWidth: .infinity)
                }
                .padding(.horizontal, 16)

                Spacer()

                Button {
                    durationSeconds = (pickerHours * 3600) + (pickerMinutes * 60)
                    showingDurationPicker = false
                } label: {
                    Text("Применить")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.black)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(Color.white, in: Capsule())
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 20)
                .padding(.bottom, 16)
            }
            .background(Color(red: 0.08, green: 0.08, blue: 0.10).ignoresSafeArea())
            .navigationTitle("Время тренировки")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Закрыть") {
                        showingDurationPicker = false
                    }
                    .foregroundStyle(Theme.accent)
                }
            }
        }
        .presentationDetents([.height(340)])
    }

    // MARK: - Завершение и сохранение

    private func handleComplete() {
        UIImpactFeedbackGenerator(style: .heavy).impactOccurred()

        // Сохраняем заметку к тренировке
        if !workoutDescription.isEmpty {
            profile.setNote(
                workoutDescription,
                week: workout.week,
                day: workout.day.number
            )
        }

        // Завершаем тренировку в WorkoutSessionManager
        sessionManager.finishWorkout(
            profile: profile,
            customDuration: durationSeconds,
            effort: selectedEffort?.rawValue
        )

        // Закрываем окно итогов
        sessionManager.showingSummary = false
    }

    private func formatDuration(_ seconds: Int) -> String {
        let hours = seconds / 3600
        let mins = max(1, (seconds % 3600) / 60)
        if hours > 0 {
            return "\(hours)ч \(mins)м"
        } else {
            return "\(mins)м"
        }
    }
}
