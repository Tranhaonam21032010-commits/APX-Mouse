package com.apx.mouse

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku
import kotlin.math.min

class MainActivity : Activity() {

    private data class Placement(
        val view: View,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    )

    private val placements = mutableListOf<Placement>()
    private val textSizes = mutableMapOf<TextView, Float>()
    private val backgroundViews = mutableMapOf<View, Pair<IntArray, Float>>()

    private val canvasBackground = Color.rgb(7, 12, 27)
    private val white = Color.rgb(239, 244, 255)
    private val muted = Color.rgb(157, 174, 204)
    private val blue = Color.rgb(64, 145, 255)
    private val green = Color.rgb(68, 220, 166)

    private lateinit var canvas: FrameLayout
    private lateinit var shizukuStatus: TextView
    private lateinit var shizukuDot: View
    private lateinit var shizukuButton: TextView

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_REQUEST_CODE) {
                updateShizukuStatus()
                toast(
                    if (grantResult == PackageManager.PERMISSION_GRANTED)
                        "Đã cấp quyền Shizuku"
                    else "Quyền Shizuku chưa được cấp"
                )
            }
        }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        updateShizukuStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        updateShizukuStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = canvasBackground
        window.navigationBarColor = canvasBackground

        canvas = FrameLayout(this).apply {
            setBackgroundColor(canvasBackground)
        }

        buildHub()
        setContentView(canvas)

        canvas.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            layoutCanvas()
        }
        canvas.post { layoutCanvas() }

        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        updateShizukuStatus()
    }

    private fun buildHub() {
        // Full-screen background and three game panels from the Canva Hub composition.
        addPanel(0f, 0f, 1920f, 1080f, intArrayOf(
            Color.rgb(7, 12, 27), Color.rgb(12, 24, 48), Color.rgb(5, 10, 24)
        ), 0f)

        // Header
        addText("APX MOUSE", 53f, 49f, 360f, 64f, 39f, white, true)
        addText("GAMING SPACE", 57f, 111f, 260f, 28f, 15f, muted, true)

        addPanel(1302f, 45f, 299f, 91f, intArrayOf(
            Color.rgb(22, 47, 83), Color.rgb(15, 30, 58)
        ), 18f)
        addText("+", 1334f, 62f, 52f, 54f, 34f, blue, true, Gravity.CENTER)
        val addApp = addText("Thêm app", 1409f, 68f, 177f, 35f, 22f, white, true)
        addClick(addApp) { toast("Mục Thêm app chưa được tích hợp") }

        addPanel(1599f, 45f, 253f, 91f, intArrayOf(
            Color.rgb(22, 47, 83), Color.rgb(15, 30, 58)
        ), 18f)
        addText("⚙", 1630f, 62f, 50f, 54f, 29f, blue, true, Gravity.CENTER)
        val settings = addText("Cài đặt", 1707f, 68f, 140f, 35f, 22f, white, true)
        addClick(settings) { toast("Mục Cài đặt chưa được tích hợp") }

        // Empty Game Space state. The Canva game names were examples only;
        // never show fictional or preselected apps in a fresh installation.
        addPanel(45f, 202f, 1830f, 519f, intArrayOf(
            Color.rgb(18, 38, 69), Color.rgb(8, 17, 36), Color.rgb(12, 27, 51)
        ), 24f)
        addPanel(70f, 227f, 1780f, 469f, intArrayOf(
            Color.rgb(15, 31, 57), Color.rgb(9, 19, 39)
        ), 20f)
        addText("+", 820f, 325f, 280f, 150f, 112f, blue, true, Gravity.CENTER)
        addText("CHƯA CÓ ỨNG DỤNG", 650f, 480f, 620f, 54f, 30f, white, true, Gravity.CENTER)
        addText(
            "Chọn “Thêm app” để đưa ứng dụng đã cài vào Game Space",
            460f, 542f, 1000f, 42f, 20f, muted, false, Gravity.CENTER
        )

        // System permissions / Shizuku card
        addPanel(1279f, 787f, 596f, 271f, intArrayOf(
            Color.rgb(18, 35, 65), Color.rgb(9, 19, 40)
        ), 22f)
        addText("QUYỀN HỆ THỐNG", 1403f, 828f, 360f, 42f, 25f, white, true)
        shizukuDot = View(this).apply {
            background = rounded(Color.rgb(248, 113, 113), 20f)
        }
        addAt(shizukuDot, 1400f, 878f, 15f, 15f)
        shizukuStatus = addText("Chưa kết nối", 1435f, 876f, 280f, 34f, 19f, Color.rgb(255, 177, 177), true)

        addPanel(1302f, 944f, 299f, 91f, intArrayOf(
            Color.rgb(26, 106, 130), Color.rgb(16, 62, 91)
        ), 18f)
        shizukuButton = addText("Kết nối Shizuku", 1358f, 972f, 245f, 40f, 21f, white, true, Gravity.CENTER)
        addClick(shizukuButton) { requestShizukuPermission() }

        addPanel(1599f, 966f, 253f, 70f, intArrayOf(
            Color.rgb(43, 58, 89), Color.rgb(20, 31, 55)
        ), 18f)
        val rootButton = addText("Dùng Root", 1669f, 972f, 160f, 40f, 20f, muted, true, Gravity.CENTER)
        addClick(rootButton) { toast("Chế độ Root chưa được tích hợp") }

        // Small mapping shortcut keeps the existing editor reachable without changing its logic.
        val mappingShortcut = addText("GÁN PHÍM", 55f, 1000f, 210f, 42f, 18f, muted, true)
        addClick(mappingShortcut) {
            startActivity(Intent(this, MappingEditor::class.java))
        }
    }

    private fun addPanel(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        colors: IntArray,
        radius: Float
    ): View {
        val view = View(this)
        backgroundViews[view] = Pair(colors, radius)
        view.background = gradient(colors, radius)
        addAt(view, x, y, width, height)
        return view
    }

    private fun addText(
        value: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        size: Float,
        color: Int,
        bold: Boolean = false,
        gravity: Int = Gravity.CENTER_VERTICAL
    ): TextView {
        val view = TextView(this).apply {
            text = value
            setTextColor(color)
            this.gravity = gravity
            includeFontPadding = false
            maxLines = 1
            textSize = size
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            setPadding(0, 0, 0, 0)
            isClickable = false
        }
        textSizes[view] = size
        addAt(view, x, y, width, height)
        return view
    }

    private fun addAt(view: View, x: Float, y: Float, width: Float, height: Float) {
        placements.add(Placement(view, x, y, width, height))
        canvas.addView(view, FrameLayout.LayoutParams(1, 1))
    }

    private fun addClick(view: View, action: () -> Unit) {
        view.isClickable = true
        view.isFocusable = true
        view.setOnClickListener { action() }
    }

    private fun layoutCanvas() {
        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()
        if (width <= 0f || height <= 0f) return

        val scale = min(width / 1920f, height / 1080f)
        val offsetX = (width - 1920f * scale) / 2f
        val offsetY = (height - 1080f * scale) / 2f

        placements.forEach { item ->
            val params = FrameLayout.LayoutParams(
                (item.width * scale).toInt().coerceAtLeast(1),
                (item.height * scale).toInt().coerceAtLeast(1)
            )
            params.leftMargin = (offsetX + item.x * scale).toInt()
            params.topMargin = (offsetY + item.y * scale).toInt()
            item.view.layoutParams = params
            if (item.view is TextView) {
                textSizes[item.view]?.let {
                    item.view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, it * scale)
                }
            }
            backgroundViews[item.view]?.let { (colors, radius) ->
                item.view.background = gradient(colors, radius * scale)
            }
        }
    }

    private fun gradient(colors: IntArray, radius: Float): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            colors
        ).apply {
            cornerRadius = radius
        }
    }

    private fun rounded(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }
    }

    override fun onResume() {
        super.onResume()
        updateShizukuStatus()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        super.onDestroy()
    }

    private fun requestShizukuPermission() {
        if (!Shizuku.pingBinder()) {
            toast("Shizuku chưa chạy. Hãy khởi động Shizuku trước.")
            updateShizukuStatus()
            return
        }

        when {
            Shizuku.isPreV11() -> toast("Phiên bản Shizuku quá cũ. Hãy cập nhật Shizuku.")
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED ->
                toast("APX Mouse đã có quyền Shizuku")
            else -> Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        }
    }

    private fun updateShizukuStatus() {
        if (!::shizukuStatus.isInitialized) return

        val running = Shizuku.pingBinder()
        val granted = running &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

        when {
            !running -> {
                shizukuStatus.text = "Chưa kết nối"
                shizukuStatus.setTextColor(Color.rgb(255, 177, 177))
                shizukuDot.background = rounded(Color.rgb(248, 113, 113), 20f)
                shizukuButton.text = "Kết nối Shizuku"
            }
            granted -> {
                shizukuStatus.text = "Đã kết nối và cấp quyền"
                shizukuStatus.setTextColor(green)
                shizukuDot.background = rounded(green, 20f)
                shizukuButton.text = "Đã kết nối"
            }
            else -> {
                shizukuStatus.text = "Đang chạy · Chưa cấp quyền"
                shizukuStatus.setTextColor(Color.rgb(255, 211, 128))
                shizukuDot.background = rounded(Color.rgb(251, 191, 36), 20f)
                shizukuButton.text = "Cấp quyền Shizuku"
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val SHIZUKU_REQUEST_CODE = 2301
    }
}
