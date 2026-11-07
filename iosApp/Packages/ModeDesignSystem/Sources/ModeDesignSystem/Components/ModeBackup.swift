import SwiftUI

/// The glyphs on the Backup boards' tiles.
public enum ModeBackupIcon: String, Sendable {
    case chats = "bubble.left"
    case memory = "brain"
    case tasks = "list.bullet"
    case repeats = "repeat"
    case settings = "gearshape"
    case signIns = "key"
    case restore = "folder"

    public var image: Image { Image(systemName: rawValue) }

    /// The key leans like the board's (ring up and right).
    var rotation: Angle { self == .signIns ? .degrees(45) : .zero }
}

/// A tile's colour pair on the Backup boards: coral (chats), lilac (memory), blue (tasks), green (repeats),
/// grey (settings), orange (sign-ins).
public enum ModeBackupTone: Sendable {
    case coral, lilac, blue, green, gray, orange

    var background: Color {
        switch self {
        case .coral: ModeColor.tintSoft
        case .lilac: ModeColor.tileLilac
        case .blue: ModeColor.infoSoft
        case .green: ModeColor.tagGreen
        case .gray: ModeColor.tileGray
        case .orange: ModeColor.tileOrange
        }
    }

    var foreground: Color {
        switch self {
        case .coral: ModeColor.tint
        case .lilac: ModeColor.tileLilacText
        case .blue: ModeColor.infoText
        case .green: ModeColor.tagGreenText
        case .gray: ModeColor.textSecondary
        case .orange: ModeColor.tileOrangeText
        }
    }
}

/// One line of what's in a backup, or of what a restore added: a tinted tile or a green tick, a name, and a count.
public struct ModeBackupItem: Identifiable, Equatable, Sendable {
    public enum Mark: Equatable, Sendable {
        case tile(ModeBackupIcon, ModeBackupTone)
        /// Restored: a round green tick.
        case added
    }

    public let id: String
    public let title: String
    public let value: String
    public let mark: Mark

    public init(id: String, title: String, value: String, mark: Mark) {
        self.id = id
        self.title = title
        self.value = value
        self.mark = mark
    }
}

/// A card of [ModeBackupItem]s with hairlines between them, and an optional part under them without one
/// (the sign-ins switch).
public struct ModeBackupList<Footer: View>: View {
    private let items: [ModeBackupItem]
    private let compact: Bool
    private let footer: Footer

    public init(_ items: [ModeBackupItem], compact: Bool = false, @ViewBuilder footer: () -> Footer) {
        self.items = items
        self.compact = compact
        self.footer = footer()
    }

    public var body: some View {
        VStack(spacing: ModeSpacing.none) {
            ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                ModeBackupItemRow(item, compact: compact)
                if index < items.count - 1 {
                    Rectangle().fill(ModeColor.separator).frame(height: ModeSize.separator)
                }
            }
            footer
        }
        .padding(.horizontal, ModeSpacing.l)
        .padding(.vertical, ModeSpacing.xs)
        .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.card))
    }
}

public extension ModeBackupList where Footer == EmptyView {
    init(_ items: [ModeBackupItem], compact: Bool = false) {
        self.init(items, compact: compact) { EmptyView() }
    }
}

/// One [ModeBackupItem]: the count stays whole and the name wraps ("AI and voice / sign-ins").
public struct ModeBackupItemRow: View {
    private let item: ModeBackupItem
    private let compact: Bool

    public init(_ item: ModeBackupItem, compact: Bool = false) {
        self.item = item
        self.compact = compact
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.m) {
            switch item.mark {
            case let .tile(icon, tone): ModeBackupTile(icon, tone: tone)
            case .added:
                ModeIcon.checkmark.image.font(ModeBackupFont.tick)
                    .foregroundStyle(ModeColor.tagGreenText)
                    .frame(width: ModeSize.backupTick, height: ModeSize.backupTick)
                    .background(ModeColor.tagGreen, in: .circle)
                    .accessibilityHidden(true)
            }
            Text(item.title).modeText(.callout).frame(maxWidth: .infinity, alignment: .leading)
            Text(item.value).modeText(.subheadline, color: ModeColor.textSecondary).lineLimit(1).layoutPriority(1)
        }
        .padding(.vertical, ModeSpacing.xs)
        .frame(minHeight: compact ? ModeSize.backupRowCompact : ModeSize.backupRow)
        .accessibilityElement(children: .combine)
    }
}

/// A 32-point rounded tile with a glyph, in one of the Backup tones.
public struct ModeBackupTile: View {
    private let icon: ModeBackupIcon
    private let tone: ModeBackupTone

    public init(_ icon: ModeBackupIcon, tone: ModeBackupTone) {
        self.icon = icon
        self.tone = tone
    }

    public var body: some View {
        icon.image.font(ModeBackupFont.tile)
            .rotationEffect(icon.rotation)
            .foregroundStyle(tone.foreground)
            .frame(width: ModeSize.backupTile, height: ModeSize.backupTile)
            .background(tone.background, in: .rect(cornerRadius: ModeSize.backupTileRadius))
            .accessibilityHidden(true)
    }
}

/// "Include my AI and voice sign-ins": the title, the connected providers under it, and a switch.
public struct ModeBackupSwitchRow: View {
    private let title: LocalizedStringResource
    private let detail: String
    @Binding private var isOn: Bool

    public init(_ title: LocalizedStringResource, detail: String, isOn: Binding<Bool>) {
        self.title = title
        self.detail = detail
        self._isOn = isOn
    }

    public var body: some View {
        Toggle(isOn: $isOn) {
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(title).modeText(.callout)
                Text(detail).modeText(.footnote, color: ModeColor.textSecondary).fixedSize(horizontal: false, vertical: true)
            }
        }
        .tint(ModeColor.switchOn)
        .frame(minHeight: ModeSize.backupSwitchRow)
        .padding(.vertical, ModeSpacing.s)
    }
}

/// Choosing the backup's password: Password and Again, a tick when they match, and the orange note that
/// nobody can get it back.
public struct ModeBackupPasswordCard: View {
    private let passwordLabel: LocalizedStringResource
    private let againLabel: LocalizedStringResource
    @Binding private var password: String
    @Binding private var again: String
    private let matches: Bool
    private let matchLabel: LocalizedStringResource
    private let warning: LocalizedStringResource

    public init(password passwordLabel: LocalizedStringResource, again againLabel: LocalizedStringResource,
                text password: Binding<String>, againText again: Binding<String>, matches: Bool,
                matchLabel: LocalizedStringResource, warning: LocalizedStringResource) {
        self.passwordLabel = passwordLabel
        self.againLabel = againLabel
        self._password = password
        self._again = again
        self.matches = matches
        self.matchLabel = matchLabel
        self.warning = warning
    }

    public var body: some View {
        VStack(spacing: ModeSpacing.none) {
            field(passwordLabel, text: $password, tick: false)
            Rectangle().fill(ModeColor.separator).frame(height: ModeSize.separator)
            field(againLabel, text: $again, tick: matches)
            HStack(alignment: .firstTextBaseline, spacing: ModeSpacing.s) {
                ModeIcon.lock.image.font(ModeBackupFont.note).accessibilityHidden(true)
                Text(warning).fixedSize(horizontal: false, vertical: true)
            }
            .modeText(.footnote, color: ModeColor.tileOrangeText)
            .lineSpacing(ModeSpacing.xxs)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.vertical, ModeSpacing.s + ModeSpacing.xxs)
            .padding(.horizontal, ModeSpacing.m)
            .background(ModeColor.tileOrange, in: .rect(cornerRadius: ModeRadius.backupNote))
        }
        .padding(.horizontal, ModeSpacing.l)
        .padding(.top, ModeSpacing.xs)
        .padding(.bottom, ModeSpacing.m)
        .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.card))
    }

    private func field(_ label: LocalizedStringResource, text: Binding<String>, tick: Bool) -> some View {
        HStack(spacing: ModeSpacing.m) {
            Text(label).modeText(.callout).frame(width: ModeSize.backupFieldLabel, alignment: .leading)
            SecureField(text: text) { Text(verbatim: "") }
                .modeText(.callout)
                .textContentType(.newPassword)
                .accessibilityLabel(Text(label))
            if tick {
                ModeIcon.checkmark.image.font(ModeBackupFont.match).foregroundStyle(ModeColor.tagGreenText)
                    .accessibilityLabel(Text(matchLabel))
            }
        }
        .frame(minHeight: ModeSize.fieldHeight)
    }
}

/// A card with one row that opens something: a tinted tile, its name and a chevron ("Restore from a backup").
public struct ModeBackupLinkRow: View {
    private let title: LocalizedStringResource
    private let icon: ModeBackupIcon
    private let tone: ModeBackupTone
    private let action: () -> Void

    public init(_ title: LocalizedStringResource, icon: ModeBackupIcon, tone: ModeBackupTone, action: @escaping () -> Void) {
        self.title = title
        self.icon = icon
        self.tone = tone
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: ModeSpacing.m) {
                ModeBackupTile(icon, tone: tone)
                Text(title).modeText(.callout).frame(maxWidth: .infinity, alignment: .leading)
                ModeChevron()
            }
            .padding(.horizontal, ModeSpacing.l)
            .frame(minHeight: ModeSize.backupLinkRow)
            .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.card))
            .contentShape(.rect(cornerRadius: ModeRadius.card))
        }
        .buttonStyle(ModePressStyle())
    }
}

/// The backup about to be restored: Mo on a soft tile, "Mode backup", and when, from which iPhone and how big.
public struct ModeBackupFileCard: View {
    private let title: LocalizedStringResource
    private let detail: String

    public init(_ title: LocalizedStringResource, detail: String) {
        self.title = title
        self.detail = detail
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.m) {
            ModeMo(.idle, size: ModeSize.backupFileMo)
                .frame(width: ModeSize.backupFileTile, height: ModeSize.backupFileTile)
                .background(ModeColor.tintSoft, in: .rect(cornerRadius: ModeSize.backupFileTileRadius))
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(title).modeText(.headline)
                Text(detail).modeText(.footnote, color: ModeColor.textSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(ModeSpacing.l)
        .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.card))
        .accessibilityElement(children: .combine)
    }
}

/// The locked backup's password: a lock, the field, and a line under it, ringed in red when the password was wrong.
public struct ModeBackupLockField: View {
    private let prompt: LocalizedStringResource
    @Binding private var text: String
    private let note: String
    private let wrong: Bool

    public init(_ prompt: LocalizedStringResource, text: Binding<String>, note: String, wrong: Bool) {
        self.prompt = prompt
        self._text = text
        self.note = note
        self.wrong = wrong
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.xs) {
            HStack(spacing: ModeSpacing.m) {
                ModeIcon.lock.image.font(ModeBackupFont.field).foregroundStyle(ModeColor.textSecondary).accessibilityHidden(true)
                SecureField(text: $text) { Text(prompt).foregroundStyle(ModeColor.textSecondary) }
                    .modeText(.callout)
                    .textContentType(.password)
            }
            .frame(minHeight: ModeSize.fieldHeight)
            Text(note).modeText(.footnote, color: wrong ? ModeColor.dangerText : ModeColor.textSecondary)
                .lineSpacing(ModeSpacing.xxs)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, ModeSpacing.l)
        .padding(.top, ModeSpacing.xs)
        .padding(.bottom, ModeSpacing.m)
        .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.card))
        .overlay {
            if wrong {
                RoundedRectangle(cornerRadius: ModeRadius.card).strokeBorder(ModeColor.dangerText, lineWidth: ModeSize.backupRing)
                    .padding(-ModeSize.backupRing)
            }
        }
    }
}

/// The board's upload arrow: an arrow up over a line (Make a backup).
public struct ModeBackupUploadGlyph: View {
    public init() {}

    public var body: some View {
        Path { p in
            p.move(to: CGPoint(x: 12, y: 15)); p.addLine(to: CGPoint(x: 12, y: 3))
            p.move(to: CGPoint(x: 7, y: 8)); p.addLine(to: CGPoint(x: 12, y: 3)); p.addLine(to: CGPoint(x: 17, y: 8))
            p.move(to: CGPoint(x: 5, y: 21)); p.addLine(to: CGPoint(x: 19, y: 21))
        }
        .stroke(style: StrokeStyle(lineWidth: ModeSize.backupUploadLine, lineCap: .round, lineJoin: .round))
        .scaleEffect(ModeSize.backupUpload / Self.grid, anchor: .topLeading)
        .frame(width: ModeSize.backupUpload, height: ModeSize.backupUpload, alignment: .topLeading)
        .accessibilityHidden(true)
    }

    /// The glyph is drawn on a 24-point grid, as on the board.
    private static let grid: CGFloat = 24
}

/// The main action on the Backup pages (Make a backup, Restore into Mode, Done): an ink capsule with the upload
/// arrow when [upload], and a spinner in its place while it works.
public struct ModeBackupButton: View {
    private let title: LocalizedStringResource
    private let upload: Bool
    private let busy: Bool
    private let action: () -> Void
    @Environment(\.isEnabled) private var isEnabled

    public init(_ title: LocalizedStringResource, upload: Bool = false, busy: Bool = false, action: @escaping () -> Void) {
        self.title = title
        self.upload = upload
        self.busy = busy
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: ModeSpacing.s) {
                if busy {
                    ProgressView().tint(ModeColor.onAccent)
                } else if upload {
                    ModeBackupUploadGlyph()
                }
                Text(title).lineLimit(1)
            }
            .modeText(.headline, color: ModeColor.onAccent)
            .frame(maxWidth: .infinity, minHeight: ModeSize.primaryButton)
            .background(ModeColor.accent, in: .capsule)
            .contentShape(.capsule)
            // The whole capsule fades, as drawn; iOS doesn't grey the words on top of that.
            .environment(\.isEnabled, true)
            .compositingGroup()
            .opacity(isEnabled || busy ? 1 : ModeOpacity.backupDisabled)
        }
        .buttonStyle(ModePressStyle())
    }
}

/// Restored: Mo, done, and the line under it.
public struct ModeBackupDone: View {
    private let line: String

    public init(_ line: String) {
        self.line = line
    }

    public var body: some View {
        VStack(spacing: ModeSpacing.l) {
            ModeMo(.done, size: ModeSize.backupDoneMo)
            Text(line).modeText(.callout, color: ModeColor.textSecondary)
                .multilineTextAlignment(.center)
                .lineSpacing(ModeSpacing.xxs / 2)
                .padding(.horizontal, ModeSpacing.backupDoneLineInset)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, ModeSpacing.backupDoneTop)
        .padding(.bottom, ModeSpacing.m)
    }
}
