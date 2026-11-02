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
            // Kotlin's Boolean and Long arrive boxed (KotlinBoolean, KotlinLong).
            speak: { [weak self] text, done in self?.speak(text) { done(KotlinBoolean(bool: $0)) } },
            play: { [weak self] bytes, done in self?.play(bytes) { done(KotlinBoolean(bool: $0)) } },
            record: { [weak self] maxMs, silenceMs, done in
                self?.record(maxMs: maxMs.int64Value, silenceMs: silenceMs.int64Value) { _ = done($0) }
            },
            transcribe: { [weak self] path, done in _Concurrency.Task { let text = await self?.transcribe(path); done(text) } },
            deleteRecording: { path in try? FileManager.default.removeItem(atPath: path) },
            cancel: { [weak self] in self?.cancel() },
            prepare: { [weak self] in _Concurrency.Task { await self?.prepareHearing(ask: true) } }
        )
        // Only reads where things stand: the microphone prompt waits until the user turns Apple hearing on.
        _Concurrency.Task { await prepareHearing(ask: false) }
    }

    /// Where Apple hearing stands; with [ask], the microphone prompt and the speech model download too.
    func prepareHearing(ask: Bool) async {
        if ask, AVAudioApplication.shared.recordPermission == .undetermined {
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
            guard ask else { return }
            guard let request = try? await AssetInventory.assetInstallationRequest(supporting: [transcriber]) else { return }
            do { try await request.downloadAndInstall() } catch { return }
        }
        hearing = "ready"
    }

    /// Says [text] and reports when it has finished (true: sound started), so the microphone never opens
    /// while Mode is still talking and a line that started is not said again by another voice.
    private func speak(_ text: String, done: @escaping (Bool) -> Void) {
        started = false
        speakDone = done
        VoiceAudio.shared.forSpeech()
        synth.usesApplicationAudioSession = true
        synth.speak(AVSpeechUtterance(string: text))
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didStart utterance: AVSpeechUtterance) {
        started = true
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        finishSpeaking(true)
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        finishSpeaking(started)
    }

    private func finishSpeaking(_ soundStarted: Bool) {
        guard let done = speakDone else { return }
        speakDone = nil
        VoiceAudio.shared.done()
        done(soundStarted)
    }

    /// Plays a cloud clip to its end; true once sound started.
    private func play(_ bytes: KotlinByteArray, done: @escaping (Bool) -> Void) {
        VoiceAudio.shared.forSpeech()
        player = try? AVAudioPlayer(data: bytes.toData())
        player?.delegate = self
        guard player?.play() == true else { VoiceAudio.shared.done(); done(false); return }
        playDone = done
    }

    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        _Concurrency.Task { @MainActor in self.finishPlaying() }
    }

    nonisolated func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: Error?) {
        _Concurrency.Task { @MainActor in self.finishPlaying() }
    }

    private func finishPlaying() {
        guard let done = playDone else { return }
        playDone = nil
        VoiceAudio.shared.done()
        done(true)
    }

    private func record(maxMs: Int64, silenceMs: Int64, done: @escaping (String?) -> Void) {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("mode-listen.wav")
        try? FileManager.default.removeItem(at: url)
        let settings: [String: Any] = [
            AVFormatIDKey: kAudioFormatLinearPCM, AVSampleRateKey: 16_000, AVNumberOfChannelsKey: 1,
            AVLinearPCMBitDepthKey: 16, AVLinearPCMIsFloatKey: false,
        ]
        // The session must allow recording first: Mode's keep-alive audio is playback only.
        VoiceAudio.shared.forRecording()
        guard let recorder = try? AVAudioRecorder(url: url, settings: settings) else { VoiceAudio.shared.done(); done(nil); return }
        recorder.isMeteringEnabled = true
        guard recorder.record() else { VoiceAudio.shared.done(); done(nil); return }
        self.recorder = recorder
        recordURL = url
        recordDone = done
        heardSpeech = false
        silenceSince = nil
        let limit = Date().addingTimeInterval(TimeInterval(maxMs) / 1000)
        poll = Timer.scheduledTimer(withTimeInterval: 0.2, repeats: true) { [weak self] timer in
            _Concurrency.Task { @MainActor in
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
        VoiceAudio.shared.done()
        let url = recordURL
        let spoke = heardSpeech
        recordDone?(spoke ? url?.path : nil)
        recordDone = nil
    }

    private func cancel() {
        poll?.invalidate()
        let wasRecording = recordDone != nil
        recorder?.stop()
        synth.stopSpeaking(at: .immediate)
        player?.stop()
        recordDone?(nil)
        recordDone = nil
        if let done = playDone { playDone = nil; done(true) }
        if let done = speakDone { speakDone = nil; done(started) }
        if wasRecording || player != nil { VoiceAudio.shared.done() }
        player = nil
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
        do {
            for try await result in transcriber.results { text = String(result.text.characters) }
        } catch {
            return nil
        }
        _ = await running
        return text.isEmpty ? nil : text
    }
}
