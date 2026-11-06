import SwiftUI

/// Mo's own words in the conversation (its greeting, its reply to a task): small Mo, quiet text.
public struct ModeTalkMoLine: View {
    private let text: String

    public init(_ text: String) { self.text = text }

    public var body: some View {
        HStack(alignment: .top, spacing: ModeSpacing.s + ModeSpacing.xxs) {
            ModeMo(.idle, size: ModeSize.talkLineMo)
            Text(text).modeText(.subheadline, color: ModeColor.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .accessibilityElement(children: .combine)
    }
}

/// Mo's line after a task ("Anything else?"), and when it offers something, the two answers to tap.
public struct ModeTalkNext: View {
    private let text: String
    private let yes: String?
    private let no: String?
    private let onYes: () -> Void
    private let onNo: () -> Void

    public init(_ text: String, yes: String? = nil, no: String? = nil, onYes: @escaping () -> Void = {}, onNo: @escaping () -> Void = {}) {
        self.text = text
        self.yes = yes
        self.no = no
        self.onYes = onYes
        self.onNo = onNo
    }

    private var offers: Bool { yes != nil }

    public var body: some View {
        HStack(alignment: .top, spacing: ModeSpacing.s + ModeSpacing.xxs) {
            ModeMo(.listening, size: ModeSize.talkLineMo)
            VStack(alignment: .leading, spacing: ModeSpacing.s) {
                Text(text).modeText(.bodyEmphasis)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, ModeSpacing.m + ModeSpacing.xxs)
                    .padding(.vertical, ModeSpacing.m - ModeSpacing.xxs)
                    .background(offers ? ModeColor.infoSoft : ModeColor.card,
                                in: UnevenRoundedRectangle(topLeadingRadius: ModeRadius.bubbleTail, bottomLeadingRadius: ModeRadius.bubble,
                                                           bottomTrailingRadius: ModeRadius.bubble, topTrailingRadius: ModeRadius.bubble))
                if let yes, let no {
                    HStack(spacing: ModeSpacing.s) {
                        ModeTalkOption(yes, chosen: true, action: onYes)
                        ModeTalkOption(no, chosen: false, action: onNo)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

/// Why Mo can't hear while it works: the mic is off while it uses the apps.
public struct ModeTalkMicOff: View {
    private let text: LocalizedStringResource

    public init(_ text: LocalizedStringResource) { self.text = text }

    public var body: some View {
        HStack(spacing: ModeSpacing.s + ModeSpacing.xxs) {
            ModeIcon.micOff.image.font(ModeIconFont.small).foregroundStyle(ModeColor.textSecondary).accessibilityHidden(true)
            Text(text).modeText(.subheadline, color: ModeColor.textSecondary).frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, ModeSpacing.m + ModeSpacing.xxs)
        .padding(.vertical, ModeSpacing.m)
        .background(ModeColor.fill, in: .rect(cornerRadius: ModeRadius.bubble))
    }
}

/// A task in the conversation: how it's going (or went), each line Mo said with its time, then the result.
public struct ModeTalkTaskCard: View {
    public enum Status: Sendable { case working, done, partial, failed, stopped }

    public struct Step: Identifiable, Equatable, Sendable {
        public let id: Int
        public let text: String
        public let time: String
        public init(id: Int, text: String, time: String) { self.id = id; self.text = text; self.time = time }
    }

    public struct Row: Identifiable, Equatable, Sendable {
        public let id: Int
        public let title: String
        public let detail: String
        public let done: Bool
        public init(id: Int, title: String, detail: String, done: Bool) { self.id = id; self.title = title; self.detail = detail; self.done = done }
    }

    private let status: Status
    private let label: String
    private let title: String
    private let chip: String?
    private let steps: [Step]
    private let resultLabel: LocalizedStringResource
    private let headline: String
    private let rows: [Row]
    private let openLabel: LocalizedStringResource
    private let wrongLabel: LocalizedStringResource
    private let onOpen: () -> Void
    private let onWrong: () -> Void

    public init(status: Status, label: String, title: String, chip: String?, steps: [Step],
                resultLabel: LocalizedStringResource, headline: String, rows: [Row],
                openLabel: LocalizedStringResource, wrongLabel: LocalizedStringResource,
                onOpen: @escaping () -> Void, onWrong: @escaping () -> Void) {
        self.status = status
        self.label = label
        self.title = title
        self.chip = chip
        self.steps = steps
        self.resultLabel = resultLabel
        self.headline = headline
        self.rows = rows
        self.openLabel = openLabel
        self.wrongLabel = wrongLabel
        self.onOpen = onOpen
        self.onWrong = onWrong
    }

    private var working: Bool { status == .working }

    private var tone: (text: Color, soft: Color, mood: ModeMo.Mood) {
        switch status {
        case .working: (ModeColor.tint, ModeColor.tintSoft, .working)
        case .done: (ModeColor.tagGreenText, ModeColor.tagGreen, .done)
        case .partial: (ModeColor.tileOrangeText, ModeColor.tileOrange, .stuck)
        case .failed: (ModeColor.danger, ModeColor.tileRed, .stuck)
        case .stopped: (ModeColor.textSecondary, ModeColor.fill, .idle)
        }
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.m) {
            header
            if !steps.isEmpty { timeline }
            if !working { result }
        }
        .padding(.horizontal, ModeSpacing.l)
        .padding(.top, ModeSpacing.m + ModeSpacing.xxs)
        .padding(.bottom, ModeSpacing.l)
        .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.talkCard))
        .modeShadow(in: RoundedRectangle(cornerRadius: ModeRadius.talkCard))
    }

    private var header: some View {
        HStack(spacing: ModeSpacing.s + ModeSpacing.xxs) {
            ModeMo(tone.mood, size: ModeSize.talkCardMo)
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(label).modeText(.caption, color: tone.text).fontWeight(.bold).tracking(0.4)
                Text(title).modeText(.calloutEmphasis).lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if let chip {
                Text(chip).modeText(.caption, color: tone.text).fontWeight(.bold)
                    .padding(.horizontal, ModeSpacing.s + ModeSpacing.xxs)
                    .frame(height: ModeSize.talkChip)
                    .background(tone.soft, in: .capsule)
            }
        }
        .accessibilityElement(children: .combine)
    }

    private var timeline: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(steps.enumerated()), id: \.element.id) { index, step in
                let last = index == steps.count - 1
                let now = working && last
                HStack(alignment: .top, spacing: ModeSpacing.s + ModeSpacing.xxs) {
                    VStack(spacing: ModeSpacing.xxs) {
                        Circle().fill(now ? ModeColor.tint : ModeColor.textSecondary)
                            .frame(width: ModeSize.talkStepDot, height: ModeSize.talkStepDot)
                            .padding(.top, ModeSpacing.xs + ModeSpacing.xxs)
                        Rectangle().fill(last ? Color.clear : ModeColor.separator).frame(width: ModeSize.talkStepLine)
                    }
                    .frame(width: ModeSize.talkStepRail)
                    .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                        Text(step.text).modeText(.subheadline, color: now ? ModeColor.textPrimary : ModeColor.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                        Text(step.time).modeText(.caption, color: ModeColor.textSecondary)
                    }
                    .padding(.bottom, last ? 0 : ModeSpacing.s + ModeSpacing.xxs)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .accessibilityElement(children: .combine)
            }
        }
    }

    private var result: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.s + ModeSpacing.xxs) {
            Rectangle().fill(ModeColor.separator).frame(height: ModeSize.talkDivider).padding(.bottom, ModeSpacing.xxs)
            Text(resultLabel).modeText(.caption, color: ModeColor.textSecondary).fontWeight(.bold).textCase(.uppercase).tracking(0.6)
            if !headline.isEmpty {
                Text(headline).modeText(.headline).fixedSize(horizontal: false, vertical: true)
            }
            if !rows.isEmpty {
                VStack(spacing: ModeSpacing.s) {
                    ForEach(rows) { row in resultRow(row) }
                }
            }
            HStack(spacing: ModeSpacing.s) {
                button(openLabel, action: onOpen)
                if status == .partial || status == .failed { button(wrongLabel, action: onWrong) }
            }
        }
    }

    private func resultRow(_ row: Row) -> some View {
        HStack(spacing: ModeSpacing.s + ModeSpacing.xxs) {
            (row.done ? ModeIcon.checkmark : ModeIcon.close).image.font(ModeIconFont.small).fontWeight(.bold)
                .foregroundStyle(row.done ? ModeColor.tagGreenText : ModeColor.danger)
                .frame(width: ModeSize.talkRowIcon, height: ModeSize.talkRowIcon)
                .background(row.done ? ModeColor.tagGreen : ModeColor.tileRed, in: .circle)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(row.title).modeText(.calloutEmphasis).fixedSize(horizontal: false, vertical: true)
                if !row.detail.isEmpty {
                    Text(row.detail).modeText(.footnote, color: ModeColor.textSecondary).fixedSize(horizontal: false, vertical: true)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, ModeSpacing.m)
        .padding(.vertical, ModeSpacing.s + ModeSpacing.xxs)
        .background(ModeColor.fill, in: .rect(cornerRadius: ModeRadius.bubble))
        .accessibilityElement(children: .combine)
    }

    private func button(_ title: LocalizedStringResource, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).modeText(.subheadlineEmphasis)
                .padding(.horizontal, ModeSpacing.l)
                .frame(height: ModeSize.talkCardButton)
                .background(ModeColor.fill, in: .capsule)
        }
        .buttonStyle(ModePressStyle())
    }
}
