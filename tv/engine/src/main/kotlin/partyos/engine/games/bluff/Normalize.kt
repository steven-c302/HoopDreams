package partyos.engine.games.bluff

private val LEADING_ARTICLE = Regex("^(the|a|an)\\s+")
private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]")

/** Case-, punctuation- and whitespace-insensitive form used to compare answers. */
fun normalise(text: String): String =
    text.trim().lowercase().replace(Regex("\\s+"), " ").replace(LEADING_ARTICLE, "").replace(NON_ALNUM, "")

/** Collapses whitespace and strips control characters; returns null if the result is blank or too long. */
fun cleanText(raw: String, maxLen: Int): String? {
    val t = raw.filterNot { Character.isISOControl(it) }.trim().replace(Regex("\\s+"), " ")
    return t.takeIf { it.isNotEmpty() && it.length <= maxLen }
}
