package com.piyush.mode.llm

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64

/**
 * OpenAI's Responses API on the user's ChatGPT plan ("Continue with ChatGPT"). The plan's rules: store
 * false, stream true, no temperature or output limits; system text goes in `instructions`. Function
 * tools and images work. The plan pays, so a call costs Mode nothing (tokens are still counted).
 */
class ResponsesClient(
    private val http: HttpClient,
    /** A fresh access token (refreshed by its owner when needed), or null when not signed in. */
    private val accessToken: suspend () -> String?,
    private val baseUrl: String = "https://api.openai.com/v1",
    /** The plan's share for Mode ran out (to show when, on the account page). */
    private val onLimit: () -> Unit = {},
) : LlmClient {

    override suspend fun chat(model: String, messages: List<ChatMessage>, tools: JsonArray): ChatResult {
        val token = accessToken()?.takeIf { it.isNotBlank() } ?: throw LlmFailed(SIGN_IN_AGAIN)
        val response = http.post("$baseUrl/responses") {
            header("Authorization", "Bearer $token")
            header("Accept", "text/event-stream")
            setBody(TextContent(request(model, messages, tools).toString(), ContentType.Application.Json))
        }
        val text = response.bodyAsText()
        try {
            if (!response.status.isSuccess()) throw failure(response.status.value, text)
            return parse(text)
        } catch (e: LlmFailed) {
            if (e.message == SHARE_USED_UP) onLimit()
            throw e
        }
    }

    companion object {
        val EFFORTS = setOf("low", "medium", "high")
        const val SIGN_IN_AGAIN = "ChatGPT needs you to sign in again: open Settings → AI."
        const val SHARE_USED_UP = "Your ChatGPT plan's share for Mode is used up for now. Raise it in ChatGPT → Settings → Usage, or switch to OpenRouter in Mode's Settings."
        const val NOT_ELIGIBLE = "Continue with ChatGPT needs a Plus or Pro plan. You can connect OpenRouter in Settings instead."

        /** The request body: Mode's chat messages and function tools in the Responses shape. */
        fun request(model: String, messages: List<ChatMessage>, tools: JsonArray): JsonObject = buildJsonObject {
            // "gpt-6.1-terra@low": the model, and the reasoning effort the user picked for it.
            put("model", model.substringBefore('@'))
            model.substringAfter('@', "").takeIf { it in EFFORTS }?.let { put("reasoning", buildJsonObject { put("effort", it) }) }
            put("store", false)
            put("stream", true)
            val system = messages.filter { it.role == "system" }.joinToString("\n\n") { it.text }
            if (system.isNotBlank()) put("instructions", system)
            put("input", buildJsonArray {
                messages.filter { it.role != "system" }.forEach { m ->
                    add(buildJsonObject {
                        put("type", "message")
                        put("role", m.role)
                        put("content", buildJsonArray {
                            m.parts.forEach { part ->
                                when (part) {
                                    is ChatMessage.Part.Text -> add(buildJsonObject {
                                        put("type", if (m.role == "assistant") "output_text" else "input_text")
                                        put("text", part.text)
                                    })
                                    is ChatMessage.Part.Image -> add(buildJsonObject {
                                        put("type", "input_image")
                                        put("image_url", "data:image/jpeg;base64," + Base64.encode(part.jpeg))
                                    })
                                }
                            }
                        })
                    })
                }
            })
            if (tools.isNotEmpty()) {
                // Mode's tools are in the chat-completions shape {"type":"function","function":{…}}.
                put("tools", buildJsonArray {
                    tools.forEach { t ->
                        val f = t.jsonObject["function"]?.jsonObject ?: return@forEach
                        add(buildJsonObject {
                            put("type", "function")
                            f["name"]?.let { put("name", it) }
                            f["description"]?.let { put("description", it) }
                            f["parameters"]?.let { put("parameters", it) }
                        })
                    }
                })
                put("tool_choice", "required")
            }
        }

        /** The streamed events: the finished response carries the tool call or the text, and the usage. */
        fun parse(stream: String): ChatResult {
            var final: JsonObject? = null
            for (line in stream.lineSequence()) {
                val data = line.removePrefix("data:").trim().takeIf { line.startsWith("data:") && it.isNotEmpty() && it != "[DONE]" } ?: continue
                val event = runCatching { Json.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
                when (event["type"]?.jsonPrimitive?.contentOrNull) {
                    "response.completed" -> final = event["response"]?.jsonObject
                    "response.failed", "error" -> {
                        val error = event["response"]?.jsonObject?.get("error")?.jsonObject ?: event["error"]?.jsonObject ?: event
                        throw failure(0, error.toString())
                    }
                }
            }
            val response = final ?: Json.parseToJsonElement(stream.trim()).jsonObject.takeIf { "output" in it }
                ?: throw LlmFailed("ChatGPT ended without an answer")
            val output = response["output"]?.jsonArray.orEmpty().map { it.jsonObject }
            val call = output.firstOrNull { it["type"]?.jsonPrimitive?.contentOrNull == "function_call" }?.let { c ->
                val args = c["arguments"]?.jsonPrimitive?.contentOrNull.orEmpty()
                ToolCall(
                    c["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    runCatching { Json.parseToJsonElement(args).jsonObject }.getOrElse { JsonObject(mapOf("raw" to JsonPrimitive(args))) },
                )
            }
            val text = output.filter { it["type"]?.jsonPrimitive?.contentOrNull == "message" }
                .flatMap { it["content"]?.jsonArray.orEmpty() }
                .mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }.joinToString("\n")
            val usage = response["usage"]?.jsonObject
            fun tokens(name: String) = usage?.get(name)?.jsonPrimitive?.intOrNull ?: 0
            val cached = usage?.get("input_tokens_details")?.jsonObject?.get("cached_tokens")?.jsonPrimitive?.intOrNull ?: 0
            return ChatResult(call, text, Usage(tokens("input_tokens"), tokens("output_tokens"), costUsd = 0.0, cachedTokens = cached))
        }

        /** Plain words for the plan's errors (developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery). */
        fun failure(status: Int, body: String): LlmFailed = when {
            "subscription_sharing_usage_limit_exceeded" in body || status == 429 -> LlmFailed(SHARE_USED_UP)
            "subscription_sharing_user_not_eligible" in body || status == 403 -> LlmFailed(NOT_ELIGIBLE)
            "subscription_sharing_invalid_user" in body || status == 401 -> LlmFailed(SIGN_IN_AGAIN)
            else -> LlmFailed("ChatGPT: ${if (status > 0) "HTTP $status: " else ""}${body.take(300)}", status400 = status == 400)
        }
    }
}
