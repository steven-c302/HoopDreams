package com.partyos.tv.perf

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameTallyTest {
    @Test fun onTimeMeansTheFrameMetItsDeadline() {
        val t = FrameTally()
        repeat(95) { t.add(onTime = true, isJank = false) }
        repeat(5) { t.add(onTime = false, isJank = true) }
        val r = t.report()
        assertEquals(100, r.frames)
        assertEquals(95.0, r.onTimePct, 0.001)
        assertEquals(5.0, r.jankyPct, 0.001)
    }

    @Test fun deadlineFromOverrunOrUiTimeFallback() {
        assertEquals(true, frameOnTime(overrunNs = 0L, uiNs = 40_000_000))
        assertEquals(false, frameOnTime(overrunNs = 1L, uiNs = 1_000_000))
        assertEquals(true, frameOnTime(overrunNs = null, uiNs = 16_666_667))
        assertEquals(false, frameOnTime(overrunNs = null, uiNs = 16_666_668))
    }

    @Test fun emptyTallyReportsZero() = assertEquals(PerfReport(0, 0.0, 0.0), FrameTally().report())
}
