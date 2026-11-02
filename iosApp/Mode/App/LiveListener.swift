import AVFoundation
import Observation
import ModeKit
import Speech

/// What talking mode animates with: the microphone's loudness while Mode listens (0…1), and whether Mode is speaking.
@MainActor
@Observable
final class VoiceLevel {
    static let shared = VoiceLevel()
    var level: Double = 0
    var speaking = false
}

/// The loudness the microphone tap measures, read by the main thread (the tap runs on the audio thread).
private final class TapMeter: @unchecked Sendable {
    private let lock = NSLock()
    private var value: Double = 0
    var level: Double {
        get { lock.lock(); defer { lock.unlock() }; return value }
        set { lock.lock(); value = newValue; lock.unlock() }
    }
}

/// One listen from the microphone: Apple transcribes live while the user speaks (partial words for the screen),
/// the same audio is written to a file (for Soniox or Deepgram if Apple hears nothing), and the loudness drives the
/// orb and the bars. It stops on a pause after speech, at the limit, on Done or on cancel.
@MainActor
final class LiveListener {
    static let shared = LiveListener()

    private var engine: AVAudioEngine?
    private var file: AVAudioFile?
    private var url: URL?
    private var input: AsyncStream<AnalyzerInput>.Continuation?
    private var analyzer: SpeechAnalyzer?
    private var transcript: _Concurrency.Task<String, Never>?
    private var done: ((String?, String?) -> Void)?
    private var poll: Timer?
    private var heardSpeech = false
    private var silenceSince: Date?
    private let meter = TapMeter()

    var active: Bool { done != nil }

    func start(maxMs: Int64, silenceMs: Int64, live: Bool, partial: @escaping (String) -> Void, done: @escaping (String?, String?) -> Void) {
        if active { finish(keep: false) }
        self.done = done
        _Concurrency.Task { await self.begin(maxMs: maxMs, silenceMs: silenceMs, live: live, partial: partial) }
    }

    /// Done: what was said so far is kept.
    func finishNow() { if active { finish(keep: true) } }

    func cancel() { if active { finish(keep: false) } }

    private func begin(maxMs: Int64, silenceMs: Int64, live: Bool, partial: @escaping (String) -> Void) async {
        VoiceAudio.shared.forRecording()
        let engine = AVAudioEngine()
        let format = engine.inputNode.outputFormat(forBus: 0)
        guard format.sampleRate > 0, format.channelCount > 0 else { fail(); return }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("mode-listen.wav")
        try? FileManager.default.removeItem(at: url)
        let settings: [String: Any] = [
            AVFormatIDKey: kAudioFormatLinearPCM, AVSampleRateKey: format.sampleRate, AVNumberOfChannelsKey: format.channelCount,
            AVLinearPCMBitDepthKey: 16, AVLinearPCMIsFloatKey: false, AVLinearPCMIsBigEndianKey: false,
        ]
        let file = try? AVAudioFile(forWriting: url, settings: settings, commonFormat: format.commonFormat, interleaved: format.isInterleaved)

        // Apple, live: the analyzer takes audio in its own format, so each buffer is converted first
        // (handing it the microphone's buffers as they are crashes the app).
        var stream: AsyncStream<AnalyzerInput>.Continuation?
        var converter: AVAudioConverter?
        var analyzerFormat: AVAudioFormat?
        if live {
            let locale = await SpeechTranscriber.supportedLocale(equivalentTo: .current) ?? .current
            let transcriber = SpeechTranscriber(locale: locale, transcriptionOptions: [], reportingOptions: [.volatileResults], attributeOptions: [])
            let analyzer = SpeechAnalyzer(modules: [transcriber])
            if let best = await SpeechAnalyzer.bestAvailableAudioFormat(compatibleWith: [transcriber]),
               let conv = AVAudioConverter(from: format, to: best) {
                let (inputs, continuation) = AsyncStream<AnalyzerInput>.makeStream()
                do {
                    try await analyzer.start(inputSequence: inputs)
                    stream = continuation
                    converter = conv
                    analyzerFormat = best
                    self.analyzer = analyzer
                    transcript = _Concurrency.Task {
                        var settled = ""
                        var forming = ""
                        do {
                            for try await result in transcriber.results {
                                let words = String(result.text.characters).trimmingCharacters(in: .whitespaces)
                                if result.isFinal {
                                    if !words.isEmpty { settled += (settled.isEmpty ? "" : " ") + words }
                                    forming = ""
                                } else {
                                    forming = words
                                }
                                let now = [settled, forming].filter { !$0.isEmpty }.joined(separator: " ")
                                await MainActor.run { partial(now) }
                            }
                        } catch {}
                        return [settled, forming].filter { !$0.isEmpty }.joined(separator: " ")
                    }
                } catch {
                    continuation.finish()
                }
            }
        }
        input = stream

        let meter = self.meter
        engine.inputNode.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in
            try? file?.write(from: buffer)
            if let samples = buffer.floatChannelData?[0], buffer.frameLength > 0 {
                var sum: Float = 0
                for i in 0..<Int(buffer.frameLength) { sum += samples[i] * samples[i] }
                let rms = (sum / Float(buffer.frameLength)).squareRoot()
                let db = 20 * log10(max(rms, 0.000_01))
                meter.level = Double(min(max((db + 50) / 40, 0), 1))
            }
            if let stream, let converter, let analyzerFormat,
               let converted = LiveListener.convert(buffer, with: converter, to: analyzerFormat) {
                stream.yield(AnalyzerInput(buffer: converted))
            }
        }
        engine.prepare()
        do { try engine.start() } catch { engine.inputNode.removeTap(onBus: 0); fail(); return }
        self.engine = engine
        self.file = file
        self.url = url
        heardSpeech = false
        silenceSince = nil

        let limit = Date().addingTimeInterval(TimeInterval(maxMs) / 1000)
        poll = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak self] _ in
            _Concurrency.Task { @MainActor in
                guard let self, self.active else { return }
                let level = self.meter.level
                VoiceLevel.shared.level = level
                // About -40 dB: speech. A pause after it ends the listen.
                if level > 0.25 { self.heardSpeech = true; self.silenceSince = nil }
                else if self.heardSpeech {
                    let since = self.silenceSince ?? Date()
                    self.silenceSince = since
                    if Date().timeIntervalSince(since) >= TimeInterval(silenceMs) / 1000 { self.finish(keep: true) }
                }
                if Date() > limit { self.finish(keep: true) }
            }
        }
    }

    private nonisolated static func convert(_ buffer: AVAudioPCMBuffer, with converter: AVAudioConverter, to format: AVAudioFormat) -> AVAudioPCMBuffer? {
        let ratio = format.sampleRate / buffer.format.sampleRate
        let capacity = AVAudioFrameCount((Double(buffer.frameLength) * ratio).rounded(.up)) + 32
        guard let out = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: capacity) else { return nil }
        var given = false
        var error: NSError?
        let status = converter.convert(to: out, error: &error) { _, inputStatus in
            if given { inputStatus.pointee = .noDataNow; return nil }
            given = true
            inputStatus.pointee = .haveData
            return buffer
        }
        return status == .error || error != nil || out.frameLength == 0 ? nil : out
    }

    private func finish(keep: Bool) {
        poll?.invalidate()
        poll = nil
        engine?.inputNode.removeTap(onBus: 0)
        engine?.stop()
        engine = nil
        file = nil
        input?.finish()
        input = nil
        let analyzer = self.analyzer
        let transcript = self.transcript
        self.analyzer = nil
        self.transcript = nil
        let url = self.url
        let spoke = heardSpeech
        let callback = done
        done = nil
        VoiceLevel.shared.level = 0
        VoiceAudio.shared.done()
        _Concurrency.Task {
            if keep { try? await analyzer?.finalizeAndFinishThroughEndOfInput() } else { await analyzer?.cancelAndFinishNow() }
            let text = keep ? await transcript?.value : nil
            transcript?.cancel()
            let heard = (text?.isEmpty == false) ? text : nil
            callback?(keep && (spoke || heard != nil) ? url?.path : nil, heard)
        }
    }

    private func fail() {
        VoiceAudio.shared.done()
        let callback = done
        done = nil
        callback?(nil, nil)
    }
}
