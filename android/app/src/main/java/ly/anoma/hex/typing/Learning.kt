package ly.anoma.hex.typing

import java.io.File
import java.util.TreeMap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

internal class TypingHistory {
    private val words = TreeMap<String, Int>()
    private val pairs = HashMap<String, Int>()
    private val firsts = HashMap<String, Int>()
    var total = 0
        private set

    @Synchronized fun note(previous: String?, word: String) {
        if (word.isEmpty()) return
        words[word] = (words[word] ?: 0) + 1
        total++
        if (!previous.isNullOrEmpty()) {
            val key = "$previous $word"
            pairs[key] = (pairs[key] ?: 0) + 1
            firsts[previous] = (firsts[previous] ?: 0) + 1
        }
        trim()
    }

    private fun trim() {
        if (words.size > MAX_WORDS) {
            val rare = words.entries.sortedBy { it.value }.take(words.size - MAX_WORDS * 9 / 10).map { it.key }
            for (w in rare) { total -= words.remove(w) ?: 0 }
        }
        if (pairs.size > MAX_PAIRS) {
            val rare = pairs.entries.sortedBy { it.value }.take(pairs.size - MAX_PAIRS * 9 / 10).map { it.key }
            for (p in rare) {
                val c = pairs.remove(p) ?: 0
                val first = p.substringBefore(' ')
                val left = (firsts[first] ?: 0) - c
                if (left > 0) firsts[first] = left else firsts.remove(first)
            }
        }
    }

    fun weight(): Double = if (total == 0) 0.0 else MAX_WEIGHT * total / (total + RAMP)

    @Synchronized fun count(word: String) = words[word] ?: 0

    @Synchronized fun probability(previous: String?, word: String): Double {
        val c = words[word] ?: return 0.0
        val unigram = c.toDouble() / total
        if (previous.isNullOrEmpty()) return unigram
        val seen = firsts[previous] ?: return unigram
        val pair = pairs["$previous $word"] ?: 0
        return (pair + SMOOTHING * unigram) / (seen + SMOOTHING)
    }

    fun blend(base: Double, previous: String?, word: String): Double {
        val w = weight()
        if (w == 0.0) return base
        val p = probability(previous, word)
        if (p == 0.0) return base
        return -ln((1 - w) * exp(-base) + w * p)
    }

    @Synchronized fun completions(prefix: String, limit: Int): List<String> {
        if (prefix.isEmpty()) return emptyList()
        return words.subMap(prefix, prefix + Char.MAX_VALUE).entries.filter { it.key.length > prefix.length && it.value >= MIN_COUNT }
            .sortedByDescending { it.value }.take(limit).map { it.key }
    }

    @Synchronized fun next(previous: String, limit: Int): List<String> {
        val prefix = "$previous "
        return pairs.entries.filter { it.key.startsWith(prefix) && it.value >= MIN_COUNT }.sortedByDescending { it.value }
            .take(limit).map { it.key.substring(prefix.length) }
    }

    @Synchronized fun write(out: StringBuilder) {
        for ((w, c) in words) out.append("w\t").append(w).append('\t').append(c).append('\n')
        for ((p, c) in pairs) out.append("p\t").append(p).append('\t').append(c).append('\n')
    }

    @Synchronized fun read(kind: String, key: String, count: Int) {
        when (kind) {
            "w" -> { words[key] = count; total += count }
            "p" -> { pairs[key] = count; key.substringBefore(' ').let { firsts[it] = (firsts[it] ?: 0) + count } }
        }
    }

    @Synchronized fun clear() { words.clear(); pairs.clear(); firsts.clear(); total = 0 }

    companion object {
        const val MAX_WORDS = 20_000
        const val MAX_PAIRS = 40_000
        const val MAX_WEIGHT = 0.35
        const val RAMP = 400.0
        const val SMOOTHING = 2.0
        const val MIN_COUNT = 2
    }
}

internal class TouchModel {
    private class Offset(var dx: Double = 0.0, var dy: Double = 0.0, var n: Int = 0)
    private val keys = HashMap<Char, Offset>()

    @Synchronized fun note(c: Char, x: Double, y: Double, grid: KeyGrid) {
        val centre = grid.centres[c] ?: return
        val dx = (x - centre.x) / grid.pitchX
        val dy = (y - centre.y) / grid.pitchY
        if (abs(dx) > MAX_MISS || abs(dy) > MAX_MISS) return
        val o = keys.getOrPut(c) { Offset() }
        o.n++
        val rate = maxOf(RATE, 1.0 / o.n)
        o.dx += rate * (dx - o.dx)
        o.dy += rate * (dy - o.dy)
    }

    @Synchronized fun shifted(c: Char, centre: KeyPoint, grid: KeyGrid): KeyPoint {
        val o = keys[c] ?: return centre
        val trust = min(1.0, o.n / TRUSTED)
        val dx = (o.dx * trust).coerceIn(-MAX_SHIFT, MAX_SHIFT)
        val dy = (o.dy * trust).coerceIn(-MAX_SHIFT, MAX_SHIFT)
        return KeyPoint(centre.x + dx * grid.pitchX, centre.y + dy * grid.pitchY)
    }

    @Synchronized fun samples() = keys.values.sumOf { it.n }

    fun key(hit: Char, x: Double, y: Double, grid: KeyGrid): Char {
        fun distance(c: Char): Double {
            val p = shifted(c, grid.centres[c] ?: return Double.MAX_VALUE, grid)
            val dx = (x - p.x) / grid.pitchX; val dy = (y - p.y) / grid.pitchY
            return dx * dx + dy * dy
        }
        val own = distance(hit)
        var best = hit
        var bestDistance = own
        for (c in grid.centres.keys) {
            if (c == hit || !trusted(c)) continue
            val d = distance(c)
            if (d < bestDistance) { best = c; bestDistance = d }
        }
        return if (best != hit && own - bestDistance > MARGIN) best else hit
    }

    @Synchronized private fun trusted(c: Char) = (keys[c]?.n ?: 0) >= TRUSTED

    @Synchronized fun write(out: StringBuilder) {
        for ((c, o) in keys) out.append("t\t").append(c).append('\t').append(o.dx).append(',').append(o.dy).append(',').append(o.n).append('\n')
    }

    @Synchronized fun read(key: String, value: String) {
        val parts = value.split(',')
        if (key.length != 1 || parts.size != 3) return
        keys[key[0]] = Offset(parts[0].toDoubleOrNull() ?: return, parts[1].toDoubleOrNull() ?: return, parts[2].toIntOrNull() ?: return)
    }

    @Synchronized fun clear() = keys.clear()

    companion object {
        const val MAX_MISS = .6
        const val MAX_SHIFT = .35
        const val RATE = .02
        const val TRUSTED = 30.0
        const val MARGIN = .15
    }
}

internal class KeptCorrections {
    private val kept = HashMap<String, Pair<String, Int>>()

    @Synchronized fun keep(original: String, replacement: String) {
        val key = original.lowercase()
        val now = kept[key]
        kept[key] = replacement to (if (now?.first.equals(replacement, ignoreCase = true)) now!!.second + 1 else 1)
        if (kept.size > MAX) kept.remove(kept.keys.first())
    }

    @Synchronized fun undone(original: String, replacement: String) {
        val key = original.lowercase()
        if (kept[key]?.first.equals(replacement, ignoreCase = true)) kept.remove(key)
    }

    @Synchronized fun times(original: String, replacement: String): Int =
        kept[original.lowercase()]?.takeIf { it.first.equals(replacement, ignoreCase = true) }?.second ?: 0

    @Synchronized fun learned(original: String): String? = kept[original.lowercase()]?.takeIf { it.second >= TIMES }?.first

    @Synchronized fun write(out: StringBuilder) {
        for ((k, v) in kept) out.append("c\t").append(k).append('\t').append(v.first).append('\u0001').append(v.second).append('\n')
    }

    @Synchronized fun read(key: String, value: String) {
        val replacement = value.substringBefore('\u0001')
        kept[key] = replacement to (value.substringAfter('\u0001').toIntOrNull() ?: return)
    }

    @Synchronized fun clear() = kept.clear()

    companion object {
        const val TIMES = 2
        const val MAX = 2000
    }
}

internal class Learning(private val file: File?) {
    val history = TypingHistory()
    val touch = TouchModel()
    val corrections = KeptCorrections()
    @Volatile var enabled = true
    private var changes = 0

    init {
        try {
            file?.takeIf { it.isFile }?.forEachLine { line ->
                val parts = line.split('\t')
                if (parts.size != 3) return@forEachLine
                when (parts[0]) {
                    "w", "p" -> parts[2].toIntOrNull()?.let { history.read(parts[0], parts[1], it) }
                    "t" -> touch.read(parts[1], parts[2])
                    "c" -> corrections.read(parts[1], parts[2])
                }
            }
        } catch (_: java.io.IOException) {
        }
    }

    fun changed() { changes++ }

    fun saveIfChanged() {
        val target = file ?: return
        if (changes == 0) return
        changes = 0
        val out = StringBuilder()
        history.write(out); touch.write(out); corrections.write(out)
        try {
            val part = File(target.parentFile, target.name + ".part")
            part.writeText(out.toString())
            part.renameTo(target)
        } catch (_: java.io.IOException) {
        }
    }

    fun clear() {
        history.clear(); touch.clear(); corrections.clear()
        changes = 0
        file?.delete()
    }
}
