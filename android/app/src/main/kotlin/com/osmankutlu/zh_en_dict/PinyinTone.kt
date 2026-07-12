package com.osmankutlu.zh_en_dict

import android.os.Build
import android.text.Html
import android.text.Spanned

/**
 * Tone-colors Chinese text for the home-screen widget, mirroring the in-app
 * Dart `PinyinTone` palette and syllable segmentation so the widget matches
 * the flashcards. Word pinyin in the bundled data is written joined
 * (e.g. "tóngshì"), so we segment it into syllables via a standard
 * initials/finals table and read the tone off whichever vowel carries a
 * diacritic.
 *
 * Output is HTML (`<font color=...>`) fed through [fromHtml] into a
 * RemoteViews TextView. If text and pinyin can't be aligned we return null so
 * the caller falls back to plain (uncolored) text rather than risk wrong
 * colors.
 */
object PinyinTone {

    // Same hex values as lib/utils/pinyin_tone.dart.
    private val toneColor = mapOf(
        1 to "#1E88E5", // blue
        2 to "#43A047", // green
        3 to "#F9A825", // yellow
        4 to "#E53935", // red
        5 to "#9E9E9E", // grey - neutral
    )

    private val toneOfCharLower = mapOf(
        'ā' to 1, 'á' to 2, 'ǎ' to 3, 'à' to 4,
        'ē' to 1, 'é' to 2, 'ě' to 3, 'è' to 4,
        'ī' to 1, 'í' to 2, 'ǐ' to 3, 'ì' to 4,
        'ō' to 1, 'ó' to 2, 'ǒ' to 3, 'ò' to 4,
        'ū' to 1, 'ú' to 2, 'ǔ' to 3, 'ù' to 4,
        'ǖ' to 1, 'ǘ' to 2, 'ǚ' to 3, 'ǜ' to 4,
    )

    private val toneOfChar: Map<Char, Int> =
        toneOfCharLower + toneOfCharLower.mapKeys { it.key.uppercaseChar() }

    private val plainOfCharLower = mapOf(
        'ā' to 'a', 'á' to 'a', 'ǎ' to 'a', 'à' to 'a',
        'ē' to 'e', 'é' to 'e', 'ě' to 'e', 'è' to 'e',
        'ī' to 'i', 'í' to 'i', 'ǐ' to 'i', 'ì' to 'i',
        'ō' to 'o', 'ó' to 'o', 'ǒ' to 'o', 'ò' to 'o',
        'ū' to 'u', 'ú' to 'u', 'ǔ' to 'u', 'ù' to 'u',
        'ǖ' to 'v', 'ǘ' to 'v', 'ǚ' to 'v', 'ǜ' to 'v',
        'ü' to 'v', 'Ü' to 'v',
    )

    private val plainOfChar: Map<Char, Char> =
        plainOfCharLower + plainOfCharLower.mapKeys { it.key.uppercaseChar() }

    private val initials = listOf(
        "zh", "ch", "sh",
        "b", "p", "m", "f", "d", "t", "n", "l",
        "g", "k", "h", "j", "q", "x", "r", "z", "c", "s", "y", "w",
    ).sortedByDescending { it.length }

    private val finals = listOf(
        "iang", "iong", "uang", "ueng",
        "ian", "iao", "ing", "ong", "uai", "uan", "van",
        "ai", "ei", "ao", "ou", "an", "en", "ang", "eng", "er", "ue",
        "ia", "ie", "iu", "in", "ua", "uo", "ui", "un", "ve",
        "a", "o", "e", "i", "u", "v",
    ).sortedByDescending { it.length }

    private fun isPinyinLetter(c: Char): Boolean {
        val ascii = (c in 'a'..'z') || (c in 'A'..'Z')
        return ascii || toneOfChar.containsKey(c) || plainOfChar.containsKey(c)
    }

    private fun plain(c: Char): Char = plainOfChar[c] ?: c.lowercaseChar()

    private fun stripTones(s: String): String = s.map { plain(it) }.joinToString("")

    private fun toneOf(syllable: String): Int {
        for (c in syllable) toneOfChar[c]?.let { return it }
        return 5
    }

    /** Splits one run of pinyin letters into (syllableText, tone) pairs. */
    private fun splitRun(run: String): List<Pair<String, Int>> {
        val plain = run.map { plain(it) }.joinToString("")
        val result = mutableListOf<Pair<String, Int>>()
        var pos = 0
        while (pos < run.length) {
            var matchedInitial = ""
            for (init in initials) {
                if (plain.startsWith(init, pos)) { matchedInitial = init; break }
            }
            val afterInitial = pos + matchedInitial.length
            var matchedFinal = ""
            for (fin in finals) {
                if (plain.startsWith(fin, afterInitial)) { matchedFinal = fin; break }
            }
            if (matchedFinal.isEmpty()) {
                val chunk = run.substring(pos, pos + 1)
                // A lone trailing "r" is the 儿化 (erhua) tail of the previous
                // syllable, not a syllable of its own.
                if ((chunk == "r" || chunk == "R") && result.isNotEmpty()) {
                    val last = result.removeAt(result.size - 1)
                    result.add(Pair(last.first + chunk, last.second))
                } else {
                    result.add(Pair(chunk, toneOf(chunk)))
                }
                pos += 1
                continue
            }
            val end = afterInitial + matchedFinal.length
            val syllable = run.substring(pos, end)
            result.add(Pair(syllable, toneOf(syllable)))
            pos = end
        }
        return result
    }

    /** Flat, in-order list of every syllable as (text, tone) pairs. */
    private fun syllables(pinyin: String): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        var i = 0
        while (i < pinyin.length) {
            if (isPinyinLetter(pinyin[i])) {
                var j = i + 1
                while (j < pinyin.length && isPinyinLetter(pinyin[j])) j++
                result.addAll(splitRun(pinyin.substring(i, j)))
                i = j
            } else {
                i++
            }
        }
        return result
    }

    private fun isCjk(c: Char): Boolean {
        val code = c.code
        return (code in 0x4E00..0x9FFF) || (code in 0x3400..0x4DBF)
    }

    private fun esc(c: Char): String = when (c) {
        '&' -> "&amp;"
        '<' -> "&lt;"
        '>' -> "&gt;"
        else -> c.toString()
    }

    private fun span(color: String?, body: String): String =
        if (color == null) body else "<font color=\"$color\">$body</font>"

    /**
     * HTML for [hanzi] with each CJK character colored by the tone of its
     * aligned pinyin syllable. Returns null if there's no pinyin or the
     * syllable count doesn't line up with the characters.
     */
    fun hanziHtml(hanzi: String, pinyin: String?): String? {
        if (pinyin.isNullOrEmpty()) return null
        val syl = syllables(pinyin)
        if (syl.isEmpty()) return null
        val sb = StringBuilder()
        var s = 0
        var lastWasErhua = false
        var lastColor: String? = null
        for (c in hanzi) {
            if (!isCjk(c)) { sb.append(esc(c)); continue }
            if (c == '儿' && lastWasErhua) {
                sb.append(span(lastColor, esc(c)))
                lastWasErhua = false
                continue
            }
            if (s >= syl.size) return null // misaligned — don't risk wrong colors
            val (text, tone) = syl[s]
            val color = toneColor[tone]
            sb.append(span(color, esc(c)))
            lastColor = color
            val plainSyl = stripTones(text).lowercase()
            lastWasErhua = plainSyl.length > 1 && plainSyl.endsWith("r") && plainSyl != "er"
            s++
        }
        return sb.toString()
    }

    /** HTML for [pinyin] with each syllable colored by its tone. */
    fun pinyinHtml(pinyin: String?): String? {
        if (pinyin.isNullOrEmpty()) return null
        val sb = StringBuilder()
        var i = 0
        while (i < pinyin.length) {
            val c = pinyin[i]
            if (isPinyinLetter(c)) {
                var j = i + 1
                while (j < pinyin.length && isPinyinLetter(pinyin[j])) j++
                for ((syllable, tone) in splitRun(pinyin.substring(i, j))) {
                    sb.append(span(toneColor[tone], syllable.map { esc(it) }.joinToString("")))
                }
                i = j
            } else {
                sb.append(esc(c))
                i++
            }
        }
        return sb.toString()
    }

    @Suppress("DEPRECATION")
    fun fromHtml(html: String): Spanned =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        else
            Html.fromHtml(html)
}
