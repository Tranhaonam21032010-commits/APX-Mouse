
package com.apx.mouse

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*

class MappingEditor : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 30, 20, 20)
            setBackgroundColor(Color.rgb(15, 23, 42))
        }

        val title = TextView(this).apply {
            text = "APX MOUSE - KEY MAPPING"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        root.addView(title)

        val preview = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(35, 45, 65))
        }

        val previewParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            400
        ).apply {
            topMargin = 25
        }

        root.addView(preview, previewParams)

        fun addKey(name: String, x: Int, y: Int) {
            val key = TextView(this).apply {
                text = name
                textSize = 16f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setBackgroundColor(Color.rgb(37, 99, 235))
                setPadding(15, 10, 15, 10)
            }

            val params = FrameLayout.LayoutParams(
                100, 70
            )
            params.leftMargin = x
            params.topMargin = y

            preview.addView(key, params)
        }

        addKey("W", 150, 30)
        addKey("A", 50, 110)
        addKey("S", 150, 110)
        addKey("D", 250, 110)
        addKey("SPACE", 150, 210)

        val info = TextView(this).apply {
            text = "Bản xem trước vị trí phím"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
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
