package ly.anoma.hex.speech

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** NVIDIA Parakeet TDT 0.6B v3 through sherpa-onnx. Loading takes seconds and holds over a gigabyte,
 * so it starts when a dictation starts, while the person speaks, and is released after a quiet spell. */
internal object MultilingualParakeet {
    private const val idleSeconds = 120L
    private val worker = Executors.newSingleThreadScheduledExecutor { Thread(it, "Hex Parakeet v3").apply { isDaemon = true } }
    private var recognizer: OfflineRecognizer? = null
    private var release: ScheduledFuture<*>? = null

    fun warm(context: Context) {
        val app = context.applicationContext
        worker.execute { load(app) }
    }

    fun decode(context: Context, pcm: ShortArray, count: Int): String? {
        val app = context.applicationContext
        return try {
            worker.submit<String?> {
                val engine = load(app) ?: return@submit null
                val samples = FloatArray(count) { pcm[it] / 32768f }
                val stream = engine.createStream()
                try {
                    stream.acceptWaveform(samples, 16_000)
                    engine.decode(stream)
                    engine.getResult(stream).text
                } finally { stream.release() }
            }.get()
        } catch (e: Exception) {
            Log.w("HexSpeech", "Parakeet failed", e)
            null
        }
    }

    private fun load(app: Context): OfflineRecognizer? {
        release?.cancel(false)
        release = worker.schedule({ recognizer?.release(); recognizer = null }, idleSeconds, TimeUnit.SECONDS)
        recognizer?.let { return it }
        val dir = ModelStore.multilingual.installed(app) ?: return null
        fun path(name: String) = File(dir, name).absolutePath
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 128),
            modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(path("encoder.int8.onnx"), path("decoder.int8.onnx"), path("joiner.int8.onnx")),
                tokens = path("tokens.txt"),
                numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4),
                provider = "cpu",
                modelType = "nemo_transducer",
            ),
            decodingMethod = "greedy_search",
        )
        return try {
            OfflineRecognizer(config = config).also { recognizer = it }
        } catch (e: Throwable) {
            Log.w("HexSpeech", "Parakeet unavailable", e)
            null
        }
    }
}
