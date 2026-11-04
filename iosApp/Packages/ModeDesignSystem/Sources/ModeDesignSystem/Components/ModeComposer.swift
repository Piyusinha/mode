import SwiftUI

/// The message bar: a glass capsule with the text, a private switch and a send button.
public struct ModeComposer: View {
    public struct Labels {
        let prompt: LocalizedStringResource
        let send: LocalizedStringResource
        let privateOn: LocalizedStringResource
        let privateOff: LocalizedStringResource

        public init(prompt: LocalizedStringResource, send: LocalizedStringResource, privateOn: LocalizedStringResource, privateOff: LocalizedStringResource) {
            self.prompt = prompt
            self.send = send
            self.privateOn = privateOn
            self.privateOff = privateOff
        }
    }

    @Binding private var text: String
    private let isPrivate: Bool
    private let labels: Labels
    private let onTogglePrivate: () -> Void
    private let onSend: () -> Void
    @FocusState private var focused: Bool

    public init(text: Binding<String>, isPrivate: Bool, labels: Labels, onTogglePrivate: @escaping () -> Void, onSend: @escaping () -> Void) {
        self._text = text
        self.isPrivate = isPrivate
        self.labels = labels
        self.onTogglePrivate = onTogglePrivate
        self.onSend = onSend
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.xs) {
            TextField(text: $text, axis: .vertical) { Text(labels.prompt).foregroundStyle(ModeColor.textSecondary) }
                .modeText(.callout)
                .lineLimit(1...5)
                .focused($focused)
                .submitLabel(.send)
                .onSubmit(onSend)
                .padding(.leading, ModeSpacing.l)
            Button(action: onTogglePrivate) {
                (isPrivate ? ModeIcon.privateOn : ModeIcon.privateOff).image
                    .font(ModeIconFont.regular)
                    .foregroundStyle(isPrivate ? ModeColor.tint : ModeColor.textSecondary)
                    .frame(width: ModeSize.sendButton, height: ModeSize.sendButton)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text(isPrivate ? labels.privateOn : labels.privateOff))
            ModeIconButton(.send, label: labels.send, prominent: true, size: ModeSize.sendButton, action: onSend)
        }
        .padding(.vertical, ModeSpacing.xs + 1)
        .padding(.trailing, ModeSpacing.xs + 1)
        .frame(minHeight: ModeSize.composerHeight)
        .background(ModeColor.card, in: .rect(cornerRadius: ModeSize.composerHeight / 2))
        .modeShadow(in: RoundedRectangle(cornerRadius: ModeSize.composerHeight / 2))
    }
}

/// A question bar without the private switch (board TaskDebugAsk): a white capsule with the text and an ink Send.
public struct ModeAskBar: View {
    @Binding private var text: String
    private let prompt: LocalizedStringResource
    private let send: LocalizedStringResource
    private let canSend: Bool
    private let onSend: () -> Void

    public init(text: Binding<String>, prompt: LocalizedStringResource, send: LocalizedStringResource, canSend: Bool, onSend: @escaping () -> Void) {
        self._text = text
        self.prompt = prompt
        self.send = send
        self.canSend = canSend
        self.onSend = onSend
    }

    public var body: some View {
        HStack(alignment: .bottom, spacing: ModeSpacing.s) {
            TextField(text: $text, axis: .vertical) { Text(prompt).foregroundStyle(ModeColor.textSecondary) }
                .modeText(.callout)
                .lineLimit(1...5)
                .submitLabel(.send)
                .onSubmit { if canSend { onSend() } }
                .frame(maxWidth: .infinity, minHeight: ModeSize.askSend, alignment: .leading)
            Button(action: onSend) {
                ModeIcon.send.image
                    .font(ModeIconFont.chatSend)
                    .foregroundStyle(ModeColor.onAccent)
                    .frame(width: ModeSize.askSend, height: ModeSize.askSend)
                    .background(ModeColor.accent, in: .circle)
                    .contentShape(.circle)
            }
            .buttonStyle(ModePressStyle())
            .disabled(!canSend)
            .accessibilityLabel(Text(send))
        }
        .padding(.leading, ModeSpacing.l + ModeSpacing.xxs)
        .padding([.vertical, .trailing], ModeSpacing.xs)
        .frame(minHeight: ModeSize.composerHeight)
        .background(ModeColor.card, in: .rect(cornerRadius: ModeSize.composerHeight / 2))
        .modeShadow(in: RoundedRectangle(cornerRadius: ModeSize.composerHeight / 2))
    }
}

/// A suggested question to tap ("What should I change?"): a soft blue pill.
public struct ModeSuggestionChip: View {
    private let text: String
    private let action: () -> Void

    public init(_ text: String, action: @escaping () -> Void) {
        self.text = text
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            Text(text).modeText(.subheadlineEmphasis, color: ModeColor.infoText)
                .lineLimit(1)
                .fixedSize()
                .padding(.horizontal, ModeSpacing.m + ModeSpacing.xxs)
                .frame(minHeight: ModeSize.askChip)
                .background(ModeColor.infoSoft, in: .capsule)
                .contentShape(Rectangle().inset(by: (ModeSize.askChip - ModeSize.touch) / 2))
        }
        .buttonStyle(ModePressStyle())
    }
}
