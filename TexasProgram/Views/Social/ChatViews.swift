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

private struct ChatRow: View {
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
                    Text((chat.last.fromMe ? "Вы: " : "") + chat.last.text)
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
                        MessageBubble(message: message) { delete(message) }
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

    private var inputBar: some View {
        VStack(spacing: 6) {
            if let error {
                Text(error)
                    .font(.system(size: 12))
                    .foregroundStyle(Theme.warning)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            HStack(alignment: .bottom, spacing: 10) {
                TextField("Сообщение", text: $draft, axis: .vertical)
                    .lineLimit(1...5)
                    .focused($typing)
                    .font(.system(size: 16))
                    .padding(.horizontal, 14)
                    .padding(.vertical, 11)
                    .background(Color(white: 0.13), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
                Button(action: send) {
                    Image(systemName: "arrow.up")
                        .font(.system(size: 17, weight: .heavy))
                        .foregroundStyle(.black)
                        .frame(width: 42, height: 42)
                        .background(canSend ? Color.white : Color(white: 0.3), in: Circle())
                }
                .disabled(!canSend)
                .accessibilityLabel("Отправить")
            }
        }
        .padding(.horizontal, 12)
        .padding(.top, 8)
        .padding(.bottom, 8)
        .background(.ultraThinMaterial)
    }

    private var canSend: Bool { !sending && !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

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
        guard let latest = messages.last(where: { !$0.id.hasPrefix("local-") }) else {
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

    /// Сообщение сразу на экране; не ушло — возвращаем текст в поле.
    private func send() {
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        let clientId = UUID().uuidString.lowercased()
        let local = SocialMessage(id: "local-" + clientId, text: text, fromMe: true, createdAt: Date().timeIntervalSince1970 * 1000)
        draft = ""
        sending = true
        withAnimation(Motion.snappy) { messages.append(local) }
        Task {
            defer { sending = false }
            do {
                let sent = try await social.sendMessage(to: username, text: text, clientId: clientId)
                messages.removeAll { $0.id == local.id }
                merge([sent])
                error = nil
            } catch {
                messages.removeAll { $0.id == local.id }
                if draft.isEmpty { draft = text }
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

private struct MessageBubble: View {
    let message: SocialMessage
    var onDelete: () -> Void

    var body: some View {
        HStack {
            if message.fromMe { Spacer(minLength: 60) }
            VStack(alignment: message.fromMe ? .trailing : .leading, spacing: 2) {
                Text(message.text)
                    .font(.system(size: 16))
                    .foregroundStyle(message.fromMe ? Color.white : Color.white.opacity(0.92))
                    .textSelection(.enabled)
                    .fixedSize(horizontal: false, vertical: true)
                HStack(spacing: 4) {
                    Text(message.date, format: .dateTime.hour().minute())
                    if message.fromMe {
                        Image(systemName: message.id.hasPrefix("local-") ? "clock" : (message.read ? "checkmark.circle.fill" : "checkmark"))
                    }
                }
                .font(.system(size: 11))
                .foregroundStyle(Color.white.opacity(0.55))
            }
            .padding(.horizontal, 13)
            .padding(.vertical, 8)
            .background(
                message.fromMe ? Theme.accent.opacity(0.9) : Color(white: 0.16),
                in: RoundedRectangle(cornerRadius: 18, style: .continuous)
            )
            .contextMenu {
                Button {
                    UIPasteboard.general.string = message.text
                } label: { Label("Скопировать", systemImage: "doc.on.doc") }
                if message.fromMe && !message.id.hasPrefix("local-") {
                    Button(role: .destructive, action: onDelete) { Label("Удалить", systemImage: "trash") }
                }
            }
            if !message.fromMe { Spacer(minLength: 60) }
        }
    }
}
