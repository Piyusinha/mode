import SwiftUI

/// Progress as a few segments with labels under them: done ones filled, the current
/// one bold, the rest waiting (the live panel, the Dynamic Island).
public struct ModeSegments: View {
    public enum State: String, Hashable, Codable, Sendable { case done, now, next }

    public struct Segment: Hashable, Codable, Sendable {
        public let label: String
        public let state: State

        public init(_ label: String, state: State) {
            self.label = label
            self.state = state
        }
    }

    private let segments: [Segment]

    public init(_ segments: [Segment]) {
        self.segments = segments
    }

    public var body: some View {
        HStack(alignment: .top, spacing: ModeSpacing.xs) {
            ForEach(Array(segments.enumerated()), id: \.offset) { _, segment in
                VStack(alignment: .leading, spacing: ModeSpacing.s) {
                    Capsule().fill(fill(segment.state)).frame(height: ModeSize.segmentHeight)
                    Text(segment.label)
                        .modeText(segment.state == .now ? .captionEmphasis : .caption,
                                  color: segment.state == .now ? ModeColor.textPrimary : ModeColor.textSecondary)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private func fill(_ state: State) -> Color {
        switch state {
        case .done, .now: ModeColor.accent
        case .next: ModeColor.separator
        }
    }
}
