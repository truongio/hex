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

    @Test fun addressesTypedAreFoundAndRememberedNewestFirst() {
        assertEquals(listOf("new.one@mail.example.se"), EmailSuggestions.found("to new.one@mail.example.se."))
        assertEquals(emptyList<String>(), EmailSuggestions.found("not an address @ all"))
        val saved = EmailSuggestions.remember(listOf("old@example.com"), listOf("New@Example.com"))
        assertEquals(listOf("New@Example.com", "old@example.com"), saved)
        assertEquals(listOf("old@example.com", "New@Example.com"), EmailSuggestions.remember(saved, listOf("OLD@example.com")).let {
            listOf(it[0].lowercase(), it[1]) })
        assertEquals(EmailSuggestions.KEPT, EmailSuggestions.remember(emptyList(), (1..30).map { "a$it@x.se" }).size)
    }
}
