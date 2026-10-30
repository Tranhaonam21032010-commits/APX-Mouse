package com.apx.mouse

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku

/**
 * Display controls are executed by a Shizuku UserService (shell/root identity),
 * never by the normal application process.
 */
object DisplayScaleController {
    private const val PREFS = "apx_mouse"
    private const val KEY_CAPTURED = "display_original_captured"
    private const val KEY_WIDTH = "display_original_width"
    private const val KEY_HEIGHT = "display_original_height"
    private const val KEY_DENSITY = "display_original_density"
    private const val KEY_SIZE_OVERRIDE = "display_original_size_override"
    private const val KEY_DENSITY_OVERRIDE = "display_original_density_override"
    private const val SERVICE_VERSION = 1

    data class Result(val success: Boolean, val output: String)

    @Volatile private var shell: IDisplayShellService? = null
    @Volatile private var bindError: String? = null
    private var bound = false
    private var appContext: Context? = null

    private val args: Shizuku.UserServiceArgs
        get() {
            val context = appContext ?: throw IllegalStateException("Chưa khởi tạo Shizuku UserService")
            return Shizuku.UserServiceArgs(
                ComponentName(context, DisplayShellUserService::class.java)
            ).daemon(false).processNameSuffix("display").debuggable(false).version(SERVICE_VERSION)
        }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            shell = IDisplayShellService.Stub.asInterface(binder)
            bindError = if (shell == null) "Không kết nối được Display UserService" else null
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            shell = null
            bindError = "Shizuku UserService đã ngắt kết nối"
        }
    }

    @Synchronized
    fun connect(context: Context): Result {
        appContext = context.applicationContext
        if (shell?.asBinder()?.isBinderAlive == true) return Result(true, "Shizuku đã sẵn sàng")
        if (!Shizuku.pingBinder()) return Result(false, "Shizuku chưa chạy")
        if (Shizuku.isPreV11() || Shizuku.getVersion() < 11)
            return Result(false, "Cần Shizuku phiên bản 11 trở lên")
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED)
            return Result(false, "APX MOUSE chưa được cấp quyền Shizuku")
        return try {
            if (!bound) {
                Shizuku.bindUserService(args, connection)
                bound = true
            }
            Result(true, "Đang kết nối UserService; thử lại sau một chút nếu chưa sẵn sàng")
        } catch (e: Exception) {
            bindError = e.message
            Result(false, "Không thể mở UserService: ${e.message}")
        }
    }

    fun execute(command: String): Result {
        val service = shell
        if (service == null || service.asBinder()?.isBinderAlive != true) {
            return Result(false, bindError ?: "Shizuku UserService chưa sẵn sàng")
        }
        return try {
            val output = service.execute(command).trim()
            Result(!output.startsWith("ERROR:"), output.ifBlank { "OK" })
        } catch (e: Exception) {
            Result(false, "Lỗi Shizuku: ${e.message}")
        }
    }

    fun captureOriginal(context: Context): Result {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_CAPTURED, false)) return Result(true, "Đã lưu thông số màn hình gốc")
        val size = execute("wm size")
        if (!size.success) return size
        val density = execute("wm density")
        if (!density.success) return density

        val sizeText = size.output
        val densityText = density.output
        val physicalSize = Regex("Physical size:\\s*(\\d+)x(\\d+)").find(sizeText)
            ?: return Result(false, "Không đọc được độ phân giải gốc: $sizeText")
        val overrideSize = Regex("Override size:\\s*(\\d+)x(\\d+)").find(sizeText)
        val physicalDensity = Regex("Physical density:\\s*(\\d+)").find(densityText)
            ?: return Result(false, "Không đọc được DPI gốc: $densityText")
        val overrideDensity = Regex("Override density:\\s*(\\d+)").find(densityText)

        val width = (overrideSize ?: physicalSize).groupValues[1].toInt()
        val height = (overrideSize ?: physicalSize).groupValues[2].toInt()
        val dpi = (overrideDensity ?: physicalDensity).groupValues[1].toInt()
        prefs.edit()
            .putInt(KEY_WIDTH, width).putInt(KEY_HEIGHT, height).putInt(KEY_DENSITY, dpi)
            .putBoolean(KEY_SIZE_OVERRIDE, overrideSize != null)
            .putBoolean(KEY_DENSITY_OVERRIDE, overrideDensity != null)
            .putBoolean(KEY_CAPTURED, true).apply()
        return Result(true, "Đã lưu cấu hình gốc: ${width}×${height}, ${dpi} DPI")
    }

    fun apply(width: Int, height: Int): Result = apply(width, height, null)

    fun apply(width: Int, height: Int, density: Int?): Result {
        if (width !in 320..7680 || height !in 320..7680)
            return Result(false, "Kích thước phải từ 320 đến 7680")
        if (density != null && density !in 100..1000)
            return Result(false, "DPI hợp lệ: 100 đến 1000")
        val size = execute("wm size ${width}x${height}")
        if (!size.success) return size
        if (density != null) {
            val dpi = execute("wm density $density")
            if (!dpi.success) return Result(false, "Đã đổi độ phân giải nhưng DPI lỗi: ${dpi.output}")
        }
        return Result(true, "Đã áp dụng ${width}×${height}" + (density?.let { " · $it DPI" } ?: ""))
    }

    fun restore(context: Context): Result {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_CAPTURED, false))
            return Result(true, "Chưa có cấu hình màn hình tạm để khôi phục")
        val width = prefs.getInt(KEY_WIDTH, 0)
        val height = prefs.getInt(KEY_HEIGHT, 0)
        val dpi = prefs.getInt(KEY_DENSITY, 0)
        if (width <= 0 || height <= 0 || dpi <= 0)
            return Result(false, "Dữ liệu cấu hình gốc không hợp lệ")
        val size = if (prefs.getBoolean(KEY_SIZE_OVERRIDE, false))
            execute("wm size ${width}x${height}") else execute("wm size reset")
        if (!size.success) return size
        val density = if (prefs.getBoolean(KEY_DENSITY_OVERRIDE, false))
            execute("wm density $dpi") else execute("wm density reset")
        if (!density.success) return density
        prefs.edit().putBoolean(KEY_CAPTURED, false).apply()
        return Result(true, "Đã khôi phục màn hình về ${width}×${height}, ${dpi} DPI")
    }

    @Synchronized
    fun disconnect() {
        try {
            if (bound && appContext != null) Shizuku.unbindUserService(args, connection, true)
        } catch (_: Exception) { }
        bound = false
        shell = null
    }
}
