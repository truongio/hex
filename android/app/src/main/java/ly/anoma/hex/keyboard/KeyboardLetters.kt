package ly.anoma.hex.keyboard

/** Each layout's letter rows, lowercase, keyed by the layout's BCP 47 code. A new layout is a new
 * entry here (and its accents in LetterFeedback); anything unknown types on English's rows. */
internal object KeyboardLetters {
    private val rows = mapOf(
        "en" to listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
        "sv" to listOf("qwertyuiopå", "asdfghjklöä", "zxcvbnm"),
    )

    fun rows(layoutCode: String) = rows[layoutCode] ?: rows.getValue("en")

    /** Row 1's digits, 1 to 0 on its first ten keys, as Gboard draws and holds them. */
    fun digit(row: Int, index: Int): Char? = if (row == 0 && index < 10) "1234567890"[index] else null

    /** The 123 page and the second symbols page. */
    fun numbers(layoutCode: String) = listOf("1234567890", "-/:;()$&@\"", punctuation(layoutCode, more = false))
    fun symbols(layoutCode: String) = listOf("[]{}#%^*+=", "_\\|~<>€£¥•", punctuation(layoutCode, more = true))
    private fun punctuation(layoutCode: String, more: Boolean) = ".,?!'"
}
