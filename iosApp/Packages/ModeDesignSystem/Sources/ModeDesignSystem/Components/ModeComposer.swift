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
