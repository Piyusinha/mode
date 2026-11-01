// Draws Mode's app icon ("Gesture M") at 1024 px in the three iOS appearances
// and writes them into the app's asset catalog.
//
//   swift tools/make-icon.swift
//
// One stroke draws the M, fading in where it began like a finger's swipe, and
// ends in a tap with its ripple. iOS masks the corners and adds its own glass.
import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

struct Variant {
    let file: String
    let background: (UInt32, UInt32)
    let trail: (UInt32, Double, UInt32)  // start colour, start opacity, end colour
    let ring: (UInt32, Double)
    let dot: UInt32
}

let variants = [
    Variant(file: "AppIcon.png", background: (0x8583FF, 0x3B39C4), trail: (0xFFFFFF, 0.42, 0xFFFFFF), ring: (0xFFFFFF, 0.40), dot: 0xFFFFFF),
    Variant(file: "AppIcon-Dark.png", background: (0x1F1E36, 0x08080D), trail: (0x8E8CFF, 0.50, 0xCFCEFF), ring: (0xA9A8FF, 0.45), dot: 0xE4E3FF),
    // Tinted: iOS colours a grayscale image itself.
    Variant(file: "AppIcon-Tinted.png", background: (0x1A1A1A, 0x000000), trail: (0xFFFFFF, 0.38, 0xFFFFFF), ring: (0xFFFFFF, 0.38), dot: 0xFFFFFF),
]

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
    // Top-left origin, like the design's SVG.
    ctx.translateBy(x: 0, y: CGFloat(size))
    ctx.scaleBy(x: 1, y: -1)

    let bg = CGGradient(colorsSpace: space, colors: [color(v.background.0), color(v.background.1)] as CFArray, locations: [0, 1])!
    ctx.drawLinearGradient(bg, start: .zero, end: CGPoint(x: size, y: size), options: [])

    // The ripple sits behind the stroke, around where the swipe ends.
    ctx.setStrokeColor(color(v.ring.0, v.ring.1))
    ctx.setLineWidth(22)
    ctx.strokeEllipse(in: CGRect(x: 700 - 148, y: 700 - 148, width: 296, height: 296))

    let m = CGMutablePath()
    m.move(to: CGPoint(x: 228, y: 700))
    m.addLine(to: CGPoint(x: 228, y: 264))
    m.addLine(to: CGPoint(x: 464, y: 524))
    m.addLine(to: CGPoint(x: 700, y: 264))
    m.addLine(to: CGPoint(x: 700, y: 700))
    ctx.saveGState()
    ctx.addPath(m.copy(strokingWithWidth: 112, lineCap: .round, lineJoin: .round, miterLimit: 10))
    ctx.clip()
    let trail = CGGradient(colorsSpace: space, colors: [color(v.trail.0, v.trail.1), color(v.trail.2)] as CFArray, locations: [0, 1])!
    ctx.drawLinearGradient(trail, start: CGPoint(x: 228, y: 0), end: CGPoint(x: 700, y: 0), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
    ctx.restoreGState()

    ctx.setFillColor(color(v.dot))
    ctx.fillEllipse(in: CGRect(x: 700 - 88, y: 700 - 88, width: 176, height: 176))

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
