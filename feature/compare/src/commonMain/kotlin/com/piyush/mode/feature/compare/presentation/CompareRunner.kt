package com.piyush.mode.feature.compare.presentation

import com.piyush.mode.agent.AgentConfig
import com.piyush.mode.agent.AgentOutcome
import com.piyush.mode.core.common.ActivityTracker
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.core.common.nowId
import com.piyush.mode.core.storage.FileStore
import com.piyush.mode.feature.compare.domain.CompareRow
import com.piyush.mode.feature.compare.domain.CompareState
import com.piyush.mode.feature.connection.presentation.PhoneSession
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.tasks.presentation.TaskRunner
import com.piyush.mode.ops.Op
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Runs each task the user wrote with each model, from the Home Screen, and
 * tabulates success, steps, time and cost. The tasks come from the user.
 */
public class CompareRunner(
    private val runner: TaskRunner,
    private val session: PhoneSession,
    private val settings: SettingsRepository,
    private val files: FileStore,
    private val activity: ActivityTracker,
) {
    private val mutableState = MutableStateFlow(CompareState())
    public val state: StateFlow<CompareState> = mutableState.asStateFlow()

    public suspend fun run(tasks: List<String>, models: List<String>) {
        if (state.value.running) return
        mutableState.value = CompareState(running = true)
        val id = "compare-" + nowId()
        AppLog.log("compare", "$id: ${tasks.size} tasks × ${models.size} models")
        try {
            activity.track {
                for (model in models) for (task in tasks) {
                    runCatching { session.phone().send(Op.Home) }
                    val result = runner.runTask(
                        task,
                        settings.agentConfig().let { AgentConfig(model = model, strongModel = null, maxSteps = it.maxSteps, maxCostUsd = it.maxCostUsd) },
                        tag = id,
                        stayConnected = true,
                    )
                    val row = if (result == null) {
                        CompareRow(model, task, "did not run: ${runner.state.value.status}", 0, 0, 0.0)
                    } else {
                        val outcome = when (val o = result.outcome) {
                            is AgentOutcome.Done -> if (o.success) "✅ ${o.summary}" else "❌ ${o.summary}"
                            is AgentOutcome.Stopped -> "⏹ ${o.why}"
                            is AgentOutcome.Failed -> "❌ ${o.why}"
                        }
                        CompareRow(model, task, outcome, result.steps, result.millis / 1000, result.costUsd)
                    }
                    mutableState.update { it.copy(rows = it.rows + row) }
                    files.write("compare/$id.txt", summary().encodeToByteArray())
                }
            }
        } finally {
            mutableState.update { it.copy(running = false) }
            withContext(NonCancellable) { session.disconnect() }
            AppLog.log("compare", "$id finished:\n" + summary())
        }
    }

    /** Per model: successes, steps, time, cost, then each task. */
    public fun summary(): String = buildString {
        for ((model, rows) in state.value.rows.groupBy { it.model }) {
            val wins = rows.count { it.outcome.startsWith("✅") }
            append("$model: $wins/${rows.size} done, ${rows.sumOf { it.steps }} steps, ")
            append("${rows.sumOf { it.seconds }}s, $${money(rows.sumOf { it.costUsd })}\n")
            rows.forEach { append("  • ${it.task}: ${it.outcome} (${it.steps} steps, ${it.seconds}s, $${money(it.costUsd)})\n") }
        }
    }

    private fun money(usd: Double) = ((usd * 10000).roundToInt() / 10000.0).toString()
}
