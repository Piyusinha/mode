import SwiftUI

/// The running task, shown above the tab bar: what it is, what it's doing, and Stop.
public struct ModeRunningBar: View {
    private let title: String
    private let status: String
    private let stopLabel: LocalizedStringResource
    private let onStop: () -> Void

    public init(title: String, status: String, stopLabel: LocalizedStringResource, onStop: @escaping () -> Void) {
        self.title = title
        self.status = status
        self.stopLabel = stopLabel
        self.onStop = onStop
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.m) {
            ProgressView().tint(ModeColor.tint)
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(title).modeText(.subheadline).lineLimit(1)
                Text(status).modeText(.caption, color: ModeColor.textSecondary).lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: onStop) { ModeIcon.stop.image.frame(width: ModeSize.touch, height: ModeSize.touch) }
                .buttonStyle(.plain)
                .foregroundStyle(ModeColor.textPrimary)
                .accessibilityLabel(Text(stopLabel))
        }
        .padding(.leading, ModeSpacing.l)
    }
}
