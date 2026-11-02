import UIKit
import ModeKit

/// Synthetic image for the account check. No screen capture or personal data.
enum ClaudeProbeImage {
    static func make() -> KotlinByteArray {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        let image = UIGraphicsImageRenderer(size: CGSize(width: 160, height: 100), format: format).image { context in
            UIColor.white.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 160, height: 100))
            ("42" as NSString).draw(at: CGPoint(x: 35, y: 15), withAttributes: [
                .font: UIFont.systemFont(ofSize: 60, weight: .bold),
                .foregroundColor: UIColor.black
            ])
        }
        let data = image.jpegData(compressionQuality: 0.9) ?? Data()
        let bytes = KotlinByteArray(size: Int32(data.count))
        for (index, byte) in data.enumerated() { bytes.set(index: Int32(index), value: Int8(bitPattern: byte)) }
        return bytes
    }
}
