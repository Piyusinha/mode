package com.piyush.mode.design

import androidx.annotation.DrawableRes

/**
 * The logos of AI providers, model makers and voice services, as vectors tinted at runtime.
 * From LobeHub's AI icons (MIT: AI makers, ElevenLabs, Fish Audio), Simple Icons (CC0: Apple,
 * Deepgram) and soniox.com (Soniox); the marks belong to their owners and only name the service
 * the user connects or the model or voice they pick. Generated from the iPhone's Logos.xcassets.
 */
public enum class ModeLogo(@DrawableRes public val res: Int) {
    OpenAI(R.drawable.logo_openai),
    Claude(R.drawable.logo_claude),
    OpenRouter(R.drawable.logo_openrouter),
    Gemini(R.drawable.logo_gemini),
    Meta(R.drawable.logo_meta),
    Mistral(R.drawable.logo_mistral),
    DeepSeek(R.drawable.logo_deepseek),
    Qwen(R.drawable.logo_qwen),
    Grok(R.drawable.logo_grok),
    Moonshot(R.drawable.logo_moonshot),
    Zhipu(R.drawable.logo_zhipu),
    Apple(R.drawable.logo_apple),
    ElevenLabs(R.drawable.logo_elevenlabs),
    FishAudio(R.drawable.logo_fishaudio),
    Soniox(R.drawable.logo_soniox),
    Deepgram(R.drawable.logo_deepgram),
    ;

    public companion object {
        /** The maker's logo for a model id ("anthropic/claude-haiku-5.5", "gpt-6.1-terra"), if known. */
        public fun forModel(id: String): ModeLogo? {
            val m = (if ('/' in id) id.substringBefore('/') else id).lowercase()
            return when {
                m.startsWith("anthropic") || m.startsWith("claude") -> Claude
                m.startsWith("openai") || m.startsWith("gpt") || m.startsWith("o1") || m.startsWith("o3") || m.startsWith("o4") -> OpenAI
                m.startsWith("google") || m.startsWith("gemini") -> Gemini
                m.startsWith("meta") -> Meta
                m.startsWith("mistral") -> Mistral
                m.startsWith("deepseek") -> DeepSeek
                m.startsWith("qwen") -> Qwen
                m.startsWith("x-ai") || m.startsWith("grok") -> Grok
                m.startsWith("moonshot") -> Moonshot
                m.startsWith("z-ai") || m.startsWith("zhipu") -> Zhipu
                else -> null
            }
        }
    }
}
