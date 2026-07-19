package com.osmankutlu.zh_en_dict

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import org.json.JSONObject

/**
 * The home-screen widget: a single word/grammar card on a faux stacked-deck
 * background — exactly one word on screen. Tapping the card shows the next
 * random word; ⚙ opens settings, ★ favorites the word, ⓘ opens the popup.
 * The word also changes on screen unlock, per the configured frequency.
 */
class WordWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            WidgetDataStore.pickRandomItem(context, appWidgetId)
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
            editor.remove(WidgetDataStore.itemKey(id))
        }
        editor.apply()
    }

    companion object {
        /**
         * On screen unlock, change the word of every widget that's due per its
         * configured frequency (every N unlocks; "0" = manual only).
         */
        fun onScreenUnlock(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
            for (appWidgetId in appWidgetIds) {
                if (WidgetDataStore.shouldChangeOnUnlock(context, appWidgetId)) {
                    WidgetDataStore.pickRandomItem(context, appWidgetId)
                    renderWidget(context, appWidgetManager, appWidgetId)
                }
            }
        }

        fun renderWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = WidgetDataStore.prefs(context)
            var raw = prefs.getString(WidgetDataStore.itemKey(appWidgetId), null)
            if (raw == null) {
                WidgetDataStore.pickRandomItem(context, appWidgetId)
                raw = prefs.getString(WidgetDataStore.itemKey(appWidgetId), null)
            }
            val displayMode = prefs.getString(WidgetDataStore.displayKey(appWidgetId), "both") ?: "both"
            val views = RemoteViews(context.packageName, R.layout.word_widget)

            // Settings (⚙) is available even while loading.
            views.setOnClickPendingIntent(R.id.widget_settings, configPendingIntent(context, appWidgetId))

            if (raw == null) {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_loading))
                views.setTextViewText(R.id.widget_subtitle, "")
                views.setViewVisibility(R.id.widget_subtitle, View.GONE)
                views.setViewVisibility(R.id.widget_fav, View.GONE)
                views.setViewVisibility(R.id.widget_info, View.GONE)
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }

            val item = JSONObject(raw)
            val mode = item.optString("mode", "word")
            val itemId = item.optString("itemId")

            if (mode == "grammar") {
                views.setTextViewText(R.id.widget_title, item.optString("title"))
                views.setViewVisibility(R.id.widget_subtitle, View.GONE)
            } else {
                val showHanzi = displayMode != "pinyin"
                val showPinyin = displayMode != "hanzi"
                val hanzi = item.optString("hanzi")
                val pinyin = item.optString("pinyin")
                if (showHanzi) {
                    val html = PinyinTone.hanziHtml(hanzi, pinyin)
                    views.setTextViewText(R.id.widget_title, if (html != null) PinyinTone.fromHtml(html) else hanzi)
                } else {
                    val html = PinyinTone.pinyinHtml(pinyin)
                    views.setTextViewText(R.id.widget_title, if (html != null) PinyinTone.fromHtml(html) else pinyin)
                }
                if (showHanzi && showPinyin) {
                    val subHtml = PinyinTone.pinyinHtml(pinyin)
                    views.setTextViewText(R.id.widget_subtitle, if (subHtml != null) PinyinTone.fromHtml(subHtml) else pinyin)
                    views.setViewVisibility(R.id.widget_subtitle, View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.widget_subtitle, View.GONE)
                }
            }

            // Favorite star reflects state; tap toggles it.
            val isFav = WidgetDataStore.isFavorite(context, mode, itemId)
            views.setViewVisibility(R.id.widget_fav, View.VISIBLE)
            views.setImageViewResource(
                R.id.widget_fav,
                if (isFav) R.drawable.ic_widget_star_filled else R.drawable.ic_widget_star
            )
            views.setOnClickPendingIntent(
                R.id.widget_fav,
                actionPendingIntent(context, appWidgetId, WidgetActionReceiver.ACTION_FAVORITE)
            )

            // Tap the left half → previous word, right half → next word.
            views.setOnClickPendingIntent(
                R.id.widget_prev,
                actionPendingIntent(context, appWidgetId, WidgetActionReceiver.ACTION_PREV)
            )
            views.setOnClickPendingIntent(
                R.id.widget_next,
                actionPendingIntent(context, appWidgetId, WidgetActionReceiver.ACTION_NEXT)
            )

            // ⓘ → meaning/examples popup for the current item.
            views.setViewVisibility(R.id.widget_info, View.VISIBLE)
            val popupIntent = Intent(context, WordPopupActivity::class.java).apply {
                putExtra(WordPopupActivity.EXTRA_MODE, mode)
                putExtra(WordPopupActivity.EXTRA_ITEM_ID, itemId)
            }
            views.setOnClickPendingIntent(
                R.id.widget_info,
                PendingIntent.getActivity(
                    context, appWidgetId * 10 + 3, popupIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            // 🔍 → walks the permission flow (if needed) and toggles the
            // screen-lens overlay. Same relay activity/request code for every
            // widget instance: it's a single global on/off switch, not
            // per-widget.
            val lensIntent = Intent(context, ScreenCapturePermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            views.setOnClickPendingIntent(
                R.id.widget_lens,
                PendingIntent.getActivity(
                    context, LENS_REQUEST_CODE, lensIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun actionPendingIntent(context: Context, appWidgetId: Int, action: String): PendingIntent {
            val intent = Intent(context, WidgetActionReceiver::class.java).apply {
                putExtra(WidgetActionReceiver.EXTRA_ACTION, action)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                // Distinct action string in the data keeps change/favorite
                // PendingIntents from being treated as the same one.
                data = android.net.Uri.parse("zhendict://widget/$appWidgetId/$action")
            }
            val requestCode = appWidgetId * 10 + when (action) {
                WidgetActionReceiver.ACTION_NEXT -> 1
                WidgetActionReceiver.ACTION_PREV -> 5
                WidgetActionReceiver.ACTION_FAVORITE -> 2
                else -> 6
            }
            return PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        // A fixed request code (not per-widget, unlike the others below):
        // the lens is one global overlay regardless of which widget instance
        // its button was tapped from.
        private const val LENS_REQUEST_CODE = 99999

        private fun configPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, WidgetConfigureActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return PendingIntent.getActivity(
                context, appWidgetId * 10 + 4, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
