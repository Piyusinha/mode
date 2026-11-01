package com.piyush.mode.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.piyush.mode.app.graph.ModeGraph
import com.piyush.mode.feature.compare.presentation.CompareRunner
import com.piyush.mode.feature.connection.domain.label
import com.piyush.mode.feature.diagnostics.presentation.HandsCheck
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.settings.data.ModelCatalog
import com.piyush.mode.feature.settings.domain.ModelOption
import com.piyush.mode.feature.memory.data.MemoryRepository
import com.piyush.mode.feature.memory.domain.MemoryKind
import com.piyush.mode.feature.tasks.domain.PendingQuestion
import com.piyush.mode.feature.tasks.presentation.TaskRunner
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun App(graph: ModeGraph, decodeImage: (ByteArray) -> ImageBitmap) {
    val connection by graph.session.state.collectAsState()
    val task by graph.tasks.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val appScope = rememberCoroutineScope()

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                var stopping by remember { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(connection.label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    OutlinedButton(
                        onClick = {
                            stopping = true
                            graph.tasks.stop()
                            appScope.launch {
                                graph.session.stopSession()
                                stopping = false
                            }
                        },
                        enabled = !stopping,
                    ) { Text(if (stopping) "Stopping…" else "Stop session") }
                }
                PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                    listOf("Agent", "Compare", "Logs", "Settings", "Hands").forEachIndexed { i, title ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title, maxLines = 1) })
                    }
                }
                when (tab) {
                    0 -> AgentScreen(graph.tasks, connection.label)
                    1 -> CompareScreen(graph.compare, graph.tasks, graph.models)
                    2 -> LogsScreen(graph.files, graph.runs, decodeImage)
                    3 -> SettingsScreen(graph.settings, graph.models, graph.memory)
                    else -> HandsCheckScreen(graph.handsCheck, decodeImage)
                }
            }
            task.question?.let { QuestionDialog(it, graph.tasks) }
        }
    }
}

@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun AgentScreen(runner: TaskRunner, connection: String) {
    val state by runner.state.collectAsState()
    val focus = LocalFocusManager.current
    var task by remember { mutableStateOf("") }
    var private by remember { mutableStateOf(false) }
    Page {
        Text(connection, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            task, { task = it }, Modifier.fillMaxWidth().heightIn(min = 96.dp),
            label = { Text("What should Mode do?") },
            placeholder = { Text("e.g. Turn on Dark Mode") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { focus.clearFocus(); runner.start(task.trim(), private = private) },
                enabled = !state.running && task.isNotBlank(),
            ) { Text("Run") }
            OutlinedButton(onClick = { runner.stop() }, enabled = state.running) { Text("Stop") }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(private, { private = it })
                Text("Private", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (state.status.isNotBlank()) Text(state.status, style = MaterialTheme.typography.titleSmall)
        for (item in state.remembered) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Remembered: ${item.text}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { runner.undoRemembered(item.id) }) { Text("Undo") }
            }
        }

        for (s in state.steps.reversed()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text("${s.index}. ${s.action}", style = MaterialTheme.typography.bodyMedium)
                    if (s.reason.isNotBlank()) Text(s.reason, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${s.outcome} · ${s.model.substringAfter('/')} · ${s.millis / 100 / 10.0}s · $${money(s.costUsd)}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompareScreen(compare: CompareRunner, runner: TaskRunner, catalog: ModelCatalog) {
    val scope = rememberCoroutineScope()
    val state by compare.state.collectAsState()
    val task by runner.state.collectAsState()
    val focus = LocalFocusManager.current
    var tasks by remember { mutableStateOf("") }
    var models by remember { mutableStateOf(catalog.state.value.recommended.take(4).joinToString("\n") { it.id }) }
    Page {
        Text(
            "Runs every task with every model, each from the Home Screen, and compares success, steps, time and cost. " +
                "Pick safe tasks; anything risky still asks you first.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(tasks, { tasks = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp), label = { Text("Tasks, one per line") })
        OutlinedTextField(models, { models = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp), label = { Text("Models, one per line") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    focus.clearFocus()
                    scope.launch { compare.run(lines(tasks), lines(models)) }
                },
                enabled = !state.running && !task.running && lines(tasks).isNotEmpty() && lines(models).isNotEmpty(),
            ) { Text("Run comparison") }
            OutlinedButton(onClick = { runner.stop() }, enabled = task.running) { Text("Stop current") }
        }
        if (state.running) Text(task.status, style = MaterialTheme.typography.bodySmall)
        if (state.rows.isNotEmpty()) {
            val byModel = state.rows.groupBy { it.model }
            SelectionContainer {
                Text(
                    buildString {
                        for ((model, rows) in byModel) {
                            val wins = rows.count { it.outcome.startsWith("✅") }
                            append("$model: $wins/${rows.size} done, ${rows.sumOf { it.steps }} steps, ")
                            append("${rows.sumOf { it.seconds }}s, $${money(rows.sumOf { it.costUsd })}\n")
                            rows.forEach { append("  • ${it.task}: ${it.outcome} (${it.steps} steps, ${it.seconds}s, $${money(it.costUsd)})\n") }
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(settings: SettingsRepository, catalog: ModelCatalog, memory: MemoryRepository) {
    val current by settings.settings.collectAsState()
    var key by remember { mutableStateOf(settings.apiKey.orEmpty()) }
    var model by remember { mutableStateOf(current.model) }
    var strong by remember { mutableStateOf(current.strongModel.orEmpty()) }
    var steps by remember { mutableStateOf(current.maxSteps.toString()) }
    var cost by remember { mutableStateOf(current.maxCostPerTaskUsd.toString()) }
    var saved by remember { mutableStateOf("") }
    Page {
        OutlinedTextField(
            key, { key = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("OpenRouter API key") }, visualTransformation = PasswordVisualTransformation(),
        )
        Text("Kept in the iPhone Keychain on this device only.", style = MaterialTheme.typography.bodySmall)
        val models by catalog.state.collectAsState()
        var query by remember { mutableStateOf("") }
        Text("Model: $model", style = MaterialTheme.typography.titleSmall)
        Text("When stuck: ${strong.ifBlank { "never step up" }}", style = MaterialTheme.typography.bodySmall)
        Text("Picked models (${models.source.name.lowercase()} list, cost for a typical task):", style = MaterialTheme.typography.titleSmall)
        for (option in models.recommended) ModelRow(option, onMain = { model = option.id }, onStuck = { strong = option.id })
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search all ${models.all.size} models") })
        if (query.isNotBlank()) for (option in catalog.search(query).take(15)) ModelRow(option, onMain = { model = option.id }, onStuck = { strong = option.id })
        TextButton(onClick = { strong = "" }) { Text("Never step up to a stronger model") }
        OutlinedTextField(steps, { steps = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Max steps per task") })
        OutlinedTextField(cost, { cost = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Max cost per task (US dollars)") })
        Button(onClick = {
            settings.apiKey = key
            settings.update {
                it.copy(
                    model = model,
                    strongModel = strong,
                    maxSteps = steps.trim().toIntOrNull() ?: it.maxSteps,
                    maxCostPerTaskUsd = cost.trim().toDoubleOrNull() ?: it.maxCostPerTaskUsd,
                )
            }
            val now = settings.settings.value
            steps = now.maxSteps.toString()
            cost = now.maxCostPerTaskUsd.toString()
            saved = "Saved."
        }) { Text("Save") }
        if (saved.isNotEmpty()) Text(saved)
        MemorySection(settings, memory)
    }
}

/** Everything Mode remembers, by kind: delete one, forget everything, or stop learning. */
@Composable
private fun MemorySection(settings: SettingsRepository, memory: MemoryRepository) {
    val current by settings.settings.collectAsState()
    val items by memory.observe().collectAsState(emptyList())
    Text("Memory (on this iPhone only)", style = MaterialTheme.typography.titleMedium)
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(current.learnFromTasks, { on -> settings.update { it.copy(learnFromTasks = on) } })
        Text("Learn from tasks", style = MaterialTheme.typography.bodyMedium)
    }
    for ((kind, heading) in listOf(MemoryKind.Fact to "About you", MemoryKind.Skill to "Skills", MemoryKind.Tip to "App tips")) {
        val ofKind = items.filter { it.kind == kind }
        Text("$heading (${ofKind.size})", style = MaterialTheme.typography.titleSmall)
        for (item in ofKind) {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.bodyMedium)
                        Text(item.text, style = MaterialTheme.typography.bodySmall)
                        if (item.uses > 0) Text("worked ${item.successes} of ${item.uses} times", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { memory.forget(item.id) }) { Text("Delete") }
                }
            }
        }
    }
    if (items.isNotEmpty()) OutlinedButton(onClick = { memory.forgetEverything() }) { Text("Forget everything") }
}

@Composable
private fun ModelRow(option: ModelOption, onMain: () -> Unit, onStuck: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text(
                option.name + (option.tag?.let { "  · ${it.name}" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "about $${money(option.costPerTaskUsd)} a task" + (if (option.note.isNotBlank()) " · ${option.note}" else "") + "\n${option.id}",
                style = MaterialTheme.typography.labelSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onMain) { Text("Use") }
                TextButton(onClick = onStuck) { Text("Use when stuck") }
            }
        }
    }
}

@Composable
private fun QuestionDialog(q: PendingQuestion, runner: TaskRunner) {
    var reply by remember(q) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(if (q.yesNo) "Allow this?" else "Mode needs you") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(q.text)
                if (!q.yesNo) OutlinedTextField(reply, { reply = it }, Modifier.fillMaxWidth(), label = { Text("Your answer") })
            }
        },
        confirmButton = {
            if (q.yesNo) {
                TextButton(onClick = { runner.answer("yes") }) { Text("Allow") }
            } else {
                TextButton(onClick = { runner.answer(reply.ifBlank { "ok" }) }) { Text("Answer") }
            }
        },
        dismissButton = {
            if (q.yesNo) {
                TextButton(onClick = { runner.answer("no") }) { Text("Don't allow") }
            } else {
                TextButton(onClick = { runner.answer(null) }) { Text("Stop task") }
            }
        },
    )
}

@Composable
private fun HandsCheckScreen(check: HandsCheck, decodeImage: (ByteArray) -> ImageBitmap) {
    val scope = rememberCoroutineScope()
    val state by check.state.collectAsState()
    val focus = LocalFocusManager.current
    var app by remember { mutableStateOf("") }
    var tap by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }

    Page {
        Text(state.status, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { scope.launch { check.connect() } }, enabled = !state.busy) {
                Text("Test connection")
            }
            OutlinedButton(onClick = { scope.launch { check.lookHere() } }, enabled = !state.busy) { Text("Look") }
            OutlinedButton(onClick = { scope.launch { check.home() } }, enabled = !state.busy) { Text("Home") }
        }
        OutlinedTextField(app, { app = it }, Modifier.fillMaxWidth(), label = { Text("Open app (name or bundle id)") }, singleLine = true)
        OutlinedTextField(tap, { tap = it }, Modifier.fillMaxWidth(), label = { Text("Then tap text containing (optional)") }, singleLine = true)
        OutlinedTextField(type, { type = it }, Modifier.fillMaxWidth(), label = { Text("Then type (optional)") }, singleLine = true)
        Button(
            onClick = { focus.clearFocus(); scope.launch { check.probe(app.trim(), tap.trim(), type) } },
            enabled = !state.busy && app.isNotBlank(),
        ) { Text("Run in that app, then come back") }
        SelectionContainer {
            Text(state.log.takeLast(40).joinToString("\n"), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
        for (shot in state.shots.take(4)) {
            Text(shot.label, style = MaterialTheme.typography.titleSmall)
            val image = remember(shot) { decodeImage(shot.jpeg) }
            Image(image, shot.label, Modifier.fillMaxWidth().height(460.dp), contentScale = ContentScale.Fit)
            Text(shot.texts.take(25).joinToString("\n") { "${it.text}  @ (${it.x.toInt()}, ${it.y.toInt()})" }, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun lines(text: String) = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

private fun money(usd: Double): String = ((usd * 10000).roundToInt() / 10000.0).toString()
