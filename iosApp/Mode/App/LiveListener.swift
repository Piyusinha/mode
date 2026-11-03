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

/// Audio for Apple's live transcription. The microphone starts before the analyzer is ready, so its first
/// buffers wait here (up to about 8 s) and are handed over the moment it is; nothing said early is lost.
/// The tap (audio thread) pushes; the main thread attaches and closes.
private final class AnalyzerFeed: @unchecked Sendable {
    private let lock = NSLock()
    private var pending: [AVAudioPCMBuffer] = []
    private var stream: AsyncStream<AnalyzerInput>.Continuation?
    private var converter: AVAudioConverter?
    private var format: AVAudioFormat?
    private var closed = false

    func push(_ buffer: AVAudioPCMBuffer) {
        lock.lock(); defer { lock.unlock() }
        if closed { return }
        if let stream, let converter, let format {
            if let out = LiveListener.convert(buffer, with: converter, to: format) { stream.yield(AnalyzerInput(buffer: out)) }
        } else if pending.count < 400, let copy = AnalyzerFeed.copy(buffer) {
            pending.append(copy)
        }
    }

    func attach(_ stream: AsyncStream<AnalyzerInput>.Continuation, converter: AVAudioConverter, format: AVAudioFormat) {
        lock.lock(); defer { lock.unlock() }
        if closed { stream.finish(); return }
        for buffer in pending {
            if let out = LiveListener.convert(buffer, with: converter, to: format) { stream.yield(AnalyzerInput(buffer: out)) }
        }
        pending = []
        self.stream = stream
        self.converter = converter
        self.format = format
    }

    func close() {
        lock.lock(); defer { lock.unlock() }
        closed = true
        stream?.finish()
        stream = nil
        pending = []
    }

    private static func copy(_ buffer: AVAudioPCMBuffer) -> AVAudioPCMBuffer? {
        guard let out = AVAudioPCMBuffer(pcmFormat: buffer.format, frameCapacity: buffer.frameLength) else { return nil }
        out.frameLength = buffer.frameLength
        let channels = Int(buffer.format.channelCount)
        if let src = buffer.floatChannelData, let dst = out.floatChannelData {
            for c in 0..<channels { dst[c].update(from: src[c], count: Int(buffer.frameLength)) }
        } else if let src = buffer.int16ChannelData, let dst = out.int16ChannelData {
            for c in 0..<channels { dst[c].update(from: src[c], count: Int(buffer.frameLength)) }
        } else {
            return nil
        }
        return out
    }
}

/// One listen from the microphone: Apple transcribes live while the user speaks (partial words for the screen),
/// the same audio is written to a small 16 kHz mono file (what Deepgram or Soniox transcribe when chosen), and the
/// loudness drives the orb and the bars. The microphone opens first; Apple's analyzer catches up with what was said.
/// It stops when the user has finished: a pause against the room's own noise level, or Apple's words standing
/// still for a moment, at the limit, on Done or on cancel.
@MainActor
final class LiveListener {
    static let shared = LiveListener()

    private var engine: AVAudioEngine?
    private var file: AVAudioFile?
    private var url: URL?
    private var feed: AnalyzerFeed?
    private var analyzer: SpeechAnalyzer?
    private var transcript: _Concurrency.Task<String, Never>?
    /// The recording, Apple's words, and why listening stopped (for the trace).
    private var done: ((String?, String?, String) -> Void)?
    private var poll: Timer?
    private var heardSpeech = false
    private var silenceSince: Date?
    /// The room's loudness without speech, learned as the listen goes.
    private var floor: Double = 0.2
    private var words = ""
    private var wordsChangedAt = Date()
    private let meter = TapMeter()

    var active: Bool { done != nil }

    func start(maxMs: Int64, silenceMs: Int64, live: Bool, partial: @escaping (String) -> Void, done: @escaping (String?, String?, String) -> Void) {
        if active { finish(keep: false, reason: "replaced") }
        self.done = done
        _Concurrency.Task { await self.begin(maxMs: maxMs, silenceMs: silenceMs, live: live, partial: partial) }
    }

    /// Done: what was said so far is kept.
    func finishNow() { if active { finish(keep: true, reason: "done") } }

    func cancel() { if active { finish(keep: false, reason: "cancelled") } }

    private func begin(maxMs: Int64, silenceMs: Int64, live: Bool, partial: @escaping (String) -> Void) async {
        VoiceAudio.shared.forRecording()
        let engine = AVAudioEngine()
        let format = engine.inputNode.outputFormat(forBus: 0)
        guard format.sampleRate > 0, format.channelCount > 0 else { fail(); return }

        // The file: 16 kHz mono 16-bit, a third of the microphone's size, so a cloud upload is quick.
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("mode-listen.wav")
        try? FileManager.default.removeItem(at: url)
        let fileFormat = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: 16_000, channels: 1, interleaved: true)
        let fileConverter = fileFormat.flatMap { AVAudioConverter(from: format, to: $0) }
        let file = fileFormat.flatMap { try? AVAudioFile(forWriting: url, settings: $0.settings, commonFormat: .pcmFormatInt16, interleaved: true) }

        let feed = live ? AnalyzerFeed() : nil
        let meter = self.meter
        engine.inputNode.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in
            if let file, let fileConverter, let fileFormat, let small = LiveListener.convert(buffer, with: fileConverter, to: fileFormat) {
                try? file.write(from: small)
            }
            if let samples = buffer.floatChannelData?[0], buffer.frameLength > 0 {
                var sum: Float = 0
                for i in 0..<Int(buffer.frameLength) { sum += samples[i] * samples[i] }
                let rms = (sum / Float(buffer.frameLength)).squareRoot()
                let db = 20 * log10(max(rms, 0.000_01))
                meter.level = Double(min(max((db + 50) / 40, 0), 1))
            }
            feed?.push(buffer)
        }
        engine.prepare()
        do { try engine.start() } catch { engine.inputNode.removeTap(onBus: 0); fail(); return }
        self.engine = engine
        self.file = file
        self.url = url
        self.feed = feed
        heardSpeech = false
        silenceSince = nil
        floor = 0.2
        words = ""
        wordsChangedAt = Date()

        let limit = Date().addingTimeInterval(TimeInterval(maxMs) / 1000)
        poll = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak self] _ in
            _Concurrency.Task { @MainActor in
                guard let self, self.active else { return }
                let level = self.meter.level
                VoiceLevel.shared.level = level
                // The room's floor: falls at once, rises slowly, so speech never becomes the floor.
                self.floor = level < self.floor ? level : self.floor * 0.995 + level * 0.005
                let speaking = level > max(0.25, self.floor + 0.18)
                if speaking { self.heardSpeech = true; self.silenceSince = nil }
                else if self.heardSpeech {
                    let since = self.silenceSince ?? Date()
                    self.silenceSince = since
                    // A thinking pause doesn't end the turn; words that stop mid-sentence wait longer (TurnEnd).
                    let pause = max(TimeInterval(silenceMs), TimeInterval(TurnEnd.shared.pauseMs(words: self.words))) / 1000
                    let quiet = Date().timeIntervalSince(since) >= pause
                    // Apple's words have stopped changing: the user has finished, even in a noisy room.
                    let settle = TimeInterval(TurnEnd.shared.settleMs(words: self.words)) / 1000
                    let settled = !self.words.isEmpty && Date().timeIntervalSince(self.wordsChangedAt) >= settle
                    if quiet || settled { self.finish(keep: true, reason: quiet ? "quiet" : "settled"); return }
                }
                if Date() > limit { self.finish(keep: true, reason: "limit") }
            }
        }

        // Apple, live, set up while the microphone already listens.
        guard let feed else { return }
        let locale = await SpeechTranscriber.supportedLocale(equivalentTo: .current) ?? .current
        let transcriber = SpeechTranscriber(locale: locale, transcriptionOptions: [], reportingOptions: [.volatileResults], attributeOptions: [])
        let analyzer = SpeechAnalyzer(modules: [transcriber])
        guard active, self.feed === feed,
              let best = await SpeechAnalyzer.bestAvailableAudioFormat(compatibleWith: [transcriber]),
              let converter = AVAudioConverter(from: format, to: best) else { return }
        let (inputs, continuation) = AsyncStream<AnalyzerInput>.makeStream()
        do { try await analyzer.start(inputSequence: inputs) } catch { continuation.finish(); return }
        guard active, self.feed === feed else { continuation.finish(); await analyzer.cancelAndFinishNow(); return }
        self.analyzer = analyzer
        transcript = _Concurrency.Task {
            var settled = ""
            var forming = ""
            do {
                for try await result in transcriber.results {
                    let said = String(result.text.characters).trimmingCharacters(in: .whitespaces)
                    if result.isFinal {
                        if !said.isEmpty { settled += (settled.isEmpty ? "" : " ") + said }
                        forming = ""
                    } else {
                        forming = said
                    }
                    let now = [settled, forming].filter { !$0.isEmpty }.joined(separator: " ")
                    await MainActor.run {
                        if now != self.words { self.words = now; self.wordsChangedAt = Date() }
                        partial(now)
                    }
                }
            } catch {}
            return [settled, forming].filter { !$0.isEmpty }.joined(separator: " ")
        }
        feed.attach(continuation, converter: converter, format: best)
    }


    nonisolated static func convert(_ buffer: AVAudioPCMBuffer, with converter: AVAudioConverter, to format: AVAudioFormat) -> AVAudioPCMBuffer? {
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

    private func finish(keep: Bool, reason: String) {
        poll?.invalidate()
        poll = nil
        engine?.inputNode.removeTap(onBus: 0)
        engine?.stop()
        engine = nil
        file = nil
        feed?.close()
        feed = nil
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
            // Done means the person spoke: the recording goes to the listener even if the level never crossed the bar.
            callback?(keep && (spoke || heard != nil || reason == "done") ? url?.path : nil, heard, reason)
        }
    }

    private func fail() {
        VoiceAudio.shared.done()
        let callback = done
        done = nil
        callback?(nil, nil, "failed")
    }
}
