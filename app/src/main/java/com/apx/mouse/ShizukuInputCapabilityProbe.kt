package com.apx.mouse

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Read-only Shizuku diagnostics. It does not inject touch events.
 * Finding the Android input command does not prove injection or multi-touch support.
 */
object ShizukuInputCapabilityProbe {
    data class Result(
        val connected: Boolean,
        val permissionGranted: Boolean,
        val shellIdentity: String?,
        val inputCommandAvailable: Boolean?,
        val inputHelp: String?,
        val error: String?
    ) {
        fun toReport(): String = buildString {
            appendLine("APX MOUSE — SHIZUKU INPUT CHECK")
            appendLine("Shizuku connected: $connected")
            appendLine("Permission granted: $permissionGranted")
            appendLine("Shell identity: ${shellIdentity ?: "not checked"}")
            appendLine("Android input command: ${inputCommandAvailable?.toString() ?: "not checked"}")
            if (!inputHelp.isNullOrBlank()) {
                appendLine()
                appendLine("input help (first lines):")
                appendLine(inputHelp)
            }
            if (!error.isNullOrBlank()) appendLine("Error: $error")
            appendLine()
            appendLine("Touch injection: NOT tested")
            appendLine("Multi-touch injection: NOT tested")
            appendLine("No root access or Android security bypass is performed.")
        }
    }

    fun run(): Result {
        val connected = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!connected) {
            return Result(false, false, null, null, null, "Shizuku service is not running.")
        }

        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!granted) {
            return Result(true, false, null, null, null, "Grant APX MOUSE permission in Shizuku first.")
        }

        return try {
            val identity = DisplayScaleController.execute("id")
            val inputCheck = DisplayScaleController.execute("command -v input")
            val available = inputCheck.success
            val help = if (available) "Lệnh input tồn tại; chưa xác minh khả năng inject." else null

            Result(
                connected = true,
                permissionGranted = true,
                shellIdentity = identity.output.trim().ifBlank { "unknown (exit ${identity.exitCode})" },
                inputCommandAvailable = available,
                inputHelp = help,
                error = listOf(identity.error, inputCheck.error).filterNotNull().joinToString("; ").ifBlank { null }
            )
        } catch (e: Exception) {
            Result(true, true, null, null, null, e.message ?: e.javaClass.simpleName)
        }
    }

}
