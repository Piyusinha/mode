package com.piyush.mode.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.piyush.mode.core.storage.FileStore
import com.piyush.mode.feature.runs.data.RunRepository
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.math.roundToInt

/** Past runs step by step (screenshots, model answers, timings) and the app event log. */
@Composable
fun LogsScreen(store: FileStore, runs: RunRepository, decodeImage: (ByteArray) -> ImageBitmap) {
    var refresh by remember { mutableIntStateOf(0) }
    var openRun by remember { mutableStateOf<String?>(null) }
    var showEvents by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val run = openRun
        when {
            run != null -> RunDetail(runs, run, decodeImage) { openRun = null }
            showEvents -> {
                TextButton(onClick = { showEvents = false }) { Text("← Runs") }
                val lines = store.read("events.log")?.decodeToString()?.lines()?.filter { it.isNotBlank() }?.takeLast(300).orEmpty()
                SelectionContainer {
                    Text(lines.reversed().joinToString("\n"), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
            else -> {
                Text(
                    "Saved on this iPhone: Files › On My iPhone › Mode › logs.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { refresh++ }) { Text("Refresh") }
                    OutlinedButton(onClick = { showEvents = true }) { Text("App events") }
                    OutlinedButton(onClick = { store.delete(""); refresh++ }) { Text("Delete all") }
                }
                val list = remember(refresh) { runs.runs() }
                if (list.isEmpty()) Text("No runs yet.")
                for (r in list) {
                    Card(Modifier.fillMaxWidth().clickable { openRun = r.id }) {
                        Column(Modifier.padding(10.dp)) {
                            Text(r.task, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                listOfNotNull(
                                    r.outcome,
                                    r.model?.substringAfter('/'),
                                    r.steps?.let { "$it steps" },
                                    r.millis?.let { "${it / 1000}s" },
                                    r.costUsd?.let { "$" + money(it) },
                                    r.tag,
                                ).filter { it.isNotEmpty() }.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(r.id, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RunDetail(runs: RunRepository, id: String, decodeImage: (ByteArray) -> ImageBitmap, back: () -> Unit) {
    TextButton(onClick = back) { Text("← Runs") }
    val events = remember(id) { runs.events(id) }
    val run = remember(id) { runs.run(id) }
    run?.let {
        Text(it.task, style = MaterialTheme.typography.titleMedium)
        Text("${it.outcome}: ${it.summary}", style = MaterialTheme.typography.bodySmall)
    }
    for ((step, group) in events.filter { it.int("step") != null }.groupBy { it.int("step")!! }) {
        var showPrompt by remember(id, step) { mutableStateOf(false) }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Step $step", style = MaterialTheme.typography.titleSmall)
                for (e in group) when (e.str("event")) {
                    "look" -> {
                        Text("looked: ${e.int("texts")} texts, ${e.long("ms")} ms", style = MaterialTheme.typography.labelSmall)
                        runs.image(id, e.str("image"))?.let { bytes ->
                            runCatching { decodeImage(bytes) }.getOrNull()?.let {
                                Image(it, "step $step", Modifier.fillMaxWidth().height(320.dp), contentScale = ContentScale.Fit)
                            }
                        }
                    }
                    "ask" -> {
                        Text(
                            "asked ${e.str("model")}" + (if (e.str("stepped_up") == "true") " (stepped up)" else ""),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        TextButton(onClick = { showPrompt = !showPrompt }) { Text(if (showPrompt) "Hide prompt" else "Show prompt") }
                        if (showPrompt) SelectionContainer {
                            Text(e.str("prompt"), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                    "answer" -> SelectionContainer {
                        Text(
                            (e.str("error").takeIf { it.isNotEmpty() }?.let { "MODEL ERROR: $it" }
                                ?: "${e.str("tool")} ${e["arguments"] ?: ""}") +
                                "\n${e.long("ms")} ms · ${e.int("prompt_tokens") ?: "?"} in / ${e.int("completion_tokens") ?: "?"} out · $${money(e.double("cost_usd") ?: 0.0)}" +
                                (e.str("text").takeIf { it.isNotEmpty() }?.let { "\ntext: $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    "act" -> Text(
                        "→ ${e.str("action")}: ${e.str("outcome")} (${e.long("ms")} ms)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private fun JsonElement?.text(): String = (this as? JsonPrimitive)?.content.orEmpty()
private fun JsonObject.str(name: String) = this[name].text()
private fun JsonObject.int(name: String) = (this[name] as? JsonPrimitive)?.intOrNull
private fun JsonObject.long(name: String) = (this[name] as? JsonPrimitive)?.longOrNull
private fun JsonObject.double(name: String) = (this[name] as? JsonPrimitive)?.doubleOrNull
private fun money(usd: Double): String = ((usd * 10000).roundToInt() / 10000.0).toString()
