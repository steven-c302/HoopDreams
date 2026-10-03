package partyos.engine.games.songdrop

object SongRules {
    /** 1 = 60s and 70s, 2 = 80s, 3 = 90s, 4 = 00s, 5 = 10s and 20s. */
    fun eraOf(year: Int): Int = when {
        year <= 1979 -> 1
        year <= 1989 -> 2
        year <= 1999 -> 3
        year <= 2009 -> 4
        else -> 5
    }
}
