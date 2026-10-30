package com.apx.mouse

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
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
    private data class Placement(val view: View, val x: Float, val y: Float, val width: Float, val height: Float)

    private val placements = mutableListOf<Placement>()
    private val textSizes = mutableMapOf<TextView, Float>()
    private val backgroundViews = mutableMapOf<View, Pair<IntArray, Float>>()
    private val dynamicViews = mutableListOf<View>()
    private val emptyStateViews = mutableListOf<View>()

    private val canvasBackground = Color.rgb(7, 12, 27)
    private val white = Color.rgb(239, 244, 255)
    private val muted = Color.rgb(157, 174, 204)
    private val blue = Color.rgb(64, 145, 255)
    private val green = Color.rgb(68, 220, 166)

    private lateinit var canvas: FrameLayout
    private lateinit var shizukuStatus: TextView
    private lateinit var shizukuDot: View
    private lateinit var shizukuButton: TextView

    private val prefs by lazy { getSharedPreferences("apx_mouse", MODE_PRIVATE) }
    private val selectedPackages by lazy {
        prefs.getStringSet(KEY_SELECTED_APPS, emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_REQUEST_CODE) {
                updateShizukuStatus()
                toast(if (grantResult == PackageManager.PERMISSION_GRANTED)
                    "Đã cấp quyền Shizuku" else "Quyền Shizuku chưa được cấp")
            }
        }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { updateShizukuStatus() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { updateShizukuStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = canvasBackground
        window.navigationBarColor = canvasBackground
        canvas = FrameLayout(this).apply { setBackgroundColor(canvasBackground) }
        buildHub()
        setContentView(canvas)
        canvas.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> layoutCanvas() }
        canvas.post { layoutCanvas() }
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        updateShizukuStatus()
    }

    private fun buildHub() {
        // Hub layout follows Canva page 1 (1920 x 1080).
        addPanel(0f, 0f, 1920f, 1080f,
            intArrayOf(Color.rgb(5, 9, 22), Color.rgb(10, 20, 42), Color.rgb(4, 8, 19)), 0f)

        addText("APX MOUSE", 53f, 49f, 360f, 64f, 39f, white, true)
        addText("GAMING SPACE", 57f, 111f, 260f, 28f, 15f, muted, true)

        // Top actions
        addPanel(1302f, 45f, 299f, 91f, intArrayOf(Color.rgb(20, 48, 86), Color.rgb(12, 27, 53)), 18f)
        addText("+", 1334f, 62f, 52f, 54f, 34f, blue, true, Gravity.CENTER)
        addClick(addText("Thêm app", 1409f, 68f, 177f, 35f, 22f, white, true)) { showAppPicker() }
        addPanel(1599f, 45f, 253f, 91f, intArrayOf(Color.rgb(20, 48, 86), Color.rgb(12, 27, 53)), 18f)
        addText("⚙", 1630f, 62f, 50f, 54f, 29f, blue, true, Gravity.CENTER)
        addClick(addText("Cài đặt", 1707f, 68f, 140f, 35f, 22f, white, true)) { showSettings() }

        // Left navigation / library panel
        addPanel(45f, 202f, 458f, 519f,
            intArrayOf(Color.rgb(17, 35, 65), Color.rgb(8, 16, 34)), 24f)
        addText("GAME SPACE", 88f, 245f, 340f, 42f, 23f, white, true)
        addText("THƯ VIỆN ỨNG DỤNG", 88f, 307f, 340f, 30f, 15f, muted, true)
        addText("Ứng dụng đã thêm", 88f, 374f, 300f, 34f, 20f, white, true)
        addText("Chọn ứng dụng bằng nút", 88f, 425f, 340f, 30f, 17f, muted, false)
        addText("“Thêm app” ở góc trên.", 88f, 460f, 340f, 30f, 17f, muted, false)
        addPanel(86f, 548f, 365f, 102f, intArrayOf(Color.rgb(20, 52, 91), Color.rgb(11, 28, 54)), 16f)
        addText("GÁN PHÍM", 111f, 582f, 300f, 36f, 21f, blue, true)
        addClick(addText("Mở trình chỉnh sửa", 111f, 618f, 300f, 25f, 15f, muted, false)) {
            startActivity(Intent(this, MappingEditor::class.java))
        }

        // Main game stage
        addPanel(434f, 90f, 1029f, 766f,
            intArrayOf(Color.rgb(18, 39, 72), Color.rgb(7, 15, 32), Color.rgb(12, 27, 52)), 28f)
        addText("TRÒ CHƠI CỦA BẠN", 490f, 133f, 620f, 42f, 25f, white, true)
        addText("Chọn game trong thư viện để mở nhanh", 492f, 180f, 700f, 30f, 17f, muted, false)

        emptyStateViews += addText("+", 805f, 385f, 280f, 130f, 100f, blue, true, Gravity.CENTER)
        emptyStateViews += addText("CHƯA CÓ ỨNG DỤNG", 650f, 530f, 620f, 48f, 27f, white, true, Gravity.CENTER)
        emptyStateViews += addText("Nhấn “Thêm app” để bắt đầu tạo Game Space",
            570f, 584f, 760f, 36f, 18f, muted, false, Gravity.CENTER)

        // System access panel in the lower-right, matching the Canva composition.
        addPanel(1096f, 832f, 824f, 248f,
            intArrayOf(Color.rgb(15, 32, 60), Color.rgb(7, 15, 32)), 22f)
        addText("QUYỀN HỆ THỐNG", 1279f, 858f, 440f, 38f, 24f, white, true)
        shizukuDot = View(this).apply { background = rounded(Color.rgb(248, 113, 113), 20f) }
        addAt(shizukuDot, 1282f, 914f, 15f, 15f)
        shizukuStatus = addText("Chưa kết nối", 1317f, 908f, 420f, 34f, 19f, Color.rgb(255, 177, 177), true)

        addPanel(1279f, 966f, 321f, 92f,
            intArrayOf(Color.rgb(24, 105, 133), Color.rgb(12, 54, 83)), 18f)
        shizukuButton = addText("Kết nối Shizuku", 1310f, 991f, 260f, 40f, 20f, white, true, Gravity.CENTER)
        addClick(shizukuButton) { requestShizukuPermission() }

        addPanel(1622f, 966f, 207f, 92f,
            intArrayOf(Color.rgb(37, 53, 82), Color.rgb(17, 28, 50)), 18f)
        addClick(addText("Dùng Root", 1644f, 991f, 164f, 40f, 19f, muted, true, Gravity.CENTER)) {
            toast("Chế độ Root chưa được tích hợp")
        }
        renderGameSpace()
    }

    private fun showAppPicker() {
        val apps = getLaunchableApps()
        if (apps.isEmpty()) {
            toast("Không tìm thấy ứng dụng có thể mở")
            return
        }
        val labels = apps.map { it.loadLabel(packageManager).toString() }.toTypedArray()
        val stagedPackages = selectedPackages.toMutableSet()
        val checked = BooleanArray(apps.size) { apps[it].activityInfo.packageName in stagedPackages }
        AlertDialog.Builder(this)
            .setTitle("Thêm ứng dụng vào Game Space")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val packageName = apps[which].activityInfo.packageName
                if (isChecked) stagedPackages.add(packageName) else stagedPackages.remove(packageName)
            }
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Lưu") { _, _ ->
                selectedPackages.clear()
                selectedPackages.addAll(stagedPackages)
                prefs.edit().putStringSet(KEY_SELECTED_APPS, selectedPackages.toSet()).apply()
                renderGameSpace()
                toast("Đã cập nhật Game Space")
            }
            .show()
    }

    private fun getLaunchableApps(): List<ResolveInfo> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcherIntent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .filter {
                prefs.getBoolean(KEY_SHOW_SYSTEM_APPS, false) ||
                    (it.activityInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0
            }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
    }

    private fun renderGameSpace() {
        dynamicViews.forEach { view ->
            canvas.removeView(view)
            placements.removeAll { it.view === view }
            if (view is TextView) textSizes.remove(view)
            backgroundViews.remove(view)
        }
        dynamicViews.clear()

        val packages = selectedPackages.filter { packageManager.getLaunchIntentForPackage(it) != null }.sorted()
        emptyStateViews.forEach { it.visibility = if (packages.isEmpty()) View.VISIBLE else View.GONE }

        packages.take(6).forEachIndexed { index, packageName ->
            val info = packageManager.getApplicationInfo(packageName, 0)
            val label = packageManager.getApplicationLabel(info).toString()
            val col = index % 2
            val row = index / 2
            val x = 520f + col * 450f
            val y = 250f + row * 190f
            addDynamicPanel(x, y, 405f, 154f,
                intArrayOf(Color.rgb(25, 55, 94), Color.rgb(10, 24, 47)), 20f)
            val name = addDynamicText(label, x + 24f, y + 28f, 350f, 48f, 23f, white, true)
            addClick(name) { launchSelectedApp(packageName) }
            val open = addDynamicText("MỞ ỨNG DỤNG", x + 24f, y + 96f, 350f, 30f, 16f, blue, true)
            addClick(open) { launchSelectedApp(packageName) }
            name.setOnLongClickListener {
                confirmRemoveApp(packageName, label)
                true
            }
            open.setOnLongClickListener {
                confirmRemoveApp(packageName, label)
                true
            }
        }
        if (packages.size > 6) {
            val more = addDynamicText("Còn ${packages.size - 6} ứng dụng trong thư viện",
                520f, 830f, 700f, 28f, 16f, muted, false)
            more.isClickable = false
        }
        layoutCanvas()
    }

    private fun confirmRemoveApp(packageName: String, label: String) {
        AlertDialog.Builder(this)
            .setTitle("Xóa khỏi Game Space?")
            .setMessage("Ứng dụng $label vẫn được giữ trên điện thoại.")
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Xóa") { _, _ ->
                selectedPackages.remove(packageName)
                prefs.edit().putStringSet(KEY_SELECTED_APPS, selectedPackages.toSet()).apply()
                renderGameSpace()
            }
            .show()
    }

    private fun launchSelectedApp(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            selectedPackages.remove(packageName)
            prefs.edit().putStringSet(KEY_SELECTED_APPS, selectedPackages.toSet()).apply()
            renderGameSpace()
            toast("Ứng dụng không còn khả dụng")
        } else startActivity(launchIntent)
    }

    private fun showSettings() {
        val options = arrayOf(
            "Hiện ứng dụng hệ thống trong danh sách Thêm app",
            "Xóa toàn bộ ứng dụng khỏi Game Space",
            "Thông tin APX MOUSE"
        )
        AlertDialog.Builder(this)
            .setTitle("Cài đặt")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val currently = prefs.getBoolean(KEY_SHOW_SYSTEM_APPS, false)
                        prefs.edit().putBoolean(KEY_SHOW_SYSTEM_APPS, !currently).apply()
                        toast(if (!currently) "Đã bật hiển thị ứng dụng hệ thống" else "Đã ẩn ứng dụng hệ thống")
                    }
                    1 -> AlertDialog.Builder(this)
                        .setTitle("Xóa danh sách Game Space?")
                        .setMessage("Thao tác này chỉ xóa danh sách trong APX MOUSE, không gỡ ứng dụng khỏi điện thoại.")
                        .setNegativeButton("Hủy", null)
                        .setPositiveButton("Xóa") { _, _ ->
                            selectedPackages.clear()
                            prefs.edit().putStringSet(KEY_SELECTED_APPS, emptySet()).apply()
                            renderGameSpace()
                        }.show()
                    2 -> {
                        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: "Không rõ"
                        AlertDialog.Builder(this).setTitle("APX MOUSE")
                            .setMessage("Phiên bản $version\nGame Space lưu danh sách ứng dụng trên thiết bị.")
                            .setPositiveButton("Đóng", null).show()
                    }
                }
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun addPanel(x: Float, y: Float, width: Float, height: Float, colors: IntArray, radius: Float): View {
        val view = View(this)
        backgroundViews[view] = Pair(colors, radius)
        view.background = gradient(colors, radius)
        addAt(view, x, y, width, height)
        return view
    }

    private fun addText(
        value: String, x: Float, y: Float, width: Float, height: Float, size: Float,
        color: Int, bold: Boolean = false, gravity: Int = Gravity.CENTER_VERTICAL
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

    private fun addDynamicPanel(x: Float, y: Float, width: Float, height: Float, colors: IntArray, radius: Float) {
        val view = View(this)
        dynamicViews += view
        backgroundViews[view] = Pair(colors, radius)
        view.background = gradient(colors, radius)
        addAt(view, x, y, width, height)
    }

    private fun addDynamicText(
        value: String, x: Float, y: Float, width: Float, height: Float, size: Float,
        color: Int, bold: Boolean
    ): TextView {
        val view = addText(value, x, y, width, height, size, color, bold)
        dynamicViews += view
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
        if (!::canvas.isInitialized) return
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
                textSizes[item.view]?.let { item.view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, it * scale) }
            }
            backgroundViews[item.view]?.let { (colors, radius) ->
                item.view.background = gradient(colors, radius * scale)
            }
        }
    }

    private fun gradient(colors: IntArray, radius: Float) =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply { cornerRadius = radius }

    private fun rounded(color: Int, radius: Float) =
        GradientDrawable().apply { setColor(color); cornerRadius = radius }

    override fun onResume() {
        super.onResume()
        updateShizukuStatus()
        if (::canvas.isInitialized) renderGameSpace()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        super.onDestroy()
    }

    private fun requestShizukuPermission() {
        if (!Shizuku.pingBinder()) {
            val managerIntent = packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            if (managerIntent != null) {
                toast("Mở Shizuku để khởi động dịch vụ, sau đó quay lại APX MOUSE")
                startActivity(managerIntent)
            } else {
                toast("Chưa tìm thấy Shizuku. Hãy cài và khởi động Shizuku trước.")
            }
            updateShizukuStatus()
            return
        }
        when {
            Shizuku.isPreV11() -> toast("Phiên bản Shizuku quá cũ. Hãy cập nhật Shizuku.")
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED ->
                toast("APX MOUSE đã có quyền Shizuku")
            else -> Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        }
    }

    private fun updateShizukuStatus() {
        if (!::shizukuStatus.isInitialized) return
        val running = Shizuku.pingBinder()
        val granted = running && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        when {
            !running -> {
                shizukuStatus.text = "Chưa kết nối"
                shizukuStatus.setTextColor(Color.rgb(255, 177, 177))
                shizukuDot.background = rounded(Color.rgb(248, 113, 113), 20f)
                shizukuButton.text = "Mở Shizuku"
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

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    companion object {
        private const val SHIZUKU_REQUEST_CODE = 2301
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val KEY_SELECTED_APPS = "selected_apps"
        private const val KEY_SHOW_SYSTEM_APPS = "show_system_apps"
    }
}
