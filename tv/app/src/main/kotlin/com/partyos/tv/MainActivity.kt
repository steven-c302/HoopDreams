package com.partyos.tv

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.partyos.tv.perf.BenchmarkTour
import com.partyos.tv.perf.PerfHud
import com.partyos.tv.perf.PerfMonitor
import com.partyos.tv.service.PartyService
import com.partyos.tv.ui.TvApp
import com.partyos.tv.ui.theme.PartyTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val menuPresses = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    // Created after setContent: JankStats needs the window's DecorView to exist.
    private val perf = mutableStateOf<PerfMonitor?>(null)
    private var tour: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        PartyService.start(this)
        setContent {
            PartyTheme {
                val settings by partyRuntime.settings.settings.collectAsState(initial = null)
                Box(Modifier.fillMaxSize()) {
                    TvApp(partyRuntime, menuPresses, onBenchmark = ::startTour, onExit = {
                        PartyService.stop(this@MainActivity)
                        finish()
                    })
                    val monitor by perf
                    if (settings?.perfHud == true) monitor?.let { PerfHud(it, Modifier.align(Alignment.TopEnd).padding(12.dp)) }
                }
            }
        }
        perf.value = PerfMonitor(window)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** `adb shell am start -n com.partyos.tv/.MainActivity --ez tour true` runs the benchmark tour. */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        // Debug builds only: scripts/emulator-party.sh points the QR at the Mac's LAN proxy.
        val debuggable = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        intent.getStringExtra("override")?.takeIf { debuggable }?.let { v ->
            lifecycleScope.launch { partyRuntime.settings.setOverride(v) }
        }
        if (intent.getBooleanExtra("tour", false)) startTour()
    }

    private fun startTour() {
        if (tour?.isActive == true) return
        tour = lifecycleScope.launch {
            partyRuntime.live.filterNotNull().first()
            BenchmarkTour(partyRuntime, perf.value ?: return@launch).run()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_M) {
            menuPresses.tryEmit(Unit)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
