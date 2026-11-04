import SwiftUI

/// The page behind every screen: plain warm paper (light) or near-black (dark). No glows.
public struct ModeBackground: View {
    public init() {}

    public var body: some View {
        ModeColor.page.ignoresSafeArea()
    }
}

public extension View {
    /// Puts Mode's page behind this screen.
    func modeScreen() -> some View {
        background(ModeBackground())
            .scrollContentBackground(.hidden)
            .toolbarBackground(.hidden, for: .navigationBar)
    }

    /// The soft lift under something floating: a shadow in light, a hairline ring in dark (as on the canvas).
    /// [lifted] false keeps only the dark-mode hairline (a flat card on a list page).
    func modeShadow<S: Shape>(in shape: S, lifted: Bool = true) -> some View {
        modifier(ModeShadowModifier(shape: shape, lifted: lifted))
    }

    /// Fades the page over content scrolling under the top bar or the tab bar.
    func modeEdgeFade(_ edge: VerticalEdge, height: CGFloat? = nil) -> some View {
        overlay(alignment: edge == .top ? .top : .bottom) {
            ModeEdgeFade(edge: edge)
                .frame(height: height ?? (edge == .top ? ModeSize.edgeFadeTop : ModeSize.edgeFadeBottom))
                .ignoresSafeArea(edges: edge == .top ? .top : .bottom)
                .allowsHitTesting(false)
        }
    }
}

/// The page colour fading out: 96% → 60% → clear, as on the canvas.
public struct ModeEdgeFade: View {
    private let edge: VerticalEdge

    public init(edge: VerticalEdge) {
        self.edge = edge
    }

    public var body: some View {
        LinearGradient(stops: [
            .init(color: ModeColor.page.opacity(0.97), location: 0),
            .init(color: ModeColor.page.opacity(0.6), location: 0.55),
            .init(color: ModeColor.page.opacity(0), location: 1),
        ], startPoint: edge == .top ? .top : .bottom, endPoint: edge == .top ? .bottom : .top)
        .accessibilityHidden(true)
    }
}

private struct ModeShadowModifier<S: Shape>: ViewModifier {
    let shape: S
    let lifted: Bool
    @Environment(\.colorScheme) private var scheme

    func body(content: Content) -> some View {
        content
            .overlay(shape.stroke(Color.white.opacity(scheme == .dark ? 0.08 : 0), lineWidth: 1))
            .shadow(color: ModeColor.moInk.opacity(scheme == .light && lifted ? ModeShadow.opacity : 0), radius: ModeShadow.radius, y: ModeShadow.y)
    }
}
