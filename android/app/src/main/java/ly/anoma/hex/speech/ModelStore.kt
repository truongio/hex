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

/** Hex's network operation: an explicit, fixed model GET from Hex's own screens, each file pinned by revision, size and
 * SHA-256 ([small] from setup or Dictation settings, [english] from Labs, [multilingual] from Dictation settings). The
 * keyboard never calls it. A model of several files keeps them in its own [folder]. */
internal class ModelStore private constructor(private val name: String, private val folder: String, private val files: List<ModelFile>) {
    companion object {
        private fun single(name: String, size: Long, hash: String, url: String, extension: String) =
            ModelStore(name, "", listOf(ModelFile("$name-$hash.$extension", url, size, hash)))
        /** Whisper small, the default speech model. */
        val small = single("whisper-small", 190085487L, "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb",
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-small-q5_1.bin", "bin")
        /** Labs › Faster English dictation: NVIDIA's Parakeet TDT-CTC 110M, as transcribe.cpp's authors converted it. */
        val english = single("parakeet-tdt_ctc-110m", 135373280L, "7dd44c74a331d788a4e5f8b16913b3feb29ced22cf5613aad0e0f6cd30516296",
            "https://huggingface.co/handy-computer/parakeet-tdt_ctc-110m-gguf/resolve/1c3f0d5378fdd358905cc410a93ed9e64f52d170/parakeet-tdt_ctc-110m-Q8_0.gguf",
            "gguf")
        private const val multilingualRepo = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/resolve/ed5d745623472f97cfc2e6c2916ee248798fc5a8"
        /** Dictation › Parakeet: NVIDIA's Parakeet TDT 0.6B v3, 25 European languages, for sherpa-onnx. */
        val multilingual = ModelStore("parakeet-tdt-0.6b-v3", "parakeet-tdt-0.6b-v3-int8-ed5d745", listOf(
            ModelFile("tokens.txt", "$multilingualRepo/tokens.txt", 93939L, "d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d"),
            ModelFile("decoder.int8.onnx", "$multilingualRepo/decoder.int8.onnx", 11845274L, "0222a86147299d4dee90edf95b6d13827edb42ad8b7c9cf0b2582f030937c0b5"),
            ModelFile("joiner.int8.onnx", "$multilingualRepo/joiner.int8.onnx", 6355277L, "cbd02f3696e9944970111fb439d9c3fd365a80374565f13f2b9c7007d253fc98"),
            ModelFile("encoder.int8.onnx", "$multilingualRepo/encoder.int8.onnx", 652282294L, "1afbe69c1ffdabee94755f42e4daf1d93549131a3da11356bda84fcef0f2c479")))
    }

    enum class Problem { Connection, Server, Space }
    sealed interface State {
        data object Available : State
        data object Ready : State
        data class Retry(val problem: Problem) : State
        data class Loading(val percent: Int) : State
    }
    private class Failure(val problem: Problem) : Exception()
    private class Deleted : Exception()
    private val progress = MutableStateFlow<State>(State.Available)
    val state = progress.asStateFlow()
    private var running = false
    private var waiting = false
    private var watching = false
    private var started = 0
    private var stops = 0
    /** Bumped by [delete], so a transfer in progress stops and leaves nothing behind. */
    @Volatile private var generation = 0
    private val size = files.sumOf { it.size }
    /** The installed file's name, after its SHA-256 (a single-file model's). */
    internal val fileName get() = files[0].name
    private fun dir(base: File) = if (folder.isEmpty()) base else File(base, folder)
    private fun path(base: File, file: ModelFile) = File(dir(base), file.name)
    private fun part(base: File, file: ModelFile) = File(dir(base), file.name.substringBeforeLast('.') + ".part")
    private fun done(base: File, file: ModelFile) = path(base, file).let { it.isFile && it.length() == file.size }
    private fun partial(context: Context) = files.any { part(context.noBackupFilesDir, it).exists() }
    private fun have(base: File) = files.sumOf { if (done(base, it)) it.size else part(base, it).length().coerceIn(0, it.size) }
    fun installed(context: Context) = installedIn(context.noBackupFilesDir)
    /** The model's file, or its folder when it has several, once every file is in place. */
    internal fun installedIn(base: File): File? =
        if (files.all { done(base, it) }) (if (files.size == 1) path(base, files[0]) else dir(base)) else null
    /** The download's size, said before it starts. */
    val megabytes get() = ((size + 500_000) / 1_000_000).toInt()
    /** The file checked against its SHA-256 in this process, so it is hashed once, not at every load. */
    @Volatile private var checked: File? = null

    /** [installed], once its contents match the pinned SHA-256 (hashed the first time in each process, on the caller's
     * thread). A file that doesn't is deleted, so its row offers the download again. */
    fun verified(context: Context) = verifiedIn(context.noBackupFilesDir)
    internal fun verifiedIn(base: File): File? {
        val installed = installedIn(base) ?: return null
        if (checked == installed) return installed
        for (file in files) {
            val digest = MessageDigest.getInstance("SHA-256")
            val ok = runCatching { path(base, file).inputStream().use { input -> pump(input) { buffer, count -> digest.update(buffer, 0, count) } } }.isSuccess &&
                digest.digest().joinToString("") { "%02x".format(it) } == file.hash
            if (!ok) {
                synchronized(this) { path(base, file).delete(); if (!running) progress.value = State.Available }
                return null
            }
        }
        checked = installed
        return installed
    }

    /** Removes the file and any partial download, stopping a transfer in progress; [state] is Available again. */
    @Synchronized fun delete(context: Context) {
        val app = context.applicationContext
        generation++
        waiting = false
        checked = null
        for (file in files) { path(app.noBackupFilesDir, file).delete(); part(app.noBackupFilesDir, file).delete() }
        progress.value = State.Available
    }

    @Synchronized fun refresh(context: Context) {
        val app = context.applicationContext as Application
        if (!watching) { watching = true; app.registerActivityLifecycleCallbacks(foreground) }
        if (installed(app) != null) progress.value = State.Ready
        else if (progress.value == State.Ready) progress.value = State.Available
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
        val base = app.noBackupFilesDir
        progress.value = State.Loading((have(base) * 100 / size).toInt().coerceAtMost(99))
        val since = stops
        val mine = generation
        Thread({
            val result = try {
                dir(base).mkdirs()
                // A resumed file that fails the checksum is discarded and fetched once more from the start.
                if (files.all { done(base, it) || part(base, it).length() > 0 && transfer(app, it, mine) || transfer(app, it, mine) })
                    State.Ready else State.Retry(Problem.Server)
            } catch (_: Deleted) { null }
            catch (e: Failure) { State.Retry(e.problem) }
            catch (_: IOException) { State.Retry(if (app.noBackupFilesDir.usableSpace < 8_000_000) Problem.Space else Problem.Connection) }
            catch (_: Exception) { State.Retry(Problem.Server) }
            synchronized(this) {
                running = false
                if (result == null || generation != mine) {
                    // Deleted while it ran: whatever the transfer wrote last goes too.
                    for (file in files) { part(base, file).delete(); path(base, file).delete() }
                    progress.value = State.Available
                } else if (result == State.Retry(Problem.Connection) && (started == 0 || stops != since)) {
                    waiting = true
                    if (started > 0) download(app)
                } else progress.value = result
            }
        }, "Hex $name download").start()
    }

    /** Returns false, after deleting the partial file, when the finished file fails the checksum. */
    private fun transfer(app: Context, file: ModelFile, mine: Int): Boolean {
        val base = app.noBackupFilesDir
        val part = part(base, file)
        val size = file.size
        val url = file.url
        if (part.length() > size) part.delete()
        if (base.usableSpace <= this.size - have(base) + 8_000_000) throw Failure(Problem.Space)
        val digest = MessageDigest.getInstance("SHA-256")
        var total = part.length()
        if (total > 0) part.inputStream().use { input -> pump(input) { buffer, count -> digest.update(buffer, 0, count) } }
        var http = open(url, total)
        if (total > 0 && if (http == null) total < size else http.responseCode == 206 && !resumes(http, total, size)) {
            http?.disconnect(); part.delete(); total = 0; digest.reset()
            http = open(url, 0) ?: throw Failure(Problem.Server)
        }
        val before = have(base) - total
        if (http != null) try {
            if (http.responseCode == 200) { total = 0; digest.reset() }
            http.inputStream.use { input ->
                FileOutputStream(part, total > 0).use { output ->
                    pump(input) { buffer, count ->
                        if (generation != mine) throw Deleted()
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
        synchronized(this) {
            if (generation != mine) throw Deleted()
            Files.move(part.toPath(), path(base, file).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }
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
