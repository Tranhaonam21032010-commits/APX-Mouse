package com.apx.mouse

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MappingEditor : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 24)
            setBackgroundColor(Color.rgb(15, 23, 42))
        }

        val title = TextView(this).apply {
            text = "APX MOUSE - KEY MAPPING"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        root.addView(title)

        val info = TextView(this).apply {
            text = "Trình chỉnh sửa gán phím"
            textSize = 18f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 48, 0, 48)
        }
        root.addView(info)

        val back = Button(this).apply {
            text = "QUAY LẠI"
            setOnClickListener { finish() }
        }
        root.addView(back)

        setContentView(root)
    }
}
