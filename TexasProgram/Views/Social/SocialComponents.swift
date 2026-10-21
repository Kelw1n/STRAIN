import ImageIO
import SwiftUI
import UIKit

// MARK: - Фото с сервера

/// Загрузчик фото: готовые картинки держит в памяти, распаковывает и уменьшает
/// в фоне. Стандартный AsyncImage кэша в памяти не держит — при прокрутке назад
/// фото грузилось заново, мигала заглушка, а распаковка шла на главном потоке.
final class ImagePipeline: @unchecked Sendable {
    static let shared = ImagePipeline()

    private let cache = NSCache<NSString, UIImage>()
    private let session: URLSession
    private var tasks: [String: Task<UIImage?, Never>] = [:]
    private let lock = NSLock()

    private init() {
        cache.totalCostLimit = 120 * 1024 * 1024
        let configuration = URLSessionConfiguration.default
        // Адрес фото никогда не меняется — диск хранит его надолго.
        configuration.urlCache = URLCache(memoryCapacity: 8 * 1024 * 1024, diskCapacity: 250 * 1024 * 1024)
        configuration.requestCachePolicy = .returnCacheDataElseLoad
        session = URLSession(configuration: configuration)
    }

    private func key(_ url: URL, _ maxPixel: Int) -> NSString { "\(url.absoluteString)#\(maxPixel)" as NSString }

    /// Уже готовая картинка — сразу, без ожидания и без мигания.
    func cached(_ url: URL, maxPixel: Int) -> UIImage? { cache.object(forKey: key(url, maxPixel)) }

    func image(_ url: URL, maxPixel: Int) async -> UIImage? {
        let cacheKey = key(url, maxPixel)
        if let hit = cache.object(forKey: cacheKey) { return hit }
        // Проверка «уже грузится?» и запись новой загрузки — одним шагом под замком:
        // два одинаковых фото в ленте качаются один раз.
        let (task, created): (Task<UIImage?, Never>, Bool) = lock.withLock {
            if let running = tasks[cacheKey as String] { return (running, false) }
            let task = Task<UIImage?, Never>.detached(priority: .userInitiated) { [session] in
                guard let (data, _) = try? await session.data(from: url) else { return nil }
                return Self.decode(data, maxPixel: maxPixel)
            }
            tasks[cacheKey as String] = task
            return (task, true)
        }
        let image = await task.value
        guard created else { return image }
        lock.withLock { tasks[cacheKey as String] = nil }
        if let image {
            let cost = Int(image.size.width * image.size.height * image.scale * image.scale * 4)
            cache.setObject(image, forKey: cacheKey, cost: cost)
        }
        return image
    }

    /// Уменьшает до нужного размера сразу при чтении и распаковывает заранее:
    /// главному потоку остаётся только показать готовые пиксели.
    private static func decode(_ data: Data, maxPixel: Int) -> UIImage? {
        let options = [kCGImageSourceShouldCache: false] as CFDictionary
        guard let source = CGImageSourceCreateWithData(data as CFData, options) else { return nil }
        let thumbOptions = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixel
        ] as CFDictionary
        guard let cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0, thumbOptions) else { return nil }
        return UIImage(cgImage: cgImage)
    }
}

/// Фото по идентификатору. Адрес фото никогда не меняется, поэтому
/// оно кэшируется и второй раз не скачивается и не распаковывается.
struct RemoteImage: View {
    let id: String?
    var contentMode: ContentMode = .fill
    /// Сторона в точках, под которую уменьшить. По умолчанию — ширина экрана.
    var maxPoints: CGFloat = 430

    @State private var image: UIImage?
    @State private var failed = false
    @Environment(\.displayScale) private var displayScale

    private var url: URL? { SocialService.shared.imageURL(id) }
    private var maxPixel: Int { Int(min(maxPoints * displayScale, 1600)) }

    var body: some View {
        ZStack {
            if let image = image ?? url.flatMap({ ImagePipeline.shared.cached($0, maxPixel: maxPixel) }) {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: contentMode)
                    .transition(.opacity)
            } else {
                placeholder(icon: (url == nil || failed) ? "photo" : nil)
            }
        }
        .animation(.easeOut(duration: 0.18), value: image != nil)
        .task(id: id) {
            failed = false
            guard let url else { image = nil; return }
            if let ready = ImagePipeline.shared.cached(url, maxPixel: maxPixel) {
                image = ready
                return
            }
            let loaded = await ImagePipeline.shared.image(url, maxPixel: maxPixel)
            guard !Task.isCancelled else { return }
            image = loaded
            failed = loaded == nil
        }
    }

    /// Ровная подложка без спиннера: в ленте десятки спиннеров съедали кадры.
    private func placeholder(icon: String?) -> some View {
        ZStack {
            Color(white: 0.12)
            if let icon {
                Image(systemName: icon)
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(Color.white.opacity(0.25))
            }
        }
    }
}

struct AvatarView: View {
    let imageId: String?
    let username: String
    var size: CGFloat = 44

    var body: some View {
        Group {
            if imageId != nil {
                RemoteImage(id: imageId, maxPoints: size)
            } else {
                ZStack {
                    LinearGradient(colors: [Theme.accent, Theme.accentDeep], startPoint: .topLeading, endPoint: .bottomTrailing)
                    Text(String(username.prefix(1)).uppercased())
                        .font(.system(size: size * 0.42, weight: .heavy, design: Theme.fontDesign))
                        .foregroundStyle(.white)
                }
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .overlay(Circle().strokeBorder(Color.white.opacity(0.08), lineWidth: 1))
        .accessibilityHidden(true)
    }
}

// MARK: - Медаль

struct Hexagon: Shape {
    func path(in rect: CGRect) -> Path {
        let w = rect.width
        let h = rect.height
        var path = Path()
        path.move(to: CGPoint(x: rect.minX + w * 0.25, y: rect.minY + h * 0.04))
        path.addLine(to: CGPoint(x: rect.minX + w * 0.75, y: rect.minY + h * 0.04))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.midY))
        path.addLine(to: CGPoint(x: rect.minX + w * 0.75, y: rect.maxY - h * 0.04))
        path.addLine(to: CGPoint(x: rect.minX + w * 0.25, y: rect.maxY - h * 0.04))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.midY))
        path.closeSubpath()
        return path
    }
}

/// Шестиугольная медаль: цвет — уровень, значок — что за достижение.
struct MedalView: View {
    let kind: AchievementKind?
    let level: Int
    var size: CGFloat = 64

    static func colors(for level: Int) -> [Color] {
        switch level {
        case 1: return [Color(red: 0.80, green: 0.52, blue: 0.32), Color(red: 0.55, green: 0.32, blue: 0.18)]
        case 2: return [Color(red: 0.86, green: 0.88, blue: 0.92), Color(red: 0.55, green: 0.58, blue: 0.64)]
        case 3: return [Color(red: 1.00, green: 0.82, blue: 0.35), Color(red: 0.80, green: 0.55, blue: 0.10)]
        case 4: return [Color(red: 0.55, green: 0.92, blue: 0.95), Color(red: 0.20, green: 0.55, blue: 0.75)]
        case 5: return [Theme.accent, Color(red: 0.55, green: 0.10, blue: 0.45)]
        default: return [Color(white: 0.22), Color(white: 0.14)]
        }
    }

    var body: some View {
        ZStack {
            Hexagon()
                .fill(LinearGradient(colors: Self.colors(for: level), startPoint: .topLeading, endPoint: .bottomTrailing))
            Hexagon()
                .strokeBorderCompat(Color.white.opacity(level > 0 ? 0.35 : 0.08), lineWidth: max(1, size * 0.03))
            Image(systemName: kind?.symbol ?? "questionmark")
                .font(.system(size: size * 0.38, weight: .bold))
                .foregroundStyle(level > 0 ? Color.white : Color.white.opacity(0.3))
                .shadow(color: .black.opacity(level > 0 ? 0.35 : 0), radius: 2, y: 1)
        }
        .frame(width: size, height: size * 0.9)
        .accessibilityLabel("\(kind?.title ?? "Медаль"), уровень \(level)")
    }
}

private extension Shape {
    /// `strokeBorder` есть только у `InsettableShape` — для шестиугольника просто обводим внутрь.
    func strokeBorderCompat(_ color: Color, lineWidth: CGFloat) -> some View {
        stroke(color, lineWidth: lineWidth).padding(lineWidth / 2)
    }
}

// MARK: - Карусель фото поста

struct PhotoCarousel: View {
    let ids: [String]
    @State private var page = 0

    var body: some View {
        TabView(selection: $page) {
            ForEach(Array(ids.enumerated()), id: \.offset) { index, id in
                RemoteImage(id: id)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .clipped()
                    .tag(index)
            }
        }
        .tabViewStyle(.page(indexDisplayMode: ids.count > 1 ? .always : .never))
        .aspectRatio(4.0 / 5.0, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
}

// MARK: - Камера

extension UIImage {
    /// Уменьшенная копия. Снимок камеры на 12 Мп в памяти весит под 50 МБ —
    /// пять таких на экране итогов могли выгрузить приложение на старом iPhone.
    func downscaled(maxSide: CGFloat = 2048) -> UIImage {
        let longest = max(size.width, size.height)
        guard longest > maxSide else { return self }
        let ratio = maxSide / longest
        let target = CGSize(width: (size.width * ratio).rounded(), height: (size.height * ratio).rounded())
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        return UIGraphicsImageRenderer(size: target, format: format).image { _ in
            draw(in: CGRect(origin: .zero, size: target))
        }
    }

    /// Уменьшенная и отражённая по горизонтали копия — селфи таким, каким его
    /// видно в камере. Уже отражённый кадр второй раз не переворачиваем.
    func mirroredSelfie(maxSide: CGFloat = 2048) -> UIImage {
        switch imageOrientation {
        case .upMirrored, .downMirrored, .leftMirrored, .rightMirrored: return downscaled(maxSide: maxSide)
        default: break
        }
        let ratio = min(1, maxSide / max(size.width, size.height))
        let target = CGSize(width: (size.width * ratio).rounded(), height: (size.height * ratio).rounded())
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        return UIGraphicsImageRenderer(size: target, format: format).image { context in
            context.cgContext.translateBy(x: target.width, y: 0)
            context.cgContext.scaleBy(x: -1, y: 1)
            draw(in: CGRect(origin: .zero, size: target))
        }
    }
}

/// Съёмка прямо из приложения: не нужно уходить в «Камеру» и потом искать фото в галерее.
struct CameraPicker: UIViewControllerRepresentable {
    @Binding var isPresented: Bool
    let onImage: (UIImage) -> Void

    static var isAvailable: Bool { UIImagePickerController.isSourceTypeAvailable(.camera) }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = Self.isAvailable ? .camera : .photoLibrary
        if Self.isAvailable { picker.cameraCaptureMode = .photo }
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ controller: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(isPresented: $isPresented, onImage: onImage) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let isPresented: Binding<Bool>
        let onImage: (UIImage) -> Void

        init(isPresented: Binding<Bool>, onImage: @escaping (UIImage) -> Void) {
            self.isPresented = isPresented
            self.onImage = onImage
        }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let image = info[.originalImage] as? UIImage {
                // Фронталка отдаёт кадр не зеркальным, и лицо выглядит непривычно
                // перекошенным. Сохраняем, как было в превью, — как «Зеркальная
                // фронтальная камера» в настройках iPhone, только для всех сразу.
                let selfie = picker.sourceType == .camera && picker.cameraDevice == .front
                onImage(selfie ? image.mirroredSelfie() : image)
            }
            isPresented.wrappedValue = false
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            isPresented.wrappedValue = false
        }
    }
}

extension View {
    /// Камера на весь экран. Чёрный фон под ней — чтобы не мелькал экран итогов.
    func cameraSheet(isPresented: Binding<Bool>, onImage: @escaping (UIImage) -> Void) -> some View {
        fullScreenCover(isPresented: isPresented) {
            CameraPicker(isPresented: isPresented, onImage: onImage)
                .ignoresSafeArea()
                .background(Color.black.ignoresSafeArea())
        }
    }
}

// MARK: - Мелочи

struct SocialStat: View {
    let value: String
    let title: String

    var body: some View {
        VStack(spacing: 3) {
            Text(value)
                .font(.system(size: 22, weight: .heavy, design: Theme.fontDesign))
                .foregroundStyle(.white)
                .contentTransition(.numericText())
            Text(title)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(Color.white.opacity(0.55))
        }
        .frame(maxWidth: .infinity)
    }
}

struct FollowButton: View {
    let isFollowing: Bool
    var compact = false
    let action: () -> Void

    var body: some View {
        Button(action: {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            action()
        }) {
            Text(isFollowing ? "Вы подписаны" : "Подписаться")
                .font(.system(size: compact ? 13 : 16, weight: .bold))
                .foregroundStyle(isFollowing ? Color.white : Color.black)
                .padding(.horizontal, compact ? 14 : 0)
                .frame(maxWidth: compact ? nil : .infinity)
                .frame(height: compact ? 32 : 50)
                .background(isFollowing ? Color(white: 0.16) : Color.white, in: RoundedRectangle(cornerRadius: compact ? 16 : 18, style: .continuous))
                // Подписался — кнопка плавно темнеет, а не перескакивает.
                .animation(Motion.snappy, value: isFollowing)
        }
        .buttonStyle(.pressable)
    }
}

/// Плашка ошибки с кнопкой повтора — общая для ленты и профилей.
struct SocialErrorCard: View {
    let message: String
    var retry: (() -> Void)?

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "wifi.exclamationmark")
                .font(.system(size: 26, weight: .semibold))
                .foregroundStyle(Color.white.opacity(0.5))
            Text(message)
                .font(.system(size: 14))
                .foregroundStyle(Color.white.opacity(0.65))
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            if let retry {
                Button("Повторить", action: retry)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(Theme.accent)
            }
        }
        .padding(22)
        .frame(maxWidth: .infinity)
        .background(Theme.surface(.dark), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
}
