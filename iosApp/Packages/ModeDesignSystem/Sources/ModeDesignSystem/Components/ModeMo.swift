import SwiftUI

/// Mo, Mode's character: a soft orange blob whose face says what Mode is doing.
/// Drawn from the canvas's Mo component (a 120-point grid), so it stays sharp at any size
/// and works in widgets and Live Activities.
public struct ModeMo: View {
    public enum Mood: String, CaseIterable, Sendable {
        /// Ready for a task.
        case idle
        /// Hearing you.
        case listening
        /// Working in your apps.
        case working
        /// Needs an answer or your hands.
        case yourTurn
        /// Finished.
        case done
        /// Couldn't finish.
        case stuck
        /// Resting: nothing to do.
        case sleep
    }

    private let mood: Mood
    private let size: CGFloat
    private let tint: Color

    public init(_ mood: Mood = .idle, size: CGFloat = ModeSize.moRow, tint: Color = ModeColor.brand) {
        self.mood = mood
        self.size = size
        self.tint = tint
    }

    public var body: some View {
        ZStack {
            MoShape(MoParts.shadow).fill(ModeColor.moInk.opacity(0.12))
            MoShape(MoParts.body).fill(tint)
            MoShape(MoParts.shine).fill(Color.white.opacity(0.38))
            face
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }

    private var ink: Color { ModeColor.moInk }

    @ViewBuilder private var face: some View {
        switch mood {
        case .idle:
            MoShape { p in p.ellipse(47, 58, 5, 8); p.ellipse(73, 58, 5, 8) }.fill(ink)
            MoShape { p in p.move(52, 76); p.quad(60, 83, 68, 76) }.stroke(ink, style: line(4))
        case .listening:
            MoShape { p in p.ellipse(49, 52, 5, 8); p.ellipse(75, 52, 5, 8); p.ellipse(62, 78, 4, 4) }.fill(ink)
            MoShape { p in p.move(110, 46); p.quad(117, 60, 110, 74) }.stroke(ink.opacity(0.45), style: line(3.5))
            MoShape { p in p.move(114, 38); p.quad(124, 60, 114, 82) }.stroke(ink.opacity(0.25), style: line(3.5))
        case .working:
            MoShape { p in p.move(39, 60); p.line(54, 57); p.move(66, 57); p.line(81, 60) }.stroke(ink, style: line(5))
            MoShape { p in p.move(54, 78); p.line(66, 77) }.stroke(ink, style: line(4))
            MoShape { p in p.move(2, 46); p.line(9, 46); p.move(0, 60); p.line(8, 60); p.move(2, 74); p.line(9, 74) }
                .stroke(ink.opacity(0.35), style: line(3.5))
        case .yourTurn:
            MoShape { p in p.ellipse(46, 59, 8, 8); p.ellipse(74, 59, 8, 8); p.ellipse(60, 80, 5, 4) }.fill(ink)
            MoShape { p in p.ellipse(49, 56, 2.6, 2.6); p.ellipse(77, 56, 2.6, 2.6) }.fill(Color.white)
            MoShape { p in p.ellipse(100, 22, 13, 13) }.fill(ModeColor.moBadge)
            MoShape { p in p.ellipse(100, 22, 13, 13) }.stroke(Color.white, lineWidth: 3 * size / 120)
            MoShape { p in p.move(100, 15); p.line(100, 23) }.stroke(Color.white, style: line(3.5))
            MoShape { p in p.ellipse(100, 29, 2, 2) }.fill(Color.white)
        case .done:
            MoShape { p in p.move(40, 61); p.quad(47, 51, 54, 61); p.move(66, 61); p.quad(73, 51, 80, 61) }.stroke(ink, style: line(5))
            MoShape { p in p.move(47, 73); p.quad(60, 89, 73, 73); p.closeSubpath() }.fill(ink)
            MoShape { p in
                p.move(103, 12); p.line(106, 21); p.line(115, 24); p.line(106, 27); p.line(103, 36)
                p.line(100, 27); p.line(91, 24); p.line(100, 21); p.closeSubpath()
            }.fill(ModeColor.moSparkle)
        case .stuck:
            MoShape { p in p.move(38, 50); p.line(52, 54); p.move(68, 54); p.line(82, 50) }.stroke(ink, style: line(3.5))
            MoShape { p in p.ellipse(46, 63, 4.5, 4.5); p.ellipse(74, 63, 4.5, 4.5) }.fill(ink)
            MoShape { p in p.move(49, 81); p.quad(54.5, 76, 60, 81); p.quad(65.5, 86, 71, 81) }.stroke(ink, style: line(4))
            MoShape { p in
                p.move(98, 30); p.cubic(98, 30, 91, 40, 91, 45); p.cubic(91, 49, 94, 52, 98, 52)
                p.cubic(102, 52, 105, 49, 105, 45); p.cubic(105, 40, 98, 30, 98, 30); p.closeSubpath()
            }.fill(ModeColor.moDrop)
        case .sleep:
            MoShape { p in p.move(39, 60); p.quad(46.5, 67, 54, 60); p.move(66, 60); p.quad(73.5, 67, 81, 60) }.stroke(ink, style: line(4.5))
            MoShape { p in p.move(55, 79); p.quad(60, 82, 65, 79) }.stroke(ink, style: line(3.5))
            MoShape { p in p.move(92, 14); p.line(104, 14); p.line(92, 27); p.line(104, 27) }.stroke(ink.opacity(0.55), style: line(3.5))
            MoShape { p in p.move(108, 2); p.line(115, 2); p.line(108, 10); p.line(115, 10) }.stroke(ink.opacity(0.35), style: line(3))
        }
    }

    /// A round-capped line, its width given on the 120-point grid.
    private func line(_ width: CGFloat) -> StrokeStyle {
        StrokeStyle(lineWidth: width * size / 120, lineCap: .round, lineJoin: .round)
    }
}

/// Builds one part of Mo on the 120-point grid and scales it to the frame.
private struct MoShape: Shape {
    let build: @Sendable (inout MoPath) -> Void

    init(_ build: @escaping @Sendable (inout MoPath) -> Void) { self.build = build }

    func path(in rect: CGRect) -> Path {
        var p = MoPath(scale: min(rect.width, rect.height) / 120, origin: rect.origin)
        build(&p)
        return p.path
    }
}

private enum MoParts {
    static let shadow: @Sendable (inout MoPath) -> Void = { $0.ellipse(60, 113, 30, 4) }
    static let body: @Sendable (inout MoPath) -> Void = { p in
        p.move(60, 12); p.cubic(90, 12, 106, 30, 106, 60); p.cubic(106, 92, 88, 108, 60, 108)
        p.cubic(32, 108, 14, 92, 14, 60); p.cubic(14, 30, 30, 12, 60, 12); p.closeSubpath()
    }
    static let shine: @Sendable (inout MoPath) -> Void = { $0.ellipse(42, 32, 12, 6.5, rotation: -24) }
}

/// Path commands in the canvas's SVG coordinates.
private struct MoPath {
    var path = Path()
    let scale: CGFloat
    let origin: CGPoint

    private func pt(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: origin.x + x * scale, y: origin.y + y * scale) }
    mutating func move(_ x: CGFloat, _ y: CGFloat) { path.move(to: pt(x, y)) }
    mutating func line(_ x: CGFloat, _ y: CGFloat) { path.addLine(to: pt(x, y)) }
    mutating func quad(_ cx: CGFloat, _ cy: CGFloat, _ x: CGFloat, _ y: CGFloat) { path.addQuadCurve(to: pt(x, y), control: pt(cx, cy)) }
    mutating func cubic(_ c1x: CGFloat, _ c1y: CGFloat, _ c2x: CGFloat, _ c2y: CGFloat, _ x: CGFloat, _ y: CGFloat) {
        path.addCurve(to: pt(x, y), control1: pt(c1x, c1y), control2: pt(c2x, c2y))
    }
    mutating func closeSubpath() { path.closeSubpath() }
    mutating func ellipse(_ cx: CGFloat, _ cy: CGFloat, _ rx: CGFloat, _ ry: CGFloat, rotation: CGFloat = 0) {
        let rect = CGRect(x: -rx * scale, y: -ry * scale, width: 2 * rx * scale, height: 2 * ry * scale)
        let c = pt(cx, cy)
        let t = CGAffineTransform(rotationAngle: rotation * .pi / 180).concatenating(CGAffineTransform(translationX: c.x, y: c.y))
        path.addEllipse(in: rect, transform: t)
    }
}

#Preview {
    VStack {
        HStack { ForEach(ModeMo.Mood.allCases, id: \.self) { ModeMo($0, size: 48) } }
        ModeMo(.idle, size: 120)
    }
    .padding()
    .background(ModeColor.page)
}
