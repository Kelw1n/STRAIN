import AVFoundation
import Foundation
import Observation

/// Записанное голосовое: звук AAC (m4a), длительность и «волна» для пузыря.
struct VoiceRecording {
    let data: Data
    let durationMs: Int
    let waveform: String
}

/// Запись голосового. Тот же формат, что пишет Android, — AAC в m4a: голосовое
/// с айфона играет на Android и наоборот.
@Observable
@MainActor
final class VoiceRecorder {
    /// Дольше минуты голосовое не бывает: дошли до неё — запись заканчивается сама.
    static let maxDuration: TimeInterval = 60
    /// Короче полусекунды — это было нажатие, а не запись.
    static let minDuration: TimeInterval = 0.5

    private(set) var isRecording = false
    private(set) var elapsed: TimeInterval = 0
    /// Последние уровни громкости от 0 до 1 — живая волна, пока записываем.
    private(set) var recentLevels: [Double] = []

    /// Запись дошла до минуты — экран сам отправляет её.
    var onLimit: (() -> Void)?

    private var recorder: AVAudioRecorder?
    private var timer: Timer?
    private var levels: [Double] = []
    private let url = FileManager.default.temporaryDirectory.appendingPathComponent("voice-message.m4a")

    enum Permission { case granted, undetermined, denied }

    var permission: Permission {
        switch AVAudioApplication.shared.recordPermission {
        case .granted: return .granted
        case .denied: return .denied
        default: return .undetermined
        }
    }

    func requestPermission() async -> Bool {
        await AVAudioApplication.requestRecordPermission()
    }

    @discardableResult
    func start() -> Bool {
        guard !isRecording, permission == .granted else { return false }
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker, .allowBluetooth])
            try session.setActive(true)
            let settings: [String: Any] = [
                AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
                AVSampleRateKey: 22_050,
                AVNumberOfChannelsKey: 1,
                AVEncoderBitRateKey: 32_000,
                AVEncoderAudioQualityKey: AVAudioQuality.medium.rawValue
            ]
            try? FileManager.default.removeItem(at: url)
            let recorder = try AVAudioRecorder(url: url, settings: settings)
            recorder.isMeteringEnabled = true
            guard recorder.record() else { return false }
            self.recorder = recorder
        } catch {
            return false
        }
        levels = []
        recentLevels = []
        elapsed = 0
        isRecording = true
        timer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak self] _ in
            MainActor.assumeIsolated { self?.tick() }
        }
        return true
    }

    /// Закончить и отдать запись; слишком короткая — `nil`.
    func stop() -> VoiceRecording? {
        guard isRecording, let recorder else { return nil }
        let duration = recorder.currentTime
        recorder.stop()
        finish()
        defer { try? FileManager.default.removeItem(at: url) }
        guard duration >= Self.minDuration, let data = try? Data(contentsOf: url), !data.isEmpty else { return nil }
        return VoiceRecording(
            data: data,
            durationMs: Int((min(duration, Self.maxDuration) * 1000).rounded()),
            waveform: Self.waveform(levels)
        )
    }

    func cancel() {
        guard isRecording else { return }
        recorder?.stop()
        recorder?.deleteRecording()
        finish()
    }

    private func finish() {
        timer?.invalidate()
        timer = nil
        recorder = nil
        isRecording = false
        // Музыка в наушниках, приглушённая на время записи, играет дальше.
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    private func tick() {
        guard let recorder, isRecording else { return }
        recorder.updateMeters()
        // Децибелы от −50 (тишина) до 0 (громко) — в долю от 0 до 1.
        let power = Double(recorder.averagePower(forChannel: 0))
        let level = max(0, min(1, (power + 50) / 50))
        levels.append(level)
        recentLevels = Array((recentLevels + [level]).suffix(28))
        elapsed = recorder.currentTime
        if elapsed >= Self.maxDuration { onLimit?() }
    }

    /// 40 столбиков волны: в каждом — самый громкий кусочек, цифрой от 0 до f.
    nonisolated static func waveform(_ levels: [Double], bars: Int = 40) -> String {
        guard !levels.isEmpty else { return "" }
        return (0..<bars).map { bar -> String in
            let start = bar * levels.count / bars
            let end = max(start + 1, (bar + 1) * levels.count / bars)
            let peak = levels[min(start, levels.count - 1)..<min(end, levels.count)].max() ?? 0
            return String(Int((peak * 15).rounded()), radix: 16)
        }.joined()
    }
}

/// Проигрывание голосовых: одновременно звучит одно. Звук скачивается один
/// раз и дальше берётся из памяти.
@Observable
@MainActor
final class VoicePlayer: NSObject, AVAudioPlayerDelegate {
    static let shared = VoicePlayer()

    /// Какое голосовое играет и какое скачивается.
    private(set) var playingId: String?
    private(set) var loadingId: String?
    private(set) var failedId: String?

    @ObservationIgnored private var player: AVAudioPlayer?
    @ObservationIgnored private let cache = NSCache<NSString, NSData>()

    /// Сколько уже проиграно, от 0 до 1. Читается из `TimelineView`, поэтому
    /// не наблюдаемое: перерисовывается только играющий пузырь.
    func progress(of id: String) -> Double {
        guard playingId == id, let player, player.duration > 0 else { return 0 }
        return player.currentTime / player.duration
    }

    func currentTime(of id: String) -> TimeInterval {
        guard playingId == id, let player else { return 0 }
        return player.currentTime
    }

    func toggle(_ voice: SocialVoice) {
        if playingId == voice.id {
            stop()
            return
        }
        stop()
        failedId = nil
        Task { await play(voice) }
    }

    func stop() {
        player?.stop()
        player = nil
        playingId = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    private func play(_ voice: SocialVoice) async {
        let data: Data
        if let cached = cache.object(forKey: voice.id as NSString) {
            data = cached as Data
        } else {
            guard let url = SocialService.shared.voiceURL(voice.id) else { return }
            loadingId = voice.id
            defer { if loadingId == voice.id { loadingId = nil } }
            do {
                let (body, response) = try await URLSession.shared.data(from: url)
                guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw URLError(.badServerResponse) }
                cache.setObject(body as NSData, forKey: voice.id as NSString)
                data = body
            } catch {
                failedId = voice.id
                return
            }
            // Пока качали, нажали на другое голосовое.
            guard loadingId == voice.id else { return }
        }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .spokenAudio)
            try session.setActive(true)
            let player = try AVAudioPlayer(data: data)
            player.delegate = self
            player.prepareToPlay()
            player.play()
            self.player = player
            playingId = voice.id
        } catch {
            failedId = voice.id
        }
    }

    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor in
            guard self.player === player else { return }
            self.stop()
        }
    }
}
