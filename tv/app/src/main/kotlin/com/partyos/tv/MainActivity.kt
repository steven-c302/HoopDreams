package com.partyos.tv

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.partyos.tv.service.PartyService
import com.partyos.tv.ui.TvApp
import com.partyos.tv.ui.theme.PartyTheme
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {
    private val menuPresses = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        PartyService.start(this)
        setContent { PartyTheme { TvApp(partyRuntime, menuPresses, onBenchmark = {}) } }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_M) {
            menuPresses.tryEmit(Unit)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
