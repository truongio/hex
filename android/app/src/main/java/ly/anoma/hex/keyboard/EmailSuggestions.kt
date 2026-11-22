package ly.anoma.hex.keyboard

internal object EmailSuggestions {
    private val ADDRESS = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}")
    const val KEPT = 20

    fun matching(addresses: List<String>, typed: String): List<String> =
        addresses.filter { it.startsWith(typed, ignoreCase = true) && !it.equals(typed, ignoreCase = true) }.take(3)

    fun found(text: String): List<String> = ADDRESS.findAll(text).map { it.value.trimEnd('.') }.toList()

    fun remember(saved: List<String>, typed: List<String>): List<String> =
        (typed.reversed() + saved).distinctBy { it.lowercase() }.take(KEPT)
}
