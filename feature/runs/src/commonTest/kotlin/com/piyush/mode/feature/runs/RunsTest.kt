package com.piyush.mode.feature.runs

import com.piyush.mode.agent.AgentConfig
import com.piyush.mode.agent.AgentOutcome
import com.piyush.mode.agent.AgentResult
import com.piyush.mode.feature.runs.data.RunRecorder
import com.piyush.mode.feature.runs.data.RunRepository
import com.piyush.mode.testing.InMemoryFileStore
import com.piyush.mode.testing.testDatabase
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RunsTest {
    private val files = InMemoryFileStore()
    private val repo = RunRepository(files, testDatabase())

    @Test
    fun aRecordedRunIsListedWithItsStepsAndScreenshots() {
        val recorder = RunRecorder(files, id = "2026-11-01T08-12-00", index = repo, taskId = "k1", startedAt = 42)
        recorder.runStarted("Turn off dark mode", AgentConfig(model = "m"), listOf("tap", "done"))
        assertEquals("running", repo.runs().single().outcome, "listed as it starts")
        recorder.looked(1, byteArrayOf(1, 2, 3), 500, 1000, emptyList(), 40)
        recorder.acted(1, "tap \"Dark\"", "input.tap", "ok", 50)
        recorder.runEnded(AgentResult(AgentOutcome.Done("Light mode on", true), steps = 1, costUsd = 0.0004, millis = 3000))

        val run = repo.runs().single()
        assertEquals("Turn off dark mode", run.task)
        assertEquals("done", run.outcome)
        assertEquals("Light mode on", run.summary)
        assertEquals("m", run.model)
        assertEquals(1, run.steps)
        assertEquals("k1", run.taskId)
        assertEquals(42, run.startedAt)
        assertEquals(listOf("start", "look", "act"), repo.events(run.id).map { it["event"].toString().trim('"') })
        assertContentEquals(byteArrayOf(1, 2, 3), repo.image(run.id, "step-1.jpg"))
    }

    @Test
    fun aRunThatCouldNotStartSaysWhy() {
        RunRecorder(files, id = "a", index = repo).failedToStart("Text Sonali", "no API key")
        val run = repo.run("a")!!
        assertEquals("not_started", run.outcome)
        assertEquals("no API key", run.summary)
        assertNull(run.steps)
    }

    @Test
    fun runsRecordedBeforeTheDatabaseAreImportedOnce() {
        listOf("2026-10-30T10-00-00", "2026-10-31T09-00-00").forEach { RunRecorder(files, id = it).failedToStart("t", "x") }
        assertTrue(repo.runs().isEmpty(), "files only so far")
        assertEquals(2, repo.backfill())
        assertEquals(0, repo.backfill(), "already listed")
        assertEquals(listOf("2026-10-31T09-00-00", "2026-10-30T10-00-00"), repo.runs().map { it.id })
    }

    @Test
    fun pruneKeepsTheNewestWithTheirFiles() {
        listOf("2026-10-30", "2026-10-31", "2026-11-01").forEach { RunRecorder(files, id = it, index = repo).failedToStart("t", "x") }
        repo.prune(keep = 2)
        assertEquals(listOf("2026-11-01", "2026-10-31"), repo.runs().map { it.id })
        assertNull(files.read("runs/2026-10-30/run.json"), "its files went too")
    }
}
