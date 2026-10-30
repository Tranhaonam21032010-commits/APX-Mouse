package com.apx.mouse

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

class InputDiagnosticsActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var output: TextView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 12, 27)
        window.navigationBarColor = Color.rgb(7, 12, 27)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.rgb(7, 12, 27))
        }
        fun text(value: String, size: Float, color: Int) = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            setPadding(0, 8, 0, 8)
        }
        root.addView(text("APX INPUT DIAGNOSTICS", 20f, Color.WHITE))
        root.addView(text("Shizuku · Android 13 · Vivo Y21s", 14f, Color.LTGRAY))
        status = text("Đang kiểm tra Shizuku…", 14f, Color.YELLOW)
        root.addView(status)
        root.addView(Button(this).apply {
            text = "1. Quét thiết bị input"
            setOnClickListener { runProbe(false) }
        })
        root.addView(Button(this).apply {
            text = "2. Ghi sự kiện 5 giây (di chuyển chuột / bấm phím)"
            setOnClickListener { runProbe(true) }
        })
        val scroll = ScrollView(this)
        output = text("Kết quả chẩn đoán sẽ xuất hiện tại đây.", 12f, Color.rgb(210, 224, 245)).apply {
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        scroll.addView(output)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Button(this).apply {
            text = "Đóng"
            setOnClickListener { finish() }
        })
        setContentView(root)
        val result = DisplayScaleController.connect(this)
        status.text = result.output
        if (!result.success) output.text = "Khởi động Shizuku, cấp quyền cho APX MOUSE rồi mở lại màn hình này."
    }

    private fun runProbe(events: Boolean) {
        status.text = if (events) "Đang ghi sự kiện trong tối đa 5 giây…" else "Đang quét thiết bị…"
        executor.execute {
            val result = if (events) DisplayScaleController.captureInputEvents(5)
                else DisplayScaleController.inputInventory()
            runOnUiThread {
                status.text = if (result.success) "Hoàn tất · kiểm tra kết quả bên dưới" else "Chưa hoàn tất · ${result.output.take(180)}"
                output.text = result.output
            }
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}