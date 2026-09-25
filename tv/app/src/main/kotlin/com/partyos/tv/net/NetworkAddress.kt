package com.partyos.tv.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.InetAddress

/** The first IPv4 address phones could reach: not loopback, not wildcard, not link-local. */
fun pickIpv4(addresses: List<InetAddress>): String? = addresses
    .filterIsInstance<Inet4Address>()
    .firstOrNull { !it.isLoopbackAddress && !it.isAnyLocalAddress && !it.isLinkLocalAddress }
    ?.hostAddress

/**
 * The join URL encoded in the QR. A non-blank [override] ("host" or "host:port") wins, which is how the
 * emulator points phones at the Mac's LAN proxy. Returns null rather than an address phones can't reach.
 */
fun advertisedUrl(override: String?, ip: String?, port: Int, room: String): String? {
    val o = override?.trim().orEmpty()
    val authority = when {
        o.isNotEmpty() -> if (':' in o) o else "$o:$port"
        ip.isNullOrBlank() || ip == "0.0.0.0" -> return null
        else -> "$ip:$port"
    }
    return "http://$authority/j/$room"
}

/** Tracks the TV's current LAN address as the default network changes. */
class NetworkAddressMonitor(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val _ip = MutableStateFlow(current())
    val ip: StateFlow<String?> = _ip.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
            _ip.value = pickIpv4(lp.linkAddresses.map { it.address })
        }
        override fun onLost(network: Network) {
            _ip.value = current()
        }
    }

    fun start() = cm.registerDefaultNetworkCallback(callback)
    fun stop() = runCatching { cm.unregisterNetworkCallback(callback) }

    private fun current(): String? = cm.activeNetwork?.let { cm.getLinkProperties(it) }?.let { lp -> pickIpv4(lp.linkAddresses.map { it.address }) }
}
