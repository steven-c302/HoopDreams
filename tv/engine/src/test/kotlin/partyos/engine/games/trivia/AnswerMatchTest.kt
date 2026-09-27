package partyos.engine.games.trivia

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnswerMatchTest {
    private fun yes(guess: String, answer: String) = assertTrue(AnswerMatch.accepts(guess, answer), "\"$guess\" should count for \"$answer\"")
    private fun no(guess: String, answer: String) = assertFalse(AnswerMatch.accepts(guess, answer), "\"$guess\" should not count for \"$answer\"")

    @Test fun caseAccentsPunctuationArticlesAndSpacesDontMatter() {
        yes("seine", "The Seine")
        yes("THE SEINE!", "The Seine")
        yes("creeper", "A Creeper")
        yes("pokemon", "Pokémon")
        yes("insideout", "Inside Out")
        yes("spice girls", "Spice Girls")
        yes("Winters coming", "Winter is coming")
    }

    @Test fun typosCountButOtherWordsDont() {
        yes("Seatle", "Seattle")
        yes("peregrin falcon", "Peregrine falcon")
        yes("Starbuks", "Starbucks")
        yes("Leonardo da Vinchi", "Leonardo da Vinci")
        no("Boat", "Goat")
        no("Rex", "Red")
        no("Rhine", "Seine")
        no("Portugal", "Spain")
    }

    @Test fun theDistinctivePartOfALongerAnswerCounts() {
        yes("da vinci", "Leonardo da Vinci")
        yes("falcon", "Peregrine falcon")
        yes("pyramid of giza", "Great Pyramid of Giza")
        yes("the river seine", "The Seine")
        yes("mount everest", "Everest")
        no("vinci", "Leonardo da Vinci")
        no("d", "Vitamin D")
        no("vitamin", "Vitamin D")
        no("carolina", "North Carolina")
        no("A negative", "O negative")
        no("negative", "O negative")
        yes("o negative", "O negative")
        yes("vitamind", "Vitamin D")
        no("south carolina", "North Carolina")
    }

    @Test fun numbersMustBeExact() {
        yes("1989", "1989")
        no("1988", "1989")
        yes("seven", "7")
        yes("7", "Seven")
    }

    @Test fun everyWriteItDownAnswerInThePackAcceptsItselfTypedPlainly() {
        val writable = TriviaPack.core().mc.filter(BrainDrain::writable)
        assertTrue(writable.size >= 150, "only ${writable.size} questions work without options")
        for (q in writable) {
            yes(q.answer, q.answer)
            yes(q.answer.lowercase(), q.answer)
            yes(q.answer.replace(Regex("[^\\p{L}\\p{N} ]"), ""), q.answer) // typed without punctuation
            for (w in q.wrong) if (AnswerMatch.accepts(w, q.answer)) error("${q.id}: wrong option \"$w\" would count for \"${q.answer}\"")
        }
    }

    @Test fun hedgingAndEmptyAnswersDontCount() {
        no("red or blue", "Red")
        no("red blue green", "Red")
        no("", "Red")
        no("   ", "Red")
        no("?!", "Red")
    }
}
