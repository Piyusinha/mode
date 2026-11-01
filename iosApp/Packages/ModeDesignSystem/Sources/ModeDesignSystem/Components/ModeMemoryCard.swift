import SwiftUI

/// One thing Mode remembers, in its own card, easy to read: a short title, the full text wrapped,
/// a routine's numbered steps, and a quiet footer ("Learned today · worked 3 of 3 times").
/// While editing a list, a selection mark sits before the title.
public struct ModeMemoryCard: View {
    private let title: String
    private let text: String?
    private let steps: [String]
    private let footer: String?
    private let selected: Bool?
    private let action: () -> Void

    public init(title: String, text: String?, steps: [String] = [], footer: String?, selected: Bool? = nil, action: @escaping () -> Void) {
        self.title = title
        self.text = text
        self.steps = steps
        self.footer = footer
        self.selected = selected
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack(alignment: .top, spacing: ModeSpacing.m) {
                if let selected { ModeLeadingView(selected ? .success : .pending) }
                VStack(alignment: .leading, spacing: ModeSpacing.s) {
                    Text(title).modeText(.bodyEmphasis)
                    // A routine reads steps first, then its notes ("If it goes wrong: …").
                    if steps.isEmpty, let text, !text.isEmpty { Text(text).modeText(.body) }
                    if !steps.isEmpty {
                        VStack(alignment: .leading, spacing: ModeSpacing.xs) {
                            ForEach(Array(steps.enumerated()), id: \.offset) { index, step in
                                HStack(alignment: .firstTextBaseline, spacing: ModeSpacing.s) {
                                    Text(verbatim: "\(index + 1).").modeText(.body, color: ModeColor.textSecondary).monospacedDigit()
                                    Text(step).modeText(.body)
                                }
                            }
                        }
                    }
                    if !steps.isEmpty, let text, !text.isEmpty { Text(text).modeText(.callout, color: ModeColor.textSecondary) }
                    if let footer { Text(footer).modeText(.footnote, color: ModeColor.textSecondary) }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .multilineTextAlignment(.leading)
            }
            .padding(ModeSpacing.l)
            .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.field))
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(selected == true ? [.isButton, .isSelected] : .isButton)
    }
}

/// A text area in its own card: the label above, as many lines as the text needs below.
public struct ModeTextAreaCard: View {
    private let label: LocalizedStringResource
    @Binding private var text: String
    private let minLines: Int

    public init(_ label: LocalizedStringResource, text: Binding<String>, minLines: Int = 1) {
        self.label = label
        self._text = text
        self.minLines = minLines
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.xs) {
            Text(label).modeText(.footnote, color: ModeColor.textSecondary)
            TextField(text: $text, axis: .vertical) { Text(label) }
                .lineLimit(minLines...)
                .modeText(.body)
        }
        .padding(.horizontal, ModeSpacing.l)
        .padding(.vertical, ModeSpacing.m)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.field))
    }
}
