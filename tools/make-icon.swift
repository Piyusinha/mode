// Draws Mode's app icon ("Mo peeking up") at 1024 px in the three iOS appearances
// and writes them into the app's asset catalog.
//
//   swift tools/make-icon.swift
//
// Mo, big, rising from the bottom edge on ink and looking up at you: "ready when you are".
// The shapes are Mo's own (the character in the app, drawn on a 120-unit grid); iOS masks
// the corners and adds its own glass on top. Chosen from six concepts on the Mo canvas
// (row "App icon · Mo as the icon"), replacing the Orbit icon (D27).

import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

struct Variant {
    let file: String
    let background: UInt32
    /// Mo's body.
    let body: UInt32
    /// Eyes and mouth.
    let face: UInt32
    /// The highlight on Mo's head and the glints in its eyes.
    let light: UInt32
    let shine: Double
}

let variants = [
    Variant(file: "AppIcon.png", background: 0x1A1A1F, body: 0xFF6B3D, face: 0x1A1A1F, light: 0xFFFFFF, shine: 0.38),
    // Dark: a deeper ground, the same Mo.
    Variant(file: "AppIcon-Dark.png", background: 0x0E0E12, body: 0xFF6B3D, face: 0x0E0E12, light: 0xFFFFFF, shine: 0.32),
    // Tinted: iOS colours a grayscale image itself, so Mo is light on black and the face stays dark.
    Variant(file: "AppIcon-Tinted.png", background: 0x000000, body: 0xE8E8E8, face: 0x000000, light: 0xFFFFFF, shine: 0.55),
]

let space = CGColorSpace(name: CGColorSpace.sRGB)!

func color(_ rgb: UInt32, _ alpha: Double = 1) -> CGColor {
    CGColor(colorSpace: space, components: [
        CGFloat((rgb >> 16) & 0xFF) / 255, CGFloat((rgb >> 8) & 0xFF) / 255, CGFloat(rgb & 0xFF) / 255, CGFloat(alpha),
    ])!
}

/// Mo's body on the 120-unit grid (the same curve as the in-app Mo).
func moBody() -> CGPath {
    let p = CGMutablePath()
    p.move(to: CGPoint(x: 60, y: 12))
    p.addCurve(to: CGPoint(x: 106, y: 60), control1: CGPoint(x: 90, y: 12), control2: CGPoint(x: 106, y: 30))
    p.addCurve(to: CGPoint(x: 60, y: 108), control1: CGPoint(x: 106, y: 92), control2: CGPoint(x: 88, y: 108))
    p.addCurve(to: CGPoint(x: 14, y: 60), control1: CGPoint(x: 32, y: 108), control2: CGPoint(x: 14, y: 92))
    p.addCurve(to: CGPoint(x: 60, y: 12), control1: CGPoint(x: 14, y: 30), control2: CGPoint(x: 30, y: 12))
    p.closeSubpath()
    return p
}

func render(_ v: Variant, into folder: URL) throws {
    let size = 1024
    guard let ctx = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0, space: space,
                              bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
    else { throw NSError(domain: "icon", code: 1) }

    ctx.setFillColor(color(v.background))
    ctx.fill(CGRect(x: 0, y: 0, width: size, height: size))

    // From the 120-unit grid (y down) to pixels (y up), then Mo's pose: centred at (60, 80), 1.3× bigger,
    // so its body runs off the bottom edge.
    let unit = CGFloat(size) / 120
    ctx.translateBy(x: 0, y: CGFloat(size))
    ctx.scaleBy(x: unit, y: -unit)
    ctx.translateBy(x: 60, y: 80)
    ctx.scaleBy(x: 1.3, y: 1.3)
    ctx.translateBy(x: -60, y: -60)

    ctx.addPath(moBody())
    ctx.setFillColor(color(v.body))
    ctx.fillPath()

    // The highlight on Mo's head, tilted.
    ctx.saveGState()
    ctx.translateBy(x: 42, y: 32)
    ctx.rotate(by: -24 * .pi / 180)
    ctx.setFillColor(color(v.light, v.shine))
    ctx.fillEllipse(in: CGRect(x: -12, y: -6.5, width: 24, height: 13))
    ctx.restoreGState()

    // Eyes looking up, with glints.
    ctx.setFillColor(color(v.face))
    ctx.fillEllipse(in: CGRect(x: 48 - 5, y: 47 - 7.5, width: 10, height: 15))
    ctx.fillEllipse(in: CGRect(x: 72 - 5, y: 47 - 7.5, width: 10, height: 15))
    ctx.setFillColor(color(v.light))
    ctx.fillEllipse(in: CGRect(x: 49.6 - 1.8, y: 43.6 - 1.8, width: 3.6, height: 3.6))
    ctx.fillEllipse(in: CGRect(x: 73.6 - 1.8, y: 43.6 - 1.8, width: 3.6, height: 3.6))

    // A small smile.
    let smile = CGMutablePath()
    smile.move(to: CGPoint(x: 55, y: 63))
    smile.addQuadCurve(to: CGPoint(x: 65, y: 63), control: CGPoint(x: 60, y: 67))
    ctx.addPath(smile)
    ctx.setStrokeColor(color(v.face))
    ctx.setLineWidth(3.5)
    ctx.setLineCap(.round)
    ctx.strokePath()

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
