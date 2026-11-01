package com.apx.mouse

import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Conservative Shizuku shell backend.
 *
 * Current supported operation: a discrete single-pointer tap using Android's
 * public shell "input tap" command. This is NOT a continuous touch stream and
 * does NOT provide drag, pointer concurrency, mouse-look, or multi-touch.
 *
 * Never reports unsupported operations as successful. A future native/user
 * service backend can implement the same interface after device testing.
 */
class ShizukuInputBackend(
    private val listener: Listener
) : InputEngine.CommandSink, AutoCloseable {

    interface Listener {
        fun onStatus(message: String)
        fun onTapResult(success: Boolean, message: String)
    }

    data class Capability(
        val shizukuRunning: Boolean,
        val permissionGranted: Boolean,
        val shellInputAvailable: Boolean?,
        val supportsContinuousTouch: Boolean = false,
        val supportsMultiTouch: Boolean = false,
        val detail: String
    )

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val closed = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCommand(command: InputEngine.TouchCommand) {
        when (command) {
            is InputEngine.TouchCommand.Down -> sendTap(command.x, command.y)
            is InputEngine.TouchCommand.Up -> Unit // input tap already sends DOWN+UP.
            is InputEngine.TouchCommand.Move ->
                postStatus("Chưa hỗ trợ kéo/di chuyển cảm ứng liên tục.")
            is InputEngine.TouchCommand.AimDelta ->
                postStatus("Chưa hỗ trợ chuyển động ngắm liên tục.")
            is InputEngine.TouchCommand.Joystick ->
                if (command.x != 0f || command.y != 0f) {
                    postStatus("Chưa hỗ trợ giữ joystick hoặc đa điểm chạm.")
                }
            is InputEngine.TouchCommand.Wheel -> Unit
            is InputEngine.TouchCommand.KeyState ->
                postStatus("Bàn phím hiện chỉ được phân tích trong InputEngine; chưa inject sang game.")
        }
    }

    fun inspectCapabilities(callback: (Capability) -> Unit) {
        executor.execute {
            val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
            val granted = running && runCatching {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)

            val shellAvailable = if (granted) {
                runCatching {
                    val process = Shizuku.newProcess(arrayOf("sh", "-c", "command -v input"), null, null)
                    val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
                    val exit = process.waitFor()
                    output.isNotEmpty() && exit == 0
                }.getOrDefault(false)
            } else null

            val detail = when {
                !running -> "Shizuku chưa chạy."
                !granted -> "Shizuku chưa cấp quyền cho APX MOUSE."
                shellAvailable == true -> "Shell input có sẵn. Hiện chỉ xác nhận được lệnh tap đơn."
                else -> "Không tìm thấy lệnh input hoặc shell không cho phép chạy."
            }
            val result = Capability(running, granted, shellAvailable, detail = detail)
            mainHandler.post { callback(result) }
        }
    }

    /**
     * A discrete tap only. Coordinates are validated before being passed to
     * the shell; no command text is accepted from external input.
     */
    fun sendTap(x: Float, y: Float) {
        if (closed.get()) return
        if (!x.isFinite() || !y.isFinite() || x < 0f || y < 0f) {
            postTapResult(false, "Tọa độ tap không hợp lệ.")
            return
        }
        executor.execute {
            try {
                if (!Shizuku.pingBinder() ||
                    Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
                ) {
                    postTapResult(false, "Shizuku chưa chạy hoặc chưa được cấp quyền.")
                    return@execute
                }

                val xi = x.toInt()
                val yi = y.toInt()
                val process = Shizuku.newProcess(
                    arrayOf("input", "tap", xi.toString(), yi.toString()),
                    null,
                    null
                )
                val stdout = process.inputStream.bufferedReader().use { it.readText() }
                val stderr = process.errorStream.bufferedReader().use { it.readText() }
                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    postTapResult(true, "Đã gửi lệnh tap tại ($xi, $yi).")
                } else {
                    postTapResult(false, "Lệnh tap thất bại (exit=$exitCode): ${stderr.ifBlank { stdout }}")
                }
            } catch (t: Throwable) {
                postTapResult(false, "Không thể gửi tap: ${t.message ?: t.javaClass.simpleName}")
            }
        }
    }

    private fun postStatus(message: String) {
        mainHandler.post { if (!closed.get()) listener.onStatus(message) }
    }

    private fun postTapResult(success: Boolean, message: String) {
        mainHandler.post {
            if (!closed.get()) listener.onTapResult(success, message)
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) executor.shutdownNow()
    }
}
