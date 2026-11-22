package ly.anoma.hex.typing

import org.junit.Assert.*
import org.junit.Test

class WordCountsTest {
    @Test fun aWordIsLearnedTheSecondTimeOnly() {
        var counts = WordCounts.parse(null)
        val first = WordCounts.note(counts, "Zorbex"); counts = first.first
        assertFalse(first.second)
        val second = WordCounts.note(counts, "zorbex"); counts = second.first
        assertTrue(second.second)
        assertFalse(WordCounts.note(counts, "zorbex").second)
        assertEquals(mapOf("zorbex" to 3), WordCounts.parse(WordCounts.format(counts)))
    }
}
