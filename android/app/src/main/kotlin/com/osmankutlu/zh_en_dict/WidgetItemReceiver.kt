package com.osmankutlu.zh_en_dict

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles taps inside a StackView card: opening the popup (card body) and
 * toggling favorite (★). The action + item come from the card's fill-in
 * intent merged onto the StackView's pending-intent template.
 */
class WidgetItemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        val mode = intent.getStringExtra(EXTRA_MODE) ?: "word"
        val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: return
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        when (action) {
            ACTION_POPUP -> {
                val popup = Intent(context, WordPopupActivity::class.java).apply {
                    putExtra(WordPopupActivity.EXTRA_MODE, mode)
                    putExtra(WordPopupActivity.EXTRA_ITEM_ID, itemId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(popup)
            }
            ACTION_FAVORITE -> {
                WidgetDataStore.toggleFavorite(context, mode, itemId)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    // Rebuild the cards so the star reflects the new state.
                    // consumeReshuffle stays false here, so the deck order and
                    // (as much as StackView allows) the current card are kept.
                    AppWidgetManager.getInstance(context)
                        .notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_stack)
                }
            }
        }
    }

    companion object {
        const val ACTION_ITEM = "com.osmankutlu.zh_en_dict.ACTION_WIDGET_ITEM"
        const val EXTRA_ACTION = "item_action"
        const val EXTRA_MODE = "mode"
        const val EXTRA_ITEM_ID = "item_id"
        const val ACTION_POPUP = "popup"
        const val ACTION_FAVORITE = "favorite"
    }
}
