package com.piyush.mode.llm

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebSearchToolTest {
    private val function = buildJsonObject {
        put("type", "function")
        putJsonObject("function") { put("name", "tap"); put("description", "x"); putJsonObject("parameters") { put("type", "object") } }
    }
    private val messages = listOf(ChatMessage("system", "s"), ChatMessage("user", "IndiGo 6E223 status today"))

    @Test
    fun chatGptGetsItsWebSearchAndNoForcedFunctionCall() {
        val body = ResponsesClient.request("gpt-6.1-terra", messages, buildJsonArray { add(WebSearchTool.MARKER) })
        assertEquals("web_search", body.getValue("tools").jsonArray.single().jsonObject.getValue("type").jsonPrimitive.content)
        assertNull(body["tool_choice"], "a lookup wants text back")
        val both = ResponsesClient.request("gpt-6.1-terra", messages, buildJsonArray { add(function); add(WebSearchTool.MARKER) })
        assertEquals(listOf("function", "web_search"), both.getValue("tools").jsonArray.map { it.jsonObject.getValue("type").jsonPrimitive.content })
        assertEquals("required", both.getValue("tool_choice").jsonPrimitive.content)
    }

    @Test
    fun claudeGetsItsServerSideWebSearch() {
        val body = ClaudeOAuthClient.request("claude-sonnet-5-5", messages, buildJsonArray { add(WebSearchTool.MARKER); add(function) })
        val tools = body.getValue("tools").jsonArray.map { it.jsonObject }
        assertEquals("web_search_20260209", tools[0].getValue("type").jsonPrimitive.content)
        assertEquals("web_search", tools[0].getValue("name").jsonPrimitive.content)
        assertEquals("tap", tools[1].getValue("name").jsonPrimitive.content)
    }

    @Test
    fun theMarkerIsToldFromFunctionTools() {
        val tools = buildJsonArray { add(function); add(WebSearchTool.MARKER) }
        assertEquals(true, WebSearchTool.wanted(tools))
        assertEquals(1, WebSearchTool.functions(tools).size)
        assertEquals(false, WebSearchTool.wanted(buildJsonArray { add(function) }))
    }
}
