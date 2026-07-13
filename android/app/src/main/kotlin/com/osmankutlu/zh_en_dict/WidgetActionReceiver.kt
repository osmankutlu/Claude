package com.osmankutlu.zh_en_dict

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles the widget's tap actions: tapping the card shows the next random
 * word ([ACTION_CHANGE]); tapping the ★ toggles favorite ([ACTION_FAVORITE]).
 */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        when (action) {
            ACTION_CHANGE -> WidgetDataStore.pickRandomItem(context, appWidgetId)
            ACTION_FAVORITE -> {
                val item = WidgetDataStore.currentItem(context, appWidgetId) ?: return
                val mode = item.optString("mode", "word")
                val itemId = item.optString("itemId")
                if (itemId.isEmpty()) return
                WidgetDataStore.toggleFavorite(context, mode, itemId)
            }
        }

        WordWidgetProvider.renderWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
    }

    companion object {
        const val EXTRA_ACTION = "widget_action"
        const val ACTION_CHANGE = "change"
        const val ACTION_FAVORITE = "favorite"
    }
}
