package com.osmankutlu.zh_en_dict

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import org.json.JSONObject

class WordWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            // A fresh pick on every onUpdate (initial placement, or the
            // configure screen saving new settings) — matches how the
            // widget has always behaved: it shows something new whenever
            // it's (re)placed, and otherwise only changes via the
            // prev/next arrows or on screen unlock.
            WidgetDataStore.pickRandomItem(context, appWidgetId)
            renderWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = WidgetDataStore.prefs(context).edit()
        for (id in appWidgetIds) {
            editor.remove(WidgetDataStore.modeKey(id))
            editor.remove(WidgetDataStore.levelKey(id))
            editor.remove(WidgetDataStore.displayKey(id))
            editor.remove(WidgetDataStore.textscaleKey(id))
            editor.remove(WidgetDataStore.itemKey(id))
        }
        editor.apply()
    }

    companion object {
        /** Re-picks a random item for every widget instance (used on screen unlock). */
        fun refreshAll(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
            for (appWidgetId in appWidgetIds) {
                WidgetDataStore.pickRandomItem(context, appWidgetId)
                renderWidget(context, appWidgetManager, appWidgetId)
            }
        }

        fun renderWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = WidgetDataStore.prefs(context)
            val raw = prefs.getString(WidgetDataStore.itemKey(appWidgetId), null)
            val displayMode = prefs.getString(WidgetDataStore.displayKey(appWidgetId), "both") ?: "both"
            // User-chosen text size: scales all widget font sizes so the same
            // widget can be big and readable or small enough to shrink down.
            val scale = when (prefs.getString(WidgetDataStore.textscaleKey(appWidgetId), "large")) {
                "small" -> 0.7f
                "medium" -> 1.0f
                "xlarge" -> 1.7f
                else -> 1.35f // "large" (default) — bigger than the old fixed size
            }
            val views = RemoteViews(context.packageName, R.layout.word_widget)

            if (raw == null) {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_loading))
                views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, 18f)
                views.setTextViewText(R.id.widget_subtitle, "")
                views.setViewVisibility(R.id.widget_subtitle, View.GONE)
            } else {
                val item = JSONObject(raw)
                val mode = item.optString("mode", "word")

                if (mode == "grammar") {
                    // Grammar titles are whole phrases — the layout's huge
                    // hanzi size would overflow, so scale down for them.
                    views.setTextViewText(R.id.widget_title, item.optString("title"))
                    views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, 22f * scale)
                    views.setViewVisibility(R.id.widget_subtitle, View.GONE)
                } else {
                    val showHanzi = displayMode != "pinyin"
                    val showPinyin = displayMode != "hanzi"
                    val hanzi = item.optString("hanzi")
                    val pinyin = item.optString("pinyin")
                    // Tone-color the characters (hanzi) / syllables (pinyin) to
                    // match the in-app flashcards; fall back to plain text if
                    // the pinyin can't be aligned.
                    if (showHanzi) {
                        val html = PinyinTone.hanziHtml(hanzi, pinyin)
                        if (html != null) {
                            views.setTextViewText(R.id.widget_title, PinyinTone.fromHtml(html))
                        } else {
                            views.setTextViewText(R.id.widget_title, hanzi)
                        }
                    } else {
                        val html = PinyinTone.pinyinHtml(pinyin)
                        if (html != null) {
                            views.setTextViewText(R.id.widget_title, PinyinTone.fromHtml(html))
                        } else {
                            views.setTextViewText(R.id.widget_title, pinyin)
                        }
                    }
                    // Pinyin strings run much longer than their hanzi, so
                    // pinyin-only mode gets a smaller size to avoid clipping.
                    views.setTextViewTextSize(
                        R.id.widget_title,
                        TypedValue.COMPLEX_UNIT_SP,
                        (if (showHanzi) 44f else 26f) * scale
                    )
                    views.setViewVisibility(
                        R.id.widget_subtitle,
                        if (showHanzi && showPinyin) View.VISIBLE else View.GONE
                    )
                    if (showHanzi && showPinyin) {
                        val subHtml = PinyinTone.pinyinHtml(pinyin)
                        if (subHtml != null) {
                            views.setTextViewText(R.id.widget_subtitle, PinyinTone.fromHtml(subHtml))
                        } else {
                            views.setTextViewText(R.id.widget_subtitle, pinyin)
                        }
                        views.setTextViewTextSize(R.id.widget_subtitle, TypedValue.COMPLEX_UNIT_SP, 16f * scale)
                    }
                }

                val popupIntent = Intent(context, WordPopupActivity::class.java).apply {
                    putExtra(WordPopupActivity.EXTRA_MODE, mode)
                    putExtra(WordPopupActivity.EXTRA_ITEM_ID, item.optString("itemId"))
                }
                val popupPending = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    popupIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_card, popupPending)
            }

            views.setOnClickPendingIntent(R.id.widget_prev, navigatePendingIntent(context, appWidgetId, -1))
            views.setOnClickPendingIntent(R.id.widget_next, navigatePendingIntent(context, appWidgetId, 1))

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun navigatePendingIntent(context: Context, appWidgetId: Int, direction: Int): PendingIntent {
            val intent = Intent(context, NavigateReceiver::class.java).apply {
                action = NavigateReceiver.ACTION_NAVIGATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(NavigateReceiver.EXTRA_DIRECTION, direction)
            }
            // direction (-1/1) folded into the low bit keeps prev/next
            // PendingIntents distinct per widget instance.
            val requestCode = appWidgetId * 2 + if (direction < 0) 0 else 1
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
