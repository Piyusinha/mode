import SwiftUI

/// What a screen shows when it has nothing yet.
public struct ModeEmptyState: View {
    private let icon: ModeIcon
    private let title: LocalizedStringResource
    private let message: LocalizedStringResource

    public init(icon: ModeIcon, title: LocalizedStringResource, message: LocalizedStringResource) {
        self.icon = icon
        self.title = title
        self.message = message
    }

    public var body: some View {
        VStack(spacing: ModeSpacing.m) {
            icon.image.font(ModeTextStyle.largeTitle.font).foregroundStyle(ModeColor.tint).accessibilityHidden(true)
            Text(title).modeText(.title2).multilineTextAlignment(.center)
            Text(message).modeText(.callout, color: ModeColor.textSecondary).multilineTextAlignment(.center)
        }
        .frame(maxWidth: ModeSize.readableWidth)
        .padding(ModeSpacing.xxl)
    }
}

/// A large icon, title and line of text at the top of a setup page.
public struct ModeHero: View {
    private let icon: ModeIcon
    private let title: LocalizedStringResource
    private let message: LocalizedStringResource

    public init(icon: ModeIcon, title: LocalizedStringResource, message: LocalizedStringResource) {
        self.icon = icon
        self.title = title
        self.message = message
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.m) {
            icon.image
                .font(ModeTextStyle.title.font)
                .foregroundStyle(ModeColor.tint)
                .frame(width: ModeSize.iconHero, height: ModeSize.iconHero)
                .glassEffect(.regular, in: .circle)
                .accessibilityHidden(true)
            Text(title).modeText(.largeTitle)
            Text(message).modeText(.callout, color: ModeColor.textSecondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
