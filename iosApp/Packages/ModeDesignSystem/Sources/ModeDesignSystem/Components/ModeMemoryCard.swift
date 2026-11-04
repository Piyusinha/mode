import SwiftUI

// Memory, as on the "Memory, screen by screen" boards: a page header, the hub's and App Tips' rows,
// one remembered item's card, and the editor's fields, notes and Delete.

/// A Memory page's title (Bricolage, 34 heavy) and the line under it.
public struct ModeMemoryHeader: View {
    private let title: String
    private let subtitle: String?

    public init(_ title: String, subtitle: String?) {
        self.title = title
        self.subtitle = subtitle
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.xs) {
            Text(title).modeText(.largeTitle).accessibilityAddTraits(.isHeader)
            if let subtitle, !subtitle.isEmpty { Text(subtitle).modeText(.subheadline, color: ModeColor.textSecondary) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, ModeSpacing.xs)
    }
}

/// What starts a Memory row: an icon in a soft circle, Mo, or an app's initial.
public enum ModeMemoryLeading: Equatable, Sendable {
    case icon(ModeIcon, background: Color, foreground: Color)
    case mo
    case initial(String)
}

/// A row that opens a page: the hub's kinds (large), an app in App Tips or "Routines in this app" (compact).
public struct ModeMemoryRow: View {
    private let title: String
    private let subtitle: String?
    private let leading: ModeMemoryLeading
    private let compact: Bool
    private let action: () -> Void

    public init(_ title: String, subtitle: String?, leading: ModeMemoryLeading, compact: Bool = false, action: @escaping () -> Void) {
        self.title = title
        self.subtitle = subtitle
        self.leading = leading
        self.compact = compact
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: ModeSpacing.memoryRowGap) {
                leadingView
                VStack(alignment: .leading, spacing: compact ? ModeSpacing.xxs : ModeSpacing.xxs + ModeSpacing.xxs / 2) {
                    Text(title).modeText(compact ? .calloutEmphasis : .headline)
                    if let subtitle, !subtitle.isEmpty {
                        Text(subtitle).font(ModeMemoryFont.detail).foregroundStyle(ModeColor.textSecondary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .multilineTextAlignment(.leading)
                ModeChevron()
            }
            .padding(.vertical, ModeSpacing.m)
            .frame(minHeight: compact ? ModeSize.memoryAppRow : ModeSize.memoryKindRow)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
    }

    private var size: CGFloat { compact ? ModeSize.memoryAppInitial : ModeSize.memoryKindIcon }

    @ViewBuilder private var leadingView: some View {
        switch leading {
        case let .icon(icon, background, foreground):
            icon.image
                .font(ModeIconFont.regular)
                .foregroundStyle(foreground)
                .frame(width: size, height: size)
                .background(background, in: .circle)
                .accessibilityHidden(true)
        case .mo:
            ModeMo(.idle, size: size)
        case let .initial(letter):
            Text(letter)
                .font(ModeMemoryFont.initial)
                .foregroundStyle(ModeColor.textPrimary)
                .frame(width: size, height: size)
                .background(ModeColor.cardRaised, in: .circle)
                .accessibilityHidden(true)
        }
    }
}

/// Learn from my tasks: a switch in its own lifted card.
public struct ModeMemoryToggleCard: View {
    private let title: LocalizedStringResource
    @Binding private var isOn: Bool

    public init(_ title: LocalizedStringResource, isOn: Binding<Bool>) {
        self.title = title
        self._isOn = isOn
    }

    public var body: some View {
        Toggle(isOn: $isOn) { Text(title).modeText(.callout) }
            .tint(ModeColor.success)
            .padding(.horizontal, ModeSpacing.l)
            .frame(minHeight: ModeSize.memoryToggleRow)
            .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.card))
            .modeShadow(in: RoundedRectangle(cornerRadius: ModeRadius.card))
    }
}

/// A small tag on a remembered item: how often a routine worked (green), or that the user taught it (blue),
/// or that Mode learned it on its own (Mo, orange).
public enum ModeMemoryBadge: Equatable, Sendable {
    case worked(String)
    case taught(String)
    case learned(String)
}

private struct ModeMemoryBadgeView: View {
    let badge: ModeMemoryBadge

    var body: some View {
        switch badge {
        case let .worked(text):
            Text(text)
                .modeText(.footnoteEmphasis, color: ModeColor.tagGreenText)
                .padding(.horizontal, ModeSpacing.memoryCardGap)
                .frame(minHeight: ModeSize.memoryChip)
                .background(ModeColor.tagGreen, in: .capsule)
        case let .taught(text):
            Text(text)
                .font(ModeMemoryFont.badge)
                .foregroundStyle(ModeColor.infoText)
                .padding(.horizontal, ModeSpacing.s)
                .frame(minHeight: ModeSize.memoryBadge)
                .background(ModeColor.infoSoft, in: .capsule)
        case let .learned(text):
            HStack(spacing: ModeSpacing.xs) {
                ModeMo(.idle, size: ModeSize.memoryBadgeMo)
                Text(text).font(ModeMemoryFont.badge).foregroundStyle(ModeColor.tint)
            }
            .padding(.leading, ModeSpacing.xxs)
            .padding(.trailing, ModeSpacing.s)
            .frame(minHeight: ModeSize.memoryBadge)
            .background(ModeColor.tintSoft, in: .capsule)
        }
    }
}

/// One thing Mode remembers, in its own card: a short title, the text (or a routine's numbered steps),
/// then a quiet footer ("Learned today · used 3 times") and/or a tag ("Worked 3 of 3 times").
/// While editing a list, a selection mark sits before the title.
public struct ModeMemoryCard: View {
    private let title: String
    private let text: String?
    private let steps: [String]
    private let footer: String?
    private let badge: ModeMemoryBadge?
    private let selected: Bool?
    private let action: () -> Void

    public init(title: String, text: String?, steps: [String] = [], footer: String?, badge: ModeMemoryBadge? = nil, selected: Bool? = nil,
                action: @escaping () -> Void) {
        self.title = title
        self.text = text
        self.steps = steps
        self.footer = footer
        self.badge = badge
        self.selected = selected
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack(alignment: .top, spacing: ModeSpacing.m) {
                if let selected { ModeLeadingView(selected ? .success : .pending) }
                VStack(alignment: .leading, spacing: ModeSpacing.memoryCardLine) {
                    Text(title).modeText(.calloutEmphasis)
                    // A routine reads steps first, then its notes ("If it goes wrong: …").
                    if steps.isEmpty, let text, !text.isEmpty { Text(text).modeText(.subheadline).lineSpacing(ModeSpacing.xxs) }
                    if !steps.isEmpty {
                        VStack(alignment: .leading, spacing: ModeSpacing.xs) {
                            ForEach(Array(steps.enumerated()), id: \.offset) { index, step in
                                HStack(alignment: .firstTextBaseline, spacing: ModeSpacing.xs + ModeSpacing.xxs) {
                                    Text(verbatim: "\(index + 1).").modeText(.subheadline).monospacedDigit()
                                        .frame(minWidth: ModeSize.memoryChip - ModeSpacing.xxs - ModeSpacing.xxs, alignment: .trailing)
                                    Text(step).modeText(.subheadline).lineSpacing(ModeSpacing.xxs)
                                }
                            }
                        }
                        .padding(.top, ModeSpacing.xxs)
                    }
                    if !steps.isEmpty, let text, !text.isEmpty { Text(text).modeText(.footnote, color: ModeColor.textSecondary) }
                    if badge != nil || footer != nil {
                        HStack(spacing: ModeSpacing.s) {
                            if let badge { ModeMemoryBadgeView(badge: badge) }
                            if let footer { Text(footer).modeText(badge == nil ? .footnote : .caption, color: ModeColor.textSecondary) }
                        }
                        .padding(.top, badgeIsChip ? ModeSpacing.xxs : ModeSpacing.none)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .multilineTextAlignment(.leading)
            }
            .padding(.horizontal, ModeSpacing.l)
            .padding(.vertical, ModeSpacing.memoryCardPadding)
            .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.memoryCard))
            .modeShadow(in: RoundedRectangle(cornerRadius: ModeRadius.memoryCard))
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(selected == true ? [.isButton, .isSelected] : .isButton)
    }

    /// The green "Worked" chip sits a little lower than the small badges.
    private var badgeIsChip: Bool {
        if case .worked = badge { true } else { false }
    }
}

/// One app's page header: its tile (a letter), its name, and how its tips are used.
public struct ModeMemoryAppHeader: View {
    private let letter: String
    private let title: String
    private let subtitle: String

    public init(letter: String, title: String, subtitle: String) {
        self.letter = letter
        self.title = title
        self.subtitle = subtitle
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.m) {
            Text(letter)
                .font(ModeMemoryFont.appLetter)
                .foregroundStyle(ModeColor.onMemoryAppTile)
                .frame(width: ModeSize.memoryAppTile, height: ModeSize.memoryAppTile)
                .background(ModeColor.memoryAppTile, in: .rect(cornerRadius: ModeSize.tileRadius))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(title).font(ModeMemoryFont.appTitle).tracking(-0.6).foregroundStyle(ModeColor.textPrimary)
                    .lineLimit(2).minimumScaleFactor(ModeTextScale.title)
                    .accessibilityAddTraits(.isHeader)
                Text(subtitle).font(ModeMemoryFont.detail).foregroundStyle(ModeColor.textSecondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// A tip in one app's list: what Mode knows, then whose it is ("You taught this", "Mode learned") and when.
public struct ModeMemoryTipRow: View {
    private let text: String
    private let badge: ModeMemoryBadge?
    private let footer: String?

    public init(text: String, badge: ModeMemoryBadge?, footer: String?) {
        self.text = text
        self.badge = badge
        self.footer = footer
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.memoryCardLine) {
            Text(text).modeText(.callout).lineSpacing(ModeSpacing.xxs + ModeSpacing.xxs / 2)
            if badge != nil || footer != nil {
                HStack(spacing: ModeSpacing.s) {
                    if let badge { ModeMemoryBadgeView(badge: badge) }
                    if let footer { Text(footer).modeText(.caption, color: ModeColor.textSecondary) }
                }
            }
        }
        .padding(.vertical, ModeSpacing.memoryCardPadding)
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(.rect)
        .accessibilityElement(children: .combine)
    }
}

/// A row in its own small card (radius 22), for a link under a list of cards ("Routines in this app").
public struct ModeMemoryLinkCard: View {
    private let row: ModeMemoryRow

    public init(_ title: String, subtitle: String?, action: @escaping () -> Void) {
        row = ModeMemoryRow(title, subtitle: subtitle, leading: .mo, compact: true, action: action)
    }

    public var body: some View {
        row
            .padding(.horizontal, ModeSpacing.l)
            .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.memoryCard))
            .modeShadow(in: RoundedRectangle(cornerRadius: ModeRadius.memoryCard))
    }
}

/// A capsule button in the top bar: Edit (white) or Save (ink).
public struct ModeMemoryBarButton: View {
    private let title: LocalizedStringResource
    private let prominent: Bool
    private let action: () -> Void

    public init(_ title: LocalizedStringResource, prominent: Bool = false, action: @escaping () -> Void) {
        self.title = title
        self.prominent = prominent
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            Text(title)
                .modeText(.calloutEmphasis, color: prominent ? ModeColor.onAccent : ModeColor.textPrimary)
                .padding(.horizontal, ModeSpacing.memoryBarButton)
                .frame(height: ModeSize.touch)
                .background(prominent ? ModeColor.accent : ModeColor.card, in: .capsule)
                .modeShadow(in: Capsule(), lifted: !prominent)
                .contentShape(.capsule)
        }
        .buttonStyle(ModePressStyle())
    }
}

/// The editor's field: a label above, the text in a card below.
public struct ModeMemoryField: View {
    private let label: LocalizedStringResource
    @Binding private var text: String
    private let minHeight: CGFloat?

    public init(_ label: LocalizedStringResource, text: Binding<String>, minHeight: CGFloat? = nil) {
        self.label = label
        self._text = text
        self.minHeight = minHeight
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.memoryCardLine) {
            Text(label).modeText(.footnoteEmphasis, color: ModeColor.textSecondary).padding(.leading, ModeSpacing.xs)
            TextField(text: $text, axis: .vertical) { Text(label) }
                .modeText(.callout)
                .lineSpacing(ModeSpacing.xs)
                .padding(.horizontal, ModeSpacing.l)
                .padding(.vertical, ModeSpacing.memoryCardPadding)
                .frame(maxWidth: .infinity, minHeight: minHeight, alignment: .topLeading)
                .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.memoryCard))
                .modeShadow(in: RoundedRectangle(cornerRadius: ModeRadius.memoryCard))
        }
    }
}

/// A quiet note under the editor: where it was learned (an info mark), how often it worked (a green check).
public struct ModeMemoryNote: View {
    public enum Kind: Sendable { case learned, worked }

    private let kind: Kind
    private let text: String

    public init(_ kind: Kind, _ text: String) {
        self.kind = kind
        self.text = text
    }

    public var body: some View {
        HStack(alignment: .top, spacing: ModeSpacing.memoryCardGap) {
            Image(systemName: kind == .learned ? ModeIcon.info.rawValue : "checkmark.circle")
                .font(ModeIconFont.small)
                .foregroundStyle(kind == .learned ? ModeColor.textSecondary : ModeColor.success)
                .frame(width: ModeSize.memoryNoteIcon)
                .accessibilityHidden(true)
            Text(text).font(ModeMemoryFont.detail).foregroundStyle(ModeColor.textSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, ModeSpacing.xs)
    }
}

/// Delete, on a soft red capsule (the editor; selected items while editing a list).
public struct ModeMemoryDeleteButton: View {
    private let title: String
    private let action: () -> Void

    public init(_ title: String, action: @escaping () -> Void) {
        self.title = title
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            Text(title)
                .modeText(.headline, color: ModeColor.danger)
                .frame(maxWidth: .infinity, minHeight: ModeSize.memoryDelete)
                .background(ModeColor.tileRed, in: .capsule)
                .contentShape(.capsule)
        }
        .buttonStyle(ModePressStyle())
    }
}

/// A tip the user just taught in a chat: what it is for and which app, the tip, Edit and Undo.
public struct ModeLessonCard: View {
    private let heading: LocalizedStringResource
    private let context: String
    private let text: String
    private let editTitle: LocalizedStringResource
    private let undoTitle: LocalizedStringResource
    private let onEdit: () -> Void
    private let onUndo: () -> Void

    public init(heading: LocalizedStringResource, context: String, text: String, editTitle: LocalizedStringResource, undoTitle: LocalizedStringResource,
                onEdit: @escaping () -> Void, onUndo: @escaping () -> Void) {
        self.heading = heading
        self.context = context
        self.text = text
        self.editTitle = editTitle
        self.undoTitle = undoTitle
        self.onEdit = onEdit
        self.onUndo = onUndo
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.m) {
            HStack(spacing: ModeSpacing.m) {
                ModeLeadingView(.iconTile(.tip, background: ModeColor.tintSoft, foreground: ModeColor.tint))
                VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                    Text(heading).modeText(.captionEmphasis, color: ModeColor.tint)
                    Text(context).modeText(.footnote, color: ModeColor.textSecondary).lineLimit(1)
                }
            }
            Text(text).modeText(.bodyEmphasis)
            HStack(spacing: ModeSpacing.s) {
                Button(action: onEdit) { Text(editTitle).modeText(.subheadline).fontWeight(.semibold).padding(.horizontal, ModeSpacing.l).frame(minHeight: ModeSize.touch - ModeSpacing.s) }
                    .buttonStyle(.plain).background(ModeColor.fill, in: .capsule)
                Button(action: onUndo) { Text(undoTitle).modeText(.subheadline).fontWeight(.semibold).padding(.horizontal, ModeSpacing.l).frame(minHeight: ModeSize.touch - ModeSpacing.s) }
                    .buttonStyle(.plain).background(ModeColor.fill, in: .capsule)
            }
        }
        .padding(ModeSpacing.l)
        .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.card))
    }
}
