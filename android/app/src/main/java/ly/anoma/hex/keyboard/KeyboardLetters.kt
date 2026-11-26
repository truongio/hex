package ly.anoma.hex.keyboard

/** Each layout's letter rows, lowercase, keyed by the layout's BCP 47 code. A new layout is a new
 * entry here (and its accents in LetterFeedback); anything unknown types on English's rows. */
internal object KeyboardLetters {
    private val rows = mapOf(
        "en" to listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
        "sv" to listOf("qwertyuiopå", "asdfghjklöä", "zxcvbnm"),
    )

    /** The number row's digits, in the order Gboard's row 1 holds them. */
    const val DIGITS = "1234567890"

    fun rows(layoutCode: String) = rows[layoutCode] ?: rows.getValue("en")

    /** Row 1's digits, 1 to 0 on its first ten keys, as Gboard draws and holds them. */
    fun digit(row: Int, index: Int): Char? = if (row == 0 && index < 10) DIGITS[index] else null

    /** The 123 page and the second symbols page. [currency] is Labs › Keyboard › Local currency's symbol,
     * in $'s place on the 123 page, which gives $ its slot on the second page, or ¥'s. */
    fun numbers(currency: Char? = null) = listOf(DIGITS, "-/:;()${currency ?: '$'}&@\"", PUNCTUATION)
    fun symbols(currency: Char? = null): List<String> {
        val money = when {
            currency == null -> "€£¥"
            currency in "€£¥" -> "€£¥".replace(currency, '$')
            else -> "€£$"
        }
        return listOf("[]{}#%^*+=", "_\\|~<>$money•", PUNCTUATION)
    }
    private const val PUNCTUATION = ".,?!'"
}
