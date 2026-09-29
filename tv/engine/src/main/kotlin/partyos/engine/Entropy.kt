package partyos.engine

import java.security.MessageDigest
import java.security.SecureRandom

/** Source of unguessable tokens and uniform random ints; injectable so tests stay deterministic where needed. */
interface Entropy {
    fun token(): String
    fun nextInt(bound: Int): Int
    fun nextLong(): Long
}

class SecureEntropy(private val rng: SecureRandom = SecureRandom()) : Entropy {
    override fun token(): String {
        val bytes = ByteArray(16).also(rng::nextBytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
    override fun nextInt(bound: Int) = rng.nextInt(bound)
    override fun nextLong() = rng.nextLong()
}

fun sha256(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
