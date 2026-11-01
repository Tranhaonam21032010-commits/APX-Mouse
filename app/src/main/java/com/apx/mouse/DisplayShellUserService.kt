package com.apx.mouse

import android.os.Binder
import android.os.SystemClock
import android.os.Build
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Runs as Shizuku shell/root identity. Only display-related wm commands are accepted.
 */
class DisplayShellUserService : IDisplayShellService.Stub() {
    private val inputCaptureLock = Any()
    @Volatile private var inputCaptureProcess: Process? = null
    @Volatile private var inputCaptureThread: Thread? = null
    @Volatile private var inputCaptureListener: IInputEventListener? = null

    override fun destroy() {
        stopInputCapture()
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
        return runDiagnostic("timeout ${duration}s getevent -lt 2>&1", (duration + 2).toLong())
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

    /**
     * Reports whether this shell process can resolve the platform injection API.
     * This is an API-presence probe, not proof that the device accepts injected events.
     */
    /**
     * Streams only explicitly selected /dev/input nodes. The app controls when
     * capture starts and stops; the reader never writes raw events to disk.
     */
    override fun startInputCapture(
        eventPaths: Array<out String>,
        listener: IInputEventListener
    ): String {
        val paths = eventPaths.map { it.trim() }.distinct()
        if (paths.isEmpty() || paths.size > 8 ||
            paths.any { !Regex("/dev/input/event[0-9]{1,3}").matches(it) }
        ) return "ERROR: Chọn từ 1 đến 8 thiết bị input hợp lệ"

        synchronized(inputCaptureLock) {
            if (inputCaptureProcess?.isAlive == true) {
                return "ERROR: Phiên đọc input đang chạy"
            }

            val command = listOf("getevent", "-lt") + paths
            val process = try {
                ProcessBuilder(command).redirectErrorStream(true).start()
            } catch (e: Exception) {
                return "ERROR: Không khởi động được getevent: ${e.message ?: e.javaClass.simpleName}"
            }

            inputCaptureProcess = process
            inputCaptureListener = listener
            val reader = Thread(
                { readInputCapture(process, listener) },
                "APX-EvdevInputReader"
            ).apply { isDaemon = true }
            inputCaptureThread = reader
            reader.start()
        }
        return "OK: Đang đọc ${paths.size} thiết bị input đã chọn"
    }

    override fun stopInputCapture(): String {
        val process: Process?
        synchronized(inputCaptureLock) {
            process = inputCaptureProcess
            inputCaptureProcess = null
            inputCaptureThread = null
            inputCaptureListener = null
        }
        if (process == null) return "OK: Không có phiên input đang chạy"
        return try {
            process.destroy()
            if (!process.waitFor(300, TimeUnit.MILLISECONDS)) process.destroyForcibly()
            "OK: Đã dừng đọc input"
        } catch (e: Exception) {
            process.destroyForcibly()
            "ERROR: Không dừng được getevent: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun readInputCapture(process: Process, listener: IInputEventListener) {
        val batch = ArrayList<String>(32)
        var lastFlushAt = SystemClock.uptimeMillis()

        fun flushBatch() {
            if (batch.isEmpty()) return
            val snapshot = batch.toTypedArray()
            batch.clear()
            lastFlushAt = SystemClock.uptimeMillis()
            try {
                listener.onInputEventBatch(snapshot)
            } catch (_: Throwable) {
                process.destroy()
            }
        }

        var completion = "Phiên đọc input đã kết thúc"
        try {
            process.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    batch.add(line.take(512))
                    val now = SystemClock.uptimeMillis()
                    if (batch.size >= 32 || now - lastFlushAt >= 16L) flushBatch()
                }
            }
            flushBatch()
            val exitCode = process.waitFor()
            completion = if (exitCode == 0) "getevent đã dừng" else "getevent kết thúc với mã $exitCode"
        } catch (e: Exception) {
            completion = "Lỗi đọc input: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            synchronized(inputCaptureLock) {
                if (inputCaptureProcess === process) {
                    inputCaptureProcess = null
                    inputCaptureThread = null
                    inputCaptureListener = null
                }
            }
            runCatching { listener.onInputCaptureEnded(completion) }
        }
    }

    override fun probeTouchInjection(): String {
        return try {
            val target = inputManagerTarget()
            target.first.getDeclaredMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
            "OK: ${target.first.name}"
        } catch (e: Exception) {
            "ERROR: Touch injection API unavailable: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /**
     * Injects one complete touch frame. Arrays describe the full active pointer set,
     * including the pointer being lifted for POINTER_UP/UP events.
     */
    override fun injectTouchFrame(
        action: Int,
        pointerIds: IntArray,
        xs: FloatArray,
        ys: FloatArray,
        downTime: Long,
        eventTime: Long
    ): String {
        if (pointerIds.isEmpty() || pointerIds.size > 10 ||
            xs.size != pointerIds.size || ys.size != pointerIds.size ||
            pointerIds.any { it !in 0..31 } ||
            pointerIds.toSet().size != pointerIds.size ||
            xs.any { !it.isFinite() || it < 0f } ||
            ys.any { !it.isFinite() || it < 0f } ||
            downTime <= 0L || eventTime < downTime
        ) return "ERROR: Invalid touch frame"

        val maskedAction = action and MotionEvent.ACTION_MASK
        if (maskedAction !in setOf(
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN,
                MotionEvent.ACTION_POINTER_UP
            )
        ) return "ERROR: Unsupported touch action"

        return try {
            val properties = Array(pointerIds.size) { index ->
                MotionEvent.PointerProperties().apply {
                    id = pointerIds[index]
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }
            val coordinates = Array(pointerIds.size) { index ->
                MotionEvent.PointerCoords().apply {
                    x = xs[index]
                    y = ys[index]
                    pressure = 1f
                    size = 1f
                }
            }
            val event = MotionEvent.obtain(
                downTime, eventTime, action, pointerIds.size, properties, coordinates,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
            )
            try {
                val (managerClass, manager) = inputManagerTarget()
                val method = managerClass.getDeclaredMethod(
                    "injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType
                ).apply { isAccessible = true }
                val accepted = method.invoke(manager, event, 0) as? Boolean ?: false
                if (accepted) "OK" else "ERROR: Android rejected the injected touch event"
            } finally {
                event.recycle()
            }
        } catch (e: Throwable) {
            "ERROR: Touch injection failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun inputManagerTarget(): Pair<Class<*>, Any> {
        val className = if (Build.VERSION.SDK_INT >= 31)
            "android.hardware.input.InputManagerGlobal"
        else
            "android.hardware.input.InputManager"
        val clazz = Class.forName(className)
        val instance = clazz.getDeclaredMethod("getInstance").apply { isAccessible = true }.invoke(null)
            ?: throw IllegalStateException("Input manager instance unavailable")
        return clazz to instance
    }

    override fun execute(command: String): String {
        val normalized = command.trim()
        if (!(normalized == "id" || normalized == "command -v input" ||
              normalized == "wm size" || normalized == "wm density" ||
              normalized == "wm size reset" || normalized == "wm density reset" ||
              normalized.matches(Regex("wm size [0-9]{3,4}x[0-9]{3,4}")) ||
              normalized.matches(Regex("wm density [0-9]{2,4}")) ||
              normalized.matches(Regex("input tap [0-9]{1,5} [0-9]{1,5}")))) {
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