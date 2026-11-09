import SwiftUI

/// An open voice session, minimised (board: Voice session flow › The session bar). It sits above the tab bar, or
/// above a chat's message bar, and always says what the session is doing now. A tap on it opens the voice screen
/// again; the mic pauses or resumes listening; × ends the session. Draws what it's given, sends taps back.
public struct ModeSessionBar: View {
    public enum Kind: Sendable, Equatable {
        /// Listening for the next thing to do.
        case listening
        /// Mode is talking: [title] is what it says.
        case speaking
        /// A task is running in another app (the mic is off meanwhile).
        case working
        /// Mode asked something only the user can answer.
        case needsYou
        /// The session is open but not listening.
        case paused
    }

    public struct Labels: Sendable {
        let open: LocalizedStringResource
        let pause: LocalizedStringResource
        let resume: LocalizedStringResource
        let end: LocalizedStringResource
        let stop: LocalizedStringResource
        let answer: LocalizedStringResource

        public init(open: LocalizedStringResource, pause: LocalizedStringResource, resume: LocalizedStringResource,
                    end: LocalizedStringResource, stop: LocalizedStringResource, answer: LocalizedStringResource) {
            self.open = open
            self.pause = pause
            self.resume = resume
            self.end = end
            self.stop = stop
            self.answer = answer
        }
    }

    private let kind: Kind
    private let title: String
    private let subtitle: String
    private let labels: Labels
    private let onOpen: () -> Void
    private let onMic: () -> Void
    private let onEnd: () -> Void
    private let onStop: () -> Void

    public init(_ kind: Kind, title: String, subtitle: String, labels: Labels,
                onOpen: @escaping () -> Void, onMic: @escaping () -> Void, onEnd: @escaping () -> Void, onStop: @escaping () -> Void) {
        self.kind = kind
        self.title = title
        self.subtitle = subtitle
        self.labels = labels
        self.onOpen = onOpen
        self.onMic = onMic
        self.onEnd = onEnd
        self.onStop = onStop
    }

    private var light: Bool { kind == .paused }
    private var background: Color {
        switch kind {
        case .needsYou: ModeColor.info
        case .paused: ModeColor.card
        default: ModeColor.sessionBar
        }
    }
    private var titleColor: Color { light ? ModeColor.textPrimary : ModeColor.sessionBarText }
    private var subtitleColor: Color { light ? ModeColor.textSecondary : ModeColor.sessionBarSecondary }
    private var actionFill: Color { light ? ModeColor.fill : ModeColor.onDarkFill }
    private var actionTint: Color { light ? ModeColor.textPrimary : ModeColor.sessionBarText }

    public var body: some View {
        HStack(spacing: ModeSpacing.s) {
            Button(action: onOpen) {
                HStack(spacing: ModeSpacing.m) {
                    lead
                    VStack(alignment: .leading, spacing: ModeSpacing.none) {
                        Text(title).modeText(.calloutEmphasis, color: titleColor).lineLimit(1)
                        Text(subtitle).modeText(.footnote, color: subtitleColor).lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    if kind == .listening { ModeSessionWave() }
                }
                .contentShape(.rect)
            }
            .buttonStyle(ModePressStyle())
            .accessibilityLabel(Text(labels.open))
            .accessibilityValue(Text(title))
            trailing
        }
        .padding(.leading, ModeSpacing.m)
        .padding(.trailing, ModeSpacing.s)
        .padding(.vertical, ModeSpacing.s)
        .background(background, in: .rect(cornerRadius: ModeRadius.sessionBar))
        .overlay {
            if light { RoundedRectangle(cornerRadius: ModeRadius.sessionBar).strokeBorder(ModeColor.separator) }
        }
        .modeShadow(in: RoundedRectangle(cornerRadius: ModeRadius.sessionBar))
        .animation(ModeMotion.standard, value: kind)
    }

    @ViewBuilder private var lead: some View {
        switch kind {
        case .working:
            ProgressView().tint(ModeColor.brand).frame(width: ModeSize.sessionBarMo, height: ModeSize.sessionBarMo)
        case .needsYou:
            EmptyView()
        case .speaking:
            ModeMo(.listening, size: ModeSize.sessionBarMo)
        case .paused:
            ModeMo(.sleep, size: ModeSize.sessionBarMo)
        case .listening:
            ModeMo(.idle, size: ModeSize.sessionBarMo)
        }
    }

    @ViewBuilder private var trailing: some View {
        switch kind {
        case .working:
            pill(labels.stop, fill: ModeColor.brand, tint: ModeColor.onAccent, action: onStop)
        case .needsYou:
            pill(labels.answer, fill: ModeColor.card, tint: ModeColor.info, action: onOpen)
        case .paused:
            round(.microphone, label: labels.resume, fill: ModeColor.brand, tint: ModeColor.onAccent, action: onMic)
            round(.close, label: labels.end, fill: actionFill, tint: actionTint, action: onEnd)
        case .listening, .speaking:
            round(.microphone, label: labels.pause, fill: actionFill, tint: actionTint, action: onMic)
            round(.close, label: labels.end, fill: actionFill, tint: actionTint, action: onEnd)
        }
    }

    private func round(_ icon: ModeIcon, label: LocalizedStringResource, fill: Color, tint: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            icon.image.font(.system(size: ModeSize.sessionBarActionIcon, weight: .semibold)).foregroundStyle(tint)
                .frame(width: ModeSize.sessionBarAction, height: ModeSize.sessionBarAction)
                .background(fill, in: .circle)
        }
        .buttonStyle(ModePressStyle())
        .accessibilityLabel(Text(label))
    }

    private func pill(_ label: LocalizedStringResource, fill: Color, tint: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).modeText(.calloutEmphasis, color: tint)
                .padding(.horizontal, ModeSpacing.l)
                .frame(minHeight: ModeSize.sessionBarAction)
                .background(fill, in: .capsule)
        }
        .buttonStyle(ModePressStyle())
    }
}

/// The bars that move while the session listens.
struct ModeSessionWave: View {
    @State private var up = false

    var body: some View {
        HStack(spacing: ModeSize.sessionBarWaveGap) {
            ForEach(ModeSessionBarWave.rest.indices, id: \.self) { i in
                Capsule()
                    .fill(ModeColor.brand)
                    .frame(width: ModeSize.sessionBarWaveWidth,
                           height: ModeSize.sessionBarWaveHeight * (up ? ModeSessionBarWave.rest[i] : ModeSessionBarWave.quiet))
                    .animation(ModeMotion.sessionBarWave.delay(Double(i) * ModeSessionBarWave.staggerSeconds), value: up)
            }
        }
        .frame(height: ModeSize.sessionBarWaveHeight)
        .accessibilityHidden(true)
        .onAppear { up = true }
    }
}
