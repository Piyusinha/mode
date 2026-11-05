import SwiftUI

/// The small grey label above a group (Mode v2): 13 semibold, 4 in from the group's edge.
public struct ModeGroupLabel: View {
    private let title: LocalizedStringResource

    public init(_ title: LocalizedStringResource) {
        self.title = title
    }

    public var body: some View {
        Text(title)
            .modeText(.footnoteEmphasis, color: ModeColor.textSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, ModeSpacing.labelInset)
            .accessibilityAddTraits(.isHeader)
    }
}

/// A label and its group, 6 apart.
public struct ModeLabeledGroup<Content: View>: View {
    private let title: LocalizedStringResource?
    private let content: Content

    public init(_ title: LocalizedStringResource?, @ViewBuilder content: () -> Content) {
        self.title = title
        self.content = content()
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.labelGap) {
            if let title { ModeGroupLabel(title) }
            content
        }
    }
}

/// Rows in a 22-point card with hairlines between them (Mode v2). [surface] tints it: a soft warning,
/// a soft success, or a raised grey for a note.
public struct ModeGroup<Content: View>: View {
    public enum Surface: Sendable { case card, raised, warning, success }

    private let surface: Surface
    private let content: Content

    public init(_ surface: Surface = .card, @ViewBuilder content: () -> Content) {
        self.surface = surface
        self.content = content()
    }

    public var body: some View {
        VStack(spacing: ModeSpacing.none) {
            Group(subviews: content) { rows in
                ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                    row
                    if index < rows.count - 1 {
                        Rectangle().fill(separator).frame(height: ModeSize.separator)
                    }
                }
            }
        }
        .padding(.horizontal, ModeSpacing.groupInset)
        .background(background, in: .rect(cornerRadius: ModeRadius.group))
    }

    private var background: Color {
        switch surface {
        case .card: ModeColor.card
        case .raised: ModeColor.cardRaised
        case .warning: ModeColor.tileOrange
        case .success: ModeColor.tagGreen
        }
    }

    private var separator: Color {
        switch surface {
        case .warning: ModeColor.warningSeparator
        default: ModeColor.separator
        }
    }
}

/// What a group row shows at its end.
public enum ModeGroupTrailing: Equatable, Sendable {
    case none
    /// A grey value ("Off", "Automatic", "27 things").
    case value(String)
    /// A green dot and a word ("Ready", "Connected").
    case ready(String)
}

/// A row in a group (Mode v2): an optional 36-point icon tile, a title (semibold when it has a second
/// line), the second line, a value or a green "Ready", and a chevron when it opens something.
public struct ModeGroupRow: View {
    private let title: String
    private let subtitle: String?
    private let icon: ModeIcon?
    private let trailing: ModeGroupTrailing
    private let minHeight: CGFloat
    private let chevron: Bool
    private let action: (() -> Void)?

    public init(_ title: String, subtitle: String? = nil, icon: ModeIcon? = nil, trailing: ModeGroupTrailing = .none,
                minHeight: CGFloat? = nil, chevron: Bool = true, action: (() -> Void)? = nil) {
        self.title = title
        self.subtitle = subtitle
        self.icon = icon
        self.trailing = trailing
        self.minHeight = minHeight ?? (icon == nil && subtitle == nil ? ModeSize.groupRowCompact : ModeSize.groupRow)
        self.chevron = chevron
        self.action = action
    }

    public var body: some View {
        if let action {
            Button(action: action) { content }.buttonStyle(.plain)
        } else {
            content
        }
    }

    private var content: some View {
        HStack(spacing: icon == nil ? ModeSpacing.s : ModeSpacing.m) {
            if let icon {
                icon.image
                    .font(ModeIconFont.small)
                    .foregroundStyle(ModeColor.textPrimary)
                    .frame(width: ModeSize.groupTile, height: ModeSize.groupTile)
                    .background(ModeColor.cardRaised, in: .rect(cornerRadius: ModeRadius.tile))
                    .accessibilityHidden(true)
            }
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(title).modeText(subtitle == nil ? .subheadline : .subheadlineEmphasis)
                if let subtitle { Text(subtitle).modeText(.footnote, color: ModeColor.textSecondary) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            switch trailing {
            case .none: EmptyView()
            case let .value(text): Text(text).modeText(.subheadline, color: ModeColor.textSecondary)
            case let .ready(text):
                HStack(spacing: ModeSpacing.xs + ModeSpacing.xxs) {
                    Circle().fill(ModeColor.success).frame(width: ModeSize.statusDot, height: ModeSize.statusDot)
                    Text(text).modeText(.subheadlineEmphasis, color: ModeColor.tagGreenText)
                }
            }
            if action != nil && chevron { ModeChevron() }
        }
        .frame(minHeight: minHeight)
        .contentShape(.rect)
    }
}

/// A page's heading in Mode v2: 28 heavy and a 15 secondary line under it, 4 in.
public struct ModePageHeading: View {
    private let title: LocalizedStringResource
    private let subtitle: LocalizedStringResource?

    public init(_ title: LocalizedStringResource, subtitle: LocalizedStringResource? = nil) {
        self.title = title
        self.subtitle = subtitle
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.xs) {
            Text(title).modeText(.heading).accessibilityAddTraits(.isHeader)
            if let subtitle { Text(subtitle).modeText(.subheadline, color: ModeColor.textSecondary) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, ModeSpacing.labelInset)
    }
}

/// A grey tag in a pill at the top of a guide ("Optional").
public struct ModePillTag: View {
    private let text: LocalizedStringResource

    public init(_ text: LocalizedStringResource) {
        self.text = text
    }

    public var body: some View {
        Text(text)
            .modeText(.footnoteEmphasis, color: ModeColor.textSecondary)
            .padding(.horizontal, ModeSpacing.m)
            .frame(height: ModeSize.tagHeight)
            .background(ModeColor.cardRaised, in: .capsule)
    }
}

/// One numbered step: a title and a note.
public struct ModeStep: Identifiable, Equatable, Sendable {
    public let id: Int
    public let title: String
    public let note: String

    public init(_ number: Int, title: String, note: String) {
        self.id = number
        self.title = title
        self.note = note
    }
}

/// Numbered steps in a group (a guide): an ink badge with the number, the step, and a note.
public struct ModeStepList: View {
    private let steps: [ModeStep]

    public init(_ steps: [ModeStep]) {
        self.steps = steps
    }

    public var body: some View {
        ModeGroup {
            ForEach(steps) { step in
                HStack(alignment: .top, spacing: ModeSpacing.m) {
                    Text(step.id, format: .number)
                        .modeText(.captionEmphasis, color: ModeColor.onAccent)
                        .frame(width: ModeSize.stepBadge, height: ModeSize.stepBadge)
                        .background(ModeColor.accent, in: .circle)
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                        Text(step.title).modeText(.subheadlineEmphasis)
                        Text(step.note).modeText(.footnote, color: ModeColor.textSecondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.vertical, ModeSpacing.m)
                .accessibilityElement(children: .combine)
            }
        }
    }
}

/// Notes on a soft orange: each with its own icon (a warning, the mic, a lock).
public struct ModeWarningNotes: View {
    public struct Note: Identifiable, Equatable, Sendable {
        public var id: String { text }
        public let icon: ModeIcon
        public let text: String

        public init(_ icon: ModeIcon, _ text: String) {
            self.icon = icon
            self.text = text
        }
    }

    private let notes: [Note]

    public init(_ notes: [Note]) {
        self.notes = notes
    }

    public var body: some View {
        ModeGroup(.warning) {
            ForEach(notes) { note in
                HStack(alignment: .top, spacing: ModeSpacing.m) {
                    note.icon.image.font(ModeIconFont.small).foregroundStyle(ModeColor.tileOrangeText).accessibilityHidden(true)
                    // Markdown, for a word in bold ("**Anyone** near your iPhone…").
                    Text(LocalizedStringKey(note.text)).modeText(.subheadline).frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.vertical, ModeSpacing.s + ModeSpacing.xxs)
            }
        }
    }
}

/// Mo beside a line, on a raised card: what happens, in Mo's words.
public struct ModeMoNote: View {
    private let text: LocalizedStringResource
    private let mood: ModeMo.Mood
    private let tint: Color

    public init(_ text: LocalizedStringResource, mood: ModeMo.Mood = .idle, tint: Color = ModeColor.brand) {
        self.text = text
        self.mood = mood
        self.tint = tint
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.m) {
            ModeMo(mood, size: ModeSize.moNote, tint: tint)
            Text(text).modeText(.subheadline).frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(ModeSpacing.groupInset)
        .background(ModeColor.cardRaised, in: .rect(cornerRadius: ModeRadius.group))
    }
}

/// Mo, a title and a line on a card that says how something stands (a check in progress, it works, it
/// didn't): tinted green when it worked, orange when it didn't. [trailing] is a small status (a countdown).
public struct ModeStatusCard: View {
    public enum Tone: Sendable { case neutral, success, warning }

    private let mood: ModeMo.Mood
    private let title: String
    private let detail: String
    private let tone: Tone
    private let trailing: String?

    public init(mood: ModeMo.Mood, title: String, detail: String, tone: Tone, trailing: String? = nil) {
        self.mood = mood
        self.title = title
        self.detail = detail
        self.tone = tone
        self.trailing = trailing
    }

    public var body: some View {
        HStack(alignment: .top, spacing: ModeSpacing.m) {
            ModeMo(mood, size: ModeSize.moNote)
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                HStack(alignment: .firstTextBaseline) {
                    Text(title).modeText(.subheadlineEmphasis, color: titleColor)
                    Spacer(minLength: ModeSpacing.s)
                    if let trailing { Text(trailing).modeText(.footnoteEmphasis, color: ModeColor.textSecondary).monospacedDigit() }
                }
                Text(detail).modeText(.footnote, color: ModeColor.textSecondary)
            }
        }
        .padding(ModeSpacing.groupInset)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(background, in: .rect(cornerRadius: ModeRadius.group))
        .accessibilityElement(children: .combine)
    }

    private var background: Color {
        switch tone {
        case .neutral: ModeColor.card
        case .success: ModeColor.tagGreen
        case .warning: ModeColor.tileOrange
        }
    }

    private var titleColor: Color {
        switch tone {
        case .neutral: ModeColor.textPrimary
        case .success: ModeColor.tagGreenText
        case .warning: ModeColor.tileOrangeText
        }
    }
}

/// A green dot and a word ("Working", "Ready") at the end of a row.
public struct ModeGroupTrailingReady: View {
    private let text: String

    public init(_ text: String) {
        self.text = text
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.xs + ModeSpacing.xxs) {
            Circle().fill(ModeColor.success).frame(width: ModeSize.statusDot, height: ModeSize.statusDot)
            Text(text).modeText(.subheadlineEmphasis, color: ModeColor.tagGreenText)
        }
    }
}
