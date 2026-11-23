package ly.anoma.hex.speech

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

/** One file of a speech model: where it comes from and the exact bytes it must have. */
internal class ModelFile(val name: String, val url: String, val size: Long, val hash: String)

/** The only network operations in Hex: explicit, fixed model GETs from setup or Dictation settings. */
internal class ModelStore private constructor(private val folder: String, private val files: List<ModelFile>) {
    companion object {
        private const val parakeetRepo = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/resolve/ed5d745623472f97cfc2e6c2916ee248798fc5a8"
        val whisper = ModelStore("", listOf(ModelFile("whisper-small-ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb.bin",
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-small-q5_1.bin",
            190085487L, "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb")))
        val parakeet = ModelStore("parakeet-tdt-0.6b-v3-int8-ed5d745", listOf(
            ModelFile("tokens.txt", "$parakeetRepo/tokens.txt", 93939L, "d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d"),
            ModelFile("decoder.int8.onnx", "$parakeetRepo/decoder.int8.onnx", 11845274L, "0222a86147299d4dee90edf95b6d13827edb42ad8b7c9cf0b2582f030937c0b5"),
            ModelFile("joiner.int8.onnx", "$parakeetRepo/joiner.int8.onnx", 6355277L, "cbd02f3696e9944970111fb439d9c3fd365a80374565f13f2b9c7007d253fc98"),
            ModelFile("encoder.int8.onnx", "$parakeetRepo/encoder.int8.onnx", 652282294L, "1afbe69c1ffdabee94755f42e4daf1d93549131a3da11356bda84fcef0f2c479")))
    }

    enum class Problem { Connection, Server, Space }
    sealed interface State {
        data object Available : State
        data object Ready : State
        data class Retry(val problem: Problem) : State
        data class Loading(val percent: Int) : State
    }
    private class Failure(val problem: Problem) : Exception()
    private val progress = MutableStateFlow<State>(State.Available)
    val state = progress.asStateFlow()
    private val size = files.sumOf { it.size }
    private var running = false
    private var waiting = false
    private var watching = false
    private var started = 0
    private var stops = 0
    private fun dir(context: Context) = if (folder.isEmpty()) context.noBackupFilesDir else File(context.noBackupFilesDir, folder)
    private fun path(context: Context, file: ModelFile) = File(dir(context), file.name)
    private fun part(context: Context, file: ModelFile) = File(dir(context), file.name.substringBeforeLast('.') + ".part")
    private fun done(context: Context, file: ModelFile) = path(context, file).let { it.isFile && it.length() == file.size }
    private fun partial(context: Context) = files.any { part(context, it).exists() || done(context, it) }
    private fun have(context: Context) = files.sumOf { if (done(context, it)) it.size else part(context, it).length().coerceIn(0, it.size) }
    /** The model's folder (or its single file) once every file is in place. */
    fun installed(context: Context): File? =
        if (files.all { done(context, it) }) (if (files.size == 1) path(context, files[0]) else dir(context)) else null

    @Synchronized fun refresh(context: Context) {
        val app = context.applicationContext as Application
        if (!watching) { watching = true; app.registerActivityLifecycleCallbacks(foreground) }
        if (installed(app) != null) progress.value = State.Ready
    }

    // Android closes a backgrounded app's connections, and a swipe from recents or a reboot ends the process.
    // A started download keeps its partial file and carries on whenever a Hex screen is in front again.
    private val foreground = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            synchronized(this@ModelStore) {
                if (started++ == 0 && !running && (waiting || partial(activity)) && installed(activity) == null) download(activity)
            }
        }
        override fun onActivityStopped(activity: Activity) { synchronized(this@ModelStore) { if (--started == 0) stops++ } }
        override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    @Synchronized fun download(context: Context) {
        val app = context.applicationContext
        if (installed(app) != null) { progress.value = State.Ready; return }
        if (running) return
        running = true; waiting = false
        progress.value = State.Loading((have(app) * 100 / size).toInt().coerceAtMost(99))
        val since = stops
        Thread({
            val result = try {
                dir(app).mkdirs()
                // A resumed file that fails the checksum is discarded and fetched once more from the start.
                if (files.all { done(app, it) || part(app, it).length() > 0 && transfer(app, it) || transfer(app, it) })
                    State.Ready else State.Retry(Problem.Server)
            } catch (e: Failure) { State.Retry(e.problem) }
            catch (_: IOException) { State.Retry(if (app.noBackupFilesDir.usableSpace < 8_000_000) Problem.Space else Problem.Connection) }
            catch (_: Exception) { State.Retry(Problem.Server) }
            synchronized(this) {
                running = false
                if (result == State.Retry(Problem.Connection) && (started == 0 || stops != since)) {
                    waiting = true
                    if (started > 0) download(app)
                } else progress.value = result
            }
        }, "Hex model download").start()
    }

    /** Returns false, after deleting the partial file, when the finished file fails the checksum. */
    private fun transfer(app: Context, file: ModelFile): Boolean {
        val part = part(app, file)
        val size = file.size
        if (part.length() > size) part.delete()
        if (app.noBackupFilesDir.usableSpace <= this.size - have(app) + 8_000_000) throw Failure(Problem.Space)
        val digest = MessageDigest.getInstance("SHA-256")
        var total = part.length()
        if (total > 0) part.inputStream().use { input -> pump(input) { buffer, count -> digest.update(buffer, 0, count) } }
        var http = open(file.url, total)
        if (total > 0 && if (http == null) total < size else http.responseCode == 206 && !resumes(http, total, size)) {
            http?.disconnect(); part.delete(); total = 0; digest.reset()
            http = open(file.url, 0) ?: throw Failure(Problem.Server)
        }
        val before = have(app) - total
        if (http != null) try {
            if (http.responseCode == 200) { total = 0; digest.reset() }
            http.inputStream.use { input ->
                FileOutputStream(part, total > 0).use { output ->
                    pump(input) { buffer, count ->
                        total += count
                        if (total > size) throw Failure(Problem.Server)
                        digest.update(buffer, 0, count); output.write(buffer, 0, count)
                        progress.value = State.Loading(((before + total) * 100 / this.size).toInt().coerceAtMost(99))
                    }
                    output.fd.sync()
                }
            }
        } finally { http.disconnect() }
        if (total < size) throw IOException("Model download ended early")
        if (digest.digest().joinToString("") { "%02x".format(it) } != file.hash) { part.delete(); return false }
        Files.move(part.toPath(), path(app, file).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        return true
    }

    private inline fun pump(input: InputStream, each: (ByteArray, Int) -> Unit) {
        val buffer = ByteArray(128 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            each(buffer, count)
        }
    }

    private fun resumes(http: HttpsURLConnection, from: Long, size: Long) =
        http.getHeaderField("Content-Range") == "bytes $from-${size - 1}/$size"

    /** Opens the model at [from] bytes; null when the server says nothing is left to send. */
    private fun open(url: String, from: Long): HttpsURLConnection? {
        var target = URL(url)
        repeat(6) {
            val host = target.host.lowercase()
            if (target.protocol != "https" || target.userInfo != null || target.port !in listOf(-1, 443) ||
                !(host == "huggingface.co" || host.endsWith(".cdn.hf.co") || host == "cas-bridge.xethub.hf.co")) throw Failure(Problem.Server)
            val http = target.openConnection() as HttpsURLConnection
            try {
                http.instanceFollowRedirects = false
                http.connectTimeout = 15_000; http.readTimeout = 45_000
                // Android's default user agent includes the phone model and OS build.
                http.setRequestProperty("User-Agent", "HexModelDownloader/1")
                http.setRequestProperty("Accept-Encoding", "identity")
                if (from > 0) http.setRequestProperty("Range", "bytes=$from-")
                when (http.responseCode) {
                    200, 206 -> return http
                    416 -> { http.disconnect(); return null }
                    301, 302, 303, 307, 308 -> target = URL(target, http.getHeaderField("Location") ?: throw Failure(Problem.Server))
                    else -> throw Failure(Problem.Server)
                }
            } catch (e: Exception) { http.disconnect(); throw e }
            http.disconnect()
        }
        throw Failure(Problem.Server)
    }
}
