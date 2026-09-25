package com.partyos.tv.perf

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameTallyTest {
    @Test fun countsFramesWithinTheSixtyHertzBudgetAsOnTime() {
        val t = FrameTally()
        repeat(95) { t.add(12_000_000, isJank = false) }
        repeat(5) { t.add(40_000_000, isJank = true) }
        val r = t.report()
        assertEquals(100, r.frames)
        assertEquals(95.0, r.onTimePct, 0.001)
        assertEquals(5.0, r.jankyPct, 0.001)
    }

    @Test fun exactlyOneFrameBudgetIsOnTime() {
        val t = FrameTally()
        t.add(16_666_667, isJank = false)
        assertEquals(100.0, t.report().onTimePct, 0.001)
    }

    @Test fun emptyTallyReportsZero() = assertEquals(PerfReport(0, 0.0, 0.0), FrameTally().report())
}
