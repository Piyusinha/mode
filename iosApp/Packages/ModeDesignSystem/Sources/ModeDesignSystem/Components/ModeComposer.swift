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
            TextField(text: $text, axis: .vertical) { Text(labels.prompt) }
                .modeText(.body)
                .lineLimit(1...5)
                .focused($focused)
                .submitLabel(.send)
                .onSubmit(onSend)
                .padding(.leading, ModeSpacing.l)
            Button(action: onTogglePrivate) {
                (isPrivate ? ModeIcon.privateOn : ModeIcon.privateOff).image
                    .foregroundStyle(isPrivate ? ModeColor.tint : ModeColor.textSecondary)
                    .frame(width: ModeSize.touch, height: ModeSize.touch)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text(isPrivate ? labels.privateOn : labels.privateOff))
            ModeIconButton(.send, label: labels.send, prominent: true, action: onSend)
        }
        .padding(ModeSpacing.xs)
        .frame(minHeight: ModeSize.composerHeight)
        .glassEffect(.regular.interactive(), in: .capsule)
    }
}

/// A question bar without the private switch (Ask about this task): the text and a send button.
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
        HStack(spacing: ModeSpacing.xs) {
            TextField(text: $text, axis: .vertical) { Text(prompt) }
                .modeText(.body)
                .lineLimit(1...5)
                .submitLabel(.send)
                .onSubmit { if canSend { onSend() } }
                .padding(.leading, ModeSpacing.l)
            ModeIconButton(.send, label: send, prominent: true, action: onSend)
                .disabled(!canSend)
                .opacity(canSend ? 1 : 0.5)
        }
        .padding(ModeSpacing.xs)
        .frame(minHeight: ModeSize.composerHeight)
        .glassEffect(.regular.interactive(), in: .capsule)
    }
}

/// A suggested question to tap ("Why did it stop?").
public struct ModeSuggestionChip: View {
    private let text: String
    private let action: () -> Void

    public init(_ text: String, action: @escaping () -> Void) {
        self.text = text
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            Text(text).modeText(.footnote, color: ModeColor.tint)
                .padding(.horizontal, ModeSpacing.m)
                .frame(minHeight: ModeSize.touch)
                .glassEffect(.regular.interactive(), in: .capsule)
        }
        .buttonStyle(.plain)
    }
}
