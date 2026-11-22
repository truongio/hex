package ly.anoma.hex.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class LetterHoldsTest {
    @Test fun theLettersSymbolComesFirstThenItsDigitAndAccents() {
        assertEquals(listOf("%", "1"), LetterFeedback.holds('q', "sv", '1', LetterFeedback.symbol('q')))
        assertEquals("@", LetterFeedback.holds('a', "sv", null, LetterFeedback.symbol('a')).first())
        assertEquals(listOf("ø", "ö"), LetterFeedback.holds('ö', "sv", null, LetterFeedback.symbol('ö')))
        assertEquals("/", LetterFeedback.holds('M', "sv", null, LetterFeedback.symbol('M')).first())
        assertEquals(LetterFeedback.holds('e', "sv", '3'), LetterFeedback.holds('e', "sv", '3', null))
    }
}
