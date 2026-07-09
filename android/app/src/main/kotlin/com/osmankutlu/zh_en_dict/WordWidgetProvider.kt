package com.osmankutlu.zh_en_dict

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class WordWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateAll(context, appWidgetManager, appWidgetIds)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = WidgetDataStore.prefs(context).edit()
        for (id in appWidgetIds) {
            editor.remove(WidgetDataStore.modeKey(id))
            editor.remove(WidgetDataStore.levelKey(id))
            editor.remove(WidgetDataStore.displayKey(id))
        }
        editor.apply()
    }

    companion object {
        fun updateAll(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.word_widget)

                val adapterIntent = Intent(context, WordStackWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    // RemoteViewsService intents are matched by more than
                    // just extras (extras aren't part of Intent identity),
                    // so without a widget-id-unique data Uri every widget's
                    // stack would share one adapter/factory instance.
                    data = android.net.Uri.parse("widget://word_stack/$appWidgetId")
                }
                views.setRemoteAdapter(R.id.widget_stack, adapterIntent)
                views.setEmptyView(R.id.widget_stack, R.id.widget_empty)

                val popupIntent = Intent(context, WordPopupActivity::class.java)
                val popupTemplate = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    popupIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                views.setPendingIntentTemplate(R.id.widget_stack, popupTemplate)

                appWidgetManager.updateAppWidget(appWidgetId, views)
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_stack)
            }
        }
    }
}
