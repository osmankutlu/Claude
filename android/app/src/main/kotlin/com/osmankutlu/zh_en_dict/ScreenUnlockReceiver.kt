package com.osmankutlu.zh_en_dict

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Reshuffles every widget instance's card stack each time the screen
 * unlocks, so re-opening the phone doesn't always land on the same word
 * (WordStackRemoteViewsFactory.onDataSetChanged shuffles on every refresh).
 */
class ScreenUnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, WordWidgetProvider::class.java))
        if (ids.isEmpty()) return

        WordWidgetProvider.updateAll(context, appWidgetManager, ids)
    }
}
