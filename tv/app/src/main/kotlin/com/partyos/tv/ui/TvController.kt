package com.partyos.tv.ui

import com.partyos.tv.PartyRuntime
import kotlinx.coroutines.launch
import partyos.engine.ActionResult
import partyos.engine.HostCmd
import partyos.engine.PlayerId

/** What the TV remote can do. Every call goes through the same serialised engine host the phones use. */
class TvController(private val runtime: PartyRuntime, private val onReject: (String) -> Unit = {}) {
    private fun send(cmd: HostCmd) = runtime.scope.launch {
        val r = runtime.live.value?.host?.mutate { host(cmd) }
        if (r is ActionResult.Rejected) onReject(r.code)
    }

    fun start(gameId: String, rounds: Int) = send(HostCmd.StartGame(gameId, mapOf("rounds" to rounds)))
    fun pause() = send(HostCmd.Pause)
    fun resume() = send(HostCmd.Resume)
    fun skip() = send(HostCmd.SkipPhase)
    fun end() = send(HostCmd.EndGame)
    fun kick(id: PlayerId) = send(HostCmd.Kick(id))
    fun newParty() = runtime.scope.launch { runtime.newParty() }
    fun newPin() = runtime.scope.launch { runtime.changePin((1000..9999).random().toString()) }
    fun setOverride(v: String) = runtime.scope.launch { runtime.settings.setOverride(v) }
    fun setPerfHud(on: Boolean) = runtime.scope.launch { runtime.settings.setPerfHud(on) }
    fun setRounds(n: Int) = runtime.scope.launch { runtime.settings.setRounds(n) }
}
