package com.osmankutlu.zh_en_dict

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONObject

/** Handles taps on the star icon inside a widget instance. */
class ToggleFavoriteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val prefs = WidgetDataStore.prefs(context)
        val raw = prefs.getString(WidgetDataStore.itemKey(appWidgetId), null) ?: return
        val item = JSONObject(raw)
        val mode = item.optString("mode", "word")
        val itemId = item.optString("itemId")
        if (itemId.isEmpty()) return

        WidgetDataStore.toggleFavorite(context, mode, itemId)

        val appWidgetManager = AppWidgetManager.getInstance(context)
        WordWidgetProvider.updateWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {
        const val ACTION_TOGGLE = "com.osmankutlu.zh_en_dict.ACTION_TOGGLE_FAVORITE"
    }
}
