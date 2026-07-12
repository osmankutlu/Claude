package com.osmankutlu.zh_en_dict

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles taps on a widget instance's favorite (★) icon. */
class FavoriteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE_FAVORITE) return
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val item = WidgetDataStore.currentItem(context, appWidgetId) ?: return
        val mode = item.optString("mode", "word")
        val itemId = item.optString("itemId")
        if (itemId.isEmpty()) return

        WidgetDataStore.toggleFavorite(context, mode, itemId)

        val appWidgetManager = AppWidgetManager.getInstance(context)
        WordWidgetProvider.renderWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {
        const val ACTION_TOGGLE_FAVORITE = "com.osmankutlu.zh_en_dict.ACTION_TOGGLE_FAVORITE"
    }
}
