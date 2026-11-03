package com.piyush.mode.llm

import com.piyush.mode.core.telemetry.Field
import com.piyush.mode.core.telemetry.Operation
import com.piyush.mode.core.telemetry.Telemetry
import com.piyush.mode.core.telemetry.trace
import kotlinx.serialization.json.JsonArray

/** Records no request/response bodies, tool arguments, credentials, or URLs. */
class ObservedLlmClient(
    private val inner: LlmClient,
    private val telemetry: Telemetry,
    private val provider: ModelProvider? = null,
) : LlmClient {
    override fun forProvider(provider: ModelProvider): LlmClient =
        ObservedLlmClient(inner.forProvider(provider), telemetry, provider)

    override suspend fun chat(model: String, messages: List<ChatMessage>, tools: JsonArray): ChatResult =
        telemetry.trace(Operation.ModelRequest, buildMap {
            put(Field.Model, model.substringBefore('@'))
            model.substringAfter('@', "").takeIf { it.isNotEmpty() }?.let { put(Field.Effort, it) }
            provider?.let { put(Field.Provider, it.name) }
        }) { span ->
            inner.chat(model, messages, tools).also { result ->
                span.fields(mapOf(
                    Field.InputTokens to result.usage.promptTokens.toString(),
                    Field.OutputTokens to result.usage.completionTokens.toString(),
                    Field.CachedTokens to result.usage.cachedTokens.toString(),
                    Field.CacheWriteTokens to result.usage.cacheWriteTokens.toString(),
                ))
            }
        }
}
