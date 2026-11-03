import ModeDesignSystem
import ModeKit
import Observation
import SwiftUI
import UIKit

/// Talking mode (boards "Talking mode": allow the microphone, listening, starting, Mode asks, back to work):
/// one full screen from "Start Mode" to done, and for a question in a spoken task when Mode comes forward.
@MainActor
@Observable
final class TalkModel {
    struct State: Equatable {
        var phase: TalkPhase = .greeting
        var greeting = ""
        /// Live words: the settled part and the word still forming.
        var settled = ""
        var forming = ""
        var task = ""
        var reply = ""
        var working = ""
        var question = ""
        var options: [String] = []
        var answer = ""
        var answerReply = ""
        var micDenied = false
        var finished = false
    }

    private(set) var state = State()
    var typed = ""
    /// Answering a question by typing (Type, beside the mic).
    var typingAnswer = false
    /// The microphone's loudness and whether Mode is talking, for the orb and the bars.
    var level: Double { VoiceLevel.shared.level }
    var speaking: Bool { VoiceLevel.shared.speaking }
    private let presenter: ListenPresenter
    private let forQuestion: Bool
    private var updates: Updates?

    init(screens: Screens, forQuestion: Bool) {
        self.forQuestion = forQuestion
        presenter = forQuestion ? screens.talkQuestion() : screens.listen()
        updates = observe(presenter.state) { [weak self] s in self?.state = Self.map(s) }
    }

    func begin() { if !forQuestion { presenter.begin() } }
    func allowMic() { presenter.allowMic() }
    func done() { presenter.finish() }
    func typeInstead() {
        if state.phase == .asking || state.phase == .answering { typingAnswer = true; presenter.later() } else { presenter.typeInstead() }
    }
    func send() {
        let text = typed
        typed = ""
        if typingAnswer { typingAnswer = false; presenter.answer(option: text) } else { presenter.submitTyped(text: text) }
    }
    func answer(_ option: String) { presenter.answer(option: option) }
    func later() { presenter.later() }
    func stop() { presenter.stop() }
    func dismiss() { presenter.dismiss() }

    isolated deinit {
        updates?.cancel()
        presenter.close()
    }

    private static func map(_ s: ListenUiState) -> State {
        // The newest word is still forming: it shows lighter until the next one comes.
        let words = s.partial.split(separator: " ").map(String.init)
        return State(
            phase: s.phase, greeting: s.greeting,
            settled: words.dropLast().joined(separator: " "), forming: words.last ?? "",
            task: s.task, reply: s.reply, working: s.working, question: s.question, options: s.options,
            answer: s.answer, answerReply: s.answerReply, micDenied: s.micDenied, finished: s.finished
        )
    }
}

struct TalkScreen: View {
    let state: TalkModel.State
    let level: Double
    let speaking: Bool
    let typingAnswer: Bool
    @Binding var typed: String
    let onAllowMic: () -> Void
    let onDone: () -> Void
    let onType: () -> Void
    let onSend: () -> Void
    let onAnswer: (String) -> Void
    let onLater: () -> Void
    let onStop: () -> Void
    let onClose: () -> Void
    @FocusState private var typing: Bool

    private var listening: Bool { (state.phase == .listening || state.phase == .answering) && !typingAnswer }

    var body: some View {
        VStack(spacing: ModeSpacing.l) {
            header
            ScrollView {
                VStack(spacing: ModeSpacing.l) { content }
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, ModeSpacing.screenEdge)
            }
            .scrollBounceBehavior(.basedOnSize)
            dock
        }
        .padding(.bottom, ModeSpacing.s)
        .modeScreen()
        .onChange(of: state.phase) { _, phase in typing = phase == .typing }
        .onChange(of: typingAnswer) { _, on in typing = on }
    }

    // MARK: Header: close, what is happening, type

    private var header: some View {
        HStack {
            ModeIconButton(.close, label: L10n.Talk.close, action: onClose)
            Spacer()
            HStack(spacing: ModeSpacing.s) {
                if let dot = statusDot { Circle().fill(dot).frame(width: ModeSize.liveDot, height: ModeSize.liveDot) }
                Text(statusTitle).modeText(.subheadline).fontWeight(.semibold)
            }
            .padding(.horizontal, ModeSpacing.m)
            .padding(.vertical, ModeSpacing.xs)
            .background(ModeColor.card, in: .capsule)
            Spacer()
            if listening || state.phase == .greeting || state.phase == .asking {
                ModeIconButton(.keyboard, label: L10n.Talk.type, action: onType)
            } else {
                Color.clear.frame(width: ModeSize.touch, height: ModeSize.touch)
            }
        }
        .padding(.horizontal, ModeSpacing.screenEdge)
    }

    private var statusTitle: LocalizedStringResource {
        switch state.phase {
        case .needsMic: L10n.Talk.startMode
        case .greeting: L10n.Talk.speaking
        case .listening: L10n.Talk.listening
        case .starting: L10n.Talk.working
        case .asking, .answering: L10n.Talk.needsYou
        case .answered: L10n.Talk.gotIt
        default: L10n.Talk.typeIt
        }
    }

    private var statusDot: Color? {
        switch state.phase {
        case .listening: ModeColor.stopRed
        case .asking, .answering: ModeColor.warning
        case .starting: ModeColor.success
        default: nil
        }
    }

    // MARK: Content, by phase

    @ViewBuilder private var content: some View {
        switch state.phase {
        case .needsMic: needsMic
        case .greeting, .listening: listen
        case .starting: starting
        case .asking, .answering: question
        case .answered: answered
        default: typeBox(L10n.Talk.typeTask)
        }
    }

    private var needsMic: some View {
        VStack(spacing: ModeSpacing.l) {
            ModeTalkOrb(level: 0, speaking: false, size: ModeSize.talkOrb * 0.85, showsRing: false, icon: .microphone)
                .padding(.vertical, ModeSpacing.xxl)
            Text(L10n.Talk.micTitle).modeText(.title).multilineTextAlignment(.center)
            Text(state.micDenied ? L10n.Talk.micDenied : L10n.Talk.micMessage)
                .modeText(.callout, color: ModeColor.textSecondary).multilineTextAlignment(.center)
            if !state.micDenied {
                ModeRow(String(localized: L10n.Talk.micModel), leading: VoiceService.apple.tile) { EmptyView() }
                    .padding(.horizontal, ModeSpacing.l)
                    .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.field))
            }
        }
    }

    private var listen: some View {
        VStack(spacing: ModeSpacing.l) {
            if !state.greeting.isEmpty {
                // Mode said this out loud as it opened; it stays as a quiet caption.
                HStack(alignment: .firstTextBaseline, spacing: ModeSpacing.xs) {
                    ModeIcon.speaker.image.foregroundStyle(ModeColor.tint).accessibilityHidden(true)
                    Text(state.greeting).modeText(.subheadline, color: ModeColor.textSecondary)
                }
                .multilineTextAlignment(.center)
            }
            ModeTalkOrb(level: level, speaking: speaking || state.phase == .greeting, size: ModeSize.talkOrb)
                .padding(.vertical, ModeSpacing.xxl)
            ModeTalkWave(level: level, speaking: speaking || state.phase == .greeting)
            if state.phase == .listening {
                ModeLiveLabel(String(localized: L10n.Talk.hearingLive))
                liveWords(placeholder: L10n.Talk.sayIt)
                Text(L10n.Talk.wordsAppear).modeText(.subheadline, color: ModeColor.textSecondary).multilineTextAlignment(.center)
            } else {
                Text(L10n.Talk.micOpensAfter).modeText(.subheadline, color: ModeColor.textSecondary).multilineTextAlignment(.center)
            }
        }
    }

    private var starting: some View {
        VStack(spacing: ModeSpacing.l) {
            ModeTalkOrb(level: 0, speaking: speaking, size: ModeSize.talkOrbSmall, showsRing: false).padding(.vertical, ModeSpacing.l)
            userBubble(state.task)
            modeSays(state.reply)
            HStack(spacing: ModeSpacing.m) {
                ModeMark(size: ModeSize.badge + ModeSpacing.xs)
                VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                    Text(L10n.Talk.workingOnIt).modeText(.bodyEmphasis)
                    Text(state.working.isEmpty ? String(localized: L10n.Talk.inBackground) : state.working)
                        .modeText(.footnote, color: ModeColor.textSecondary).lineLimit(2)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                ProgressView().tint(ModeColor.tint)
            }
            .padding(ModeSpacing.l)
            .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.field))
            Text(L10n.Talk.ifNeeded).modeText(.subheadline, color: ModeColor.textSecondary).multilineTextAlignment(.center)
        }
    }

    private var question: some View {
        VStack(spacing: ModeSpacing.l) {
            ModeTalkOrb(level: level, speaking: speaking || state.phase == .asking, size: ModeSize.talkOrbSmall).padding(.vertical, ModeSpacing.l)
            if !state.task.isEmpty {
                Text(state.task).modeText(.footnote, color: ModeColor.textSecondary).lineLimit(1)
            }
            Text(state.question).modeText(.title).multilineTextAlignment(.center)
            if !state.options.isEmpty {
                ModeChipFlow {
                    ForEach(state.options, id: \.self) { option in
                        Button { onAnswer(option) } label: {
                            Text(option).modeText(.body).fontWeight(.semibold)
                                .padding(.horizontal, ModeSpacing.l)
                                .frame(minHeight: ModeSize.touch + ModeSpacing.xs)
                                .glassEffect(.regular.interactive(), in: .capsule)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            if typingAnswer {
                typeBox(L10n.Talk.typeAnswer)
            } else {
                ModeTalkWave(level: level, speaking: speaking, bars: 26)
                if state.phase == .answering { liveWords(placeholder: nil) }
                Text(state.options.isEmpty ? L10n.Talk.sayAnswer : L10n.Talk.sayOrTap).modeText(.subheadline, color: ModeColor.textSecondary)
            }
        }
    }

    private var answered: some View {
        VStack(spacing: ModeSpacing.l) {
            ModeTalkOrb(level: 0, speaking: speaking, size: ModeSize.talkOrbSmall, showsRing: false).padding(.vertical, ModeSpacing.l)
            userBubble(state.answer)
            modeSays(state.answerReply)
            Text(L10n.Talk.leavesSoon).modeText(.subheadline, color: ModeColor.textSecondary).multilineTextAlignment(.center)
        }
    }

    // MARK: Pieces

    /// The words heard so far, the newest one lighter, then a cursor.
    @ViewBuilder private func liveWords(placeholder: LocalizedStringResource?) -> some View {
        if state.settled.isEmpty && state.forming.isEmpty {
            if let placeholder { Text(placeholder).modeText(.title, color: ModeColor.textSecondary).multilineTextAlignment(.center) }
        } else {
            (Text(state.settled.isEmpty ? "" : state.settled + " ").foregroundStyle(ModeColor.textPrimary)
             + Text(state.forming).foregroundStyle(ModeColor.textSecondary)
             + Text(verbatim: "▏").foregroundStyle(ModeColor.accent))
                .modeText(.title)
                .multilineTextAlignment(.center)
                .contentTransition(.opacity)
                .animation(ModeMotion.quick, value: state.forming)
        }
    }

    private func userBubble(_ text: String) -> some View {
        Text(text).modeText(.body, color: ModeColor.onAccent).fontWeight(.semibold)
            .padding(.horizontal, ModeSpacing.l)
            .padding(.vertical, ModeSpacing.m)
            .background(ModeColor.accent, in: .rect(cornerRadius: ModeRadius.field))
            .frame(maxWidth: .infinity, alignment: .trailing)
    }

    private func modeSays(_ text: String) -> some View {
        HStack(alignment: .top, spacing: ModeSpacing.m) {
            ModeIcon.speaker.image.foregroundStyle(ModeColor.tint)
                .frame(width: ModeSize.badge, height: ModeSize.badge)
                .background(ModeColor.tintSoft, in: .circle)
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(speaking ? L10n.Talk.modeSpeaking : L10n.Talk.modeLabel).modeText(.captionEmphasis, color: ModeColor.tint)
                Text(text).modeText(.bodyEmphasis)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if speaking { ModeWaveBars() }
        }
        .padding(ModeSpacing.l)
        .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.field))
    }

    private func typeBox(_ prompt: LocalizedStringResource) -> some View {
        TextField(text: $typed, axis: .vertical) { Text(prompt) }
            .modeText(.title2)
            .multilineTextAlignment(.center)
            .focused($typing)
            .submitLabel(.send)
            .onSubmit(onSend)
            .padding(ModeSpacing.l)
            .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.field))
    }

    // MARK: Dock

    @ViewBuilder private var dock: some View {
        Group {
            switch state.phase {
            case .needsMic:
                VStack(spacing: ModeSpacing.m) {
                    if state.micDenied, let settings = URL(string: UIApplication.openSettingsURLString) {
                        Link(destination: settings) {
                            Text(L10n.Talk.openSettings).modeText(.body, color: ModeColor.onAccent).fontWeight(.semibold)
                                .frame(maxWidth: .infinity, minHeight: ModeSize.touch + ModeSpacing.m)
                                .background(ModeColor.accent, in: .capsule)
                        }
                    } else {
                        ModeButton(L10n.Talk.allowMic, icon: .microphone, fullWidth: true, action: onAllowMic)
                    }
                    ModeButton(L10n.Talk.typeInstead, role: .plain, action: onType)
                }
            case .greeting, .listening:
                HStack(spacing: ModeSpacing.l) {
                    ModeTalkSideButton(.keyboard, label: L10n.Talk.type, action: onType)
                    ModeTalkMicButton(listening: state.phase == .listening, label: L10n.Talk.done, action: onDone)
                    ModeTalkSideButton(.close, label: L10n.Talk.cancel, action: onClose)
                }
            case .asking, .answering:
                if typingAnswer {
                    ModeButton(L10n.Talk.send, fullWidth: true, action: onSend).disabled(typed.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                } else {
                    HStack(spacing: ModeSpacing.l) {
                        ModeTalkSideButton(.keyboard, label: L10n.Talk.type, action: onType)
                        ModeTalkMicButton(listening: state.phase == .answering, label: L10n.Talk.done, action: onDone)
                        ModeTalkSideButton(.arrowRight, label: L10n.Talk.later, action: onLater)
                    }
                }
            case .starting:
                ModeButton(L10n.Talk.stop, role: .secondary, fullWidth: true, action: onStop)
            case .answered:
                EmptyView()
            default:
                ModeButton(L10n.Talk.send, fullWidth: true, action: onSend).disabled(typed.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
        }
        .padding(.horizontal, ModeSpacing.screenEdge)
    }
}

/// Opens talking mode and closes it: when the user closes it, when a spoken task finishes, or (for a question)
/// when MainTabs stops showing it.
struct TalkRoute: View {
    @State private var model: TalkModel
    private let onClose: () -> Void
    @Environment(\.dismiss) private var dismiss

    init(app: AppModel, forQuestion: Bool, onClose: @escaping () -> Void) {
        _model = State(initialValue: TalkModel(screens: app.screens, forQuestion: forQuestion))
        self.onClose = onClose
    }

    var body: some View {
        TalkScreen(
            state: model.state, level: model.level, speaking: model.speaking, typingAnswer: model.typingAnswer, typed: $model.typed,
            onAllowMic: model.allowMic, onDone: model.done, onType: model.typeInstead, onSend: model.send,
            onAnswer: model.answer, onLater: { model.later(); close() }, onStop: { model.stop(); close() }, onClose: close
        )
        .task { model.begin() }
        .onChange(of: model.state.finished) { _, finished in if finished { close() } }
    }

    private func close() {
        model.dismiss()
        onClose()
        dismiss()
    }
}
