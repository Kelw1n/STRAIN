import SwiftUI

/// Карточка активного отдыха в стиле REBRN с контекстом упражнения, весом, подходами и кнопками управления.
struct RebrnRestTimerCard: View {
    @Environment(RestTimer.self) private var timer

    var body: some View {
        if timer.isRunning {
            VStack(spacing: 12) {
                // Верхняя строка: «отдых» слева, прошедшее время справа
                HStack {
                    HStack(spacing: 6) {
                        Circle()
                            .stroke(Theme.accent.opacity(0.35), lineWidth: 2)
                            .overlay {
                                Circle()
                                    .trim(from: 0, to: CGFloat(timer.progress))
                                    .stroke(Theme.accent, style: StrokeStyle(lineWidth: 2, lineCap: .round))
                                    .rotationEffect(.degrees(-90))
                                    .animation(.linear(duration: 0.2), value: timer.progress)
                            }
                            .frame(width: 14, height: 14)

                        Text("отдых")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(Theme.accent)
                    }

                    Spacer()

                    Text(timer.elapsedText)
                        .font(.system(size: 13, weight: .bold, design: Theme.fontDesign))
                        .foregroundStyle(.white.opacity(0.85))
                }

                // Средняя строка: иконка упражнения + название + точки подходов и вес
                HStack(spacing: 12) {
                    ExerciseThumb(name: timer.exerciseName ?? "", size: 44)

                    VStack(alignment: .leading, spacing: 4) {
                        Text(timer.exerciseName ?? "Отдых между подходами")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(.white)
                            .lineLimit(1)

                        HStack(spacing: 5) {
                            // Как на острове: сделанные залиты, следующий — кольцом.
                            if timer.totalSets > 0 {
                                ForEach(0..<min(timer.totalSets, 8), id: \.self) { idx in
                                    if idx == timer.completedSets {
                                        Circle()
                                            .strokeBorder(Theme.accent, lineWidth: 1.5)
                                            .frame(width: 7, height: 7)
                                    } else {
                                        Circle()
                                            .fill(idx < timer.completedSets ? Theme.accent : Color.white.opacity(0.25))
                                            .frame(width: 7, height: 7)
                                    }
                                }
                            }

                            if let details = timer.setDetails, !details.isEmpty {
                                Text(details)
                                    .font(.system(size: 13, weight: .medium))
                                    .foregroundStyle(.white.opacity(0.70))
                            }
                        }
                    }

                    Spacer()
                }

                // Нижняя строка: кнопки «−», большой таймер, «+», и оранжевая кнопка «Пропустить»
                HStack(spacing: 12) {
                    Button {
                        withAnimation(Motion.snappy) { timer.add(-30) }
                    } label: {
                        Image(systemName: "minus")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(width: 44, height: 44)
                            .background(Color(white: 0.18), in: Circle())
                    }
                    .buttonStyle(.pressable)

                    Spacer()

                    Text(timer.remainingText)
                        .font(.system(size: 30, weight: .black, design: Theme.fontDesign))
                        .monospacedDigit()
                        .foregroundStyle(Theme.accent)
                        // Цифры перекатываются, а не прыгают.
                        .contentTransition(.numericText(countsDown: true))
                        .animation(.snappy(duration: 0.3), value: timer.remainingText)

                    Spacer()

                    Button {
                        withAnimation(Motion.snappy) { timer.add(30) }
                    } label: {
                        Image(systemName: "plus")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(width: 44, height: 44)
                            .background(Color(white: 0.18), in: Circle())
                    }
                    .buttonStyle(.pressable)

                    Button {
                        timer.stop()
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    } label: {
                        Text("Пропустить")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 22)
                            .padding(.vertical, 12)
                            .background(Theme.accent, in: Capsule())
                    }
                    .buttonStyle(.pressable)
                }
            }
            .padding(16)
            .background(Color(white: 0.07))
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .strokeBorder(
                        LinearGradient(
                            stops: [
                                .init(color: Color.white.opacity(0.28), location: 0.0),
                                .init(color: Theme.accent.opacity(0.35), location: 0.5),
                                .init(color: Color.white.opacity(0.08), location: 1.0)
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: 1
                    )
            )
            .shadow(color: Color.black.opacity(0.6), radius: 20, y: 10)
            .padding(.horizontal, 16)
        }
    }
}
