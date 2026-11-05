import SwiftUI

/// The plan on a dark card (the Lock Screen, the Dynamic Island): a bar per step (done in Mo's orange,
/// the one being done softer, the rest faint) and the steps' names spread under them, the current one bold.
public struct ModeActivityBars: View {
    private let segments: [ModeSegments.Segment]

    public init(_ segments: [ModeSegments.Segment]) {
        self.segments = segments
    }

    public var body: some View {
        VStack(spacing: ModeSpacing.activityBarGap) {
            HStack(spacing: ModeSpacing.activityBarGap) {
                ForEach(Array(segments.enumerated()), id: \.offset) { _, segment in
                    Capsule().fill(fill(segment.state)).frame(height: ModeSize.activityBar)
                }
            }
            HStack(spacing: ModeSpacing.xs) {
                ForEach(Array(segments.enumerated()), id: \.offset) { index, segment in
                    if index > 0 { Spacer(minLength: ModeSpacing.xs) }
                    Text(segment.label)
                        .modeText(.caption, color: segment.state == .now ? ModeColor.heroText : ModeColor.onDarkLabel)
                        .fontWeight(segment.state == .now ? .semibold : .regular)
                        .lineLimit(1)
                }
            }
        }
        .accessibilityElement(children: .combine)
    }

    private func fill(_ state: ModeSegments.State) -> Color {
        switch state {
        case .done: ModeColor.brand
        case .now: ModeColor.activityNow
        case .next: ModeColor.onDarkTrack
        }
    }
}

/// Beside the camera while Mode works: a ring that fills as the plan's steps get done.
public struct ModeIslandRing: View {
    private let fraction: Double

    public init(fraction: Double) {
        self.fraction = fraction
    }

    public var body: some View {
        ZStack {
            Circle().stroke(ModeColor.onDarkTrack, lineWidth: ModeSize.islandRingLine)
            Circle().trim(from: 0, to: max(0, min(1, fraction)))
                .stroke(ModeColor.brand, style: StrokeStyle(lineWidth: ModeSize.islandRingLine, lineCap: .butt))
                .rotationEffect(.degrees(-90))
        }
        .padding(ModeSize.islandRingLine / 2)
        .frame(width: ModeSize.islandRing, height: ModeSize.islandRing)
        .accessibilityHidden(true)
    }
}

/// The marks the Live Activity's status uses (outlined, as on the boards).
public enum ModeActivityGlyph: String, Sendable {
    case needsYou = "exclamationmark.circle"
    case yourTurn = "lock"
    /// The iPhone is locked: the task waits for it to unlock.
    case locked = "lock.fill"
    case done = "checkmark"
    case couldntFinish = "exclamationmark.triangle"
    case stopped = "stop.circle"
    case paused = "pause.fill"

    public var image: Image { Image(systemName: rawValue) }
}

/// The red Stop disc on a dark card.
public struct ModeActivityStopDisc: View {
    public init() {}

    public var body: some View {
        RoundedRectangle(cornerRadius: ModeSpacing.xxs + ModeSpacing.xxs / 2)
            .fill(ModeColor.stopOnDark)
            .frame(width: ModeSize.activityStopSquare, height: ModeSize.activityStopSquare)
            .frame(width: ModeSize.activityStop, height: ModeSize.activityStop)
            .background(ModeColor.stopDisc, in: .circle)
    }
}

/// What Mode is doing, asks or found, two lines at most: Bricolage on the Lock Screen, SF semibold in the island.
/// Inline markdown (**bold**) is kept.
public struct ModeActivityTitle: View {
    private let text: String
    private let island: Bool

    public init(_ text: String, island: Bool) {
        self.text = text
        self.island = island
    }

    public var body: some View {
        Group {
            if island {
                Text(ModeInlineText.attributed(text)).modeText(.headline)
            } else {
                Text(ModeInlineText.attributed(text)).font(ModeActivityFont.title).tracking(-0.17).foregroundStyle(ModeColor.textPrimary)
            }
        }
        .lineLimit(2)
        .minimumScaleFactor(0.85)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
