package com.apx.mouse

import android.os.Binder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Runs as Shizuku shell/root identity. Only display-related wm commands are accepted.
 */
class DisplayShellUserService : IDisplayShellService.Stub() {
    override fun destroy() {
        Thread { System.exit(0) }.start()
    }

    override fun inputInventory(): String {
        return runDiagnostic(
            "id; echo '\\n--- INPUT DEVICES ---'; cat /proc/bus/input/devices; " +
            "echo '\\n--- /dev/input ---'; ls -l /dev/input 2>&1; " +
            "echo '\\n--- GETEVENT CAPABILITIES ---'; getevent -pl 2>&1",
            8
        )
    }

    override fun captureInputEvents(seconds: Int): String {
        val duration = seconds.coerceIn(2, 8)
        return runDiagnostic("timeout ${duration}s getevent -lt 2>&1", duration + 2)
    }

    private fun runDiagnostic(command: String, timeoutSeconds: Long): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
            val output = StringBuilder()
            val reader = Thread {
                try {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            synchronized(output) {
                                if (output.length < 16000) {
                                    output.append(line).append('\n')
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    // The process may be terminated after the timeout.
                }
            }.apply {
                name = "APX-InputDiagnostic-Reader"
                isDaemon = true
                start()
            }

            if (!process.waitFor(timeoutSeconds.coerceAtLeast(1), TimeUnit.SECONDS)) {
                process.destroy()
                if (!process.waitFor(300, TimeUnit.MILLISECONDS)) process.destroyForcibly()
                reader.join(500)
                return "ERROR: Chẩn đoán vượt quá thời gian cho phép"
            }

            reader.join(700)
            synchronized(output) {
                output.toString().take(16000).ifBlank { "(Không có dữ liệu trả về)" }
            }
        } catch (e: Exception) {
            "ERROR: ${e.message ?: "Không chạy được chẩn đoán"}"
        }
    }

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