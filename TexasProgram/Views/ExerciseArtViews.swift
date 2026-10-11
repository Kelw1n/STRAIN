import PhotosUI
import SwiftUI

/// Квадратная картинка упражнения. Своя из галереи, если поставлена, иначе иллюстрация.
struct ExerciseThumb: View {
    let name: String
    var size: CGFloat = 48

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: size * 0.25, style: .continuous)
        Group {
            if let image = ExerciseArt.shared.image(for: name) {
                Image(uiImage: image)
                    .resizable()
                    .interpolation(.high)
                    .scaledToFill()
            } else {
                Color(white: 0.85)
                    .overlay {
                        Image(systemName: "dumbbell.fill")
                            .font(.system(size: size * 0.42, weight: .semibold))
                            .foregroundStyle(Color(white: 0.4))
                    }
            }
        }
        .frame(width: size, height: size)
        .clipShape(shape)
        .accessibilityHidden(true)
    }
}

/// Цель листа картинки: `sheet(item:)` ждёт Identifiable.
struct ExerciseArtTarget: Identifiable, Hashable {
    let name: String
    var id: String { name }
}

/// Лист с крупной картинкой упражнения: поставить свою из галереи или вернуть стандартную.
struct ExerciseArtSheet: View {
    let name: String
    @Environment(\.dismiss) private var dismiss
    @State private var pick: PhotosPickerItem?
    @State private var loading = false

    private var art: ExerciseArt { ExerciseArt.shared }

    var body: some View {
        VStack(spacing: 18) {
            Capsule()
                .fill(Color.white.opacity(0.25))
                .frame(width: 40, height: 5)
                .padding(.top, 10)

            Text(name)
                .font(.system(size: 20, weight: .heavy, design: Theme.fontDesign))
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
                .padding(.horizontal)

            ExerciseThumb(name: name, size: 240)
                .overlay {
                    if loading {
                        ProgressView().tint(.white)
                    }
                }
                .shadow(color: .black.opacity(0.5), radius: 20, y: 10)
                .id(art.revision)
                .transition(.scale(scale: 0.92).combined(with: .opacity))

            Text("Стандартная иллюстрация подбирается по названию. Можно поставить свою — она будет у этого упражнения во всех тренировках.")
                .font(.footnote)
                .foregroundStyle(Color.white.opacity(0.5))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 28)

            VStack(spacing: 10) {
                PhotosPicker(selection: $pick, matching: .images) {
                    Label("Выбрать из галереи", systemImage: "photo.on.rectangle")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.black)
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                        .background(Color.white, in: Capsule())
                }
                .buttonStyle(.pressable)

                if art.hasCustomImage(for: name) {
                    Button(role: .destructive) {
                        withAnimation(Motion.snappy) { art.removeCustomImage(for: name) }
                    } label: {
                        Text("Вернуть стандартную")
                            .font(.system(size: 15, weight: .semibold))
                            .frame(maxWidth: .infinity)
                            .frame(height: 44)
                    }
                    .transition(.opacity)
                }
            }
            .padding(.horizontal, 20)

            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity)
        .background(Color(white: 0.07).ignoresSafeArea())
        .presentationDetents([.large])
        .presentationDragIndicator(.hidden)
        .preferredColorScheme(.dark)
        .onChange(of: pick) { _, item in
            guard let item else { return }
            loading = true
            Task {
                let data = try? await item.loadTransferable(type: Data.self)
                await MainActor.run {
                    loading = false
                    pick = nil
                    guard let data, let image = UIImage(data: data) else { return }
                    withAnimation(Motion.bouncy) { art.setCustomImage(image, for: name) }
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                }
            }
        }
    }
}
