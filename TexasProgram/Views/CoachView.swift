import SwiftUI

/// Разбор тренировок: приложение отправляет заметки и цифры модели и показывает ответ.
///
/// Всё, что можно посчитать, приложение считает само. Сюда уходит только проза —
/// то, что человек написал про самочувствие, и цифры рядом, чтобы совет
/// опирался на реальные веса, а не на общие слова.
struct CoachCard: View {
    @Bindable var profile: ProgramProfile
    @AppStorage("coachKey") private var storedKey = ""
    @AppStorage("coachModel") private var storedModel = ""
    @State private var loading = false
    @State private var errorText: String?

    private var hasData: Bool { CoachAdvisor.hasData(profile) }

    var body: some View {
        CardView {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    Image(systemName: "text.magnifyingglass")
                        .font(.footnote.weight(.bold))
                        .foregroundStyle(Theme.accentGradient)
                    Text("Разбор тренировок")
                        .font(.headline)
                    Spacer(minLength: 0)
                    if let date = profile.lastAdviceDate {
                        Text(RuDate.dayMonth(date))
                            .font(.caption2).foregroundStyle(.secondary)
                    }
                }

                if let advice = profile.lastAdvice, !advice.isEmpty {
                    Text(advice)
                        .font(.callout)
                        .foregroundStyle(.primary)
                        .fixedSize(horizontal: false, vertical: true)
                        .textSelection(.enabled)
                } else {
                    Text(hasData
                         ? "Приложение отправит заметки и записанные подходы за последние недели и вернёт разбор: что видно, что поменять, за чем следить."
                         : "Пиши заметки после тренировок и записывай подходы — тогда будет что разбирать.")
                        .font(.caption).foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }

                if let errorText {
                    Text(errorText)
                        .font(.caption)
                        .foregroundStyle(Theme.record)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Button {
                    Task { await analyze() }
                } label: {
                    HStack(spacing: 8) {
                        if loading {
                            ProgressView().controlSize(.small)
                        } else {
                            Image(systemName: profile.lastAdvice == nil ? "sparkles" : "arrow.clockwise")
                        }
                        Text(loading ? "Смотрю…" : (profile.lastAdvice == nil ? "Разобрать" : "Обновить разбор"))
                            .font(.subheadline.weight(.semibold))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 11)
                    .background(Theme.accentGradient.opacity(hasData ? 1 : 0.35),
                                in: RoundedRectangle(cornerRadius: 13, style: .continuous))
                    .foregroundStyle(.white)
                }
                .buttonStyle(.plain)
                .disabled(loading || !hasData)
            }
        }
    }

    private func analyze() async {
        loading = true
        errorText = nil
        do {
            let text = try await CoachAdvisor.analyze(profile: profile, key: storedKey, model: storedModel)
            withAnimation(Motion.card) {
                profile.lastAdvice = text
                profile.lastAdviceDate = Date()
            }
        } catch {
            errorText = (error as? CoachError)?.errorDescription ?? "Не получилось связаться с сервером. Проверь интернет."
        }
        loading = false
    }
}
