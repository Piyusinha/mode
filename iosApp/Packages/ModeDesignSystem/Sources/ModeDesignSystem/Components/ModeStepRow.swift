import SwiftUI

/// One step of a run: what the agent did and why.
public struct ModeStepRow: View {
    public enum State: Sendable { case done, failed, current }

    private let index: Int
    private let title: String
    private let detail: String?
    private let state: State

    public init(index: Int, title: String, detail: String?, state: State) {
        self.index = index
        self.title = title
        self.detail = detail
        self.state = state
    }

    public var body: some View {
        HStack(alignment: .top, spacing: ModeSpacing.m) {
            marker
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(title).modeText(.body)
                if let detail { Text(detail).modeText(.footnote, color: ModeColor.textSecondary) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, ModeSpacing.s)
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private var marker: some View {
        switch state {
        case .done: ModeIcon.done.image.foregroundStyle(ModeColor.success)
        case .failed: ModeIcon.failed.image.foregroundStyle(ModeColor.danger)
        case .current: ProgressView().tint(ModeColor.tint)
        }
    }
}
