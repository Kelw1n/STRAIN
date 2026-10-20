import PhotosUI
import SwiftUI
import UIKit

// MARK: - Список переписок

/// Переписки во вкладке «Друзья»: с кем, последнее сообщение и непрочитанные.
struct ChatListView: View {
    var onOpen: (String) -> Void

    @State private var chats: [SocialChat] = []
    @State private var loading = true
    @State private var error: String?

    private var social: SocialService { .shared }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let error, chats.isEmpty {
                SocialErrorCard(message: error) { Task { await reload() } }
            } else if chats.isEmpty && !loading {
                VStack(spacing: 8) {
                    Image(systemName: "bubble.left.and.bubble.right")
                        .font(.system(size: 30, weight: .semibold))
                        .foregroundStyle(Color.white.opacity(0.35))
                    Text("Сообщений пока нет")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.white)
                    Text("Открой профиль друга и нажми «Написать».")
                        .font(.system(size: 14))
                        .foregroundStyle(Color.white.opacity(0.55))
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 30)
            }

            if !chats.isEmpty {
                VStack(spacing: 0) {
                    ForEach(chats) { chat in
                        Button { onOpen(chat.user.username) } label: { ChatRow(chat: chat) }
                            .buttonStyle(PressableStyle(scale: 0.985))
                        if chat.id != chats.last?.id {
                            Divider().overlay(Color.white.opacity(0.06)).padding(.leading, 74)
                        }
                    }
                }
                .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            }

            if loading && chats.isEmpty {
                ProgressView().tint(.white).frame(maxWidth: .infinity).padding(20)
            }
        }
        .task {
            await reload()
            // Пока список открыт, новые сообщения подтягиваются сами.
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 10_000_000_000)
                guard !Task.isCancelled else { return }
                await reload()
            }
        }
    }

    private func reload() async {
        loading = true
        defer { loading = false }
        do {
            chats = try await social.chats().chats
            error = nil
        } catch {
            if let message = error.socialMessage { self.error = message }
        }
    }
}

struct ChatRow: View {
    let chat: SocialChat

    var body: some View {
        HStack(spacing: 12) {
            AvatarView(imageId: chat.user.avatar, username: chat.user.username, size: 48)
                .overlay(alignment: .bottomTrailing) {
                    // Друг сейчас тренируется — зелёная точка, как «в сети».
                    if chat.live != nil {
                        Circle()
                            .fill(Theme.success)
                            .frame(width: 13, height: 13)
                            .overlay(Circle().strokeBorder(Theme.surface(.dark), lineWidth: 2.5))
                    }
                }
            VStack(alignment: .leading, spacing: 3) {
                HStack {
                    Text("@\(chat.user.username)")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(.white)
                    Spacer()
                    Text(SocialFormat.ago(chat.last.date))
                        .font(.system(size: 12))
                        .foregroundStyle(Color.white.opacity(0.4))
                }
                HStack {
                    if let live = chat.live, chat.unread == 0 {
                        Text("Тренируется · \(live.line)")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Theme.success)
                            .lineLimit(1)
                    } else {
                        Text((chat.last.fromMe ? "Вы: " : "") + chat.last.preview)
                            .font(.system(size: 14, weight: chat.unread > 0 ? .semibold : .regular))
                            .foregroundStyle(Color.white.opacity(chat.unread > 0 ? 0.9 : 0.5))
                            .lineLimit(1)
                    }
                    Spacer()
                    if chat.unread > 0 {
                        Text("\(chat.unread)")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 7)
                            .frame(minWidth: 22, minHeight: 22)
                            .background(Theme.accent, in: Capsule())
                    }
                }
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 11)
        .contentShape(Rectangle())
    }
}

// MARK: - Переписка

/// Переписка с одним человеком. Новое — сообщения, реакции, прочтение,
/// правки и удаление — подтягивается каждые несколько секунд, пока экран
/// открыт; панель вкладок на это время прячется. Своё сообщение сначала
/// ложится в очередь на телефоне: без связи оно ждёт с часиками и уходит само.
struct ChatView: View {
    let username: String
    @Binding var path: NavigationPath

    @State private var user: SocialUserShort?
    @State private var messages: [SocialMessage] = []
    @State private var older: Double?
    @State private var loading = true
    @State private var loadingOlder = false
    @State private var draft = ""
    @State private var error: String?
    /// Фото, выбранное к отправке, — показывается над полем ввода.
    @State private var attachment: UIImage?
    @State private var showingGallery = false
    @State private var showingCamera = false
    @State private var galleryItem: PhotosPickerItem?
    /// Фото ещё не ушедших сообщений: пока идёт загрузка, видно их, а не заглушку.
    @State private var localImages: [String: UIImage] = [:]
    @State private var viewing: ViewedImage?
    /// На какое сообщение отвечаем — цитата над полем ввода.
    @State private var replyingTo: SocialMessage?
    /// Какое своё сообщение правим — его текст в поле ввода.
    @State private var editing: SocialMessage?
    /// Какое сообщение пересылаем — открыт выбор, кому.
    @State private var forwarding: SocialMessage?
    /// Не ушедшее сообщение, по которому нажали: повторить или удалить.
    @State private var failed: SocialMessage?
    @State private var openedWorkout: OpenedWorkout?
    /// Сообщение, к которому прокрутили по цитате, — коротко подсвечено.
    @State private var highlighted: String?
    /// С этого времени сервера спрашиваем изменения.
    @State private var serverTime: Double?
    @State private var otherTyping = false
    @State private var lastSeen: Double?
    @State private var live: SocialLive?
    @FocusState private var typing: Bool
    /// Голосовое: запись, сдвиг пальца влево (отмена) и подсказка над полем.
    @State private var recorder = VoiceRecorder()
    @State private var micHeld = false
    @State private var micCancelled = false
    @State private var micDrag: CGFloat = 0
    @State private var hint: String?

    private var social: SocialService { .shared }
    private static let cancelDistance: CGFloat = 110

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 4) {
                    if older != nil {
                        Button {
                            Task { await loadOlder() }
                        } label: {
                            if loadingOlder {
                                ProgressView().tint(.white)
                            } else {
                                Text("Ранние сообщения")
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundStyle(Theme.accent)
                            }
                        }
                        .padding(.vertical, 8)
                    }
                    if loading && messages.isEmpty {
                        ProgressView().tint(.white).padding(30)
                    } else if messages.isEmpty {
                        Text("Напиши первым — например, позови на тренировку.")
                            .font(.system(size: 14))
                            .foregroundStyle(Color.white.opacity(0.5))
                            .multilineTextAlignment(.center)
                            .padding(30)
                    }
                    // Ключ строки, а не id: своё сообщение после ответа сервера
                    // остаётся той же строкой и не пересоздаётся.
                    ForEach(Array(messages.enumerated()), id: \.element.key) { index, message in
                        if index == 0 || !Calendar.current.isDate(messages[index - 1].date, inSameDayAs: message.date) {
                            Text(dayTitle(message.date))
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(Color.white.opacity(0.4))
                                .padding(.top, 12)
                                .padding(.bottom, 4)
                        }
                        if let invite = message.invite {
                            InviteBubble(message: message, invite: invite, username: username) { updated in
                                if let index = messages.firstIndex(where: { $0.id == message.id }) { messages[index].invite = updated }
                            }
                            .id(message.key)
                            .transition(message.fromMe ? .ownMessage : .incomingMessage)
                        } else {
                        MessageBubble(
                            message: message,
                            username: username,
                            localImage: localImages[message.key],
                            highlighted: highlighted == message.id,
                            failure: message.clientId.flatMap { message.isLocal ? social.pendingFailures[$0] : nil },
                            onOpenImage: { viewing = ViewedImage(id: $0) },
                            onReply: { reply(to: message) },
                            onReact: { react(message, $0) },
                            onOpenQuote: { id in
                                guard let target = messages.first(where: { $0.id == id }) else { return }
                                withAnimation(Motion.snappy) { proxy.scrollTo(target.key, anchor: .center) }
                                flash(id)
                            },
                            onEdit: { startEditing(message) },
                            onForward: { forwarding = message },
                            onOpenWorkout: { workout in
                                openedWorkout = OpenedWorkout(
                                    workout: workout,
                                    author: message.forwarded?.username ?? (message.fromMe ? nil : username)
                                )
                            },
                            onListen: { listened(message) },
                            onFailedTap: { failed = message },
                            onDelete: { delete(message) }
                        )
                        .id(message.key)
                        .transition(message.fromMe ? .ownMessage : .incomingMessage)
                        }
                    }
                    if otherTyping {
                        TypingBubble()
                            .id("typing")
                            .transition(.scale(scale: 0.6, anchor: .bottomLeading).combined(with: .opacity))
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .animation(Motion.snappy, value: otherTyping)
            }
            .scrollDismissesKeyboard(.interactively)
            .defaultScrollAnchor(.bottom)
            // Одна прокрутка на новое сообщение и той же пружиной, что и сам пузырь.
            // Подтверждение сервера ключ не меняет — второй прокрутки нет.
            .onChange(of: messages.last?.key) { _, key in
                guard let key else { return }
                withAnimation(Motion.chat) { proxy.scrollTo(key, anchor: .bottom) }
            }
            .onChange(of: otherTyping) { _, value in
                if value { withAnimation(Motion.snappy) { proxy.scrollTo("typing", anchor: .bottom) } }
            }
        }
        // Панель ввода плавает поверх переписки: сообщения видны сквозь стекло.
        .safeAreaInset(edge: .bottom) { inputBar }
        .background(Color.black.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                Button { path.append(FeedRoute.user(username)) } label: {
                    HStack(spacing: 8) {
                        AvatarView(imageId: user?.avatar, username: username, size: 30)
                            .overlay(alignment: .bottomTrailing) {
                                if live != nil || isOnline {
                                    Circle()
                                        .fill(Theme.success)
                                        .frame(width: 9, height: 9)
                                        .overlay(Circle().strokeBorder(Color.black, lineWidth: 1.5))
                                }
                            }
                        VStack(alignment: .leading, spacing: 0) {
                            Text("@\(username)")
                                .font(.system(size: 15, weight: .bold))
                                .foregroundStyle(.white)
                            if let status {
                                Text(status.text)
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundStyle(status.color)
                                    .lineLimit(1)
                                    .contentTransition(.opacity)
                            }
                        }
                    }
                    .animation(Motion.snappy, value: status?.text)
                }
                .buttonStyle(.pressable)
            }
        }
        .preferredColorScheme(.dark)
        .photosPicker(isPresented: $showingGallery, selection: $galleryItem, matching: .images)
        .onChange(of: galleryItem) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self), let image = UIImage(data: data) {
                    attachment = image.downscaled()
                }
                galleryItem = nil
            }
        }
        .onChange(of: draft) { _, value in
            if editing == nil && !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { social.sendTyping(to: username) }
        }
        .cameraSheet(isPresented: $showingCamera) { attachment = $0.downscaled() }
        .fullScreenCover(item: $viewing) { item in
            ImageViewer(id: item.id) { viewing = nil }
        }
        .sheet(item: $forwarding) { message in
            ChatTargetSheet(title: "Переслать") { target in forward(message, to: target) }
        }
        .sheet(item: $openedWorkout) { opened in
            ChatWorkoutDetail(workout: opened.workout, author: opened.author)
        }
        .confirmationDialog(
            failed.flatMap { $0.clientId }.flatMap { social.pendingFailures[$0] } ?? "Сообщение не отправлено",
            isPresented: Binding(get: { failed != nil }, set: { if !$0 { failed = nil } }),
            titleVisibility: .visible,
            presenting: failed
        ) { message in
            Button("Повторить") {
                if let clientId = message.clientId { social.retryPending(clientId) }
            }
            Button("Удалить", role: .destructive) { delete(message) }
            Button("Отмена", role: .cancel) {}
        }
        .onAppear {
            AppChrome.shared.hidesTabBar = true
            // Дошли до минуты — голосовое уходит само.
            recorder.onLimit = { finishRecording() }
            // Ушло из очереди — пузырь с часиками становится обычным на том же месте.
            social.onMessageSent(in: username) { clientId, sent in confirm(clientId: clientId, with: sent) }
            // Доиграло голосовое — следующее включается само.
            VoicePlayer.shared.onFinish = { id in playNext(after: id) }
            restorePending()
        }
        .onDisappear {
            AppChrome.shared.hidesTabBar = false
            social.onMessageSent(in: username, nil)
            VoicePlayer.shared.onFinish = nil
            recorder.cancel()
            VoicePlayer.shared.stop()
        }
        .task {
            await loadLatest()
            await social.refreshUnread()
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 3_000_000_000)
                guard !Task.isCancelled else { return }
                // Связь вернулась — то, что ждало в очереди, уходит.
                if !social.pending(for: username).isEmpty { social.flushMessages() }
                await loadChanges()
            }
        }
    }

    /// В сети — если заходил последние две минуты.
    private var isOnline: Bool {
        guard let lastSeen else { return false }
        return Date().timeIntervalSince1970 * 1000 - lastSeen < 120_000
    }

    /// Строка под именем: печатает, тренируется или когда был в сети.
    private var status: (text: String, color: Color)? {
        if otherTyping { return ("печатает…", Theme.accent) }
        if let live { return ("тренируется · \(live.line)", Theme.success) }
        if let lastSeen {
            return (SocialFormat.lastSeen(Date(timeIntervalSince1970: lastSeen / 1000)), Color.white.opacity(isOnline ? 0.75 : 0.45))
        }
        return nil
    }

    /// Сообщения этой переписки ждут связи.
    private var waitingForNetwork: Bool {
        social.chatOffline && social.pending(for: username).contains { social.pendingFailures[$0.clientId] == nil }
    }

    /// Поле ввода как в Telegram на iOS 26: стеклянные капсулы поверх переписки,
    /// слева — прикрепить фото, справа — отправить.
    private var inputBar: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let error {
                Text(error)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Theme.warning)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .chatGlass(Capsule())
            } else if waitingForNetwork {
                Label("Нет связи — сообщения уйдут сами, когда она появится", systemImage: "wifi.slash")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Color.white.opacity(0.8))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .chatGlass(Capsule())
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
            if let hint {
                Text(hint)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .chatGlass(Capsule())
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
            if let editing {
                composerBanner(
                    icon: "pencil",
                    title: "Изменить сообщение",
                    text: editing.text.isEmpty ? "📷 Фото" : editing.text,
                    close: "Не изменять"
                ) { cancelEditing() }
            } else if let replyingTo {
                composerBanner(
                    icon: "arrowshape.turn.up.left.fill",
                    title: replyingTo.fromMe ? "Ответ себе" : "Ответ @\(username)",
                    text: replyingTo.preview,
                    close: "Не отвечать"
                ) { self.replyingTo = nil }
            }
            if let attachment {
                ZStack(alignment: .topTrailing) {
                    Image(uiImage: attachment)
                        .resizable()
                        .scaledToFill()
                        .frame(width: 76, height: 76)
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    Button { withAnimation(Motion.snappy) { self.attachment = nil } } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 20))
                            .foregroundStyle(.white, .black.opacity(0.6))
                    }
                    .offset(x: 6, y: -6)
                    .accessibilityLabel("Убрать фото")
                }
                .padding(6)
                .chatGlass(RoundedRectangle(cornerRadius: 20, style: .continuous))
                .transition(.scale(scale: 0.8).combined(with: .opacity))
            }
            HStack(alignment: .bottom, spacing: 8) {
                if recorder.isRecording {
                    VoiceRecordingStrip(recorder: recorder, drag: micDrag, cancelDistance: Self.cancelDistance)
                        .transition(.move(edge: .leading).combined(with: .opacity))
                } else {
                Menu {
                    Button { showingCamera = true } label: { Label("Снять фото", systemImage: "camera") }
                        .disabled(!CameraPicker.isAvailable)
                    // Не PhotosPicker прямо в меню: оттуда он на iOS не открывается.
                    Button { showingGallery = true } label: { Label("Из галереи", systemImage: "photo.on.rectangle") }
                } label: {
                    Image(systemName: "paperclip")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 44, height: 44)
                        .chatGlass(Circle())
                }
                .disabled(editing != nil)
                .opacity(editing == nil ? 1 : 0.4)
                .accessibilityLabel("Прикрепить фото")

                TextField(editing == nil ? "Сообщение" : "Новый текст", text: $draft, axis: .vertical)
                    .lineLimit(1...5)
                    .focused($typing)
                    .font(.system(size: 16))
                    .padding(.horizontal, 16)
                    .padding(.vertical, 11)
                    .frame(minHeight: 44)
                    .chatGlass(RoundedRectangle(cornerRadius: 22, style: .continuous))
                }

                if showsMic {
                    micButton
                        .transition(.scale(scale: 0.6).combined(with: .opacity))
                } else {
                Button(action: send) {
                    Image(systemName: editing == nil ? "arrow.up" : "checkmark")
                        .font(.system(size: 17, weight: .heavy))
                        .contentTransition(.symbolEffect(.replace))
                        .foregroundStyle(canSend ? Color.black : Color.white.opacity(0.5))
                        .frame(width: 44, height: 44)
                        .background(canSend ? Color.white : Color.clear, in: Circle())
                        .chatGlass(Circle(), enabled: !canSend)
                }
                .buttonStyle(PressableStyle(scale: 0.88))
                .disabled(!canSend)
                .accessibilityLabel(editing == nil ? "Отправить" : "Сохранить")
                .animation(Motion.snappy, value: canSend)
                .transition(.scale(scale: 0.6).combined(with: .opacity))
                }
            }
        }
        .padding(.horizontal, 10)
        .padding(.top, 6)
        .padding(.bottom, 6)
        .animation(Motion.snappy, value: attachment != nil)
        .animation(Motion.snappy, value: replyingTo?.id)
        .animation(Motion.snappy, value: editing?.id)
        .animation(Motion.snappy, value: showsMic)
        .animation(Motion.snappy, value: recorder.isRecording)
        .animation(Motion.snappy, value: hint)
        .animation(Motion.snappy, value: waitingForNetwork)
    }

    /// Плашка над полем ввода: на что отвечаем или что правим.
    private func composerBanner(icon: String, title: String, text: String, close: String, onClose: @escaping () -> Void) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Theme.accent)
            VStack(alignment: .leading, spacing: 1) {
                Text(title)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Theme.accent)
                Text(text)
                    .font(.system(size: 13))
                    .foregroundStyle(Color.white.opacity(0.7))
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
            Button { withAnimation(Motion.snappy) { onClose() } } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Color.white.opacity(0.6))
                    .frame(width: 28, height: 28)
            }
            .accessibilityLabel(close)
        }
        .padding(.leading, 14)
        .padding(.trailing, 6)
        .padding(.vertical, 6)
        .chatGlass(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }

    private var trimmedDraft: String { draft.trimmingCharacters(in: .whitespacesAndNewlines) }

    private var canSend: Bool {
        if let editing { return !trimmedDraft.isEmpty || editing.image != nil }
        return attachment != nil || !trimmedDraft.isEmpty
    }

    /// Поле пустое и фото нет — вместо «Отправить» микрофон, как в Telegram.
    private var showsMic: Bool {
        editing == nil && (recorder.isRecording || (attachment == nil && trimmedDraft.isEmpty))
    }

    // MARK: Голосовое

    /// Зажал — пишет, отпустил — ушло, увёл влево — отменено. Пока пишет,
    /// кнопка крупнее и едет за пальцем.
    private var micButton: some View {
        Image(systemName: "mic.fill")
            .font(.system(size: 18, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: 44, height: 44)
            .background(recorder.isRecording ? Theme.record : Color.clear, in: Circle())
            .chatGlass(Circle(), enabled: !recorder.isRecording)
            .scaleEffect(recorder.isRecording ? 1.3 : 1)
            .offset(x: recorder.isRecording ? micDrag : 0)
            .animation(Motion.bouncy, value: recorder.isRecording)
            .contentShape(Circle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        if !micHeld {
                            micHeld = true
                            micCancelled = false
                            beginRecording()
                        }
                        guard recorder.isRecording, !micCancelled else { return }
                        micDrag = min(0, value.translation.width)
                        if micDrag < -Self.cancelDistance {
                            micCancelled = true
                            recorder.cancel()
                            UINotificationFeedbackGenerator().notificationOccurred(.warning)
                            withAnimation(Motion.snappy) { micDrag = 0 }
                        }
                    }
                    .onEnded { _ in
                        micHeld = false
                        withAnimation(Motion.snappy) { micDrag = 0 }
                        if micCancelled {
                            micCancelled = false
                            return
                        }
                        finishRecording()
                    }
            )
            .accessibilityLabel("Голосовое: удерживай и говори")
    }

    private func beginRecording() {
        switch recorder.permission {
        case .granted:
            if recorder.start() {
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                error = nil
                hint = nil
            } else {
                error = "Не удалось включить микрофон"
            }
        case .undetermined:
            Task {
                if await recorder.requestPermission() { showHint("Готово — теперь зажми микрофон и говори") }
            }
        case .denied:
            error = "Микрофон выключен: Настройки → STRAIN → Микрофон"
        }
    }

    private func finishRecording() {
        guard recorder.isRecording else { return }
        if let recording = recorder.stop() {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            sendVoice(recording)
        } else {
            showHint("Удерживай микрофон, чтобы записать голосовое")
        }
    }

    private func showHint(_ text: String) {
        withAnimation(Motion.snappy) { hint = text }
        Task {
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            if hint == text { withAnimation(Motion.snappy) { hint = nil } }
        }
    }

    /// Голосовое сразу в переписке, звук — в очередь вместе с сообщением.
    /// В тексте — подпись для старых версий приложения, которые голосовые не играют.
    private func sendVoice(_ recording: VoiceRecording) {
        let clientId = UUID().uuidString.lowercased()
        let item = ChatPending(
            clientId: clientId,
            username: username,
            text: SocialVoice.previewText,
            replyTo: replyingTo.map { SocialMessageQuote($0) },
            voice: SocialVoice(id: "local-" + clientId, durationMs: recording.durationMs, waveform: recording.waveform)
        )
        withAnimation(Motion.chat) {
            replyingTo = nil
            messages.append(item.message)
        }
        social.queue(item, voice: recording.data)
        error = nil
    }

    // MARK: Голосовые: прослушано и следующее

    /// Чужое голосовое включили — точка гаснет у обоих.
    private func listened(_ message: SocialMessage) {
        guard !message.fromMe, !message.heard, !message.isLocal,
              let index = messages.firstIndex(where: { $0.id == message.id }) else { return }
        messages[index].heard = true
        social.markHeard(message.id)
    }

    /// Следующее голосовое ниже по переписке включается само, как в Telegram.
    private func playNext(after voiceId: String) {
        guard let index = messages.firstIndex(where: { $0.voice?.id == voiceId }),
              let next = messages[(index + 1)...].first(where: { $0.voice != nil && !$0.isLocal }),
              let voice = next.voice else { return }
        listened(next)
        VoicePlayer.shared.play(voice)
    }

    private func dayTitle(_ date: Date) -> String {
        let calendar = Calendar.current
        if calendar.isDateInToday(date) { return "Сегодня" }
        if calendar.isDateInYesterday(date) { return "Вчера" }
        return RuDate.shortDayMonth(date, calendar: calendar)
    }

    /// Новое добавляется, изменённое заменяется, удалённое пропадает.
    /// Сервер отдаёт новые первыми, на экране — старые сверху.
    private func apply(_ incoming: [SocialMessage], animated: Bool = true) {
        let updated = SocialMessage.merged(messages, with: incoming)
        guard updated != messages else { return }
        if animated {
            withAnimation(Motion.chat) { messages = updated }
        } else {
            var quiet = Transaction()
            quiet.disablesAnimations = true
            withTransaction(quiet) { messages = updated }
        }
    }

    /// Очередь отправила сообщение: та же строка, только часики сменились
    /// галочкой. Без анимации — пузырь не пересоздаётся и переписка не дёргается.
    private func confirm(clientId: String, with sent: SocialMessage) {
        var confirmed = sent
        confirmed.rowKey = "local-" + clientId
        let updated = SocialMessage.merged(messages.filter { $0.id != "local-" + clientId }, with: [confirmed])
        var quiet = Transaction()
        quiet.disablesAnimations = true
        withTransaction(quiet) { messages = updated }
    }

    /// Сообщения, которые ждали отправки, когда переписку закрыли, — снова на месте.
    private func restorePending() {
        let known = Set(messages.map(\.key))
        let waiting = social.pending(for: username).filter { !known.contains($0.message.key) }
        guard !waiting.isEmpty else { return }
        for item in waiting where item.hasPhotoFile {
            if let data = ChatOutboxStore.photo(item.clientId), let image = UIImage(data: data) {
                localImages[item.message.key] = image
            }
        }
        apply(waiting.map(\.message), animated: false)
    }

    private func absorb(_ page: SocialChatPage) {
        user = page.user
        if let time = page.serverTime { serverTime = time }
        otherTyping = page.typing ?? false
        lastSeen = page.lastSeen ?? lastSeen
        live = page.live
    }

    private func loadLatest() async {
        loading = true
        defer { loading = false }
        do {
            let page = try await social.messages(with: username)
            absorb(page)
            older = page.next
            // Первая загрузка — сразу на месте, без вылетающих пузырей.
            apply(page.messages, animated: !messages.contains { !$0.isLocal })
            error = nil
        } catch {
            if let message = error.socialMessage { self.error = message }
        }
    }

    /// Всё, что поменялось с прошлого раза: новые сообщения, реакции, прочтение, правки, удаление.
    private func loadChanges() async {
        guard let since = serverTime else {
            await loadLatest()
            return
        }
        guard let page = try? await social.messages(with: username, since: since) else { return }
        absorb(page)
        if error == SocialError.offline.message { error = nil }
        let known = Set(messages.map(\.id))
        let hadIncoming = page.messages.contains { !$0.fromMe && !$0.deleted && !known.contains($0.id) }
        apply(page.messages)
        if hadIncoming { await social.refreshUnread() }
    }

    private func loadOlder() async {
        guard let older, !loadingOlder else { return }
        loadingOlder = true
        defer { loadingOlder = false }
        if let page = try? await social.messages(with: username, before: older) {
            self.older = page.next
            apply(page.messages, animated: false)
        }
    }

    private func reply(to message: SocialMessage) {
        guard !message.isLocal else { return }
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        withAnimation(Motion.snappy) {
            editing = nil
            replyingTo = message
        }
        typing = true
    }

    private func flash(_ id: String) {
        withAnimation(.easeOut(duration: 0.2)) { highlighted = id }
        Task {
            try? await Task.sleep(nanoseconds: 1_200_000_000)
            withAnimation(.easeOut(duration: 0.5)) { if highlighted == id { highlighted = nil } }
        }
    }

    /// Реакция видна сразу; ответ сервера её подтверждает или откатывает.
    private func react(_ message: SocialMessage, _ emoji: String) {
        guard !message.isLocal, let index = messages.firstIndex(where: { $0.id == message.id }) else { return }
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        let before = messages[index]
        withAnimation(Motion.bouncy) { messages[index].reactions = SocialReaction.toggled(before.reactions, emoji) }
        Task {
            do {
                apply([try await social.react(message.id, emoji: emoji)])
            } catch {
                apply([before])
                self.error = error.socialMessage
            }
        }
    }

    /// Сообщение сразу на экране и в очереди на телефоне; фото сначала
    /// загружается на сервер. Нет связи — пузырь ждёт с часиками и уходит сам.
    private func send() {
        if editing != nil {
            saveEdit()
            return
        }
        let text = trimmedDraft
        let photo = attachment
        guard !text.isEmpty || photo != nil else { return }
        let item = ChatPending(
            username: username,
            text: text,
            replyTo: replyingTo.map { SocialMessageQuote($0) },
            image: photo == nil ? nil : "local"
        )
        let local = item.message
        if let photo { localImages[local.key] = photo }
        // Поле пустеет, пузырь вылетает, переписка едет вверх — одной пружиной,
        // а не тремя анимациями наперегонки.
        withAnimation(Motion.chat) {
            draft = ""
            attachment = nil
            replyingTo = nil
            messages.append(local)
        }
        social.queue(item, photo: photo.flatMap { SocialService.jpeg($0, maxSide: 1600) })
        error = nil
    }

    // MARK: Правка и пересылка

    private func startEditing(_ message: SocialMessage) {
        guard message.canEdit else { return }
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        withAnimation(Motion.snappy) {
            replyingTo = nil
            attachment = nil
            editing = message
            draft = message.text
        }
        typing = true
    }

    private func cancelEditing() {
        editing = nil
        draft = ""
    }

    /// Новый текст виден сразу; не вышло — возвращается прежний.
    private func saveEdit() {
        guard let original = editing, let index = messages.firstIndex(where: { $0.id == original.id }) else {
            cancelEditing()
            return
        }
        let text = trimmedDraft
        guard text != original.text else {
            withAnimation(Motion.snappy) { cancelEditing() }
            return
        }
        var optimistic = messages[index]
        optimistic.text = text
        optimistic.edited = true
        withAnimation(Motion.chat) {
            messages[index] = optimistic
            cancelEditing()
        }
        Task {
            do {
                apply([try await social.editMessage(original.id, text: text)], animated: false)
                error = nil
            } catch {
                apply([original])
                self.error = error.socialMessage
            }
        }
    }

    /// Пересланное уходит той же очередью; переслали сюда же — появляется и здесь.
    private func forward(_ message: SocialMessage, to target: SocialUserShort) {
        let item = ChatPending(
            username: target.username,
            text: message.text,
            image: message.image,
            voice: message.voice,
            workout: message.workout,
            forward: message.id,
            forwarded: message.forwarded ?? SocialForward(username: message.fromMe ? social.me?.username : username)
        )
        social.queue(item)
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        if target.username == username {
            withAnimation(Motion.chat) { messages.append(item.message) }
        } else {
            showHint("Переслано @\(target.username)")
        }
    }

    private func delete(_ message: SocialMessage) {
        // Не ушедшее — просто убираем из очереди.
        if message.isLocal {
            if let clientId = message.clientId { social.discardPending(clientId) }
            withAnimation(Motion.chat) { messages.removeAll { $0.id == message.id } }
            localImages[message.key] = nil
            return
        }
        Task {
            do {
                try await social.deleteMessage(message.id)
                UIImpactFeedbackGenerator(style: .rigid).impactOccurred()
                withAnimation(Motion.chat) { messages.removeAll { $0.id == message.id } }
            } catch {
                self.error = error.socialMessage
            }
        }
    }
}

/// Тренировка из переписки, открытая целиком.
struct OpenedWorkout: Identifiable {
    let id = UUID()
    let workout: SocialChatWorkout
    /// Чья это тренировка — для «Скопировать к себе»; `nil` — своя.
    let author: String?
}

/// Три точки собеседника, пока он печатает. Анимацию ведёт сама система —
/// с частотой экрана, без пересчёта вью на каждый кадр.
struct TypingBubble: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var bouncing = false

    var body: some View {
        HStack {
            HStack(spacing: 4) {
                ForEach(0..<3, id: \.self) { index in
                    Circle()
                        .fill(Color.white.opacity(0.7))
                        .frame(width: 7, height: 7)
                        .scaleEffect(bouncing ? 1 : 0.7)
                        .opacity(bouncing ? 1 : 0.6)
                        .animation(
                            reduceMotion ? nil : .easeInOut(duration: 0.42).repeatForever(autoreverses: true).delay(Double(index) * 0.14),
                            value: bouncing
                        )
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(Color(white: 0.16), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .accessibilityLabel("Печатает")
            Spacer(minLength: 60)
        }
        .onAppear { bouncing = true }
    }
}

/// Фото, открытое на весь экран.
struct ViewedImage: Identifiable, Hashable {
    let id: String
}

struct MessageBubble: View {
    let message: SocialMessage
    /// Собеседник — подпись над цитатой его сообщения.
    var username: String = ""
    var localImage: UIImage? = nil
    var highlighted = false
    /// Почему своё сообщение не ушло: сервер отказал.
    var failure: String? = nil
    var onOpenImage: (String) -> Void = { _ in }
    var onReply: () -> Void = {}
    var onReact: (String) -> Void = { _ in }
    var onOpenQuote: (String) -> Void = { _ in }
    var onEdit: () -> Void = {}
    var onForward: () -> Void = {}
    var onOpenWorkout: (SocialChatWorkout) -> Void = { _ in }
    var onListen: () -> Void = {}
    var onFailedTap: () -> Void = {}
    var onDelete: () -> Void

    /// Сдвиг при свайпе влево — «ответить», как в Telegram.
    @State private var swipe: CGFloat = 0
    /// Дотянули до порога: отпустишь — ответ.
    @State private var armed = false

    private static let replyDistance: CGFloat = 64

    var body: some View {
        HStack {
            if message.fromMe { Spacer(minLength: 60) }
            VStack(alignment: message.fromMe ? .trailing : .leading, spacing: 4) {
                bubble
                if !message.reactions.isEmpty {
                    reactions
                }
                if failure != nil {
                    Button(action: onFailedTap) {
                        Label("Не отправлено — нажми", systemImage: "exclamationmark.circle.fill")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(Theme.record)
                    }
                    .buttonStyle(.pressable)
                    .transition(.opacity)
                }
            }
            .offset(x: swipe)
            .background(alignment: .trailing) {
                // Стрелка «ответить» выезжает справа, пока тянем пузырь.
                Image(systemName: "arrowshape.turn.up.left.circle.fill")
                    .font(.system(size: 26))
                    .foregroundStyle(armed ? Theme.accent : Color.white.opacity(0.8))
                    .opacity(Double(min(1, -swipe / Self.replyDistance)))
                    .scaleEffect(armed ? 1.15 : 0.6 + 0.4 * min(1, -swipe / Self.replyDistance))
                    .animation(Motion.bouncy, value: armed)
                    .offset(x: swipe + 34)
            }
            if !message.fromMe { Spacer(minLength: 60) }
        }
        .contentShape(Rectangle())
        .replySwipe(onChange: pull, onEnd: release)
        .animation(Motion.snappy, value: failure)
    }

    /// Пузырь едет за пальцем; дальше порога — с сопротивлением и щелчком.
    /// Пока своё голосовое играет, палец по волне перематывает, а не отвечает.
    private func pull(_ dx: CGFloat) {
        guard !message.isLocal else { return }
        if let voice = message.voice, VoicePlayer.shared.playingId == voice.id { return }
        let pulled = min(0, dx)
        swipe = pulled < -Self.replyDistance ? -Self.replyDistance + (pulled + Self.replyDistance) * 0.25 : pulled
        let reached = swipe <= -Self.replyDistance
        if reached != armed {
            armed = reached
            if reached { UIImpactFeedbackGenerator(style: .medium).impactOccurred() }
        }
    }

    private func release() {
        if armed { onReply() }
        armed = false
        withAnimation(Motion.bouncy) { swipe = 0 }
    }

    private var bubble: some View {
        BubbleStack(spacing: 4) {
            if let forwarded = message.forwarded {
                Label(forwarded.title, systemImage: "arrowshape.turn.up.right.fill")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(message.fromMe ? Color.white.opacity(0.8) : Theme.accent)
                    .lineLimit(1)
                    .padding(.horizontal, message.image == nil ? 0 : 9)
                    .padding(.top, message.image == nil ? 0 : 5)
            }
            if let quote = message.replyTo {
                Button { onOpenQuote(quote.id) } label: { quoteView(quote) }
                    .buttonStyle(.pressable)
                    .padding(.horizontal, message.image == nil ? 0 : 5)
                    .padding(.top, message.image == nil ? 0 : 5)
            }
            if message.image != nil {
                photo
            }
            if let voice = message.voice {
                VoiceMessageContent(
                    voice: voice,
                    fromMe: message.fromMe,
                    isLocal: message.isLocal,
                    heard: message.heard,
                    onListen: onListen
                )
            } else if let workout = message.workout {
                ChatWorkoutCard(workout: workout, fromMe: message.fromMe) { onOpenWorkout(workout) }
            } else if !message.text.isEmpty {
                Text(message.text)
                    .font(.system(size: 16))
                    .foregroundStyle(message.fromMe ? Color.white : Color.white.opacity(0.92))
                    .textSelection(.enabled)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, message.image == nil ? 0 : 9)
            }
            HStack(spacing: 4) {
                if message.edited {
                    Text("изменено")
                }
                Text(message.date, format: .dateTime.hour().minute())
                if message.fromMe {
                    Image(systemName: statusIcon)
                        .foregroundStyle(failure != nil ? Theme.record : Color.white.opacity(0.55))
                        .contentTransition(.symbolEffect(.replace))
                }
            }
            .font(.system(size: 11))
            .foregroundStyle(Color.white.opacity(0.55))
            .frame(maxWidth: .infinity, alignment: .trailing)
            .padding(.horizontal, message.image == nil ? 0 : 9)
            .padding(.bottom, message.image == nil ? 0 : 4)
        }
        .padding(.horizontal, message.image == nil ? 13 : 4)
        .padding(.vertical, message.image == nil ? 8 : 4)
        .background(
            message.fromMe ? Theme.accent.opacity(failure == nil ? 0.9 : 0.5) : Color(white: 0.16),
            in: RoundedRectangle(cornerRadius: 18, style: .continuous)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Color.white.opacity(highlighted ? 0.18 : 0))
                .allowsHitTesting(false)
        )
        // Двойное нажатие — 🔥, как сердечко в Telegram.
        .onTapGesture(count: 2) { onReact("🔥") }
        .contextMenu {
            if !message.isLocal {
                ControlGroup {
                    ForEach(SocialReaction.choices, id: \.self) { emoji in
                        Button { onReact(emoji) } label: { Text(emoji) }
                    }
                }
                .controlGroupStyle(.palette)
                Button(action: onReply) { Label("Ответить", systemImage: "arrowshape.turn.up.left") }
            }
            if message.canForward {
                Button(action: onForward) { Label("Переслать", systemImage: "arrowshape.turn.up.right") }
            }
            if message.canEdit {
                Button(action: onEdit) { Label("Изменить", systemImage: "pencil") }
            }
            if !message.text.isEmpty && message.voice == nil && message.workout == nil {
                Button {
                    UIPasteboard.general.string = message.text
                } label: { Label("Скопировать", systemImage: "doc.on.doc") }
            }
            if failure != nil {
                Button(action: onFailedTap) { Label("Повторить", systemImage: "arrow.clockwise") }
            }
            if message.fromMe {
                Button(role: .destructive, action: onDelete) {
                    Label(message.isLocal ? "Не отправлять" : "Удалить", systemImage: "trash")
                }
            }
        }
    }

    /// Часики — в пути или ждёт связи, «!» — не ушло, галочка — доставлено, две — прочитано.
    private var statusIcon: String {
        if failure != nil { return "exclamationmark.circle.fill" }
        if message.isLocal { return "clock" }
        return message.read ? "checkmark.circle.fill" : "checkmark"
    }

    private func quoteView(_ quote: SocialMessageQuote) -> some View {
        HStack(spacing: 8) {
            RoundedRectangle(cornerRadius: 1.5)
                .fill(message.fromMe ? Color.white : Theme.accent)
                .frame(width: 3)
            if let image = quote.image {
                RemoteImage(id: image, maxPoints: 64)
                    .frame(width: 32, height: 32)
                    .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
            }
            VStack(alignment: .leading, spacing: 1) {
                Text(quote.fromMe ? "Вы" : "@\(username)")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(message.fromMe ? Color.white : Theme.accent)
                    .lineLimit(1)
                // Длинная цитата не раздувает пузырь — обрезается.
                Text(quote.preview)
                    .font(.system(size: 13))
                    .foregroundStyle(Color.white.opacity(0.75))
                    .lineLimit(1)
                    .frame(maxWidth: 190, alignment: .leading)
            }
        }
        .padding(.vertical, 5)
        .padding(.leading, 6)
        .padding(.trailing, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white.opacity(message.fromMe ? 0.16 : 0.07), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    private var reactions: some View {
        HStack(spacing: 5) {
            ForEach(message.reactions, id: \.emoji) { reaction in
                Button { onReact(reaction.emoji) } label: {
                    HStack(spacing: 3) {
                        Text(reaction.emoji).font(.system(size: 14))
                        if reaction.count > 1 {
                            Text("\(reaction.count)")
                                .font(.system(size: 12, weight: .bold).monospacedDigit())
                                .foregroundStyle(.white)
                        }
                    }
                    .padding(.horizontal, 8)
                    .frame(height: 26)
                    .background(reaction.mine ? Theme.accent.opacity(0.45) : Color(white: 0.16), in: Capsule())
                    .overlay(Capsule().strokeBorder(reaction.mine ? Theme.accent : Color.white.opacity(0.08), lineWidth: 1))
                }
                .buttonStyle(.pressable)
                .transition(.scale.combined(with: .opacity))
                .accessibilityLabel("\(reaction.emoji) \(reaction.count)")
            }
        }
    }

    @ViewBuilder
    private var photo: some View {
        Group {
            if let localImage {
                Image(uiImage: localImage).resizable().scaledToFill()
                    .overlay {
                        if message.isLocal && failure == nil { ProgressView().tint(.white) }
                    }
            } else {
                RemoteImage(id: message.image)
            }
        }
        .frame(width: 230, height: 270)
        .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
        .contentShape(Rectangle())
        .onTapGesture {
            if let id = message.image, id != "local", localImage == nil || !message.isLocal { onOpenImage(id) }
        }
    }
}

// MARK: - Появление и свайп

extension AnyTransition {
    /// Своё сообщение вылетает из поля ввода: снизу справа, с лёгким отскоком.
    static var ownMessage: AnyTransition {
        .asymmetric(
            insertion: .scale(scale: 0.45, anchor: .bottomTrailing)
                .combined(with: .offset(y: 26))
                .combined(with: .opacity),
            removal: .scale(scale: 0.6).combined(with: .opacity)
        )
    }

    /// Сообщение друга проявляется слева, от его стороны переписки.
    static var incomingMessage: AnyTransition {
        .asymmetric(
            insertion: .scale(scale: 0.6, anchor: .bottomLeading).combined(with: .opacity),
            removal: .scale(scale: 0.6).combined(with: .opacity)
        )
    }
}

extension View {
    /// Свайп влево по сообщению — «ответить». С iOS 18 — жест UIKit, который
    /// берёт только горизонтальное движение и не спорит с прокруткой переписки;
    /// на iOS 17 — обычный жест SwiftUI.
    @ViewBuilder
    func replySwipe(onChange: @escaping (CGFloat) -> Void, onEnd: @escaping () -> Void) -> some View {
        if #available(iOS 18.0, *) {
            gesture(HorizontalSwipeGesture(onChange: onChange, onEnd: onEnd))
        } else {
            simultaneousGesture(
                DragGesture(minimumDistance: 18)
                    .onChanged { value in
                        guard abs(value.translation.width) > abs(value.translation.height) * 1.6 else { return }
                        onChange(value.translation.width)
                    }
                    .onEnded { _ in onEnd() }
            )
        }
    }
}

/// Горизонтальный свайп влево: начинается, только если палец пошёл в сторону,
/// и работает вместе с прокруткой, а не вместо неё.
@available(iOS 18.0, *)
private struct HorizontalSwipeGesture: UIGestureRecognizerRepresentable {
    var onChange: (CGFloat) -> Void
    var onEnd: () -> Void

    func makeUIGestureRecognizer(context: Context) -> UIPanGestureRecognizer {
        let pan = UIPanGestureRecognizer()
        pan.delegate = context.coordinator
        return pan
    }

    func handleUIGestureRecognizerAction(_ recognizer: UIPanGestureRecognizer, context: Context) {
        switch recognizer.state {
        case .began, .changed:
            onChange(recognizer.translation(in: recognizer.view).x)
        case .ended, .cancelled, .failed:
            onEnd()
        default:
            break
        }
    }

    func makeCoordinator(converter: CoordinateSpaceConverter) -> Coordinator { Coordinator() }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
            guard let pan = gestureRecognizer as? UIPanGestureRecognizer else { return false }
            let velocity = pan.velocity(in: pan.view)
            return velocity.x < 0 && abs(velocity.x) > abs(velocity.y) * 1.2
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            true
        }
    }
}

// MARK: - Голосовые

/// Пузырь голосового: ▶/❚❚, волна (проигранное — ярче), время, скорость
/// и точка, пока его не прослушали. По волне можно перематывать пальцем.
/// Перерисовывается каждый кадр только пока играет — через `TimelineView`.
struct VoiceMessageContent: View {
    let voice: SocialVoice
    let fromMe: Bool
    var isLocal = false
    /// Получатель уже прослушал.
    var heard = true
    /// Включили — для «прослушано».
    var onListen: () -> Void = {}

    /// Куда тянут пальцем по волне, от 0 до 1.
    @State private var scrub: Double?

    private var player: VoicePlayer { .shared }
    private static let waveWidth: CGFloat = 150

    var body: some View {
        let playing = player.playingId == voice.id
        let loading = player.loadingId == voice.id
        let failed = player.failedId == voice.id
        HStack(spacing: 10) {
            Button {
                if !playing { onListen() }
                player.toggle(voice)
            } label: {
                ZStack {
                    Circle().fill(fromMe ? Color.white : Theme.accent)
                    if loading || isLocal {
                        ProgressView().tint(fromMe ? Theme.accent : .white)
                    } else {
                        Image(systemName: failed ? "arrow.clockwise" : (playing ? "pause.fill" : "play.fill"))
                            .font(.system(size: 15, weight: .heavy))
                            .foregroundStyle(fromMe ? Theme.accent : .white)
                            .offset(x: playing || failed ? 0 : 1.5)
                            .contentTransition(.symbolEffect(.replace))
                    }
                }
                .frame(width: 38, height: 38)
            }
            .buttonStyle(.pressable)
            .disabled(isLocal)
            .accessibilityLabel(playing ? "Пауза" : "Слушать голосовое, \(Int(voice.duration.rounded())) с")

            TimelineView(.animation(paused: !playing)) { _ in
                VStack(alignment: .leading, spacing: 3) {
                    VoiceWaveform(levels: voice.levels, progress: scrub ?? player.progress(of: voice.id), fromMe: fromMe)
                        .frame(width: Self.waveWidth, height: 24)
                        .contentShape(Rectangle())
                        // Не играет — нажатие включает с этого места; играет — палец
                        // по волне перематывает.
                        .onTapGesture(coordinateSpace: .local) { location in
                            guard !isLocal, !playing else { return }
                            onListen()
                            player.play(voice, from: Double(location.x / Self.waveWidth))
                        }
                        .gesture(
                            DragGesture(minimumDistance: 0)
                                .onChanged { value in scrub = fraction(value.location.x) }
                                .onEnded { value in
                                    player.seek(voice, to: fraction(value.location.x))
                                    scrub = nil
                                },
                            including: playing ? .all : .subviews
                        )
                    HStack(spacing: 6) {
                        Text(SocialVoice.format(timeShown(playing: playing)))
                            .font(.system(size: 12, weight: .medium).monospacedDigit())
                            .foregroundStyle(Color.white.opacity(0.7))
                        if !heard && !isLocal {
                            Circle()
                                .fill(fromMe ? Color.white : Theme.accent)
                                .frame(width: 6, height: 6)
                                .transition(.scale.combined(with: .opacity))
                                .accessibilityLabel("Не прослушано")
                        }
                        Spacer(minLength: 0)
                        if playing {
                            Button { player.cycleRate() } label: {
                                Text(rateText)
                                    .font(.system(size: 11, weight: .heavy).monospacedDigit())
                                    .foregroundStyle(fromMe ? Theme.accent : .white)
                                    .padding(.horizontal, 6)
                                    .frame(height: 18)
                                    .background(fromMe ? Color.white : Theme.accent.opacity(0.85), in: Capsule())
                                    .contentTransition(.numericText())
                            }
                            .buttonStyle(.pressable)
                            .accessibilityLabel("Скорость \(rateText)")
                            .transition(.scale.combined(with: .opacity))
                        }
                    }
                    .frame(width: Self.waveWidth)
                    .animation(Motion.snappy, value: playing)
                    .animation(Motion.snappy, value: heard)
                    .animation(Motion.snappy, value: player.rate)
                }
            }
        }
        .padding(.vertical, 2)
        .padding(.trailing, 4)
    }

    private var rateText: String {
        player.rate == 1 ? "1×" : (player.rate == 2 ? "2×" : "1,5×")
    }

    private func fraction(_ x: CGFloat) -> Double { Double(min(max(x / Self.waveWidth, 0), 1)) }

    private func timeShown(playing: Bool) -> TimeInterval {
        if let scrub { return scrub * voice.duration }
        return playing ? player.currentTime(of: voice.id) : voice.duration
    }
}

/// Столбики громкости. Проигранная часть — яркая, остальная — приглушённая.
struct VoiceWaveform: View {
    let levels: [Double]
    var progress: Double = 0
    var fromMe = true

    var body: some View {
        Canvas { context, size in
            guard !levels.isEmpty else { return }
            let gap: CGFloat = 2
            let width = max(1.5, (size.width - gap * CGFloat(levels.count - 1)) / CGFloat(levels.count))
            for (index, level) in levels.enumerated() {
                let height = max(3, CGFloat(level) * size.height)
                let x = CGFloat(index) * (width + gap)
                let rect = CGRect(x: x, y: (size.height - height) / 2, width: width, height: height)
                let played = Double(index) / Double(levels.count) < progress
                let color: Color = played ? (fromMe ? .white : Theme.accent) : Color.white.opacity(fromMe ? 0.45 : 0.35)
                context.fill(Path(roundedRect: rect, cornerRadius: width / 2), with: .color(color))
            }
        }
    }
}

/// Вместо поля ввода, пока идёт запись: красная точка, время, живая волна
/// и «влево — отмена», которая гаснет по мере того, как палец уходит влево.
/// Отдельная вью — 20 раз в секунду перерисовывается только она, а не переписка.
struct VoiceRecordingStrip: View {
    let recorder: VoiceRecorder
    let drag: CGFloat
    let cancelDistance: CGFloat

    var body: some View {
        let pulled = min(1, -drag / cancelDistance)
        HStack(spacing: 10) {
            Circle()
                .fill(Theme.record)
                .frame(width: 10, height: 10)
                .phaseAnimator([false, true]) { dot, dim in
                    dot.opacity(dim ? 0.3 : 1)
                } animation: { _ in Animation.easeInOut(duration: 0.6) }
            Text(SocialVoice.format(recorder.elapsed))
                .font(.system(size: 16, weight: .semibold).monospacedDigit())
                .foregroundStyle(.white)
            VoiceWaveform(levels: recorder.recentLevels, progress: 1, fromMe: true)
                .frame(height: 22)
                .frame(maxWidth: 90)
                .animation(.linear(duration: 0.05), value: recorder.recentLevels)
            Spacer(minLength: 4)
            HStack(spacing: 3) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 12, weight: .bold))
                Text("отмена")
                    .font(.system(size: 14, weight: .semibold))
            }
            .foregroundStyle(Color.white.opacity(0.6))
            .opacity(1 - pulled)
            .offset(x: drag * 0.4)
        }
        .padding(.horizontal, 16)
        .frame(height: 44)
        .chatGlass(Capsule())
    }
}

/// Столбец пузыря: ширина — по самой широкой строке, но не шире места на экране,
/// и все строки растягиваются до неё. Так время прижато вправо, цитата — во всю
/// ширину, а короткое сообщение остаётся коротким пузырём.
struct BubbleStack: Layout {
    var spacing: CGFloat = 4

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = contentWidth(proposal, subviews)
        let heights = subviews.map { $0.sizeThatFits(ProposedViewSize(width: width, height: nil)).height }
        return CGSize(width: width, height: heights.reduce(0, +) + spacing * CGFloat(max(0, subviews.count - 1)))
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for subview in subviews {
            let height = subview.sizeThatFits(ProposedViewSize(width: bounds.width, height: nil)).height
            subview.place(at: CGPoint(x: bounds.minX, y: y), proposal: ProposedViewSize(width: bounds.width, height: height))
            y += height + spacing
        }
    }

    private func contentWidth(_ proposal: ProposedViewSize, _ subviews: Subviews) -> CGFloat {
        let ideal = subviews.map { $0.sizeThatFits(.unspecified).width }.max() ?? 0
        return min(ideal, proposal.width ?? ideal)
    }
}

/// Фото из переписки на весь экран: смахни вниз или нажми крестик.
struct ImageViewer: View {
    let id: String
    var onClose: () -> Void
    @State private var offset: CGFloat = 0

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.opacity(1 - min(Double(abs(offset)) / 400.0, 0.6)).ignoresSafeArea()
            RemoteImage(id: id, contentMode: .fit, maxPoints: 1000)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .offset(y: offset)
                .gesture(
                    DragGesture()
                        .onChanged { offset = $0.translation.height }
                        .onEnded { value in
                            if abs(value.translation.height) > 120 { onClose() } else { withAnimation(Motion.snappy) { offset = 0 } }
                        }
                )
            Button(action: onClose) {
                Image(systemName: "xmark")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 40, height: 40)
                    .chatGlass(Circle())
            }
            .padding(16)
            .accessibilityLabel("Закрыть")
        }
    }
}

extension View {
    /// Жидкое стекло iOS 26 там, где оно есть; на старых iOS — матовое стекло с тонкой кромкой.
    @ViewBuilder
    func chatGlass<S: Shape>(_ shape: S, enabled: Bool = true) -> some View {
        if !enabled {
            self
        } else {
            #if compiler(>=6.2)
            if #available(iOS 26.0, *) {
                self.glassEffect(.regular, in: shape)
            } else {
                self.background(.ultraThinMaterial, in: shape)
                    .overlay(shape.stroke(Color.white.opacity(0.12), lineWidth: 1))
            }
            #else
            self.background(.ultraThinMaterial, in: shape)
                .overlay(shape.stroke(Color.white.opacity(0.12), lineWidth: 1))
            #endif
        }
    }
}
