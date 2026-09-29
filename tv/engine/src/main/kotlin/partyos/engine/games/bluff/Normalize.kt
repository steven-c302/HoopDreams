package partyos.engine.games.bluff

private val LEADING_ARTICLE = Regex("^(the|a|an)\\s+")
private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")
private val WHITESPACE = Regex("\\s+")

/**
 * Case-, punctuation- and whitespace-insensitive form used to compare answers. Punctuation is removed
 * before the leading article, so a quoted truth is still caught. Text with no letters or digits
 * (e.g. emoji-only) keeps its symbols so different emoji answers stay distinct.
 */
fun normalise(text: String): String {
    val lower = text.trim().lowercase()
    val words = lower.replace(NON_ALNUM, " ").trim().replace(LEADING_ARTICLE, "")
    val key = words.replace(WHITESPACE, "")
    return key.ifEmpty { lower.replace(WHITESPACE, "") }
}

/** Collapses whitespace and strips control characters; returns null if the result is blank or too long. */
fun cleanText(raw: String, maxLen: Int): String? {
    val t = raw.filterNot { Character.isISOControl(it) }.trim().replace(Regex("\\s+"), " ")
    return t.takeIf { it.isNotEmpty() && it.length <= maxLen }
}
