
package com.apx.mouse

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private val bg = Color.rgb(15, 23, 42)
    private val blue = Color.rgb(37, 99, 235)
    private val white = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 35, 24, 24)
            setBackgroundColor(bg)
        }

        fun addText(
            text: String,
            size: Float,
            color: Int,
            bold: Boolean = false
        ) {
            val view = TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER_VERTICAL
                if (bold) setTypeface(null, 1)
                setPadding(12, 15, 12, 15)
            }
            root.addView(view)
        }

        addText("APX MOUSE", 28f, white, true)
        addText("GAMING CONTROL CENTER", 12f, blue)
        addText("Chào mừng đến với APX MOUSE", 18f, white)

        fun addButton(title: String, action: () -> Unit) {
            val button = TextView(this).apply {
                text = title
                textSize = 16f
                setTextColor(white)
                gravity = Gravity.CENTER
                setBackgroundColor(blue)
                setPadding(15, 20, 15, 20)
                setOnClickListener { action() }
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 10, 0, 10)
            }

            root.addView(button, params)
        }

        addButton("🎮  Quản lý game") {
            showMessage("Game Library")
        }

        addButton("⌨️  Chỉnh sửa gán phím") {
            showMessage("Key Mapping đang phát triển")
        }

        addButton("🖱️  Cài đặt chuột") {
            showMessage("Mouse Settings đang phát triển")
        }

        addButton("⚙️  Cài đặt") {
            showMessage("Settings")
        }

        setContentView(root)
    }

    private fun showMessage(message: String) {
        android.widget.Toast.makeText(
            this,
            message,
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}
