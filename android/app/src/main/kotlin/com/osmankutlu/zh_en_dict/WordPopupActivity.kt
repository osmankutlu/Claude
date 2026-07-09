package com.osmankutlu.zh_en_dict

import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.android.FlutterActivityLaunchConfigs.BackgroundMode
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/**
 * Opened by tapping a card in the widget's StackView. Runs its own Flutter
 * engine (see AndroidManifest's transparent PopupTheme) showing a single
 * dialog-styled route with the word/topic's meaning and examples, so the
 * user never actually navigates into the main app to see it.
 */
class WordPopupActivity : FlutterActivity() {
    override fun getInitialRoute(): String = "/wordPopup"

    // Without this, FlutterActivity renders onto an opaque surface by
    // default and the transparent PopupTheme window would have no visible
    // effect — Dart's own Scaffold(backgroundColor: Colors.black54) scrim
    // needs a genuinely transparent Flutter surface to blend against.
    override fun getBackgroundMode(): BackgroundMode = BackgroundMode.transparent

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val mode = intent?.getStringExtra(EXTRA_MODE)
        val itemId = intent?.getStringExtra(EXTRA_ITEM_ID)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getArgs" -> result.success(mapOf("mode" to mode, "itemId" to itemId))
                    else -> result.notImplemented()
                }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No status/nav bar chrome so this reads as a floating popup, not a
        // regular full-screen page of the app.
        window.setBackgroundDrawableResource(android.R.color.transparent)
    }

    companion object {
        const val CHANNEL = "com.osmankutlu.zh_en_dict/popup"
        const val EXTRA_MODE = "mode"
        const val EXTRA_ITEM_ID = "item_id"
    }
}
