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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject

class GameOverlayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var overlayRoot: FrameLayout? = null
    private var bubbleRoot: FrameLayout? = null
    private var fanOpen = false
    private var manuallyHidden = false
    private var mappingRoot: FrameLayout? = null
    private var renderedMapping: String? = null
    private var editorRoot: FrameLayout? = null
    private val editorPoints = mutableListOf<EditorPoint>()
    private var editorSelected = -1
    private var editorGamePackage = ""
    private var editorCanvas: View? = null
    private var gamePackage: String = ""
    private var lastForegroundPackage: String? = null
    private var lastUsageEventTime: Long = 0L
    private var gameActivityDestroyedAt: Long? = null
    private var outsideGameSince: Long? = null
    private var statsView: TextView? = null
    private val pingExecutor = Executors.newSingleThreadExecutor()
    private val pingInProgress = AtomicBoolean(false)
    @Volatile private var networkPingText: String = "--"
    private var lastPingStartedAt = 0L

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
            if (inGame || foreground == packageName) {
                outsideGameSince = null
            } else {
                val since = outsideGameSince ?: System.currentTimeMillis().also { outsideGameSince = it }
                if (System.currentTimeMillis() - since > 4000L) {
                    stopSelf()
                    return
                }
            }
            overlayRoot?.visibility = if (inGame && isStatsEnabled()) View.VISIBLE else View.GONE
            bubbleRoot?.visibility = if (inGame && !manuallyHidden) View.VISIBLE else View.GONE
            if (!inGame) setFanOpen(false)
            updateMappingOverlay(inGame)
            updateStats()
            handler.postDelayed(this, 1000L)
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
        outsideGameSince = null
        if (gamePackage.isBlank() || !android.provider.Settings.canDrawOverlays(this) || !hasUsageAccess()) {
            stopSelf()
            return START_NOT_STICKY
        }
        DisplayScaleController.captureOriginal(this)
        if (overlayRoot == null) createOverlay()
        if (bubbleRoot == null) createBubbleOverlay()
        handler.removeCallbacks(monitor)
        handler.post(monitor)
        return START_NOT_STICKY
    }

    private fun createOverlay() {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        // Compact horizontal status bar: battery, network ping and FPS only.
        val bar = TextView(this).apply {
            text = "PIN --%    PING --    FPS --"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(12), 0, dp(12), 0)
            background = GradientDrawable().apply {
                setColor(Color.argb(225, 8, 20, 39))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.rgb(64, 145, 255))
            }
            elevation = dp(6).toFloat()
            contentDescription = "Pin, ping và FPS"
        }

        val params = WindowManager.LayoutParams(
            dp(250), dp(40),
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

        overlayRoot = FrameLayout(this).apply {
            addView(bar, FrameLayout.LayoutParams(-1, -1))
            visibility = View.GONE
        }
        statsView = bar
        try {
            windowManager.addView(overlayRoot, params)
        } catch (_: Exception) {
            overlayRoot = null
            statsView = null
            stopSelf()
        }
    }


    private val actionOverlays = mutableListOf<View>()

    private fun createBubbleOverlay() {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        // Each interactive control gets its own small window. A large transparent
        // touchable window would steal taps from the game across its whole rectangle.
        val positions = listOf(
            Triple(92, 4, 48), Triple(28, 62, 48), Triple(8, 122, 48),
            Triple(106, 154, 48), Triple(164, 112, 48)
        )
        val actions = listOf(
            createAction("⌨", "Chỉnh sửa keymap") { openMappingEditor() },
            createAction("≋", "Bật / tắt thanh thông số") { toggleStatsOverlay() },
            createAction("–", "Ẩn bong bóng") {
                manuallyHidden = true
                setFanOpen(false)
                bubbleRoot?.visibility = View.GONE
            },
            createAction("⚙", "Chỉnh độ phân giải game") { openDisplayScale() },
            createAction("×", "Đóng overlay APX") { stopSelf() }
        )
        actions.forEachIndexed { index, view ->
            val (left, top, size) = positions[index]
            val params = WindowManager.LayoutParams(
                dp(size), dp(size), type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = dp(8 + 224 - left - size)
                y = dp(72 + top)
            }
            view.visibility = View.GONE
            try {
                windowManager.addView(view, params)
                actionOverlays.add(view)
            } catch (_: Exception) { }
        }

        val bubble = FrameLayout(this).apply {
            background = circle(Color.rgb(17, 35, 62), dp(28).toFloat(), Color.rgb(64, 145, 255), dp(2))
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
        val bubbleParams = WindowManager.LayoutParams(
            dp(52), dp(52), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(8)
            y = dp(72)
        }
        bubble.visibility = View.GONE
        bubbleRoot = bubble
        try {
            windowManager.addView(bubble, bubbleParams)
        } catch (_: Exception) {
            bubbleRoot = null
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
        actionOverlays.forEachIndexed { index, item ->
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

    private fun isStatsEnabled(): Boolean =
        getSharedPreferences(MAPPING_PREFS, MODE_PRIVATE)
            .getBoolean(STATS_VISIBLE_PREFIX + gamePackage, true)

    private fun toggleStatsOverlay() {
        val prefs = getSharedPreferences(MAPPING_PREFS, MODE_PRIVATE)
        val enabled = !isStatsEnabled()
        prefs.edit().putBoolean(STATS_VISIBLE_PREFIX + gamePackage, enabled).apply()
        overlayRoot?.visibility = if (enabled) View.VISIBLE else View.GONE
        setFanOpen(false)
    }

    private fun openDisplayScale() {
        setFanOpen(false)
        startActivity(Intent(this, DisplayScaleActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private data class EditorPoint(var x: Float, var y: Float, var keyCode: Int = KeyEvent.KEYCODE_UNKNOWN)

    private fun openMappingEditor() {
        setFanOpen(false)
        if (editorRoot != null) { closeMappingEditor(); return }
        editorGamePackage = gamePackage
        editorPoints.clear()
        editorSelected = -1
        val raw = getSharedPreferences(MAPPING_PREFS, MODE_PRIVATE).getString(KEY_PREFIX + editorGamePackage, null)
        try {
            if (!raw.isNullOrBlank()) {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    editorPoints.add(EditorPoint(item.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f),
                        item.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f),
                        item.optInt("key", KeyEvent.KEYCODE_UNKNOWN)))
                }
            }
        } catch (_: Exception) { }

        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val root = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN && editorSelected in editorPoints.indices &&
                    event.keyCode != KeyEvent.KEYCODE_BACK) {
                    editorPoints[editorSelected].keyCode = event.keyCode
                    editorCanvas?.invalidate()
                    return true
                }
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    closeMappingEditor()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            setBackgroundColor(Color.TRANSPARENT)
            isFocusable = true
            isFocusableInTouchMode = true
        }

        val canvas = object : View(this) {
            private val p = Paint(Paint.ANTI_ALIAS_FLAG)
            private var dragging = false
            override fun onDraw(c: Canvas) {
                super.onDraw(c)
                editorPoints.forEachIndexed { index, point ->
                    val cx = point.x * width
                    val cy = point.y * height
                    p.style = Paint.Style.FILL
                    p.color = if (index == editorSelected) Color.argb(225, 0, 155, 255) else Color.argb(205, 8, 35, 66)
                    c.drawCircle(cx, cy, dp(23).toFloat(), p)
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = dp(2).toFloat()
                    p.color = Color.rgb(0, 190, 255)
                    c.drawCircle(cx, cy, dp(23).toFloat(), p)
                    p.style = Paint.Style.FILL
                    p.color = Color.WHITE
                    p.textSize = dp(11).toFloat()
                    p.textAlign = Paint.Align.CENTER
                    val label = if (point.keyCode == KeyEvent.KEYCODE_UNKNOWN) "+" else editorKeyLabel(point.keyCode)
                    c.drawText(label.take(7), cx, cy + dp(4).toFloat(), p)
                }
            }
            override fun onTouchEvent(e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        val hit = editorPoints.indices.reversed().firstOrNull { i ->
                            val dx = editorPoints[i].x * width - e.x
                            val dy = editorPoints[i].y * height - e.y
                            dx * dx + dy * dy < dp(34) * dp(34).toFloat()
                        }
                        if (hit == null) {
                            editorPoints.add(EditorPoint((e.x / width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                (e.y / height.coerceAtLeast(1)).coerceIn(0f, 1f)))
                            editorSelected = editorPoints.lastIndex
                            dragging = false
                        } else { editorSelected = hit; dragging = true }
                        invalidate()
                        root.requestFocus()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> if (dragging && editorSelected in editorPoints.indices) {
                        editorPoints[editorSelected].x = (e.x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        editorPoints[editorSelected].y = (e.y / height.coerceAtLeast(1)).coerceIn(0f, 1f)
                        invalidate()
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { dragging = false; return true }
                }
                return true
            }
        }
        editorCanvas = canvas
        root.addView(canvas, FrameLayout.LayoutParams(-1, -1))

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.argb(225, 5, 16, 32))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.rgb(0, 150, 230))
            }
        }
        fun tool(label: String, action: () -> Unit) {
            toolbar.addView(TextView(this).apply {
                text = label; textSize = 12f; gravity = Gravity.CENTER
                setTextColor(Color.WHITE); setPadding(dp(9), dp(10), dp(9), dp(10))
                isClickable = true; setOnClickListener { action() }
            }, LinearLayout.LayoutParams(-2, dp(42)))
        }
        tool("＋ Thêm") {
            editorPoints.add(EditorPoint(0.5f, 0.5f)); editorSelected = editorPoints.lastIndex; canvas.invalidate()
        }
        tool("Xóa") {
            if (editorSelected in editorPoints.indices) {
                editorPoints.removeAt(editorSelected); editorSelected = -1; canvas.invalidate()
            }
        }
        tool("Bỏ gán") {
            if (editorSelected in editorPoints.indices) {
                editorPoints[editorSelected].keyCode = KeyEvent.KEYCODE_UNKNOWN; canvas.invalidate()
            }
        }
        tool("Lưu") { saveEditorMappings() }
        tool("Đóng") { closeMappingEditor() }
        root.addView(toolbar, FrameLayout.LayoutParams(-2, dp(50), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(8)
        })
        val hint = TextView(this).apply {
            text = "Chạm vị trí trong game để thêm nút · Kéo để di chuyển · Chọn nút rồi nhấn phím"
            textSize = 11f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = GradientDrawable().apply {
                setColor(Color.argb(190, 5, 16, 32)); cornerRadius = dp(8).toFloat()
            }
        }
        root.addView(hint, FrameLayout.LayoutParams(-1, dp(34), Gravity.BOTTOM))
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        try {
            windowManager.addView(root, params)
            editorRoot = root
            bubbleRoot?.visibility = View.GONE
            root.requestFocus()
        } catch (_: Exception) { editorRoot = null; editorCanvas = null }
    }

    private fun editorKeyLabel(code: Int): String = when (code) {
        KeyEvent.KEYCODE_BUTTON_1 -> "LMB"
        KeyEvent.KEYCODE_BUTTON_2 -> "RMB"
        KeyEvent.KEYCODE_BUTTON_3 -> "MMB"
        else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
    }

    private fun saveEditorMappings() {
        val array = JSONArray()
        editorPoints.forEach { point ->
            array.put(JSONObject().put("x", point.x).put("y", point.y).put("key", point.keyCode))
        }
        getSharedPreferences(MAPPING_PREFS, MODE_PRIVATE).edit()
            .putString(KEY_PREFIX + editorGamePackage, array.toString())
            .putBoolean(VISIBLE_PREFIX + editorGamePackage, true).apply()
        renderedMapping = null
        editorCanvas?.invalidate()
        android.widget.Toast.makeText(this, "Đã lưu \${editorPoints.size} nút", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun closeMappingEditor() {
        editorRoot?.let { try { windowManager.removeView(it) } catch (_: Exception) { } }
        editorRoot = null
        editorCanvas = null
        renderedMapping = null
        bubbleRoot?.visibility = View.VISIBLE
        updateMappingOverlay(true)
    }

    private fun updateStats() {
        val batteryIntent = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        val batteryText = if (percent >= 0) "$percent%" else "--"
        startNetworkPingIfDue()
        statsView?.text = "PIN $batteryText    PING $networkPingText    FPS --"
    }

    private fun startNetworkPingIfDue() {
        val now = System.currentTimeMillis()
        if (now - lastPingStartedAt < 5000L || !pingInProgress.compareAndSet(false, true)) return
        lastPingStartedAt = now
        pingExecutor.execute {
            var result = "--"
            try {
                // General Internet latency to Cloudflare DNS, not game-server RTT.
                val process = ProcessBuilder("ping", "-c", "1", "-W", "2", "1.1.1.1").redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().use { it.readText() }
                val marker = "time="
                val pos = output.indexOf(marker)
                if (pos >= 0) {
                    val number = output.substring(pos + marker.length).takeWhile { it.isDigit() || it == '.' }
                    val value = number.toDoubleOrNull()
                    if (value != null) result = value.toInt().toString() + "ms"
                }
                process.destroy()
            } catch (_: Exception) {
                result = "--"
            } finally {
                networkPingText = result
                pingInProgress.set(false)
            }
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
        pingExecutor.shutdownNow()
        overlayRoot?.let { try { windowManager.removeView(it) } catch (_: Exception) { } }
        bubbleRoot?.let { try { windowManager.removeView(it) } catch (_: Exception) { } }
        actionOverlays.forEach { try { windowManager.removeView(it) } catch (_: Exception) { } }
        actionOverlays.clear()
        overlayRoot = null
        bubbleRoot = null
        statsView = null
        removeMappingOverlay()
        closeMappingEditor()
        DisplayScaleController.restore(this)
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
        private const val STATS_VISIBLE_PREFIX = "stats_visible_"
    }
}
