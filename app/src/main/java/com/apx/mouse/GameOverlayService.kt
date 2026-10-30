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
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

class GameOverlayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var overlayRoot: FrameLayout? = null
    private var fanOpen = false
    private var manuallyHidden = false
    private var gamePackage: String = ""
    private var layoutParams: WindowManager.LayoutParams? = null

    private val monitor = object : Runnable {
        override fun run() {
            if (!hasUsageAccess() || !android.provider.Settings.canDrawOverlays(this@GameOverlayService)) {
                stopSelf()
                return
            }
            val foreground = currentForegroundPackage()
            val shouldShow = foreground == gamePackage && !manuallyHidden
            overlayRoot?.visibility = if (shouldShow) View.VISIBLE else View.GONE
            if (!shouldShow) setFanOpen(false)
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
        layoutParams = params

        val fanItems = listOf(
            createAction("⌨", "Gán phím") { openMappingEditor() },
            createAction("–", "Ẩn overlay") {
                manuallyHidden = true
                setFanOpen(false)
                root.visibility = View.GONE
            },
            createAction("×", "Thoát") {
                stopSelf()
                val home = Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(home)
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
            background = circle(Color.rgb(17, 35, 62), dp(28).toFloat(), Color.rgb(64, 145, 255), dp(2))
            elevation = dp(8).toFloat()
            contentDescription = "Mở menu APX MOUSE"
            isClickable = true
        }
        val icon = ImageView(this).apply {
            val appIcon = try { packageManager.getApplicationIcon(gamePackage) }
                catch (_: Exception) { null }
            if (appIcon != null) setImageDrawable(appIcon)
            else setImageResource(android.R.drawable.sym_def_app_icon)
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

    private fun createAction(symbol: String, label: String, action: () -> Unit): View {
        val density = resources.displayMetrics.density
        val size = (48 * density).toInt()
        return TextView(this).apply {
            text = symbol
            textSize = 21f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = circle(Color.rgb(22, 45, 76), size / 2f, Color.rgb(64, 145, 255), 1.5f * density)
            elevation = 7 * density
            contentDescription = label
            isClickable = true
            setOnClickListener { action() }
        }
    }

    private fun setFanOpen(open: Boolean) {
        fanOpen = open
        val root = overlayRoot ?: return
        // The three fan controls are the first three children; the game avatar is last.
        for (index in 0 until minOf(3, root.childCount)) {
            root.getChildAt(index).visibility = if (open) View.VISIBLE else View.GONE
            root.getChildAt(index).alpha = if (open) 1f else 0f
        }
    }

    private fun openMappingEditor() {
        setFanOpen(false)
        startActivity(Intent(this, MappingEditor::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun currentForegroundPackage(): String? {
        val usage = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usage.queryEvents(now - 4000L, now)
        val event = UsageEvents.Event()
        var foreground: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND,
                UsageEvents.Event.ACTIVITY_RESUMED -> foreground = event.packageName
                UsageEvents.Event.MOVE_TO_BACKGROUND,
                UsageEvents.Event.ACTIVITY_PAUSED -> if (foreground == event.packageName) foreground = null
            }
        }
        return foreground
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), packageName
            )
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun circle(color: Int, radius: Float, stroke: Int, strokeWidth: Float) =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(strokeWidth.toInt().coerceAtLeast(1), stroke)
            cornerRadius = radius
        }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "APX Game Overlay", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(): Notification {
        val pending = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("APX MOUSE đang chạy")
                .setContentText("Overlay chỉ hoạt động trong phiên game mở từ Game Space")
                .setContentIntent(pending)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("APX MOUSE đang chạy")
                .setContentText("Overlay phiên Game Space")
                .setContentIntent(pending)
                .setOngoing(true)
                .build()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        overlayRoot?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        overlayRoot = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_GAME_PACKAGE = "extra_game_package"
        private const val CHANNEL_ID = "apx_game_overlay"
        private const val NOTIFICATION_ID = 4102
    }
}
