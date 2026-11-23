package ly.anoma.hex.typing

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class LearningTest {
    private fun decoder(language: String, learning: Learning?) =
        SpatialDecoder(TypingModel.file(File("src/main/assets/typing/$language.hextm"), language)).also { it.learning = learning }

    private val grid = KeyGrid(buildMap {
        for ((row, letters) in listOf("qwertyuiopå", "asdfghjklöä", "zxcvbnm").withIndex()) for ((i, c) in letters.withIndex())
            put(c, KeyPoint(201 + (i - (letters.length - 1) / 2.0) * 36.0, 66.5 + 56 * row))
    }, 36.0, 56.0, KeyRect(64.76, 212.0, 303.1, 257.0))

    private fun TypingWord.typed(text: String, dx: Double = 0.0, dy: Double = 0.0): String {
        val out = StringBuilder()
        reset("")
        for (c in text) {
            if (c == ' ') {
                val fix = finish(' ', KeyPoint(183.93, 234.5), grid)
                if (fix != null) { out.setLength(out.length - fix.original.length); out.append(fix.replacement) }
                out.append(c); reset(out.toString())
            } else {
                val p = grid.centres.getValue(c)
                val x = p.x + dx * grid.pitchX; val y = p.y + dy * grid.pitchY
                val hit = grid.centres.minBy { (_, k) -> (k.x - x) * (k.x - x) + (k.y - y) * (k.y - y) }.key
                letter(hit.toString(), x, y, grid); out.append(hit)
            }
        }
        return out.toString()
    }

    private val sentences = listOf("jag kommer hem sent i kväll", "kan du köpa mjölk på vägen", "vi ses på stationen klockan fem",
        "har du sett min telefon", "det var en bra dag idag", "jag är trött och vill sova", "ring mig när du är framme",
        "vi måste prata om helgen", "hon har redan ätit middag", "glöm inte att ta med nycklarna", "barnen leker i parken",
        "jag tror att det blir regn", "kan vi ta det imorgon", "tack för hjälpen med flytten", "han jobbar hemifrån idag",
        "vill du följa med på bio", "maten är klar om tio minuter", "jag hittar inte min plånbok")

    private fun errors(w: TypingWord, dx: Double, dy: Double) = sentences.sumOf { s ->
        w.typed("$s ", dx, dy).trim().split(' ').zip(s.split(' ')).count { (a, b) -> !a.equals(b, ignoreCase = true) }
    }

    @Test fun aSteadyOffsetIsLearnedAndThenReadAsTheKeysMeant() {
        val learning = Learning(null)
        val sv = decoder("sv", learning)
        val before = errors(TypingWord(sv, true).also { it.learns = false }, .58, .25)
        val training = TypingWord(sv, true)
        repeat(3) { for (s in sentences) training.typed("$s ", .58, .25) }
        assertTrue(learning.touch.samples() > 300)
        val after = errors(TypingWord(sv, true).also { it.learns = false }, .58, .25)
        assertTrue("before=$before after=$after", after < before)
    }

    @Test fun yourFrequentWordsAndPhrasesComeFirst() {
        val learning = Learning(null)
        val sv = decoder("sv", learning)
        val w = TypingWord(sv, true)
        val plain = TypingWord(decoder("sv", null), true)
        plain.reset("vi ses på ")
        for (c in "fi") { val p = grid.centres.getValue(c); plain.letter(c.toString(), p.x, p.y, grid) }
        assertFalse(plain.strip().toString(), plain.strip().any { it.text == "fikat" })
        repeat(5) { w.typed("vi ses på fikat ") }
        w.reset("vi ses på ")
        for (c in "fi") { val p = grid.centres.getValue(c); w.letter(c.toString(), p.x, p.y, grid) }
        assertTrue(w.strip().toString(), w.strip().any { it.text == "fikat" })
        assertTrue(TypingWord(sv, true).predictions("vi ses på ").toString(), "fikat" in TypingWord(sv, true).predictions("vi ses på "))
    }

    @Test fun aCorrectionKeptTwiceIsMadeAgainAndOneUndoForgetsIt() {
        val kept = KeptCorrections()
        kept.keep("tydligen", "tydligen"); kept.keep("tyvarr", "tyvärr")
        assertNull(kept.learned("tyvarr"))
        kept.keep("tyvarr", "tyvärr")
        assertEquals("tyvärr", kept.learned("TYVARR"))
        assertEquals(2, kept.times("tyvarr", "tyvärr"))
        kept.undone("tyvarr", "tyvärr")
        assertNull(kept.learned("tyvarr"))
        assertEquals(0, kept.times("tyvarr", "tyvärr"))
    }

    @Test fun whatWasLearnedSurvivesARestartAndClearForgetsIt() {
        val file = Files.createTempFile("learning", ".tsv").toFile()
        val first = Learning(file)
        first.history.note("vi", "ses"); first.history.note("vi", "ses")
        first.touch.note('e', grid.centres.getValue('e').x + 5, grid.centres.getValue('e').y, grid)
        first.corrections.keep("tyvarr", "tyvärr"); first.corrections.keep("tyvarr", "tyvärr")
        first.changed(); first.saveIfChanged()
        val second = Learning(file)
        assertEquals(2, second.history.count("ses"))
        assertEquals(listOf("ses"), second.history.next("vi", 3))
        assertEquals(1, second.touch.samples())
        assertEquals("tyvärr", second.corrections.learned("tyvarr"))
        second.clear()
        assertFalse(file.exists())
        assertEquals(0, Learning(file).history.count("ses"))
    }

    @Test fun nothingIsLearnedWhenTheFieldOrSettingSaysNo() {
        val learning = Learning(null)
        val sv = decoder("sv", learning)
        TypingWord(sv, true).also { it.learns = false }.typed("hemlig fras ")
        learning.enabled = false
        TypingWord(sv, true).typed("annan fras ")
        assertEquals(0, learning.history.total)
        assertEquals(0, learning.touch.samples())
    }

    @Test fun aLearnedOffsetMovesWhichKeyATouchTypes() {
        val touch = TouchModel()
        val e = grid.centres.getValue('e'); val r = grid.centres.getValue('r')
        val x = e.x + .58 * grid.pitchX
        assertEquals('r', touch.key('r', x, e.y, grid))
        repeat(40) { for (c in "qwertyuiop") { val p = grid.centres.getValue(c); touch.note(c, p.x + .58 * grid.pitchX, p.y, grid) } }
        assertEquals('e', touch.key('r', x, e.y, grid))
        assertEquals('r', touch.key('r', r.x + .58 * grid.pitchX, r.y, grid))
        assertEquals('r', TouchModel().key('r', x, e.y, grid))
    }
}
