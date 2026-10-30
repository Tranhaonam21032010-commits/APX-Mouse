package com.apx.mouse

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class DisplayScaleActivity : Activity() {
    private lateinit var widthInput: EditText
    private lateinit var heightInput: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.statusBarColor = Color.rgb(5, 12, 26)
        window.navigationBarColor = Color.rgb(5, 12, 26)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 16, 28, 16)
            setBackgroundColor(Color.rgb(5, 12, 26))
        }
        fun label(value: String, size: Float, color: Int = Color.WHITE) = TextView(this).apply {
            text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER
        }
        root.addView(label("APX DISPLAY SCALE", 24f), LinearLayout.LayoutParams(-1, 44))
        root.addView(label("Chỉnh độ phân giải màn hình trong phiên game", 14f, Color.LTGRAY),
            LinearLayout.LayoutParams(-1, 32))

        val presets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf(Triple("1920 × 1080", 1920, 1080), Triple("1600 × 900", 1600, 900),
            Triple("1280 × 720", 1280, 720), Triple("2560 × 1440", 2560, 1440)).forEach { item ->
            presets.addView(Button(this).apply {
                text = item.first
                setOnClickListener { widthInput.setText(item.second.toString()); heightInput.setText(item.third.toString()) }
            }, LinearLayout.LayoutParams(0, 58, 1f).apply { setMargins(5, 4, 5, 4) })
        }
        root.addView(presets, LinearLayout.LayoutParams(-1, 66))

        val fields = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        widthInput = EditText(this).apply {
            hint = "Chiều ngang"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER; setSingleLine()
        }
        heightInput = EditText(this).apply {
            hint = "Chiều dọc"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER; setSingleLine()
        }
        fields.addView(widthInput, LinearLayout.LayoutParams(280, 56).apply { setMargins(8, 4, 8, 4) })
        fields.addView(label("×", 22f), LinearLayout.LayoutParams(42, 56))
        fields.addView(heightInput, LinearLayout.LayoutParams(280, 56).apply { setMargins(8, 4, 8, 4) })
        root.addView(fields)

        status = label("Chọn độ phân giải hoặc tự nhập chiều ngang và chiều dọc", 13f, Color.rgb(140, 190, 255))
        root.addView(status, LinearLayout.LayoutParams(-1, 46))
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        buttons.addView(Button(this).apply { text = "ÁP DỤNG"; setOnClickListener { applySize() } },
            LinearLayout.LayoutParams(220, 56).apply { setMargins(8, 6, 8, 6) })
        buttons.addView(Button(this).apply { text = "QUAY LẠI"; setOnClickListener { finish() } },
            LinearLayout.LayoutParams(180, 56).apply { setMargins(8, 6, 8, 6) })
        root.addView(buttons)
        root.addView(label("Màn hình sẽ tự khôi phục khi phiên game kết thúc. Cần cấp quyền Shizuku.", 12f, Color.LTGRAY),
            LinearLayout.LayoutParams(-1, 40))
        setContentView(root)
    }

    private fun applySize() {
        val width = widthInput.text.toString().toIntOrNull()
        val height = heightInput.text.toString().toIntOrNull()
        if (width == null || height == null) { status.text = "Hãy nhập đủ chiều ngang và chiều dọc"; return }
        val result = DisplayScaleController.apply(width, height)
        status.text = if (result.success) "Đã áp dụng ${width} × ${height}" else "Lỗi: ${result.output}"
        Toast.makeText(this, status.text, Toast.LENGTH_LONG).show()
    }
}
