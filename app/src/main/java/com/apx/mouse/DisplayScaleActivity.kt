package com.apx.mouse

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class DisplayScaleActivity : Activity() {
    private lateinit var widthInput: EditText
    private lateinit var heightInput: EditText
    private lateinit var status: TextView

    private val density get() = resources.displayMetrics.density
    private fun dp(value: Int): Int = (value * density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.statusBarColor = Color.rgb(5, 12, 26)
        window.navigationBarColor = Color.rgb(5, 12, 26)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(12), dp(20), dp(14))
            setBackgroundColor(Color.rgb(5, 12, 26))
        }
        fun label(value: String, size: Float, color: Int = Color.WHITE) = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
        }

        content.addView(label("APX DISPLAY SCALE", 23f).apply {
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(-1, dp(42)))
        content.addView(label("Chỉnh độ phân giải màn hình trong phiên game", 13f, Color.LTGRAY),
            LinearLayout.LayoutParams(-1, dp(30)))
        content.addView(label("ĐỘ PHÂN GIẢI CÓ SẴN", 12f, Color.rgb(100, 185, 255)).apply {
            gravity = Gravity.CENTER_VERTICAL
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(-1, dp(30)))

        val presets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf(Triple("1920 × 1080", 1920, 1080), Triple("1600 × 900", 1600, 900),
            Triple("1280 × 720", 1280, 720), Triple("2560 × 1440", 2560, 1440)).forEach { item ->
            presets.addView(Button(this).apply {
                text = item.first
                textSize = 12f
                isAllCaps = false
                minHeight = dp(48)
                setOnClickListener {
                    widthInput.setText(item.second.toString())
                    heightInput.setText(item.third.toString())
                    status.text = "Đã chọn " + item.first
                }
            }, LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                setMargins(dp(4), dp(2), dp(4), dp(4))
            })
        }
        content.addView(presets, LinearLayout.LayoutParams(-1, dp(60)))

        content.addView(label("TÙY CHỈNH KÍCH THƯỚC", 12f, Color.rgb(100, 185, 255)).apply {
            gravity = Gravity.CENTER_VERTICAL
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(-1, dp(30)))

        val fields = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        fun numberField(hintText: String) = EditText(this).apply {
            hint = hintText
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            minHeight = dp(54)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.rgb(20, 34, 55))
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.rgb(65, 135, 205))
            }
        }
        widthInput = numberField("Chiều ngang")
        heightInput = numberField("Chiều dọc")
        fields.addView(widthInput, LinearLayout.LayoutParams(0, dp(58), 1f))
        fields.addView(label("×", 22f), LinearLayout.LayoutParams(dp(42), dp(58)))
        fields.addView(heightInput, LinearLayout.LayoutParams(0, dp(58), 1f))
        content.addView(fields, LinearLayout.LayoutParams(-1, dp(64)))

        status = label("Chọn preset hoặc nhập chiều ngang và chiều dọc", 13f, Color.rgb(140, 190, 255))
        content.addView(status, LinearLayout.LayoutParams(-1, dp(44)))

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        buttons.addView(Button(this).apply {
            text = "ÁP DỤNG"
            textSize = 14f
            isAllCaps = false
            setOnClickListener { applySize() }
        }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(5), dp(3), dp(5), dp(3)) })
        buttons.addView(Button(this).apply {
            text = "QUAY LẠI"
            textSize = 14f
            isAllCaps = false
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(5), dp(3), dp(5), dp(3)) })
        content.addView(buttons, LinearLayout.LayoutParams(-1, dp(60)))
        content.addView(label(
            "Độ phân giải sẽ được khôi phục khi phiên game kết thúc. Tính năng cần Shizuku hoạt động.",
            11f, Color.LTGRAY
        ), LinearLayout.LayoutParams(-1, dp(38)))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setBackgroundColor(Color.rgb(5, 12, 26))
            addView(content, ScrollView.LayoutParams(-1, -2))
        }
        setContentView(scroll)
    }

    private fun applySize() {
        val width = widthInput.text.toString().toIntOrNull()
        val height = heightInput.text.toString().toIntOrNull()
        if (width == null || height == null) {
            status.text = "Hãy nhập đủ chiều ngang và chiều dọc"
            return
        }
        if (width !in 320..7680 || height !in 320..7680) {
            status.text = "Kích thước hợp lệ: 320 đến 7680"
            return
        }
        val result = DisplayScaleController.apply(width, height)
        status.text = if (result.success) "Đã áp dụng " + width + " × " + height else "Lỗi: " + result.output
        Toast.makeText(this, status.text, Toast.LENGTH_LONG).show()
    }
}
