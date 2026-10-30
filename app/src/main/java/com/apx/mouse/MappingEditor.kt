package com.apx.mouse

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

class MappingEditor : Activity() {
    private data class MappingPoint(var x: Float, var y: Float, var keyCode: Int = KeyEvent.KEYCODE_UNKNOWN)

    private val points = mutableListOf<MappingPoint>()
    private var selectedIndex = -1
    private lateinit var gamePackage: String
    private lateinit var canvasView: MappingCanvas
    private lateinit var status: TextView
    private lateinit var visibilityButton: Button
    private var showMarkers = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gamePackage = intent.getStringExtra(EXTRA_GAME_PACKAGE).orEmpty()
        if (gamePackage.isBlank()) {
            Toast.makeText(this, "Không xác định được game cần cấu hình", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        loadMappings()
        showMarkers = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(VISIBLE_PREFIX + gamePackage, true)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 12, 18, 10)
            setBackgroundColor(Color.rgb(5, 11, 23))
        }
        root.addView(TextView(this).apply {
            text = "APX MOUSE  /  CHỈNH SỬA THAO TÁC"
            textSize = 21f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, 42))

        root.addView(TextView(this).apply {
            text = "Game: $gamePackage"
            textSize = 12f
            setTextColor(Color.rgb(120, 190, 235))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, 24))

        status = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        root.addView(status, LinearLayout.LayoutParams(-1, 32))

        canvasView = MappingCanvas()
        root.addView(canvasView, LinearLayout.LayoutParams(-1, 0, 1f))

        val tools = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        tools.addView(toolButton("＋ Thêm điểm") {
            points.add(MappingPoint(0.5f, 0.5f))
            selectedIndex = points.lastIndex
            refresh()
        })
        tools.addView(toolButton("Xóa điểm") {
            if (selectedIndex in points.indices) {
                points.removeAt(selectedIndex)
                selectedIndex = -1
                refresh()
            } else toast("Chọn điểm cần xóa trước")
        })
        tools.addView(toolButton("Bỏ gán phím") {
            if (selectedIndex in points.indices) {
                points[selectedIndex].keyCode = KeyEvent.KEYCODE_UNKNOWN
                refresh()
            } else toast("Chọn một điểm trước")
        })
        tools.addView(toolButton("Lưu cấu hình") { saveMappings() })
        tools.addView(toolButton("Đóng") { finish() })
        root.addView(tools, LinearLayout.LayoutParams(-1, 48))

        visibilityButton = toolButton("") { toggleMarkerVisibility() }
        root.addView(visibilityButton, LinearLayout.LayoutParams(-1, 42))
        setContentView(root)

        window.decorView.isFocusableInTouchMode = true
        window.decorView.requestFocus()
        refresh()
    }

    private fun toolButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 11f
        isAllCaps = false
        setPadding(8, 0, 8, 0)
        setOnClickListener { action() }
    }

    private fun refresh() {
        canvasView.invalidate()
        val selected = points.getOrNull(selectedIndex)
        status.text = when {
            selected == null -> "Chạm điểm để chọn · Kéo để di chuyển · Chọn điểm rồi nhấn phím/nút chuột"
            selected.keyCode == KeyEvent.KEYCODE_UNKNOWN ->
                "Điểm ${selectedIndex + 1} · Chưa gán · Nhấn phím hoặc nút chuột"
            else -> "Điểm ${selectedIndex + 1} · Đã gán: ${displayKey(selected.keyCode)}"
        }
        visibilityButton.text = if (showMarkers) "ĐANG HIỆN NÚT TRÊN GAME  ·  Nhấn để ẨN" else "ĐANG ẨN NÚT TRÊN GAME  ·  Nhấn để HIỆN"
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (selectedIndex in points.indices && keyCode != KeyEvent.KEYCODE_BACK) {
            points[selectedIndex].keyCode = keyCode
            refresh()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (selectedIndex in points.indices && event.action == MotionEvent.ACTION_BUTTON_PRESS) {
            val key = when {
                event.buttonState and MotionEvent.BUTTON_PRIMARY != 0 -> KeyEvent.KEYCODE_BUTTON_1
                event.buttonState and MotionEvent.BUTTON_SECONDARY != 0 -> KeyEvent.KEYCODE_BUTTON_2
                event.buttonState and MotionEvent.BUTTON_TERTIARY != 0 -> KeyEvent.KEYCODE_BUTTON_3
                else -> KeyEvent.KEYCODE_UNKNOWN
            }
            if (key != KeyEvent.KEYCODE_UNKNOWN) {
                points[selectedIndex].keyCode = key
                refresh()
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    private fun displayKey(code: Int): String = when (code) {
        KeyEvent.KEYCODE_BUTTON_1 -> "CHUỘT TRÁI"
        KeyEvent.KEYCODE_BUTTON_2 -> "CHUỘT PHẢI"
        KeyEvent.KEYCODE_BUTTON_3 -> "CHUỘT GIỮA"
        else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
    }

    private fun loadMappings() {
        val raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_PREFIX + gamePackage, null) ?: return
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                points.add(MappingPoint(
                    item.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f),
                    item.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f),
                    item.optInt("key", KeyEvent.KEYCODE_UNKNOWN)
                ))
            }
        } catch (_: Exception) {
            toast("Không đọc được cấu hình gán phím cũ")
        }
    }

    private fun saveMappings() {
        val array = JSONArray()
        points.forEach { point ->
            array.put(JSONObject().put("x", point.x).put("y", point.y).put("key", point.keyCode))
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_PREFIX + gamePackage, array.toString())
            .putBoolean(VISIBLE_PREFIX + gamePackage, showMarkers)
            .apply()
        toast("Đã lưu ${points.size} điểm cho game này")
    }

    private fun toggleMarkerVisibility() {
        showMarkers = !showMarkers
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(VISIBLE_PREFIX + gamePackage, showMarkers).apply()
        refresh()
        toast(if (showMarkers) "Đã bật hiển thị ký hiệu" else "Đã ẩn ký hiệu; cấu hình vẫn được giữ")
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private inner class MappingCanvas : View(this@MappingEditor) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var dragging = false

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.rgb(10, 20, 37))
            paint.color = Color.rgb(40, 68, 98)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            canvas.drawRect(2f, 2f, width - 2f, height - 2f, paint)
            points.forEachIndexed { index, point ->
                val cx = point.x * width
                val cy = point.y * height
                paint.style = Paint.Style.FILL
                paint.color = if (index == selectedIndex) Color.rgb(0, 190, 255) else Color.rgb(27, 83, 137)
                canvas.drawCircle(cx, cy, 25f, paint)
                paint.color = Color.WHITE
                paint.textSize = 14f
                paint.textAlign = Paint.Align.CENTER
                val label = if (point.keyCode == KeyEvent.KEYCODE_UNKNOWN) "${index + 1}" else displayKey(point.keyCode)
                canvas.drawText(label.take(7), cx, cy + 5f, paint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val hit = points.indices.reversed().firstOrNull { index ->
                        val dx = points[index].x * width - event.x
                        val dy = points[index].y * height - event.y
                        dx * dx + dy * dy < 55f * 55f
                    }
                    selectedIndex = hit ?: -1
                    dragging = hit != null
                    refresh()
                    return true
                }
                MotionEvent.ACTION_MOVE -> if (dragging && selectedIndex in points.indices) {
                    points[selectedIndex].x = (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    points[selectedIndex].y = (event.y / height.coerceAtLeast(1)).coerceIn(0f, 1f)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    refresh()
                    return true
                }
            }
            return true
        }
    }

    companion object {
        const val EXTRA_GAME_PACKAGE = "extra_game_package"
        private const val PREFS = "apx_mouse"
        private const val KEY_PREFIX = "keymap_"
        private const val VISIBLE_PREFIX = "keymap_visible_"
    }
}
