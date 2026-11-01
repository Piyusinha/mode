package com.piyush.mode.feature.runs

import com.piyush.mode.agent.AgentConfig
import com.piyush.mode.agent.AgentOutcome
import com.piyush.mode.agent.AgentResult
import com.piyush.mode.feature.runs.data.RunRecorder
import com.piyush.mode.feature.runs.data.RunRepository
import com.piyush.mode.testing.InMemoryFileStore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RunsTest {
    @Test
    fun aRecordedRunIsListedWithItsStepsAndScreenshots() {
        val files = InMemoryFileStore()
        val recorder = RunRecorder(files, id = "2026-11-01T08-12-00")
        recorder.runStarted("Turn off dark mode", AgentConfig(model = "m"), listOf("tap", "done"))
        recorder.looked(1, byteArrayOf(1, 2, 3), 500, 1000, emptyList(), 40)
        recorder.acted(1, "tap \"Dark\"", "input.tap", "ok", 50)
        recorder.runEnded(AgentResult(AgentOutcome.Done("Light mode on", true), steps = 1, costUsd = 0.0004, millis = 3000))

        val repo = RunRepository(files)
        val run = repo.runs().single()
        assertEquals("Turn off dark mode", run.task)
        assertEquals("done", run.outcome)
        assertEquals("Light mode on", run.summary)
        assertEquals("m", run.model)
        assertEquals(1, run.steps)
        assertEquals(listOf("start", "look", "act"), repo.events(run.id).map { it["event"].toString().trim('"') })
        assertContentEquals(byteArrayOf(1, 2, 3), repo.image(run.id, "step-1.jpg"))
    }

    @Test
    fun aRunThatCouldNotStartSaysWhy() {
        val files = InMemoryFileStore()
        RunRecorder(files, id = "a").failedToStart("Text Sonali", "no API key")
        val run = RunRepository(files).run("a")!!
        assertEquals("not_started", run.outcome)
        assertEquals("no API key", run.summary)
        assertNull(run.steps)
    }

    @Test
    fun pruneKeepsTheNewest() {
        val files = InMemoryFileStore()
        listOf("2026-10-30", "2026-10-31", "2026-11-01").forEach { RunRecorder(files, id = it).failedToStart("t", "x") }
        val repo = RunRepository(files)
        repo.prune(keep = 2)
        assertEquals(listOf("2026-11-01", "2026-10-31"), repo.runs().map { it.id })
    }
}
