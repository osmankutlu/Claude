package com.osmankutlu.zh_en_dict

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Invisible relay activity for the screen-lens feature, opened from the
 * widget's lens button. Draws nothing itself:
 *  - If the overlay is already running, stops it (the button is a toggle).
 *  - Otherwise walks the permissions the feature needs in order — "draw over
 *    other apps" (a one-time Settings toggle), notification (Android 13+, so
 *    the "Kapat" stop button in the ongoing notification is visible), then
 *    screen capture (a system dialog Android requires on every fresh capture
 *    session) — then starts [OcrOverlayService] and finishes.
 * Nothing here is saved across process death; if the OS kills the app the
 * user just taps the widget button again.
 */
class ScreenCapturePermissionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (OcrOverlayService.isRunning) {
            stopService(Intent(this, OcrOverlayService::class.java))
            finish()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.lens_overlay_permission_needed, Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            finish()
            return
        }

        requestNotificationPermissionThenCapture()
    }

    // Best-effort: the ongoing notification's "Kapat" button needs this on
    // Android 13+, but the overlay's own close (×) badge covers stopping the
    // feature either way, so a denial here doesn't block anything below.
    private fun requestNotificationPermissionThenCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        } else {
            requestScreenCapture()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) requestScreenCapture()
    }

    private fun requestScreenCapture() {
        val projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CAPTURE && resultCode == RESULT_OK && data != null) {
            val serviceIntent = Intent(this, OcrOverlayService::class.java).apply {
                putExtra(OcrOverlayService.EXTRA_RESULT_CODE, resultCode)
                putExtra(OcrOverlayService.EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
        finish()
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 4200
        private const val REQUEST_CAPTURE = 4201
    }
}
