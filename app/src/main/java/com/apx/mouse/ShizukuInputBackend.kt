package com.apx.mouse

import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import rikka.shizuku.Shizuku
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shizuku shell backend for discrete taps and serialized multi-pointer touch frames.
 *
 * Touch injection is performed by the Shizuku UserService process through Android's
 * InputManager API. Device/API permission support must be verified on the target phone.
 */
class ShizukuInputBackend(
    private val listener: Listener,
    private val displayWidth: Int = 0,
    private val displayHeight: Int = 0
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
        val detail: String,
        val touchApiAvailable: Boolean = false
    )

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val closed = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    // Accessed only on executor to preserve Android pointer ordering.
    private val touchState = TouchFrameState()
    private var gestureDownTime = 0L
    // Pointer 31 is reserved for the short FPS-camera swipe gesture.
    private val fpsAimPointerId = 31
    private var fpsAimProcessor: FpsAimProcessor? = null

    override fun onCommand(command: InputEngine.TouchCommand) {
        if (closed.get()) return
        when (command) {
            is InputEngine.TouchCommand.Down ->
                enqueueTouch { sendFrame(touchState.down(command.pointer, command.x, command.y)) }
            is InputEngine.TouchCommand.Move ->
                enqueueTouch { sendFrame(touchState.move(command.pointer, command.x, command.y)) }
            is InputEngine.TouchCommand.Up ->
                enqueueTouch { sendFrame(touchState.up(command.pointer)) }
            // One relative sample becomes a short, bounded DOWN -> MOVE -> UP swipe.
            is InputEngine.TouchCommand.AimDelta -> enqueueTouch { sendAimDelta(command.dx, command.dy) }
            is InputEngine.TouchCommand.Joystick -> Unit
            // Wheel mapping is intentionally not assigned a game action until the user configures one.
            is InputEngine.TouchCommand.Wheel -> Unit
            is InputEngine.TouchCommand.KeyState -> Unit
        }
    }

    fun inspectCapabilities(callback: (Capability) -> Unit) {
        executor.execute {
            val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
            val granted = running && runCatching {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)

            val shellAvailable = if (granted) {
                val result = DisplayScaleController.execute("command -v input")
                result.success && !result.output.contains("ERROR:")
            } else null
            val touchProbe = if (granted) DisplayScaleController.probeTouchInjection()
                else DisplayScaleController.Result(false, "Shizuku chưa được cấp quyền")
            val touchSupported = touchProbe.success
            val detail = when {
                !running -> "Shizuku chưa chạy."
                !granted -> "Shizuku chưa cấp quyền cho APX MOUSE."
                !touchSupported -> "Chưa xác nhận được API inject touch: ${touchProbe.output}"
                else -> "Đã tìm thấy API inject touch. Cần thử trên thiết bị để xác nhận quyền và đa điểm chạm."
            }
            val result = Capability(
                shizukuRunning = running,
                permissionGranted = granted,
                shellInputAvailable = shellAvailable,
                // API presence alone does not prove that this device grants injection permission.
                supportsContinuousTouch = false,
                supportsMultiTouch = false,
                detail = detail,
                touchApiAvailable = touchSupported
            )
            mainHandler.post { if (!closed.get()) callback(result) }
        }
    }

    /** Explicit one-shot tap retained for diagnostics and simple actions. */
    fun sendTap(x: Float, y: Float) {
        if (closed.get()) return
        if (!x.isFinite() || !y.isFinite() || x < 0f || y < 0f) {
            postTapResult(false, "Tọa độ tap không hợp lệ.")
            return
        }
        executor.execute {
            try {
                if (!hasShizukuPermission()) {
                    postTapResult(false, "Shizuku chưa chạy hoặc chưa được cấp quyền.")
                    return@execute
                }
                val xi = x.toInt()
                val yi = y.toInt()
                val result = DisplayScaleController.execute("input tap $xi $yi")
                postTapResult(result.success, if (result.success) {
                    "Đã gửi lệnh tap tại ($xi, $yi)."
                } else {
                    "Lệnh tap thất bại: ${result.output}"
                })
            } catch (t: Throwable) {
                postTapResult(false, "Không thể gửi tap: ${t.message ?: t.javaClass.simpleName}")
            }
        }
    }

    private fun enqueueTouch(block: () -> Unit) {
        runCatching { executor.execute { if (!closed.get()) block() } }
            .onFailure { postStatus("Không thể xếp hàng touch frame: ${it.message}") }
    }

    private fun sendAimDelta(dx: Float, dy: Float) {
        if (!dx.isFinite() || !dy.isFinite() || (dx == 0f && dy == 0f)) return
        val processor = fpsAimProcessor ?: createFpsAimProcessor().also {
            fpsAimProcessor = it
        } ?: run {
            postStatus("Không đọc được kích thước màn hình; chưa thể khởi tạo FPS Mouse Look.")
            return
        }
        val target = processor.target(dx, dy) ?: return
        val centerX = targetCenterX
        val centerY = targetCenterY

        // Reuse the same serialized multi-pointer state as key/button mappings.
        // This keeps ACTION_POINTER_DOWN/UP ordering valid when another mapping is held.
        sendFrame(touchState.down(fpsAimPointerId, centerX, centerY))
        if (gestureDownTime <= 0L) return
        sendFrame(touchState.move(fpsAimPointerId, target.x, target.y))
        if (gestureDownTime <= 0L) return
        sendFrame(touchState.up(fpsAimPointerId))
    }

    private var targetCenterX = 0f
    private var targetCenterY = 0f

    private fun createFpsAimProcessor(): FpsAimProcessor? {
        val dimensions = if (displayWidth > 0 && displayHeight > 0) {
            displayWidth to displayHeight
        } else {
            val result = DisplayScaleController.execute("wm size")
            if (!result.success) return null
            val text = result.output
            val sizeMatch = Regex("Override size:\\s*(\\d+)x(\\d+)").find(text)
                ?: Regex("Physical size:\\s*(\\d+)x(\\d+)").find(text)
                ?: return null
            (sizeMatch.groupValues[1].toIntOrNull() ?: return null) to
                (sizeMatch.groupValues[2].toIntOrNull() ?: return null)
        }
        val width = dimensions.first
        val height = dimensions.second
        if (width <= 0 || height <= 0) return null
        targetCenterX = (width - 1) / 2f
        targetCenterY = (height - 1) / 2f
        val radius = minOf(width, height) * 0.30f
        return FpsAimProcessor(
            centerX = targetCenterX,
            centerY = targetCenterY,
            radius = radius
        )
    }

    private fun sendFrame(frame: TouchFrameState.Frame?) {
        if (frame == null) return
        if (frame.action == TouchFrameState.ACTION_DOWN) {
            gestureDownTime = SystemClock.uptimeMillis()
        }
        if (gestureDownTime <= 0L) {
            postStatus("Bỏ qua touch frame vì thiếu downTime.")
            touchState.cancel()
            return
        }

        val pointers = frame.pointers
        val result = DisplayScaleController.injectTouchFrame(
            action = frame.action,
            pointerIds = pointers.map { it.id }.toIntArray(),
            xs = pointers.map { it.x }.toFloatArray(),
            ys = pointers.map { it.y }.toFloatArray(),
            downTime = gestureDownTime,
            eventTime = SystemClock.uptimeMillis()
        )
        if (!result.success) {
            postStatus("Inject touch thất bại: ${result.output}")
            // Best-effort cancellation prevents a partially delivered gesture sticking.
            val cancel = touchState.cancel()
            if (cancel != null) {
                DisplayScaleController.injectTouchFrame(
                    cancel.action,
                    cancel.pointers.map { it.id }.toIntArray(),
                    cancel.pointers.map { it.x }.toFloatArray(),
                    cancel.pointers.map { it.y }.toFloatArray(),
                    gestureDownTime,
                    SystemClock.uptimeMillis()
                )
            }
            gestureDownTime = 0L
            return
        }
        frame.releasedPointerId?.let { touchState.finishUp(it) }
        if (touchState.activeCount() == 0) gestureDownTime = 0L
    }

    private fun hasShizukuPermission(): Boolean =
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    private fun postStatus(message: String) {
        mainHandler.post { if (!closed.get()) listener.onStatus(message) }
    }

    private fun postTapResult(success: Boolean, message: String) {
        mainHandler.post {
            if (!closed.get()) listener.onTapResult(success, message)
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            // Send best-effort ACTION_CANCEL before stopping the serial worker.
            runCatching {
                executor.execute {
                    val cancel = touchState.cancel()
                    if (cancel != null && gestureDownTime > 0L) {
                        DisplayScaleController.injectTouchFrame(
                            cancel.action,
                            cancel.pointers.map { it.id }.toIntArray(),
                            cancel.pointers.map { it.x }.toFloatArray(),
                            cancel.pointers.map { it.y }.toFloatArray(),
                            gestureDownTime,
                            SystemClock.uptimeMillis()
                        )
                    }
                    gestureDownTime = 0L
                }
                executor.shutdown()
            }.onFailure { executor.shutdownNow() }
        }
    }
}
