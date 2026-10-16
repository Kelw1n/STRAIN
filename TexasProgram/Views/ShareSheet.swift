import SwiftUI
import UIKit

/// Обёртка над системным листом «Поделиться»: файл резервной копии или картинка тренировки.
struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    init(url: URL) {
        items = [url]
    }

    init(items: [Any]) {
        self.items = items
    }

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

/// `sheet(item:)` требует Identifiable, а расширять URL целиком ради одного экрана незачем.
struct ShareItem: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}

/// Картинка для листа «Поделиться».
struct ShareImage: Identifiable {
    let id = UUID()
    let image: UIImage
}
