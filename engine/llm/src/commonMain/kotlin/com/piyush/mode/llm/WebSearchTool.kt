package com.piyush.mode.llm

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The provider's own web search, asked for with one marker in the tools list; each client maps it to its
 * shape (ChatGPT's web_search, Claude's web_search_20260209, OpenRouter's ":online" models). A plan that
 * does not allow it answers with a 400, which callers treat as "not available".
 */
object WebSearchTool {
    const val TYPE = "web_search"
    val MARKER: JsonObject = JsonObject(mapOf("type" to JsonPrimitive(TYPE)))

    fun isMarker(tool: JsonElement): Boolean = (tool as? JsonObject)?.get("type")?.jsonPrimitive?.content == TYPE && "function" !in tool.jsonObject
    fun wanted(tools: JsonArray): Boolean = tools.any(::isMarker)
    fun functions(tools: JsonArray): JsonArray = JsonArray(tools.filterNot(::isMarker))
}
