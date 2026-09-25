package com.partyos.tv.perf

import android.util.Log
import android.view.Window
import androidx.metrics.performance.JankStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PerfStats(val fps: Int, val onTimePct: Double)
data class PerfReport(val frames: Int, val onTimePct: Double, val jankyPct: Double)

/** Pure frame bookkeeping, separated from JankStats so it can be unit tested. */
class FrameTally(private val budgetNs: Long = 16_666_667L) {
    var frames = 0; private set
    var onTime = 0; private set
    var janky = 0; private set

    fun add(durationNs: Long, isJank: Boolean) {
        frames++
        if (durationNs <= budgetNs) onTime++
        if (isJank) janky++
    }

    fun report() = PerfReport(
        frames,
        if (frames == 0) 0.0 else onTime * 100.0 / frames,
        if (frames == 0) 0.0 else janky * 100.0 / frames,
    )
}

/**
 * Records every frame the TV draws. Feeds the on-screen HUD (rolling one-second window) and the benchmark tour
 * (whole-run tally), and logs "PARTYOS_PERF frames=… onTime=…%" when a recording stops.
 */
class PerfMonitor(window: Window) {
    private val _live = MutableStateFlow(PerfStats(0, 100.0))
    val live: StateFlow<PerfStats> = _live.asStateFlow()

    private var second = FrameTally()
    private var secondStartNs = 0L
    private var recording: FrameTally? = null

    private val jank = JankStats.createAndTrack(window) { f ->
        val dur = f.frameDurationUiNanos
        recording?.add(dur, f.isJank)
        if (secondStartNs == 0L) secondStartNs = f.frameStartNanos
        second.add(dur, f.isJank)
        if (f.frameStartNanos - secondStartNs >= 1_000_000_000L) {
            val r = second.report()
            _live.value = PerfStats(r.frames, r.onTimePct)
            second = FrameTally()
            secondStartNs = f.frameStartNanos
        }
    }

    fun startRecording() {
        recording = FrameTally()
    }

    fun stopRecording(label: String): PerfReport {
        val r = (recording ?: FrameTally()).report()
        recording = null
        Log.i("PARTYOS_PERF", "PARTYOS_PERF tour=$label frames=${r.frames} onTime=${"%.1f".format(r.onTimePct)}% janky=${"%.1f".format(r.jankyPct)}%")
        return r
    }

    fun setEnabled(on: Boolean) {
        jank.isTrackingEnabled = on
    }
}
