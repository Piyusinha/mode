import SwiftUI

/// One block of a reply, as the app draws it. Text may carry inline Markdown
/// (**bold**, *italic*, `code`, links).
public enum ModeReplyBlock: Equatable, Sendable {
    case paragraph(String)
    case heading(level: Int, String)
    case bullets([String])
    case numbered(start: Int, [String])
    case agenda([ModeAgendaRow])
    case table(header: [String], rows: [[String]])
    case quote(String)
    case code(String)
    case divider
}

/// One line of an agenda: "14:00" over "15:00", then what.
public struct ModeAgendaRow: Equatable, Sendable {
    public let time: String
    public let end: String?
    public let text: String
    /// All-day items show their time smaller and without the accent bar's weight.
    public let allDay: Bool

    public init(time: String, end: String?, text: String, allDay: Bool) {
        self.time = time
        self.end = end
        self.text = text
        self.allDay = allDay
    }
}

/// A reply drawn the way a good chat app does: comfortable body text, real lists,
/// an agenda card for timed items, tables, quotes and code. Text can be selected.
public struct ModeReply: View {
    private let blocks: [ModeReplyBlock]

    public init(_ blocks: [ModeReplyBlock]) {
        self.blocks = blocks
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.m) {
            ForEach(Array(blocks.enumerated()), id: \.offset) { index, block in
                ModeReplyBlockView(block, first: index == 0)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .textSelection(.enabled)
    }
}

private struct ModeReplyBlockView: View {
    private let block: ModeReplyBlock
    private let first: Bool

    init(_ block: ModeReplyBlock, first: Bool) {
        self.block = block
        self.first = first
    }

    var body: some View {
        switch block {
        case let .paragraph(text):
            ModeInlineText(text, style: .body)
        case let .heading(level, text):
            ModeInlineText(text, style: level <= 2 ? .title2 : .headline)
                .padding(.top, first ? ModeSpacing.none : ModeSpacing.xs)
        case let .bullets(items):
            ModeListView(items: items) { _ in ModeReplyGlyph.bullet.text.modeText(.bodyEmphasis, color: ModeColor.tint) }
        case let .numbered(start, items):
            ModeListView(items: items) { index in
                Text((start + index).formatted() + ModeReplyGlyph.numberSuffix.rawValue).modeText(.bodyEmphasis, color: ModeColor.textSecondary).monospacedDigit()
            }
        case let .agenda(rows):
            ModeAgenda(rows: rows)
        case let .table(header, rows):
            ModeReplyTable(header: header, rows: rows)
        case let .quote(text):
            HStack(alignment: .top, spacing: ModeSpacing.m) {
                Capsule().fill(ModeColor.tint).frame(width: ModeSize.quoteBar)
                ModeInlineText(text, style: .body, color: ModeColor.textSecondary)
            }
            .fixedSize(horizontal: false, vertical: true)
        case let .code(text):
            ScrollView(.horizontal, showsIndicators: false) {
                Text(text).font(ModeTypography.code).foregroundStyle(ModeColor.textPrimary).padding(ModeSpacing.m)
            }
            .background(ModeColor.fill, in: .rect(cornerRadius: ModeRadius.chip))
        case .divider:
            Rectangle().fill(ModeColor.separator).frame(height: ModeSize.separator).padding(.vertical, ModeSpacing.xs)
        }
    }
}

/// Small marks used in replies.
enum ModeReplyGlyph: String {
    case bullet = "•"
    case numberSuffix = "."

    var text: Text { Text(verbatim: rawValue) }
}

/// Text with inline Markdown: **bold**, *italic*, `code`, ~~struck~~ and links (in the accent colour).
public struct ModeInlineText: View {
    private let markdown: String
    private let style: ModeTextStyle
    private let color: Color

    public init(_ markdown: String, style: ModeTextStyle, color: Color = ModeColor.textPrimary) {
        self.markdown = markdown
        self.style = style
        self.color = color
    }

    public var body: some View {
        Text(Self.attributed(markdown))
            .modeText(style, color: color)
            .lineSpacing(ModeSpacing.xxs)
            .tint(ModeColor.tint)
            .fixedSize(horizontal: false, vertical: true)
    }

    static func attributed(_ markdown: String) -> AttributedString {
        let options = AttributedString.MarkdownParsingOptions(interpretedSyntax: .inlineOnlyPreservingWhitespace, failurePolicy: .returnPartiallyParsedIfPossible)
        return (try? AttributedString(markdown: markdown, options: options)) ?? AttributedString(markdown)
    }
}

/// A list with hanging indents: the mark, then the item's text wrapping under itself.
private struct ModeListView<Mark: View>: View {
    let items: [String]
    @ViewBuilder let mark: (Int) -> Mark

    var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.s) {
            ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                HStack(alignment: .firstTextBaseline, spacing: ModeSpacing.s) {
                    mark(index).frame(minWidth: ModeSize.listMark, alignment: .leading)
                    ModeInlineText(item, style: .body)
                }
            }
        }
    }
}

/// Timed items as an agenda card: times in a column, an accent bar, then what.
public struct ModeAgenda: View {
    private let rows: [ModeAgendaRow]

    public init(rows: [ModeAgendaRow]) {
        self.rows = rows
    }

    public var body: some View {
        VStack(spacing: ModeSpacing.none) {
            ForEach(Array(rows.enumerated()), id: \.offset) { index, row in
                HStack(alignment: .center, spacing: ModeSpacing.m) {
                    VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                        Text(row.time).modeText(row.allDay ? .captionEmphasis : .bodyEmphasis, color: row.allDay ? ModeColor.textSecondary : ModeColor.textPrimary)
                        if let end = row.end { Text(end).modeText(.caption, color: ModeColor.textSecondary) }
                    }
                    .monospacedDigit()
                    .frame(width: ModeSize.agendaTime, alignment: .leading)
                    Capsule()
                        .fill(row.allDay ? ModeColor.tintSoft : ModeColor.tint)
                        .frame(width: ModeSize.agendaBar)
                        .frame(maxHeight: .infinity)
                    ModeInlineText(row.text, style: .body)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .fixedSize(horizontal: false, vertical: true)
                .padding(.vertical, ModeSpacing.m)
                .padding(.horizontal, ModeSpacing.l)
                if index < rows.count - 1 {
                    Rectangle().fill(ModeColor.separator).frame(height: ModeSize.separator)
                        .padding(.leading, ModeSpacing.l + ModeSize.agendaTime + ModeSpacing.m)
                }
            }
        }
        .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.chip))
    }
}

/// A Markdown table: a bold header, rows with separators; scrolls sideways when wide.
private struct ModeReplyTable: View {
    let header: [String]
    let rows: [[String]]

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            Grid(alignment: .leading, horizontalSpacing: ModeSpacing.l, verticalSpacing: ModeSpacing.s) {
                GridRow {
                    ForEach(Array(header.enumerated()), id: \.offset) { _, cell in ModeInlineText(cell, style: .subheadline, color: ModeColor.textSecondary) }
                }
                Rectangle().fill(ModeColor.separator).frame(height: ModeSize.separator).gridCellUnsizedAxes(.horizontal)
                ForEach(Array(rows.enumerated()), id: \.offset) { index, row in
                    GridRow {
                        ForEach(Array(row.enumerated()), id: \.offset) { _, cell in ModeInlineText(cell, style: .callout) }
                    }
                    if index < rows.count - 1 {
                        Rectangle().fill(ModeColor.separator).frame(height: ModeSize.separator).gridCellUnsizedAxes(.horizontal)
                    }
                }
            }
            .padding(ModeSpacing.l)
        }
        .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.chip))
    }
}

/// The small buttons under a reply: Copy, Try again, Share.
public struct ModeReplyActions: View {
    public struct Action: Identifiable {
        public let id: ModeIcon
        let label: LocalizedStringResource
        let perform: () -> Void

        public init(_ icon: ModeIcon, label: LocalizedStringResource, perform: @escaping () -> Void) {
            self.id = icon
            self.label = label
            self.perform = perform
        }
    }

    private let actions: [Action]

    public init(_ actions: [Action]) {
        self.actions = actions
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.xs) {
            ForEach(actions) { action in
                Button(action: action.perform) {
                    action.id.image
                        .font(ModeTextStyle.subheadline.font)
                        .foregroundStyle(ModeColor.textSecondary)
                        .frame(width: ModeSize.replyAction, height: ModeSize.replyAction)
                        .contentShape(.rect)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(action.label))
            }
        }
        .padding(.leading, -ModeSpacing.s)
    }
}

/// While Mode works: a spinner and what it's doing now, shimmering, with Stop. Tapping opens the live view.
public struct ModeWorkingLine: View {
    private let status: String
    private let stopLabel: LocalizedStringResource
    private let onOpen: () -> Void
    private let onStop: () -> Void

    public init(status: String, stopLabel: LocalizedStringResource, onOpen: @escaping () -> Void, onStop: @escaping () -> Void) {
        self.status = status
        self.stopLabel = stopLabel
        self.onOpen = onOpen
        self.onStop = onStop
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.m) {
            Button(action: onOpen) {
                HStack(spacing: ModeSpacing.m) {
                    ProgressView().controlSize(.small).tint(ModeColor.tint)
                    Text(status).modeText(.callout, color: ModeColor.textSecondary).lineLimit(1).modeShimmer()
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(.rect)
            }
            .buttonStyle(.plain)
            Button(action: onStop) {
                ModeIcon.stop.image.font(ModeTextStyle.caption.font).foregroundStyle(ModeColor.stopRed)
                    .frame(width: ModeSize.replyAction, height: ModeSize.replyAction)
            }
            .buttonStyle(.plain)
            .glassEffect(.regular.interactive(), in: .circle)
            .accessibilityLabel(Text(stopLabel))
        }
        .animation(ModeMotion.standard, value: status)
    }
}

public extension View {
    /// A light sweeping across text, for "working on it" lines.
    func modeShimmer() -> some View {
        modifier(ModeShimmer())
    }
}

private struct ModeShimmer: ViewModifier {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        if reduceMotion {
            content
        } else {
            content.overlay {
                PhaseAnimator([false, true]) { on in
                    LinearGradient(colors: [.clear, ModeColor.textPrimary.opacity(ModeOpacity.shimmer), .clear], startPoint: .leading, endPoint: .trailing)
                        .scaleEffect(x: ModeOpacity.shimmerWidth, anchor: .leading)
                        .offset(x: on ? ModeSize.shimmerTravel : -ModeSize.shimmerTravel)
                } animation: { _ in .linear(duration: ModeMotion.shimmerSeconds) }
                .mask(content)
                .allowsHitTesting(false)
            }
        }
    }
}
