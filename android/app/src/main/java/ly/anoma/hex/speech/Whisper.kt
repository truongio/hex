package ly.anoma.hex.speech

import android.content.Context
import android.content.res.AssetManager

internal interface Recognizer {
    fun prepare(options: SpeechOptions) = Unit
    fun decode(pcm: ShortArray, count: Int, options: SpeechOptions): String?
}

internal class LocalSpeech(private val app: Context) : Recognizer {
    override fun prepare(options: SpeechOptions) { if (usesParakeet(options)) Parakeet.warm(app) }

    override fun decode(pcm: ShortArray, count: Int, options: SpeechOptions): String? {
        if (usesParakeet(options)) Parakeet.decode(app, pcm, count)?.let { return it.trim() }
        val prompt = options.vocabulary.takeIf { it.isNotEmpty() }?.joinToString(", ")
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        val preferred = ModelStore.whisper.installed(app)
        val bytes = preferred?.let { Whisper.decode(app.assets, it.absolutePath, pcm, count, threads, options.spoken.code, options.translate, prompt) }
            ?: Whisper.decode(app.assets, null, pcm, count, threads, options.spoken.code, options.translate, prompt)
        return bytes?.decodeToString()?.trim()
    }

    /** Parakeet cannot translate, so a dictation pasted in English stays on Whisper. */
    private fun usesParakeet(options: SpeechOptions) =
        options.engine == Engine.Parakeet && !options.translate && ModelStore.parakeet.installed(app) != null
}

internal object Whisper {
    init { System.loadLibrary("hexspeech") }
    external fun decode(assets: AssetManager, file: String?, pcm: ShortArray, count: Int, threads: Int,
                        language: String, translate: Boolean, prompt: String?): ByteArray?
}
