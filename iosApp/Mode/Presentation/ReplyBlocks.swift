import ModeDesignSystem
import ModeKit

/// Mode's reply blocks (parsed from its Markdown in Kotlin), as the design system draws them.
extension ModeReplyBlock {
    init(_ part: ReplyPart) {
        switch onEnum(of: part) {
        case let .paragraph(p): self = .paragraph(p.text)
        case let .heading(p): self = .heading(level: Int(p.level), p.text)
        case let .bullets(p): self = .bullets(p.items)
        case let .numbered(p): self = .numbered(start: Int(p.start), p.items)
        case let .agenda(p):
            self = .agenda(p.rows.map { row in
                ModeAgendaRow(time: row.start ?? String(localized: L10n.Conversation.allDay), end: row.end, text: row.text, allDay: row.start == nil)
            })
        case let .table(p): self = .table(header: p.header, rows: p.rows)
        case let .quote(p): self = .quote(p.text)
        case let .code(p): self = .code(p.text)
        case .divider: self = .divider
        }
    }
}
