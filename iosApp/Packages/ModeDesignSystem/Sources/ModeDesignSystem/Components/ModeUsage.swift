import SwiftUI

/// A provider's page heading: its large logo tile, its name, and a status line with a dot.
public struct ModeProviderHeader: View {
    private let logo: ModeLogo
    private let tileBackground: Color
    private let tileForeground: Color
    private let title: String
    private let status: String
    private let good: Bool

    public init(logo: ModeLogo, tileBackground: Color, tileForeground: Color, title: String, status: String, good: Bool) {
        self.logo = logo
        self.tileBackground = tileBackground
        self.tileForeground = tileForeground
        self.title = title
        self.status = status
        self.good = good
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.l) {
            logo.image.resizable().scaledToFit()
                .frame(width: ModeSize.iconLarge + ModeSpacing.xs, height: ModeSize.iconLarge + ModeSpacing.xs)
                .foregroundStyle(tileForeground)
                .frame(width: ModeSize.contactAvatar, height: ModeSize.contactAvatar)
                .background(tileBackground, in: .rect(cornerRadius: ModeRadius.chip + ModeSpacing.xxs))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(title).modeText(.title)
                HStack(alignment: .firstTextBaseline, spacing: ModeSpacing.xs) {
                    Circle().fill(good ? ModeColor.success : ModeColor.separator).frame(width: ModeSize.stepDot, height: ModeSize.stepDot)
                    Text(status).modeText(.footnote, color: ModeColor.textSecondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

/// One row of a card: a label on the left, its value on the right (an optional dot before it). With an
/// action it opens something (a chevron shows). The label stays whole; a long value wraps to a second line.
public struct ModeValueRow: View {
    private let title: String
    private let value: String
    private let dot: Color?
    private let action: (() -> Void)?

    public init(_ title: String, value: String, dot: Color? = nil, action: (() -> Void)? = nil) {
        self.title = title
        self.value = value
        self.dot = dot
        self.action = action
    }

    public var body: some View {
        if let action {
            Button(action: action) { content.contentShape(.rect) }.buttonStyle(.plain)
        } else {
            content
        }
    }

    private var content: some View {
        HStack(spacing: ModeSpacing.m) {
            Text(title).modeText(.body).fixedSize()
            Spacer(minLength: ModeSpacing.m)
            HStack(spacing: ModeSpacing.xs) {
                if let dot { Circle().fill(dot).frame(width: ModeSize.stepDot, height: ModeSize.stepDot) }
                Text(value).modeText(.body, color: ModeColor.textSecondary).lineLimit(2).multilineTextAlignment(.trailing)
            }
            if action != nil { ModeChevron() }
        }
        .padding(.vertical, ModeSpacing.s)
        .frame(minHeight: ModeSize.rowMin - ModeSpacing.xs)
        .accessibilityElement(children: .combine)
    }
}

/// Steps per day for a week: one bar a day, today in the accent.
public struct ModeWeekBars: View {
    private let values: [Int]
    private let labels: [String]
    private let today: Int

    public init(values: [Int], labels: [String], today: Int) {
        self.values = values
        self.labels = labels
        self.today = today
    }

    public var body: some View {
        let top = max(values.max() ?? 0, 1)
        HStack(alignment: .bottom, spacing: ModeSpacing.s) {
            ForEach(values.indices, id: \.self) { i in
                VStack(spacing: ModeSpacing.xs) {
                    RoundedRectangle(cornerRadius: ModeSpacing.xs)
                        .fill(i == today ? ModeColor.accent : ModeColor.accent.opacity(ModeOpacity.muted))
                        .frame(height: max(ModeSpacing.xs, ModeSize.tile * 1.2 * CGFloat(values[i]) / CGFloat(top)))
                        .frame(height: ModeSize.tile * 1.2, alignment: .bottom)
                    Text(i < labels.count ? labels[i] : "").modeText(.caption, color: ModeColor.textSecondary)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .accessibilityElement(children: .ignore)
    }
}

/// A limit or balance: a title, how much (on the right), a bar, and a line under it.
public struct ModeUsageBar: View {
    private let title: String
    private let value: String
    private let fraction: Double
    private let tint: Color
    private let footnote: String?

    public init(_ title: String, value: String, fraction: Double, tint: Color, footnote: String?) {
        self.title = title
        self.value = value
        self.fraction = fraction
        self.tint = tint
        self.footnote = footnote
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.s) {
            HStack(alignment: .firstTextBaseline) {
                Text(title).modeText(.bodyEmphasis)
                Spacer(minLength: ModeSpacing.m)
                Text(value).modeText(.bodyEmphasis)
            }
            Capsule().fill(ModeColor.fill)
                .frame(height: ModeSpacing.s)
                .overlay(alignment: .leading) {
                    GeometryReader { g in Capsule().fill(tint).frame(width: g.size.width * min(max(fraction, 0), 1)) }
                }
            if let footnote { Text(footnote).modeText(.footnote, color: ModeColor.textSecondary) }
        }
        .padding(.vertical, ModeSpacing.m)
        .accessibilityElement(children: .combine)
    }
}

/// Speed and accuracy for each effort, the chosen one marked: a plain table, values aligned right.
public struct ModeEffortTable: View {
    public struct Row: Identifiable, Equatable, Sendable {
        public var id: String { label }
        public let label: String
        public let speed: String
        public let accuracy: String
        public let selected: Bool

        public init(label: String, speed: String, accuracy: String, selected: Bool) {
            self.label = label
            self.speed = speed
            self.accuracy = accuracy
            self.selected = selected
        }
    }

    private let headers: (String, String, String)
    private let rows: [Row]

    public init(headers: (String, String, String), rows: [Row]) {
        self.headers = headers
        self.rows = rows
    }

    /// The speed and accuracy columns' width, so their values line up under their headings.
    private let column = ModeSize.thumbnailWidth * 0.62

    public var body: some View {
        VStack(spacing: ModeSpacing.xxs) {
            line(Text(headers.0).modeText(.caption, color: ModeColor.textSecondary),
                 Text(headers.1).modeText(.caption, color: ModeColor.textSecondary),
                 Text(headers.2).modeText(.caption, color: ModeColor.textSecondary))
            ForEach(rows) { row in
                line(HStack(spacing: ModeSpacing.xs) {
                         Text(row.label).modeText(row.selected ? .bodyEmphasis : .body)
                         if row.selected { ModeIcon.checkmark.image.font(ModeTextStyle.captionEmphasis.font).foregroundStyle(ModeColor.tint) }
                     },
                     Text(row.speed).modeText(.subheadline, color: ModeColor.textSecondary),
                     Text(row.accuracy).modeText(.subheadline, color: ModeColor.textSecondary))
                    .padding(.vertical, ModeSpacing.s)
                    .background(row.selected ? ModeColor.tintSoft : .clear, in: .rect(cornerRadius: ModeRadius.chip))
                    .accessibilityElement(children: .combine)
            }
        }
        .padding(ModeSpacing.xs)
        .background(ModeColor.fill, in: .rect(cornerRadius: ModeRadius.field))
    }

    private func line(_ a: some View, _ b: some View, _ c: some View) -> some View {
        HStack(spacing: ModeSpacing.s) {
            a.frame(maxWidth: .infinity, alignment: .leading)
            b.frame(width: column, alignment: .trailing)
            c.frame(width: column, alignment: .trailing)
        }
        .padding(.horizontal, ModeSpacing.m)
    }
}
