import SwiftUI
import AVFoundation

/// Экран сканирования QR-кода через нативную камеру iOS
public struct QRScannerView: UIViewControllerRepresentable {
    public let onScan: (String) -> Void
    public let onCancel: () -> Void

    public init(onScan: @escaping (String) -> Void, onCancel: @escaping () -> Void) {
        self.onScan = onScan
        self.onCancel = onCancel
    }

    public func makeUIViewController(context: Context) -> ScannerViewController {
        let controller = ScannerViewController()
        controller.delegate = context.coordinator
        return controller
    }

    public func updateUIViewController(_ uiViewController: ScannerViewController, context: Context) {}

    public func makeCoordinator() -> Coordinator {
        Coordinator(onScan: onScan, onCancel: onCancel)
    }

    public final class Coordinator: NSObject, AVCaptureMetadataOutputObjectsDelegate {
        let onScan: (String) -> Void
        let onCancel: () -> Void
        private var hasScanned = false

        init(onScan: @escaping (String) -> Void, onCancel: @escaping () -> Void) {
            self.onScan = onScan
            self.onCancel = onCancel
        }

        public func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
            guard !hasScanned,
                  let metadataObject = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
                  let stringValue = metadataObject.stringValue else {
                return
            }
            hasScanned = true
            AudioServicesPlaySystemSound(SystemSoundID(kSystemSoundID_Vibrate))
            onScan(stringValue)
        }
    }

    public final class ScannerViewController: UIViewController {
        var delegate: AVCaptureMetadataOutputObjectsDelegate?
        private var captureSession: AVCaptureSession?
        private var previewLayer: AVCaptureVideoPreviewLayer?

        public override func viewDidLoad() {
            super.viewDidLoad()
            view.backgroundColor = .black
            setupCamera()
        }

        public override func viewDidLayoutSubviews() {
            super.viewDidLayoutSubviews()
            previewLayer?.frame = view.layer.bounds
        }

        public override func viewWillAppear(_ animated: Bool) {
            super.viewWillAppear(animated)
            if captureSession?.isRunning == false {
                DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                    self?.captureSession?.startRunning()
                }
            }
        }

        public override func viewWillDisappear(_ animated: Bool) {
            super.viewWillDisappear(animated)
            if captureSession?.isRunning == true {
                DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                    self?.captureSession?.stopRunning()
                }
            }
        }

        private func setupCamera() {
            let session = AVCaptureSession()
            guard let videoCaptureDevice = AVCaptureDevice.default(for: .video),
                  let videoInput = try? AVCaptureDeviceInput(device: videoCaptureDevice),
                  session.canAddInput(videoInput) else {
                showUnavailableMessage()
                return
            }

            session.addInput(videoInput)
            let metadataOutput = AVCaptureMetadataOutput()

            if session.canAddOutput(metadataOutput) {
                session.addOutput(metadataOutput)
                metadataOutput.setMetadataObjectsDelegate(delegate, queue: DispatchQueue.main)
                metadataOutput.metadataObjectTypes = [.qr]
            } else {
                showUnavailableMessage()
                return
            }

            let preview = AVCaptureVideoPreviewLayer(session: session)
            preview.videoGravity = .resizeAspectFill
            preview.frame = view.layer.bounds
            view.layer.addSublayer(preview)

            self.previewLayer = preview
            self.captureSession = session

            DispatchQueue.global(qos: .userInitiated).async {
                session.startRunning()
            }
        }

        private func showUnavailableMessage() {
            let label = UILabel()
            label.text = "Камера недоступна (симулятор или нет прав). Введи код вручную."
            label.textColor = .white
            label.textAlignment = .center
            label.numberOfLines = 0
            label.font = .systemFont(ofSize: 14, weight: .medium)
            label.translatesAutoresizingMaskIntoConstraints = false
            view.addSubview(label)

            NSLayoutConstraint.activate([
                label.centerXAnchor.constraint(equalTo: view.centerXAnchor),
                label.centerYAnchor.constraint(equalTo: view.centerYAnchor),
                label.leadingAnchor.constraint(greaterThanOrEqualTo: view.leadingAnchor, constant: 20),
                label.trailingAnchor.constraint(lessThanOrEqualTo: view.trailingAnchor, constant: -20)
            ])
        }
    }
}
