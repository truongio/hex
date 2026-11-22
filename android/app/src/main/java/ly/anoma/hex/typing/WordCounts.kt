package ly.anoma.hex.typing

internal object WordCounts {
    const val TIMES = 2
    const val KEPT = 2000
    private const val TRACKED = 4000

    fun parse(text: String?): LinkedHashMap<String, Int> {
        val out = LinkedHashMap<String, Int>()
        for (line in text.orEmpty().split('\n')) {
            val tab = line.indexOf('\t')
            if (tab > 0) out[line.substring(0, tab)] = line.substring(tab + 1).toIntOrNull() ?: continue
        }
        return out
    }

    fun format(counts: Map<String, Int>) = counts.entries.joinToString("\n") { "${it.key}\t${it.value}" }

    fun note(counts: LinkedHashMap<String, Int>, word: String): Pair<LinkedHashMap<String, Int>, Boolean> {
        val key = counts.keys.firstOrNull { it.equals(word, ignoreCase = true) } ?: word
        val count = (counts.remove(key) ?: 0) + 1
        counts[word] = count
        while (counts.size > TRACKED) counts.remove(counts.keys.first())
        return counts to (count == TIMES)
    }
}
