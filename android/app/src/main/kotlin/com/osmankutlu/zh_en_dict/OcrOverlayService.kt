package com.osmankutlu.zh_en_dict

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Foreground service backing the screen-lens feature: draws a draggable
 * magnifier overlay on top of whatever app is in front, and on drop, grabs a
 * small crop of the live screen at that spot, runs on-device Chinese OCR on
 * it, looks the recognized word up in the bundled dictionary, and shows the
 * result as a second small overlay near the drop point.
 *
 * Owns the MediaProjection session end to end: created from the
 * (resultCode, data) [ScreenCapturePermissionActivity] forwards, torn down in
 * [onDestroy] along with the overlay views and the virtual display it feeds.
 */
class OcrOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    private var lensView: View? = null
    private var resultView: View? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var dismissResultRunnable: Runnable? = null

    private val textRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Already running (e.g. system redelivered the start intent) — the
        // lens is already up, nothing more to do.
        if (lensView != null) return START_NOT_STICKY

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData: Intent? = intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        if (resultData == null) {
            // Nothing to project without the permission data — bail out
            // rather than run a useless foreground service.
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())

        // Anything below (display metrics, the virtual display, inflating
        // and adding the overlay window) throwing uncaught would crash the
        // whole process — which the user sees as the feature silently
        // stopping right after opening it. Fail closed instead: stop
        // ourselves cleanly so at worst nothing happens, not a crash.
        try {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            screenDensity = metrics.densityDpi

            val projectionManager =
                getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = projectionManager.getMediaProjection(resultCode, resultData)
            if (projection == null) {
                stopSelf()
                return START_NOT_STICKY
            }
            mediaProjection = projection
            // Android 14+ requires a callback to be registered before the
            // first createVirtualDisplay call on a MediaProjection.
            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopSelf()
                }
            }, mainHandler)

            setUpVirtualDisplay(projection)
            showLens()
        } catch (e: Throwable) {
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun setUpVirtualDisplay(projection: MediaProjection) {
        val reader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        virtualDisplay = projection.createVirtualDisplay(
            "zh_en_dict_lens",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, mainHandler
        )
    }

    // ---- Lens overlay -----------------------------------------------------

    private fun showLens() {
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_lens, null)
        lensView = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth / 2
            y = screenHeight / 3
        }

        var downRawX = 0f
        var downRawY = 0f
        var downParamX = 0
        var downParamY = 0

        val handle = view.findViewById<ImageView>(R.id.lens_handle)
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downParamX = params.x
                    downParamY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = downParamX + (event.rawX - downRawX).toInt()
                    params.y = downParamY + (event.rawY - downRawY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Drop point = current window origin + the handle's own
                    // bottom-left offset within the padded overlay window —
                    // that's where the L's corner (its hotspot) sits, same
                    // convention as a normal cursor's pointer tip.
                    val dropX = params.x + handle.left
                    val dropY = params.y + handle.top + handle.height
                    scanAt(dropX, dropY)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(view, params)
    }

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    // ---- Capture + OCR + lookup --------------------------------------------

    private fun scanAt(x: Int, y: Int) {
        // Guards the whole capture→OCR→lookup chain: any of this throwing
        // uncaught (a bad screen size, a transient MediaProjection hiccup,
        // an ML Kit model error) would otherwise crash the whole service —
        // which looks to the user like the lens randomly stopping. On any
        // failure, or when nothing recognizable is under the drop point, we
        // simply show nothing rather than an error popup — dropping the lens
        // over a picture, blank space, etc. is a normal, expected outcome,
        // not something worth interrupting the user about.
        // Computed once per scan and prefixed on every status line below —
        // a separate notify() call right after startForeground() (fired at
        // lens-open time) turned out to get silently dropped/coalesced on
        // this device, so folding it into the single notify() a scan
        // already does is the reliable way to actually see it.
        val gps = playServicesDesc()
        try {
            val bitmap = captureCrop(x, y)
            if (bitmap == null) {
                updateNotificationStatus("Tanı[$gps]: görüntü yakalanamadı (crop null)")
                return
            }
            val image = try {
                InputImage.fromFilePath(this, bitmapToFileUri(bitmap))
            } catch (e: Throwable) {
                updateNotificationStatus("Tanı[$gps]: InputImage hatası: ${diagString(e)}")
                return
            }
            val recognizer = try {
                textRecognizer
            } catch (e: Throwable) {
                updateNotificationStatus("Tanı[$gps]: recognizer init hatası: ${diagString(e)}")
                return
            }
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    try {
                        val linePreview = visionText.textBlocks
                            .flatMap { it.lines }
                            .joinToString(" | ") { it.text }
                            .take(60)
                        val match = bestMatch(visionText, bitmap.width / 2, bitmap.height / 2)
                        if (match != null) {
                            updateNotificationStatus("Tanı[$gps]: bulundu -> ${match.optString("hanzi")}")
                            showResultEntry(x, y, match)
                        } else if (linePreview.isEmpty()) {
                            updateNotificationStatus("Tanı[$gps]: OCR hiç metin bulamadı")
                        } else {
                            updateNotificationStatus("Tanı[$gps]: OCR okudu ama sözlükte yok: $linePreview")
                        }
                    } catch (e: Throwable) {
                        updateNotificationStatus("Tanı[$gps]: eşleştirme hatası: ${diagString(e)}")
                    }
                }
                .addOnFailureListener { e ->
                    updateNotificationStatus("Tanı[$gps]: OCR hatası: ${diagString(e)}")
                }
        } catch (e: Throwable) {
            updateNotificationStatus("Tanı[$gps]: tarama hatası: ${diagString(e)}")
        }
    }

    /**
     * Short Google Play services availability code (e.g. "OK", "MISSING",
     * "UPDATE_REQUIRED", "DISABLED" — see ConnectionResult), computed fresh
     * for each scan. ML Kit's Task/callback machinery relies on Play
     * services even for the on-device, no-download recognizer, and every
     * scan NPEs deep inside ML Kit's own code identically regardless of how
     * the image is supplied — which points away from our code and toward
     * something environmental. This is the leading suspect, so it rides
     * along on every diagnostic line instead of a separate notification.
     */
    private fun playServicesDesc(): String {
        return try {
            val code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this)
            if (code == com.google.android.gms.common.ConnectionResult.SUCCESS) {
                "GPS:OK"
            } else {
                "GPS:${GoogleApiAvailability.getInstance().getErrorString(code)}($code)"
            }
        } catch (e: Throwable) {
            "GPS:err(${e.javaClass.simpleName})"
        }
    }

    /** Exception message plus the top of its stack trace, so the on-device
     *  notification can pinpoint exactly which line failed without logcat access. */
    private fun diagString(e: Throwable): String {
        val top = e.stackTrace.take(4).joinToString(" < ") {
            "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}"
        }
        return "${e.javaClass.simpleName}: ${e.message} [$top]"
    }

    /**
     * TEMPORARY diagnostic aid: updates the already-visible persistent
     * notification's text with what happened on the last scan attempt. Not a
     * new on-screen popup/warning — the notification is already shown the
     * whole time the lens is active — just repurposing its text so the
     * pipeline's behavior can be inspected (via the notification shade) on a
     * real device without logcat access. Remove once the root cause of scans
     * producing no result is found and fixed.
     */
    private fun updateNotificationStatus(text: String) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_widget_lens)
            .setContentTitle(getString(R.string.lens_notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .addAction(
                R.drawable.ic_overlay_close, getString(R.string.lens_notification_stop),
                PendingIntent.getService(
                    this, 0, Intent(this, OcrOverlayService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * A [CROP_SIZE_PX]-square region of the live screen centered on ([x], [y]),
     * read directly out of the captured frame's pixel buffer. Deliberately
     * never materializes a full-screen bitmap first (a phone screen is
     * usually several thousand by several thousand pixels — allocating and
     * discarding one of those on every single drop was almost certainly what
     * was crashing the service with an OutOfMemoryError, which looks exactly
     * like "it keeps stopping by itself").
     */
    private fun captureCrop(x: Int, y: Int): Bitmap? {
        val reader = imageReader ?: return null
        val image = try {
            reader.acquireLatestImage()
        } catch (e: Exception) {
            null
        } ?: return null
        return try {
            cropFromImage(image, x, y)
        } finally {
            image.close()
        }
    }

    private fun cropFromImage(image: Image, centerX: Int, centerY: Int): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val imgWidth = image.width
        val imgHeight = image.height

        val half = CROP_SIZE_PX / 2
        val left = (centerX - half).coerceIn(0, maxOf(0, imgWidth - CROP_SIZE_PX))
        val top = (centerY - half).coerceIn(0, maxOf(0, imgHeight - CROP_SIZE_PX))
        val width = minOf(CROP_SIZE_PX, imgWidth - left)
        val height = minOf(CROP_SIZE_PX, imgHeight - top)
        if (width <= 0 || height <= 0) return null

        val pixels = IntArray(width * height)
        val rowBytes = ByteArray(width * pixelStride)
        for (row in 0 until height) {
            buffer.position((top + row) * rowStride + left * pixelStride)
            buffer.get(rowBytes, 0, rowBytes.size)
            var srcIdx = 0
            var dstIdx = row * width
            for (col in 0 until width) {
                val r = rowBytes[srcIdx].toInt() and 0xFF
                val g = rowBytes[srcIdx + 1].toInt() and 0xFF
                val b = rowBytes[srcIdx + 2].toInt() and 0xFF
                val a = rowBytes[srcIdx + 3].toInt() and 0xFF
                pixels[dstIdx] = (a shl 24) or (r shl 16) or (g shl 8) or b
                srcIdx += pixelStride
                dstIdx++
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * ML Kit's InputImage.fromBitmap() reliably NPEs deep inside its own
     * (pre-obfuscated) code — identically whether the bitmap is our
     * hand-built crop or a freshly PNG-decoded one, which rules out the
     * bitmap's own properties as the cause. So instead of feeding it an
     * in-memory Bitmap at all, write the crop to a real file and use
     * InputImage.fromFilePath(), a different internal code path (the one
     * used whenever an image is loaded from disk), to route around
     * whatever fromBitmap()'s path is missing.
     */
    private fun bitmapToFileUri(bitmap: Bitmap): Uri {
        val file = File(cacheDir, "lens_scan.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return Uri.fromFile(file)
    }

    /**
     * Finds the OCR'd line closest to [cx],[cy] (the crop's center — i.e. the
     * drop point) and looks up the word at the corresponding character
     * position via [DictLookup], approximating that position from where the
     * center falls across the line's bounding box.
     */
    private fun bestMatch(visionText: Text, cx: Int, cy: Int): JSONObject? {
        var bestLine: Text.Line? = null
        var bestDist = Int.MAX_VALUE
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox ?: continue
                val dist = pointToRectDistance(cx, cy, box)
                if (dist < bestDist) {
                    bestDist = dist
                    bestLine = line
                }
            }
        }
        val line = bestLine ?: return null
        val text = line.text
        if (text.isEmpty()) return null
        val box = line.boundingBox ?: return null
        val fraction = if (box.width() > 0)
            ((cx - box.left).toFloat() / box.width()).coerceIn(0f, 0.999f)
        else 0f
        val tapIndex = (fraction * text.length).toInt().coerceIn(0, text.length - 1)
        return DictLookup.find(this@OcrOverlayService, text, tapIndex)
    }

    private fun pointToRectDistance(x: Int, y: Int, rect: Rect): Int {
        val dx = when {
            x < rect.left -> rect.left - x
            x > rect.right -> x - rect.right
            else -> 0
        }
        val dy = when {
            y < rect.top -> rect.top - y
            y > rect.bottom -> y - rect.bottom
            else -> 0
        }
        return dx * dx + dy * dy
    }

    // ---- Result popup -------------------------------------------------------

    private fun showResultEntry(x: Int, y: Int, entry: JSONObject) {
        showResultView(x, y) { view ->
            view.findViewById<TextView>(R.id.result_hanzi).text = entry.optString("hanzi")
            view.findViewById<TextView>(R.id.result_pinyin).text = entry.optString("pinyin")
            view.findViewById<TextView>(R.id.result_meaning).text = entry.optString("meaning")
        }
    }

    private fun showResultView(x: Int, y: Int, bind: (View) -> Unit) {
        mainHandler.post {
            dismissResult()
            val view = LayoutInflater.from(this).inflate(R.layout.overlay_result, null)
            view.findViewById<TextView>(R.id.result_pinyin).visibility = View.VISIBLE
            bind(view)
            view.setOnClickListener { dismissResult() }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                this.x = x.coerceIn(0, maxOf(0, screenWidth - RESULT_WIDTH_PX))
                this.y = (y + RESULT_Y_OFFSET_PX).coerceIn(0, maxOf(0, screenHeight - RESULT_HEIGHT_ESTIMATE_PX))
            }
            try {
                windowManager.addView(view, params)
                resultView = view
                val runnable = Runnable { dismissResult() }
                dismissResultRunnable = runnable
                mainHandler.postDelayed(runnable, RESULT_AUTO_DISMISS_MS)
            } catch (e: Exception) {
                // Overlay permission could have been revoked mid-session.
            }
        }
    }

    private fun dismissResult() {
        dismissResultRunnable?.let { mainHandler.removeCallbacks(it) }
        dismissResultRunnable = null
        resultView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { }
        }
        resultView = null
    }

    // ---- Notification ---------------------------------------------------

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, getString(R.string.widget_lens), NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val stopIntent = Intent(this, OcrOverlayService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_widget_lens)
            .setContentTitle(getString(R.string.lens_notification_title))
            .setContentText(getString(R.string.lens_notification_text))
            .setOngoing(true)
            .addAction(R.drawable.ic_overlay_close, getString(R.string.lens_notification_stop), stopPendingIntent)
            .build()
    }

    // ---- Teardown ---------------------------------------------------------

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        dismissResult()
        lensView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { }
        }
        lensView = null
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
        textRecognizer.close()
    }

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val ACTION_STOP = "com.osmankutlu.zh_en_dict.action.STOP_LENS"
        private const val CHANNEL_ID = "ocr_lens"
        private const val NOTIFICATION_ID = 4301
        private const val CROP_SIZE_PX = 480
        private const val RESULT_AUTO_DISMISS_MS = 5000L
        private const val RESULT_WIDTH_PX = 700
        private const val RESULT_HEIGHT_ESTIMATE_PX = 500
        private const val RESULT_Y_OFFSET_PX = 100

        @Volatile
        var isRunning: Boolean = false
    }
}
