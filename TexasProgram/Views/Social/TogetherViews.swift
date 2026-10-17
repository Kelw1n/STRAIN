import SwiftUI

// MARK: - Совместная тренировка

/// Плашка сверху экрана: друг зовёт тренироваться вместе.
struct TogetherInviteBanner: View {
    let invite: SocialTogether

    @State private var busy = false

    var body: some View {
        HStack(spacing: 12) {
            AvatarView(imageId: invite.partner.avatar, username: invite.partner.username, size: 40)
            VStack(alignment: .leading, spacing: 2) {
                Text("@\(invite.partner.username)")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                Text("зовёт тренироваться вместе")
                    .font(.system(size: 13))
                    .foregroundStyle(Color.white.opacity(0.65))
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 4)
            Button { respond(accept: false) } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 36, height: 36)
                    .background(Color.white.opacity(0.12), in: Circle())
            }
            .accessibilityLabel("Отказаться")
            Button { respond(accept: true) } label: {
                Text("Принять")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(.black)
                    .padding(.horizontal, 14)
                    .frame(height: 36)
                    .background(Color.white, in: Capsule())
            }
        }
        .disabled(busy)
        .padding(.leading, 12)
        .padding(.trailing, 10)
        .padding(.vertical, 10)
        .chatGlass(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .padding(.horizontal, 12)
    }

    private func respond(accept: Bool) {
        busy = true
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        Task {
            defer { busy = false }
            if accept {
                try? await SocialService.shared.acceptTogether(invite.id)
            } else {
                try? await SocialService.shared.respondTogether(invite.id, "decline")
            }
        }
    }
}

/// Что сейчас делает напарник: «Жим лёжа · 2/4 · подходов 7 из 18».
struct PartnerStatus: View {
    let together: SocialTogether
    var compact = false

    var body: some View {
        HStack(spacing: 12) {
            AvatarView(imageId: together.partner.avatar, username: together.partner.username, size: compact ? 34 : 42)
                .overlay(Circle().strokeBorder(together.partnerLive != nil ? Theme.success : Color.white.opacity(0.2), lineWidth: 2).padding(-3))
            VStack(alignment: .leading, spacing: 2) {
                Text("Вместе с @\(together.partner.username)")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                HStack(spacing: 6) {
                    if together.partnerLive != nil { LivePulse(size: 6) }
                    Text(line)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(together.partnerLive != nil ? Theme.success : Color.white.opacity(0.5))
                        .lineLimit(1)
                }
            }
            Spacer(minLength: 0)
        }
    }

    private var line: String {
        if together.partnerDone { return "уже закончил(а)" }
        guard let live = together.partnerLive else { return "ещё не начал(а)" }
        let sets = live.setsTotal > 0 ? " · подходов \(live.setsDone) из \(live.setsTotal)" : ""
        return live.line + sets
    }
}

/// Карточка над лентой: идёт совместная тренировка.
struct TogetherCard: View {
    let together: SocialTogether
    @State private var confirmLeave = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("СОВМЕСТНАЯ ТРЕНИРОВКА")
                .font(.system(size: 12, weight: .bold))
                .tracking(1.2)
                .foregroundStyle(Color.white.opacity(0.5))
            HStack(spacing: 10) {
                PartnerStatus(together: together)
                Button("Выйти") { confirmLeave = true }
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Color.white.opacity(0.6))
            }
            Text("Начни тренировку как обычно — друг увидит твои подходы, а пост выйдет на двоих.")
                .font(.system(size: 12))
                .foregroundStyle(Color.white.opacity(0.45))
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(14)
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(Theme.success.opacity(0.25), lineWidth: 1))
        .confirmationDialog("Выйти из совместной тренировки?", isPresented: $confirmLeave, titleVisibility: .visible) {
            Button("Выйти", role: .destructive) {
                Task { try? await SocialService.shared.respondTogether(together.id, "leave") }
            }
        }
    }
}

/// Полоса напарника над упражнениями, пока идёт тренировка. Обновляется сама.
struct PartnerStrip: View {
    private var social: SocialService { .shared }

    var body: some View {
        Group {
            if let active = social.together.active {
                PartnerStatus(together: active, compact: true)
                    .padding(12)
                    .background(Color(white: 0.086), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(Theme.success.opacity(0.3), lineWidth: 1))
            }
        }
        .task {
            await social.refreshTogether()
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 15_000_000_000)
                guard !Task.isCancelled, social.together.active != nil else { continue }
                await social.refreshTogether()
            }
        }
    }
}

/// Приглашение в переписке: принять, отказаться или отменить своё.
struct InviteBubble: View {
    let message: SocialMessage
    let invite: SocialInvite
    let username: String
    var onChange: (SocialInvite) -> Void

    @State private var busy = false
    @State private var error: String?

    var body: some View {
        HStack {
            if message.fromMe { Spacer(minLength: 50) }
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 10) {
                    Image(systemName: "figure.strengthtraining.traditional")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: 40, height: 40)
                        .background(Theme.success.opacity(0.85), in: Circle())
                    VStack(alignment: .leading, spacing: 2) {
                        Text(message.fromMe ? "Ты зовёшь @\(username)" : "Тренируемся вместе?")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundStyle(.white)
                        Text(invite.statusText)
                            .font(.system(size: 13))
                            .foregroundStyle(invite.status == "accepted" ? Theme.success : Color.white.opacity(0.6))
                    }
                }
                if invite.isPending {
                    HStack(spacing: 8) {
                        if message.fromMe {
                            action("Отменить", primary: false) { respond("cancel", status: "cancelled") }
                        } else {
                            action("Не сейчас", primary: false) { respond("decline", status: "declined") }
                            action("Принять", primary: true) { respond("accept", status: "accepted") }
                        }
                    }
                }
                if let error {
                    Text(error).font(.system(size: 12)).foregroundStyle(Theme.warning)
                }
            }
            .padding(12)
            .frame(maxWidth: 290, alignment: .leading)
            .background(Color(white: 0.13), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(Theme.success.opacity(0.3), lineWidth: 1))
            .disabled(busy)
            if !message.fromMe { Spacer(minLength: 50) }
        }
    }

    private func action(_ title: String, primary: Bool, _ perform: @escaping () -> Void) -> some View {
        Button(action: perform) {
            Text(title)
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(primary ? Color.black : Color.white)
                .frame(maxWidth: .infinity)
                .frame(height: 38)
                .background(primary ? Color.white : Color.white.opacity(0.12), in: Capsule())
        }
        .buttonStyle(.plain)
    }

    private func respond(_ action: String, status: String) {
        busy = true
        Task {
            defer { busy = false }
            do {
                if action == "accept" {
                    try await SocialService.shared.acceptTogether(invite.id)
                } else {
                    try await SocialService.shared.respondTogether(invite.id, action)
                }
                var updated = invite
                updated.status = status
                onChange(updated)
                error = nil
            } catch {
                self.error = error.socialMessage
            }
        }
    }
}

/// Кнопка в профиле друга: позвать на совместную тренировку.
struct InviteButton: View {
    let username: String
    var onSent: () -> Void

    @State private var busy = false
    @State private var error: String?

    var body: some View {
        VStack(spacing: 6) {
            Button(action: send) {
                HStack(spacing: 8) {
                    if busy {
                        ProgressView().tint(.white)
                    } else {
                        Image(systemName: "figure.strengthtraining.traditional")
                            .font(.system(size: 16, weight: .bold))
                    }
                    Text("Тренироваться вместе")
                        .font(.system(size: 16, weight: .bold))
                }
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 50)
                .background(Theme.success.opacity(0.22), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(Theme.success.opacity(0.5), lineWidth: 1))
            }
            .buttonStyle(.pressable)
            .disabled(busy)
            if let error {
                Text(error).font(.system(size: 12)).foregroundStyle(Theme.warning)
            }
        }
    }

    private func send() {
        busy = true
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        Task {
            defer { busy = false }
            do {
                try await SocialService.shared.invite(username)
                error = nil
                onSent()
            } catch {
                self.error = error.socialMessage
            }
        }
    }
}
