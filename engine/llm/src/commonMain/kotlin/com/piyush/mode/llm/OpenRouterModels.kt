package com.piyush.mode.llm

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One model in OpenRouter's catalogue, with prices in US dollars per token. */
class ModelInfo(
    val id: String,
    val name: String,
    val promptUsdPerToken: Double,
    val completionUsdPerToken: Double,
    val cacheReadUsdPerToken: Double?,
    val takesImages: Boolean,
    val toolCalls: Boolean,
    val contextLength: Int?,
)

/** OpenRouter's public model catalogue (no key needed). */
class OpenRouterModels(
    private val http: HttpClient,
    private val baseUrl: String = "https://openrouter.ai/api/v1",
) {
    /** The catalogue as OpenRouter sends it, for caching as-is. */
    suspend fun fetchRaw(): String {
        val response = http.get("$baseUrl/models")
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) throw LlmFailed("model list: HTTP ${response.status.value}")
        return body
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Every model in a catalogue response; entries without prices are skipped. */
        fun parse(body: String): List<ModelInfo> =
            json.parseToJsonElement(body).jsonObject["data"]?.jsonArray.orEmpty().mapNotNull { element ->
                val m = element as? JsonObject ?: return@mapNotNull null
                val pricing = m["pricing"] as? JsonObject ?: return@mapNotNull null
                fun price(key: String) = pricing[key]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                val architecture = m["architecture"] as? JsonObject
                val inputs = architecture?.get("input_modalities")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
                val parameters = m["supported_parameters"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
                ModelInfo(
                    id = m["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                    name = m["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    promptUsdPerToken = price("prompt") ?: return@mapNotNull null,
                    completionUsdPerToken = price("completion") ?: return@mapNotNull null,
                    cacheReadUsdPerToken = price("input_cache_read"),
                    takesImages = "image" in inputs,
                    toolCalls = "tools" in parameters,
                    contextLength = m["context_length"]?.jsonPrimitive?.intOrNull,
                )
            }
    }
}
