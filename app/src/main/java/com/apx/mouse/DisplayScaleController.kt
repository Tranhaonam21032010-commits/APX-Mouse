package com.apx.mouse

import android.content.Context
import rikka.shizuku.Shizuku

object DisplayScaleController {
    private const val PREFS = "apx_mouse"
    private const val KEY_CAPTURED = "display_original_captured"
    private const val KEY_ORIGINAL = "display_original_size"

    data class Result(val success: Boolean, val output: String)

    fun captureOriginal(context: Context): Result {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_CAPTURED, false)) return Result(true, "Original display size already saved")
        val result = shell("wm size")
        if (!result.success) return result
        val override = Regex("Override size:\\s*(\\d+x\\d+)", RegexOption.IGNORE_CASE)
            .find(result.output)?.groupValues?.get(1)
        prefs.edit().putString(KEY_ORIGINAL, override ?: "RESET")
            .putBoolean(KEY_CAPTURED, true).apply()
        return Result(true, result.output)
    }

    fun apply(width: Int, height: Int): Result {
        if (width !in 320..7680 || height !in 320..7680) return Result(false, "Kích thước phải từ 320 đến 7680")
        return shell("wm size ${width}x${height}")
    }

    fun restore(context: Context): Result {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_CAPTURED, false)) return Result(true, "No saved display size")
        val original = prefs.getString(KEY_ORIGINAL, "RESET") ?: "RESET"
        val result = if (original == "RESET") shell("wm size reset") else shell("wm size $original")
        if (result.success) prefs.edit().remove(KEY_ORIGINAL).putBoolean(KEY_CAPTURED, false).apply()
        return result
    }

    private fun shell(command: String): Result = try {
        if (!Shizuku.pingBinder()) return Result(false, "Shizuku chưa chạy")
        if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED)
            return Result(false, "APX MOUSE chưa được cấp quyền Shizuku")
        val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val error = process.errorStream.bufferedReader().use { it.readText() }
        val code = process.waitFor()
        Result(code == 0, (output + if (error.isBlank()) "" else "\n$error").ifBlank { "exit=$code" })
    } catch (e: Exception) {
        Result(false, e.message ?: "Không thể chạy lệnh màn hình")
    }
}
