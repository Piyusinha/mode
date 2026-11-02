import AVFoundation
import ModeDesignSystem
import ModeKit
import Speech
import SwiftUI

@main
struct ModeApp: App {
    @State private var app = AppModel()

    init() {
        if !ModeTypography.registerFonts() { NSLog("Mode: Instrument Sans did not load; headings use SF Pro") }
        ModeAppearance.apply()
    }

    var body: some Scene {
        WindowGroup {
            RootView(app: app)
                .task { AppleVoice.shared.install() }
        }
    }
}

/// Apple speech and hearing, plus playback of a cloud clip and one recording.
@MainActor
final class AppleVoice: NSObject, AVSpeechSynthesizerDelegate, AVAudioPlayerDelegate {
    static let shared = AppleVoice()
    private let synth = AVSpeechSynthesizer()
    private var speakDone: ((Bool) -> Void)?
    private var started = false
    private var player: AVAudioPlayer?
    private var playDone: ((Bool) -> Void)?
    private var recorder: AVAudioRecorder?
    private var recordDone: ((String?) -> Void)?
    private var recordURL: URL?
    private var heardSpeech = false
    private var silenceSince: Date?
    private var poll: Timer?
    private var hearing = "permission"

    private override init() {
        super.init()
        synth.delegate = self
    }

    func install() {
        Voices.companion.installReader { path in
            guard let data = FileManager.default.contents(atPath: path) else { return nil }
            let bytes = KotlinByteArray(size: Int32(data.count))
            for (i, byte) in data.enumerated() { bytes.set(index: Int32(i), value: Int8(bitPattern: byte)) }
            return bytes
        }
        DeviceVoice.shared.install(
            speechReady: { true },
            hearingState: { [weak self] in self?.hearing ?? "unsupported" },
            speak: { [weak self] text, done in self?.speak(text, done: done) },
            play: { [weak self] bytes, done in self?.play(bytes, done: done) },
            record: { [weak self] maxMs, silenceMs, done in self?.record(maxMs: maxMs, silenceMs: silenceMs, done: done) },
            transcribe: { [weak self] path, done in Task { let text = await self?.transcribe(path); done(text) } },
            deleteRecording: { path in try? FileManager.default.removeItem(atPath: path) },
            cancel: { [weak self] in self?.cancel() }
        )
        Task { await prepareHearing() }
    }

    func prepareHearing() async {
        if AVAudioApplication.shared.recordPermission == .undetermined {
            _ = await AVAudioApplication.requestRecordPermission()
        }
        switch AVAudioApplication.shared.recordPermission {
        case .denied: hearing = "denied"; return
        case .undetermined: hearing = "permission"; return
        default: break
        }
        guard SpeechTranscriber.isAvailable else { hearing = "unsupported"; return }
        let locale = await SpeechTranscriber.supportedLocale(equivalentTo: .current) ?? Locale.current
        let installed = await SpeechTranscriber.installedLocales
        let transcriber = SpeechTranscriber(locale: locale, preset: .transcription)
        if !installed.contains(where: { $0.identifier(.bcp47) == locale.identifier(.bcp47) }) {
            hearing = "asset"
            if let request = try? await AssetInventory.assetInstallationRequest(supporting: [transcriber]) {
                try? await request.downloadAndInstall()
            }
        }
        hearing = "ready"
    }

    private func speak(_ text: String, done: @escaping (Bool) -> Void) {
        started = false
        speakDone = done
        synth.speak(AVSpeechUtterance(string: text))
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didStart utterance: AVSpeechUtterance) {
        started = true
        speakDone?(true)
        speakDone = nil
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        if !started { speakDone?(false) }
        speakDone = nil
    }

    private func play(_ bytes: KotlinByteArray, done: @escaping (Bool) -> Void) {
        player = try? AVAudioPlayer(data: bytes.toData())
        player?.delegate = self
        guard player?.play() == true else { done(false); return }
        done(true)
    }

    private func record(maxMs: Int64, silenceMs: Int64, done: @escaping (String?) -> Void) {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("mode-listen.wav")
        try? FileManager.default.removeItem(at: url)
        let settings: [String: Any] = [
            AVFormatIDKey: kAudioFormatLinearPCM, AVSampleRateKey: 16_000, AVNumberOfChannelsKey: 1,
            AVLinearPCMBitDepthKey: 16, AVLinearPCMIsFloatKey: false,
        ]
        guard let recorder = try? AVAudioRecorder(url: url, settings: settings) else { done(nil); return }
        self.recorder = recorder
        recordURL = url
        recordDone = done
        heardSpeech = false
        silenceSince = nil
        recorder.isMeteringEnabled = true
        recorder.record()
        let limit = Date().addingTimeInterval(TimeInterval(maxMs) / 1000)
        poll = Timer.scheduledTimer(withTimeInterval: 0.2, repeats: true) { [weak self] timer in
            Task { @MainActor in
                guard let self else { return }
                recorder.updateMeters()
                let power = recorder.averagePower(forChannel: 0)
                if power > -40 { self.heardSpeech = true; self.silenceSince = nil }
                else if self.heardSpeech {
                    let since = self.silenceSince ?? Date()
                    self.silenceSince = since
                    if Date().timeIntervalSince(since) >= TimeInterval(silenceMs) / 1000 { timer.invalidate(); self.finishRecord() }
                }
                if Date() > limit { timer.invalidate(); self.finishRecord() }
            }
        }
    }

    private func finishRecord() {
        poll?.invalidate()
        recorder?.stop()
        let url = recordURL
        let spoke = heardSpeech
        recordDone?(spoke ? url?.path : nil)
        recordDone = nil
    }

    private func cancel() {
        poll?.invalidate()
        recorder?.stop()
        synth.stopSpeaking(at: .immediate)
        player?.stop()
        recordDone?(nil)
        recordDone = nil
        if !started { speakDone?(false) }
        speakDone = nil
    }

    private func transcribe(_ path: String) async -> String? {
        let locale = await SpeechTranscriber.supportedLocale(equivalentTo: .current) ?? Locale.current
        let transcriber = SpeechTranscriber(locale: locale, preset: .transcription)
        let analyzer = SpeechAnalyzer(modules: [transcriber])
        guard let file = try? AVAudioFile(forReading: URL(fileURLWithPath: path)) else { return nil }
        let stream = AsyncStream<AnalyzerInput> { continuation in
            while true {
                guard let buffer = AVAudioPCMBuffer(pcmFormat: file.processingFormat, frameCapacity: 4096) else { break }
                guard (try? file.read(into: buffer)) != nil, buffer.frameLength > 0 else { break }
                continuation.yield(AnalyzerInput(buffer: buffer))
            }
            continuation.finish()
        }
        async let running: Void = { try? await analyzer.analyzeSequence(stream) }()
        var text = ""
        for try await result in transcriber.results { text = String(result.text.characters) }
        _ = await running
        return text.isEmpty ? nil : text
    }
}
