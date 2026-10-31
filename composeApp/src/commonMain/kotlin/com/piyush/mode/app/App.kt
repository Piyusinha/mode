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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun App(link: PhoneLink, services: AppServices) {
    val log = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    val holder = remember { PhoneHolder(link) { log += it } }
    val settings = remember { AgentSettings(services) }
    val runner = remember { AgentRunner(link, services, holder, settings) }
    val compare = remember { CompareRunner(runner, holder, services.logs) }
    val check = remember { HandsCheck(link, holder) }
    var tab by remember { mutableIntStateOf(0) }
    val appScope = rememberCoroutineScope()
    remember {
        // Leaving Mode while it is idle ends the session, so iOS drops its
        // "viewing and controlling" indicator and the VPN goes off.
        link.onAppTerminate { holder.closeNow() }
        link.onAppBackground {
            if (!runner.running && !compare.running && !check.busy) {
                AppLog.log("app", "left Mode while idle")
                appScope.launch { holder.disconnect() }
            }
        }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                var stopping by remember { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(holder.status, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    OutlinedButton(
                        onClick = {
                            stopping = true
                            runner.stop()
                            appScope.launch {
                                holder.stopSession()
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
                    0 -> AgentScreen(runner, holder)
                    1 -> CompareScreen(compare, runner)
                    2 -> LogsScreen(services.logs, link)
                    3 -> SettingsScreen(settings)
                    else -> HandsCheckScreen(check)
                }
            }
            runner.question?.let { QuestionDialog(it, runner) }
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
private fun AgentScreen(runner: AgentRunner, holder: PhoneHolder) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var task by remember { mutableStateOf("") }
    Page {
        Text(holder.status, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            task, { task = it }, Modifier.fillMaxWidth().heightIn(min = 96.dp),
            label = { Text("What should Mode do?") },
            placeholder = { Text("e.g. Turn on Dark Mode") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { focus.clearFocus(); runner.start(scope, task.trim()) },
                enabled = !runner.running && task.isNotBlank(),
            ) { Text("Run") }
            OutlinedButton(onClick = { runner.stop() }, enabled = runner.running) { Text("Stop") }
        }
        if (runner.status.isNotBlank()) Text(runner.status, style = MaterialTheme.typography.titleSmall)

        for (s in runner.steps.reversed()) {
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
private fun CompareScreen(compare: CompareRunner, runner: AgentRunner) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var tasks by remember { mutableStateOf("") }
    var models by remember { mutableStateOf(AgentSettings.CANDIDATES.take(4).joinToString("\n") { it.first }) }
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
                enabled = !compare.running && !runner.running && lines(tasks).isNotEmpty() && lines(models).isNotEmpty(),
            ) { Text("Run comparison") }
            OutlinedButton(onClick = { runner.stop() }, enabled = runner.running) { Text("Stop current") }
        }
        if (compare.running) Text(runner.status, style = MaterialTheme.typography.bodySmall)
        if (compare.rows.isNotEmpty()) {
            val byModel = compare.rows.groupBy { it.model }
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
private fun SettingsScreen(settings: AgentSettings) {
    var key by remember { mutableStateOf(settings.apiKey.orEmpty()) }
    var model by remember { mutableStateOf(settings.model) }
    var strong by remember { mutableStateOf(settings.strongModel) }
    var steps by remember { mutableStateOf(settings.maxSteps) }
    var saved by remember { mutableStateOf("") }
    Page {
        OutlinedTextField(
            key, { key = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("OpenRouter API key") }, visualTransformation = PasswordVisualTransformation(),
        )
        Text("Kept in the iPhone Keychain on this device only.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Model") })
        OutlinedTextField(strong, { strong = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Stronger model when stuck (empty: never)") })
        OutlinedTextField(steps, { steps = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Max steps per task") })
        Text("Models to try ($ per million tokens, in · out):", style = MaterialTheme.typography.titleSmall)
        for ((id, price) in AgentSettings.CANDIDATES) {
            TextButton(onClick = { model = id }) { Text("$id  ($price)") }
        }
        Button(onClick = {
            settings.apiKey = key
            settings.model = model.trim()
            settings.strongModel = strong.trim()
            settings.maxSteps = steps.trim()
            saved = "Saved."
        }) { Text("Save") }
        if (saved.isNotEmpty()) Text(saved)
    }
}

@Composable
private fun QuestionDialog(q: PendingQuestion, runner: AgentRunner) {
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
private fun HandsCheckScreen(check: HandsCheck) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var app by remember { mutableStateOf("") }
    var tap by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }

    Page {
        Text(check.status, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { scope.launch { check.connect() } }, enabled = !check.busy) {
                Text("Test connection")
            }
            OutlinedButton(onClick = { scope.launch { check.lookHere() } }, enabled = check.connected && !check.busy) { Text("Look") }
            OutlinedButton(onClick = { scope.launch { check.home() } }, enabled = check.connected && !check.busy) { Text("Home") }
        }
        OutlinedTextField(app, { app = it }, Modifier.fillMaxWidth(), label = { Text("Open app (name or bundle id)") }, singleLine = true)
        OutlinedTextField(tap, { tap = it }, Modifier.fillMaxWidth(), label = { Text("Then tap text containing (optional)") }, singleLine = true)
        OutlinedTextField(type, { type = it }, Modifier.fillMaxWidth(), label = { Text("Then type (optional)") }, singleLine = true)
        Button(
            onClick = { focus.clearFocus(); scope.launch { check.probe(app.trim(), tap.trim(), type) } },
            enabled = check.connected && !check.busy && app.isNotBlank(),
        ) { Text("Run in that app, then come back") }
        SelectionContainer {
            Text(check.log.takeLast(40).joinToString("\n"), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
        for (shot in check.shots.take(4)) {
            Text(shot.label, style = MaterialTheme.typography.titleSmall)
            Image(shot.image, shot.label, Modifier.fillMaxWidth().height(460.dp), contentScale = ContentScale.Fit)
            Text(shot.texts.take(25).joinToString("\n") { "${it.text}  @ (${it.x.toInt()}, ${it.y.toInt()})" }, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun lines(text: String) = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

private fun money(usd: Double): String = ((usd * 10000).roundToInt() / 10000.0).toString()
