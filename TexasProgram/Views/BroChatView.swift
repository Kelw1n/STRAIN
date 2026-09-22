import SwiftUI
import PhotosUI

/// Экран тренировочного чата с бро (сообщения, шеринг подходов и фото)
struct BroChatView: View {
    let buddy: BroProfileData
    let profile: ProgramProfile
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var scheme

    @State private var service = BroTrackerService.shared
    @State private var messages: [BroChatMessage] = []
    @State private var inputText: String = ""
    @State private var selectedPhotoItem: PhotosPickerItem? = nil
    @State private var showingWorkoutShareSheet = false
    @State private var fullScreenPhoto: UIImage? = nil
    @State private var isSending = false

    private let quickPhrases = [
        "Я в зале 🏋️",
        "Накинь блины 🥞",
        "Подстрахуй 🤝",
        "Закончил день 🏁",
        "Памп бешеный 🔥"
    ]

    var body: some View {
        NavigationStack {
            ZStack {
                AppBackground()

                VStack(spacing: 0) {
                    // Шапка собеседника
                    chatHeader

                    Divider().opacity(0.3)

                    // Список сообщений
                    messagesScrollView

                    // Быстрые чипы
                    quickPhrasesBar

                    Divider().opacity(0.3)

                    // Нижняя панель ввода
                    inputBar
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(action: { dismiss() }) {
                        Image(systemName: "xmark")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .task {
                reloadMessages()
                // Циклическое обновление онлайна и сообщений
                while !Task.isCancelled {
                    try? await Task.sleep(nanoseconds: 3_000_000_000)
                    await service.refreshBuddies()
                    reloadMessages()
                }
            }
            .onChange(of: selectedPhotoItem) { _, newItem in
                guard let newItem else { return }
                Task {
                    if let data = try? await newItem.loadTransferable(type: Data.self),
                       let uiImage = UIImage(data: data) {
                        await sendPhoto(uiImage)
                    }
                    selectedPhotoItem = nil
                }
            }
            .sheet(isPresented: $showingWorkoutShareSheet) {
                WorkoutShareSheet(profile: profile) { payload in
                    Task {
                        await sendWorkoutResult(payload)
                    }
                }
            }
            .fullScreenCover(item: Binding<FullScreenImage?>(
                get: { fullScreenPhoto.map { FullScreenImage(image: $0) } },
                set: { fullScreenPhoto = $0?.image }
            )) { item in
                ZStack {
                    Color.black.ignoresSafeArea()
                    Image(uiImage: item.image)
                        .resizable()
                        .scaledToFit()
                    VStack {
                        HStack {
                            Spacer()
                            Button(action: { fullScreenPhoto = nil }) {
                                Image(systemName: "xmark.circle.fill")
                                    .font(.title)
                                    .foregroundStyle(.white)
                                    .padding()
                            }
                        }
                        Spacer()
                    }
                }
            }
        }
    }

    private var chatHeader: some View {
        HStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(Theme.accentGradient)
                    .frame(width: 42, height: 42)
                Text(buddy.name.prefix(1).uppercased())
                    .font(.headline.weight(.bold))
                    .foregroundStyle(.white)
            }

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(buddy.name)
                        .font(.headline)
                        .foregroundStyle(.primary)

                    Circle()
                        .fill(buddy.isOnline ? Theme.success : (buddy.isRecentlyActive ? Theme.warning : Color.gray))
                        .frame(width: 8, height: 8)
                }

                Text(buddy.statusDescription)
                    .font(.caption2)
                    .foregroundStyle(buddy.isOnline ? Theme.success : .secondary)
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 2) {
                Text("ПРОГРАММА")
                    .font(.system(size: 9, weight: .bold))
                    .foregroundStyle(.secondary)
                Text("Нед. \(buddy.currentWeek) · Дн. \(buddy.currentDay)")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Theme.accent)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }

    private var messagesScrollView: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 12) {
                    if messages.isEmpty {
                        VStack(spacing: 8) {
                            Image(systemName: "message.badge.filled.fill")
                                .font(.system(size: 40))
                                .foregroundStyle(Theme.accent.opacity(0.6))
                                .padding(.top, 40)
                            Text("Здесь начнётся ваш разговор!")
                                .font(.headline)
                                .foregroundStyle(.primary)
                            Text("Поделись результатом подхода или черкани пару слов бро.")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .multilineTextAlignment(.center)
                        }
                        .padding(.horizontal, 32)
                    } else {
                        ForEach(messages) { msg in
                            MessageBubble(
                                message: msg,
                                isMe: msg.senderId == service.myBroId,
                                onPhotoTap: { img in
                                    fullScreenPhoto = img
                                }
                            )
                            .id(msg.id)
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 14)
            }
            .onChange(of: messages.count) { _, _ in
                if let last = messages.last {
                    withAnimation {
                        proxy.scrollTo(last.id, anchor: .bottom)
                    }
                }
            }
        }
    }

    private var quickPhrasesBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(quickPhrases, id: \.self) { phrase in
                    Button(action: {
                        Task { await sendText(phrase) }
                    }) {
                        Text(phrase)
                            .font(.caption.weight(.medium))
                            .foregroundStyle(.primary)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(Color.primary.opacity(0.06), in: Capsule())
                            .overlay(Capsule().strokeBorder(Theme.hairline(scheme), lineWidth: 1))
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
        }
    }

    private var inputBar: some View {
        HStack(spacing: 10) {
            // Кнопка прикрепления фото
            PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                Image(systemName: "camera.fill")
                    .font(.system(size: 18))
                    .foregroundStyle(Theme.accent)
                    .frame(width: 38, height: 38)
                    .background(Color.primary.opacity(0.06), in: Circle())
            }

            // Кнопка шеринга текущего подхода/рекорда
            Button(action: { showingWorkoutShareSheet = true }) {
                Image(systemName: "flame.fill")
                    .font(.system(size: 18))
                    .foregroundStyle(Theme.warning)
                    .frame(width: 38, height: 38)
                    .background(Color.primary.opacity(0.06), in: Circle())
            }

            // Поле ввода текста
            TextField("Сообщение бро...", text: $inputText)
                .textFieldStyle(.plain)
                .padding(.horizontal, 14)
                .padding(.vertical, 9)
                .background(Color.primary.opacity(0.06), in: RoundedRectangle(cornerRadius: 20))
                .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(Theme.hairline(scheme), lineWidth: 1))
                .foregroundStyle(.primary)
                .onSubmit {
                    Task { await sendCurrentText() }
                }

            // Кнопка отправки
            Button(action: {
                Task { await sendCurrentText() }
            }) {
                Image(systemName: "arrow.up.circle.fill")
                    .font(.system(size: 32))
                    .foregroundStyle(inputText.trimmingCharacters(in: .whitespaces).isEmpty ? Color.secondary.opacity(0.4) : Theme.accent)
            }
            .disabled(inputText.trimmingCharacters(in: .whitespaces).isEmpty || isSending)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(Theme.surface(scheme))
    }

    private func reloadMessages() {
        self.messages = service.getMessages(buddyId: buddy.broId)
    }

    private func sendCurrentText() async {
        let trimmed = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        inputText = ""
        await sendText(trimmed)
    }

    private func sendText(_ text: String) async {
        isSending = true
        defer { isSending = false }
        _ = await service.sendMessage(
            to: buddy.broId,
            text: text,
            type: .text,
            profile: profile
        )
        reloadMessages()
    }

    private func sendWorkoutResult(_ payload: WorkoutSharePayload) async {
        isSending = true
        defer { isSending = false }
        let text = "\(payload.isPR ? "🔥 НОВЫЙ РЕКОРД!" : "💥 Подход выполнен:") \(payload.exercise) \(payload.weight) \(payload.sets)×\(payload.reps)"
        _ = await service.sendMessage(
            to: buddy.broId,
            text: text,
            type: .workoutResult,
            workoutPayload: payload,
            profile: profile
        )
        reloadMessages()
    }

    private func sendPhoto(_ uiImage: UIImage) async {
        isSending = true
        defer { isSending = false }

        // Сжимаем изображение до 800x800 и JPEG качества 0.6
        let resized = resizeImage(uiImage, maxDimension: 800)
        guard let jpegData = resized.jpegData(compressionQuality: 0.6) else { return }
        let base64 = "data:image/jpeg;base64," + jpegData.base64EncodedString()

        _ = await service.sendMessage(
            to: buddy.broId,
            text: "📷 Фотография с тренировки",
            type: .photo,
            photoBase64: base64,
            profile: profile
        )
        reloadMessages()
    }

    private func resizeImage(_ image: UIImage, maxDimension: CGFloat) -> UIImage {
        let size = image.size
        let ratio = min(maxDimension / size.width, maxDimension / size.height)
        if ratio >= 1.0 { return image }
        let newSize = CGSize(width: size.width * ratio, height: size.height * ratio)
        UIGraphicsBeginImageContextWithOptions(newSize, false, 1.0)
        image.draw(in: CGRect(origin: .zero, size: newSize))
        let resized = UIGraphicsGetImageFromCurrentImageContext() ?? image
        UIGraphicsEndImageContext()
        return resized
    }
}

/// Пузырь отдельного сообщения
private struct MessageBubble: View {
    let message: BroChatMessage
    let isMe: Bool
    let onPhotoTap: (UIImage) -> Void
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        HStack(alignment: .bottom, spacing: 6) {
            if isMe { Spacer(minLength: 40) }

            VStack(alignment: isMe ? .trailing : .leading, spacing: 4) {
                if !isMe {
                    Text(message.senderName)
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 4)
                }

                switch message.type {
                case .text:
                    Text(message.text)
                        .font(.subheadline)
                        .foregroundStyle(isMe ? .white : .primary)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(
                            isMe ? AnyShapeStyle(Theme.accentGradient) : AnyShapeStyle(Color.primary.opacity(0.06)),
                            in: RoundedRectangle(cornerRadius: 16)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .strokeBorder(isMe ? Color.clear : Theme.hairline(scheme), lineWidth: 1)
                        )

                case .workoutResult:
                    if let p = message.workoutPayload {
                        WorkoutResultBubble(payload: p, isMe: isMe)
                    } else {
                        Text(message.text)
                            .font(.subheadline)
                            .foregroundStyle(.primary)
                            .padding(12)
                            .background(Color.primary.opacity(0.06), in: RoundedRectangle(cornerRadius: 16))
                    }

                case .photo:
                    PhotoBubble(message: message, isMe: isMe, onPhotoTap: onPhotoTap)
                }

                Text(message.timeFormatted)
                    .font(.system(size: 9))
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 4)
            }

            if !isMe { Spacer(minLength: 40) }
        }
    }
}

/// Специальная карточка для шеринга результата подхода
private struct WorkoutResultBubble: View {
    let payload: WorkoutSharePayload
    let isMe: Bool
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Image(systemName: payload.isPR ? "trophy.fill" : "flame.fill")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(payload.isPR ? Color.yellow : Theme.warning)

                Text(payload.isPR ? "ЛИЧНЫЙ РЕКОРД" : "РЕЗУЛЬТАТ ПОДХОДА")
                    .font(.system(size: 10, weight: .black))
                    .foregroundStyle(payload.isPR ? Color.yellow : Theme.warning)

                Spacer()

                Text("Нед. \(payload.week) · Дн. \(payload.day)")
                    .font(.system(size: 9, weight: .semibold))
                    .foregroundStyle(.secondary)
            }

            Text(payload.exercise)
                .font(.headline.weight(.bold))
                .foregroundStyle(.primary)

            HStack(spacing: 8) {
                HStack(spacing: 4) {
                    Text(payload.weight)
                        .font(.title3.weight(.black))
                        .foregroundStyle(Theme.accent)
                }

                Text("·")
                    .foregroundStyle(.secondary)

                Text("\(payload.sets)×\(payload.reps)")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.primary)
            }
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Theme.surface(scheme))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .strokeBorder(payload.isPR ? Color.yellow.opacity(0.6) : Theme.accent.opacity(0.4), lineWidth: 1.5)
                )
        )
    }
}

/// Пузырь с фото
private struct PhotoBubble: View {
    let message: BroChatMessage
    let isMe: Bool
    let onPhotoTap: (UIImage) -> Void
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        VStack(alignment: isMe ? .trailing : .leading, spacing: 6) {
            if let base64 = message.photoBase64,
               let uiImage = decodeBase64(base64) {
                Image(uiImage: uiImage)
                    .resizable()
                    .scaledToFill()
                    .frame(maxWidth: 240, maxHeight: 240)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                    .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Theme.hairline(scheme), lineWidth: 1))
                    .onTapGesture {
                        onPhotoTap(uiImage)
                    }
            } else {
                Text(message.text)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .padding(8)
            }
        }
        .padding(4)
        .background(Color.primary.opacity(0.06), in: RoundedRectangle(cornerRadius: 16))
    }

    private func decodeBase64(_ base64String: String) -> UIImage? {
        var clean = base64String
        if clean.contains(",") {
            clean = clean.components(separatedBy: ",").last ?? clean
        }
        guard let data = Data(base64Encoded: clean) else { return nil }
        return UIImage(data: data)
    }
}

/// Вспомогательная структура для полноэкранного фото
private struct FullScreenImage: Identifiable {
    let id = UUID()
    let image: UIImage
}

/// Шторка выбора подхода для отправки в чат
private struct WorkoutShareSheet: View {
    let profile: ProgramProfile
    let onShare: (WorkoutSharePayload) -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var exerciseName: String = "Жим лёжа"
    @State private var weight: String = "100 кг"
    @State private var sets: String = "5"
    @State private var reps: String = "5"
    @State private var isPR: Bool = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Что поднял, бро?") {
                    TextField("Упражнение", text: $exerciseName)
                    TextField("Рабочий вес (напр. 120 кг)", text: $weight)
                    HStack {
                        TextField("Подходы", text: $sets)
                        Text("×")
                        TextField("Повторения", text: $reps)
                    }
                    Toggle("🔥 Личный рекорд (PR)?", isOn: $isPR)
                }

                Section {
                    Button(action: {
                        let curWeek = profile.currentWeek
                        let activeDay = profile.workoutPlan.weeks
                            .first(where: { $0.number == curWeek })?
                            .days.first(where: { !profile.isCompleted(week: curWeek, day: $0.number) })?
                            .number ?? 1
                        let payload = WorkoutSharePayload(
                            exercise: exerciseName.isEmpty ? "Упражнение" : exerciseName,
                            weight: weight.isEmpty ? "100 кг" : weight,
                            sets: sets.isEmpty ? "1" : sets,
                            reps: reps.isEmpty ? "5" : reps,
                            isPR: isPR,
                            week: curWeek,
                            day: activeDay
                        )
                        onShare(payload)
                        dismiss()
                    }) {
                        HStack {
                            Spacer()
                            Text("Закинуть в чат 💥")
                                .font(.headline)
                                .foregroundStyle(.white)
                            Spacer()
                        }
                    }
                    .listRowBackground(Theme.accentGradient)
                }
            }
            .navigationTitle("Поделиться подходом")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Отмена") { dismiss() }
                }
            }
            .onAppear {
                // Предзаполняем данными активной программы
                if let firstEx = profile.workoutPlan.weeks.first(where: { $0.number == profile.currentWeek })?.days.first?.exercises.first {
                    exerciseName = firstEx.name
                    weight = firstEx.load.displayText
                    sets = "\(firstEx.sets)"
                    reps = firstEx.reps
                }
            }
        }
        .presentationDetents([.medium])
    }
}
