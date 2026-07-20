package com.osmankutlu.zh_en_dict

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Looks up dictionary entries for text the screen-lens OCR recognizes.
 * Chinese has no spaces between words, so given a whole OCR'd text line and
 * the character index the user actually dropped the lens on, [find] tries
 * progressively shorter substrings around that index — longest match first —
 * against the bundled word list, the same way a human reader would visually
 * group characters into the most specific known word.
 *
 * [findExtended] does the same search against a much larger bundled
 * CC-CEDICT-derived dataset (~120k entries, English meanings + pinyin, no
 * examples) for words our own curated ~8000-word list doesn't have —
 * notably including compound words our own list is missing entirely, like
 * 大学, which [find] alone can't recognize as a whole word even though its
 * search already prefers longer matches over shorter ones.
 */
object DictLookup {
    // Longest hanzi entry in our own word list is a handful of characters
    // (idioms like 众所周知); 8 covers everything with margin and keeps the
    // substring search cheap.
    private const val MAX_WORD_LEN = 8
    // CC-CEDICT has a long tail of longer idioms/phrases; entries above this
    // were dropped when the bundled dataset was built, so search need not
    // look further than this either.
    private const val MAX_WORD_LEN_EXTENDED = 10

    private var cachedByHanzi: Map<String, JSONObject>? = null
    private var cachedExtended: Map<String, JSONObject>? = null

    private fun readAsset(context: Context, path: String): String {
        val input = context.assets.open(path)
        val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
        val text = reader.readText()
        reader.close()
        return text
    }

    private fun byHanzi(context: Context): Map<String, JSONObject> {
        cachedByHanzi?.let { return it }
        val all = JSONArray(readAsset(context, "flutter_assets/assets/data/words.json"))
        val map = HashMap<String, JSONObject>(all.length() * 2)
        for (i in 0 until all.length()) {
            val o = all.getJSONObject(i)
            // First entry wins on duplicate hanzi (shouldn't happen — the
            // batch-append script already dedups — but stay defensive).
            val hanzi = o.optString("hanzi")
            if (hanzi.isNotEmpty() && !map.containsKey(hanzi)) map[hanzi] = o
        }
        cachedByHanzi = map
        return map
    }

    private fun extendedByHanzi(context: Context): Map<String, JSONObject> {
        cachedExtended?.let { return it }
        val all = JSONArray(readAsset(context, "flutter_assets/assets/data/cedict.json"))
        val map = HashMap<String, JSONObject>(all.length() * 2)
        for (i in 0 until all.length()) {
            val o = all.getJSONObject(i)
            val hanzi = o.optString("hanzi")
            if (hanzi.isNotEmpty() && !map.containsKey(hanzi)) map[hanzi] = o
        }
        cachedExtended = map
        return map
    }

    /**
     * Parses both bundled dictionaries up front. Call this once when the
     * screen lens opens (off the main thread) rather than letting the first
     * scan pay for parsing an ~11MB JSON file — [find]/[findExtended] are
     * cheap map lookups once cached, but that first parse is not.
     */
    fun preload(context: Context) {
        byHanzi(context)
        extendedByHanzi(context)
    }

    /**
     * Finds the entry for the word at [tapIndex] within [text] in our own
     * curated word list. Prefers the longest known word that contains
     * [tapIndex], trying every start position within [MAX_WORD_LEN]
     * characters before it (a drop can land anywhere inside a multi-character
     * word, not just its first character) and every length from longest to
     * shortest at each start. Falls back to the single character at
     * [tapIndex] if no multi-char entry matches, since many single hanzi are
     * valid entries on their own.
     */
    fun find(context: Context, text: String, tapIndex: Int): JSONObject? =
        search(text, tapIndex, byHanzi(context), MAX_WORD_LEN)

    /** Same search, against the much larger bundled CC-CEDICT dataset. */
    fun findExtended(context: Context, text: String, tapIndex: Int): JSONObject? =
        search(text, tapIndex, extendedByHanzi(context), MAX_WORD_LEN_EXTENDED)

    private fun search(
        text: String,
        tapIndex: Int,
        dict: Map<String, JSONObject>,
        maxWordLen: Int
    ): JSONObject? {
        if (text.isEmpty() || tapIndex < 0 || tapIndex >= text.length) return null

        var best: JSONObject? = null
        var bestLen = 0
        val earliestStart = maxOf(0, tapIndex - maxWordLen + 1)
        for (start in earliestStart..tapIndex) {
            val maxLen = minOf(maxWordLen, text.length - start)
            for (len in maxLen downTo 1) {
                val end = start + len
                if (tapIndex >= end) break // this length no longer covers tapIndex
                if (len <= bestLen) break // shorter than what we already have
                val candidate = text.substring(start, end)
                val entry = dict[candidate]
                if (entry != null) {
                    best = entry
                    bestLen = len
                    break
                }
            }
        }
        return best
    }
}
