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
 * uses ("HomeWidgetPreferences"), so widget config Flutter saves via
 * HomeWidget.saveWidgetData is visible here. The widget shows one word or
 * grammar topic at a time (stored under [itemKey]); [stepItem]/[pickRandomItem]
 * change which one.
 */
object WidgetDataStore {
    private const val PREFS_NAME = "HomeWidgetPreferences"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun modeKey(appWidgetId: Int) = "widget_mode_$appWidgetId"
    fun levelKey(appWidgetId: Int) = "widget_level_$appWidgetId"
    fun displayKey(appWidgetId: Int) = "widget_display_$appWidgetId"
    fun itemKey(appWidgetId: Int) = "widget_item_$appWidgetId"
    // How often (in screen unlocks) the word changes, and the running count.
    fun unlockEveryKey(appWidgetId: Int) = "widget_unlockevery_$appWidgetId"
    fun unlockCountKey(appWidgetId: Int) = "widget_unlockcount_$appWidgetId"

    // Favorite id lists — same keys the Flutter FavoritesRepository writes, so
    // starring from the widget and from inside the app stay in sync.
    private const val FAV_WORDS = "favorite_word_ids"
    private const val FAV_GRAMMAR = "favorite_grammar_ids"

    private fun favKey(mode: String) = if (mode == "grammar") FAV_GRAMMAR else FAV_WORDS

    /** The item this widget currently shows, or null if none is stored. */
    fun currentItem(context: Context, appWidgetId: Int): JSONObject? {
        val raw = prefs(context).getString(itemKey(appWidgetId), null) ?: return null
        return try { JSONObject(raw) } catch (e: Exception) { null }
    }

    fun isFavorite(context: Context, mode: String, itemId: String): Boolean {
        val raw = prefs(context).getString(favKey(mode), null) ?: return false
        val arr = try { JSONArray(raw) } catch (e: Exception) { return false }
        for (i in 0 until arr.length()) if (arr.getString(i) == itemId) return true
        return false
    }

    /** Adds/removes [itemId] from the favorites list; returns the new state. */
    fun toggleFavorite(context: Context, mode: String, itemId: String): Boolean {
        val prefs = prefs(context)
        val key = favKey(mode)
        val arr = try {
            prefs.getString(key, null)?.let { JSONArray(it) } ?: JSONArray()
        } catch (e: Exception) { JSONArray() }
        val ids = mutableListOf<String>()
        for (i in 0 until arr.length()) ids.add(arr.getString(i))
        val nowFavorite: Boolean
        if (ids.contains(itemId)) {
            ids.remove(itemId)
            nowFavorite = false
        } else {
            ids.add(itemId)
            nowFavorite = true
        }
        prefs.edit().putString(key, JSONArray(ids).toString()).apply()
        return nowFavorite
    }

    /**
     * Called on each screen unlock. Returns true (and resets the counter) when
     * this widget is due to change its word, honoring the user's configured
     * frequency. "0" means never auto-change (manual only).
     */
    fun shouldChangeOnUnlock(context: Context, appWidgetId: Int): Boolean {
        val prefs = prefs(context)
        val every = (prefs.getString(unlockEveryKey(appWidgetId), "1") ?: "1").toIntOrNull() ?: 1
        if (every <= 0) return false
        val count = prefs.getInt(unlockCountKey(appWidgetId), 0) + 1
        return if (count >= every) {
            prefs.edit().putInt(unlockCountKey(appWidgetId), 0).apply()
            true
        } else {
            prefs.edit().putInt(unlockCountKey(appWidgetId), count).apply()
            false
        }
    }

    /**
     * [levelSpec] is either "0" (all levels) or a comma-separated list of
     * HSK levels to mix together, e.g. "1,3,5".
     */
    private fun levelMatches(levelSpec: String, level: Int): Boolean {
        if (levelSpec.isEmpty() || levelSpec == "0") return true
        return levelSpec.split(",").any { it.trim() == level.toString() }
    }

    // In-memory only (not persisted): a widget update, a nav-button tap and
    // a screen unlock can all trigger several of these calls back-to-back in
    // the same process, and re-parsing the ~2.5k-entry words.json from the
    // bundled assets every time is wasted work a simple per-process cache
    // avoids. Safe to keep for the process's lifetime since the bundled
    // asset only changes on an app update (which restarts the process).
    private var cachedWordsJson: JSONArray? = null
    private var cachedGrammarJson: JSONArray? = null

    private fun readAsset(context: Context, path: String): String {
        val input = context.assets.open(path)
        val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
        val text = reader.readText()
        reader.close()
        return text
    }

    private fun loadWords(context: Context): JSONArray {
        cachedWordsJson?.let { return it }
        val loaded = JSONArray(readAsset(context, "flutter_assets/assets/data/words.json"))
        cachedWordsJson = loaded
        return loaded
    }

    private fun loadGrammar(context: Context): JSONArray {
        cachedGrammarJson?.let { return it }
        val loaded = JSONArray(readAsset(context, "flutter_assets/assets/data/grammar.json"))
        cachedGrammarJson = loaded
        return loaded
    }

    private fun filteredGrammar(context: Context, levelSpec: String): List<JSONObject> {
        val all = loadGrammar(context)
        val filtered = mutableListOf<JSONObject>()
        for (i in 0 until all.length()) {
            val o = all.getJSONObject(i)
            if (levelMatches(levelSpec, o.getInt("level"))) filtered.add(o)
        }
        return filtered
    }

    private fun filteredWords(context: Context, levelSpec: String): List<JSONObject> {
        val all = loadWords(context)
        val filtered = mutableListOf<JSONObject>()
        for (i in 0 until all.length()) {
            val o = all.getJSONObject(i)
            if (levelMatches(levelSpec, o.getInt("level"))) filtered.add(o)
        }
        return filtered
    }

    private fun buildItem(mode: String, picked: JSONObject): JSONObject {
        val item = JSONObject()
        item.put("mode", mode)
        item.put("itemId", picked.getString("id"))
        item.put("level", picked.getInt("level"))
        if (mode == "grammar") {
            item.put("title", picked.getString("title"))
        } else {
            item.put("hanzi", picked.getString("hanzi"))
            item.put("pinyin", picked.getString("pinyin"))
        }
        return item
    }

    private fun filtered(context: Context, mode: String, levelSpec: String): List<JSONObject> =
        if (mode == "grammar") filteredGrammar(context, levelSpec) else filteredWords(context, levelSpec)

    /**
     * Picks a new random word/grammar item (respecting the widget's
     * configured mode+level) and stores it as this widget's current item.
     * Returns false (and stores nothing) if that mode+level combination has
     * no matches.
     */
    fun pickRandomItem(context: Context, appWidgetId: Int): Boolean {
        val prefs = prefs(context)
        val mode = prefs.getString(modeKey(appWidgetId), "word") ?: "word"
        val level = prefs.getString(levelKey(appWidgetId), "0") ?: "0"
        val list = filtered(context, mode, level)
        if (list.isEmpty()) return false
        val item = buildItem(mode, list[Random.nextInt(list.size)])
        prefs.edit().putString(itemKey(appWidgetId), item.toString()).apply()
        return true
    }

    /**
     * Steps to the next/previous item (wrapping around) from whatever this
     * widget currently shows. [direction] is +1 (next) or -1 (previous).
     * Falls back to [pickRandomItem] if nothing's stored yet.
     */
    fun stepItem(context: Context, appWidgetId: Int, direction: Int) {
        val prefs = prefs(context)
        val raw = prefs.getString(itemKey(appWidgetId), null)
        if (raw == null) {
            pickRandomItem(context, appWidgetId)
            return
        }
        val current = JSONObject(raw)
        val mode = current.optString("mode", "word")
        val level = prefs.getString(levelKey(appWidgetId), "0") ?: "0"
        val list = filtered(context, mode, level)
        if (list.isEmpty()) return
        val currentId = current.optString("itemId")
        val curIndex = list.indexOfFirst { it.getString("id") == currentId }
        val newIndex = ((if (curIndex < 0) 0 else curIndex) + direction + list.size) % list.size
        val item = buildItem(mode, list[newIndex])
        prefs.edit().putString(itemKey(appWidgetId), item.toString()).apply()
    }
}
