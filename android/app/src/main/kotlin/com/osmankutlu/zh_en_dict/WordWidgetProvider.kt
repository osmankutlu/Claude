package com.osmankutlu.zh_en_dict

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import org.json.JSONObject

class WordWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = WidgetDataStore.prefs(context).edit()
        for (id in appWidgetIds) {
            editor.remove(WidgetDataStore.modeKey(id))
            editor.remove(WidgetDataStore.levelKey(id))
            editor.remove(WidgetDataStore.itemKey(id))
            editor.remove(WidgetDataStore.displayKey(id))
        }
        editor.apply()
    }

    /**
     * Fired whenever the user resizes the widget on the home screen (drag
     * handles), letting the widget adapt its content to the space actually
     * available instead of just clipping.
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {
        fun updateAll(context: Context, manager: AppWidgetManager, ids: IntArray) {
            for (id in ids) updateWidget(context, manager, id)
        }

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = WidgetDataStore.prefs(context)
            val raw = prefs.getString(WidgetDataStore.itemKey(appWidgetId), null)
            val displayMode = prefs.getString(WidgetDataStore.displayKey(appWidgetId), "both") ?: "both"
            val views = RemoteViews(context.packageName, R.layout.word_widget)

            // Height available on the home screen right now, so very small
            // (e.g. 1-cell-tall) placements drop the example section instead
            // of clipping it.
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minHeightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
            val compact = minHeightDp in 1 until 130

            if (raw == null) {
                views.setTextViewText(R.id.widget_level_badge, "")
                views.setTextViewText(R.id.widget_title, "Çince Sözlük")
                views.setTextViewText(R.id.widget_subtitle, "")
                views.setViewVisibility(R.id.widget_subtitle, View.GONE)
                views.setTextViewText(R.id.widget_meaning, "Yükleniyor…")
                views.setViewVisibility(R.id.widget_meaning, View.VISIBLE)
                views.setViewVisibility(R.id.widget_star, View.GONE)
                views.setViewVisibility(R.id.widget_example_header, View.GONE)
                views.setViewVisibility(R.id.widget_example_chips, View.GONE)
                views.setViewVisibility(R.id.widget_example_pinyin, View.GONE)
                views.setViewVisibility(R.id.widget_example_en, View.GONE)
            } else {
                val item = JSONObject(raw)
                val mode = item.optString("mode", "word")
                val level = item.optInt("level", 1)
                views.setTextViewText(R.id.widget_level_badge, "HSK$level")
                views.setViewVisibility(R.id.widget_star, View.VISIBLE)

                val showExampleSection = !compact
                // Only consulted in word mode below; grammar always hides
                // both the chip row and the pinyin line outright.
                val showHanzi = displayMode != "pinyin"
                // In pinyin-only mode the title already carries the pinyin,
                // so the subtitle underneath it would just repeat it.
                val showSubtitlePinyin = displayMode == "both"
                val showExamplePinyin = displayMode != "hanzi"

                if (mode == "grammar") {
                    views.setTextViewText(R.id.widget_title, item.optString("title"))
                    views.setTextViewText(R.id.widget_subtitle, "")
                    views.setViewVisibility(R.id.widget_subtitle, View.GONE)
                    views.setTextViewText(R.id.widget_meaning, item.optString("summary"))
                    views.setViewVisibility(R.id.widget_meaning, if (compact) View.GONE else View.VISIBLE)
                    views.setViewVisibility(R.id.widget_example_header, View.GONE)
                    views.setViewVisibility(R.id.widget_example_chips, View.GONE)
                    views.setViewVisibility(R.id.widget_example_pinyin, View.GONE)
                    views.setViewVisibility(R.id.widget_example_en, View.GONE)
                } else {
                    // In pinyin-only mode there is no hanzi to show, so the
                    // (bigger, bolder) title view carries the pinyin instead.
                    views.setTextViewText(
                        R.id.widget_title,
                        if (showHanzi) item.optString("hanzi") else item.optString("pinyin")
                    )
                    views.setViewVisibility(R.id.widget_subtitle, if (showSubtitlePinyin) View.VISIBLE else View.GONE)
                    if (showSubtitlePinyin) views.setTextViewText(R.id.widget_subtitle, item.optString("pinyin"))
                    views.setViewVisibility(R.id.widget_meaning, if (compact) View.GONE else View.VISIBLE)
                    views.setTextViewText(R.id.widget_meaning, item.optString("meaning"))

                    val examples = item.optJSONArray("examples")
                    if (showExampleSection && examples != null && examples.length() > 0) {
                        val exampleIndex = item.optInt("exampleIndex", 0).coerceIn(0, examples.length() - 1)
                        val example = examples.getJSONObject(exampleIndex)
                        views.setViewVisibility(R.id.widget_example_header, View.VISIBLE)
                        views.setTextViewText(
                            R.id.widget_example_counter,
                            "${exampleIndex + 1}/${examples.length()}"
                        )
                        views.setTextViewText(R.id.widget_example_en, example.optString("en"))
                        views.setViewVisibility(R.id.widget_example_en, View.VISIBLE)

                        if (showHanzi) {
                            views.setViewVisibility(R.id.widget_example_chips, View.VISIBLE)
                            // `views` is freshly constructed on every call, so
                            // widget_example_chips has no children yet — no
                            // need to clear it first.
                            addWordChipRows(
                                context, views, appWidgetId,
                                WidgetDataStore.segmentExample(context, example.optString("zh")),
                                options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250) ?: 250
                            )
                        } else {
                            views.setViewVisibility(R.id.widget_example_chips, View.GONE)
                        }

                        views.setViewVisibility(R.id.widget_example_pinyin, if (showExamplePinyin) View.VISIBLE else View.GONE)
                        if (showExamplePinyin) views.setTextViewText(R.id.widget_example_pinyin, example.optString("pinyin"))

                        val prevExamplePending = navigatePendingIntent(
                            context, appWidgetId, NavigateReceiver.ACTION_NAVIGATE_EXAMPLE, -1, requestOffset = 2
                        )
                        val nextExamplePending = navigatePendingIntent(
                            context, appWidgetId, NavigateReceiver.ACTION_NAVIGATE_EXAMPLE, 1, requestOffset = 3
                        )
                        views.setOnClickPendingIntent(R.id.widget_prev_example, prevExamplePending)
                        views.setOnClickPendingIntent(R.id.widget_next_example, nextExamplePending)
                    } else {
                        views.setViewVisibility(R.id.widget_example_header, View.GONE)
                        views.setViewVisibility(R.id.widget_example_chips, View.GONE)
                        views.setViewVisibility(R.id.widget_example_pinyin, View.GONE)
                        views.setViewVisibility(R.id.widget_example_en, View.GONE)
                    }
                }

                val itemId = item.optString("itemId")
                val isFav = if (mode == "grammar")
                    WidgetDataStore.isFavoriteGrammar(context, itemId)
                else
                    WidgetDataStore.isFavoriteWord(context, itemId)
                views.setImageViewResource(
                    R.id.widget_star,
                    if (isFav) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off
                )

                val toggleIntent = Intent(context, ToggleFavoriteReceiver::class.java).apply {
                    action = ToggleFavoriteReceiver.ACTION_TOGGLE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                val togglePending = PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    toggleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_star, togglePending)

                val prevItemPending = navigatePendingIntent(
                    context, appWidgetId, NavigateReceiver.ACTION_NAVIGATE_ITEM, -1, requestOffset = 0
                )
                val nextItemPending = navigatePendingIntent(
                    context, appWidgetId, NavigateReceiver.ACTION_NAVIGATE_ITEM, 1, requestOffset = 1
                )
                views.setOnClickPendingIntent(R.id.widget_prev_item, prevItemPending)
                views.setOnClickPendingIntent(R.id.widget_next_item, nextItemPending)
            }

            // A distinct action (not just a distinct request code) keeps this
            // from ever being treated as equivalent to a word-chip's
            // PendingIntent below: appWidgetId is a device-global counter
            // shared across every app widget, so two widgets' numeric
            // request codes can coincide (e.g. widget #1000's plain "open
            // app" tap vs widget #1's first word chip). Since PendingIntent
            // extras aren't part of its identity, only action/data/component
            // differences reliably prevent FLAG_UPDATE_CURRENT from
            // silently overwriting one with the other's extras.
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_APP
            }
            val openAppPending = PendingIntent.getActivity(
                context,
                appWidgetId,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppPending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /**
         * Builds a PendingIntent for [NavigateReceiver]. [requestOffset] (0-3)
         * keeps the four navigation buttons' request codes distinct per widget
         * instance so their PendingIntents don't collide/overwrite each other.
         */
        private fun navigatePendingIntent(
            context: Context,
            appWidgetId: Int,
            action: String,
            direction: Int,
            requestOffset: Int
        ): PendingIntent {
            val intent = Intent(context, NavigateReceiver::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(NavigateReceiver.EXTRA_DIRECTION, direction)
            }
            return PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + requestOffset,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /**
         * Builds a PendingIntent that opens the app straight to [wordId]'s
         * detail screen (handled by MainActivity's deep-link channel).
         * [segIndex] keeps each chip's request code unique within this
         * widget's currently-rendered example (only one example's chips are
         * ever on screen at once, so that's all uniqueness requires); the
         * distinct action — not just the request code — is what actually
         * keeps this from colliding with another widget's plain "open app"
         * PendingIntent, since appWidgetId is a device-global counter and
         * two different widgets' numeric request codes can coincide.
         */
        private fun openWordPendingIntent(
            context: Context,
            appWidgetId: Int,
            segIndex: Int,
            wordId: String
        ): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_WORD
                putExtra(MainActivity.EXTRA_WORD_ID, wordId)
            }
            val requestCode = appWidgetId * 1000 + segIndex
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /**
         * RemoteViews has no flow/wrap layout and doesn't support
         * HorizontalScrollView, so tappable example words are packed into
         * fixed rows (rough dp-width estimate per run) and each row is added
         * as its own nested horizontal LinearLayout. Capped at a few rows so
         * a long sentence can't push the rest of the widget off-screen.
         */
        private fun addWordChipRows(
            context: Context,
            views: RemoteViews,
            appWidgetId: Int,
            segments: List<Pair<String, String?>>,
            availableWidthDpRaw: Int
        ) {
            val availableWidthDp = (availableWidthDpRaw - 24).coerceAtLeast(80)
            val maxRows = 3

            val rows = mutableListOf<MutableList<Pair<String, String?>>>()
            var currentRow = mutableListOf<Pair<String, String?>>()
            var currentRowWidth = 0
            for (segment in segments) {
                val chipWidth = 12 + segment.first.length * 15
                if (currentRow.isNotEmpty() && currentRowWidth + chipWidth > availableWidthDp) {
                    rows.add(currentRow)
                    if (rows.size >= maxRows) {
                        currentRow = mutableListOf()
                        break
                    }
                    currentRow = mutableListOf()
                    currentRowWidth = 0
                }
                currentRow.add(segment)
                currentRowWidth += chipWidth
            }
            if (currentRow.isNotEmpty() && rows.size < maxRows) rows.add(currentRow)

            var segIndex = 0
            for (row in rows) {
                val rowViews = RemoteViews(context.packageName, R.layout.widget_chip_row)
                for ((text, wordId) in row) {
                    if (wordId != null) {
                        val chip = RemoteViews(context.packageName, R.layout.widget_word_chip)
                        chip.setTextViewText(R.id.chip_text, text)
                        val openPending = openWordPendingIntent(context, appWidgetId, segIndex, wordId)
                        chip.setOnClickPendingIntent(R.id.chip_text, openPending)
                        rowViews.addView(R.id.chip_row, chip)
                    } else {
                        val plainRun = RemoteViews(context.packageName, R.layout.widget_plain_text_run)
                        plainRun.setTextViewText(R.id.plain_text, text)
                        rowViews.addView(R.id.chip_row, plainRun)
                    }
                    segIndex++
                }
                views.addView(R.id.widget_example_chips, rowViews)
            }
        }
    }
}
