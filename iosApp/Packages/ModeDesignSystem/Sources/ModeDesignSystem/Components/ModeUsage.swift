import SwiftUI

/// A provider's page heading: its big logo tile, its name, a status line with a dot, and Mo on the right
/// (awake when the provider is in use, asleep when not).
public struct ModeProviderHeader: View {
    private let logo: ModeLogo
    private let tileBackground: Color
    private let tileForeground: Color
    private let title: String
    private let status: String
    private let good: Bool
    private let mood: ModeMo.Mood?

    public init(logo: ModeLogo, tileBackground: Color, tileForeground: Color, title: String, status: String, good: Bool,
                mood: ModeMo.Mood? = nil) {
        self.logo = logo
        self.tileBackground = tileBackground
        self.tileForeground = tileForeground
        self.title = title
        self.status = status
        self.good = good
        self.mood = mood
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.headerGap) {
            logo.image.resizable().scaledToFit()
                .frame(width: ModeSize.iconLarge, height: ModeSize.iconLarge)
                .foregroundStyle(tileForeground)
                .frame(width: ModeSize.providerTile, height: ModeSize.providerTile)
                .background(tileBackground, in: .rect(cornerRadius: ModeSize.providerTileRadius))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: ModeSpacing.xs) {
                Text(title).modeText(.title).lineLimit(1).minimumScaleFactor(ModeTextScale.title).accessibilityAddTraits(.isHeader)
                ModeStatusLine(status, dot: good ? ModeColor.success : ModeColor.ring)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if let mood { ModeMo(mood, size: ModeSize.moProvider) }
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
            Text(title).modeText(.callout).fixedSize()
            Spacer(minLength: ModeSpacing.m)
            HStack(spacing: ModeSpacing.xs + ModeSpacing.xxs) {
                if let dot { Circle().fill(dot).frame(width: ModeSize.statusDot, height: ModeSize.statusDot) }
                Text(value).modeText(.callout, color: ModeColor.textSecondary).lineLimit(2).multilineTextAlignment(.trailing)
            }
            if action != nil { ModeChevron() }
        }
        .padding(.vertical, ModeSpacing.s)
        .frame(minHeight: action == nil ? ModeSize.valueRow : ModeSize.rowMin)
        .accessibilityElement(children: .combine)
    }
}

/// Steps per day for a week: one bar a day on a faint track colour, today in Mo's orange.
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
        HStack(alignment: .bottom, spacing: ModeSpacing.xs + ModeSpacing.xxs) {
            ForEach(values.indices, id: \.self) { i in
                VStack(spacing: ModeSpacing.xs) {
                    RoundedRectangle(cornerRadius: ModeRadius.bar)
                        .fill(i == today ? ModeColor.brand : ModeColor.track)
                        .frame(height: max(ModeSpacing.xs, ModeSize.weekBars * CGFloat(values[i]) / CGFloat(top)))
                        .frame(height: ModeSize.weekBars, alignment: .bottom)
                    Text(i < labels.count ? labels[i] : "").modeText(.caption, color: ModeColor.textSecondary)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .accessibilityElement(children: .ignore)
    }
}

/// A limit or balance: a title, how much (on the right; [prominent] sets it large, for credits left), a bar,
/// and a line under it.
public struct ModeUsageBar: View {
    private let title: String
    private let value: String
    private let fraction: Double
    private let tint: Color
    private let footnote: String?
    private let prominent: Bool

    public init(_ title: String, value: String, fraction: Double, tint: Color, footnote: String?, prominent: Bool = false) {
        self.title = title
        self.value = value
        self.fraction = fraction
        self.tint = tint
        self.footnote = footnote
        self.prominent = prominent
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: prominent ? ModeSpacing.s : ModeSpacing.xs + ModeSpacing.xxs) {
            HStack(alignment: .firstTextBaseline) {
                Text(title).modeText(.subheadlineEmphasis)
                Spacer(minLength: ModeSpacing.m)
                Text(value).modeText(prominent ? .title2 : .subheadlineEmphasis)
            }
            Capsule().fill(ModeColor.track)
                .frame(height: ModeSize.usageBar)
                .overlay(alignment: .leading) {
                    GeometryReader { g in Capsule().fill(tint).frame(width: g.size.width * min(max(fraction, 0), 1)) }
                }
                .clipShape(.capsule)
            if let footnote { Text(footnote).modeText(.footnote, color: ModeColor.textSecondary) }
        }
        .padding(.vertical, prominent ? ModeSpacing.headerGap : ModeSpacing.m)
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

    public var body: some View {
        VStack(spacing: ModeSpacing.xxs) {
            line(Text(headers.0), Text(headers.1), Text(headers.2))
                .modeText(.caption, color: ModeColor.textSecondary)
                .padding(.bottom, ModeSpacing.xs)
            ForEach(rows) { row in
                line(HStack(spacing: ModeSpacing.xs) {
                         Text(row.label).modeText(row.selected ? .subheadlineEmphasis : .subheadline)
                         if row.selected { ModeIcon.checkmark.image.font(ModeIconFont.chevron.weight(.bold)).foregroundStyle(ModeColor.success) }
                     },
                     Text(row.speed).modeText(.subheadline, color: ModeColor.textSecondary),
                     Text(row.accuracy).modeText(.subheadline, color: ModeColor.textSecondary))
                    .padding(.vertical, ModeSpacing.s)
                    .background(row.selected ? ModeColor.card : .clear, in: .rect(cornerRadius: ModeRadius.tableRow))
                    .accessibilityElement(children: .combine)
            }
        }
        .padding(.horizontal, ModeSpacing.xs)
        .padding(.vertical, ModeSpacing.m - ModeSpacing.xxs)
        .background(ModeColor.cardRaised, in: .rect(cornerRadius: ModeRadius.table))
    }

    private func line(_ a: some View, _ b: some View, _ c: some View) -> some View {
        HStack(spacing: ModeSpacing.m) {
            a.lineLimit(1).minimumScaleFactor(0.8).frame(maxWidth: .infinity, alignment: .leading)
            b.lineLimit(1).minimumScaleFactor(0.8).frame(width: ModeSize.effortColumn, alignment: .trailing)
            c.lineLimit(1).minimumScaleFactor(0.8).frame(width: ModeSize.effortColumn + ModeSpacing.m, alignment: .trailing)
        }
        .padding(.horizontal, ModeSpacing.m)
    }
}
