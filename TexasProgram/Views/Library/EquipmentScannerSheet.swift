import PhotosUI
import SwiftUI
import UIKit

struct EquipmentScannerSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var front: Data?
    @State private var side: Data?
    @State private var result: EquipmentScan?
    @State private var failure: String?
    @State private var needsKey = false
    @State private var showingKey = false
    @State private var showingCamera = false
    @State private var capturingSide = false
    @State private var frontPick: PhotosPickerItem?
    @State private var sidePick: PhotosPickerItem?
    @State private var readingPhoto = false
    @State private var request: UUID?
    @State private var working = false

    private var busy: Bool { working || readingPhoto }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(L("Сними тренажёр целиком. Фото отправляется в Google Gemini для распознавания."))
                        .foregroundStyle(Color.ink.opacity(0.7))
                    HStack(alignment: .top, spacing: 10) {
                        if let front { preview(front, title: "Общий вид") }
                        if let side { preview(side, title: "Фото сбоку") }
                    }
                    if let result { resultCard(result) }
                    if let failure {
                        Text(L(failure))
                            .foregroundStyle(Theme.warning)
                            .accessibilityAddTraits(.updatesFrequently)
                    }
                    if result?.status == .sideView || side != nil {
                        photoControls(sideView: true, selection: $sidePick)
                    }
                    if working {
                        ProgressView(L("Распознаю тренажёр…"))
                            .tint(.ink)
                    } else if needsKey {
                        scannerButton("Настроить ключ Gemini", icon: "key") { showingKey = true }
                    } else if front != nil, result == nil {
                        scannerButton(side == nil ? "Распознать тренажёр" : "Уточнить по двум фото", icon: "viewfinder") {
                            request = UUID()
                        }
                    }
                    photoControls(sideView: false, selection: $frontPick)
                }
                .padding(16)
            }
            .screenBackground()
            .navigationTitle(L("Что за тренажёр?"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(L("Закрыть")) { dismiss() }
                        .foregroundStyle(Color.ink)
                }
            }
        }
        .cameraSheet(isPresented: $showingCamera) { image in
            accept(image, sideView: capturingSide)
        }
        .sheet(isPresented: $showingKey, onDismiss: {
            if GeminiKeychain.load() != nil {
                needsKey = false
                failure = nil
            }
        }) {
            GeminiKeySheet(
                title: "Ключ для сканера тренажёров",
                intro: "Сканер использует тот же ключ Google Gemini, что и распознавание еды."
            )
        }
        .task(id: frontPick) { await loadPhoto(frontPick, sideView: false) }
        .task(id: sidePick) { await loadPhoto(sidePick, sideView: true) }
        .task(id: request) {
            if request != nil { await scan() }
        }
    }

    private func preview(_ data: Data, title: String) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            if let image = UIImage(data: data) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .frame(maxWidth: .infinity)
                    .frame(height: 145)
                    .background(Color.tone(0.11), in: RoundedRectangle(cornerRadius: 14))
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                    .accessibilityHidden(true)
            }
            Text(L(title))
                .font(.caption)
                .foregroundStyle(Color.ink.opacity(0.65))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func photoControls(sideView: Bool, selection: Binding<PhotosPickerItem?>) -> some View {
        VStack(spacing: 8) {
            if CameraPicker.isAvailable {
                scannerButton(sideView ? "Снять сбоку" : (front == nil ? "Снять тренажёр" : "Снять заново"), icon: "camera") {
                    capturingSide = sideView
                    showingCamera = true
                }
            }
            PhotosPicker(selection: selection, matching: .images) {
                Label(L(sideView ? "Выбрать фото сбоку" : "Выбрать фото тренажёра"), systemImage: "photo")
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
            }
            .buttonStyle(.pressable)
            .foregroundStyle(Color.ink)
        }
        .disabled(busy)
    }

    private func scannerButton(_ title: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(L(title), systemImage: icon)
                .font(.system(size: 15, weight: .semibold))
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(14)
                .background(Color.tone(0.11), in: RoundedRectangle(cornerRadius: 14))
        }
        .buttonStyle(.pressable)
        .foregroundStyle(Color.ink)
        .disabled(busy)
    }

    private func resultCard(_ scan: EquipmentScan) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            if scan.status == .sideView {
                Text(L("Нужно фото сбоку")).font(.headline)
                Text(L("Покажи спинку, рукоятки и рычаги в одном кадре."))
                    .foregroundStyle(Color.ink.opacity(0.7))
            } else if scan.status == .unknown {
                Text(L("Не удалось определить тренажёр")).font(.headline)
                Text(L("Попробуй снять тренажёр целиком при хорошем свете."))
                    .foregroundStyle(Color.ink.opacity(0.7))
            }
            ForEach(scan.candidates) { candidate in
                let label = candidate.label
                VStack(alignment: .leading, spacing: 5) {
                    Text(verbatim: label.name).font(.headline)
                    muscleLine("Основные мышцы", label.primary)
                    muscleLine("Помогают", label.secondary)
                    if label.primary.isEmpty {
                        Text(L("Мышцы зависят от упражнения и положения блока."))
                            .foregroundStyle(Color.ink.opacity(0.7))
                    }
                }
            }
            if !scan.observation.isEmpty { Text(verbatim: scan.observation) }
        }
        .foregroundStyle(Color.ink)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Color.tone(0.11), in: RoundedRectangle(cornerRadius: 18))
    }

    @ViewBuilder
    private func muscleLine(_ title: String, _ values: [String]) -> some View {
        if !values.isEmpty {
            Text(verbatim: L(title) + ": " + values.joined(separator: ", "))
                .font(.subheadline)
        }
    }

    @MainActor
    private func loadPhoto(_ item: PhotosPickerItem?, sideView: Bool) async {
        guard let item else { return }
        readingPhoto = true
        defer { readingPhoto = false }
        let data = try? await item.loadTransferable(type: Data.self)
        guard !Task.isCancelled else { return }
        if sideView { sidePick = nil } else { frontPick = nil }
        guard let data, let image = UIImage(data: data) else {
            failure = "Не удалось открыть фото. Выбери другой снимок."
            return
        }
        accept(image, sideView: sideView)
    }

    private func accept(_ image: UIImage, sideView: Bool) {
        guard let jpeg = FoodRecognizer.uploadJPEG(image) else {
            failure = "Не удалось открыть фото. Выбери другой снимок."
            return
        }
        if sideView {
            side = jpeg
        } else {
            front = jpeg
            side = nil
        }
        result = nil
        failure = nil
    }

    @MainActor
    private func scan() async {
        guard let front, !working else { return }
        guard let key = GeminiKeychain.load() else {
            needsKey = true
            failure = "Для сканера нужен ключ Google Gemini."
            return
        }
        working = true
        failure = nil
        defer { working = false }
        do {
            let scan = try await EquipmentScanner.recognize(front: front, side: side, key: key)
            guard !Task.isCancelled else { return }
            result = scan
        } catch {
            guard !Task.isCancelled else { return }
            let known = error as? FoodRecognizer.Failure
            needsKey = known == .badKey || known == .noKey
            failure = known == .nothingFound
                ? "Не удалось получить ответ. Попробуй ещё раз."
                : error.localizedDescription
        }
    }
}
