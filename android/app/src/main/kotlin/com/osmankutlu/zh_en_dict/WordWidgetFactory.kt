package com.osmankutlu.zh_en_dict

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import org.json.JSONObject

/**
 * Builds the cards for a widget instance's StackView deck. Each card shows one
 * word/grammar item (tone-colored) and carries two click targets via
 * fill-in intents: the card body opens the popup, the ★ toggles favorite.
 *
 * The deck is a shuffled slice of the widget's mode+level list. It's only
 * reshuffled when [WidgetDataStore.consumeReshuffle] says so (placement or a
 * due screen unlock) — a favorite toggle just rebuilds the same cards so the
 * star updates without reordering the deck.
 */
class WordWidgetFactory(
    private val context: Context,
    intent: Intent,
) : RemoteViewsService.RemoteViewsFactory {

    private val appWidgetId = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID
    )

    private var items: List<JSONObject> = emptyList()

    private fun reload() {
        // A generous shuffled slice — big enough to feel endless when swiping,
        // small enough to stay light for RemoteViews.
        items = WidgetDataStore.itemsFor(context, appWidgetId).shuffled().take(60)
    }

    override fun onCreate() {
        WidgetDataStore.consumeReshuffle(context, appWidgetId)
        reload()
    }

    override fun onDataSetChanged() {
        // Reshuffle only when asked (placement / unlock); otherwise keep the
        // deck order and just rebuild cards (e.g. to refresh a ★ after a toggle).
        if (WidgetDataStore.consumeReshuffle(context, appWidgetId) || items.isEmpty()) {
            reload()
        }
    }

    override fun onDestroy() {}

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.word_widget_item)
        if (position < 0 || position >= items.size) return rv
        val item = items[position]
        val mode = item.optString("mode", "word")
        val itemId = item.optString("id")
        val displayMode = WidgetDataStore.displayModeFor(context, appWidgetId)

        if (mode == "grammar") {
            rv.setTextViewText(R.id.widget_item_title, item.optString("title"))
            rv.setViewVisibility(R.id.widget_item_subtitle, View.GONE)
        } else {
            val showHanzi = displayMode != "pinyin"
            val showPinyin = displayMode != "hanzi"
            val hanzi = item.optString("hanzi")
            val pinyin = item.optString("pinyin")
            if (showHanzi) {
                val html = PinyinTone.hanziHtml(hanzi, pinyin)
                rv.setTextViewText(R.id.widget_item_title, if (html != null) PinyinTone.fromHtml(html) else hanzi)
            } else {
                val html = PinyinTone.pinyinHtml(pinyin)
                rv.setTextViewText(R.id.widget_item_title, if (html != null) PinyinTone.fromHtml(html) else pinyin)
            }
            if (showHanzi && showPinyin) {
                val subHtml = PinyinTone.pinyinHtml(pinyin)
                rv.setTextViewText(R.id.widget_item_subtitle, if (subHtml != null) PinyinTone.fromHtml(subHtml) else pinyin)
                rv.setViewVisibility(R.id.widget_item_subtitle, View.VISIBLE)
            } else {
                rv.setViewVisibility(R.id.widget_item_subtitle, View.GONE)
            }
        }

        // Favorite star reflects current state; tapping it toggles.
        val isFav = WidgetDataStore.isFavorite(context, mode, itemId)
        rv.setImageViewResource(
            R.id.widget_item_fav,
            if (isFav) R.drawable.ic_widget_star_filled else R.drawable.ic_widget_star
        )

        // Card body → open popup; star → toggle favorite. Both merge into the
        // StackView's pending-intent template (WidgetItemReceiver).
        rv.setOnClickFillInIntent(
            R.id.widget_item_card,
            Intent()
                .putExtra(WidgetItemReceiver.EXTRA_ACTION, WidgetItemReceiver.ACTION_POPUP)
                .putExtra(WidgetItemReceiver.EXTRA_MODE, mode)
                .putExtra(WidgetItemReceiver.EXTRA_ITEM_ID, itemId)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        )
        rv.setOnClickFillInIntent(
            R.id.widget_item_fav,
            Intent()
                .putExtra(WidgetItemReceiver.EXTRA_ACTION, WidgetItemReceiver.ACTION_FAVORITE)
                .putExtra(WidgetItemReceiver.EXTRA_MODE, mode)
                .putExtra(WidgetItemReceiver.EXTRA_ITEM_ID, itemId)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        )
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}
