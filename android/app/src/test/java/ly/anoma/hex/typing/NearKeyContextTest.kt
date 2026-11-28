package ly.anoma.hex.typing

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class NearKeyContextTest {
    private fun decoder(language: String) = SpatialDecoder(TypingModel.file(File("src/main/assets/typing/$language.hextm"), language))
    private val sv by lazy { decoder("sv") }
    private val en by lazy { decoder("en") }

    private val grid = KeyGrid(buildMap {
        for ((row, letters) in listOf("qwertyuiopå", "asdfghjklöä", "zxcvbnm").withIndex()) for ((i, c) in letters.withIndex())
            put(c, KeyPoint(201 + (i - (letters.length - 1) / 2.0) * 36.0, 66.5 + 56 * row))
    }, 36.0, 56.0, KeyRect(64.76, 212.0, 303.1, 257.0))

    // `toward` nudges the tap for the letter at an index of the text that share of the way to another key.
    private fun TypingWord.typed(text: String, toward: Map<Int, Pair<Char, Double>> = emptyMap()): String {
        val out = StringBuilder()
        reset("")
        for ((i, c) in text.withIndex()) {
            if (c == ' ') {
                val fix = finish(' ', KeyPoint(183.93, 234.5), grid)
                if (fix != null) { out.setLength(out.length - fix.original.length); out.append(fix.replacement) }
                out.append(c); reset(out.toString())
            } else {
                var p = grid.centres.getValue(c.lowercaseChar())
                toward[i]?.let { (key, share) -> val q = grid.centres.getValue(key); p = KeyPoint(p.x + (q.x - p.x) * share, p.y + (q.y - p.y) * share) }
                letter(c.toString(), p.x, p.y, grid); out.append(c)
            }
        }
        return out.toString()
    }

    @Test fun theNextWordRereadsANeighbouringKey() {
        val text = "gillar du någon av dem hör färgerna "
        val ö = text.indexOf("hör") + 1
        assertEquals("gillar du någon av dem här färgerna ", TypingWord(sv, true).typed(text, mapOf(ö to ('ä' to .3))))
        assertEquals("gillar du någon av dem här färgerna ", TypingWord(sv, true, en).typed(text, mapOf(ö to ('ä' to .3))))
    }

    @Test fun sentencesTypedRightStayAsTyped() {
        val changed = ArrayList<String>()
        for (text in listOf(
            "jag hör dig ", "jag har en bil ", "dem har färgerna ", "vi ses imorgon ", "han bor i göteborg ",
            "det är bra ", "kom hit nu ", "vi far till stan ", "jag får inte ", "han bör gå ", "jag hör inte vad du säger ",
            "var är du ", "vår bil är röd ", "vad gör du ", "hon går hem ", "ska vi äta ", "jag vet inte ", "min mamma är sjuk ",
            "har du sett filmen ", "här är den ", "det var en gång ", "jag tycker om dig ", "vi ska åka till skolan ",
            "det kostar mycket ", "jag kan inte komma ", "hur mår du ", "kan du hämta mig ", "vi hörs sen ",
            "jag läser en bok ", "det regnar ute ", "hon heter Anna ", "vill du ha kaffe ", "jag gör det sen ",
        )) for (w in listOf(TypingWord(sv, true), TypingWord(sv, true, en))) w.typed(text).let { if (it != text) changed += "$text -> $it" }
        assertEquals(emptyList<String>(), changed)
        for (text in listOf("i want to go home ", "can you send the file ", "that is not what i said ", "we were there "))
            assertEquals(text, text.replace("i ", "I "), TypingWord(en, true).typed(text).replace("i ", "I "))
    }
}
