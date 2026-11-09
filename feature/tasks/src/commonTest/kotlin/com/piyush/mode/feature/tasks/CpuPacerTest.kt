package com.piyush.mode.feature.tasks

import com.piyush.mode.feature.tasks.presentation.CpuPacer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CpuPacerTest {
    /** A fake clock and CPU meter: [busy] is the share of each ms the "process" spends on CPU. */
    private class Meter(var busy: Double) {
        var now = 0L
        var cpu = 0.0
        fun run(ms: Long) { now += ms; cpu += ms * busy }
        fun idle(ms: Long) { now += ms }
    }

    @Test
    fun underTheLimitNothingWaits() = runTest {
        val m = Meter(0.3)
        val pacer = CpuPacer({ m.cpu.toLong() }, { m.now }, sleep = { m.idle(it) })
        var waited = 0L
        repeat(30) { m.run(4_000); waited += pacer.pace() }
        assertEquals(0L, waited)
    }

    @Test
    fun aBusyRunIsSlowedToStayUnderTheLimit() = runTest {
        // Steps that keep the CPU at 95%, as in the run iOS killed (81% over 60 s).
        val m = Meter(0.95)
        val pacer = CpuPacer({ m.cpu.toLong() }, { m.now }, sleep = { m.idle(it) })
        val start = m.now
        var waited = 0L
        repeat(40) { m.run(4_000); waited += pacer.pace() }
        assertTrue(waited > 0, "it paused")
        // Over any 60 s window after the first minute, CPU stays well under iOS's 80%.
        val overall = m.cpu / (m.now - start) * 100
        assertTrue(overall < 75, "average ${overall.toInt()}% must stay under the 80% limit")
    }

    @Test
    fun unknownCpuNeverWaits() = runTest {
        val pacer = CpuPacer({ null }, { 0L }, sleep = { error("must not sleep") })
        assertEquals(0L, pacer.pace())
    }
}
