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
                            .buttonStyle(.plain)
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

/// Переписка с одним человеком. Новое — сообщения, реакции, прочтение и
/// удаление — подтягивается каждые несколько секунд, пока экран открыт;
/// панель вкладок на это время прячется.
struct ChatView: View {
    let username: String
    @Binding var path: NavigationPath

    @State private var user: SocialUserShort?
    @State private var messages: [SocialMessage] = []
    @State private var older: Double?
    @State private var loading = true
    @State private var loadingOlder = false
    @State private var draft = ""
    @State private var sending = false
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
    /// Сообщение, к которому прокрутили по цитате, — коротко подсвечено.
    @State private var highlighted: String?
    /// С этого времени сервера спрашиваем изменения.
    @State private var serverTime: Double?
    @State private var otherTyping = false
    @State private var lastSeen: Double?
    @State private var live: SocialLive?
    @FocusState private var typing: Bool

    private var social: SocialService { .shared }

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
                    ForEach(Array(messages.enumerated()), id: \.element.id) { index, message in
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
                            .id(message.id)
                        } else {
                        MessageBubble(
                            message: message,
                            username: username,
                            localImage: localImages[message.id],
                            highlighted: highlighted == message.id,
                            onOpenImage: { viewing = ViewedImage(id: $0) },
                            onReply: { reply(to: message) },
                            onReact: { react(message, $0) },
                            onOpenQuote: { id in
                                guard messages.contains(where: { $0.id == id }) else { return }
                                withAnimation(Motion.snappy) { proxy.scrollTo(id, anchor: .center) }
                                flash(id)
                            },
                            onDelete: { delete(message) }
                        )
                        .id(message.id)
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
            .onChange(of: messages.last?.id) { _, id in
                guard let id else { return }
                withAnimation(Motion.snappy) { proxy.scrollTo(id, anchor: .bottom) }
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
                .buttonStyle(.plain)
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
            if !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { social.sendTyping(to: username) }
        }
        .cameraSheet(isPresented: $showingCamera) { attachment = $0.downscaled() }
        .fullScreenCover(item: $viewing) { item in
            ImageViewer(id: item.id) { viewing = nil }
        }
        .onAppear { AppChrome.shared.hidesTabBar = true }
        .onDisappear { AppChrome.shared.hidesTabBar = false }
        .task {
            await loadLatest()
            await social.refreshUnread()
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 3_000_000_000)
                guard !Task.isCancelled else { return }
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
            }
            if let replyingTo {
                HStack(spacing: 10) {
                    Image(systemName: "arrowshape.turn.up.left.fill")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Theme.accent)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(replyingTo.fromMe ? "Ответ себе" : "Ответ @\(username)")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(Theme.accent)
                        Text(replyingTo.preview)
                            .font(.system(size: 13))
                            .foregroundStyle(Color.white.opacity(0.7))
                            .lineLimit(1)
                    }
                    Spacer(minLength: 0)
                    Button { withAnimation(Motion.snappy) { self.replyingTo = nil } } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(Color.white.opacity(0.6))
                            .frame(width: 28, height: 28)
                    }
                    .accessibilityLabel("Не отвечать")
                }
                .padding(.leading, 14)
                .padding(.trailing, 6)
                .padding(.vertical, 6)
                .chatGlass(RoundedRectangle(cornerRadius: 18, style: .continuous))
                .transition(.move(edge: .bottom).combined(with: .opacity))
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
                .accessibilityLabel("Прикрепить фото")

                TextField("Сообщение", text: $draft, axis: .vertical)
                    .lineLimit(1...5)
                    .focused($typing)
                    .font(.system(size: 16))
                    .padding(.horizontal, 16)
                    .padding(.vertical, 11)
                    .frame(minHeight: 44)
                    .chatGlass(RoundedRectangle(cornerRadius: 22, style: .continuous))

                Button(action: send) {
                    Group {
                        if sending {
                            ProgressView().tint(.black)
                        } else {
                            Image(systemName: "arrow.up")
                                .font(.system(size: 17, weight: .heavy))
                        }
                    }
                    .foregroundStyle(canSend ? Color.black : Color.white.opacity(0.5))
                    .frame(width: 44, height: 44)
                    .background(canSend ? Color.white : Color.clear, in: Circle())
                    .chatGlass(Circle(), enabled: !canSend)
                }
                .disabled(!canSend)
                .accessibilityLabel("Отправить")
                .animation(Motion.snappy, value: canSend)
            }
        }
        .padding(.horizontal, 10)
        .padding(.top, 6)
        .padding(.bottom, 6)
        .animation(Motion.snappy, value: attachment != nil)
        .animation(Motion.snappy, value: replyingTo?.id)
    }

    private var canSend: Bool {
        !sending && (attachment != nil || !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
    }

    private func dayTitle(_ date: Date) -> String {
        let calendar = Calendar.current
        if calendar.isDateInToday(date) { return "Сегодня" }
        if calendar.isDateInYesterday(date) { return "Вчера" }
        return RuDate.shortDayMonth(date, calendar: calendar)
    }

    /// Новое добавляется, изменённое заменяется, удалённое пропадает.
    /// Сервер отдаёт новые первыми, на экране — старые сверху.
    private func apply(_ incoming: [SocialMessage]) {
        let updated = SocialMessage.merged(messages, with: incoming)
        guard updated != messages else { return }
        withAnimation(Motion.snappy) { messages = updated }
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
            apply(page.messages)
            error = nil
        } catch {
            if let message = error.socialMessage { self.error = message }
        }
    }

    /// Всё, что поменялось с прошлого раза: новые сообщения, реакции, прочтение, удаление.
    private func loadChanges() async {
        guard let since = serverTime else {
            await loadLatest()
            return
        }
        guard let page = try? await social.messages(with: username, since: since) else { return }
        absorb(page)
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
            apply(page.messages)
        }
    }

    private func reply(to message: SocialMessage) {
        guard !message.isLocal else { return }
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        withAnimation(Motion.snappy) { replyingTo = message }
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

    /// Сообщение сразу на экране; фото сначала грузится на сервер.
    /// Не ушло — текст, фото и цитата возвращаются в поле.
    private func send() {
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        let photo = attachment
        let quoted = replyingTo
        guard !text.isEmpty || photo != nil else { return }
        let clientId = UUID().uuidString.lowercased()
        let local = SocialMessage(
            id: "local-" + clientId,
            text: text,
            image: photo == nil ? nil : "local",
            fromMe: true,
            createdAt: Date().timeIntervalSince1970 * 1000,
            replyTo: quoted.map { SocialMessageQuote(id: $0.id, text: $0.text, image: $0.image, fromMe: $0.fromMe) }
        )
        if let photo { localImages[local.id] = photo }
        draft = ""
        attachment = nil
        replyingTo = nil
        sending = true
        withAnimation(Motion.snappy) { messages.append(local) }
        Task {
            defer { sending = false }
            do {
                var imageId: String?
                if let photo { imageId = try await social.uploadImage(photo, maxSide: 1600) }
                let sent = try await social.sendMessage(to: username, text: text, image: imageId, replyTo: quoted?.id, clientId: clientId)
                messages.removeAll { $0.id == local.id }
                localImages[local.id] = nil
                apply([sent])
                error = nil
            } catch {
                messages.removeAll { $0.id == local.id }
                localImages[local.id] = nil
                if draft.isEmpty { draft = text }
                if attachment == nil { attachment = photo }
                if replyingTo == nil { replyingTo = quoted }
                self.error = error.socialMessage
            }
        }
    }

    private func delete(_ message: SocialMessage) {
        Task {
            do {
                try await social.deleteMessage(message.id)
                withAnimation(Motion.snappy) { messages.removeAll { $0.id == message.id } }
            } catch {
                self.error = error.socialMessage
            }
        }
    }
}

/// Три точки собеседника, пока он печатает.
struct TypingBubble: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        HStack {
            TimelineView(.animation(minimumInterval: 0.05, paused: reduceMotion)) { context in
                let phase = context.date.timeIntervalSinceReferenceDate * 3
                HStack(spacing: 4) {
                    ForEach(0..<3, id: \.self) { index in
                        Circle()
                            .fill(Color.white.opacity(0.7))
                            .frame(width: 7, height: 7)
                            .scaleEffect(reduceMotion ? 1 : 0.7 + 0.3 * max(0, sin(phase - Double(index) * 0.7)))
                    }
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(Color(white: 0.16), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .accessibilityLabel("Печатает")
            Spacer(minLength: 60)
        }
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
    var onOpenImage: (String) -> Void = { _ in }
    var onReply: () -> Void = {}
    var onReact: (String) -> Void = { _ in }
    var onOpenQuote: (String) -> Void = { _ in }
    var onDelete: () -> Void

    /// Сдвиг при свайпе влево — «ответить», как в Telegram.
    @State private var swipe: CGFloat = 0

    private static let replyDistance: CGFloat = 64

    var body: some View {
        HStack {
            if message.fromMe { Spacer(minLength: 60) }
            VStack(alignment: message.fromMe ? .trailing : .leading, spacing: 4) {
                bubble
                if !message.reactions.isEmpty {
                    reactions
                }
            }
            .offset(x: swipe)
            .background(alignment: .trailing) {
                // Стрелка «ответить» выезжает справа, пока тянем пузырь.
                Image(systemName: "arrowshape.turn.up.left.circle.fill")
                    .font(.system(size: 26))
                    .foregroundStyle(Color.white.opacity(0.8))
                    .opacity(Double(min(1, -swipe / Self.replyDistance)))
                    .scaleEffect(0.6 + 0.4 * min(1, -swipe / Self.replyDistance))
                    .offset(x: swipe + 34)
            }
            if !message.fromMe { Spacer(minLength: 60) }
        }
        .contentShape(Rectangle())
        .simultaneousGesture(swipeToReply)
    }

    private var bubble: some View {
        BubbleStack(spacing: 4) {
            if let quote = message.replyTo {
                Button { onOpenQuote(quote.id) } label: { quoteView(quote) }
                    .buttonStyle(.plain)
                    .padding(.horizontal, message.image == nil ? 0 : 5)
                    .padding(.top, message.image == nil ? 0 : 5)
            }
            if message.image != nil {
                photo
            }
            if !message.text.isEmpty {
                Text(message.text)
                    .font(.system(size: 16))
                    .foregroundStyle(message.fromMe ? Color.white : Color.white.opacity(0.92))
                    .textSelection(.enabled)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, message.image == nil ? 0 : 9)
            }
            HStack(spacing: 4) {
                Text(message.date, format: .dateTime.hour().minute())
                if message.fromMe {
                    Image(systemName: message.isLocal ? "clock" : (message.read ? "checkmark.circle.fill" : "checkmark"))
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
            message.fromMe ? Theme.accent.opacity(0.9) : Color(white: 0.16),
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
            if !message.text.isEmpty {
                Button {
                    UIPasteboard.general.string = message.text
                } label: { Label("Скопировать", systemImage: "doc.on.doc") }
            }
            if message.fromMe && !message.isLocal {
                Button(role: .destructive, action: onDelete) { Label("Удалить", systemImage: "trash") }
            }
        }
    }

    private func quoteView(_ quote: SocialMessageQuote) -> some View {
        HStack(spacing: 8) {
            RoundedRectangle(cornerRadius: 1.5)
                .fill(message.fromMe ? Color.white : Theme.accent)
                .frame(width: 3)
            if let image = quote.image {
                RemoteImage(id: image)
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
                .buttonStyle(.plain)
                .transition(.scale.combined(with: .opacity))
                .accessibilityLabel("\(reaction.emoji) \(reaction.count)")
            }
        }
    }

    /// Тянем влево — пузырь едет за пальцем; дотянули — ответ.
    /// Вертикальная прокрутка не трогается: смотрим, куда палец пошёл сначала.
    private var swipeToReply: some Gesture {
        DragGesture(minimumDistance: 18)
            .onChanged { value in
                guard !message.isLocal, abs(value.translation.width) > abs(value.translation.height) * 1.6 else { return }
                let pulled = min(0, value.translation.width)
                // Дальше порога — с сопротивлением.
                swipe = pulled < -Self.replyDistance ? -Self.replyDistance + (pulled + Self.replyDistance) * 0.25 : pulled
            }
            .onEnded { _ in
                if swipe <= -Self.replyDistance { onReply() }
                withAnimation(Motion.snappy) { swipe = 0 }
            }
    }

    @ViewBuilder
    private var photo: some View {
        Group {
            if let localImage {
                Image(uiImage: localImage).resizable().scaledToFill()
                    .overlay { ProgressView().tint(.white) }
            } else {
                RemoteImage(id: message.image)
            }
        }
        .frame(width: 230, height: 270)
        .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
        .contentShape(Rectangle())
        .onTapGesture {
            if let id = message.image, !message.isLocal { onOpenImage(id) }
        }
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
            RemoteImage(id: id, contentMode: .fit)
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
