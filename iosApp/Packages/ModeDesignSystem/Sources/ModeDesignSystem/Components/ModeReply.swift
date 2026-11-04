import SwiftUI

/// One block of a reply, as the app draws it. Text may carry inline Markdown
/// (**bold**, *italic*, `code`, links).
public enum ModeReplyBlock: Hashable, Codable, Sendable {
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
public struct ModeAgendaRow: Hashable, Codable, Sendable {
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

/// How roomy a reply is: the chat, or a compact card (the Lock Screen, the Dynamic Island).
public enum ModeReplyDensity: Sendable {
    case regular, compact

    var text: ModeTextStyle { self == .regular ? .body : .callout }
    var lead: ModeTextStyle { self == .regular ? .body : .subheadline }
    var spacing: CGFloat { self == .regular ? ModeSpacing.chatGap : ModeSpacing.xs }
    /// A compact reply's first line: one line, so the rows below still fit.
    static let compactLines = 1
}

/// A reply drawn the way a good chat app does: comfortable body text, real lists,
/// an agenda card for timed items, tables, quotes and code. Text can be selected.
public struct ModeReply: View {
    private let blocks: [ModeReplyBlock]
    private let density: ModeReplyDensity

    public init(_ blocks: [ModeReplyBlock], density: ModeReplyDensity = .regular) {
        self.blocks = blocks
        self.density = density
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: density.spacing) {
            ForEach(Array(blocks.enumerated()), id: \.offset) { index, block in
                ModeReplyBlockView(block, first: index == 0, density: density)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .textSelection(.enabled)
    }
}

private struct ModeReplyBlockView: View {
    private let block: ModeReplyBlock
    private let first: Bool
    private let density: ModeReplyDensity

    init(_ block: ModeReplyBlock, first: Bool, density: ModeReplyDensity) {
        self.block = block
        self.first = first
        self.density = density
    }

    var body: some View {
        switch block {
        case let .paragraph(text):
            ModeInlineText(text, style: first ? density.lead : density.text)
                .lineLimit(density == .compact ? ModeReplyDensity.compactLines : nil)
        case let .heading(level, text):
            ModeInlineText(text, style: level <= 2 && density == .regular ? .title2 : .headline)
                .padding(.top, first ? ModeSpacing.none : ModeSpacing.xs)
        case let .bullets(items):
            ModeListView(items: items, style: density.text) { _ in ModeReplyGlyph.bullet.text.modeText(.bodyEmphasis, color: ModeColor.tint) }
        case let .numbered(start, items):
            ModeListView(items: items, style: density.text) { index in
                Text((start + index).formatted() + ModeReplyGlyph.numberSuffix.rawValue).modeText(.bodyEmphasis, color: ModeColor.textSecondary).monospacedDigit()
            }
        case let .agenda(rows):
            ModeAgenda(rows: rows, style: density == .regular ? .card : .plain)
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
    let style: ModeTextStyle
    @ViewBuilder let mark: (Int) -> Mark

    var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.s) {
            ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                HStack(alignment: .firstTextBaseline, spacing: ModeSpacing.s) {
                    mark(index).frame(minWidth: ModeSize.listMark, alignment: .leading)
                    ModeInlineText(item, style: style)
                }
            }
        }
    }
}

/// Timed items as an agenda: times in a column, then what. As a card (the chat:
/// an accent bar and separators) or plain rows (the Lock Screen).
public struct ModeAgenda: View {
    public enum Style: Sendable { case card, plain }

    private let rows: [ModeAgendaRow]
    private let style: Style

    public init(rows: [ModeAgendaRow], style: Style = .card) {
        self.rows = rows
        self.style = style
    }

    public var body: some View {
        if style == .plain { plain } else { card }
    }

    private var plain: some View {
        Grid(alignment: .leadingFirstTextBaseline, horizontalSpacing: ModeSpacing.m, verticalSpacing: ModeSpacing.xxs) {
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                GridRow {
                    Text(row.time).modeText(row.allDay ? .captionEmphasis : .callout, color: row.allDay ? ModeColor.textSecondary : ModeColor.textPrimary)
                        .fontWeight(.semibold)
                        .monospacedDigit()
                    ModeInlineText(row.text, style: .callout).lineLimit(1)
                }
            }
        }
    }

    /// The chat's agenda (board Chat): the time in a narrow bold column, then what; tight rows, no card.
    private var card: some View {
        Grid(alignment: .leadingFirstTextBaseline, horizontalSpacing: ModeSpacing.chatGap, verticalSpacing: ModeSpacing.s) {
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                GridRow {
                    VStack(alignment: .leading, spacing: ModeSpacing.none) {
                        Text(row.time).modeText(row.allDay ? .footnoteEmphasis : .calloutEmphasis, color: row.allDay ? ModeColor.textSecondary : ModeColor.textPrimary)
                        if let end = row.end { Text(end).modeText(.caption, color: ModeColor.textSecondary) }
                    }
                    .monospacedDigit()
                    .frame(minWidth: ModeSize.agendaTimeChat, alignment: .leading)
                    ModeInlineText(row.text, style: .callout)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
        .padding(.horizontal, ModeSpacing.xs)
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

/// The running task in its chat (board TeachSteer): Mode's mark, "Working · 0:42" counting up, what it is
/// doing now, and a red Stop. Tapping it opens the live run.
public struct ModeWorkingCard: View {
    private let title: LocalizedStringResource
    private let status: String
    private let since: Date?
    private let stopLabel: LocalizedStringResource
    private let onOpen: () -> Void
    private let onStop: () -> Void

    public init(title: LocalizedStringResource, status: String, since: Date?, stopLabel: LocalizedStringResource,
                onOpen: @escaping () -> Void, onStop: @escaping () -> Void) {
        self.title = title
        self.status = status
        self.since = since
        self.stopLabel = stopLabel
        self.onOpen = onOpen
        self.onStop = onStop
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.m) {
            Button(action: onOpen) {
                HStack(spacing: ModeSpacing.m) {
                    ModeMo(.working, size: ModeSize.moChatCard).accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                        TimelineView(.periodic(from: .now, by: 1)) { context in
                            Text(elapsedLine(at: context.date)).modeText(.footnoteEmphasis, color: ModeColor.tint).monospacedDigit()
                        }
                        Text(status).modeText(.subheadlineEmphasis).lineLimit(2).modeShimmer()
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .contentShape(.rect)
            }
            .buttonStyle(.plain)
            Button(action: onStop) {
                RoundedRectangle(cornerRadius: ModeSize.stopGlyphRadius).fill(ModeColor.stopRed)
                    .frame(width: ModeSize.stopGlyph, height: ModeSize.stopGlyph)
                    .frame(width: ModeSize.workingStop, height: ModeSize.workingStop)
                    .background(ModeColor.tileRed, in: .circle)
                    .frame(width: ModeSize.touch, height: ModeSize.touch)
                    .contentShape(.circle)
            }
            .buttonStyle(ModePressStyle())
            .accessibilityLabel(Text(stopLabel))
        }
        .padding(.leading, ModeSpacing.m)
        .padding(.trailing, ModeSpacing.s)
        .padding(.vertical, ModeSpacing.m)
        .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.chatCard))
        .overlay(RoundedRectangle(cornerRadius: ModeRadius.chatCard).strokeBorder(ModeColor.brand, lineWidth: ModeSize.chatRing))
        .animation(ModeMotion.standard, value: status)
    }

    private func elapsedLine(at now: Date) -> String {
        let head = String(localized: title)
        guard let since else { return head }
        let seconds = max(0, Int(now.timeIntervalSince(since)))
        return "\(head) · \(seconds / 60):" + String(format: "%02d", seconds % 60)
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
