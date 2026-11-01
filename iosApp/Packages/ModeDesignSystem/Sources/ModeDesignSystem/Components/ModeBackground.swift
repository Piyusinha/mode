import SwiftUI

/// The page behind every screen: a calm base with two soft glows at the top.
public struct ModeBackground: View {
    public init() {}

    public var body: some View {
        GeometryReader { proxy in
            ZStack {
                ModeColor.page
                RadialGradient(colors: [ModeColor.glowViolet, .clear], center: .topLeading,
                               startRadius: 0, endRadius: proxy.size.width * ModeSize.glowWidth)
                RadialGradient(colors: [ModeColor.glowPink, .clear], center: .topTrailing,
                               startRadius: 0, endRadius: proxy.size.height * ModeSize.glowHeight)
            }
        }
        .ignoresSafeArea()
    }
}

public extension View {
    /// Puts Mode's page behind this screen.
    func modeScreen() -> some View {
        background(ModeBackground())
            .scrollContentBackground(.hidden)
    }
}
