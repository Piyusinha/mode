import SwiftUI

/// A text field in Mode's style, with its label above.
public struct ModeTextField: View {
    private let label: LocalizedStringResource
    private let prompt: LocalizedStringResource
    @Binding private var text: String
    private let secure: Bool

    public init(_ label: LocalizedStringResource, prompt: LocalizedStringResource, text: Binding<String>, secure: Bool = false) {
        self.label = label
        self.prompt = prompt
        self._text = text
        self.secure = secure
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.xs) {
            Text(label).modeText(.footnote, color: ModeColor.textSecondary).padding(.leading, ModeSpacing.xs)
            Group {
                if secure {
                    SecureField(text: $text) { Text(prompt) }
                } else {
                    TextField(text: $text) { Text(prompt) }
                }
            }
            .modeText(.callout)
            .textInputAutocapitalization(secure ? .never : .sentences)
            .autocorrectionDisabled(secure)
            .padding(.horizontal, ModeSpacing.l)
            .frame(minHeight: ModeSize.fieldHeight)
            .background(ModeColor.field, in: .rect(cornerRadius: ModeRadius.field))
            .modeShadow(in: RoundedRectangle(cornerRadius: ModeRadius.field))
        }
    }
}

/// A row with a switch.
public struct ModeToggleRow: View {
    private let title: LocalizedStringResource
    private let subtitle: LocalizedStringResource?
    @Binding private var isOn: Bool

    public init(_ title: LocalizedStringResource, subtitle: LocalizedStringResource? = nil, isOn: Binding<Bool>) {
        self.title = title
        self.subtitle = subtitle
        self._isOn = isOn
    }

    public var body: some View {
        Toggle(isOn: $isOn) {
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(title).modeText(.callout)
                if let subtitle { Text(subtitle).modeText(.footnote, color: ModeColor.textSecondary) }
            }
        }
        .tint(ModeColor.success)
        .frame(minHeight: ModeSize.rowMin)
    }
}

/// A choice of a few options side by side.
public struct ModeSegmented<Option: Hashable>: View {
    private let options: [(Option, LocalizedStringResource)]
    @Binding private var selection: Option

    public init(_ options: [(Option, LocalizedStringResource)], selection: Binding<Option>) {
        self.options = options
        self._selection = selection
    }

    public var body: some View {
        Picker(selection: $selection) {
            ForEach(options, id: \.0) { option, title in Text(title).tag(option) }
        } label: { EmptyView() }
        .pickerStyle(.segmented)
    }
}

/// Seven round buttons, one per day; [labels] are the short names, Monday first.
public struct ModeDayPicker: View {
    private let labels: [String]
    private let selected: Set<Int>
    private let toggle: (Int) -> Void

    /// [selected] holds 1 = Monday … 7 = Sunday.
    public init(labels: [String], selected: Set<Int>, toggle: @escaping (Int) -> Void) {
        self.labels = labels
        self.selected = selected
        self.toggle = toggle
    }

    public var body: some View {
        HStack {
            ForEach(Array(labels.enumerated()), id: \.offset) { index, label in
                let day = index + 1
                let on = selected.contains(day)
                Button { toggle(day) } label: {
                    Text(label)
                        .modeText(.calloutEmphasis, color: on ? ModeColor.onAccent : ModeColor.textPrimary)
                        .frame(width: ModeSize.dayButton, height: ModeSize.dayButton)
                        .background(on ? ModeColor.accent : ModeColor.cardRaised, in: .circle)
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(on ? .isSelected : [])
                if day < labels.count { Spacer(minLength: 0) }
            }
        }
    }
}
