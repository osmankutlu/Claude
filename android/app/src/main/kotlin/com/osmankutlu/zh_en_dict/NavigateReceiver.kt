package com.osmankutlu.zh_en_dict

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles taps on a widget instance's prev/next arrows. */
class NavigateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_NAVIGATE) return
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val direction = intent.getIntExtra(EXTRA_DIRECTION, 1)

        WidgetDataStore.stepItem(context, appWidgetId, direction)

        val appWidgetManager = AppWidgetManager.getInstance(context)
        WordWidgetProvider.renderWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {
        const val ACTION_NAVIGATE = "com.osmankutlu.zh_en_dict.ACTION_NAVIGATE"
        const val EXTRA_DIRECTION = "direction"
    }
}
