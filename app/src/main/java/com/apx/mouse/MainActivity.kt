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
import android.os.Build
import android.provider.Settings
import android.app.AppOpsManager
import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
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
    private var appPage = 0
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var pagingGesture = false

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
        renderCurrentPage()
        setContentView(canvas)
        canvas.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> layoutCanvas() }
        canvas.post { layoutCanvas() }
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        updateShizukuStatus()
    }

    private fun buildHub() {
        addPanel(0f, 0f, 1920f, 1080f, intArrayOf(Color.rgb(4, 8, 19), Color.rgb(8, 18, 38), Color.rgb(4, 9, 20)), 0f)
        addText("APX MOUSE", 54f, 42f, 420f, 66f, 40f, white, true)
        addText("GAMING CONTROL CENTER", 58f, 108f, 420f, 28f, 15f, muted, true)
        pageButton("CHỌN GAME", 1280f, 48f, 220f, 82f) { navigateTo(UiPage.GAME_SELECTION) }
        val addAppButton = addPanel(1650f, 48f, 82f, 82f, intArrayOf(Color.rgb(20, 55, 96), Color.rgb(10, 28, 52)), 18f)
        addAppButton.contentDescription = "Thêm ứng dụng vào Game Space"
        addClick(addAppButton) { navigateTo(UiPage.ADD_APPS) }
        val addAppIcon = addText("+", 1650f, 48f, 82f, 82f, 42f, blue, true, Gravity.CENTER)
        addAppIcon.contentDescription = "Thêm ứng dụng vào Game Space"
        addClick(addAppIcon) { navigateTo(UiPage.ADD_APPS) }
        val settingsButton = addPanel(1760f, 48f, 82f, 82f, intArrayOf(Color.rgb(20, 48, 86), Color.rgb(12, 27, 53)), 18f)
        settingsButton.contentDescription = "Mở cài đặt"
        addClick(settingsButton) { navigateTo(UiPage.SETTINGS) }
        val settingsIcon = addText("⚙", 1760f, 48f, 82f, 82f, 34f, blue, true, Gravity.CENTER)
        settingsIcon.contentDescription = "Mở cài đặt"
        addClick(settingsIcon) { navigateTo(UiPage.SETTINGS) }
        addPanel(60f, 164f, 1380f, 850f, intArrayOf(Color.rgb(18, 39, 72), Color.rgb(7, 15, 32), Color.rgb(12, 27, 52)), 30f)
        addText("TRÒ CHƠI CỦA BẠN", 112f, 202f, 650f, 48f, 27f, white, true)
        addText("Chọn ứng dụng để mở nhanh · Vuốt ngang để chuyển trang", 114f, 253f, 900f, 34f, 18f, muted, false)
        emptyStateViews += addText("+", 570f, 390f, 300f, 130f, 100f, blue, true, Gravity.CENTER)
        emptyStateViews += addText("CHƯA CÓ ỨNG DỤNG", 420f, 530f, 600f, 48f, 27f, white, true, Gravity.CENTER)
        emptyStateViews += addText("Nhấn THÊM APP để đưa trò chơi vào Game Space", 330f, 588f, 780f, 36f, 18f, muted, false, Gravity.CENTER)
        addPanel(1480f, 164f, 380f, 850f, intArrayOf(Color.rgb(15, 32, 60), Color.rgb(7, 15, 32)), 26f)
        addText("QUYỀN HỆ THỐNG", 1515f, 205f, 320f, 42f, 23f, white, true)
        shizukuDot = View(this).apply { background = rounded(Color.rgb(248, 113, 113), 20f) }
        addAt(shizukuDot, 1520f, 285f, 16f, 16f)
        shizukuStatus = addText("Chưa kết nối", 1550f, 275f, 285f, 48f, 17f, Color.rgb(255, 177, 177), true)
        addPanel(1515f, 365f, 310f, 86f, intArrayOf(Color.rgb(24, 105, 133), Color.rgb(12, 54, 83)), 18f)
        shizukuButton = addText("Kết nối Shizuku", 1530f, 388f, 280f, 40f, 19f, white, true, Gravity.CENTER)
        addClick(shizukuButton) { requestShizukuPermission() }
        addText("ROOT ACCESS", 1518f, 540f, 300f, 32f, 15f, muted, true)
        addPanel(1515f, 585f, 310f, 86f, intArrayOf(Color.rgb(37, 53, 82), Color.rgb(17, 28, 50)), 18f)
        addClick(addText("DÙNG ROOT", 1530f, 608f, 280f, 40f, 19f, muted, true, Gravity.CENTER)) { toast("Chế độ Root chưa được tích hợp") }
        addText("Shizuku và Root là hai phương thức cấp quyền riêng biệt.", 1518f, 710f, 300f, 72f, 14f, muted, false)
        renderGameSpace()
    }


    private enum class UiPage { HUB, GAME_SELECTION, ADD_APPS, SETTINGS, APP_DETAILS }
    private var currentPage = UiPage.HUB
    private var selectedPackage: String? = null
    private var selectedGame = "Đấu Trường"

    private fun navigateTo(page: UiPage) {
        currentPage = page
        renderCurrentPage()
    }

    private fun renderCurrentPage() {
        clearScreen()
        when (currentPage) {
            UiPage.HUB -> buildHub()
            UiPage.GAME_SELECTION -> buildGameSelectionPage()
            UiPage.ADD_APPS -> buildAddAppsPage()
            UiPage.SETTINGS -> buildSettingsPage()
            UiPage.APP_DETAILS -> buildAppDetailsPage()
        }
        layoutCanvas()
        if (currentPage == UiPage.HUB) updateShizukuStatus()
    }

    private fun clearScreen() {
        placements.forEach { it.view.animate().cancel() }
        canvas.removeAllViews()
        placements.clear()
        textSizes.clear()
        backgroundViews.clear()
        dynamicViews.clear()
        emptyStateViews.clear()
    }

    private fun buildPageHeader(title: String, subtitle: String) {
        addPanel(0f, 0f, 1920f, 1080f,
            intArrayOf(Color.rgb(4, 8, 19), Color.rgb(8, 18, 38), Color.rgb(4, 9, 20)), 0f)
        addText("APX MOUSE", 54f, 35f, 350f, 55f, 34f, white, true)
        addText(title, 420f, 40f, 1000f, 52f, 31f, white, true)
        addText(subtitle, 424f, 94f, 1050f, 30f, 16f, muted, false)
        pageButton("← GAME SPACE", 54f, 132f, 220f, 64f) { navigateTo(UiPage.HUB) }
        pageButton("CÀI ĐẶT", 1640f, 42f, 220f, 64f) { navigateTo(UiPage.SETTINGS) }
    }

    private fun pageButton(
        label: String, x: Float, y: Float, width: Float, height: Float,
        action: () -> Unit
    ) {
        val panel = addPanel(x, y, width, height,
            intArrayOf(Color.rgb(24, 72, 118), Color.rgb(10, 31, 58)), 16f)
        addClick(panel, action)
        val text = addText(label, x + 12f, y + 4f, width - 24f, height - 8f,
            18f, white, true, Gravity.CENTER)
        addClick(text, action)
        text.contentDescription = label
    }

    private fun buildGameSelectionPage() {
        buildPageHeader("CHỌN KHU VỰC GAME", "Chọn hồ sơ game để mở giao diện điều khiển tương ứng")
        val games = listOf("Đua Sao", "Sinh Tồn Đêm", "Đấu Trường")
        games.forEachIndexed { index, game ->
            val x = 180f + index * 540f
            val active = selectedGame == game
            val colors = if (active) intArrayOf(Color.rgb(23, 105, 155), Color.rgb(10, 43, 75))
                else intArrayOf(Color.rgb(22, 48, 82), Color.rgb(8, 21, 42))
            val card = addPanel(x, 340f, 460f, 300f, colors, 26f)
            addClick(card) { selectedGame = game; navigateTo(UiPage.GAME_SELECTION) }
            val title = addText(game, x + 28f, 410f, 404f, 64f, 32f, white, true, Gravity.CENTER)
            addClick(title) { selectedGame = game; navigateTo(UiPage.GAME_SELECTION) }
            val state = if (active) "ĐANG CHỌN" else "CHỌN HỒ SƠ"
            addText(state, x + 28f, 500f, 404f, 38f, 17f,
                if (active) green else muted, true, Gravity.CENTER)
            if (active) addText("✓", x + 190f, 555f, 80f, 60f, 42f, green, true, Gravity.CENTER)
        }
        pageButton("MỞ GAME SPACE", 760f, 820f, 400f, 76f) { navigateTo(UiPage.HUB) }
    }

    private fun buildAddAppsPage() {
        buildPageHeader("THÊM ỨNG DỤNG", "Chọn ứng dụng muốn xuất hiện trong Game Space · Chạm để thêm hoặc bỏ")
        val apps = getLaunchableApps()
        if (apps.isEmpty()) {
            addText("Không tìm thấy ứng dụng có thể mở", 400f, 430f, 1120f, 70f,
                26f, muted, false, Gravity.CENTER)
            return
        }
        apps.take(12).forEachIndexed { index, app ->
            val packageName = app.activityInfo.packageName
            val label = app.loadLabel(packageManager).toString()
            val selected = selectedPackages.contains(packageName)
            val col = index % 4
            val row = index / 4
            val x = 70f + col * 455f
            val y = 245f + row * 205f
            val colors = if (selected) intArrayOf(Color.rgb(20, 91, 130), Color.rgb(9, 40, 68))
                else intArrayOf(Color.rgb(22, 43, 73), Color.rgb(8, 19, 39))
            val card = addPanel(x, y, 420f, 170f, colors, 20f)
            val toggle = {
                if (selectedPackages.contains(packageName)) selectedPackages.remove(packageName)
                else selectedPackages.add(packageName)
                prefs.edit().putStringSet(KEY_SELECTED_APPS, selectedPackages.toSet()).apply()
                navigateTo(UiPage.ADD_APPS)
            }
            addClick(card, toggle)
            val icon = ImageView(this).apply {
                setImageDrawable(app.loadIcon(packageManager))
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = label
            }
            addAt(icon, x + 22f, y + 32f, 86f, 86f)
            val name = addText(label, x + 125f, y + 26f, 270f, 60f, 20f, white, true)
            addClick(name, toggle)
            addText(if (selected) "✓ ĐÃ THÊM" else "+ THÊM VÀO GAME SPACE",
                x + 125f, y + 92f, 270f, 40f, 15f,
                if (selected) green else blue, true)
        }
        pageButton("HOÀN TẤT · ${selectedPackages.size} ỨNG DỤNG", 730f, 900f, 460f, 70f) {
            navigateTo(UiPage.HUB)
        }
    }

    private fun buildSettingsPage() {
        buildPageHeader("CÀI ĐẶT", "Tùy chỉnh giao diện và quyền hoạt động của APX MOUSE")
        val settings = listOf(
            Triple("Giao diện", "Tối · Gaming", "Giao diện tối ưu cho màn hình chơi game"),
            Triple("Quản lý quyền", "Mở cài đặt quyền", "Kiểm tra quyền hiển thị và quyền truy cập"),
            Triple("Hiệu ứng neon", if (prefs.getBoolean("neon_enabled", true)) "Bật" else "Tắt", "Bật hoặc tắt hiệu ứng nhấn"),
            Triple("Độ mạnh ánh sáng", prefs.getString("neon_strength", "Trung bình") ?: "Trung bình", "Thấp · Trung bình · Cao"),
            Triple("Kiểu chuyển app", prefs.getString("app_transition", "Trượt") ?: "Trượt", "Trượt · Mờ dần")
        )
        settings.forEachIndexed { index, item ->
            val y = 235f + index * 132f
            val card = addPanel(220f, y, 1480f, 108f,
                intArrayOf(Color.rgb(19, 46, 78), Color.rgb(8, 21, 42)), 18f)
            addText(item.first, 260f, y + 16f, 620f, 36f, 23f, white, true)
            addText(item.third, 260f, y + 58f, 900f, 28f, 15f, muted, false)
            val value = addText(item.second, 1250f, y + 20f, 390f, 60f, 19f, blue, true, Gravity.CENTER)
            val action = {
                when (index) {
                    0 -> toast("Giao diện Gaming đang được sử dụng")
                    1 -> startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$packageName")))
                    2 -> {
                        prefs.edit().putBoolean("neon_enabled",
                            !prefs.getBoolean("neon_enabled", true)).apply()
                        navigateTo(UiPage.SETTINGS)
                    }
                    3 -> {
                        val values = arrayOf("Thấp", "Trung bình", "Cao")
                        val current = prefs.getString("neon_strength", "Trung bình")
                        val next = values[(values.indexOf(current).coerceAtLeast(0) + 1) % values.size]
                        prefs.edit().putString("neon_strength", next).apply()
                        navigateTo(UiPage.SETTINGS)
                    }
                    4 -> {
                        val values = arrayOf("Trượt", "Mờ dần")
                        val current = prefs.getString("app_transition", "Trượt")
                        val next = values[(values.indexOf(current).coerceAtLeast(0) + 1) % values.size]
                        prefs.edit().putString("app_transition", next).apply()
                        navigateTo(UiPage.SETTINGS)
                    }
                }
            }
            addClick(card, action)
            addClick(value, action)
        }
        pageButton("CHẨN ĐOÁN CHUỘT / BÀN PHÍM", 710f, 920f, 500f, 64f) { startActivity(Intent(this, InputDiagnosticsActivity::class.java)) }
    }

    private fun buildAppDetailsPage() {
        buildPageHeader("CHI TIẾT ỨNG DỤNG", selectedGame)
        val packageName = selectedPackage
        if (packageName == null || packageManager.getLaunchIntentForPackage(packageName) == null) {
            addText("Chưa chọn ứng dụng", 500f, 420f, 920f, 70f, 28f, muted, true, Gravity.CENTER)
            pageButton("QUAY LẠI GAME SPACE", 720f, 560f, 480f, 72f) { navigateTo(UiPage.HUB) }
            return
        }
        val info = packageManager.getApplicationInfo(packageName, 0)
        val label = packageManager.getApplicationLabel(info).toString()
        val icon = ImageView(this).apply {
            setImageDrawable(packageManager.getApplicationIcon(info))
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = label
        }
        addAt(icon, 520f, 320f, 180f, 180f)
        addText(label, 750f, 330f, 700f, 70f, 34f, white, true)
        addText("SẴN SÀNG MỞ", 755f, 410f, 500f, 38f, 17f, green, true)
        pageButton("MỞ ỨNG DỤNG", 620f, 610f, 300f, 76f) { launchSelectedApp(packageName) }
        pageButton("XÓA KHỎI KHU VỰC", 970f, 610f, 330f, 76f) {
            confirmRemoveApp(packageName, label)
            navigateTo(UiPage.HUB)
        }
        pageButton("CHỌN HỒ SƠ GAME", 760f, 760f, 400f, 68f) {
            navigateTo(UiPage.GAME_SELECTION)
        }
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

    private fun renderGameSpace(animateFromRight: Boolean = false) {
        dynamicViews.forEach { view ->
            // Stop any in-flight property animation before detaching recycled page views.
            view.animate().cancel()
            canvas.removeView(view)
            placements.removeAll { it.view === view }
            if (view is TextView) textSizes.remove(view)
            backgroundViews.remove(view)
        }
        dynamicViews.clear()
        val packages = selectedPackages.filter { packageManager.getLaunchIntentForPackage(it) != null }.sorted()
        val pageCount = ((packages.size + 3) / 4).coerceAtLeast(1)
        appPage = appPage.coerceIn(0, pageCount - 1)
        emptyStateViews.forEach { it.visibility = if (packages.isEmpty()) View.VISIBLE else View.GONE }
        packages.drop(appPage * 4).take(4).forEachIndexed { index, packageName ->
            val info = packageManager.getApplicationInfo(packageName, 0)
            val label = packageManager.getApplicationLabel(info).toString()
            val col = index % 2
            val row = index / 2
            val x = 145f + col * 640f
            val y = 320f + row * 260f
            val panel = addDynamicPanel(x, y, 590f, 220f, intArrayOf(Color.rgb(24, 62, 105), Color.rgb(9, 24, 48)), 24f)
            panel.isClickable = true
            panel.isFocusable = true
            panel.contentDescription = "Mở $label trong Game Space. Nhấn giữ để xóa khỏi danh sách."
            panel.setOnClickListener { selectedPackage = packageName; navigateTo(UiPage.APP_DETAILS) }
            panel.setOnLongClickListener { confirmRemoveApp(packageName, label); true }
            val icon = ImageView(this).apply {
                setImageDrawable(packageManager.getApplicationIcon(info))
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = label
                isClickable = true
                isFocusable = true
                setOnClickListener { selectedPackage = packageName; navigateTo(UiPage.APP_DETAILS) }
            }
            dynamicViews += icon
            addAt(icon, x + 28f, y + 56f, 108f, 108f)
            val name = addDynamicText(label, x + 158f, y + 48f, 405f, 64f, 27f, white, true)
            val subtitle = addDynamicText("GAME / APP", x + 158f, y + 110f, 380f, 28f, 15f, muted, true)
            val open = addDynamicText("MỞ ỨNG DỤNG  →", x + 158f, y + 156f, 380f, 34f, 18f, blue, true)
            listOf(name, subtitle, open).forEach { item ->
                item.isClickable = true
                item.setOnClickListener { selectedPackage = packageName; navigateTo(UiPage.APP_DETAILS) }
                item.setOnLongClickListener { confirmRemoveApp(packageName, label); true }
            }
        }
        if (packages.isNotEmpty()) {
            val pageCountText = "TRANG " + (appPage + 1) + " / " + pageCount + "   ·   " + packages.size + " ỨNG DỤNG"
            val indicator = addDynamicText(pageCountText, 500f, 900f, 600f, 34f, 17f, muted, true)
            indicator.gravity = Gravity.CENTER
            val previous = addDynamicText("‹", 390f, 892f, 64f, 48f, 36f, blue, true)
            previous.gravity = Gravity.CENTER
            previous.isClickable = appPage > 0
            previous.alpha = if (appPage > 0) 1f else 0.25f
            previous.setOnClickListener { if (appPage > 0) { appPage--; renderGameSpace(true) } }
            val next = addDynamicText("›", 1160f, 892f, 64f, 48f, 36f, blue, true)
            next.gravity = Gravity.CENTER
            next.isClickable = appPage < pageCount - 1
            next.alpha = if (appPage < pageCount - 1) 1f else 0.25f
            next.setOnClickListener { if (appPage < pageCount - 1) { appPage++; renderGameSpace(true) } }
        }
        layoutCanvas()
        if (animateFromRight) dynamicViews.forEach { item ->
            item.translationX = 20f
            item.alpha = 0f
            item.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(PAGE_TRANSITION_DURATION_MS)
                .setInterpolator(DecelerateInterpolator(1.5f))
                .start()
        }
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
        } else {
            if (!Settings.canDrawOverlays(this)) {
                toast("Cấp quyền hiển thị trên ứng dụng khác để dùng overlay")
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:${this.packageName}")))
                return
            }
            if (!hasUsageAccess()) {
                toast("Cấp quyền Truy cập dữ liệu sử dụng để APX nhận biết khi bạn rời game")
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                return
            }
            val serviceIntent = Intent(this, GameOverlayService::class.java)
                .putExtra(GameOverlayService.EXTRA_GAME_PACKAGE, packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
            else startService(serviceIntent)
            startActivity(launchIntent)
        }
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun showSettings() {
        val options = arrayOf(
            "Hiện ứng dụng hệ thống trong danh sách Thêm app",
            "Xóa toàn bộ ứng dụng khỏi Game Space",
            "Chẩn đoán chuột / bàn phím (Shizuku)",
            "Giới thiệu APX MOUSE · Phiên bản"
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
                    2 -> startActivity(Intent(this, InputDiagnosticsActivity::class.java))
                    3 -> {
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

    private fun addDynamicPanel(x: Float, y: Float, width: Float, height: Float, colors: IntArray, radius: Float): View {
        val view = View(this)
        dynamicViews += view
        backgroundViews[view] = Pair(colors, radius)
        view.background = gradient(colors, radius)
        addAt(view, x, y, width, height)
        return view
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
        // Fit the complete 1920x1080 hub inside the available screen.
        // maxOf() cropped the top/bottom on wide displays and pushed controls off-screen.
        val scale = minOf(width / 1920f, height / 1080f)
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
                textSizes[item.view]?.let { item.view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, it * scale * 1.16f) }
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

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { swipeStartX = event.x; swipeStartY = event.y; pagingGesture = false }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - swipeStartX
                val dy = event.y - swipeStartY
                val threshold = ViewConfiguration.get(this).scaledTouchSlop * 2
                if (!pagingGesture && kotlin.math.abs(dx) > threshold && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.25f) {
                    pagingGesture = true
                    val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    val count = selectedPackages.filter { packageManager.getLaunchIntentForPackage(it) != null }.size
                    val pages = ((count + 3) / 4).coerceAtLeast(1)
                    appPage = (appPage + if (dx < 0) 1 else -1).coerceIn(0, pages - 1)
                    renderGameSpace(true)
                }
                if (pagingGesture) return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (pagingGesture) { pagingGesture = false; return true }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onResume() {
        super.onResume()
        updateShizukuStatus()
        if (::canvas.isInitialized && currentPage == UiPage.HUB) renderGameSpace()
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
        private const val PAGE_TRANSITION_DURATION_MS = 190L
        private const val SHIZUKU_REQUEST_CODE = 2301
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val KEY_SELECTED_APPS = "selected_apps"
        private const val KEY_SHOW_SYSTEM_APPS = "show_system_apps"
    }
}
