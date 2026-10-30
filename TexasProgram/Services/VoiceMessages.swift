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
/// раз и дальше берётся из памяти. Скорость — 1×, 1,5× или 2×, как в Telegram,
/// и запоминается; доигравшее голосовое передаёт ход следующему.
@Observable
@MainActor
final class VoicePlayer: NSObject, AVAudioPlayerDelegate {
    static let shared = VoicePlayer()
    static let rates: [Float] = [1, 1.5, 2]

    /// Какое голосовое играет и какое скачивается.
    private(set) var playingId: String?
    private(set) var loadingId: String?
    private(set) var failedId: String?
    private(set) var rate: Float = VoicePlayer.savedRate()

    /// Голосовое доиграло до конца — переписка включает следующее.
    @ObservationIgnored var onFinish: ((String) -> Void)?

    @ObservationIgnored private var player: AVAudioPlayer?
    @ObservationIgnored private let cache = NSCache<NSString, NSData>()

    private static func savedRate() -> Float {
        let saved = UserDefaults.standard.float(forKey: "voice_rate")
        return rates.contains(saved) ? saved : 1
    }

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
        } else {
            play(voice)
        }
    }

    /// С начала или с места на волне, от 0 до 1.
    func play(_ voice: SocialVoice, from fraction: Double = 0) {
        stop()
        failedId = nil
        Task { await start(voice, from: fraction) }
    }

    /// Перемотка пальцем по волне: играет — сразу туда, не играет — с этого места.
    func seek(_ voice: SocialVoice, to fraction: Double) {
        let fraction = min(max(fraction, 0), 1)
        if playingId == voice.id, let player {
            player.currentTime = fraction * player.duration
        } else {
            play(voice, from: fraction)
        }
    }

    /// 1× → 1,5× → 2× → 1×.
    func cycleRate() {
        let next = Self.rates.first { $0 > rate + 0.01 } ?? Self.rates[0]
        rate = next
        UserDefaults.standard.set(next, forKey: "voice_rate")
        player?.rate = next
    }

    func stop() {
        player?.stop()
        player = nil
        playingId = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    private func start(_ voice: SocialVoice, from fraction: Double) async {
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
            player.enableRate = true
            player.prepareToPlay()
            player.rate = rate
            if fraction > 0 && fraction < 0.98 { player.currentTime = fraction * player.duration }
            player.play()
            self.player = player
            playingId = voice.id
        } catch {
            failedId = voice.id
        }
    }

    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor in
            guard self.player === player, let id = self.playingId else { return }
            self.stop()
            if flag { self.onFinish?(id) }
        }
    }
}
