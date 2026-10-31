package com.piyush.mode.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun App(link: PhoneLink) {
    val check = remember { HandsCheck(link) }
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            HandsCheckScreen(check)
        }
    }
}

@Composable
private fun HandsCheckScreen(check: HandsCheck) {
    val scope = rememberCoroutineScope()
    var app by remember { mutableStateOf("") }
    var tap by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Mode", style = MaterialTheme.typography.headlineMedium)
        Text(check.status, style = MaterialTheme.typography.bodyMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { scope.launch { check.connect() } }, enabled = !check.busy) {
                Text(if (check.connected) "Reconnect" else "Connect")
            }
            OutlinedButton(onClick = { scope.launch { check.lookHere() } }, enabled = check.connected && !check.busy) {
                Text("Look")
            }
            OutlinedButton(onClick = { scope.launch { check.home() } }, enabled = check.connected && !check.busy) {
                Text("Home")
            }
        }

        OutlinedTextField(app, { app = it }, Modifier.fillMaxWidth(), label = { Text("Open app (name or bundle id)") }, singleLine = true)
        OutlinedTextField(tap, { tap = it }, Modifier.fillMaxWidth(), label = { Text("Then tap text containing (optional)") }, singleLine = true)
        OutlinedTextField(type, { type = it }, Modifier.fillMaxWidth(), label = { Text("Then type (optional)") }, singleLine = true)
        Button(
            onClick = { scope.launch { check.probe(app.trim(), tap.trim(), type) } },
            enabled = check.connected && !check.busy && app.isNotBlank(),
        ) { Text("Run in that app, then come back") }

        SelectionContainer {
            Text(
                check.log.takeLast(40).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }

        for (shot in check.shots.take(4)) {
            Text(shot.label, style = MaterialTheme.typography.titleSmall)
            Image(shot.image, shot.label, Modifier.fillMaxWidth().height(460.dp), contentScale = ContentScale.Fit)
            Text(
                shot.texts.take(25).joinToString("\n") { "${it.text}  @ (${it.x.toInt()}, ${it.y.toInt()})" },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
