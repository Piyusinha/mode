package com.piyush.mode.llm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** Provider-independent image + tool round-trip. The caller supplies a synthetic JPEG showing 42. */
object ModelConnectionCheck {
    suspend fun verify(client: LlmClient, model: String, image: ByteArray): Boolean {
        require(image.isNotEmpty()) { "Could not create the connection test image." }
        val reply = client.chat(model, listOf(
            ChatMessage("system", "This is a connection test. Read the number in the image and call connection_check exactly once, with observed containing that number. Do not answer in prose."),
            ChatMessage("user", "What number is in this test image?", image),
        ), tools)
        return reply.toolCall?.let { it.name == "connection_check" && it.arguments["observed"]?.jsonPrimitive?.contentOrNull?.trim() == "42" } == true
    }

    private val tools = Json.parseToJsonElement("""[{"type":"function","function":{"name":"connection_check","description":"Report the number in the image","parameters":{"type":"object","properties":{"observed":{"type":"string"}},"required":["observed"]}}}]""").jsonArray
}
