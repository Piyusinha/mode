import ModeDesignSystem
import SwiftUI

/// A model's tile: its maker's logo when Mode knows it (Claude orange, OpenAI grey, others in the accent),
/// otherwise the maker's first letter.
enum ModelTile {
    static func leading(for id: String, name: String) -> ModeRowLeading {
        if let logo = ModeLogo.forModel(id) {
            return switch logo {
            case .claude: .logoTile(logo, background: ModeColor.tileOrange, foreground: ModeColor.tileOrangeText)
            case .openAI: .logoTile(logo, background: ModeColor.tileGray, foreground: ModeColor.textPrimary)
            default: .logoTile(logo, background: ModeColor.tintSoft, foreground: ModeColor.tint)
            }
        }
        let maker = id.contains("/") ? String(id.split(separator: "/").first ?? "") : name
        return .letterTile(String(maker.prefix(1)).uppercased(), background: ModeColor.tintSoft, foreground: ModeColor.tint)
    }
}
