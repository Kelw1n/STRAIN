import SwiftUI
import UIKit

// MARK: - Кому отправить

/// Друзья, которым можно что-то отправить: сначала те, с кем переписываешься,
/// потом подписки. Своего профиля здесь нет.
@MainActor
enum ChatRecipients {
    static func load() async -> [SocialUserShort] {
        let social = SocialService.shared
        var result: [SocialUserShort] = []
        var seen = Set<String>()
        func add(_ users: [SocialUserShort]) {
            for user in users where !user.isMe && user.id != social.me?.id && seen.insert(user.id).inserted {
                result.append(user)
            }
        }
        if let chats = try? await social.chats() { add(chats.chats.map(\.user)) }
        if let me = social.me, let following = try? await social.following(of: me.username) { add(following) }
        return result
    }
}

/// Лист «Переслать» / «Отправить другу»: недавние переписки, подписки и поиск.
struct ChatTargetSheet: View {
    let title: String
    var onPick: (SocialUserShort) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var recent: [SocialUserShort] = []
    @State private var found: [SocialUserShort] = []
    @State private var loading = true

    private var searching: Bool { !query.trimmingCharacters(in: .whitespaces).isEmpty }
    private var shown: [SocialUserShort] { searching ? found : recent }

    var body: some View {
        NavigationStack {
            List {
                if loading && shown.isEmpty {
                    ProgressView().tint(.white).frame(maxWidth: .infinity).listRowBackground(Color.clear)
                } else if shown.isEmpty {
                    Text(searching ? "Никого не нашли" : "Пока не с кем — найди друга по юзернейму")
                        .font(.system(size: 14))
                        .foregroundStyle(Color.white.opacity(0.5))
                        .listRowBackground(Color.clear)
                }
                ForEach(shown) { user in
                    Button {
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        onPick(user)
                        dismiss()
                    } label: {
                        HStack(spacing: 12) {
                            AvatarView(imageId: user.avatar, username: user.username, size: 40)
                            Text("@\(user.username)")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundStyle(.white)
                            Spacer()
                            Image(systemName: "paperplane.fill")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundStyle(Theme.accent)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.pressable)
                    .listRowBackground(Theme.surface(.dark))
                }
            }
            .scrollContentBackground(.hidden)
            .background(Color.black.ignoresSafeArea())
            .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "Юзернейм")
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Отмена") { dismiss() } }
            }
            .task {
                recent = await ChatRecipients.load()
                loading = false
            }
            .task(id: query) {
                guard searching else { found = []; return }
                // Ждём, пока допечатают, — не шлём запрос на каждую букву.
                try? await Task.sleep(nanoseconds: 300_000_000)
                guard !Task.isCancelled else { return }
                loading = true
                let users = (try? await SocialService.shared.searchUsers(SocialFormat.normalizedUsername(query))) ?? []
                guard !Task.isCancelled else { return }
                found = users.filter { !$0.isMe }
                loading = false
            }
        }
        .presentationDetents([.medium, .large])
        .preferredColorScheme(.dark)
    }
}

extension SocialService {
    /// Тренировка карточкой — тому, кого выбрали. Уходит той же очередью, что
    /// и сообщения: без связи дождётся её.
    func send(_ workout: SocialChatWorkout, to username: String) {
        queue(ChatPending(username: username, text: workout.previewText, workout: workout))
    }
}

/// Друзья кружками в итогах тренировки: отметил — тренировка уйдёт им
/// карточкой в переписку, когда нажмёшь «Дальше».
struct WorkoutRecipientsPicker: View {
    @Binding var selected: Set<String>
    @State private var friends: [SocialUserShort] = []
    @State private var loaded = false

    var body: some View {
        Group {
            if loaded && friends.isEmpty {
                Text("Подпишись на друзей или напиши кому-нибудь — и здесь можно будет отправить им тренировку.")
                    .font(.system(size: 12))
                    .foregroundStyle(Color.white.opacity(0.5))
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 14) {
                        if !loaded {
                            ProgressView().tint(.white).frame(width: 58, height: 58)
                        }
                        ForEach(friends) { friend in
                            let on = selected.contains(friend.username)
                            Button {
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                withAnimation(Motion.bouncy) {
                                    if on { selected.remove(friend.username) } else { selected.insert(friend.username) }
                                }
                            } label: {
                                VStack(spacing: 6) {
                                    AvatarView(imageId: friend.avatar, username: friend.username, size: 52)
                                        .overlay(Circle().strokeBorder(on ? Theme.accent : Color.clear, lineWidth: 3).padding(-4))
                                        .overlay(alignment: .bottomTrailing) {
                                            if on {
                                                Image(systemName: "checkmark.circle.fill")
                                                    .font(.system(size: 18))
                                                    .foregroundStyle(.white, Theme.accent)
                                                    .background(Circle().fill(Color.black).padding(2))
                                                    .transition(.scale.combined(with: .opacity))
                                            }
                                        }
                                        .scaleEffect(on ? 1.04 : 1)
                                    Text("@\(friend.username)")
                                        .font(.system(size: 11, weight: on ? .bold : .medium))
                                        .foregroundStyle(on ? .white : Color.white.opacity(0.6))
                                        .lineLimit(1)
                                        .frame(width: 66)
                                }
                            }
                            .buttonStyle(.pressable)
                            .accessibilityLabel("@\(friend.username)")
                            .accessibilityAddTraits(on ? .isSelected : [])
                        }
                    }
                    .padding(.vertical, 6)
                    .padding(.horizontal, 2)
                }
            }
        }
        .task {
            guard !loaded else { return }
            friends = await ChatRecipients.load()
            loaded = true
        }
    }
}

// MARK: - Тренировка в переписке

/// Тренировка карточкой в пузыре: название, время, объём, подходы, рекорды
/// и лучшие подходы первых упражнений. По нажатию открывается целиком.
struct ChatWorkoutCard: View {
    let workout: SocialChatWorkout
    let fromMe: Bool
    var onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 6) {
                    Image(systemName: "dumbbell.fill")
                    Text("ТРЕНИРОВКА")
                        .tracking(1.1)
                    if workout.records > 0 {
                        Spacer(minLength: 4)
                        Label("\(workout.records)", systemImage: "trophy.fill")
                            .foregroundStyle(Theme.warning)
                    }
                }
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(Color.white.opacity(0.7))

                Text(workout.title)
                    .font(.system(size: 18, weight: .heavy, design: Theme.fontDesign))
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.leading)
                    .lineLimit(2)

                HStack(spacing: 14) {
                    stat(SocialFormat.duration(workout.durationSeconds), "время")
                    stat("\(SocialFormat.grouped(workout.volumeKg)) кг", "объём")
                    stat("\(workout.sets)", RuPlural.word(workout.sets, "подход", "подхода", "подходов"))
                }

                if !workout.workout.exercises.isEmpty {
                    VStack(alignment: .leading, spacing: 5) {
                        ForEach(Array(workout.workout.exercises.prefix(3).enumerated()), id: \.offset) { _, exercise in
                            HStack(spacing: 6) {
                                if exercise.hasRecord {
                                    Image(systemName: "trophy.fill")
                                        .font(.system(size: 10, weight: .bold))
                                        .foregroundStyle(Theme.warning)
                                }
                                Text(exercise.name)
                                    .lineLimit(1)
                                Spacer(minLength: 6)
                                if let best = exercise.best {
                                    Text(best.text)
                                        .fontWeight(.bold)
                                        .monospacedDigit()
                                }
                            }
                            .font(.system(size: 13))
                            .foregroundStyle(Color.white.opacity(0.85))
                        }
                        if workout.workout.exercises.count > 3 {
                            let rest = workout.workout.exercises.count - 3
                            Text("и ещё \(rest) \(RuPlural.word(rest, "упражнение", "упражнения", "упражнений"))")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(Color.white.opacity(0.5))
                        }
                    }
                    .padding(10)
                    .background(Color.black.opacity(fromMe ? 0.18 : 0.25), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                }

                HStack(spacing: 4) {
                    Text("Открыть")
                    Image(systemName: "chevron.right")
                }
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(fromMe ? Color.white : Theme.accent)
            }
            .frame(width: 236, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.pressable)
        .accessibilityLabel("Тренировка «\(workout.title)»")
    }

    private func stat(_ value: String, _ title: String) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(value)
                .font(.system(size: 15, weight: .heavy, design: Theme.fontDesign))
                .foregroundStyle(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
            Text(title)
                .font(.system(size: 11))
                .foregroundStyle(Color.white.opacity(0.55))
        }
    }
}

/// Тренировка из переписки целиком: все подходы и «Скопировать к себе».
struct ChatWorkoutDetail: View {
    let workout: SocialChatWorkout
    /// Чья тренировка; `nil` — своя, копировать незачем.
    let author: String?

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    HStack(alignment: .top, spacing: 22) {
                        stat(SocialFormat.duration(workout.durationSeconds), unit: nil, title: "Время")
                        stat(SocialFormat.grouped(workout.volumeKg), unit: "кг", title: "Объём")
                        stat("\(workout.sets)", unit: nil, title: RuPlural.word(workout.sets, "Подход", "Подхода", "Подходов"))
                        if let effort = workout.effort {
                            stat("\(effort)", unit: "/10", title: "Усилие")
                        }
                    }
                    if let author, !workout.workout.exercises.isEmpty {
                        CopyProgramButton(days: [], author: author, workout: (title: workout.title, workout: workout.workout))
                    }
                    PostWorkoutSets(workout: workout.workout)
                }
                .padding(16)
            }
            .background(Color.black.ignoresSafeArea())
            .navigationTitle(workout.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Готово") { dismiss() } }
            }
        }
        .presentationDetents([.medium, .large])
        .preferredColorScheme(.dark)
    }

    private func stat(_ value: String, unit: String?, title: String) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(alignment: .firstTextBaseline, spacing: 3) {
                Text(value)
                    .font(.system(size: 20, weight: .heavy, design: Theme.fontDesign))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                if let unit {
                    Text(unit)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Color.white.opacity(0.5))
                }
            }
            Text(title)
                .font(.system(size: 13))
                .foregroundStyle(Color.white.opacity(0.5))
        }
    }
}
