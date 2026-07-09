package com.osmankutlu.zh_en_dict

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import org.json.JSONObject

class WordStackWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return WordStackRemoteViewsFactory(applicationContext, appWidgetId)
    }
}

/**
 * Feeds [R.id.widget_stack] one card per word/grammar topic matching this
 * widget's configured mode+level, in a freshly shuffled order each time
 * [onDataSetChanged] runs (screen unlock, or the widget being reconfigured)
 * so re-opening/updating the widget doesn't always start on the same card.
 * The user then moves between cards by swiping the StackView itself.
 */
class WordStackRemoteViewsFactory(
    private val context: Context,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<JSONObject> = emptyList()
    private var mode: String = "word"
    private var displayMode: String = "both"

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val prefs = WidgetDataStore.prefs(context)
        mode = prefs.getString(WidgetDataStore.modeKey(appWidgetId), "word") ?: "word"
        val level = prefs.getString(WidgetDataStore.levelKey(appWidgetId), "0") ?: "0"
        displayMode = prefs.getString(WidgetDataStore.displayKey(appWidgetId), "both") ?: "both"

        items = if (mode == "grammar") {
            WidgetDataStore.filteredGrammar(context, level)
        } else {
            WidgetDataStore.filteredWords(context, level)
        }.shuffled()
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val picked = items[position]
        val views = RemoteViews(context.packageName, R.layout.widget_stack_card)
        views.setTextViewText(R.id.card_level_badge, "HSK${picked.getInt("level")}")

        if (mode == "grammar") {
            views.setTextViewText(R.id.card_hanzi, picked.getString("title"))
            views.setViewVisibility(R.id.card_pinyin, android.view.View.GONE)
        } else {
            val showHanzi = displayMode != "pinyin"
            val showPinyin = displayMode != "hanzi"
            views.setTextViewText(
                R.id.card_hanzi,
                if (showHanzi) picked.getString("hanzi") else picked.getString("pinyin")
            )
            views.setViewVisibility(R.id.card_pinyin, if (showHanzi && showPinyin) android.view.View.VISIBLE else android.view.View.GONE)
            if (showHanzi && showPinyin) views.setTextViewText(R.id.card_pinyin, picked.getString("pinyin"))
        }

        val fillInIntent = Intent().apply {
            putExtra(WordPopupActivity.EXTRA_MODE, mode)
            putExtra(WordPopupActivity.EXTRA_ITEM_ID, picked.getString("id"))
        }
        views.setOnClickFillInIntent(R.id.card_root, fillInIntent)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
}
