package com.osmankutlu.zh_en_dict

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import org.json.JSONObject

class WordWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = WidgetDataStore.prefs(context).edit()
        for (id in appWidgetIds) {
            editor.remove(WidgetDataStore.modeKey(id))
            editor.remove(WidgetDataStore.levelKey(id))
            editor.remove(WidgetDataStore.itemKey(id))
        }
        editor.apply()
    }

    companion object {
        fun updateAll(context: Context, manager: AppWidgetManager, ids: IntArray) {
            for (id in ids) updateWidget(context, manager, id)
        }

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = WidgetDataStore.prefs(context)
            val raw = prefs.getString(WidgetDataStore.itemKey(appWidgetId), null)
            val views = RemoteViews(context.packageName, R.layout.word_widget)

            if (raw == null) {
                views.setTextViewText(R.id.widget_level_badge, "")
                views.setTextViewText(R.id.widget_title, "Çince Sözlük")
                views.setTextViewText(R.id.widget_subtitle, "")
                views.setTextViewText(R.id.widget_meaning, "Yükleniyor…")
                views.setViewVisibility(R.id.widget_star, View.GONE)
            } else {
                val item = JSONObject(raw)
                val mode = item.optString("mode", "word")
                val level = item.optInt("level", 1)
                views.setTextViewText(R.id.widget_level_badge, "HSK$level")
                views.setViewVisibility(R.id.widget_star, View.VISIBLE)

                if (mode == "grammar") {
                    views.setTextViewText(R.id.widget_title, item.optString("title"))
                    views.setTextViewText(R.id.widget_subtitle, "")
                    views.setTextViewText(R.id.widget_meaning, item.optString("summary"))
                } else {
                    views.setTextViewText(R.id.widget_title, item.optString("hanzi"))
                    views.setTextViewText(R.id.widget_subtitle, item.optString("pinyin"))
                    views.setTextViewText(R.id.widget_meaning, item.optString("meaning"))
                }

                val itemId = item.optString("itemId")
                val isFav = if (mode == "grammar")
                    WidgetDataStore.isFavoriteGrammar(context, itemId)
                else
                    WidgetDataStore.isFavoriteWord(context, itemId)
                views.setImageViewResource(
                    R.id.widget_star,
                    if (isFav) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off
                )

                val toggleIntent = Intent(context, ToggleFavoriteReceiver::class.java).apply {
                    action = ToggleFavoriteReceiver.ACTION_TOGGLE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                val togglePending = PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    toggleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_star, togglePending)
            }

            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPending = PendingIntent.getActivity(
                context,
                appWidgetId,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppPending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
