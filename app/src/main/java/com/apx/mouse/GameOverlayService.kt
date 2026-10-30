package com.apx.mouse

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

class GameOverlayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var overlayRoot: FrameLayout? = null
    private var mappingRoot: FrameLayout? = null
    private var renderedMapping: String? = null
    private var fanOpen = false
    private var manuallyHidden = false
    private var gamePackage: String = ""
    private var lastForegroundPackage: String? = null
    private var lastUsageEventTime: Long = 0L
    private var gameActivityDestroyedAt: Long? = null

    private val monitor = object : Runnable {
        override fun run() {
            if (!hasUsageAccess() || !android.provider.Settings.canDrawOverlays(this@GameOverlayService)) {
                stopSelf()
                return
            }
            val foreground = currentForegroundPackage()
            val destroyedAt = gameActivityDestroyedAt
            if (destroyedAt != null && System.currentTimeMillis() - destroyedAt > 2500L) {
                stopSelf()
                return
            }
            val inGame = foreground == gamePackage
            overlayRoot?.visibility = if (inGame && !manuallyHidden) View.VISIBLE else View.GONE
            if (!inGame) setFanOpen(false)
            updateMappingOverlay(inGame)
            handler.postDelayed(this, 650L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        gamePackage = intent?.getStringExtra(EXTRA_GAME_PACKAGE).orEmpty()
        lastForegroundPackage = null
        lastUsageEventTime = System.currentTimeMillis() - 30_000L
        gameActivityDestroyedAt = null
        if (gamePackage.isBlank() || !android.provider.Settings.canDrawOverlays(this) || !hasUsageAccess()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (overlayRoot == null) createOverlay()
        handler.removeCallbacks(monitor)
        handler.post(monitor)
        return START_NOT_STICKY
    }

    private fun createOverlay() {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = FrameLayout(this)
        val params = WindowManager.LayoutParams(
            dp(224), dp(224),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(8)
            y = dp(72)
        }

        val fanItems = listOf(
            createAction("⌨", "Chỉnh sửa thao tác") { openMappingEditor() },
            createAction("–", "Ẩn overlay") {
                manuallyHidden = true
                setFanOpen(false)
                root.visibility = View.GONE
            },
            createAction("×", "Thoát") {
                stopSelf()
                startActivity(Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            }
        )
        val positions = listOf(Triple(92, 4, 48), Triple(106, 68, 48), Triple(164, 112, 48))
        fanItems.forEachIndexed { index, view ->
            val (x, y, size) = positions[index]
            root.addView(view, FrameLayout.LayoutParams(dp(size), dp(size)).apply {
                leftMargin = dp(x)
                topMargin = dp(y)
            })
            view.visibility = View.GONE
        }

        val bubble = FrameLayout(this).apply {
            background = circle(Color.rgb(17, 35, 62), dp(28).toFloat(), Color.rgb(64, 145, 255), dp(2).toFloat())
            elevation = dp(8).toFloat()
            contentDescription = "Mở menu APX MOUSE"
            isClickable = true
        }
        val icon = ImageView(this).apply {
            val appIcon = try { packageManager.getApplicationIcon(packageName) } catch (_: Exception) { null }
            if (appIcon != null) setImageDrawable(appIcon) else setImageResource(android.R.drawable.sym_def_app_icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(7), dp(7), dp(7), dp(7))
        }
        bubble.addView(icon, FrameLayout.LayoutParams(-1, -1))
        bubble.setOnClickListener { setFanOpen(!fanOpen) }
        root.addView(bubble, FrameLayout.LayoutParams(dp(52), dp(52)).apply {
            leftMargin = dp(164)
            topMargin = 0
        })

        root.visibility = View.GONE
        overlayRoot = root
        try {
            windowManager.addView(root, params)
        } catch (_: Exception) {
            overlayRoot = null
            stopSelf()
        }
    }

    private fun updateMappingOverlay(inGame: Boolean) {
        val prefs = getSharedPreferences(MAPPING_PREFS, MODE_PRIVATE)
        val visible = prefs.getBoolean(VISIBLE_PREFIX + gamePackage, true)
        val raw = prefs.getString(KEY_PREFIX + gamePackage, null)
        if (!inGame || !visible || raw.isNullOrBlank()) {
            removeMappingOverlay()
            return
        }
        if (mappingRoot != null && renderedMapping == raw) return
        removeMappingOverlay()

        try {
            val array = JSONArray(raw)
            if (array.length() == 0) return
            val density = resources.displayMetrics.density
            val screenWidth = resources.displayMetrics.widthPixels
            val screenHeight = resources.displayMetrics.heightPixels
            val layer = FrameLayout(this)
            for (i in 0 until array.length()) {
                val item: JSONObject = array.getJSONObject(i)
                val x = item.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f)
                val y = item.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f)
                val keyCode = item.optInt("key", android.view.KeyEvent.KEYCODE_UNKNOWN)
                val label = markerLabel(i, keyCode)
                val size = (38 * density).toInt()
                val marker = TextView(this).apply {
                    text = label
                    textSize = 10f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    background = circle(Color.argb(190, 8, 35, 66), size / 2f, Color.rgb(0, 190, 255), (1.5f * density).toInt().coerceAtLeast(1))
                    elevation = 5f * density
                }
                layer.addView(marker, FrameLayout.LayoutParams(size, size).apply {
                    leftMargin = (x * (screenWidth - size)).toInt()
                    topMargin = (y * (screenHeight - size)).toInt()
                })
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START }
            windowManager.addView(layer, params)
            mappingRoot = layer
            renderedMapping = raw
        } catch (_: Exception) {
            removeMappingOverlay()
        }
    }

    private fun markerLabel(index: Int, keyCode: Int): String {
        if (keyCode == android.view.KeyEvent.KEYCODE_UNKNOWN) return "${index + 1}"
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_BUTTON_1 -> "LMB"
            android.view.KeyEvent.KEYCODE_BUTTON_2 -> "RMB"
            android.view.KeyEvent.KEYCODE_BUTTON_3 -> "MMB"
            else -> android.view.KeyEvent.keyCodeToString(keyCode)
                .removePrefix("KEYCODE_").take(5)
        }
    }

    private fun removeMappingOverlay() {
        mappingRoot?.let { try { windowManager.removeView(it) } catch (_: Exception) { } }
        mappingRoot = null
        renderedMapping = null
    }

    private fun createAction(symbol: String, label: String, action: () -> Unit): View {
        val density = resources.displayMetrics.density
        val size = (48 * density).toInt()
        return TextView(this).apply {
            text = symbol
            textSize = 21f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = circle(Color.rgb(22, 45, 76), size / 2f, Color.rgb(64, 145, 255), (1.5f * density).toInt())
            elevation = 7 * density
            contentDescription = label
            isClickable = true
            setOnClickListener { action() }
        }
    }

    private fun setFanOpen(open: Boolean) {
        if (fanOpen == open) return
        fanOpen = open
        val root = overlayRoot ?: return
        for (index in 0 until minOf(3, root.childCount)) {
            val item = root.getChildAt(index)
            item.animate().cancel()
            if (open) {
                item.visibility = View.VISIBLE
                item.alpha = 0f
                item.scaleX = 0.25f
                item.scaleY = 0.25f
                item.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setStartDelay(index * 35L).setDuration(180L).start()
            } else {
                item.animate().alpha(0f).scaleX(0.25f).scaleY(0.25f)
                    .setStartDelay(0L).setDuration(120L)
                    .withEndAction { item.visibility = View.GONE }.start()
            }
        }
    }

    private fun openMappingEditor() {
        setFanOpen(false)
        startActivity(Intent(this, MappingEditor::class.java)
            .putExtra(MappingEditor.EXTRA_GAME_PACKAGE, gamePackage)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun currentForegroundPackage(): String? {
        val usage = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val from = if (lastUsageEventTime == 0L) now - 30_000L else lastUsageEventTime
        val events = usage.queryEvents(from, now)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND,
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    lastForegroundPackage = event.packageName
                    if (event.packageName == gamePackage) gameActivityDestroyedAt = null
                }
                UsageEvents.Event.ACTIVITY_DESTROYED -> {
                    if (event.packageName == gamePackage) gameActivityDestroyedAt = event.timeStamp
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND,
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    if (lastForegroundPackage == event.packageName) lastForegroundPackage = null
                }
            }
        }
        lastUsageEventTime = now
        return lastForegroundPackage
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), packageName)
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun circle(color: Int, radius: Float, stroke: Int, strokeWidth: Int) =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(strokeWidth.coerceAtLeast(1), stroke)
            cornerRadius = radius
        }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "APX Game Overlay", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun buildNotification(): Notification {
        val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("APX MOUSE đang chạy")
                .setContentText("Overlay chỉ hoạt động trong phiên game mở từ Game Space")
                .setContentIntent(pending).setOngoing(true).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("APX MOUSE đang chạy")
                .setContentText("Overlay phiên Game Space")
                .setContentIntent(pending).setOngoing(true).build()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        overlayRoot?.let { try { windowManager.removeView(it) } catch (_: Exception) { } }
        overlayRoot = null
        removeMappingOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_GAME_PACKAGE = "extra_game_package"
        private const val CHANNEL_ID = "apx_game_overlay"
        private const val NOTIFICATION_ID = 4102
        private const val MAPPING_PREFS = "apx_mouse"
        private const val KEY_PREFIX = "keymap_"
        private const val VISIBLE_PREFIX = "keymap_visible_"
    }
}
