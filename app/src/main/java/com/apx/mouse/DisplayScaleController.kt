package com.apx.mouse

import android.content.Context

/**
 * Display-size changes are temporarily disabled.
 *
 * Shizuku API 13.1.5 no longer exposes newProcess to applications. The feature
 * must be migrated to a UserService/AIDL implementation before it can safely
 * execute "wm size" with shell identity.
 */
object DisplayScaleController {
    private const val PREFS = "apx_mouse"
    private const val KEY_CAPTURED = "display_original_captured"
    private const val KEY_ORIGINAL = "display_original_size"

    data class Result(val success: Boolean, val output: String)

    fun captureOriginal(context: Context): Result {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_CAPTURED, false)) {
            return Result(false, "Tính năng đổi độ phân giải đang tạm tắt; chưa thay đổi màn hình.")
        }
        return Result(false, "Tính năng đổi độ phân giải đang chờ chuyển sang Shizuku UserService.")
    }

    fun apply(width: Int, height: Int): Result {
        if (width !in 320..7680 || height !in 320..7680) {
            return Result(false, "Kích thước phải từ 320 đến 7680")
        }
        return Result(false, "Chưa bật: cần hoàn tất Shizuku UserService để áp dụng an toàn.")
    }

    fun restore(context: Context): Result {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Do not run shell commands from the normal app process. Clear stale state
        // left by the previous broken implementation; no display override is changed.
        prefs.edit().remove(KEY_ORIGINAL).putBoolean(KEY_CAPTURED, false).apply()
        return Result(true, "Không có thay đổi độ phân giải nào cần khôi phục.")
    }
}
