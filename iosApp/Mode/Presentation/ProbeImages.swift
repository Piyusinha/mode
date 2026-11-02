import ModeKit
import UIKit

/// Images for "Measure on this iPhone": a phone-sized screen with a heading, some lines and one number to
/// read, made by the app (no screen capture, no personal data). The number differs every time.
enum ProbeImages {
    static func make(count: Int = 4) -> [ModelBench.Probe] {
        (0..<count).map { _ in
            let number = Int.random(in: 10...99)
            return ModelBench.Probe(image: bytes(of: image(number)), answer: String(number))
        }
    }

    private static func image(_ number: Int) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        // The size of the screenshots Mode sends (shrunk to 1000 px high).
        let size = CGSize(width: 460, height: 1000)
        return UIGraphicsImageRenderer(size: size, format: format).image { context in
            UIColor(white: 0.96, alpha: 1).setFill() // literal-ok: a synthetic test image, not UI
            context.fill(CGRect(origin: .zero, size: size))
            ("Order status" as NSString).draw(at: CGPoint(x: 32, y: 90), withAttributes: [.font: UIFont.boldSystemFont(ofSize: 34)])
            for row in 0..<6 {
                UIColor(white: 0.85, alpha: 1).setFill() // literal-ok: placeholder lines of the test image
                context.fill(CGRect(x: 32, y: 180 + row * 70, width: 300 - row * 25, height: 18))
            }
            ("Arriving in" as NSString).draw(at: CGPoint(x: 32, y: 640), withAttributes: [.font: UIFont.systemFont(ofSize: 22)])
            ("\(number) min" as NSString).draw(at: CGPoint(x: 32, y: 680), withAttributes: [.font: UIFont.boldSystemFont(ofSize: 56)])
        }
    }

    private static func bytes(of image: UIImage) -> KotlinByteArray {
        let data = image.jpegData(compressionQuality: 0.8) ?? Data()
        let bytes = KotlinByteArray(size: Int32(data.count))
        for (index, byte) in data.enumerated() { bytes.set(index: Int32(index), value: Int8(bitPattern: byte)) }
        return bytes
    }
}
