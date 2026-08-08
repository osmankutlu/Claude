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
    // Only meaningful when mode == "favorite_group": which user-created
    // favorite group (see FavoriteGroupsRepository on the Dart side) this
    // widget instance shows.
    fun groupKey(appWidgetId: Int) = "widget_group_$appWidgetId"
    fun displayKey(appWidgetId: Int) = "widget_display_$appWidgetId"
    fun itemKey(appWidgetId: Int) = "widget_item_$appWidgetId"
    // How often (in screen unlocks) the word changes, and the running count.
    fun unlockEveryKey(appWidgetId: Int) = "widget_unlockevery_$appWidgetId"
    fun unlockCountKey(appWidgetId: Int) = "widget_unlockcount_$appWidgetId"
    // A persistent shuffled visiting order over the current item set's ids,
    // for [stepItem] — see [shuffledOrder].
    fun orderKey(appWidgetId: Int) = "widget_order_$appWidgetId"

    /** The word/grammar items this widget should show, per its mode+level
     *  (or mode+group, for "favorite_group" widgets). */
    fun itemsFor(context: Context, appWidgetId: Int): List<JSONObject> = resolvedItems(context, appWidgetId)

    fun displayModeFor(context: Context, appWidgetId: Int): String =
        prefs(context).getString(displayKey(appWidgetId), "both") ?: "both"

    // Favorite id lists — same keys the Flutter FavoritesRepository writes, so
    // starring from the widget and from inside the app stay in sync.
    private const val FAV_WORDS = "favorite_word_ids"
    private const val FAV_GRAMMAR = "favorite_grammar_ids"
    // Same key FavoriteGroupsRepository (Dart) writes: a JSON array of
    // {id, name, wordIds}.
    private const val FAV_GROUPS = "favorite_groups"

    private fun favKey(mode: String) = if (mode == "grammar") FAV_GRAMMAR else FAV_WORDS

    /** Word ids belonging to the favorite group [groupId], or empty if the
     *  group doesn't exist / no groups are stored. */
    private fun favoriteGroupWordIds(context: Context, groupId: String): Set<String> {
        if (groupId.isEmpty()) return emptySet()
        val raw = prefs(context).getString(FAV_GROUPS, null) ?: return emptySet()
        val groupsArr = try { JSONArray(raw) } catch (e: Exception) { return emptySet() }
        for (i in 0 until groupsArr.length()) {
            val g = groupsArr.getJSONObject(i)
            if (g.optString("id") != groupId) continue
            val idsArr = g.optJSONArray("wordIds") ?: return emptySet()
            val ids = HashSet<String>(idsArr.length())
            for (j in 0 until idsArr.length()) ids.add(idsArr.getString(j))
            return ids
        }
        return emptySet()
    }

    private fun favoriteWordIds(context: Context): Set<String> {
        val raw = prefs(context).getString(FAV_WORDS, null) ?: return emptySet()
        val arr = try { JSONArray(raw) } catch (e: Exception) { return emptySet() }
        val ids = HashSet<String>(arr.length())
        for (i in 0 until arr.length()) ids.add(arr.getString(i))
        return ids
    }

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

    /** Words belonging to favorite group [groupId] — intersected with the
     *  words actually still favorited, since a word can be un-favorited
     *  (including from this widget's own ★ button) without that group
     *  membership being cleaned up on the Dart side. */
    private fun filteredFavoriteGroup(context: Context, groupId: String): List<JSONObject> {
        val groupIds = favoriteGroupWordIds(context, groupId)
        if (groupIds.isEmpty()) return emptyList()
        val favIds = favoriteWordIds(context)
        val all = loadWords(context)
        val filtered = mutableListOf<JSONObject>()
        for (i in 0 until all.length()) {
            val o = all.getJSONObject(i)
            val id = o.optString("id")
            if (groupIds.contains(id) && favIds.contains(id)) filtered.add(o)
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

    /** The items a widget's stored mode+level (or mode+group) config
     *  resolves to — the single place all three public entry points below
     *  read that config from, so mode/level/group always agree. */
    private fun resolvedItems(context: Context, appWidgetId: Int): List<JSONObject> {
        val prefs = prefs(context)
        val mode = prefs.getString(modeKey(appWidgetId), "word") ?: "word"
        return when (mode) {
            "grammar" -> filteredGrammar(context, prefs.getString(levelKey(appWidgetId), "0") ?: "0")
            "favorite_group" -> filteredFavoriteGroup(context, prefs.getString(groupKey(appWidgetId), "") ?: "")
            else -> filteredWords(context, prefs.getString(levelKey(appWidgetId), "0") ?: "0")
        }
    }

    /**
     * Picks a new random word/grammar item (respecting the widget's
     * configured mode+level, or mode+group for favorite-group widgets) and
     * stores it as this widget's current item. Returns false (and stores
     * nothing) if that configuration has no matches.
     */
    fun pickRandomItem(context: Context, appWidgetId: Int): Boolean {
        val prefs = prefs(context)
        val mode = prefs.getString(modeKey(appWidgetId), "word") ?: "word"
        val list = resolvedItems(context, appWidgetId)
        if (list.isEmpty()) return false
        val item = buildItem(mode, list[Random.nextInt(list.size)])
        prefs.edit().putString(itemKey(appWidgetId), item.toString()).apply()
        return true
    }

    /**
     * A persistent, randomized visiting order over [list]'s ids for this
     * widget instance — [stepItem] walks forward/backward through this
     * instead of [list]'s own order, which is really just whatever order
     * words.json/grammar.json happen to be in (effectively alphabetical),
     * making next/prev feel like it was cycling the dictionary in order.
     * Rebuilt whenever the stored order doesn't match the current id set
     * (config changed, list size changed, or first use).
     */
    private fun shuffledOrder(context: Context, appWidgetId: Int, list: List<JSONObject>): List<String> {
        val prefs = prefs(context)
        val ids = list.map { it.getString("id") }
        val stored = prefs.getString(orderKey(appWidgetId), null)?.let { raw ->
            try {
                val arr = JSONArray(raw)
                List(arr.length()) { arr.getString(it) }
            } catch (e: Exception) {
                null
            }
        }
        if (stored != null && stored.size == ids.size && stored.toSet() == ids.toSet()) return stored
        val shuffled = ids.shuffled(Random)
        prefs.edit().putString(orderKey(appWidgetId), JSONArray(shuffled).toString()).apply()
        return shuffled
    }

    /**
     * Steps to the next/previous item (wrapping around, via [shuffledOrder])
     * from whatever this widget currently shows. [direction] is +1 (next)
     * or -1 (previous). Falls back to [pickRandomItem] if nothing's stored
     * yet.
     */
    fun stepItem(context: Context, appWidgetId: Int, direction: Int) {
        val prefs = prefs(context)
        val raw = prefs.getString(itemKey(appWidgetId), null)
        if (raw == null) {
            pickRandomItem(context, appWidgetId)
            return
        }
        val current = JSONObject(raw)
        val mode = prefs.getString(modeKey(appWidgetId), "word") ?: "word"
        val list = resolvedItems(context, appWidgetId)
        if (list.isEmpty()) return
        val order = shuffledOrder(context, appWidgetId, list)
        val currentId = current.optString("itemId")
        val curIndex = order.indexOf(currentId)
        val newIndex = ((if (curIndex < 0) 0 else curIndex) + direction + order.size) % order.size
        val picked = list.firstOrNull { it.getString("id") == order[newIndex] } ?: list[0]
        val item = buildItem(mode, picked)
        prefs.edit().putString(itemKey(appWidgetId), item.toString()).apply()
    }
}
