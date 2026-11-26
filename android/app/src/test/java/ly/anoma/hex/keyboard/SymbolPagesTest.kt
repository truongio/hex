package ly.anoma.hex.keyboard

import ly.anoma.hex.speech.Spoken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gboard's two symbol pages (?123 and =<), which every layout shares: the keys each page draws, that
 * every printable ASCII character types from the letters or one of them, and that no page draws a key
 * twice, the bottom row included. */
class SymbolPagesTest {
    private fun page(language: Spoken = Spoken.English, more: Boolean = false, currency: Char? = null,
                     kind: FieldKind = FieldKind.Text) =
        KeyboardPage(language, symbols = true, more, shifted = false, locked = false, enter = EnterKey(null, 0),
            kind = kind, currency = currency)

    private fun lettersPage(language: Spoken) =
        KeyboardPage(language, symbols = false, more = false, shifted = false, locked = false, enter = EnterKey(null, 0))

    /** Every currency Labs › Keyboard › Local currency can put in $'s place, read as it reads them. */
    private val currencies = listOf("GBP", "EUR", "JPY", "CNY", "INR", "PHP", "KRW", "RUB", "UAH", "USD", "CHF")
        .map { LocalCurrency.symbol(it) }

    /** What the letters page types beside its letters: the digits and accents their holds offer, the
     * field's own mark, a period, and the punctuation a held period offers. */
    private fun letters(language: Spoken): Set<Char> {
        val page = lettersPage(language)
        val out = LinkedHashSet<Char>()
        for ((index, line) in page.lines.withIndex()) for ((position, c) in line.withIndex()) {
            out += c
            out += c.uppercaseChar() // shift types the same key's capital
            out += LetterFeedback.holds(c, language.layoutCode, page.hint(index, position)).flatMap { it.toList() }
        }
        out += page.commaText[0]
        out += page.periodText[0]
        out += LetterFeedback.periodHolds.flatten().flatMap { it.toList() }
        return out
    }

    /** What a symbol page types: each key's own character and what holding it offers, the bottom row's
     * comma and period keys, and what holding those offers. */
    private fun symbols(page: KeyboardPage): Set<Char> {
        val out = LinkedHashSet<Char>()
        for (line in page.lines) for (c in line) {
            out += c
            out += LetterFeedback.symbolHolds(c, page.currency).flatMap { it.toList() }
        }
        for (text in listOf(page.commaText, page.periodText)) {
            out += text[0]
            out += LetterFeedback.symbolHolds(text[0]).flatMap { it.toList() }
        }
        return out
    }

    @Test fun thePagesKeepTheForksLayout() {
        assertEquals(listOf("1234567890", "-/:;()\$&@\"", ".,?!'"), page(more = false).lines)
        assertEquals(listOf("[]{}#%^*+=", "_\\|~<>€£¥•", ".,?!'"), page(more = true).lines)
        for (more in listOf(false, true)) {
            assertEquals(",", page(more = more).commaText)
            assertEquals(".", page(more = more).periodText)
        }
    }

    @Test fun everyPrintableAsciiCharacterTypesOnEveryLayout() {
        val ascii = ('!'..'~').toList()
        for (language in Spoken.entries) for (currency in currencies) {
            val typed = LinkedHashSet<Char>()
            typed += letters(language)
            typed += symbols(page(language, more = false, currency = currency))
            typed += symbols(page(language, more = true, currency = currency))
            val missing = ascii.filter { it !in typed }
            assertTrue("${language.code} with ${currency ?: '$'} cannot type $missing", missing.isEmpty())
        }
    }

    @Test fun aLocalCurrencyTakesDollarsPlaceAndKeepsDollar() {
        for (code in listOf("GBP", "EUR", "JPY", "CNY", "INR", "PHP", "KRW", "RUB", "UAH", "USD", "CHF")) {
            val currency = LocalCurrency.symbol(code) ?: continue
            val numbers = page(currency = currency, more = false).lines.joinToString("")
            val symbols = page(currency = currency, more = true).lines.joinToString("")
            assertTrue("$code's 123 page lacks $currency", currency in numbers)
            assertTrue("$code draws no $", '$' in numbers + symbols)
        }
    }

    @Test fun noPageDrawsTheSameKeyTwice() {
        for (language in Spoken.entries) for (currency in currencies) for (more in listOf(false, true)) {
            val drawn = page(language, more = more, currency = currency).lines.joinToString("")
            val twice = drawn.groupBy { it }.filterValues { it.size > 1 }.keys
            assertTrue("${language.code} ${currency ?: '$'} draws $twice twice", twice.isEmpty())
        }
    }
}
