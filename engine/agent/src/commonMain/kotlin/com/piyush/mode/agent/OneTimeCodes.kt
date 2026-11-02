package com.piyush.mode.agent

import com.piyush.mode.llm.Usage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One-time codes (OTPs) the agent sees on screen. The model may read and type them; nothing Mode keeps
 * holds them: every line recorded has them masked, and a screen showing one is not saved. Generic: a 4 to
 * 8 digit number in a line that says what it is (code, OTP, verification, one-time, passcode), or iOS's
 * "From Messages" suggestion above the keyboard.
 */
class OneTimeCodes {
    private val seen = linkedSetOf<String>()

    /** Codes in [texts]; they are masked from now on. True if the screen shows one. */
    fun scan(texts: List<String>): Boolean {
        val found = texts.flatMap(::codesIn)
        seen += found
        return found.isNotEmpty()
    }

    /** [text] with every code seen so far replaced by dots. */
    fun mask(text: String): String =
        if (seen.isEmpty()) text else seen.fold(text) { t, code -> t.replace(code, MASK) }

    /** Remembers a code the agent typed, though it was not seen as one (it is masked from now on). */
    fun typed(text: String) {
        val digits = text.trim()
        if (DIGITS.matches(digits)) seen += digits
    }

    companion object {
        const val MASK: String = "••••••"
        private val DIGITS = Regex("""^\d{4,8}$""")
        private val NUMBER = Regex("""(?<![\d.,])\d{4,8}(?![\d.,])""")
        private val SAYS_CODE = Regex(
            """\b(otp|code|verification|verify|one[- ]?time|passcode|from messages)\b""",
            RegexOption.IGNORE_CASE,
        )

        /** The codes in one line of screen text. */
        fun codesIn(line: String): List<String> =
            if (SAYS_CODE.containsMatchIn(line)) NUMBER.findAll(line).map { it.value }.toList() else emptyList()
    }
}

/** A trace that never records a one-time code: lines are masked and a screen showing one is not kept. */
internal class RedactingTrace(private val inner: AgentTrace, private val codes: OneTimeCodes) : AgentTrace {
    private fun m(s: String) = codes.mask(s)
    private fun m(e: JsonElement): JsonElement = when (e) {
        is JsonPrimitive -> if (e.isString) JsonPrimitive(m(e.content)) else e
        is JsonObject -> JsonObject(e.mapValues { m(it.value) })
        is JsonArray -> JsonArray(e.map { m(it) })
    }

    override fun runStarted(task: String, config: AgentConfig, tools: List<String>) = inner.runStarted(m(task), config, tools)
    override fun decided(question: String, answer: String?, costUsd: Double, error: String?) = inner.decided(m(question), answer?.let(::m), costUsd, error)
    override fun briefed(model: String, brief: String?, costUsd: Double, error: String?) = inner.briefed(model, brief?.let(::m), costUsd, error)

    override fun looked(step: Int, jpeg: ByteArray, imageWidth: Int, imageHeight: Int, texts: List<Triple<String, Int, Int>>, millis: Long) {
        // A screen with a code on it is not saved: an empty image stands in for it.
        val showsCode = codes.scan(texts.map { it.first })
        inner.looked(step, if (showsCode) ByteArray(0) else jpeg, imageWidth, imageHeight, texts.map { Triple(m(it.first), it.second, it.third) }, millis)
    }

    override fun asking(step: Int, model: String, steppedUp: Boolean, prompt: String) = inner.asking(step, model, steppedUp, m(prompt))
    override fun answered(step: Int, model: String, toolName: String?, arguments: JsonObject?, text: String, usage: Usage?, millis: Long, error: String?) {
        // A code typed by the agent is masked even when the screen never named it.
        if (toolName == "type_text") (arguments?.get("text") as? JsonPrimitive)?.content?.let(codes::typed)
        inner.answered(step, model, toolName, arguments?.let { m(it) as JsonObject }, m(text), usage, millis, error)
    }
    override fun acted(step: Int, action: String, op: String?, outcome: String, millis: Long) = inner.acted(step, m(action), op?.let(::m), m(outcome), millis)
    override fun recalled(step: Int, query: String, found: List<String>) = inner.recalled(step, m(query), found.map(::m))
    override fun noted(step: Int, text: String) = inner.noted(step, m(text))
    override fun verifyRejected(step: Int, why: String) = inner.verifyRejected(step, m(why))
    override fun stuck(step: Int, why: String) = inner.stuck(step, m(why))
    override fun runEnded(result: AgentResult) = inner.runEnded(result)
}
