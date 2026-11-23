package ly.anoma.hex.keyboard

import android.text.InputType.*
import android.view.inputmethod.EditorInfo.*
import org.junit.Assert.assertEquals
import org.junit.Test

class FieldTypingTest {
    @Test fun searchBarsCompleteAndCorrect() {
        assertEquals(FieldTyping.Full, FieldTyping.of(TYPE_CLASS_TEXT, IME_ACTION_SEARCH))
        assertEquals(FieldTyping.Full, FieldTyping.of(TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_AUTO_COMPLETE, IME_ACTION_SEARCH))
        assertEquals(FieldTyping.Full, FieldTyping.of(TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_NO_SUGGESTIONS, IME_ACTION_SEARCH))
        assertEquals(FieldTyping.Full, FieldTyping.of(TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_FILTER, IME_ACTION_DONE))
        assertEquals(FieldTyping.Full, FieldTyping.of(TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_AUTO_COMPLETE, IME_ACTION_GO))
    }

    @Test fun browserBarsCompleteAndCorrect() {
        assertEquals(FieldTyping.Full, FieldTyping.of(TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_URI, IME_ACTION_GO))
        assertEquals(FieldTyping.Full, FieldTyping.of(TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_URI or TYPE_TEXT_FLAG_NO_SUGGESTIONS, IME_ACTION_GO))
    }

    @Test fun chatPromptsCompleteAndCorrect() {
        assertEquals(FieldTyping.Full, FieldTyping.of(TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_MULTI_LINE or TYPE_TEXT_FLAG_NO_SUGGESTIONS, IME_ACTION_NONE))
        assertEquals(FieldTyping.Full, FieldTyping.of(TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_MULTI_LINE or TYPE_TEXT_FLAG_CAP_SENTENCES or TYPE_TEXT_FLAG_NO_SUGGESTIONS, IME_ACTION_SEND))
        assertEquals(FieldTyping.Literal, FieldTyping.of(TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_MULTI_LINE or TYPE_TEXT_VARIATION_PASSWORD, IME_ACTION_NONE))
        assertEquals(FieldTyping.Literal, FieldTyping.of(TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_MULTI_LINE or TYPE_TEXT_VARIATION_EMAIL_ADDRESS, IME_ACTION_NONE))
    }

    @Test fun privateAndLiteralFieldsStayLiteral() {
        assertEquals(FieldTyping.Full, FieldTyping.of(TYPE_CLASS_TEXT, IME_ACTION_SEND))
        assertEquals(FieldTyping.Literal, FieldTyping.of(TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_NO_SUGGESTIONS, IME_ACTION_DONE))
        assertEquals(FieldTyping.Literal, FieldTyping.of(TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_PASSWORD, IME_ACTION_SEARCH))
        assertEquals(FieldTyping.Literal, FieldTyping.of(TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_EMAIL_ADDRESS, IME_ACTION_SEARCH))
        assertEquals(FieldTyping.Literal, FieldTyping.of(TYPE_CLASS_NUMBER, IME_ACTION_SEARCH))
        assertEquals(FieldTyping.Full, FieldTyping.of(TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_URI, IME_ACTION_GO or IME_FLAG_NO_PERSONALIZED_LEARNING))
    }
}
