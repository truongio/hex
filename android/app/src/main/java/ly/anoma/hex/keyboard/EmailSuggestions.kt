package ly.anoma.hex.keyboard

internal object EmailSuggestions {
    fun matching(addresses: List<String>, typed: String): List<String> =
        addresses.filter { it.startsWith(typed, ignoreCase = true) && !it.equals(typed, ignoreCase = true) }.take(3)
}
