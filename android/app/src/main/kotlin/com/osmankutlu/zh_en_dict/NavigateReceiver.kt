package com.osmankutlu.zh_en_dict

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles taps on the manual prev/next arrows (item and example) inside a widget instance. */
class NavigateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val direction = intent.getIntExtra(EXTRA_DIRECTION, 1)

        when (intent.action) {
            ACTION_NAVIGATE_ITEM -> WidgetDataStore.navigateItem(context, appWidgetId, direction)
            ACTION_NAVIGATE_EXAMPLE -> WidgetDataStore.navigateExample(context, appWidgetId, direction)
            else -> return
        }

        val appWidgetManager = AppWidgetManager.getInstance(context)
        WordWidgetProvider.updateWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {
        const val ACTION_NAVIGATE_ITEM = "com.osmankutlu.zh_en_dict.ACTION_NAVIGATE_ITEM"
        const val ACTION_NAVIGATE_EXAMPLE = "com.osmankutlu.zh_en_dict.ACTION_NAVIGATE_EXAMPLE"
        const val EXTRA_DIRECTION = "direction"
    }
}
