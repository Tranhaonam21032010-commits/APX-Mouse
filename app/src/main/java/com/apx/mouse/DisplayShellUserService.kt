package com.apx.mouse

import android.os.Binder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Runs as Shizuku shell/root identity. Only display-related wm commands are accepted.
 */
class DisplayShellUserService : IDisplayShellService.Stub() {
    override fun execute(command: String): String {
        val normalized = command.trim()
        if (!(normalized == "wm size" || normalized == "wm density" ||
              normalized == "wm size reset" || normalized == "wm density reset" ||
              normalized.matches(Regex("wm size [0-9]{3,4}x[0-9]{3,4}")) ||
              normalized.matches(Regex("wm density [0-9]{2,4}")))) {
            return "ERROR: Lệnh không được phép"
        }
        return try {
            val process = ProcessBuilder("sh", "-c", normalized).redirectErrorStream(true).start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            if (!process.waitFor(4, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                "ERROR: Lệnh quá thời gian"
            } else {
                val code = process.exitValue()
                if (code == 0) output.ifBlank { "OK" } else "ERROR: $output"
            }
        } catch (e: Exception) {
            "ERROR: ${e.message ?: "Không chạy được lệnh"}"
        }
    }
}