package com.apx.mouse

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    private val bg = Color.rgb(15, 23, 42)
    private val blue = Color.rgb(37, 99, 235)
    private lateinit var statusText: TextView

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_REQUEST_CODE) {
                updateShizukuStatus()
                toast(if (grantResult == PackageManager.PERMISSION_GRANTED)
                    "Đã cấp quyền Shizuku" else "Quyền Shizuku chưa được cấp")
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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 35, 24, 24)
            setBackgroundColor(bg)
        }

        fun addText(text: String, size: Float, color: Int, bold: Boolean = false) {
            root.addView(TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER_VERTICAL
                if (bold) setTypeface(null, 1)
                setPadding(12, 15, 12, 15)
            })
        }

        fun addButton(title: String, action: () -> Unit) {
            root.addView(TextView(this).apply {
                text = title
                textSize = 16f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setBackgroundColor(blue)
                setPadding(15, 20, 15, 20)
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 10, 0, 10) })
        }

        addText("APX MOUSE", 28f, Color.WHITE, true)
        addText("GAMING CONTROL CENTER", 12f, blue)
        addText("Kết nối Shizuku qua Wireless debugging", 17f, Color.WHITE, true)

        statusText = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(12, 14, 12, 14)
        }
        root.addView(statusText)

        addButton("KIỂM TRA SHIZUKU") { updateShizukuStatus() }
        addButton("YÊU CẦU CẤP QUYỀN") { requestShizukuPermission() }
        addButton("MỞ SHIZUKU") {
            val launch = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (launch != null) startActivity(launch)
            else toast("Chưa tìm thấy ứng dụng Shizuku")
        }
        addButton("CHỈNH SỬA GÁN PHÍM") {
            startActivity(Intent(this, MappingEditor::class.java))
        }

        val probeReport = TextView(this).apply {
            text = "Chưa chạy kiểm tra thiết bị."
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(12, 18, 12, 24)
            setTextIsSelectable(true)
        }
        root.addView(probeReport)
        addButton("BETA: QUÉT CHUỘT / BÀN PHÍM") {
            probeReport.text = DeviceCapabilityProbe.run(this)
        }

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        updateShizukuStatus()
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
        statusText.text = when {
            !running -> "Trạng thái: Shizuku chưa kết nối\nBật Wireless debugging, ghép nối và Start trong Shizuku."
            granted -> "Trạng thái: Đã kết nối và được cấp quyền Shizuku."
            else -> "Trạng thái: Shizuku đang chạy, APX Mouse chưa được cấp quyền."
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val SHIZUKU_REQUEST_CODE = 2301
    }
}
