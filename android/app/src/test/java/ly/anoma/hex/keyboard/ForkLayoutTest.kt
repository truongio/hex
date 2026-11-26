package ly.anoma.hex.keyboard

import ly.anoma.hex.speech.Spoken
import org.junit.Assert.assertEquals
import org.junit.Test

/** The fork's own symbol layout, which upstream merges must never replace. */
class ForkLayoutTest {
    private fun page(more: Boolean) =
        KeyboardPage(Spoken.Swedish, symbols = true, more, shifted = false, locked = false, enter = EnterKey(null, 0))

    @Test fun theSymbolPagesAreTheForks() {
        assertEquals(listOf("1234567890", "-/:;()\$&@\"", ".,?!'"), page(more = false).lines)
        assertEquals(listOf("[]{}#%^*+=", "_\\|~<>€£¥•", ".,?!'"), page(more = true).lines)
        for (more in listOf(false, true)) {
            assertEquals(",", page(more).commaText)
            assertEquals(".", page(more).periodText)
        }
    }

    @Test fun thePeriodHoldsOnlyExclamationCommaQuestion() {
        assertEquals(listOf(listOf("?", ",", "!")), LetterFeedback.periodHolds)
        assertEquals(",", LetterFeedback.periodHolds[0][LetterFeedback.PERIOD_DEFAULT])
    }

    @Test fun theLettersHoldTheForksSymbols() {
        val expected = mapOf('q' to '%', 'w' to '^', 'e' to '~', 'r' to '|', 't' to '[', 'y' to ']', 'u' to '<',
            'i' to '>', 'o' to '{', 'p' to '}', 'å' to null, 'a' to '@', 's' to '#', 'd' to '&', 'f' to '*',
            'g' to '-', 'h' to '+', 'j' to '=', 'k' to '(', 'l' to ')', 'ö' to 'ø', 'ä' to 'æ', 'z' to '_',
            'x' to '$', 'c' to '"', 'v' to '\'', 'b' to ':', 'n' to ';', 'm' to '/')
        assertEquals(expected, expected.keys.associateWith { LetterFeedback.letterSymbol(it) })
        assertEquals(listOf("%", "1"), LetterFeedback.holds('q', "sv", '1', symbolsOnLetters = true).take(2))
    }
}
