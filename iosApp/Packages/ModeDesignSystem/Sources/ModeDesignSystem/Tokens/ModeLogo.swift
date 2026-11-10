import SwiftUI

/// The logos of AI providers, model makers and voice services, as vector images that take the tile's colour.
/// From LobeHub's AI icons (@lobehub/icons-static-svg, MIT licence: AI makers, ElevenLabs, Fish Audio), Simple Icons
/// (CC0: Apple, Deepgram), soniox.com (Soniox) and speechify.ai (Speechify); the marks belong to their owners and only name the service the
/// user connects or the model or voice they pick.
public enum ModeLogo: String, CaseIterable, Sendable {
    case openAI = "logo-openai"
    case claude = "logo-claude"
    case openRouter = "logo-openrouter"
    case gemini = "logo-gemini"
    case meta = "logo-meta"
    case mistral = "logo-mistral"
    case deepSeek = "logo-deepseek"
    case qwen = "logo-qwen"
    case grok = "logo-grok"
    case moonshot = "logo-moonshot"
    case zhipu = "logo-zhipu"
    case apple = "logo-apple"
    case elevenLabs = "logo-elevenlabs"
    case speechify = "logo-speechify"
    case fishAudio = "logo-fishaudio"
    case soniox = "logo-soniox"
    case deepgram = "logo-deepgram"

    public var image: Image { Image(rawValue, bundle: .module).renderingMode(.template) }

    /// The maker's logo for a model id ("anthropic/claude-haiku-5.5", "qwen/qwen3.7-flash", "gpt-6.1-terra"), if known.
    public static func forModel(_ id: String) -> ModeLogo? {
        let maker = id.contains("/") ? String(id.split(separator: "/").first ?? "") : id
        let m = maker.lowercased()
        return switch true {
        case m.hasPrefix("anthropic") || m.hasPrefix("claude"): .claude
        case m.hasPrefix("openai") || m.hasPrefix("gpt") || m.hasPrefix("o1") || m.hasPrefix("o3") || m.hasPrefix("o4"): .openAI
        case m.hasPrefix("google") || m.hasPrefix("gemini"): .gemini
        case m.hasPrefix("meta"): .meta
        case m.hasPrefix("mistral"): .mistral
        case m.hasPrefix("deepseek"): .deepSeek
        case m.hasPrefix("qwen"): .qwen
        case m.hasPrefix("x-ai") || m.hasPrefix("grok"): .grok
        case m.hasPrefix("moonshot"): .moonshot
        case m.hasPrefix("z-ai") || m.hasPrefix("zhipu"): .zhipu
        default: nil
        }
    }
}
