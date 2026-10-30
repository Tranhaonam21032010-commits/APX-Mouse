package com.apx.mouse

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Switch

class MouseTweakActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("apx_mouse", MODE_PRIVATE) }
    private var gamePackage = ""
    private var sensitivityX = 100
    private var sensitivityY = 100
    private var cursorStyle = 0
    private var cursorColor = 0
    private var rainbow = false
    private lateinit var valueX: TextView
    private lateinit var valueY: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gamePackage = intent.getStringExtra(EXTRA_GAME_PACKAGE).orEmpty()
        if (gamePackage.isBlank()) { finish(); return }
        val suffix = gamePackage
        sensitivityX = prefs.getInt("mouse_x_$suffix", 100)
        sensitivityY = prefs.getInt("mouse_y_$suffix", 100)
        cursorStyle = prefs.getInt("mouse_style_$suffix", 0)
        cursorColor = prefs.getInt("mouse_color_$suffix", 0)
        rainbow = prefs.getBoolean("mouse_rainbow_$suffix", false)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(5, 12, 27), Color.rgb(12, 29, 54)))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(12))
        }
        content.addView(text("TWEAK MOUSE", 25f, Color.WHITE, true))
        content.addView(text("Hồ sơ riêng cho: $gamePackage", 13f, Color.LTGRAY))
        content.addView(section("ĐỘ NHẠY CHUỘT"))
        valueX = text("Trục X: $sensitivityX%", 16f, Color.WHITE, true)
        content.addView(valueX)
        content.addView(seekBar(sensitivityX) { sensitivityX = it; valueX.text = "Trục X: $it%" })
        valueY = text("Trục Y: $sensitivityY%", 16f, Color.WHITE, true)
        content.addView(valueY)
        content.addView(seekBar(sensitivityY) { sensitivityY = it; valueY.text = "Trục Y: $it%" })

        content.addView(section("KIỂU CON TRỎ"))
        val styles = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        listOf("Mũi tên", "Chấm", "Vòng tròn").forEachIndexed { index, label ->
            styles.addView(RadioButton(this).apply {
                id = 100 + index; text = label; textSize = 14f; setTextColor(Color.WHITE)
            })
        }
        styles.check(100 + cursorStyle.coerceIn(0, 2))
        styles.setOnCheckedChangeListener { _, checked -> cursorStyle = (checked - 100).coerceIn(0, 2) }
        content.addView(styles)

        content.addView(section("MÀU CON TRỎ"))
        val colors = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        listOf("Trắng", "Xanh", "Đỏ", "Tím", "Vàng").forEachIndexed { index, label ->
            colors.addView(RadioButton(this).apply {
                id = 200 + index; text = label; textSize = 13f; setTextColor(Color.WHITE)
            })
        }
        colors.check(200 + cursorColor.coerceIn(0, 4))
        colors.setOnCheckedChangeListener { _, checked -> cursorColor = (checked - 200).coerceIn(0, 4) }

        content.addView(colors)
        content.addView(Switch(this).apply {
            text = "Rainbow tự đổi màu"; textSize = 15f; setTextColor(Color.WHITE)
            isChecked = rainbow
            setOnCheckedChangeListener { _, checked -> rainbow = checked }
        })
        content.addView(section("TÍNH NĂNG PHẦN CỨNG"))
        content.addView(text("Polling rate: chưa đọc được thông số USB HID trên thiết bị này nên chưa cho phép chỉnh.", 14f, Color.LTGRAY))
        content.addView(text("Mouse Lock: chưa hỗ trợ khóa con trỏ và chuyển chuyển động chuột thành camera input trong game. Không bật giả lập.", 14f, Color.rgb(255, 205, 130)))
        content.addView(text("Lưu ý: X/Y, kiểu và màu hiện được lưu theo từng game. Chúng chưa tác động vào chuột hệ thống cho đến khi có bộ xử lý input tương thích.", 13f, Color.LTGRAY))

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        buttons.addView(button("Hủy") { finish() })
        buttons.addView(button("Lưu cấu hình") {
            prefs.edit()
                .putInt("mouse_x_$suffix", sensitivityX)
                .putInt("mouse_y_$suffix", sensitivityY)
                .putInt("mouse_style_$suffix", cursorStyle)
                .putInt("mouse_color_$suffix", cursorColor)
                .putBoolean("mouse_rainbow_$suffix", rainbow)
                .apply()
            finish()
        })
        content.addView(buttons)
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun seekBar(value: Int, changed: (Int) -> Unit) = SeekBar(this).apply {
        max = 300
        progress = value.coerceIn(10, 300)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) changed(progress.coerceAtLeast(10))
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun section(label: String) = text(label, 16f, Color.rgb(75, 177, 255), true)
        .apply { setPadding(0, dp(18), 0, dp(8)) }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            setPadding(0, dp(5), 0, dp(5))
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 14f; setOnClickListener { action() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object { const val EXTRA_GAME_PACKAGE = "extra_game_package" }
}
