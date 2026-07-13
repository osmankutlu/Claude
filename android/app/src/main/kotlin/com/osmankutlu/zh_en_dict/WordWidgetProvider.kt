package com.osmankutlu.zh_en_dict

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews

/**
 * The home-screen widget: a swipeable StackView deck of word/grammar cards.
 * The cards themselves come from [WordWidgetFactory]; this provider just wires
 * up the adapter, the tap template (popup / favorite), the settings button,
 * and reshuffles the deck on placement and on due screen unlocks.
 */
class WordWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            // Fresh deck on (re)placement or when settings are saved.
            WidgetDataStore.setReshuffle(context, appWidgetId, true)
            renderWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = WidgetDataStore.prefs(context).edit()
        for (id in appWidgetIds) {
            editor.remove(WidgetDataStore.modeKey(id))
            editor.remove(WidgetDataStore.levelKey(id))
            editor.remove(WidgetDataStore.displayKey(id))
            editor.remove(WidgetDataStore.unlockEveryKey(id))
            editor.remove(WidgetDataStore.unlockCountKey(id))
            editor.remove(WidgetDataStore.reshuffleKey(id))
        }
        editor.apply()
    }

    companion object {
        /**
         * On screen unlock, reshuffle the deck of every widget that's due per
         * its configured frequency (every N unlocks; "0" = manual only).
         */
        fun onScreenUnlock(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
            for (appWidgetId in appWidgetIds) {
                if (WidgetDataStore.shouldChangeOnUnlock(context, appWidgetId)) {
                    WidgetDataStore.setReshuffle(context, appWidgetId, true)
                    appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_stack)
                }
            }
        }

        fun renderWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.word_widget)

            // The StackView's cards are provided by WordWidgetService. A unique
            // data Uri per widget id keeps each instance's factory separate.
            val serviceIntent = Intent(context, WordWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_stack, serviceIntent)
            views.setEmptyView(R.id.widget_stack, R.id.widget_empty)

            // Tap template for the cards: the card body opens the popup and the
            // ★ toggles favorite (the action + item id come from each card's
            // fill-in intent). Must be mutable so those extras can merge in.
            val templateIntent = Intent(context, WidgetItemReceiver::class.java).apply {
                action = WidgetItemReceiver.ACTION_ITEM
            }
            val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_MUTABLE else 0
            val templatePending = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                templateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
            )
            views.setPendingIntentTemplate(R.id.widget_stack, templatePending)

            // Settings (⚙) → reopen the widget configure screen.
            val configIntent = Intent(context, WidgetConfigureActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val configPending = PendingIntent.getActivity(
                context,
                appWidgetId + 500000,
                configIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_settings, configPending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_stack)
        }
    }
}
