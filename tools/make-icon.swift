// Draws Mode's app icon ("Orbit") at 1024 px in the three iOS appearances
// and writes them into the app's asset catalog.
//
//   swift tools/make-icon.swift
//
// A glass phone with an agent orbiting it: the orbit passes behind the phone
// at the top and in front at the bottom, and the agent (the bright dot) leaves
// a fading trail. iOS masks the corners and adds its own glass on top.
import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

struct Variant {
    let file: String
    let background: (UInt32, UInt32)
    let bloom: Double          // white light in the top-left, 0…1
    let ink: UInt32            // strokes and the agent
    let glass: Double          // phone fill opacity
    let phoneStroke: Double
    let backOrbit: Double
    let frontOrbit: Double
    /// The phone's frost: dims the orbit where it passes behind the phone.
    let frost: UInt32
}

let variants = [
    Variant(file: "AppIcon.png", background: (0x7C6CFF, 0x2B20A8), bloom: 0.32, ink: 0xFFFFFF,
            glass: 0.16, phoneStroke: 0.78, backOrbit: 0.24, frontOrbit: 0.80, frost: 0x5446D4),
    Variant(file: "AppIcon-Dark.png", background: (0x1F1E36, 0x08080D), bloom: 0.10, ink: 0xC9C7FF,
            glass: 0.10, phoneStroke: 0.70, backOrbit: 0.22, frontOrbit: 0.75, frost: 0x141322),
    // Tinted: iOS colours a grayscale image itself.
    Variant(file: "AppIcon-Tinted.png", background: (0x1A1A1A, 0x000000), bloom: 0.0, ink: 0xFFFFFF,
            glass: 0.12, phoneStroke: 0.72, backOrbit: 0.22, frontOrbit: 0.75, frost: 0x0D0D0D),
]

// The orbit: an ellipse around the icon's centre, tilted up to the right.
let centre = CGPoint(x: 512, y: 512)
let radiusX: CGFloat = 360
let radiusY: CGFloat = 150
let tilt = -24.0 * Double.pi / 180
/// The agent sits here on the orbit (degrees); the trail runs up to it from `trailFrom`.
let agentAt = -25.0
let trailFrom = 40.0

func orbitPoint(_ degrees: Double) -> CGPoint {
    let t = degrees * Double.pi / 180
    let x = Double(radiusX) * cos(t), y = Double(radiusY) * sin(t)
    return CGPoint(
        x: Double(centre.x) + x * cos(tilt) - y * sin(tilt),
        y: Double(centre.y) + x * sin(tilt) + y * cos(tilt),
    )
}

func orbitArc(from start: Double, to end: Double) -> CGPath {
    let path = CGMutablePath()
    let steps = 240
    for i in 0...steps {
        let p = orbitPoint(start + (end - start) * Double(i) / Double(steps))
        if i == 0 { path.move(to: p) } else { path.addLine(to: p) }
    }
    return path
}

let space = CGColorSpace(name: CGColorSpace.sRGB)!

func color(_ rgb: UInt32, _ alpha: Double = 1) -> CGColor {
    CGColor(
        colorSpace: space,
        components: [CGFloat((rgb >> 16) & 0xFF) / 255, CGFloat((rgb >> 8) & 0xFF) / 255, CGFloat(rgb & 0xFF) / 255, CGFloat(alpha)],
    )!
}

func render(_ v: Variant, into folder: URL) throws {
    let size = 1024
    guard let ctx = CGContext(
        data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0,
        space: space, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue,
    ) else { throw NSError(domain: "icon", code: 1) }
    ctx.translateBy(x: 0, y: CGFloat(size))  // top-left origin, like the design's SVG
    ctx.scaleBy(x: 1, y: -1)
    ctx.setLineCap(.round)

    let bg = CGGradient(colorsSpace: space, colors: [color(v.background.0), color(v.background.1)] as CFArray, locations: [0, 1])!
    ctx.drawLinearGradient(bg, start: .zero, end: CGPoint(x: size, y: size), options: [])
    if v.bloom > 0 {
        let bloom = CGGradient(colorsSpace: space, colors: [color(0xFFFFFF, v.bloom), color(0xFFFFFF, 0)] as CFArray, locations: [0, 1])!
        ctx.drawRadialGradient(bloom, startCenter: CGPoint(x: 287, y: 205), startRadius: 0,
                               endCenter: CGPoint(x: 287, y: 205), endRadius: 614, options: [])
    }

    // The far half of the orbit, behind the phone. Square ends, so the two
    // halves meet without a bright overlap.
    ctx.setLineCap(.butt)
    // The whole orbit at the front's brightness; the phone's frost dims the
    // part behind it, so the depth changes exactly at the phone's edges.
    ctx.setLineWidth(18)
    ctx.setStrokeColor(color(v.ink, v.frontOrbit))
    ctx.addPath(orbitArc(from: 180, to: 360))
    ctx.strokePath()

    // The phone: frosted glass with a bright edge.
    let phone = CGPath(roundedRect: CGRect(x: 352, y: 214, width: 320, height: 596), cornerWidth: 84, cornerHeight: 84, transform: nil)
    ctx.addPath(phone)
    ctx.setFillColor(color(v.frost, 1 - v.backOrbit))
    ctx.fillPath()
    ctx.addPath(phone)
    ctx.setFillColor(color(v.ink, v.glass))
    ctx.fillPath()
    ctx.addPath(phone)
    ctx.setStrokeColor(color(v.ink, v.phoneStroke))
    ctx.strokePath()
    // The Dynamic Island, so it reads as an iPhone even when small.
    ctx.addPath(CGPath(roundedRect: CGRect(x: 462, y: 248, width: 100, height: 30), cornerWidth: 15, cornerHeight: 15, transform: nil))
    ctx.setFillColor(color(v.ink, v.phoneStroke * 0.8))
    ctx.fillPath()

    // The near half, in front of the phone.
    ctx.setStrokeColor(color(v.ink, v.frontOrbit))
    ctx.addPath(orbitArc(from: 0, to: 180))
    ctx.strokePath()

    // The agent's trail: brightening as it reaches the agent.
    ctx.setLineCap(.round)
    let segments = 90
    for i in 0..<segments {
        let a = trailFrom + (agentAt - trailFrom) * Double(i) / Double(segments)
        let b = trailFrom + (agentAt - trailFrom) * Double(i + 1) / Double(segments)
        let seg = CGMutablePath()
        seg.move(to: orbitPoint(a))
        seg.addLine(to: orbitPoint(b))
        ctx.addPath(seg)
        ctx.setStrokeColor(color(v.ink, 0.95 * Double(i + 1) / Double(segments)))
        ctx.setLineWidth(22)
        ctx.strokePath()
    }

    // The agent, with a soft glow.
    let agent = orbitPoint(agentAt)
    ctx.setFillColor(color(v.ink, 0.18))
    ctx.fillEllipse(in: CGRect(x: agent.x - 110, y: agent.y - 110, width: 220, height: 220))
    ctx.setFillColor(color(v.ink))
    ctx.fillEllipse(in: CGRect(x: agent.x - 64, y: agent.y - 64, width: 128, height: 128))

    let url = folder.appendingPathComponent(v.file)
    guard let image = ctx.makeImage(),
          let dest = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil)
    else { throw NSError(domain: "icon", code: 2) }
    CGImageDestinationAddImage(dest, image, nil)
    guard CGImageDestinationFinalize(dest) else { throw NSError(domain: "icon", code: 3) }
    print("wrote \(url.path)")
}

let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
let folder = root.appendingPathComponent("iosApp/Mode/Assets.xcassets/AppIcon.appiconset")
try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
for v in variants { try render(v, into: folder) }
print("agent at \(orbitPoint(agentAt))")
