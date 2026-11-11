package com.apx.mouse

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class ActivationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_ACTIVATED, false)) {
            openDashboard()
            return
        }

        window.statusBarColor = BACKGROUND
        window.navigationBarColor = BACKGROUND

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(24), dp(28), dp(24))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(BACKGROUND, Color.rgb(12, 25, 48), BACKGROUND)
            )
        }

        val title = TextView(this).apply {
            text = "APX MOUSE SECURITY"
            textSize = 25f
            setTextColor(CYAN)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        root.addView(title, matchWrap())

        val subtitle = TextView(this).apply {
            text = "Nhập key bản quyền để tiếp tục"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        root.addView(subtitle, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })

        val keyInput = EditText(this).apply {
            hint = "License Key"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 16f
            setSingleLine(true)
            backgroundTintList = android.content.res.ColorStateList.valueOf(CYAN)
        }
        root.addView(keyInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(56)
        ).apply { topMargin = dp(28) })

        val message = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(255, 110, 110))
            gravity = Gravity.CENTER
        }
        root.addView(message, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        val activate = Button(this).apply {
            text = "KÍCH HOẠT NGAY"
            setTextColor(Color.BLACK)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            backgroundTintList = android.content.res.ColorStateList.valueOf(CYAN)
            setOnClickListener {
                val enteredKey = keyInput.text?.toString().orEmpty()
                if (LicenseKeyValidator.isValid(enteredKey)) {
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                        .putBoolean(KEY_ACTIVATED, true)
                        .apply()
                    openDashboard()
                } else {
                    message.text = if (enteredKey.isBlank()) {
                        "Vui lòng nhập key trước khi kích hoạt."
                    } else {
                        "Key không hợp lệ hoặc đã hết hạn."
                    }
                }
            }
        }
        root.addView(activate, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ).apply { topMargin = dp(18) })

        setContentView(root)
    }

    private fun openDashboard() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val PREFS_NAME = "APX_Prefs"
        private const val KEY_ACTIVATED = "IS_ACTIVATED"
        private val BACKGROUND = Color.rgb(7, 12, 27)
        private val CYAN = Color.rgb(0, 229, 255)
    }
}
