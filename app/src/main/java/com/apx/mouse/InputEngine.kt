package com.apx.mouse

import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.hypot

/**
 * APX MOUSE input translation engine.
 *
 * Receives Android key/mouse events delivered to the focused APX view and
 * converts them into platform-neutral commands. It does NOT inject input into
 * another app. A separately tested Shizuku-capable backend is required for that.
 */
class InputEngine(
    private val sink: CommandSink,
    config: Config = Config()
) {
    interface CommandSink {
        fun onCommand(command: TouchCommand)
    }

    data class MouseButtonBinding(
        val pointerId: Int,
        val x: Float,
        val y: Float,
        val enabled: Boolean = true
    )

    data class Config(
        val screenWidth: Int = 0,
        val screenHeight: Int = 0,
        val aimSensitivity: Float = 1.0f,
        val maxAimStepPx: Float = 120f,
        val invertMouseY: Boolean = false,
        val smoothMouse: Boolean = false,
        val wheelSensitivity: Float = 1.0f,
        val joystickRadius: Float = 0.12f,
        val joystickCenterX: Float = 0.18f,
        val joystickCenterY: Float = 0.78f,
        val movementKeys: Set<Int> = setOf(
            KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_A,
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D
        ),
        val mouseButtons: Map<Int, MouseButtonBinding> = defaultMouseBindings()
    )

    sealed class TouchCommand {
        data class Down(val pointer: Int, val x: Float, val y: Float) : TouchCommand()
        data class Move(val pointer: Int, val x: Float, val y: Float) : TouchCommand()
        data class Up(val pointer: Int) : TouchCommand()
        data class Joystick(val x: Float, val y: Float) : TouchCommand()
        data class AimDelta(val dx: Float, val dy: Float) : TouchCommand()
        data class Wheel(val horizontal: Float, val vertical: Float) : TouchCommand()
        /** Every Android key code is forwarded, not just a small hard-coded list. */
        data class KeyState(
            val keyCode: Int,
            val pressed: Boolean,
            val scanCode: Int,
            val metaState: Int,
            val repeatCount: Int,
            val unicodeChar: Int
        ) : TouchCommand()
    }

    private val pressedKeys = linkedSetOf<Int>()
    private val activeMouseButtons = linkedSetOf<Int>()
    private var currentConfig = config
    private var enabled = true
    private var lastMouseX = Float.NaN
    private var lastMouseY = Float.NaN
    private var smoothedDx = 0f
    private var smoothedDy = 0f
    private var joystickX = 0f
    private var joystickY = 0f

    @Synchronized
    fun updateConfig(newConfig: Config) {
        releaseAll()
        currentConfig = newConfig
    }

    @Synchronized
    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (!value) releaseAll()
    }

    /** Receives all key codes, including function, navigation and modifier keys. */
    @Synchronized
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (!enabled) return false
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) {
            return false
        }

        val down = event.action == KeyEvent.ACTION_DOWN
        if (down) pressedKeys.add(event.keyCode) else pressedKeys.remove(event.keyCode)

        sink.onCommand(
            TouchCommand.KeyState(
                keyCode = event.keyCode,
                pressed = down,
                scanCode = event.scanCode,
                metaState = event.metaState,
                repeatCount = event.repeatCount,
                unicodeChar = event.unicodeChar
            )
        )

        if (event.keyCode in currentConfig.movementKeys) {
            updateJoystick()
            return true
        }
        // Forward unknown keys too; caller may choose whether to consume them.
        return true
    }

    /** Mouse movement, hover, relative motion and wheel. */
    @Synchronized
    fun onMouseMotion(event: MotionEvent): Boolean {
        if (!enabled || !event.isFromSource(InputDevice.SOURCE_MOUSE)) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_MOVE -> {
                var dx: Float
                var dy: Float
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val relativeX = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
                    val relativeY = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
                    if (relativeX != 0f || relativeY != 0f) {
                        dx = relativeX
                        dy = relativeY
                    } else {
                        dx = if (lastMouseX.isFinite()) event.x - lastMouseX else 0f
                        dy = if (lastMouseY.isFinite()) event.y - lastMouseY else 0f
                    }
                } else {
                    dx = if (lastMouseX.isFinite()) event.x - lastMouseX else 0f
                    dy = if (lastMouseY.isFinite()) event.y - lastMouseY else 0f
                }
                lastMouseX = event.x
                lastMouseY = event.y

                dx *= currentConfig.aimSensitivity
                dy *= currentConfig.aimSensitivity * if (currentConfig.invertMouseY) -1f else 1f
                dx = dx.coerceIn(-currentConfig.maxAimStepPx, currentConfig.maxAimStepPx)
                dy = dy.coerceIn(-currentConfig.maxAimStepPx, currentConfig.maxAimStepPx)

                if (currentConfig.smoothMouse) {
                    smoothedDx = smoothedDx * 0.35f + dx * 0.65f
                    smoothedDy = smoothedDy * 0.35f + dy * 0.65f
                    dx = smoothedDx
                    dy = smoothedDy
                }
                if (dx != 0f || dy != 0f) sink.onCommand(TouchCommand.AimDelta(dx, dy))
            }
            MotionEvent.ACTION_SCROLL -> {
                val horizontal = event.getAxisValue(MotionEvent.AXIS_HSCROLL) *
                    currentConfig.wheelSensitivity
                val vertical = event.getAxisValue(MotionEvent.AXIS_VSCROLL) *
                    currentConfig.wheelSensitivity
                if (horizontal != 0f || vertical != 0f) {
                    sink.onCommand(TouchCommand.Wheel(horizontal, vertical))
                }
            }
        }
        return true
    }

    /** Handles every mouse button code delivered by Android, including side buttons. */
    @Synchronized
    fun onMouseButtons(event: MotionEvent): Boolean {
        if (!enabled) return false
        if (event.actionMasked != MotionEvent.ACTION_BUTTON_PRESS &&
            event.actionMasked != MotionEvent.ACTION_BUTTON_RELEASE) return false

        val buttonCode = event.actionButton
        val binding = currentConfig.mouseButtons[buttonCode] ?: return false
        if (!binding.enabled) return false

        if (event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS) {
            if (activeMouseButtons.add(buttonCode)) {
                val point = normalizedPoint(binding.x, binding.y)
                sink.onCommand(TouchCommand.Down(binding.pointerId, point.first, point.second))
            }
        } else if (activeMouseButtons.remove(buttonCode)) {
            sink.onCommand(TouchCommand.Up(binding.pointerId))
        }
        return true
    }

    /** Useful for devices/Android versions that expose button state but not button actions. */
    @Synchronized
    fun syncMouseButtonState(event: MotionEvent): Boolean {
        if (!enabled || !event.isFromSource(InputDevice.SOURCE_MOUSE)) return false
        var handled = false
        currentConfig.mouseButtons.forEach { (buttonCode, binding) ->
            if (!binding.enabled) return@forEach
            val isDown = event.buttonState and buttonCode != 0
            val wasDown = buttonCode in activeMouseButtons
            if (isDown && !wasDown) {
                activeMouseButtons.add(buttonCode)
                val point = normalizedPoint(binding.x, binding.y)
                sink.onCommand(TouchCommand.Down(binding.pointerId, point.first, point.second))
                handled = true
            } else if (!isDown && wasDown) {
                activeMouseButtons.remove(buttonCode)
                sink.onCommand(TouchCommand.Up(binding.pointerId))
                handled = true
            }
        }
        return handled
    }

    @Synchronized
    fun reset() = releaseAll()

    @Synchronized
    fun pressedKeyCodes(): Set<Int> = pressedKeys.toSet()

    @Synchronized
    fun activeMouseButtonCodes(): Set<Int> = activeMouseButtons.toSet()

    private fun updateJoystick() {
        var x = 0f
        var y = 0f
        if (KeyEvent.KEYCODE_A in pressedKeys) x -= 1f
        if (KeyEvent.KEYCODE_D in pressedKeys) x += 1f
        if (KeyEvent.KEYCODE_W in pressedKeys) y -= 1f
        if (KeyEvent.KEYCODE_S in pressedKeys) y += 1f

        val length = hypot(x, y)
        if (length > 1f) {
            x /= length
            y /= length
        }
        if (x != joystickX || y != joystickY) {
            joystickX = x
            joystickY = y
            sink.onCommand(TouchCommand.Joystick(x, y))
        }
    }

    private fun normalizedPoint(x: Float, y: Float): Pair<Float, Float> {
        val width = currentConfig.screenWidth.coerceAtLeast(1).toFloat()
        val height = currentConfig.screenHeight.coerceAtLeast(1).toFloat()
        return x.coerceIn(0f, 1f) * width to y.coerceIn(0f, 1f) * height
    }

    private fun releaseAll() {
        activeMouseButtons.toList().forEach { code ->
            currentConfig.mouseButtons[code]?.let { sink.onCommand(TouchCommand.Up(it.pointerId)) }
        }
        activeMouseButtons.clear()
        pressedKeys.toList().forEach { code ->
            sink.onCommand(TouchCommand.KeyState(code, false, 0, 0, 0, 0))
        }
        pressedKeys.clear()
        joystickX = 0f
        joystickY = 0f
        sink.onCommand(TouchCommand.Joystick(0f, 0f))
        lastMouseX = Float.NaN
        lastMouseY = Float.NaN
        smoothedDx = 0f
        smoothedDy = 0f
    }

    companion object {
        /** Android MotionEvent button bitmasks; extra buttons are supported when reported by device. */
        fun defaultMouseBindings(): Map<Int, MouseButtonBinding> = mapOf(
            MotionEvent.BUTTON_PRIMARY to MouseButtonBinding(1, 0.82f, 0.78f),
            MotionEvent.BUTTON_SECONDARY to MouseButtonBinding(2, 0.78f, 0.48f),
            MotionEvent.BUTTON_TERTIARY to MouseButtonBinding(3, 0.50f, 0.50f),
            MotionEvent.BUTTON_BACK to MouseButtonBinding(4, 0.18f, 0.50f),
            MotionEvent.BUTTON_FORWARD to MouseButtonBinding(5, 0.28f, 0.50f)
        )
    }
}
