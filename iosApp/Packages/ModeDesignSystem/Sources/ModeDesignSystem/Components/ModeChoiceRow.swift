import SwiftUI

/// Something to pick from a list (a model): its tile, its name with an optional coloured tag
/// ("Recommended", "Cheapest"), one line about it, and a radio.
public struct ModeChoiceRow: View {
    public struct Tag: Equatable, Sendable {
        public let text: String
        public let background: Color
        public let foreground: Color

        public init(_ text: String, background: Color, foreground: Color) {
            self.text = text
            self.background = background
            self.foreground = foreground
        }
    }

    private let title: String
    private let tag: Tag?
    private let detail: String
    private let leading: ModeRowLeading
    private let selected: Bool
    private let action: () -> Void

    public init(_ title: String, tag: Tag?, detail: String, leading: ModeRowLeading, selected: Bool, action: @escaping () -> Void) {
        self.title = title
        self.tag = tag
        self.detail = detail
        self.leading = leading
        self.selected = selected
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: ModeSpacing.m) {
                ModeLeadingView(leading)
                VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                    HStack(spacing: ModeSpacing.s) {
                        Text(title).modeText(.bodyEmphasis).lineLimit(1)
                        if let tag { ModeTag(tag.text, background: tag.background, foreground: tag.foreground).fixedSize() }
                    }
                    Text(detail).modeText(.footnote, color: ModeColor.textSecondary).lineLimit(2)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                ModeRadio(selected: selected)
            }
            .padding(.vertical, ModeSpacing.m)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }
}

/// A search field in a glass capsule, always visible at the top of a list.
public struct ModeSearchField: View {
    private let prompt: String
    @Binding private var text: String

    public init(_ prompt: String, text: Binding<String>) {
        self.prompt = prompt
        self._text = text
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.s) {
            ModeIcon.search.image.foregroundStyle(ModeColor.textSecondary).accessibilityHidden(true)
            TextField(text: $text) { Text(prompt) }
                .modeText(.body)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.search)
            if !text.isEmpty {
                Button { text = "" } label: { ModeIcon.close.image.foregroundStyle(ModeColor.textSecondary) }
                    .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, ModeSpacing.l)
        .frame(minHeight: ModeSize.touch)
        .glassEffect(.regular, in: .capsule)
    }
}
