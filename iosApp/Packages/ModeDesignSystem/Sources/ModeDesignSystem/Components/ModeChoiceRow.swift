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
    private let emphasis: ModeRowEmphasis
    private let action: () -> Void

    /// [emphasis] .prominent sets the name semibold (models, Access); .standard plain (languages).
    public init(_ title: String, tag: Tag?, detail: String, leading: ModeRowLeading, selected: Bool, emphasis: ModeRowEmphasis = .prominent,
                action: @escaping () -> Void) {
        self.emphasis = emphasis
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
                    // The name is never cut short: when it and its tag don't fit on one line, the tag goes under it.
                    ViewThatFits(in: .horizontal) {
                        HStack(spacing: ModeSpacing.xs + ModeSpacing.xxs) {
                            Text(title).modeText(titleStyle).fixedSize()
                            tagView
                        }
                        VStack(alignment: .leading, spacing: ModeSpacing.xs) {
                            Text(title).modeText(titleStyle).fixedSize(horizontal: false, vertical: true)
                            tagView
                        }
                    }
                    Text(detail).modeText(.footnote, color: ModeColor.textSecondary).lineSpacing(ModeSpacing.xxs).lineLimit(3)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                ModeRadio(selected: selected)
            }
            .padding(.vertical, emphasis == .prominent ? ModeSpacing.headerGap : ModeSpacing.s)
            .frame(minHeight: leading == .none ? ModeSize.rowMin : ModeSize.tileRow)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }

    private var titleStyle: ModeTextStyle { emphasis == .prominent ? .calloutEmphasis : .callout }

    @ViewBuilder private var tagView: some View {
        if let tag { ModeTag(tag.text, background: tag.background, foreground: tag.foreground).fixedSize() }
    }
}

/// A search field in a white capsule, always visible at the top of a list.
public struct ModeSearchField: View {
    private let prompt: String
    @Binding private var text: String

    public init(_ prompt: String, text: Binding<String>) {
        self.prompt = prompt
        self._text = text
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.s) {
            ModeIcon.search.image.font(ModeIconFont.small).foregroundStyle(ModeColor.textSecondary).accessibilityHidden(true)
            TextField(text: $text) { Text(prompt).foregroundStyle(ModeColor.textSecondary) }
                .modeText(.body)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.search)
            if !text.isEmpty {
                Button { text = "" } label: { ModeIcon.close.image.foregroundStyle(ModeColor.textSecondary) }
                    .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, ModeSpacing.headerGap)
        .frame(minHeight: ModeSize.touch)
        .background(ModeColor.card, in: .capsule)
        .modeShadow(in: Capsule())
    }
}
