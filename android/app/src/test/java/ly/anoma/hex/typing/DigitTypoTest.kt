package ly.anoma.hex.typing

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class DigitTypoTest {
    private val en by lazy { SpatialDecoder(TypingModel.file(File("src/main/assets/typing/en.hextm"), "en")) }
    private val grid = KeyGrid(buildMap {
        for ((row, letters) in listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").withIndex()) for ((i, c) in letters.withIndex())
            put(c, KeyPoint(201 + (i - (letters.length - 1) / 2.0) * 36.0, 66.5 + 56 * row))
    }, 36.0, 56.0, KeyRect(64.76, 212.0, 303.1, 257.0))
    private val digitKeys = "qwertyuiop".withIndex().associate { (i, c) -> "1234567890"[i] to c }

    private fun typed(text: String, before: String = "that is "): TypingWord {
        val w = TypingWord(en, true); w.reset(before)
        for (c in text) {
            val p = grid.centres.getValue(digitKeys[c] ?: c)
            if (c.isDigit()) w.digit(c.toString(), p.x, p.y, grid) else w.letter(c.toString(), p.x, p.y, grid)
        }
        return w
    }

    @Test fun aDigitForItsLetterIsSuggestedAsTheWord() {
        for ((text, word) in listOf("w3ird" to "weird", "h3llo" to "hello", "kn0w" to "know", "2ould" to "would", "2rite" to "write", "3asy" to "easy")) {
            val strip = typed(text).strip().map { it.text }
            assertTrue("$text: $strip", word in strip)
        }
    }

    @Test fun aNumberGetsNoSuggestions() {
        for (text in listOf("2", "10", "2026")) assertTrue(text, typed(text).strip().isEmpty())
    }

    @Test fun aWordWithADigitIsNeverAutocorrected() {
        for (text in listOf("w3ird", "2rite", "mp3", "h2o", "b2b", "4k", "2026")) assertNull(text, typed(text).finish(' '))
    }
}
