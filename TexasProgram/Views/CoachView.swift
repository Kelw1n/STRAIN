import SwiftUI
import UIKit

// MARK: - ИИ-тренер

/// Вход в тренера в «Прогрессе». В понедельник и вторник — ещё и разбор
/// прошлой недели одной кнопкой.
struct CoachEntryCard: View {
    @Bindable var profile: ProgramProfile
    @State private var opened: CoachLaunch?
    @State private var review = false

    private struct CoachLaunch: Identifiable {
        let id = UUID()
        let review: Bool
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Button {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                opened = CoachLaunch(review: false)
            } label: {
                HStack(spacing: 14) {
                    Image(systemName: "sparkles")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(Theme.accent)
                        .frame(width: 46, height: 46)
                        .background(Theme.accent.opacity(0.15), in: Circle())
                    VStack(alignment: .leading, spacing: 3) {
                        Text("ИИ-тренер")
                            .font(.system(size: 17, weight: .bold))
                            .foregroundStyle(.white)
                        Text("Спроси про застой, прогресс, восстановление и питание — он видит твои тренировки.")
                            .font(.system(size: 13))
                            .foregroundStyle(Color.white.opacity(0.5))
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.right")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(Color.white.opacity(0.3))
                }
            }
            .buttonStyle(.pressable)

            if review {
                Button {
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    opened = CoachLaunch(review: true)
                } label: {
                    Label("Разбор прошлой недели", systemImage: "calendar.badge.checkmark")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(.black)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(Theme.accent, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .buttonStyle(.pressable)
            }
        }
        .padding(16)
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .sheet(item: $opened, onDismiss: refreshReview) { launch in
            CoachView(profile: profile, startReview: launch.review)
        }
        .onAppear(perform: refreshReview)
    }

    private func refreshReview() {
        review = Coach.needsReview(CoachHistory.read(profile.profileID), profile: profile)
    }
}

struct CoachView: View {
    @Bindable var profile: ProgramProfile
    var startReview = false

    @Environment(\.dismiss) private var dismiss
    @State private var messages: [CoachMessage] = []
    @State private var draft = ""
    @State private var thinking = false
    @State private var error: String?
    @State private var lastQuestion: String?
    @State private var showingKey = false
    @State private var hasKey = GeminiKeychain.load() != nil
    @State private var confirmClear = false
    @State private var startedReview = false
    @FocusState private var focused: Bool

    var body: some View {
        NavigationStack {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 12) {
                        if !hasKey { keyCard }
                        if messages.isEmpty { intro }
                        ForEach(messages) { message in
                            CoachBubble(message: message, profile: profile) { index in apply(index, in: message) }
                                .id(message.id)
                        }
                        if thinking {
                            HStack(spacing: 8) {
                                ProgressView().tint(.white)
                                Text("Тренер думает…")
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundStyle(Color.white.opacity(0.6))
                            }
                            .id("thinking")
                        }
                        if let error {
                            VStack(alignment: .leading, spacing: 8) {
                                Text(error)
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundStyle(Theme.record)
                                if let lastQuestion {
                                    Button("Повторить") { Task { await ask(lastQuestion, retry: true) } }
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundStyle(Theme.accent)
                                }
                            }
                            .id("error")
                        }
                        Color.clear.frame(height: 4).id("bottom")
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                }
                .scrollDismissesKeyboard(.interactively)
                .onChange(of: messages.count) { _, _ in
                    withAnimation(Motion.smooth) { proxy.scrollTo("bottom", anchor: .bottom) }
                }
                .onChange(of: thinking) { _, _ in
                    withAnimation(Motion.smooth) { proxy.scrollTo("bottom", anchor: .bottom) }
                }
                .onAppear { proxy.scrollTo("bottom", anchor: .bottom) }
            }
            .background(Color.black.ignoresSafeArea())
            .safeAreaInset(edge: .bottom) { composer }
            .navigationTitle("ИИ-тренер")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Menu {
                        Button { showingKey = true } label: { Label("Ключ Google AI", systemImage: "key") }
                        Button(role: .destructive) { confirmClear = true } label: { Label("Очистить переписку", systemImage: "trash") }
                    } label: {
                        Image(systemName: "ellipsis.circle").foregroundStyle(.white)
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Готово") { dismiss() }
                        .font(.body.weight(.bold))
                        .foregroundStyle(Theme.accent)
                }
            }
            .confirmationDialog("Очистить переписку с тренером?", isPresented: $confirmClear, titleVisibility: .visible) {
                Button("Очистить", role: .destructive) {
                    messages = []
                    CoachHistory.delete(profile.profileID)
                }
            }
            .sheet(isPresented: $showingKey, onDismiss: { hasKey = GeminiKeychain.load() != nil }) {
                GeminiKeySheet(
                    title: "Ключ для ИИ-тренера",
                    intro: "Тренер отвечает через ИИ Google Gemini по твоему ключу — тому же, что и для фото еды. Ключ бесплатный и хранится только на этом телефоне."
                )
            }
            .task {
                messages = CoachHistory.read(profile.profileID)
                if startReview && !startedReview {
                    startedReview = true
                    await ask(Coach.reviewPrompt, review: Coach.reviewWeek())
                }
            }
        }
        .preferredColorScheme(.dark)
    }

    private var keyCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Нужен ключ Google AI")
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(.white)
            Text("Тренер работает на ИИ Google Gemini по твоему бесплатному ключу. Если ключ уже есть для фото еды — он подойдёт.")
                .font(.system(size: 13))
                .foregroundStyle(Color.white.opacity(0.6))
                .fixedSize(horizontal: false, vertical: true)
            Button { showingKey = true } label: {
                Text("Вставить ключ")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(.black)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .background(Theme.accent, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.pressable)
        }
        .padding(16)
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private var intro: some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: "sparkles")
                .font(.system(size: 28, weight: .bold))
                .foregroundStyle(Theme.accent)
            Text("Привет! Я вижу твою программу, подходы за полтора месяца, вес тела и питание.")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(.white)
                .fixedSize(horizontal: false, vertical: true)
            Text("Программу сам не меняю: если предложу сбросить вес или поменять шаг прибавки — появится кнопка «Применить».")
                .font(.system(size: 13))
                .foregroundStyle(Color.white.opacity(0.5))
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.vertical, 12)
    }

    private var composer: some View {
        VStack(spacing: 8) {
            ScrollView(.horizontal) {
                HStack(spacing: 8) {
                    ForEach(Coach.quickQuestions, id: \.self) { question in
                        Button { Task { await ask(question) } } label: {
                            Text(question)
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 12)
                                .frame(height: 32)
                                .background(Color(white: 0.16), in: Capsule())
                        }
                        .buttonStyle(.pressable)
                        .disabled(thinking)
                    }
                }
                .padding(.horizontal, 16)
            }
            .scrollIndicators(.hidden)
            HStack(alignment: .bottom, spacing: 8) {
                TextField("Спроси тренера", text: $draft, axis: .vertical)
                    .lineLimit(1...5)
                    .focused($focused)
                    .font(.system(size: 16))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(Color(white: 0.13), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                Button {
                    let question = draft
                    draft = ""
                    Task { await ask(question) }
                } label: {
                    Image(systemName: "arrow.up")
                        .font(.system(size: 16, weight: .heavy))
                        .foregroundStyle(.black)
                        .frame(width: 40, height: 40)
                        .background(Theme.accent, in: Circle())
                }
                .buttonStyle(.pressable)
                .disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || thinking)
                .opacity(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || thinking ? 0.4 : 1)
            }
            .padding(.horizontal, 16)
        }
        .padding(.top, 8)
        .padding(.bottom, 8)
        .background(.ultraThinMaterial)
    }

    // MARK: Вопрос и ответ

    private func ask(_ raw: String, review: String? = nil, retry: Bool = false) async {
        let question = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !question.isEmpty, !thinking else { return }
        guard let key = GeminiKeychain.load() else {
            showingKey = true
            return
        }
        error = nil
        lastQuestion = question
        if !retry {
            messages.append(CoachMessage(role: .user, text: question, reviewWeek: review))
            CoachHistory.write(messages, for: profile.profileID)
        }
        thinking = true
        defer { thinking = false }
        let system = Coach.system + "\n\nДАННЫЕ:\n" + Coach.context(for: profile, nutrition: NutritionStore.read(profile.profileID))
        // Последние реплики: первой должна идти реплика человека.
        var turns = messages.suffix(16).map { (role: $0.role == .user ? "user" : "model", text: $0.text) }
        while turns.first?.role == "model" { turns.removeFirst() }
        do {
            let raw = try await FoodRecognizer.converse(system: system, turns: turns, key: key)
            let reply = Coach.parse(raw)
            let actions = reply.actions.filter { Coach.resolve($0, profile: profile) != nil }
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            withAnimation(Motion.snappy) {
                messages.append(CoachMessage(role: .coach, text: reply.text, actions: actions, reviewWeek: review))
            }
            CoachHistory.write(messages, for: profile.profileID)
            lastQuestion = nil
        } catch FoodRecognizer.Failure.nothingFound {
            error = "Тренер не ответил. Попробуй ещё раз."
        } catch is CancellationError {
            return
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func apply(_ index: Int, in message: CoachMessage) {
        guard let position = messages.firstIndex(where: { $0.id == message.id }),
              message.actions.indices.contains(index), !message.applied.contains(index) else { return }
        Coach.apply(message.actions[index], to: profile)
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        withAnimation(Motion.snappy) { messages[position].applied.append(index) }
        CoachHistory.write(messages, for: profile.profileID)
    }
}

/// Реплика: свои — справа оранжевым, тренера — слева с кнопками действий.
private struct CoachBubble: View {
    let message: CoachMessage
    let profile: ProgramProfile
    var onApply: (Int) -> Void

    var body: some View {
        if message.role == .user {
            HStack {
                Spacer(minLength: 48)
                Text(message.text)
                    .font(.system(size: 15))
                    .foregroundStyle(.black)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(Theme.accent, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            }
        } else {
            VStack(alignment: .leading, spacing: 10) {
                if message.reviewWeek != nil {
                    Label("Разбор недели", systemImage: "calendar.badge.checkmark")
                        .font(.system(size: 12, weight: .heavy))
                        .foregroundStyle(Theme.accent)
                }
                Text(LocalizedStringKey(message.text))
                    .font(.system(size: 15))
                    .foregroundStyle(.white)
                    .textSelection(.enabled)
                    .fixedSize(horizontal: false, vertical: true)
                ForEach(Array(message.actions.enumerated()), id: \.offset) { index, action in
                    actionRow(index, action)
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .padding(.trailing, 24)
        }
    }

    @ViewBuilder
    private func actionRow(_ index: Int, _ action: CoachAction) -> some View {
        let applied = message.applied.contains(index)
        if applied || Coach.resolve(action, profile: profile) != nil {
            let resolved = Coach.resolve(action, profile: profile)
            HStack(spacing: 10) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(resolved?.title ?? "Применено")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(.white)
                    if let detail = resolved?.detail, !applied {
                        Text(detail)
                            .font(.system(size: 12))
                            .foregroundStyle(Color.white.opacity(0.5))
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                Spacer(minLength: 8)
                if applied {
                    Label("Готово", systemImage: "checkmark.circle.fill")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(Theme.success)
                } else {
                    Button("Применить") { onApply(index) }
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(.black)
                        .padding(.horizontal, 12)
                        .frame(height: 34)
                        .background(Theme.accent, in: Capsule())
                        .buttonStyle(.pressable)
                }
            }
            .padding(12)
            .background(Color(white: 0.13), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
    }
}
