package ly.anoma.hex.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldTypingTest {
    private fun text(variation: Int = InputType.TYPE_TEXT_VARIATION_NORMAL, flags: Int = 0,
                     options: Int = EditorInfo.IME_ACTION_NONE) =
        FieldTyping.of(InputType.TYPE_CLASS_TEXT or variation or flags, options)

    @Test fun aPlainTextFieldGetsWordsAndAutocorrection() {
        val plain = text()
        assertTrue(plain.words)
        assertTrue(plain.corrects)
        assertFalse(plain.secure)
        assertTrue(plain.learns)
        assertFalse(plain.exact)
    }

    @Test fun noLearningKeepsWordsAndAutocorrectionButRemembersNothing() {
        // A browser's incognito tab: the flag says only that nothing typed there is learned.
        val private = text(options = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        assertTrue(private.words)
        assertTrue(private.corrects)
        assertFalse(private.secure)
        assertFalse(private.learns)
    }

    @Test fun emailAddressesStayLiteral() {
        for (variation in listOf(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)) {
            assertFalse("variation $variation", text(variation).words)
            assertFalse("variation $variation", text(variation).corrects)
            assertFalse("variation $variation", text(variation).exact)
            assertFalse("variation $variation", text(variation).secure)
        }
        assertFalse(text(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            options = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING).words)
    }

    @Test fun urlFieldsReadAndCorrectWordsButKeepTheirMarks() {
        // A plain URL field: suggestions, swipes and this fork's autocorrection, but no space or tap fix of Hex's own.
        val url = text(InputType.TYPE_TEXT_VARIATION_URI)
        assertTrue(url.words)
        assertTrue(url.corrects)
        assertTrue(url.exact)
        assertFalse(url.secure)
        assertTrue(url.learns)
        // Chrome's address bar (a web search field too): URI | NO_SUGGESTIONS, imeOptions 0x12000002.
        val omnibox = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        assertEquals(0x80011, omnibox)
        val chrome = FieldTyping.of(omnibox, 0x12000002)
        assertTrue(chrome.words)
        assertTrue(chrome.corrects)
        assertTrue(chrome.exact)
        // An incognito tab's address bar is still a URL field, and remembers nothing typed there.
        val incognito = FieldTyping.of(omnibox, 0x12000002 or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        assertTrue(incognito.words)
        assertTrue(incognito.corrects)
        assertTrue(incognito.exact)
        assertFalse(incognito.learns)
    }

    @Test fun searchBoxesAndPromptsCorrectButOtherNoSuggestionFieldsDoNot() {
        // A search box, filter or completing field: this fork reads and corrects words there.
        val filtered = text(InputType.TYPE_TEXT_VARIATION_FILTER)
        assertTrue(filtered.words)
        assertTrue(filtered.corrects)
        assertTrue(text(flags = InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE).corrects)
        assertTrue(text(flags = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, options = EditorInfo.IME_ACTION_SEARCH).corrects)
        // Any other single-line field asking for no suggestions keeps its words without a fix.
        val plain = text(flags = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        assertTrue(plain.words)
        assertFalse(plain.corrects)
        // A chat app's multi-line prompt that asks for no suggestions still corrects (Gemini's).
        assertTrue(text(flags = InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, options = EditorInfo.IME_ACTION_SEND).corrects)
        // The Google app's search box: TEXT|MULTI_LINE|NO_SUGGESTIONS, whose suggestions and swipes Gboard keeps.
        val search = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        assertEquals(0xa0001, search)
        val google = FieldTyping.of(search, 0)
        assertTrue(google.words)
        assertTrue(google.corrects)
        assertFalse(google.exact)
    }

    @Test fun editorsThatKeepTheirOwnTextTakeKeyEvents() {
        // TYPE_NULL (a terminal): the field's text stays on its own side, so only key presses reach it.
        val terminal = FieldTyping.of(InputType.TYPE_NULL, 0)
        assertTrue(terminal.keyEvents)
        assertFalse(terminal.secure)
        assertFalse(terminal.words)
        assertFalse(terminal.corrects)
        // A flag only adds to the class: TYPE_NULL | MULTI_LINE is still TYPE_NULL.
        assertTrue(FieldTyping.of(InputType.TYPE_NULL or InputType.TYPE_TEXT_FLAG_MULTI_LINE, 0).keyEvents)
        assertFalse(text().keyEvents)
    }

    @Test fun passwordFieldsAreSecureAndNeverWords() {
        for (variation in listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) {
            assertTrue("variation $variation", text(variation).secure)
            assertFalse("variation $variation", text(variation).words)
            assertFalse("variation $variation", text(variation).corrects)
            assertFalse("variation $variation", text(variation).exact)
        }
        val pin = FieldTyping.of(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD, 0)
        assertTrue(pin.secure)
        assertFalse(pin.words)
        assertFalse(pin.corrects)
    }

    @Test fun numberFieldsAreNeverWords() {
        val number = FieldTyping.of(InputType.TYPE_CLASS_NUMBER, 0)
        assertFalse(number.secure)
        assertFalse(number.words)
        assertFalse(number.corrects)
    }
}
