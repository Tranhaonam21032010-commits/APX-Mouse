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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    private val background = Color.rgb(10, 15, 28)
    private val sidebarColor = Color.rgb(14, 22, 39)
    private val panelColor = Color.rgb(20, 30, 51)
    private val blue = Color.rgb(59, 130, 246)
    private val muted = Color.rgb(151, 165, 190)
    private val white = Color.rgb(240, 245, 255)
    private val green = Color.rgb(52, 211, 153)

    private lateinit var statusText: TextView
    private lateinit var statusPill: TextView
    private lateinit var statusDot: View

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
        window.statusBarColor = background
        window.navigationBarColor = background

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(background)
        }

        // Left navigation rail
        val sidebar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(20))
            setBackgroundColor(sidebarColor)
        }
        root.addView(
            sidebar,
            LinearLayout.LayoutParams(dp(218), LinearLayout.LayoutParams.MATCH_PARENT)
        )

        val brandMark = TextView(this).apply {
            text = "APX"
            textSize = 25f
            setTextColor(white)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            background = rounded(blue, 14)
        }
        sidebar.addView(brandMark, LinearLayout.LayoutParams(dp(54), dp(48)))

        val brandName = text("APX MOUSE", 17f, white, true)
        brandName.setPadding(0, dp(15), 0, 0)
        sidebar.addView(brandName)

        val brandSub = text("GAMING CONTROL", 10f, muted, true)
        brandSub.setPadding(0, dp(4), 0, dp(30))
        sidebar.addView(brandSub)

        sidebar.addView(text("WORKSPACE", 10f, muted, true).apply {
            setPadding(dp(4), 0, 0, dp(10))
        })

        sidebar.addView(navItem("⌂", "Hub", true))
        sidebar.addView(navItem("⌘", "Gán phím", false) {
            startActivity(Intent(this, MappingEditor::class.java))
        })

        val spacer = View(this)
        sidebar.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))

        val sideStatus = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(panelColor, 14)
        }
        sideStatus.addView(text("SYSTEM STATUS", 10f, muted, true))
        sideStatus.addView(text("APX MOUSE  •  v1.1", 11f, white, true).apply {
            setPadding(0, dp(7), 0, 0)
        })
        sidebar.addView(sideStatus)

        // Main workspace
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        root.addView(
            scroll,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        )

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(22), dp(28), dp(26))
        }
        scroll.addView(content)

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val headingGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        headingGroup.addView(text("Control Center", 25f, white, true))
        headingGroup.addView(text("Quản lý kết nối và cấu hình APX MOUSE", 12f, muted).apply {
            setPadding(0, dp(5), 0, 0)
        })
        topBar.addView(headingGroup, LinearLayout.LayoutParams(0, -2, 1f))
        topBar.addView(text("LANDSCAPE  /  HUB", 10f, blue, true).apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(Color.rgb(22, 45, 82), 20)
        })
        content.addView(topBar)

        val welcome = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = rounded(Color.rgb(22, 43, 76), 18)
        }
        welcome.addView(text("WELCOME TO APX MOUSE", 10f, Color.rgb(147, 197, 253), true))
        welcome.addView(text("Your setup. Your control.", 22f, white, true).apply {
            setPadding(0, dp(7), 0, dp(5))
        })
        welcome.addView(text("Truy cập nhanh các công cụ điều khiển và thiết lập thiết bị.", 12f, muted))
        val welcomeParams = LinearLayout.LayoutParams(-1, -2)
        welcomeParams.topMargin = dp(20)
        content.addView(welcome, welcomeParams)

        // Connection status card
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = rounded(panelColor, 16)
        }
        val statusHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        statusHeader.addView(text("SHIZUKU CONNECTION", 11f, muted, true), LinearLayout.LayoutParams(0, -2, 1f))
        statusPill = text("ĐANG KIỂM TRA", 10f, white, true).apply {
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = rounded(Color.rgb(55, 65, 81), 18)
        }
        statusHeader.addView(statusPill)
        statusCard.addView(statusHeader)

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, dp(8))
        }
        statusDot = View(this).apply {
            background = rounded(green, 20)
        }
        statusRow.addView(statusDot, LinearLayout.LayoutParams(dp(10), dp(10)))
        statusText = text("Đang đọc trạng thái Shizuku...", 13f, white, true).apply {
            setPadding(dp(10), 0, 0, 0)
        }
        statusRow.addView(statusText)
        statusCard.addView(statusRow)
        statusCard.addView(text(
            "Bật Wireless debugging, ghép nối và khởi động Shizuku nếu trạng thái chưa kết nối.",
            11f, muted
        ))
        val statusActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        statusActions.addView(actionButton("KIỂM TRA LẠI", blue) {
            updateShizukuStatus()
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        statusActions.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
        statusActions.addView(actionButton("CẤP QUYỀN", Color.rgb(37, 99, 235)) {
            requestShizukuPermission()
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        val actionParams = LinearLayout.LayoutParams(-1, -2)
        actionParams.topMargin = dp(16)
        statusCard.addView(statusActions, actionParams)

        val statusParams = LinearLayout.LayoutParams(-1, -2)
        statusParams.topMargin = dp(16)
        content.addView(statusCard, statusParams)

        // Feature cards
        content.addView(text("QUICK ACCESS", 11f, muted, true).apply {
            setPadding(0, dp(22), 0, dp(10))
        })

        val cardsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        cardsRow.addView(featureCard(
            "01", "Key Mapping", "Mở trình chỉnh sửa và xem trước bố cục gán phím.",
            "MỞ TRÌNH CHỈNH SỬA"
        ) {
            startActivity(Intent(this, MappingEditor::class.java))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        cardsRow.addView(View(this), LinearLayout.LayoutParams(dp(12), 1))
        cardsRow.addView(featureCard(
            "02", "Shizuku Manager", "Mở ứng dụng Shizuku để kiểm tra dịch vụ và kết nối.",
            "MỞ SHIZUKU"
        ) {
            val launch = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (launch != null) startActivity(launch)
            else toast("Chưa tìm thấy ứng dụng Shizuku")
        }, LinearLayout.LayoutParams(0, -1, 1f))
        content.addView(cardsRow, LinearLayout.LayoutParams(-1, -2))

        content.addView(text("APX MOUSE  •  GAMING CONTROL CENTER", 10f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, 0)
        })

        setContentView(root)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        updateShizukuStatus()
    }

    private fun navItem(
        symbol: String,
        label: String,
        selected: Boolean,
        action: (() -> Unit)? = null
    ): TextView {
        return TextView(this).apply {
            text = "$symbol    $label"
            textSize = 13f
            setTextColor(if (selected) white else muted)
            typeface = Typeface.create("sans-serif", if (selected) Typeface.BOLD else Typeface.NORMAL)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), 0, dp(10), 0)
            background = rounded(
                if (selected) Color.rgb(28, 58, 102) else Color.TRANSPARENT,
                11
            )
            val params = LinearLayout.LayoutParams(-1, dp(44))
            params.bottomMargin = dp(7)
            layoutParams = params
            isClickable = action != null
            isFocusable = action != null
            setOnClickListener { action?.invoke() }
        }
    }

    private fun featureCard(
        number: String,
        title: String,
        description: String,
        buttonLabel: String,
        action: () -> Unit
    ): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = rounded(panelColor, 16)
        }
        card.addView(text(number, 11f, blue, true))
        card.addView(text(title, 17f, white, true).apply {
            setPadding(0, dp(8), 0, dp(6))
        })
        card.addView(text(description, 11f, muted))
        val button = actionButton(buttonLabel, Color.rgb(35, 70, 120), action)
        val params = LinearLayout.LayoutParams(-1, dp(40))
        params.topMargin = dp(14)
        card.addView(button, params)
        return card
    }

    private fun actionButton(label: String, color: Int, action: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 10f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(10), 0)
            background = rounded(color, 10)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
    }

    private fun text(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

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
            toast("Shizuku chưa chạy. Hãy bật Wireless debugging và khởi động Shizuku trước.")
            updateShizukuStatus()
            return
        }

        when {
            Shizuku.isPreV11() -> toast("Phiên bản Shizuku quá cũ. Hãy cập nhật Shizuku.")
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED ->
                toast("APX Mouse đã có quyền Shizuku")
            Shizuku.shouldShowRequestPermissionRationale() ->
                Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
            else -> Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        }
    }

    private fun updateShizukuStatus() {
        if (!::statusText.isInitialized) return
        val running = Shizuku.pingBinder()
        val granted = running &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

        when {
            !running -> {
                statusPill.text = "CHƯA KẾT NỐI"
                statusPill.background = rounded(Color.rgb(127, 50, 55), 18)
                statusDot.background = rounded(Color.rgb(248, 113, 113), 20)
                statusText.text = "Shizuku chưa kết nối"
            }
            granted -> {
                statusPill.text = "ĐÃ CẤP QUYỀN"
                statusPill.background = rounded(Color.rgb(18, 95, 73), 18)
                statusDot.background = rounded(green, 20)
                statusText.text = "Đã kết nối và được cấp quyền Shizuku"
            }
            else -> {
                statusPill.text = "CHỜ CẤP QUYỀN"
                statusPill.background = rounded(Color.rgb(117, 83, 31), 18)
                statusDot.background = rounded(Color.rgb(251, 191, 36), 20)
                statusText.text = "Shizuku đang chạy, APX Mouse chưa được cấp quyền"
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
