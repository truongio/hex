package ly.anoma.hex.keyboard

import ly.anoma.hex.speech.Spoken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyboardPageTest {
    private fun page(numberRow: Boolean, symbols: Boolean = false, more: Boolean = false, pad: Pad? = null,
                     language: Spoken = Spoken.English) =
        KeyboardPage(language, symbols, more, shifted = false, locked = false, enter = EnterKey(null, 0),
            pad = pad, numberRow = numberRow)

    @Test fun theNumberRowShowsOneToZeroAboveTheLetters() {
        assertEquals("1234567890", page(numberRow = true).digits)
    }

    @Test fun offThereIsNoNumberRow() {
        assertNull(page(numberRow = false).digits)
    }

    @Test fun theNumberRowNeverChangesTheLettersOrTheSymbolPages() {
        assertEquals(KeyboardLetters.rows("en"), page(numberRow = true).lines)
        assertNull(page(numberRow = true, symbols = true).digits)
        assertNull(page(numberRow = true, symbols = true, more = true).digits)
        // A phone or number field's pad draws its own keys; the number row is for the letters.
        assertNull(page(numberRow = true, symbols = true, pad = Pad.Number).digits)
    }

    @Test fun aPadPageKeepsItsOwnKeysWithoutTheNumberRow() {
        // A pad page can be drawn without the symbol pages' flag; the number row is still not its row.
        assertNull(page(numberRow = true, pad = Pad.Number).digits)
    }

    @Test fun theNumberRowHidesTheLetterRowsCornerDigits() {
        val plain = page(numberRow = false)
        assertEquals("1", plain.hint(0, 0)?.toString())
        assertEquals("0", plain.hint(0, 9)?.toString())
        assertNull(plain.hint(1, 0))
        assertNull(page(numberRow = true).hint(0, 0))
        assertNull(page(numberRow = true).hint(0, 9))
    }

    @Test fun heldLettersKeepTheirAccentsWithoutTheirDigit() {
        assertEquals(listOf("e", "è", "é", "ê", "ë", "ē", "ė", "ę"),
            LetterFeedback.holds('e', "en", page(numberRow = true).hint(0, 2)))
        assertEquals(listOf("3", "e", "è", "é", "ê", "ë", "ē", "ė", "ę"),
            LetterFeedback.holds('e', "en", page(numberRow = false).hint(0, 2)))
    }

    @Test fun withBothLabsTheTopRowShowsGboardsSymbols() {
        // With the number row on, the hidden digit lets q to p show and hold Gboard's symbols.
        val both = page(numberRow = true)
        assertEquals(listOf("%", "\\", "|", "=", "[", "]", "<", ">", "{", "}"),
            "qwertyuiop".map { LetterFeedback.letterSymbol(it, topRow = true)?.toString() })
        assertEquals("%", both.hint(0, 0)?.toString() ?: LetterFeedback.letterSymbol('q', topRow = true)?.toString())
        assertEquals("}", both.hint(0, 9)?.toString() ?: LetterFeedback.letterSymbol('p', topRow = true)?.toString())
        assertEquals(listOf("%"), LetterFeedback.holds('q', "en", both.hint(0, 0), symbolsOnLetters = true, topRow = true))
        assertEquals(listOf("}"), LetterFeedback.holds('p', "en", both.hint(0, 9), symbolsOnLetters = true, topRow = true))
        // With the number row off, the digit is still the hint and the first hold, before any symbol.
        val digits = page(numberRow = false)
        assertEquals("1", digits.hint(0, 0)?.toString())
        assertEquals(listOf("1"),
            LetterFeedback.holds('q', "en", digits.hint(0, 0), symbolsOnLetters = true, topRow = true))
        // Off the lab, the top row is what it always was: its digit and accents.
        assertEquals(listOf("1"), LetterFeedback.holds('q', "en", digits.hint(0, 0)))
    }

    @Test fun theHomeAndBottomRowsShowGboardsSymbols() {
        // Gboard's English QWERTY: the marks its ?123 page draws on the same rows.
        assertEquals(listOf("@", "#", "$", "_", "&", "-", "+", "(", ")"),
            "asdfghjkl".map { LetterFeedback.letterSymbol(it)?.toString() })
        assertEquals(listOf("*", "\"", "'", ":", ";", "!", "?"),
            "zxcvbnm".map { LetterFeedback.letterSymbol(it)?.toString() })
        // ! and ? are holdable from n and m, as Gboard has them; $ stays d's own symbol.
        assertEquals(listOf("!") + LetterFeedback.options('n', "en"),
            LetterFeedback.holds('n', "en", null, symbolsOnLetters = true))
        assertEquals(listOf("?"), LetterFeedback.holds('m', "en", null, symbolsOnLetters = true))
        assertEquals(listOf("$"), LetterFeedback.holds('d', "en", null, symbolsOnLetters = true))
    }

    /** Every symbol the letters page shows in a key's corner with both labs on, exactly as KeyPlaneView
     * resolves it: its digit, else its letter symbol. */
    private fun letterHints(shown: KeyboardPage): List<Char> = shown.lines.flatMapIndexed { row, line ->
        line.withIndex().mapNotNull { (position, c) ->
            shown.hint(row, position) ?: LetterFeedback.letterSymbol(c, topRow = row == 0, currency = shown.currency)
        }
    }

    @Test fun noSymbolIsDrawnOnTwoLetterKeys() {
        // With both labs on, every letter key's corner is a symbol and no mark lands on two keys, on every
        // layout and for every currency d can hold: r's = is no longer j's too.
        val currencies = listOf<Char?>(null) + listOf("GBP", "EUR", "JPY", "CNY", "INR", "PHP", "KRW", "RUB", "UAH")
            .map { LocalCurrency.symbol(it) }
        for (language in Spoken.entries) for (currency in currencies) {
            val shown = letterHints(page(numberRow = true, language = language).copy(currency = currency))
            assertEquals("${language.code} with ${currency ?: '$'}", emptyList<Char>(),
                shown.groupBy { it }.filterValues { it.size > 1 }.keys.toList())
        }
    }

    @Test fun theLocalCurrencyTakesDsSymbol() {
        // Off, or in a region with no symbol Hex knows, d holds Gboard's $.
        assertEquals("$", LetterFeedback.letterSymbol('d')?.toString())
        // On, d holds the phone's own, as the ?123 page shows it.
        assertEquals("£", LetterFeedback.letterSymbol('d', currency = '£')?.toString())
        assertEquals("€", LetterFeedback.letterSymbol('D', currency = '€')?.toString())
        assertEquals(listOf("¥"), LetterFeedback.holds('d', "en", null, symbolsOnLetters = true, currency = '¥'))
        // As the letters page resolves it: with both labs on, d's corner shows the page's currency, and $
        // when the lab is off or the region has none.
        val letters = page(numberRow = true).copy(currency = '£')
        val d = letters.lines[1].indexOf('d')
        assertEquals("£", letters.hint(1, d)?.toString()
            ?: LetterFeedback.letterSymbol('d', topRow = false, currency = letters.currency)?.toString())
        assertEquals("$", LetterFeedback.letterSymbol('d', currency = page(numberRow = true).currency)?.toString())
        // Only d: the rest of the rows keep Gboard's marks.
        assertEquals("@", LetterFeedback.letterSymbol('a', currency = '¥')?.toString())
        assertEquals("?", LetterFeedback.letterSymbol('m', currency = '¥')?.toString())
    }
}
