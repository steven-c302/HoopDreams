package partyos.server

import java.net.InetAddress

/** True for loopback, RFC 1918, link-local and IPv6 ULA/link-local literals. Hostnames other than localhost are refused. */
fun isPrivateAddress(host: String): Boolean {
    if (host == "localhost") return true
    val literal = host.removePrefix("[").removeSuffix("]").substringBefore('%')
    if (literal.isEmpty() || !(literal.contains(':') || literal.matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}")))) return false
    val addr = runCatching { InetAddress.getByName(literal) }.getOrNull() ?: return false
    if (addr.isLoopbackAddress || addr.isSiteLocalAddress || addr.isLinkLocalAddress) return true
    val b = addr.address
    return b.size == 16 && (b[0].toInt() and 0xFE) == 0xFC
}

class TokenBucket(private val capacity: Int, private val perSecond: Double, private val now: () -> Long) {
    private var tokens = capacity.toDouble()
    private var last = now()

    fun tryTake(): Boolean {
        val t = now()
        tokens = minOf(capacity.toDouble(), tokens + (t - last) / 1000.0 * perSecond)
        last = t
        if (tokens < 1) return false
        tokens -= 1
        return true
    }
}

/** Counts failed PIN attempts per client address and locks it out for [lockMs] after [maxFailures]. */
class PinLockout(private val maxFailures: Int, private val lockMs: Long, private val now: () -> Long) {
    private val failures = HashMap<String, Int>()
    private val lockedUntil = HashMap<String, Long>()

    @Synchronized fun locked(ip: String): Boolean {
        val until = lockedUntil[ip] ?: return false
        if (now() < until) return true
        lockedUntil.remove(ip); failures.remove(ip)
        return false
    }

    @Synchronized fun retryAfterSec(ip: String) = ((lockedUntil[ip] ?: now()) - now()).coerceAtLeast(0) / 1000 + 1

    @Synchronized fun fail(ip: String) {
        val n = (failures[ip] ?: 0) + 1
        failures[ip] = n
        if (n >= maxFailures) lockedUntil[ip] = now() + lockMs
    }

    @Synchronized fun succeed(ip: String) {
        failures.remove(ip)
    }
}
