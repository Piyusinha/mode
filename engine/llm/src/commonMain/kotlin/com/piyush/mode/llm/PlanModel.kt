package com.piyush.mode.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * A model a plan (ChatGPT, Claude) offers, as the provider lists it (Mode's settings decide which to show).
 * [efforts] are the reasoning efforts the provider says it takes, lowest first: empty when it takes none,
 * null when the provider did not say (Mode then offers the common ones).
 */
@Serializable
data class PlanModel(
    val id: String,
    val name: String? = null,
    val efforts: List<String>? = null,
    /** Fields the provider sent beyond the ones Mode reads (a billing or access hint, if any), for the log. */
    val otherFields: List<String> = emptyList(),
) {
    companion object {
        /** Every effort name seen from a provider, lowest first: for ordering, never for filtering. */
        val LADDER: List<String> = listOf("none", "minimal", "low", "medium", "high", "xhigh", "max")

        /** Offered when the provider does not say which efforts a model takes. */
        val COMMON: List<String> = listOf("low", "medium", "high")

        /** An effort name as a provider may send it ("x-high", "XHigh"): lower case, letters only. */
        fun effortName(raw: String): String = raw.lowercase().filter { it.isLetter() }

        /** [efforts] in ladder order, names Mode does not know last in the provider's order. */
        fun ordered(efforts: Collection<String>): List<String> =
            efforts.map(::effortName).filter { it.isNotEmpty() }.distinct()
                .sortedBy { LADDER.indexOf(it).let { i -> if (i < 0) LADDER.size else i } }

        /**
         * Efforts from a model entry, whatever shape the provider uses: an object of levels each with
         * "supported" (Anthropic's capabilities.effort), or a list of names or of objects naming one
         * (ChatGPT's reasoning levels). Null when [e] says nothing usable.
         */
        fun effortsIn(e: JsonElement?): List<String>? = when (e) {
            is JsonObject -> if ((e["supported"] as? JsonPrimitive)?.booleanOrNull == false) emptyList()
                else e.filterKeys { it != "supported" }
                    .filterValues { v -> ((v as? JsonObject)?.get("supported") as? JsonPrimitive)?.booleanOrNull == true }
                    .keys.let(::ordered).takeIf { it.isNotEmpty() || e.isEmpty() }
            is JsonArray -> e.mapNotNull { item ->
                when (item) {
                    is JsonPrimitive -> item.contentOrNull
                    is JsonObject -> listOf("effort", "reasoning_effort", "level", "id", "name")
                        .firstNotNullOfOrNull { (item[it] as? JsonPrimitive)?.contentOrNull }
                    else -> null
                }
            }.let(::ordered)
            else -> null
        }
    }
}
