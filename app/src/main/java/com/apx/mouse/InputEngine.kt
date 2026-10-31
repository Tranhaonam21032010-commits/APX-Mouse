package com.apx.mouse

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.hypot
import kotlin.math.min

/**
 * APX input translation core.
 *
 * This class only translates Android input events into abstract touch commands.
 * It does not inject events into other apps. A platform-specific backend must be
 * added after device capability checks and must report whether injection is
 * actually supported on the current non-root/Shizuku device.
 */
class InputEngine(
    private val sink: CommandSink,
    private var config: Config = Config()
) {
    interface CommandSink {
        fun onCommand(command: TouchCommand)
    }

    data class Config(
        val screenWidth: Int = 0,
        val screenHeight: Int = 0,
        val joystickCenterX: Float = 0.18f,
        val joystickCenterY: Float = 0.78f,
        val joystickRadius: Float = 0.12f,
        val aimSensitivity: Float = 1.0f,
        val maxAimStepPx: Float = 90f,
        val leftButton: Int = MotionEvent.BUTTON_PRIMARY,
        val rightButton: Int = MotionEvent.BUTTON_SECONDARY,
        val fireX: Float = 0.82f,
        val fireY: Float = 0.78f,
        val aimX: Float = 0.78f,
        val aimY: Float = 0.48f
    )

    sealed class TouchCommand {
        data class Down(val pointer: Int, val x: Float, val y: Float) : TouchCommand()
        data class Move(val pointer: Int, val x: Float, val y: Float) : TouchCommand()
        data class Up(val pointer: Int) : TouchCommand()
        data class Joystick(val x: Float, val y: Float) : TouchCommand()
        data class AimDelta(val dx: Float, val dy: Float) : TouchCommand()
        data class KeyState(val keyCode: Int, val pressed: Boolean) : TouchCommand()
    }

    private val pressedKeys = mutableSetOf<Int>()
    private val activeButtons = mutableSetOf<Int>()
    private var joystickX = 0f
    private var joystickY = 0f
    private var lastMouseX = Float.NaN
    private var lastMouseY = Float.NaN
    private var enabled = true

    @Synchronized
    fun updateConfig(newConfig: Config) {
        config = newConfig
        reset()
    }

    @Synchronized
    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (!enabled) reset()
    }

    /**
     * Call from the focused Activity/View dispatchGenericMotionEvent.
     * Mouse input is not globally observable by an ordinary Android app.
     */
    @Synchronized
    fun onMouseMotion(event: MotionEvent): Boolean {
        if (!enabled || !event.isFromSource(InputDevice.SOURCE_MOUSE)) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_MOVE -> {
                val x = event.x
                val y = event.y
                if (lastMouseX.isFinite() && lastMouseY.isFinite()) {
                    val dx = ((x - lastMouseX) * config.aimSensitivity)
                        .coerceIn(-config.maxAimStepPx, config.maxAimStepPx)
                    val dy = ((y - lastMouseY) * config.aimSensitivity)
                        .coerceIn(-config.maxAimStepPx, config.maxAimStepPx)
                    if (dx != 0f || dy != 0f) sink.onCommand(TouchCommand.AimDelta(dx, dy))
                }
                lastMouseX = x
                lastMouseY = y
            }
            MotionEvent.ACTION_SCROLL -> {
                // Scroll is intentionally left unbound by default.
            }
        }
        return true
    }

    /** Call from dispatchKeyEvent while this app's view has input focus. */
    @Synchronized
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (!enabled) return false
        val movement = isMovementKey(event.keyCode)
        val pressed = event.action == KeyEvent.ACTION_DOWN
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) {
            return false
        }

        if (pressed) pressedKeys.add(event.keyCode) else pressedKeys.remove(event.keyCode)
        sink.onCommand(TouchCommand.KeyState(event.keyCode, pressed))
        if (movement) {
            updateJoystick()
            return true
        }
        return isMappedKey(event.keyCode)
    }

    /** Call from dispatchGenericMotionEvent for mouse button state changes. */
    @Synchronized
    fun onMouseButtons(event: MotionEvent): Boolean {
        if (!enabled || event.actionMasked != MotionEvent.ACTION_BUTTON_PRESS &&
            event.actionMasked != MotionEvent.ACTION_BUTTON_RELEASE) return false

        val button = event.actionButton
        if (button != config.leftButton && button != config.rightButton) return false
        val down = event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS
        if (down) {
            if (activeButtons.add(button)) {
                val pointer = pointerFor(button)
                val point = pointFor(button)
                sink.onCommand(TouchCommand.Down(pointer, point.first, point.second))
            }
        } else {
            if (activeButtons.remove(button)) sink.onCommand(TouchCommand.Up(pointerFor(button)))
        }
        return true
    }

    @Synchronized
    fun reset() {
        activeButtons.toList().forEach { sink.onCommand(TouchCommand.Up(pointerFor(it))) }
        activeButtons.clear()
        pressedKeys.toList().forEach { sink.onCommand(TouchCommand.KeyState(it, false)) }
        pressedKeys.clear()
        joystickX = 0f
        joystickY = 0f
        sink.onCommand(TouchCommand.Joystick(0f, 0f))
        lastMouseX = Float.NaN
        lastMouseY = Float.NaN
    }

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

    private fun pointFor(button: Int): Pair<Float, Float> {
        val width = config.screenWidth.coerceAtLeast(1).toFloat()
        val height = config.screenHeight.coerceAtLeast(1).toFloat()
        val nx = if (button == config.leftButton) config.fireX else config.aimX
        val ny = if (button == config.leftButton) config.fireY else config.aimY
        return (nx.coerceIn(0f, 1f) * width) to (ny.coerceIn(0f, 1f) * height)
    }

    private fun pointerFor(button: Int): Int =
        if (button == config.leftButton) FIRE_POINTER else AIM_POINTER

    private fun isMovementKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_W || keyCode == KeyEvent.KEYCODE_A ||
        keyCode == KeyEvent.KEYCODE_S || keyCode == KeyEvent.KEYCODE_D

    private fun isMappedKey(keyCode: Int): Boolean =
        isMovementKey(keyCode) || keyCode == KeyEvent.KEYCODE_SPACE ||
        keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_R

    companion object {
        const val FIRE_POINTER = 1
        const val AIM_POINTER = 2
    }
}
