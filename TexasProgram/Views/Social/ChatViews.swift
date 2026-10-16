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
                    Text((chat.last.fromMe ? "Вы: " : "") + chat.last.preview)
                        .font(.system(size: 14, weight: chat.unread > 0 ? .semibold : .regular))
                        .foregroundStyle(Color.white.opacity(chat.unread > 0 ? 0.9 : 0.5))
                        .lineLimit(1)
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

/// Переписка с одним человеком. Новые сообщения подтягиваются каждые несколько
/// секунд, пока экран открыт; панель вкладок на это время прячется.
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
                        MessageBubble(
                            message: message,
                            localImage: localImages[message.id],
                            onOpenImage: { viewing = ViewedImage(id: $0) },
                            onDelete: { delete(message) }
                        )
                        .id(message.id)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
            }
            .scrollDismissesKeyboard(.interactively)
            .defaultScrollAnchor(.bottom)
            .onChange(of: messages.last?.id) { _, id in
                guard let id else { return }
                withAnimation(Motion.snappy) { proxy.scrollTo(id, anchor: .bottom) }
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
                        AvatarView(imageId: user?.avatar, username: username, size: 28)
                        Text("@\(username)")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(.white)
                    }
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
                try? await Task.sleep(nanoseconds: 4_000_000_000)
                guard !Task.isCancelled else { return }
                await loadNewer()
            }
        }
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

    /// Сервер отдаёт новые первыми, на экране — старые сверху.
    private func merge(_ incoming: [SocialMessage]) {
        let known = Set(messages.map(\.id))
        let fresh = incoming.filter { !known.contains($0.id) }
        guard !fresh.isEmpty else { return }
        withAnimation(Motion.snappy) {
            messages = (messages + fresh).sorted { $0.createdAt < $1.createdAt }
        }
    }

    private func loadLatest() async {
        loading = true
        defer { loading = false }
        do {
            let page = try await social.messages(with: username)
            user = page.user
            older = page.next
            merge(page.messages)
            error = nil
        } catch {
            if let message = error.socialMessage { self.error = message }
        }
    }

    private func loadNewer() async {
        guard let latest = messages.last(where: { !$0.isLocal }) else {
            await loadLatest()
            return
        }
        if let page = try? await social.messages(with: username, after: latest.createdAt), !page.messages.isEmpty {
            merge(page.messages)
            await social.refreshUnread()
        }
    }

    private func loadOlder() async {
        guard let older, !loadingOlder else { return }
        loadingOlder = true
        defer { loadingOlder = false }
        if let page = try? await social.messages(with: username, before: older) {
            self.older = page.next
            merge(page.messages)
        }
    }

    /// Сообщение сразу на экране; фото сначала грузится на сервер.
    /// Не ушло — текст и фото возвращаются в поле.
    private func send() {
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        let photo = attachment
        guard !text.isEmpty || photo != nil else { return }
        let clientId = UUID().uuidString.lowercased()
        let local = SocialMessage(
            id: "local-" + clientId,
            text: text,
            image: photo == nil ? nil : "local",
            fromMe: true,
            createdAt: Date().timeIntervalSince1970 * 1000
        )
        if let photo { localImages[local.id] = photo }
        draft = ""
        attachment = nil
        sending = true
        withAnimation(Motion.snappy) { messages.append(local) }
        Task {
            defer { sending = false }
            do {
                var imageId: String?
                if let photo { imageId = try await social.uploadImage(photo, maxSide: 1600) }
                let sent = try await social.sendMessage(to: username, text: text, image: imageId, clientId: clientId)
                messages.removeAll { $0.id == local.id }
                localImages[local.id] = nil
                merge([sent])
                error = nil
            } catch {
                messages.removeAll { $0.id == local.id }
                localImages[local.id] = nil
                if draft.isEmpty { draft = text }
                if attachment == nil { attachment = photo }
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

/// Фото, открытое на весь экран.
struct ViewedImage: Identifiable, Hashable {
    let id: String
}

struct MessageBubble: View {
    let message: SocialMessage
    var localImage: UIImage? = nil
    var onOpenImage: (String) -> Void = { _ in }
    var onDelete: () -> Void

    var body: some View {
        HStack {
            if message.fromMe { Spacer(minLength: 60) }
            VStack(alignment: message.fromMe ? .trailing : .leading, spacing: 4) {
                if message.image != nil {
                    photo
                }
                if !message.text.isEmpty {
                    Text(message.text)
                        .font(.system(size: 16))
                        .foregroundStyle(message.fromMe ? Color.white : Color.white.opacity(0.92))
                        .textSelection(.enabled)
                        .fixedSize(horizontal: false, vertical: true)
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
                .padding(.horizontal, message.image == nil ? 0 : 9)
                .padding(.bottom, message.image == nil ? 0 : 4)
            }
            .padding(.horizontal, message.image == nil ? 13 : 4)
            .padding(.vertical, message.image == nil ? 8 : 4)
            .background(
                message.fromMe ? Theme.accent.opacity(0.9) : Color(white: 0.16),
                in: RoundedRectangle(cornerRadius: 18, style: .continuous)
            )
            .contextMenu {
                if !message.text.isEmpty {
                    Button {
                        UIPasteboard.general.string = message.text
                    } label: { Label("Скопировать", systemImage: "doc.on.doc") }
                }
                if message.fromMe && !message.isLocal {
                    Button(role: .destructive, action: onDelete) { Label("Удалить", systemImage: "trash") }
                }
            }
            if !message.fromMe { Spacer(minLength: 60) }
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
