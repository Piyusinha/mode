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
        /// The conversation so far: what they asked, what Mode said, and each task's card.
        var turns: [Turn] = []
        /// A task was done in this conversation: "That's all" ends it.
        var hasDone = false
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
        /// "Mode couldn't hear with Deepgram": the chosen listener or voice failed (Update in Settings).
        var voiceNotice: String?
        var voiceNoticeHearing = false
        /// Why, in a sentence ("Deepgram didn't answer, so Mode didn't start anything.").
        var voiceNoticeMessage: String?
        /// The user chose to type after "Mode couldn't hear you".
        var noticeSeen = false
        /// The option the words heard so far point to, while answering.
        var heardOption: String?
    }

    private(set) var state = State()
    var typed = ""
    /// Answering a question by typing (Type, beside the mic).
    var typingAnswer = false
    /// The microphone's loudness and whether Mode is talking, for the orb and the bars.
    var level: Double { VoiceLevel.shared.level }
    var speaking: Bool { VoiceLevel.shared.speaking }
    /// No speech heard for a while: the screen asks whether the microphone is on.
    var cantHear: Bool { VoiceLevel.shared.cantHear }
    private let presenter: ListenPresenter
    private let forQuestion: Bool
    private var updates: Updates?
    private var noticeSeen = false

    init(screens: Screens, forQuestion: Bool, conversationId: String? = nil, entry: String = "siri") {
        self.forQuestion = forQuestion
        presenter = forQuestion ? screens.talkQuestion() : screens.listen(conversationId: conversationId, entry: entry)
        updates = observe(presenter.state) { [weak self] s in
            guard let self else { return }
            var mapped = Self.map(s)
            mapped.noticeSeen = self.noticeSeen
            self.state = mapped
        }
    }

    func begin() { if !forQuestion { presenter.begin() } }
    /// "Mode couldn't hear you" → Try again: listen again from the start.
    func tryAgain() { noticeSeen = false; presenter.begin() }
    func allowMic() { presenter.allowMic() }
    func done() { presenter.finish() }
    func typeInstead() {
        if state.voiceNotice != nil && state.phase == .typing { noticeSeen = true; state.noticeSeen = true; return }
        if state.phase == .asking || state.phase == .answering { typingAnswer = true; presenter.later() } else { presenter.typeInstead() }
    }
    func send() {
        let text = typed
        typed = ""
        if typingAnswer { typingAnswer = false; presenter.answer(option: text) } else { presenter.submitTyped(text: text) }
    }
    func answer(_ option: String) { presenter.answer(option: option) }
    /// One of Mode's offered answers ("Yes, look there"), as if said.
    func pick(_ answer: String) { presenter.pick(answer: answer) }
    func thatsAll() { presenter.thatsAll() }
    func later() { presenter.later() }
    func stop() { presenter.stop() }
    /// End: everything stops now (listening, speaking, the task from here) and the conversation closes.
    func endNow() { presenter.endNow() }
    func dismiss() { presenter.dismiss() }

    isolated deinit {
        updates?.cancel()
        presenter.close()
    }

    /// The option that the words heard so far start, if any ("Large" → "Large · ₹399").
    private static func heard(_ words: String, in options: [String]) -> String? {
        let heard = words.trimmingCharacters(in: .whitespaces).lowercased()
        guard !heard.isEmpty else { return nil }
        return options.first { $0.lowercased().hasPrefix(heard) }
    }

    struct Turn: Identifiable, Equatable {
        enum Kind: Equatable { case said, next, task }
        let id: Int
        let mine: Bool
        let kind: Kind
        let text: String
        var card: Card?
        /// Mode's offer: the yes and no answers to tap.
        var yes: String?
        var no: String?
    }

    struct Card: Equatable {
        let taskId: String
        let conversationId: String?
        let status: ModeTalkTaskCard.Status
        let title: String
        let startedAt: Date
        let seconds: Int
        let steps: [ModeTalkTaskCard.Step]
        let doneParts: Int
        let parts: Int
        let headline: String
        let rows: [ModeTalkTaskCard.Row]
    }

    private static func turn(_ t: TalkTurn) -> Turn {
        let kind: Turn.Kind = switch t.kind {
        case .next: .next
        case .task: .task
        default: .said
        }
        return Turn(id: Int(t.id), mine: t.mine, kind: kind, text: t.text, card: t.card.map(card), yes: t.offer?.yes, no: t.offer?.no)
    }

    private static func card(_ c: TalkTaskCard) -> Card {
        let status: ModeTalkTaskCard.Status = switch c.state {
        case .done: .done
        case .partial: .partial
        case .failed: .failed
        case .stopped: .stopped
        default: .working
        }
        return Card(
            taskId: c.taskId, conversationId: c.conversationId, status: status, title: c.title,
            startedAt: Date(timeIntervalSince1970: TimeInterval(c.startedAtMs) / 1000), seconds: Int(c.seconds),
            steps: c.steps.enumerated().map { ModeTalkTaskCard.Step(id: $0.offset, text: $0.element.text, time: $0.element.time) },
            doneParts: Int(c.doneParts), parts: Int(c.parts), headline: c.headline,
            rows: c.rows.enumerated().map { ModeTalkTaskCard.Row(id: $0.offset, title: $0.element.title, detail: $0.element.detail, done: $0.element.done) }
        )
    }

    private static func map(_ s: ListenUiState) -> State {
        // The newest word is still forming: it shows lighter until the next one comes.
        let words = s.partial.split(separator: " ").map(String.init)
        return State(
            phase: s.phase, greeting: s.greeting,
            turns: s.turns.map(turn), hasDone: !s.doneSoFar.isEmpty,
            settled: words.dropLast().joined(separator: " "), forming: words.last ?? "",
            task: s.task, reply: s.reply, working: s.working, question: s.question, options: s.options,
            answer: s.answer, answerReply: s.answerReply, micDenied: s.micDenied, finished: s.finished,
            voiceNotice: s.voiceNotice.map { n in
                let provider = VoiceService(id: n.provider)?.title ?? n.provider
                return n.hearing ? L10n.Voice.couldntHearWith(provider) : L10n.Voice.couldntSpeakWith(provider)
            },
            voiceNoticeHearing: s.voiceNotice?.hearing ?? false,
            voiceNoticeMessage: s.voiceNotice.map { n in
                VoiceTroubleText.long(n.trouble, provider: VoiceService(id: n.provider)?.title ?? n.provider, hearing: n.hearing)
            },
            heardOption: heard(s.partial, in: s.options)
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
    /// End, always on screen (top left, and in the dock when nothing else is there): stops everything at once.
    var onEnd: () -> Void = {}
    /// The voice notice's Update in Settings (closes talking mode first).
    var onVoiceSettings: () -> Void = {}
    /// Listening hears nothing: a small warning at the top.
    var cantHear = false
    /// Mode couldn't hear with the chosen listener: listen again.
    var onTryAgain: () -> Void = {}
    /// The conversation: an offered answer tapped, That's all, a task's chat, and What went wrong.
    var onPick: (String) -> Void = { _ in }
    var onThatsAll: () -> Void = {}
    var onOpenChat: (String) -> Void = { _ in }
    var onWhatWentWrong: (String) -> Void = { _ in }
    @FocusState private var typing: Bool

    private var listening: Bool { (state.phase == .listening || state.phase == .answering) && !typingAnswer }
    /// Hearing failed and nothing started: the "Mode couldn't hear you" page, until the user types instead.
    private var hearingFailed: Bool { state.phase == .typing && state.voiceNotice != nil && state.voiceNoticeHearing && !state.noticeSeen }

    var body: some View {
        VStack(spacing: ModeSpacing.l) {
            header
            if let notice = state.voiceNotice, !hearingFailed {
                ModeVoiceNotice(notice, actionTitle: L10n.Voice.updateInSettings, action: onVoiceSettings)
                    .padding(.horizontal, ModeSpacing.screenEdge)
            } else if listening && cantHear {
                ModeInfoCard(icon: .warning, L10n.Talk.cantHear)
                    .padding(.horizontal, ModeSpacing.screenEdge)
                    .transition(.opacity)
            }
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(spacing: ModeSpacing.l) { content }
                        .frame(maxWidth: .infinity)
                        .padding(.horizontal, ModeSpacing.xl)
                }
                .scrollBounceBehavior(.basedOnSize)
                // A conversation opens at its newest line and stays there as cards grow.
                .defaultScrollAnchor(inConversation ? .bottom : .top)
                .defaultScrollAnchor(.bottom, for: .sizeChanges)
                // The newest line stays in view: a new line, a card's new step or result, the next phase (the
                // dock above Stop grows while Mo works), and words as they're heard.
                .onChange(of: state.turns) { _, _ in toEnd(proxy) }
                .onChange(of: state.phase) { _, _ in toEnd(proxy) }
                .onChange(of: state.forming) { _, _ in toEnd(proxy) }
                .onAppear { toEnd(proxy) }
            }
            dock
        }
        .padding(.bottom, ModeSpacing.l)
        .modeScreen()
        .onChange(of: state.phase) { _, phase in typing = phase == .typing && !hearingFailed }
        .onChange(of: typingAnswer) { _, on in typing = on }
    }

    // MARK: Header: close, what is happening, type

    private var header: some View {
        HStack {
            ModeIconButton(.close, label: L10n.Talk.end, action: onEnd)
            Spacer()
            if !hearingFailed { status }
            Spacer()
            if listening || state.phase == .greeting || state.phase == .asking {
                ModeIconButton(.keyboard, label: L10n.Talk.type, action: onType)
            } else {
                Color.clear.frame(width: ModeSize.touch, height: ModeSize.touch)
            }
        }
        .padding(.horizontal, ModeSpacing.screenEdge)
        .padding(.top, ModeSpacing.s)
    }

    @ViewBuilder private var status: some View {
        switch state.phase {
        case .needsMic: ModeTalkStatus(L10n.Talk.startMode, dot: nil)
        case .greeting: ModeTalkStatus(L10n.Talk.speaking, dot: ModeColor.brand)
        case .listening: ModeTalkStatus(L10n.Talk.listening, dot: ModeColor.danger)
        case .starting: ModeTalkStatus(inConversation ? L10n.Talk.workingMicOff : L10n.Talk.starting, dot: ModeColor.brand)
        case .asking, .answering: ModeTalkStatus(L10n.Talk.needsYou, dot: ModeColor.info, tone: .info)
        case .answered: ModeTalkStatus(L10n.Talk.gotIt, dot: nil, tone: .success)
        default: ModeTalkStatus(L10n.Talk.typeIt, dot: nil)
        }
    }

    // MARK: Content, by phase

    /// They've asked for something: the screen is the conversation, with what's happening now at the bottom.
    private var inConversation: Bool {
        state.turns.contains { $0.mine } && [.greeting, .listening, .starting, .typing].contains(state.phase) && !hearingFailed
    }

    private static let now = "now"

    /// Scrolls to the newest line once the layout has settled (the dock may have just grown).
    private func toEnd(_ proxy: ScrollViewProxy) {
        guard inConversation else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + ModeMotion.settle) {
            withAnimation(ModeMotion.standard) { proxy.scrollTo(Self.now, anchor: .bottom) }
        }
    }

    @ViewBuilder private var content: some View {
        if inConversation { conversation } else { page }
    }

    private var conversation: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.m) {
            ForEach(state.turns) { turn in
                switch turn.kind {
                case .said:
                    if turn.mine { ModeTalkBubble(turn.text) } else { ModeTalkMoLine(turn.text) }
                case .next:
                    let offer = turn.id == state.turns.last?.id && [.greeting, .listening].contains(state.phase)
                    ModeTalkNext(turn.text, yes: offer ? turn.yes : nil, no: offer ? turn.no : nil,
                                 onYes: { if let yes = turn.yes { onPick(yes) } }, onNo: { if let no = turn.no { onPick(no) } })
                case .task:
                    if let card = turn.card { taskCard(card) }
                }
            }
            now.padding(.top, ModeSpacing.xs)
            // Always there (what's happening now may draw nothing): where scrolling to the newest line lands.
            Color.clear.frame(height: ModeSize.talkDivider).id(Self.now)
        }
        .padding(.top, ModeSpacing.s)
    }

    /// A task's card: while it works the header counts up every second.
    @ViewBuilder private func taskCard(_ card: TalkModel.Card) -> some View {
        if card.status == .working {
            TimelineView(.periodic(from: card.startedAt, by: 1)) { context in
                cardView(card, label: L10n.Talk.cardWorking(Self.clock(Int(context.date.timeIntervalSince(card.startedAt)))))
            }
        } else {
            cardView(card, label: Self.label(card))
        }
    }

    private func cardView(_ card: TalkModel.Card, label: String) -> some View {
        ModeTalkTaskCard(
            status: card.status, label: label, title: card.title, chip: Self.chip(card), steps: card.steps,
            resultLabel: L10n.Talk.result, headline: card.headline, rows: card.rows,
            openLabel: L10n.Talk.openChat, wrongLabel: L10n.Talk.whatWentWrong,
            onOpen: { if let id = card.conversationId { onOpenChat(id) } }, onWrong: { onWhatWentWrong(card.taskId) }
        )
    }

    /// "1:12", while it works.
    private static func clock(_ seconds: Int) -> String {
        let s = max(seconds, 0)
        return "\(s / 60):" + String(format: "%02d", s % 60)
    }

    /// "DONE · 1 MIN 48 S", "PARTLY DONE · 2 MIN 10 S", once it ended.
    private static func label(_ card: TalkModel.Card) -> String {
        let took = card.seconds >= 60 ? L10n.Talk.tookMinutes(card.seconds / 60, card.seconds % 60) : L10n.Talk.tookSeconds(card.seconds)
        return switch card.status {
        case .done: L10n.Talk.cardDone(took)
        case .partial: L10n.Talk.cardPartial(took)
        case .failed: L10n.Talk.cardFailed(took)
        case .stopped: String(localized: L10n.Talk.cardStopped)
        case .working: L10n.Talk.cardWorking(clock(card.seconds))
        }
    }

    /// "2 of 3" while it works (its plan), "Done", or "1 of 2" parts done.
    private static func chip(_ card: TalkModel.Card) -> String? {
        switch card.status {
        case .working: card.parts > 1 ? L10n.Talk.partsOf(min(card.doneParts + 1, card.parts), card.parts) : nil
        case .done: String(localized: L10n.Talk.chipDone)
        case .partial: card.rows.isEmpty ? nil : L10n.Talk.partsOf(card.rows.filter(\.done).count, card.rows.count)
        default: nil
        }
    }

    /// The live part at the bottom of the conversation.
    @ViewBuilder private var now: some View {
        switch state.phase {
        case .greeting, .listening:
            VStack(spacing: ModeSpacing.m) {
                if state.phase == .listening && !(state.settled.isEmpty && state.forming.isEmpty) {
                    liveWords(placeholder: nil, style: .liveWords, cursor: ModeColor.brand)
                }
                ModeTalkWave(level: level, speaking: speaking || state.phase == .greeting)
            }
            .frame(maxWidth: .infinity)
        case .starting:
            EmptyView()
        default:
            typeBox(L10n.Talk.typeTask)
        }
    }

    @ViewBuilder private var page: some View {
        switch state.phase {
        case .needsMic: needsMic
        case .greeting, .listening: listen
        case .starting: starting
        case .asking, .answering: question
        case .answered: answered
        default: if hearingFailed { couldntHear } else { typeBox(L10n.Talk.typeTask) }
        }
    }

    private var needsMic: some View {
        VStack(spacing: ModeSpacing.s) {
            ModeTalkMicAsk().padding(.top, ModeSpacing.xxxl + ModeSpacing.s).padding(.bottom, ModeSpacing.xxl)
            Text(L10n.Talk.micTitle).modeText(.pageTitle).multilineTextAlignment(.center)
            Text(state.micDenied ? L10n.Talk.micDenied : L10n.Talk.micMessage)
                .modeText(.subheadline, color: ModeColor.textSecondary).multilineTextAlignment(.center)
                .padding(.horizontal, ModeSpacing.m)
            if !state.micDenied {
                HStack(spacing: ModeSpacing.s + ModeSpacing.xxs) {
                    ModeVoiceTileView(.logo(.apple), size: .small)
                    Text(L10n.Talk.micModel).modeText(.small, color: ModeColor.textSecondary).frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.horizontal, ModeSpacing.m + ModeSpacing.xxs)
                .padding(.vertical, ModeSpacing.m)
                .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.bubble))
                .modeShadow(in: RoundedRectangle(cornerRadius: ModeRadius.bubble))
                .padding(.top, ModeSpacing.xxl)
            }
        }
    }

    private var listen: some View {
        VStack(spacing: ModeSpacing.l) {
            if !state.greeting.isEmpty {
                // Mode said this out loud as it opened; it stays as a quiet caption.
                HStack(alignment: .firstTextBaseline, spacing: ModeSpacing.xs + ModeSpacing.xxs) {
                    ModeIcon.speaker.image.font(ModeIconFont.chevron).foregroundStyle(ModeColor.tint).accessibilityHidden(true)
                    Text(state.greeting).modeText(.small, color: ModeColor.textSecondary)
                }
                .multilineTextAlignment(.center)
            }
            ModeTalkMo(state.phase == .greeting ? .idle : .listening, level: level, size: ModeSize.talkMoListening, halo: true)
                .frame(height: ModeSize.talkHalo)
            ModeTalkWave(level: level, speaking: speaking || state.phase == .greeting)
            if state.phase == .listening {
                VStack(spacing: ModeSpacing.m) {
                    ModeLiveLabel(String(localized: L10n.Talk.hearingLive))
                    liveWords(placeholder: L10n.Talk.sayIt, style: .liveWords, cursor: ModeColor.brand)
                    Text(L10n.Talk.wordsAppear).modeText(.subheadline, color: ModeColor.textSecondary).multilineTextAlignment(.center)
                }
            } else {
                Text(L10n.Talk.micOpensAfter).modeText(.subheadline, color: ModeColor.textSecondary).multilineTextAlignment(.center)
            }
        }
    }

    private var starting: some View {
        VStack(spacing: ModeSpacing.xl + ModeSpacing.xs) {
            ModeTalkMo(.working, size: ModeSize.talkMo)
            if !state.task.isEmpty { ModeTalkBubble(state.task) }
            if !state.reply.isEmpty { ModeTalkSays(label: speaking ? L10n.Talk.modeSpeaking : L10n.Talk.modeLabel, text: state.reply, speaking: speaking) }
            ModeTalkWorking(title: state.working.isEmpty ? String(localized: L10n.Talk.workingOnIt) : state.working,
                            detail: String(localized: L10n.Talk.inBackground))
            Text(L10n.Talk.ifNeeded).modeText(.subheadline, color: ModeColor.textSecondary).multilineTextAlignment(.center)
                .padding(.horizontal, ModeSpacing.s)
        }
    }

    private var question: some View {
        VStack(spacing: ModeSpacing.l) {
            ModeTalkMo(.yourTurn, size: ModeSize.talkMoAsk).padding(.bottom, ModeSpacing.s)
            if !state.task.isEmpty {
                Text(state.task).modeText(.small, color: ModeColor.textSecondary).lineLimit(1)
            }
            Text(state.question).modeText(.question).multilineTextAlignment(.center)
            if !state.options.isEmpty {
                ModeChipFlow {
                    ForEach(state.options, id: \.self) { option in
                        ModeTalkOption(option, chosen: option == state.heardOption) { onAnswer(option) }
                    }
                }
                .padding(.vertical, ModeSpacing.s)
            }
            if typingAnswer {
                typeBox(L10n.Talk.typeAnswer)
            } else {
                ModeTalkWave(level: level, speaking: speaking, bars: 26, height: ModeSize.talkWaveQuestion, color: ModeColor.info)
                if state.phase == .answering { liveWords(placeholder: nil, style: .answerWords, cursor: ModeColor.info) }
                Text(state.options.isEmpty ? L10n.Talk.sayAnswer : L10n.Talk.sayOrTap).modeText(.subheadline, color: ModeColor.textSecondary)
            }
        }
    }

    private var answered: some View {
        VStack(spacing: ModeSpacing.xl + ModeSpacing.xs) {
            ModeTalkMo(.done, size: ModeSize.talkMo)
            if !state.answer.isEmpty { ModeTalkBubble(state.answer) }
            if !state.answerReply.isEmpty {
                ModeTalkSays(label: speaking ? L10n.Talk.modeSpeaking : L10n.Talk.modeLabel, text: state.answerReply, speaking: speaking)
            }
            if !state.working.isEmpty {
                ModeTalkWorking(title: state.working, detail: String(localized: L10n.Talk.inBackground))
            }
            Text(L10n.Talk.leavesSoon).modeText(.subheadline, color: ModeColor.textSecondary).multilineTextAlignment(.center)
        }
    }

    /// Mode couldn't hear with the chosen listener, so nothing started (board VoiceNoticeListen).
    private var couldntHear: some View {
        VStack(spacing: ModeSpacing.xs + ModeSpacing.xxs) {
            ModeTalkMo(.stuck, size: ModeSize.talkMoNotice).padding(.top, ModeSpacing.xxxl + ModeSpacing.xxl).padding(.bottom, ModeSpacing.xxxl)
            Text(L10n.Talk.couldntHearYou).modeText(.sheetTitle).multilineTextAlignment(.center)
            if let message = state.voiceNoticeMessage {
                Text(message).modeText(.subheadline, color: ModeColor.textSecondary).multilineTextAlignment(.center)
            }
            if let notice = state.voiceNotice {
                ModeVoiceNotice(notice, actionTitle: L10n.Voice.updateInSettings, action: onVoiceSettings)
                    .padding(.top, ModeSpacing.xl)
                    .padding(.horizontal, -ModeSpacing.xs)
            }
        }
    }

    // MARK: Pieces

    /// The words heard so far, the newest one lighter, then a cursor.
    @ViewBuilder private func liveWords(placeholder: LocalizedStringResource?, style: ModeVoiceTextStyle, cursor: Color) -> some View {
        if state.settled.isEmpty && state.forming.isEmpty {
            if let placeholder { Text(placeholder).modeText(style, color: ModeColor.textSecondary).multilineTextAlignment(.center) }
        } else {
            (Text(state.settled.isEmpty ? "" : state.settled + " ").foregroundStyle(ModeColor.textPrimary)
             + Text(state.forming).foregroundStyle(ModeColor.textSecondary)
             + Text(verbatim: "|").foregroundStyle(cursor))
                .modeText(style)
                .multilineTextAlignment(.center)
                .contentTransition(.opacity)
                .animation(ModeMotion.quick, value: state.forming)
        }
    }

    private func typeBox(_ prompt: LocalizedStringResource) -> some View {
        TextField(text: $typed, axis: .vertical) { Text(prompt) }
            .modeText(.title2)
            .multilineTextAlignment(.center)
            .focused($typing)
            .submitLabel(.send)
            .onSubmit(onSend)
            .padding(ModeSpacing.l)
            .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.card))
            .modeShadow(in: RoundedRectangle(cornerRadius: ModeRadius.card))
    }

    // MARK: Dock

    @ViewBuilder private var dock: some View {
        Group {
            switch state.phase {
            case .needsMic:
                VStack(spacing: ModeSpacing.s + ModeSpacing.xxs) {
                    if state.micDenied, let settings = URL(string: UIApplication.openSettingsURLString) {
                        Link(destination: settings) {
                            Text(L10n.Talk.openSettings).modeText(.headline, color: ModeColor.onAccent)
                                .frame(maxWidth: .infinity, minHeight: ModeSize.primaryButton)
                                .background(ModeColor.accent, in: .capsule)
                        }
                    } else {
                        ModeVoiceButton(L10n.Talk.allowMic, icon: .microphone, style: .info, height: ModeSize.primaryButton, action: onAllowMic)
                    }
                    Button(action: onType) {
                        Text(L10n.Talk.typeInstead).modeText(.calloutEmphasis).frame(minHeight: ModeSize.touch)
                    }
                    .buttonStyle(ModePressStyle())
                }
            case .greeting, .listening:
                HStack(spacing: ModeSize.talkDockGap) {
                    ModeTalkSideButton(.keyboard, label: L10n.Talk.type, action: onType)
                    ModeTalkMicButton(listening: state.phase == .listening, label: L10n.Talk.done, action: onDone)
                    if state.hasDone {
                        ModeTalkSideButton(.checkmark, label: L10n.Talk.thatsAll, action: onThatsAll)
                    } else {
                        ModeTalkSideButton(.close, label: L10n.Talk.cancel, action: onClose)
                    }
                }
            case .asking, .answering:
                if typingAnswer {
                    ModeVoiceButton(L10n.Talk.send, style: .ink, height: ModeSize.primaryButton, action: onSend)
                        .disabled(typed.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                } else {
                    HStack(spacing: ModeSize.talkDockGap) {
                        ModeTalkSideButton(.keyboard, label: L10n.Talk.type, action: onType)
                        ModeTalkMicButton(listening: state.phase == .answering, label: L10n.Talk.done, action: onDone)
                        ModeTalkSideButton(.arrowRight, label: L10n.Talk.later, action: onLater)
                    }
                }
            case .starting:
                VStack(spacing: ModeSpacing.l) {
                    // Why Mo can't hear right now, just above Stop (board: Talking · one conversation, A).
                    if inConversation { ModeTalkMicOff(L10n.Talk.micOffNote) }
                    ModeVoiceButton(L10n.Talk.stop, style: .danger, height: ModeSize.primaryButton, action: onStop)
                        .frame(width: ModeSize.talkStopWidth)
                }
            case .answered:
                // Mode is saying how it went: End is still right there.
                ModeVoiceButton(L10n.Talk.end, style: .raised, height: ModeSize.primaryButton, action: onEnd)
                    .frame(width: ModeSize.talkStopWidth)
            default:
                if hearingFailed {
                    VStack(spacing: ModeSpacing.s) {
                        ModeVoiceButton(L10n.Talk.tryAgain, style: .ink, action: onTryAgain)
                        ModeVoiceButton(L10n.Talk.typeInstead, style: .raised, action: onType)
                    }
                } else {
                    ModeVoiceButton(L10n.Talk.send, style: .ink, height: ModeSize.primaryButton, action: onSend)
                        .disabled(typed.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
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
    private let onVoiceSettings: (Bool) -> Void
    private let onOpenChat: (String) -> Void
    private let onWhatWentWrong: (String) -> Void
    @Environment(\.dismiss) private var dismiss

    init(app: AppModel, forQuestion: Bool, conversationId: String? = nil, entry: String = "siri",
         onClose: @escaping () -> Void, onVoiceSettings: @escaping (Bool) -> Void = { _ in },
         onOpenChat: @escaping (String) -> Void = { _ in }, onWhatWentWrong: @escaping (String) -> Void = { _ in }) {
        _model = State(initialValue: TalkModel(screens: app.screens, forQuestion: forQuestion, conversationId: conversationId, entry: entry))
        self.onClose = onClose
        self.onVoiceSettings = onVoiceSettings
        self.onOpenChat = onOpenChat
        self.onWhatWentWrong = onWhatWentWrong
    }

    var body: some View {
        TalkScreen(
            state: model.state, level: model.level, speaking: model.speaking, typingAnswer: model.typingAnswer, typed: $model.typed,
            onAllowMic: model.allowMic, onDone: model.done, onType: model.typeInstead, onSend: model.send,
            onAnswer: model.answer, onLater: { model.later(); close() }, onStop: { model.stop(); close() }, onClose: close,
            onEnd: { model.endNow(); close() },
            onVoiceSettings: { let hearing = model.state.voiceNoticeHearing; close(); onVoiceSettings(hearing) },
            cantHear: model.cantHear,
            onTryAgain: model.tryAgain,
            onPick: model.pick,
            onThatsAll: model.thatsAll,
            onOpenChat: { id in close(); onOpenChat(id) },
            onWhatWentWrong: { taskId in close(); onWhatWentWrong(taskId) }
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
