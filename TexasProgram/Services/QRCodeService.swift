import SwiftUI
import CoreImage
import CoreImage.CIFilterBuiltins

/// Сервис генерации и обработки QR-кодов для STRAIN
public enum QRCodeService {
    private static let context = CIContext()

    /// Генерирует изображение QR-кода из строки
    public static fun generateQRCode(from string: String, size: CGFloat = 300) -> UIImage? {
        let data = Data(string.utf8)
        let filter = CIFilter.qrCodeGenerator()
        filter.setValue(data, forKey: "inputMessage")
        filter.setValue("M", forKey: "inputCorrectionLevel")

        guard let outputImage = filter.outputImage else { return nil }

        let extent = outputImage.extent
        let scaleX = size / extent.size.width
        let scaleY = size / extent.size.height
        let transformedImage = outputImage.transformed(by: CGAffineTransform(scaleX: scaleX, y: scaleY))

        guard let cgImage = context.createCGImage(transformedImage, from: transformedImage.extent) else {
            return nil
        }
        return UIImage(cgImage: cgImage)
    }
}
