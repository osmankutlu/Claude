package com.osmankutlu.zh_en_dict

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.random.Random

/**
 * Reads/writes the SharedPreferences file the `home_widget` Flutter plugin
 * uses ("HomeWidgetPreferences"), so widget data set here is visible to
 * Flutter via HomeWidget.getWidgetData, and data Flutter saves via
 * HomeWidget.saveWidgetData is visible here.
 */
object WidgetDataStore {
    private const val PREFS_NAME = "HomeWidgetPreferences"

    private const val KEY_FAV_WORDS = "favorite_word_ids"
    private const val KEY_FAV_GRAMMAR = "favorite_grammar_ids"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun modeKey(appWidgetId: Int) = "widget_mode_$appWidgetId"
    fun levelKey(appWidgetId: Int) = "widget_level_$appWidgetId"
    fun itemKey(appWidgetId: Int) = "widget_item_$appWidgetId"

    private fun readAsset(context: Context, path: String): String {
        val input = context.assets.open(path)
        val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
        val text = reader.readText()
        reader.close()
        return text
    }

    private fun loadWords(context: Context): JSONArray =
        JSONArray(readAsset(context, "flutter_assets/assets/data/words.json"))

    private fun loadGrammar(context: Context): JSONArray =
        JSONArray(readAsset(context, "flutter_assets/assets/data/grammar.json"))

    private fun idSet(context: Context, key: String): MutableList<String> {
        val raw = prefs(context).getString(key, null) ?: return mutableListOf()
        val arr = JSONArray(raw)
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) list.add(arr.getString(i))
        return list
    }

    fun isFavoriteWord(context: Context, id: String): Boolean =
        idSet(context, KEY_FAV_WORDS).contains(id)

    fun isFavoriteGrammar(context: Context, id: String): Boolean =
        idSet(context, KEY_FAV_GRAMMAR).contains(id)

    /** Toggles [id] in the favorites list for the given [mode] ("word" or "grammar"). */
    fun toggleFavorite(context: Context, mode: String, id: String) {
        val key = if (mode == "grammar") KEY_FAV_GRAMMAR else KEY_FAV_WORDS
        val current = idSet(context, key)
        if (!current.remove(id)) current.add(id)
        prefs(context).edit().putString(key, JSONArray(current).toString()).apply()
    }

    private fun filteredGrammar(context: Context, level: String): List<JSONObject> {
        val all = loadGrammar(context)
        val filtered = mutableListOf<JSONObject>()
        for (i in 0 until all.length()) {
            val o = all.getJSONObject(i)
            if (level == "0" || o.getInt("level").toString() == level) filtered.add(o)
        }
        return filtered
    }

    private fun filteredWords(context: Context, level: String): List<JSONObject> {
        val all = loadWords(context)
        val filtered = mutableListOf<JSONObject>()
        for (i in 0 until all.length()) {
            val o = all.getJSONObject(i)
            if (level == "0" || o.getInt("level").toString() == level) filtered.add(o)
        }
        return filtered
    }

    private fun buildGrammarItem(picked: JSONObject): JSONObject {
        val item = JSONObject()
        item.put("mode", "grammar")
        item.put("itemId", picked.getString("id"))
        item.put("title", picked.getString("title"))
        item.put("summary", picked.getString("summary"))
        item.put("level", picked.getInt("level"))
        return item
    }

    /**
     * Builds the stored widget item for a word, embedding every example
     * sentence for that word (not just the meaning) so the widget can cycle
     * through the full list without re-reading the bundled asset.
     */
    private fun buildWordItem(picked: JSONObject): JSONObject {
        val item = JSONObject()
        item.put("mode", "word")
        item.put("itemId", picked.getString("id"))
        item.put("hanzi", picked.getString("hanzi"))
        item.put("pinyin", picked.getString("pinyin"))
        item.put("meaning", picked.getString("meaning"))
        item.put("level", picked.getInt("level"))
        item.put("examples", picked.optJSONArray("examples") ?: JSONArray())
        item.put("exampleIndex", 0)
        return item
    }

    /**
     * Picks a new random word or grammar topic (respecting the widget's
     * configured mode/level) and stores it under this widget's item key.
     */
    fun pickNewRandomItem(context: Context, appWidgetId: Int) {
        val prefs = prefs(context)
        val mode = prefs.getString(modeKey(appWidgetId), "word") ?: "word"
        val level = prefs.getString(levelKey(appWidgetId), "0") ?: "0"

        val item: JSONObject
        if (mode == "grammar") {
            val filtered = filteredGrammar(context, level)
            if (filtered.isEmpty()) return
            item = buildGrammarItem(filtered[Random.nextInt(filtered.size)])
        } else {
            val filtered = filteredWords(context, level)
            if (filtered.isEmpty()) return
            item = buildWordItem(filtered[Random.nextInt(filtered.size)])
        }

        prefs.edit().putString(itemKey(appWidgetId), item.toString()).apply()
    }

    /**
     * Manually steps to the next/previous word or grammar topic (wrapping
     * around), independent of the automatic unlock-triggered refresh.
     * [direction] should be +1 (next) or -1 (previous).
     */
    fun navigateItem(context: Context, appWidgetId: Int, direction: Int) {
        val prefs = prefs(context)
        val raw = prefs.getString(itemKey(appWidgetId), null) ?: return
        val current = JSONObject(raw)
        val mode = current.optString("mode", "word")
        val level = prefs.getString(levelKey(appWidgetId), "0") ?: "0"
        val currentId = current.optString("itemId")

        val item: JSONObject
        if (mode == "grammar") {
            val filtered = filteredGrammar(context, level)
            if (filtered.isEmpty()) return
            val curIndex = filtered.indexOfFirst { it.getString("id") == currentId }
            val newIndex = ((if (curIndex < 0) 0 else curIndex) + direction + filtered.size) % filtered.size
            item = buildGrammarItem(filtered[newIndex])
        } else {
            val filtered = filteredWords(context, level)
            if (filtered.isEmpty()) return
            val curIndex = filtered.indexOfFirst { it.getString("id") == currentId }
            val newIndex = ((if (curIndex < 0) 0 else curIndex) + direction + filtered.size) % filtered.size
            item = buildWordItem(filtered[newIndex])
        }

        prefs.edit().putString(itemKey(appWidgetId), item.toString()).apply()
    }

    /**
     * Cycles the displayed example sentence (wrapping around) for the word
     * currently shown by this widget. No-op in grammar mode or if the word
     * has no examples.
     */
    fun navigateExample(context: Context, appWidgetId: Int, direction: Int) {
        val prefs = prefs(context)
        val raw = prefs.getString(itemKey(appWidgetId), null) ?: return
        val item = JSONObject(raw)
        if (item.optString("mode", "word") != "word") return
        val examples = item.optJSONArray("examples") ?: return
        if (examples.length() == 0) return

        val curIndex = item.optInt("exampleIndex", 0)
        val newIndex = (curIndex + direction + examples.length()) % examples.length()
        item.put("exampleIndex", newIndex)
        prefs.edit().putString(itemKey(appWidgetId), item.toString()).apply()
    }
}
