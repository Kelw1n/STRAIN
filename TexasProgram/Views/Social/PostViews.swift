import SwiftUI
import UIKit

// MARK: - Пост в ленте

/// Пост как в REBRN: кто и когда, название, заметка, время, объём и плотность,
/// фото и под ними лайк с комментариями. Без фото вместо них — упражнения.
struct PostCardView: View {
    let post: SocialPost
    var onOpen: () -> Void
    var onOpenAuthor: () -> Void
    var onChange: (SocialPost?) -> Void

    @State private var confirmDelete = false
    @State private var error: String?

    private var social: SocialService { .shared }
    private var isMine: Bool { social.me?.id == post.author.id }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            PostHeader(post: post, onOpenAuthor: onOpenAuthor) {
                if isMine {
                    Button(role: .destructive) { confirmDelete = true } label: {
                        Label("Удалить пост", systemImage: "trash")
                    }
                }
                ShareLink(item: shareText) {
                    Label("Поделиться текстом", systemImage: "square.and.arrow.up")
                }
            }

            VStack(alignment: .leading, spacing: 14) {
                PostTitleBlock(post: post)
                PostStatsRow(post: post)
                if post.photos.isEmpty {
                    PostWorkoutSummary(workout: post.workout, limit: 4)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
            .onTapGesture(perform: onOpen)
            .accessibilityAddTraits(.isButton)

            if !post.photos.isEmpty {
                // Карусель не внутри кнопки: кнопка перехватывала свайп между фото.
                PhotoCarousel(ids: post.photos)
                    .onTapGesture(perform: onOpen)
            }

            PostActions(post: post, onComments: onOpen, onChange: { onChange($0) })

            if let error {
                Text(error)
                    .font(.system(size: 12))
                    .foregroundStyle(Theme.warning)
            }
        }
        .padding(.vertical, 18)
        .confirmationDialog("Удалить тренировку из ленты?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Удалить", role: .destructive) {
                Task {
                    do {
                        try await social.deletePost(post.id)
                        onChange(nil)
                    } catch {
                        self.error = error.socialMessage
                    }
                }
            }
            Button("Отмена", role: .cancel) {}
        }
    }

    private var shareText: String {
        var lines = ["@\(post.author.username) — \(post.title)"]
        lines.append("Время \(SocialFormat.duration(post.durationSeconds)) · объём \(SocialFormat.grouped(post.volumeKg)) кг")
        for exercise in post.workout.exercises {
            if let best = exercise.best { lines.append("\(exercise.name): \(exercise.sets.count) × \(best.text)") }
        }
        lines.append("STRAIN")
        return lines.joined(separator: "\n")
    }
}

struct PostHeader<MenuContent: View>: View {
    let post: SocialPost
    var onOpenAuthor: () -> Void
    @ViewBuilder var menu: () -> MenuContent

    var body: some View {
        HStack(spacing: 12) {
            Button(action: onOpenAuthor) {
                HStack(spacing: 12) {
                    AvatarView(imageId: post.author.avatar, username: post.author.username, size: 46)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("@\(post.author.username)")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(.white)
                        Text(SocialFormat.ago(post.date))
                            .font(.system(size: 13))
                            .foregroundStyle(Color.white.opacity(0.5))
                    }
                }
            }
            .buttonStyle(.plain)
            Spacer()
            Menu(content: menu) {
                Image(systemName: "ellipsis")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(Color.white.opacity(0.55))
                    .frame(width: 38, height: 38)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel("Действия с постом")
        }
    }
}

struct PostTitleBlock: View {
    let post: SocialPost

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(post.title)
                .font(.system(size: 22, weight: .heavy, design: Theme.fontDesign))
                .foregroundStyle(.white)
                .multilineTextAlignment(.leading)
            if !post.description.isEmpty {
                Text(post.description)
                    .font(.system(size: 15))
                    .foregroundStyle(Color.white.opacity(0.6))
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

struct PostStatsRow: View {
    let post: SocialPost

    var body: some View {
        // На узком iPhone большие числа не влезают с обычными промежутками —
        // тогда берём промежутки поуже, а не обрезаем показатели.
        ViewThatFits(in: .horizontal) {
            row(spacing: 26)
            row(spacing: 16)
            row(spacing: 10)
        }
    }

    private func row(spacing: CGFloat) -> some View {
        HStack(alignment: .top, spacing: spacing) {
            stat(SocialFormat.duration(post.durationSeconds), unit: nil, title: "Время")
            stat(SocialFormat.grouped(post.volumeKg), unit: "кг", title: "Объём")
            if post.density > 0 {
                stat(SocialFormat.grouped(post.density), unit: "кг/мин", title: "Плотность")
            }
            if let effort = post.effort {
                stat("\(effort)", unit: "/10", title: "Усилие")
            }
        }
    }

    private func stat(_ value: String, unit: String?, title: String) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(alignment: .firstTextBaseline, spacing: 3) {
                Text(value)
                    .font(.system(size: 18, weight: .heavy, design: Theme.fontDesign))
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

/// Упражнения коротко: «Жим лёжа · 3 подхода · 80 кг × 5».
struct PostWorkoutSummary: View {
    let workout: SocialWorkout
    var limit: Int = .max

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if workout.exercises.isEmpty {
                Text("Без записанных подходов")
                    .font(.system(size: 14))
                    .foregroundStyle(Color.white.opacity(0.5))
                    .padding(16)
            }
            ForEach(Array(workout.exercises.prefix(limit).enumerated()), id: \.offset) { index, exercise in
                HStack(spacing: 12) {
                    Image(systemName: exercise.hasRecord ? "trophy.fill" : "dumbbell.fill")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(exercise.hasRecord ? Theme.warning : Theme.accent)
                        .frame(width: 28, height: 28)
                        .background((exercise.hasRecord ? Theme.warning : Theme.accent).opacity(0.14), in: Circle())
                    VStack(alignment: .leading, spacing: 2) {
                        Text(exercise.name)
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(.white)
                            .lineLimit(1)
                        Text("\(exercise.sets.count) \(RuPlural.word(exercise.sets.count, "подход", "подхода", "подходов"))")
                            .font(.system(size: 12))
                            .foregroundStyle(Color.white.opacity(0.45))
                    }
                    Spacer()
                    if let best = exercise.best {
                        Text(best.text)
                            .font(.system(size: 15, weight: .bold, design: Theme.fontDesign))
                            .foregroundStyle(.white)
                            .monospacedDigit()
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                if index < min(limit, workout.exercises.count) - 1 {
                    Divider().overlay(Color.white.opacity(0.06)).padding(.leading, 54)
                }
            }
            if workout.exercises.count > limit {
                Text("и ещё \(workout.exercises.count - limit)")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Color.white.opacity(0.45))
                    .padding(.horizontal, 14)
                    .padding(.bottom, 12)
            }
        }
        .background(Color(white: 0.09), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

struct PostActions: View {
    let post: SocialPost
    var onComments: () -> Void
    var onChange: (SocialPost) -> Void
    @State private var busy = false

    var body: some View {
        HStack(spacing: 22) {
            Button {
                toggleLike()
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: post.liked ? "heart.fill" : "heart")
                        .font(.system(size: 24, weight: .medium))
                        .foregroundStyle(post.liked ? Theme.record : .white)
                        .symbolEffect(.bounce, value: post.liked)
                    if post.likes > 0 {
                        Text("\(post.likes)")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(.white)
                            .contentTransition(.numericText())
                    }
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel(post.liked ? "Убрать лайк" : "Лайк")

            Button(action: onComments) {
                HStack(spacing: 6) {
                    Image(systemName: "bubble.right")
                        .font(.system(size: 23, weight: .medium))
                        .foregroundStyle(.white)
                    if post.comments > 0 {
                        Text("\(post.comments)")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(.white)
                    }
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Комментарии")
            Spacer()
        }
    }

    private func toggleLike() {
        guard SocialService.shared.isSignedIn, !busy else { return }
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        // Сразу показываем лайк, не дожидаясь сервера; ошибка вернёт как было.
        var optimistic = post
        optimistic.liked.toggle()
        optimistic.likes += optimistic.liked ? 1 : -1
        let original = post
        withAnimation(Motion.snappy) { onChange(optimistic) }
        busy = true
        Task {
            defer { busy = false }
            do {
                onChange(try await SocialService.shared.setLiked(post.id, optimistic.liked))
            } catch {
                onChange(original)
            }
        }
    }
}

// MARK: - Пост целиком с комментариями

struct PostDetailView: View {
    @State var post: SocialPost
    var onChange: (SocialPost?) -> Void = { _ in }
    var openAuthor: ((String) -> Void)?

    @Environment(\.dismiss) private var dismiss
    @State private var comments: [SocialComment] = []
    @State private var loading = true
    @State private var draft = ""
    @State private var sending = false
    @State private var error: String?
    @State private var composing = false
    @State private var confirmDelete = false
    @FocusState private var typing: Bool

    private var social: SocialService { .shared }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                PostHeader(post: post, onOpenAuthor: { openAuthor?(post.author.username) }) {
                    if social.me?.id == post.author.id {
                        Button(role: .destructive) { confirmDelete = true } label: {
                            Label("Удалить пост", systemImage: "trash")
                        }
                    }
                }
                PostTitleBlock(post: post)
                PostStatsRow(post: post)
                if !post.photos.isEmpty { PhotoCarousel(ids: post.photos) }
                PostActions(post: post, onComments: { if social.isSignedIn { composing = true } }, onChange: update)

                exercises

                commentsSection
                composeButton
            }
            .padding(.horizontal, 16)
            // Место под плавающую панель вкладок.
            .padding(.bottom, 120)
        }
        .background(Color.black.ignoresSafeArea())
        .sheet(isPresented: $composing) { composer }
        .confirmationDialog("Удалить тренировку из ленты?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Удалить", role: .destructive) {
                Task {
                    do {
                        try await social.deletePost(post.id)
                        onChange(nil)
                        dismiss()
                    } catch {
                        self.error = error.socialMessage
                    }
                }
            }
            Button("Отмена", role: .cancel) {}
        }
        .navigationTitle(post.title)
        .navigationBarTitleDisplayMode(.inline)
        .task { await loadComments() }
        .refreshable {
            if let fresh = try? await social.post(post.id) { update(fresh) }
            await loadComments()
        }
        .preferredColorScheme(.dark)
    }

    private func update(_ value: SocialPost) {
        post = value
        onChange(value)
    }

    @ViewBuilder
    private var exercises: some View {
        if !post.workout.exercises.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                Text(post.workout.program.map { "ТРЕНИРОВКА · \($0.uppercased())" } ?? "ТРЕНИРОВКА")
                    .font(.system(size: 12, weight: .bold))
                    .tracking(1.2)
                    .foregroundStyle(Color.white.opacity(0.45))
                VStack(spacing: 10) {
                    ForEach(Array(post.workout.exercises.enumerated()), id: \.offset) { _, exercise in
                        VStack(alignment: .leading, spacing: 8) {
                            Text(exercise.name)
                                .font(.system(size: 16, weight: .bold))
                                .foregroundStyle(.white)
                            ForEach(Array(exercise.sets.enumerated()), id: \.offset) { index, set in
                                HStack {
                                    Text("\(index + 1)")
                                        .font(.system(size: 13, weight: .bold, design: Theme.fontDesign))
                                        .foregroundStyle(Color.white.opacity(0.4))
                                        .frame(width: 22, alignment: .leading)
                                    Text(set.text)
                                        .font(.system(size: 15, weight: .semibold, design: Theme.fontDesign))
                                        .foregroundStyle(.white)
                                        .monospacedDigit()
                                    if set.record {
                                        Label("рекорд", systemImage: "trophy.fill")
                                            .font(.system(size: 11, weight: .bold))
                                            .foregroundStyle(Theme.warning)
                                    }
                                    Spacer()
                                }
                            }
                        }
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(white: 0.09), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    }
                }
            }
        }
    }

    private var commentsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(comments.isEmpty ? "КОММЕНТАРИИ" : "КОММЕНТАРИИ · \(comments.count)")
                .font(.system(size: 12, weight: .bold))
                .tracking(1.2)
                .foregroundStyle(Color.white.opacity(0.45))
            if loading && comments.isEmpty {
                ProgressView().tint(.white).frame(maxWidth: .infinity)
            } else if comments.isEmpty {
                Text("Пока тихо. Напиши первым — поддержи или спроси про технику.")
                    .font(.system(size: 14))
                    .foregroundStyle(Color.white.opacity(0.5))
            }
            ForEach(comments) { comment in
                HStack(alignment: .top, spacing: 10) {
                    Button { openAuthor?(comment.author.username) } label: {
                        AvatarView(imageId: comment.author.avatar, username: comment.author.username, size: 34)
                    }
                    .buttonStyle(.plain)
                    VStack(alignment: .leading, spacing: 3) {
                        HStack(spacing: 6) {
                            Text("@\(comment.author.username)")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundStyle(.white)
                            Text(SocialFormat.ago(comment.date))
                                .font(.system(size: 12))
                                .foregroundStyle(Color.white.opacity(0.4))
                        }
                        Text(comment.text)
                            .font(.system(size: 15))
                            .foregroundStyle(Color.white.opacity(0.88))
                            .fixedSize(horizontal: false, vertical: true)
                            .textSelection(.enabled)
                    }
                    Spacer(minLength: 0)
                }
                .contextMenu {
                    if canDelete(comment) {
                        Button(role: .destructive) { delete(comment) } label: {
                            Label("Удалить комментарий", systemImage: "trash")
                        }
                    }
                }
            }
            if let error {
                Text(error).font(.system(size: 12)).foregroundStyle(Theme.warning)
            }
        }
        .padding(.top, 6)
    }

    /// Кнопка «Написать комментарий»: само поле — в листе снизу. Лист перекрывает
    /// плавающую панель вкладок, а поле внизу экрана оказалось бы под ней.
    @ViewBuilder
    private var composeButton: some View {
        if social.isSignedIn {
            Button { composing = true } label: {
                HStack(spacing: 10) {
                    AvatarView(imageId: social.me?.avatar, username: social.me?.username ?? "s", size: 32)
                    Text("Написать комментарий…")
                        .font(.system(size: 15))
                        .foregroundStyle(Color.white.opacity(0.45))
                    Spacer()
                }
                .padding(.horizontal, 12)
                .frame(height: 50)
                .background(Color(white: 0.11), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            }
            .buttonStyle(.pressable)
        } else {
            Text("Создай профиль во вкладке «Профиль», чтобы комментировать")
                .font(.system(size: 13))
                .foregroundStyle(Color.white.opacity(0.5))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
        }
    }

    private var composer: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Комментарий")
                .font(.system(size: 20, weight: .heavy, design: Theme.fontDesign))
                .foregroundStyle(.white)
            HStack(alignment: .bottom, spacing: 10) {
                TextField("Поддержи или спроси про технику", text: $draft, axis: .vertical)
                    .lineLimit(1...5)
                    .focused($typing)
                    .font(.system(size: 16))
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
                    .background(Color(white: 0.13), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                Button(action: send) {
                    Group {
                        if sending { ProgressView().tint(.black) } else {
                            Image(systemName: "arrow.up").font(.system(size: 17, weight: .heavy))
                        }
                    }
                    .foregroundStyle(.black)
                    .frame(width: 44, height: 44)
                    .background(canSend ? Color.white : Color(white: 0.3), in: Circle())
                }
                .disabled(!canSend)
                .accessibilityLabel("Отправить")
            }
            if let error {
                Text(error).font(.system(size: 12)).foregroundStyle(Theme.warning)
            }
            Spacer(minLength: 0)
        }
        .padding(20)
        .presentationDetents([.height(230)])
        .presentationDragIndicator(.visible)
        .presentationBackground(Color(white: 0.07))
        .preferredColorScheme(.dark)
        .onAppear { typing = true }
    }

    private var canSend: Bool { !sending && !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

    private func canDelete(_ comment: SocialComment) -> Bool {
        guard let me = social.me else { return false }
        return comment.author.id == me.id || post.author.id == me.id
    }

    private func loadComments() async {
        loading = true
        defer { loading = false }
        do {
            comments = try await social.comments(post.id)
            error = nil
        } catch {
            self.error = error.socialMessage
        }
    }

    private func send() {
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        sending = true
        Task {
            defer { sending = false }
            do {
                let comment = try await social.addComment(post.id, text: text)
                withAnimation(Motion.snappy) { comments.append(comment) }
                draft = ""
                var updated = post
                updated.comments += 1
                update(updated)
                UINotificationFeedbackGenerator().notificationOccurred(.success)
                error = nil
                composing = false
            } catch {
                self.error = error.socialMessage
            }
        }
    }

    private func delete(_ comment: SocialComment) {
        Task {
            do {
                try await social.deleteComment(comment.id)
                withAnimation(Motion.snappy) { comments.removeAll { $0.id == comment.id } }
                var updated = post
                updated.comments = max(0, updated.comments - 1)
                update(updated)
            } catch {
                self.error = error.socialMessage
            }
        }
    }
}
