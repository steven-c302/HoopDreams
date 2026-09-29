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

/**
 * Limits PIN guesses per client address. Each attempt is counted *before* the PIN is checked, so a burst
 * of parallel requests gets at most [maxAttempts] tries per [lockMs] window; a correct PIN clears the count.
 */
class PinLockout(private val maxAttempts: Int, private val lockMs: Long, private val now: () -> Long) {
    private val attempts = HashMap<String, Int>()
    private val lockedUntil = HashMap<String, Long>()

    @Synchronized fun locked(ip: String): Boolean {
        val until = lockedUntil[ip] ?: return false
        if (now() < until) return true
        lockedUntil.remove(ip); attempts.remove(ip)
        return false
    }

    /** Reserves one guess for [ip]; false when it is locked out. */
    @Synchronized fun tryAttempt(ip: String): Boolean {
        if (locked(ip)) return false
        val n = (attempts[ip] ?: 0) + 1
        attempts[ip] = n
        if (n >= maxAttempts) lockedUntil[ip] = now() + lockMs
        return true
    }

    @Synchronized fun retryAfterSec(ip: String) = ((lockedUntil[ip] ?: now()) - now()).coerceAtLeast(0) / 1000 + 1

    @Synchronized fun succeed(ip: String) {
        attempts.remove(ip)
        lockedUntil.remove(ip)
    }
}

/** Client-chosen message ids: short and boring, so they can't bloat memory or snapshots. */
private val ID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")

fun isValidMessageId(id: String) = ID_PATTERN.matches(id)
