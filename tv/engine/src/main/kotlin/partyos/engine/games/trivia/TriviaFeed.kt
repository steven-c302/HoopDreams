package partyos.engine.games.trivia

/**
 * More multiple-choice questions from outside the bundled packs (Open Trivia DB on a real server), for Quick Draw and
 * The Heist once the bundled questions run out. The engine stays pure and never waits on the network: a feed only
 * hands over questions it already holds, and the chosen question is copied into the game state, so a restored show
 * never needs the feed.
 */
interface TriviaFeed {
    /** A question it holds whose id is not in [used], preferably not in [avoidCategory]; null if none is ready. */
    fun take(used: Set<String>, avoidCategory: String?): McItem?

    /** The bundled questions are running low: start filling up in the background. Must return immediately. */
    fun warm() {}
}
