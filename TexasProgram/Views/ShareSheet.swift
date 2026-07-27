import SwiftUI
import UIKit

/// Обёртка над системным листом «Поделиться» — им отдаём файл резервной копии.
struct ShareSheet: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [url], applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

/// `sheet(item:)` требует Identifiable, а расширять URL целиком ради одного экрана незачем.
struct ShareItem: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}
