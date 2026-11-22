package ly.anoma.hex.typing

import android.content.Context
import java.security.MessageDigest
import ly.anoma.hex.speech.Spoken

internal class TypingPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("typing", Context.MODE_PRIVATE)
    var layout: Spoken?
        get() = Spoken.matching(prefs.getString("layout", null))
        set(value) { prefs.edit().putString("layout", value?.layoutCode).apply() }
    fun secondLanguage(layout: Spoken): Spoken? {
        val saved = prefs.getString(SECOND_LANGUAGE, null)
        val choice = when (saved) {
            null -> if (layout == Spoken.Swedish) Spoken.English else null
            NONE -> null
            else -> Spoken.matching(saved)
        }
        return choice?.takeIf { it != layout }
    }
    fun secondLanguage(value: Spoken?) { prefs.edit().putString(SECOND_LANGUAGE, value?.layoutCode ?: NONE).apply() }
    /** Settings › Keyboard › Auto-capitalisation: shift comes on where the field asks for a capital. On by default. */
    var autoCapitalize: Boolean
        get() = prefs.getBoolean("autoCapitalize", true)
        set(value) { prefs.edit().putBoolean("autoCapitalize", value).apply() }
    var autocorrect: Boolean
        get() = prefs.getBoolean("autocorrect", true)
        set(value) { prefs.edit().putBoolean("autocorrect", value).apply() }
    var suggestSwearWords: Boolean
        get() = prefs.getBoolean(SUGGEST_SWEAR_WORDS, false)
        set(value) { prefs.edit().putBoolean(SUGGEST_SWEAR_WORDS, value).apply() }
    var spatial: Boolean
        get() = prefs.getBoolean("spatial", true)
        set(value) { prefs.edit().putBoolean("spatial", value).apply() }
    var keyHeight: Int
        get() = prefs.getInt(KEY_HEIGHT, 100).coerceIn(KEY_HEIGHTS)
        set(value) { prefs.edit().putInt(KEY_HEIGHT, value.coerceIn(KEY_HEIGHTS)).apply() }
    var sideMargin: Int
        get() = prefs.getInt(SIDE_MARGIN, 0).coerceIn(SIDE_MARGINS)
        set(value) { prefs.edit().putInt(SIDE_MARGIN, value.coerceIn(SIDE_MARGINS)).apply() }
    var numberRow: Boolean
        get() = prefs.getBoolean(NUMBER_ROW, false)
        set(value) { prefs.edit().putBoolean(NUMBER_ROW, value).apply() }
    var holdMs: Int
        get() = prefs.getInt(HOLD_MS, 150).coerceIn(HOLD_RANGE)
        set(value) { prefs.edit().putInt(HOLD_MS, value.coerceIn(HOLD_RANGE)).apply() }
    var hideComma: Boolean
        get() = prefs.getBoolean(HIDE_COMMA, false)
        set(value) { prefs.edit().putBoolean(HIDE_COMMA, value).apply() }
    var hideEmoji: Boolean
        get() = prefs.getBoolean(HIDE_EMOJI, false)
        set(value) { prefs.edit().putBoolean(HIDE_EMOJI, value).apply() }
    var swipeDelete: Boolean
        get() = prefs.getBoolean("swipeDelete", true)
        set(value) { prefs.edit().putBoolean("swipeDelete", value).apply() }
    var learnedEmails: List<String>
        get() = prefs.getString("learnedEmails", null)?.split('\n')?.filter(String::isNotEmpty).orEmpty()
        set(value) { prefs.edit().putString("learnedEmails", value.joinToString("\n")).apply() }
    var popups: Boolean
        get() = prefs.getBoolean("popups", true)
        set(value) { prefs.edit().putBoolean("popups", value).apply() }
    var haptics: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(value) { prefs.edit().putBoolean("haptics", value).apply() }

    // Remember only explicit rejection of this exact pair in this language; never save typed text.
    private fun pair(language: String, original: String, replacement: String): String {
        val bytes = (language + "\u0000" + original.lowercase() + "\u0000" + replacement.lowercase()).toByteArray()
        return android.util.Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes), android.util.Base64.NO_WRAP)
    }
    fun blocked(language: String, original: String, replacement: String) =
        prefs.getInt(pair(language, original, replacement), 0) >= 2
    fun undo(language: String, original: String, replacement: String) {
        val key = pair(language, original, replacement)
        prefs.edit().putInt(key, (prefs.getInt(key, 0) + 1).coerceAtMost(2)).apply()
    }
    companion object {
        const val SUGGEST_SWEAR_WORDS = "suggestSwearWords"
        private const val SECOND_LANGUAGE = "secondLanguage"
        const val KEY_HEIGHT = "keyHeight"
        const val SIDE_MARGIN = "sideMargin"
        const val NUMBER_ROW = "numberRow"
        const val HIDE_COMMA = "hideComma"
        const val HIDE_EMOJI = "hideEmoji"
        private const val HOLD_MS = "holdMs"
        val HOLD_RANGE = 50..1000
        val KEY_HEIGHTS = 70..140
        val SIDE_MARGINS = 0..48
        private const val NONE = "none"
    }
}
