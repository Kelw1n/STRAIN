import SwiftUI
import PhotosUI

/// Экран подведения итогов тренировки «Твоя тренировка» в стиле REBRN
struct WorkoutCompletionSummaryView: View {
    @Environment(\.dismiss) private var dismiss
    @Bindable var profile: ProgramProfile
    let workout: ScheduledWorkout

    @Bindable var sessionManager = WorkoutSessionManager.shared

    @State private var workoutTitle: String = ""
    @State private var workoutDescription: String = ""
    /// Оценка усилия 1–10; пусто — не ставили.
    @State private var effortScore: Int? = nil

    // Выбор фото (до 5 штук): первое ложится на карточку для сторис
    @State private var selectedPhotoItems: [PhotosPickerItem] = []
    @State private var selectedImages: [UIImage] = []
    @State private var showingCamera = false
    /// Публиковать ли тренировку в ленту — по умолчанию да, если есть профиль.
    @State private var publishToFeed = true
    /// Кому из друзей отправить тренировку карточкой в переписку.
    @State private var recipients: Set<String> = []

    // Редактирование времени тренировки
    @State private var durationSeconds: Int = 0
    @State private var showingDurationPicker: Bool = false
    /// Готовая картинка для сторис — открывает лист «Поделиться».
    @State private var shareImage: ShareImage? = nil
    @State private var pickerHours: Int = 1
    @State private var pickerMinutes: Int = 15

    var body: some View {
        NavigationStack {
            ZStack(alignment: .bottom) {
                ScrollView {
                    VStack(spacing: 20) {
                        // ФОТО (0/5)
                        photoVideoSection

                        // НАЗВАНИЕ И ОПИСАНИЕ ТРЕНИРОВКИ
                        titleAndDescriptionSection

                        // ВРЕМЯ
                        timeSection

                        // УСИЛИЕ
                        effortSection

                        // ЛЕНТА STRAIN
                        publishSection

                        // ДРУЗЬЯМ В ПЕРЕПИСКУ
                        if SocialService.shared.isSignedIn {
                            friendsSection
                        }

                        // КАРТОЧКА ДЛЯ СТОРИС
                        shareSection

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
                    .buttonStyle(.pressable)
                    .accessibilityLabel("Назад")
                }

                ToolbarItem(placement: .principal) {
                    VStack(spacing: 2) {
                        Text("Твоя тренировка")
                            .font(.system(size: 17, weight: .bold))
                            .foregroundStyle(.white)

                        Text("Время, усилие и заметка — в историю")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundStyle(Color.white.opacity(0.55))
                    }
                }
            }
            .sheet(isPresented: $showingDurationPicker) {
                durationPickerSheet
            }
            .sheet(item: $shareImage) { item in
                ShareSheet(items: [item.image])
                    .presentationDetents([.medium, .large])
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

    // MARK: - Секция Фото

    /// Фото снимаются прямо отсюда или берутся из галереи. Первое ляжет на
    /// карточку для сторис, все — в пост ленты.
    private var photoVideoSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("ФОТО (\(selectedImages.count)/5)")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(Color.white.opacity(0.45))
                .padding(.leading, 4)

            VStack(spacing: 14) {
                if selectedImages.isEmpty {
                    VStack(spacing: 4) {
                        Text("Добавь фото к тренировке")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(.white)
                        Text("до 5 штук · первое ляжет на карточку для сторис")
                            .font(.system(size: 13))
                            .foregroundStyle(Color.white.opacity(0.50))
                    }
                    .padding(.top, 6)
                    HStack(spacing: 10) {
                        cameraButton(large: true)
                        galleryButton(large: true)
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
                                            _ = withAnimation(Motion.snappy) { selectedImages.remove(at: index) }
                                        }
                                    } label: {
                                        Image(systemName: "xmark.circle.fill")
                                            .font(.system(size: 20))
                                            .foregroundStyle(Color.white, Color.black.opacity(0.7))
                                            .padding(4)
                                    }
                                    .accessibilityLabel("Убрать фото")
                                }
                            }
                            if selectedImages.count < 5 {
                                cameraButton(large: false)
                                galleryButton(large: false)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
            .frame(maxWidth: .infinity)
            .padding(16)
            .background(Color(red: 0.10, green: 0.10, blue: 0.12))
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .strokeBorder(Color.white.opacity(0.08), lineWidth: 1)
            )
            .onChange(of: selectedPhotoItems) { _, newItems in
                guard !newItems.isEmpty else { return }
                Task {
                    var loaded: [UIImage] = []
                    for item in newItems {
                        if let data = try? await item.loadTransferable(type: Data.self),
                           let image = UIImage(data: data) {
                            loaded.append(image.downscaled())
                        }
                    }
                    await MainActor.run {
                        selectedImages = Array((selectedImages + loaded).prefix(5))
                        // Сбрасываем выбор: следующий заход в галерею добавляет, а не заменяет.
                        selectedPhotoItems = []
                    }
                }
            }
            .cameraSheet(isPresented: $showingCamera) { image in
                guard selectedImages.count < 5 else { return }
                selectedImages.append(image.downscaled())
            }
        }
    }

    private func cameraButton(large: Bool) -> some View {
        Button {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            showingCamera = true
        } label: {
            tile(icon: "camera.fill", title: "Снять", large: large)
        }
        .buttonStyle(.pressable)
        .disabled(!CameraPicker.isAvailable)
        .opacity(CameraPicker.isAvailable ? 1 : 0.4)
    }

    private func galleryButton(large: Bool) -> some View {
        PhotosPicker(
            selection: $selectedPhotoItems,
            maxSelectionCount: max(1, 5 - selectedImages.count),
            matching: .images
        ) {
            tile(icon: "photo.on.rectangle", title: "Галерея", large: large)
        }
        .buttonStyle(.pressable)
    }

    private func tile(icon: String, title: String, large: Bool) -> some View {
        VStack(spacing: 6) {
            Image(systemName: icon)
                .font(.system(size: large ? 22 : 18, weight: .semibold))
                .foregroundStyle(.white.opacity(0.9))
            Text(title)
                .font(.system(size: large ? 14 : 11, weight: .bold))
                .foregroundStyle(.white.opacity(large ? 0.9 : 0.6))
        }
        .frame(maxWidth: large ? .infinity : 80)
        .frame(width: large ? nil : 80, height: large ? 76 : 80)
        .background(Color.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    // MARK: - Публикация в ленту

    private var publishSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("ЛЕНТА")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(Color.white.opacity(0.45))
                .padding(.leading, 4)
            VStack(alignment: .leading, spacing: 6) {
                if SocialService.shared.isSignedIn {
                    Toggle(isOn: $publishToFeed) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Опубликовать в ленте STRAIN")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundStyle(.white)
                            Text("Фото, время, объём и все подходы с весами. Друзья смогут поставить лайк и написать комментарий.")
                                .font(.system(size: 12))
                                .foregroundStyle(Color.white.opacity(0.5))
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .tint(Theme.accent)
                } else {
                    HStack(spacing: 12) {
                        Image(systemName: "person.crop.circle.badge.plus")
                            .font(.system(size: 22, weight: .semibold))
                            .foregroundStyle(Theme.accent)
                        Text("Создай профиль во вкладке «Профиль» — и тренировки можно будет публиковать в ленту.")
                            .font(.system(size: 13))
                            .foregroundStyle(Color.white.opacity(0.6))
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
            .padding(16)
            .background(Color(red: 0.10, green: 0.10, blue: 0.12))
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .strokeBorder(Color.white.opacity(0.08), lineWidth: 1)
            )
        }
    }

    /// Друзья, которым тренировка уйдёт карточкой в личные сообщения.
    private var friendsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(recipients.isEmpty ? "ОТПРАВИТЬ ДРУГУ" : "ОТПРАВИТЬ ДРУГУ · \(recipients.count)")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(Color.white.opacity(0.45))
                .padding(.leading, 4)
                .contentTransition(.numericText())
                .animation(Motion.snappy, value: recipients.count)
            VStack(alignment: .leading, spacing: 8) {
                Text("Отметь друзей — тренировка уйдёт им в переписку карточкой со всеми подходами.")
                    .font(.system(size: 12))
                    .foregroundStyle(Color.white.opacity(0.5))
                    .fixedSize(horizontal: false, vertical: true)
                WorkoutRecipientsPicker(selected: $recipients)
            }
            .padding(16)
            .background(Color(red: 0.10, green: 0.10, blue: 0.12))
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .strokeBorder(Color.white.opacity(0.08), lineWidth: 1)
            )
        }
    }

    /// Пост собирается до `finishWorkout`: после него менеджер сбрасывает подходы.
    private func feedDraft() -> SocialPostDraft {
        let exercises: [SocialWorkout.Exercise] = sessionManager.exercises.compactMap { exercise in
            let done = exercise.sets.filter(\.isDone)
            guard !done.isEmpty else { return nil }
            return SocialWorkout.Exercise(
                name: exercise.prescription.name,
                sets: done.map { SocialWorkout.SetLine(reps: $0.reps, weight: $0.weight, record: $0.isRecord) }
            )
        }
        return SocialPostDraft(
            clientId: UUID().uuidString.lowercased(),
            createdAt: Date(),
            title: workoutTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? workout.fullTitle : workoutTitle,
            description: workoutDescription.trimmingCharacters(in: .whitespacesAndNewlines),
            durationSeconds: durationSeconds,
            volumeKg: sessionManager.totalTonnage,
            sets: sessionManager.totalCompletedSets,
            effort: effortScore,
            workout: SocialWorkout(program: profile.programKind.rawValue, exercises: exercises),
            photoCount: 0,
            uploadedPhotos: [],
            // Тренировались вместе — пост выйдет «вместе с» напарником.
            together: SocialService.shared.together.active?.id
        )
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
                .buttonStyle(.pressable)
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
            Text("УСИЛИЕ")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(Color.white.opacity(0.45))
                .padding(.leading, 4)
            EffortSlider(score: $effortScore)
        }
    }

    // MARK: - Карточка для сторис

    private var shareSummary: WorkoutShareSummary {
        WorkoutShareSummary.current(
            title: workoutTitle.isEmpty ? workout.fullTitle : workoutTitle,
            durationSeconds: durationSeconds
        )
    }

    /// Превью карточки и кнопка. Своё фото из раздела выше становится фоном.
    private var shareSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("ПОДЕЛИТЬСЯ")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(Color.white.opacity(0.45))
                .padding(.leading, 4)

            HStack(alignment: .center, spacing: 16) {
                WorkoutShareCard(summary: shareSummary, photo: selectedImages.first)
                    .scaleEffect(0.3, anchor: .topLeading)
                    .frame(width: WorkoutShareCard.size.width * 0.3, height: WorkoutShareCard.size.height * 0.3, alignment: .topLeading)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).strokeBorder(Color.white.opacity(0.12), lineWidth: 1))
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: 10) {
                    Text("Карточка для сторис")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.white)
                    Text(selectedImages.isEmpty
                         ? "Время, тоннаж, рекорды и упражнения. Добавь фото выше — оно станет фоном."
                         : "Первое фото выше — фоном карточки.")
                        .font(.system(size: 12))
                        .foregroundStyle(Color.white.opacity(0.55))
                        .fixedSize(horizontal: false, vertical: true)

                    Button {
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                        if let image = WorkoutShareCard.render(shareSummary, photo: selectedImages.first) {
                            shareImage = ShareImage(image: image)
                        }
                    } label: {
                        Label("Поделиться", systemImage: "square.and.arrow.up")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 9)
                            .background(Theme.accent, in: Capsule())
                    }
                    .buttonStyle(.pressable)
                }
                Spacer(minLength: 0)
            }
            .padding(16)
            .background(Color(red: 0.10, green: 0.10, blue: 0.12))
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .strokeBorder(Color.white.opacity(0.08), lineWidth: 1)
            )
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
        .buttonStyle(.pressable)
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
                .buttonStyle(.pressable)
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

        // Пост в ленту и карточки друзьям — до завершения: потом подходы сессии уже сброшены.
        if SocialService.shared.isSignedIn && (publishToFeed || !recipients.isEmpty) {
            let draft = feedDraft()
            if publishToFeed {
                SocialService.shared.enqueue(draft, photos: selectedImages)
            }
            let card = SocialChatWorkout(
                title: draft.title, durationSeconds: draft.durationSeconds, volumeKg: draft.volumeKg,
                sets: draft.sets, effort: draft.effort, workout: draft.workout
            )
            for username in recipients.sorted() {
                SocialService.shared.send(card, to: username)
            }
        }
        if !publishToFeed { SocialService.shared.finishTogether() }

        // Завершаем тренировку в WorkoutSessionManager
        sessionManager.finishWorkout(
            profile: profile,
            customDuration: durationSeconds,
            effort: EffortScale.text(effortScore)
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
