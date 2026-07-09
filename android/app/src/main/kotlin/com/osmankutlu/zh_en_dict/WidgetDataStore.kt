package com.osmankutlu.zh_en_dict

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Reads the SharedPreferences file the `home_widget` Flutter plugin uses
 * ("HomeWidgetPreferences"), so widget config Flutter saves via
 * HomeWidget.saveWidgetData is visible here. The widget itself has no
 * separate "current item" state anymore — [WordStackRemoteViewsFactory]
 * derives the whole card list from mode/level/display every time.
 */
object WidgetDataStore {
    private const val PREFS_NAME = "HomeWidgetPreferences"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun modeKey(appWidgetId: Int) = "widget_mode_$appWidgetId"
    fun levelKey(appWidgetId: Int) = "widget_level_$appWidgetId"
    fun displayKey(appWidgetId: Int) = "widget_display_$appWidgetId"

    /**
     * [levelSpec] is either "0" (all levels) or a comma-separated list of
     * HSK levels to mix together, e.g. "1,3,5".
     */
    private fun levelMatches(levelSpec: String, level: Int): Boolean {
        if (levelSpec.isEmpty() || levelSpec == "0") return true
        return levelSpec.split(",").any { it.trim() == level.toString() }
    }

    // In-memory only (not persisted): every widget's StackView adapter reads
    // through here, so a per-process cache avoids re-parsing the ~2.5k-entry
    // words.json from the bundled assets on every onDataSetChanged. Safe to
    // keep for the process's lifetime since the bundled asset only changes
    // on an app update (which restarts the process).
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

    fun filteredGrammar(context: Context, levelSpec: String): List<JSONObject> {
        val all = loadGrammar(context)
        val filtered = mutableListOf<JSONObject>()
        for (i in 0 until all.length()) {
            val o = all.getJSONObject(i)
            if (levelMatches(levelSpec, o.getInt("level"))) filtered.add(o)
        }
        return filtered
    }

    fun filteredWords(context: Context, levelSpec: String): List<JSONObject> {
        val all = loadWords(context)
        val filtered = mutableListOf<JSONObject>()
        for (i in 0 until all.length()) {
            val o = all.getJSONObject(i)
            if (levelMatches(levelSpec, o.getInt("level"))) filtered.add(o)
        }
        return filtered
    }
}
