package com.osmankutlu.zh_en_dict

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Picks a fresh random word/grammar item for every widget instance each time the screen unlocks. */
class ScreenUnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, WordWidgetProvider::class.java))
        if (ids.isEmpty()) return

        for (id in ids) {
            WidgetDataStore.pickNewRandomItem(context, id)
        }
        WordWidgetProvider.updateAll(context, appWidgetManager, ids)
    }
}
