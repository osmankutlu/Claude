package com.osmankutlu.zh_en_dict

import android.content.Intent
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/**
 * Besides hosting the Flutter UI, this activity handles the "open this
 * word" deep link fired by tapping a word chip inside a widget's example
 * sentence: a word id arrives as an intent extra, and is either handed to
 * Dart immediately (if the engine is already running, e.g. app was in the
 * background) or queried by Dart once at startup via [CHANNEL] (cold start).
 */
class MainActivity : FlutterActivity() {
    private var deepLinkChannel: MethodChannel? = null
    private var pendingWordId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Read before super.onCreate(), which is what attaches the Flutter
        // engine and calls configureFlutterEngine synchronously.
        pendingWordId = intent?.getStringExtra(EXTRA_WORD_ID)
        super.onCreate(savedInstanceState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val wordId = intent.getStringExtra(EXTRA_WORD_ID) ?: return
        val channel = deepLinkChannel
        if (channel != null) {
            channel.invokeMethod("openWord", wordId)
        } else {
            pendingWordId = wordId
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        channel.setMethodCallHandler { call, result ->
            when (call.method) {
                "getPendingWordId" -> {
                    result.success(pendingWordId)
                    pendingWordId = null
                }
                else -> result.notImplemented()
            }
        }
        deepLinkChannel = channel
    }

    companion object {
        const val CHANNEL = "com.osmankutlu.zh_en_dict/deep_link"
        const val EXTRA_WORD_ID = "word_id"

        // Distinct intent actions used by WordWidgetProvider so its two
        // MainActivity-targeting PendingIntents (plain "open app" vs "open
        // this word") are never mistaken for each other by
        // FLAG_UPDATE_CURRENT, even if their numeric request codes collide.
        const val ACTION_OPEN_APP = "com.osmankutlu.zh_en_dict.ACTION_OPEN_APP"
        const val ACTION_OPEN_WORD = "com.osmankutlu.zh_en_dict.ACTION_OPEN_WORD"
    }
}
