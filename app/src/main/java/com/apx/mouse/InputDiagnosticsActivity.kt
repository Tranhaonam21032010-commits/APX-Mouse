package com.apx.mouse

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class InputDiagnosticsActivity : Activity() {
    private data class InputDeviceItem(
        val path: String,
        val name: String,
        val isKeyboard: Boolean,
        val isMouse: Boolean
    ) {
        fun displayLabel(): String = buildString {
            append(name)
            append(" · ")
            append(path)
            append(" · ")
            append(
                when {
                    isKeyboard && isMouse -> "Chuột + bàn phím"
                    isKeyboard -> "Bàn phím"
                    isMouse -> "Chuột"
                    else -> "Thiết bị input khác"
                }
            )
        }
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("apx_mouse", MODE_PRIVATE) }
    private var devices: List<InputDeviceItem> = emptyList()
    private var selectedPaths = emptySet<String>()
    private lateinit var output: TextView
    private lateinit var status: TextView
    private lateinit var captureSwitch: Switch
    private lateinit var selectedSummary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 12, 27)
        window.navigationBarColor = Color.rgb(7, 12, 27)
        selectedPaths = prefs.getStringSet(KEY_INPUT_PATHS, emptySet())?.toSet() ?: emptySet()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            setBackgroundColor(Color.rgb(7, 12, 27))
        }
        fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
            TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                setPadding(0, 7, 0, 7)
                if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

        root.addView(text("APX INPUT ENGINE", 22f, Color.WHITE, true))
        root.addView(text(
            "Thiết bị: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            13f, Color.LTGRAY
        ))
        root.addView(text(
            "APX chỉ đọc thiết bị đã chọn khi game được mở ở phía trước. Không lưu nội dung phím đã nhập. Không bật khi nhập mật khẩu hoặc dữ liệu riêng tư.",
            12f, Color.rgb(255, 205, 130)
        ))
        status = text("Đang kết nối Shizuku…", 14f, Color.YELLOW)
        root.addView(status)

        root.addView(Button(this).apply {
            text = "1. QUÉT THIẾT BỊ INPUT"
            setOnClickListener { scanDevices() }
        })
        root.addView(Button(this).apply {
            text = "2. CHỌN CHUỘT / BÀN PHÍM"
            setOnClickListener { showDevicePicker() }
        })
        selectedSummary = text("", 13f, Color.rgb(150, 205, 255))
        root.addView(selectedSummary)

        captureSwitch = Switch(this).apply {
            text = "Bật đọc chuột / bàn phím trong phiên game"
            textSize = 14f
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean(KEY_INPUT_ENABLED, false)
            setOnCheckedChangeListener { _, checked ->
                if (checked && selectedPaths.isEmpty()) {
                    isChecked = false
                    Toast.makeText(
                        this@InputDiagnosticsActivity,
                        "Hãy quét và chọn ít nhất một thiết bị trước",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    prefs.edit().putBoolean(KEY_INPUT_ENABLED, checked).apply()
                    status.text = if (checked)
                        "Input Engine sẽ chỉ chạy khi game đã chọn ở phía trước."
                    else
                        "Input Engine đã tắt."
                }
            }
        }
        root.addView(captureSwitch)

        root.addView(Button(this).apply {
            text = "3. KIỂM TRA SỰ KIỆN 5 GIÂY"
            setOnClickListener { captureSelectedEvents() }
        })
        val scroll = ScrollView(this)
        output = text(
            "Quét thiết bị để xem danh sách và khả năng của từng thiết bị.",
            12f, Color.rgb(210, 224, 245)
        ).apply {
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        scroll.addView(output)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Button(this).apply {
            text = "ĐÓNG"
            setOnClickListener { finish() }
        })
        setContentView(root)
        updateSelectedSummary()

        val connection = DisplayScaleController.connect(this)
        status.text = connection.output
    }

    private fun scanDevices() {
        status.text = "Đang quét danh sách thiết bị…"
        output.text = ""
        executor.execute {
            val ready = DisplayScaleController.awaitReady(4_000L)
            val result = if (ready.success) DisplayScaleController.inputInventory() else ready
            runOnUiThread {
                status.text = if (result.success) "Quét xong. Chọn thiết bị chuột / bàn phím." else result.output
                output.text = result.output
                devices = parseDevices(result.output)
                selectedPaths = selectedPaths.intersect(devices.map { it.path }.toSet())
                saveSelection()
                updateSelectedSummary()
                if (devices.isEmpty() && result.success) {
                    status.text = "Không nhận diện được thiết bị chuột / bàn phím. Kiểm tra lại kết nối OTG / Bluetooth."
                }
            }
        }
    }

    private fun showDevicePicker() {
        if (devices.isEmpty()) {
            Toast.makeText(this, "Hãy quét thiết bị trước", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = devices.map { it.displayLabel() }.toTypedArray()
        val checked = BooleanArray(devices.size) { devices[it].path in selectedPaths }
        AlertDialog.Builder(this)
            .setTitle("Chọn thiết bị cần đọc")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val path = devices[which].path
                selectedPaths = if (isChecked) selectedPaths + path else selectedPaths - path
            }
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Lưu") { _, _ ->
                if (selectedPaths.size > 8) {
                    selectedPaths = selectedPaths.take(8).toSet()
                    Toast.makeText(this, "Tối đa 8 thiết bị được chọn", Toast.LENGTH_LONG).show()
                }
                saveSelection()
                updateSelectedSummary()
                if (selectedPaths.isEmpty() && captureSwitch.isChecked) captureSwitch.isChecked = false
            }
            .show()
    }

    private fun captureSelectedEvents() {
        val paths = selectedPaths.toTypedArray()
        if (paths.isEmpty()) {
            Toast.makeText(this, "Hãy chọn thiết bị trước", Toast.LENGTH_SHORT).show()
            return
        }
        status.text = "Đang ghi sự kiện từ các thiết bị đã chọn trong 5 giây…"
        output.text = "Di chuyển chuột hoặc bấm phím đã chọn. Không nhập dữ liệu riêng tư."
        executor.execute {
            val ready = DisplayScaleController.awaitReady(4_000L)
            val result = if (ready.success) {
                DisplayScaleController.captureInputEvents(5, paths)
            } else ready
            runOnUiThread {
                status.text = if (result.success) "Kiểm tra 5 giây hoàn tất" else result.output
                output.text = result.output
            }
        }
    }

    private fun parseDevices(raw: String): List<InputDeviceItem> {
        val result = mutableListOf<InputDeviceItem>()
        val pathPattern = Regex("""add device\s+\d+:\s+(/dev/input/event\d+)""")
        var path: String? = null
        var name = "Thiết bị không tên"
        val block = StringBuilder()

        fun flush() {
            val currentPath = path ?: return
            val description = block.toString()
            val mouse = description.contains("REL_X") && description.contains("REL_Y")
            val keyboard = description.contains("KEY_A") &&
                (description.contains("KEY_W") || description.contains("KEY_SPACE"))
            // Exclude touchscreen, volume and unrelated system input nodes by default.
            if (keyboard || mouse) result += InputDeviceItem(currentPath, name, keyboard, mouse)
        }

        raw.lineSequence().forEach { line ->
            val newPath = pathPattern.find(line)?.groupValues?.getOrNull(1)
            if (newPath != null) {
                flush()
                path = newPath
                name = "Thiết bị không tên"
                block.clear()
            }
            if (path != null) {
                block.append(line).append('\n')
                val foundName = Regex("""name:\s*"([^"]+)"""").find(line)
                if (foundName != null) name = foundName.groupValues[1]
            }
        }
        flush()
        return result.distinctBy { it.path }.sortedBy { it.path }
    }

    private fun saveSelection() {
        prefs.edit().putStringSet(KEY_INPUT_PATHS, selectedPaths).apply()
    }

    private fun updateSelectedSummary() {
        selectedSummary.text = if (selectedPaths.isEmpty()) {
            "Chưa chọn thiết bị."
        } else {
            "Đã chọn ${selectedPaths.size} thiết bị: ${selectedPaths.sorted().joinToString()}"
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val KEY_INPUT_PATHS = "input_event_paths"
        private const val KEY_INPUT_ENABLED = "input_capture_enabled"
    }
}
