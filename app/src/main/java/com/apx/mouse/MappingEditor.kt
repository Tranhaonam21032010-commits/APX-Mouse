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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gamePackage = intent.getStringExtra(EXTRA_GAME_PACKAGE).orEmpty()
        loadMappings()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 12)
            setBackgroundColor(Color.rgb(5, 11, 23))
        }
        val title = TextView(this).apply {
            text = "CHỈNH SỬA THAO TÁC"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        root.addView(title, LinearLayout.LayoutParams(-1, 48))

        val gameLabel = TextView(this).apply {
            text = if (gamePackage.isBlank()) "Cấu hình gán phím" else "Game: $gamePackage"
            textSize = 13f
            setTextColor(Color.rgb(150, 190, 225))
            gravity = Gravity.CENTER
        }
        root.addView(gameLabel, LinearLayout.LayoutParams(-1, 30))

        status = TextView(this).apply {
            text = "Thêm điểm chạm, chọn điểm rồi nhấn phím muốn gán"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        root.addView(status, LinearLayout.LayoutParams(-1, 36))

        canvasView = MappingCanvas()
        root.addView(canvasView, LinearLayout.LayoutParams(-1, 0, 1f))

        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        tools.addView(toolButton("＋ Điểm chạm") {
            points.add(MappingPoint(0.5f, 0.5f))
            selectedIndex = points.lastIndex
            refresh()
        })
        tools.addView(toolButton("Xóa điểm") {
            if (selectedIndex in points.indices) {
                points.removeAt(selectedIndex)
                selectedIndex = -1
                refresh()
            } else toast("Chọn một điểm trước")
        })
        tools.addView(toolButton("Lưu") { saveMappings() })
        tools.addView(toolButton("Đóng") { finish() })
        root.addView(tools, LinearLayout.LayoutParams(-1, 54))

        setContentView(root)
        window.decorView.isFocusableInTouchMode = true
        window.decorView.requestFocus()
        refresh()
    }

    private fun toolButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun refresh() {
        canvasView.invalidate()
        val selected = points.getOrNull(selectedIndex)
        status.text = when {
            selected == null -> "Chạm vào điểm để chọn · Kéo điểm để đổi vị trí"
            selected.keyCode == KeyEvent.KEYCODE_UNKNOWN ->
                "Đã chọn điểm ${selectedIndex + 1} · Nhấn phím để gán"
            else -> "Điểm ${selectedIndex + 1} · Phím: ${KeyEvent.keyCodeToString(selected.keyCode)}"
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (selectedIndex in points.indices && keyCode != KeyEvent.KEYCODE_BACK) {
            points[selectedIndex].keyCode = keyCode
            refresh()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun loadMappings() {
        val raw = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString(KEY_PREFIX + gamePackage, null) ?: return
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
            Toast.makeText(this, "Không đọc được cấu hình gán phím cũ", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveMappings() {
        val array = JSONArray()
        points.forEach { point ->
            array.put(JSONObject().put("x", point.x).put("y", point.y).put("key", point.keyCode))
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_PREFIX + gamePackage, array.toString()).apply()
        toast("Đã lưu ${points.size} điểm gán phím cho game này")
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private inner class MappingCanvas : View(this@MappingEditor) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var dragging = false

        init { isFocusable = true }

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
                paint.textSize = 17f
                paint.textAlign = Paint.Align.CENTER
                val label = if (point.keyCode == KeyEvent.KEYCODE_UNKNOWN) "${index + 1}" else KeyEvent.keyCodeToString(point.keyCode).removePrefix("KEYCODE_")
                canvas.drawText(label.take(5), cx, cy + 6f, paint)
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
                    if (hit != null) {
                        selectedIndex = hit
                        dragging = true
                    } else {
                        selectedIndex = -1
                        refresh()
                    }
                    return true
                }
                MotionEvent.ACTION_MOVE -> if (dragging && selectedIndex in points.indices) {
                    points[selectedIndex].x = (event.x / width).coerceIn(0f, 1f)
                    points[selectedIndex].y = (event.y / height).coerceIn(0f, 1f)
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
    }
}
