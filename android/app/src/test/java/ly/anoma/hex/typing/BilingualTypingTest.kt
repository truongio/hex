package ly.anoma.hex.typing

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class BilingualTypingTest {
    private fun decoder(language: String) = SpatialDecoder(TypingModel.file(File("src/main/assets/typing/$language.hextm"), language))
    private val sv by lazy { decoder("sv") }
    private val en by lazy { decoder("en") }
    private fun swedish() = TypingWord(sv, true)
    private fun mixed() = TypingWord(sv, true, en)

    private val grid = KeyGrid(buildMap {
        for ((row, letters) in listOf("qwertyuiopå", "asdfghjklöä", "zxcvbnm").withIndex()) for ((i, c) in letters.withIndex())
            put(c, KeyPoint(201 + (i - (letters.length - 1) / 2.0) * 36.0, 66.5 + 56 * row))
    }, 36.0, 56.0, KeyRect(64.76, 212.0, 303.1, 257.0))

    private fun TypingWord.typed(text: String, aimed: String = text): String {
        val out = StringBuilder()
        reset("")
        for ((i, c) in text.withIndex()) {
            if (c == ' ' || TypingWord.endsWord(c)) {
                val fix = if (c == ' ') finish(' ', KeyPoint(183.93, 234.5), grid) else finish(c)
                if (fix != null) {
                    assertTrue(text, out.endsWith(fix.original))
                    out.setLength(out.length - fix.original.length); out.append(fix.replacement)
                }
                out.append(c); reset(out.toString())
            } else {
                val p = grid.centres.getValue(aimed[i].lowercaseChar())
                letter(c.toString(), p.x, p.y, grid); out.append(c)
            }
        }
        return out.toString()
    }

    @Test fun swedishTypingIsCorrected() {
        for ((typed, expected) in listOf(
            "jsg vill ha kaffe " to "jag vill ha kaffe ", "hwj hur mar du " to "hej hur mår du ",
            "vi ses pa fredag " to "vi ses på fredag ", "det var jattebra " to "det var jättebra ",
            "jag bor i stockholm " to "jag bor i Stockholm ", "kaffekoppsfabriken " to "kaffekoppsfabriken ",
            "vi ses i helgen " to "vi ses i helgen ", "det blir knappast bra " to "det blir knappast bra ",
            "jag maste ga nu " to "jag måste gå nu ",
        )) assertEquals(typed, expected, swedish().typed(typed))
    }

    @Test fun withoutASecondLanguageEnglishIsMangled() {
        assertNotEquals("can you send the file ", swedish().typed("can you send the file "))
    }

    @Test fun englishAndSwedishInOneSentenceStayAsTyped() {
        for (text in listOf("ska vi ta ett meeting imorgon ", "can you send the file ", "that was so mysigt ",
            "det var en nice dag ", "jag tror att the meeting ", "the meeting is at nine ", "sorry jag är sen ",
            "jag är lite tired idag ", "har du sett min telefon ", "kan du skicka filen till mig "))
            assertEquals(text, text, mixed().typed(text))
    }

    @Test fun typosAreFixedInEitherLanguage() {
        for ((typed, expected) in listOf(
            "jag har ett meetign imorgon " to "jag har ett meeting imorgon ",
            "jsg har ett meeting imorgon " to "jag har ett meeting imorgon ",
            "can you semd the file " to "can you send the file ",
            "you dont know " to "you don't know ",
            "sorry jag ar sen " to "sorry jag är sen ",
            "vi har en deadline pa fredag " to "vi har en deadline på fredag ",
        )) assertEquals(typed, expected, mixed().typed(typed))
    }

    @Test fun stripAndPredictionsSpeakBothLanguages() {
        assertEquals("jag bor i Stockholm ", mixed().typed("jag bor i stockholm "))
        val w = mixed(); w.reset("vi har ett ")
        for (c in "meet") { val p = grid.centres.getValue(c); w.letter(c.toString(), p.x, p.y, grid) }
        assertTrue(w.strip().toString(), w.strip().any { it.text == "meeting" })
        val english = mixed().predictions("send me the ")
        val swedish = mixed().predictions("jag vill ha en ")
        assertTrue(english.toString(), english.none { it in swedish })
        assertTrue(english.toString(), english.all { en.model.index(it.lowercase()) >= 0 })
    }

    @Test fun bundledWordsAreKeptAndSpelledTheirWay() {
        val own = decoder("sv").also { it.personal = PersonalWords(it.model, listOf("debloat", "TikTok"), emptyMap()) }
        val w = TypingWord(own, true, en)
        assertEquals("debloat TikTok ", w.typed("debloat tiktok "))
    }

    @Test fun rareAndUnknownWordsAreWorthLearningCommonOnesAreNot() {
        val w = mixed()
        assertTrue(w.worthLearning("zorbexflim"))
        assertTrue(sv.model.validLiteral("arbetsintervjun") || sv.model.index("arbetsintervjun") >= 0)
        assertTrue(w.worthLearning("arbetsintervjun"))
        assertFalse(w.worthLearning("och"))
        assertFalse(w.worthLearning("meeting"))
        val own = decoder("sv").also { it.personal = PersonalWords(it.model, listOf("zorbexflim"), emptyMap()) }
        assertFalse(TypingWord(own, true, en).worthLearning("Zorbexflim"))
    }

    @Test fun aLearnedWordIsOfferedAsItsFirstLettersAreTyped() {
        val own = decoder("sv").also { it.personal = PersonalWords(it.model, listOf("arbetsintervjun"), emptyMap()) }
        val w = TypingWord(own, true, en); w.reset("jag har en ")
        for (c in "arbe") { val p = grid.centres.getValue(c); w.letter(c.toString(), p.x, p.y, grid) }
        assertTrue(w.strip().toString(), w.strip().any { it.text == "arbetsintervjun" })
    }

    @Test fun aLongWordWithAFarLetterSwappedIsStillFixed() {
        val own = decoder("sv").also { it.personal = PersonalWords(it.model, listOf("flingsalt"), emptyMap()) }
        assertEquals("jag köpte flingsalt ", TypingWord(own, true, en).typed("jag köpte flingsald "))
    }

    @Test fun aKnownWordStillOffersAMuchCommonerNeighbour() {
        for (before in listOf("", "what's the postal code for ")) {
            val w = mixed(); w.reset(before)
            for (c in "thus") { val p = grid.centres.getValue(c); w.letter(c.toString(), p.x, p.y, grid) }
            assertEquals(before, listOf("thus", "this"), w.strip().take(2).map { it.text })
            assertNull(w.replacement())
        }
    }

    @Test fun deleteBackIntoAWordOffersWhatItsTypingDid() {
        val w = mixed(); val before = "what's the postal code for "
        w.reset(before)
        for (c in "thus") { val p = grid.centres.getValue(c); w.letter(c.toString(), p.x, p.y, grid) }
        val typing = w.strip()
        w.reset(); w.reset(before + "thus ")
        w.resume("thus", before)
        assertEquals(typing, w.strip())
        w.resume("jsg", "")
        assertEquals(listOf("jsg"), w.strip().take(1).map { it.text })
        val fixed = mixed(); fixed.reset("")
        for (c in "jsg") { val p = grid.centres.getValue(c); fixed.letter(c.toString(), p.x, p.y, grid) }
        assertEquals("jag", fixed.replacement())
        fixed.reset(); fixed.resume("jsg", "", keep = true)
        assertNull(fixed.replacement())
        assertTrue(fixed.strip().toString(), fixed.strip().any { it.text == "jag" })
    }
}
