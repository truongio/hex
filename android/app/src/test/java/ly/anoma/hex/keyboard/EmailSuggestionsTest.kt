package ly.anoma.hex.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class EmailSuggestionsTest {
    private val addresses = listOf("first.person@example.com", "fp@example.org")

    @Test fun anEmptyFieldOffersEveryAddress() = assertEquals(addresses, EmailSuggestions.matching(addresses, ""))

    @Test fun whatIsTypedNarrowsThem() {
        assertEquals(listOf("first.person@example.com"), EmailSuggestions.matching(addresses, "Fi"))
        assertEquals(listOf("fp@example.org"), EmailSuggestions.matching(addresses, "fp@"))
        assertEquals(emptyList<String>(), EmailSuggestions.matching(addresses, "fp@example.org"))
        assertEquals(emptyList<String>(), EmailSuggestions.matching(addresses, "someone"))
    }
}
