package com.osmankutlu.zh_en_dict

import android.app.Application
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/**
 * ACTION_USER_PRESENT (screen-unlock) is broadcast by the system with
 * FLAG_RECEIVER_REGISTERED_ONLY — unlike most broadcasts, it is delivered
 * *only* to dynamically (programmatically) registered receivers, never to
 * ones declared in the manifest, regardless of target SDK. A manifest
 * <receiver> for it (as this app used to have) is therefore silently never
 * invoked by the OS at all — not a permissions or targetSdk issue, just an
 * unconditional platform restriction for this specific action.
 *
 * Registering [ScreenUnlockReceiver] here instead means it actually fires
 * — but only while this process is alive. There's no permanently-running
 * foreground service backing the widget (that would mean an always-visible
 * notification, which this app deliberately avoids outside the opt-in
 * screen-lens feature), so this is a best-effort fix: it catches unlocks
 * whenever the process happens to be alive (e.g. for a while after the
 * widget's own periodic auto-update, after opening the app, or after any
 * widget interaction), not unconditionally every single unlock.
 */
class ZhEnDictApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            // NOT_EXPORTED: this is a protected system broadcast, no other
            // app should ever be the one sending it to us. Required (as of
            // API 33) to specify one or the other explicitly at all;
            // ContextCompat handles the pre-33 no-op case too.
            ContextCompat.registerReceiver(
                this,
                ScreenUnlockReceiver(),
                IntentFilter(android.content.Intent.ACTION_USER_PRESENT),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Throwable) {
            // Never let widget-refresh plumbing take the whole app down.
        }
    }
}
