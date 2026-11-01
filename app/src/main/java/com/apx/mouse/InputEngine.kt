package com.apx.mouse

import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.hypot
import kotlin.math.min

/**
 * Converts Android window events and selected evdev keyboard/mouse events
 * into ordered touch commands. It does not read devices itself; the owner must
 * explicitly start/stop the Shizuku input-capture session.
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

    data class JoystickDirection(val x: Float, val y: Float)

    private val defaultJoystickDirections = mapOf(
        KeyEvent.KEYCODE_W to JoystickDirection(0f, -1f),
        KeyEvent.KEYCODE_A to JoystickDirection(-1f, 0f),
        KeyEvent.KEYCODE_S to JoystickDirection(0f, 1f),
        KeyEvent.KEYCODE_D to JoystickDirection(1f, 0f)
    )

    data class Config(
        val screenWidth: Int = 0,
        val screenHeight: Int = 0,
        val aimSensitivityX: Float = 1.0f,
        val aimSensitivityY: Float = 1.0f,
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
        /** Direction vector for each configurable joystick key. */
        val joystickDirections: Map<Int, JoystickDirection> = mapOf(
            KeyEvent.KEYCODE_W to JoystickDirection(0f, -1f),
            KeyEvent.KEYCODE_A to JoystickDirection(-1f, 0f),
            KeyEvent.KEYCODE_S to JoystickDirection(0f, 1f),
            KeyEvent.KEYCODE_D to JoystickDirection(1f, 0f)
        ),
        /** Per-game points saved by MappingEditor, including KEYCODE_BUTTON_1..5. */
        val keyBindings: Map<Int, MouseButtonBinding> = emptyMap(),
        /** Fallback positions for direct Android MotionEvent mouse input. */
        val mouseButtons: Map<Int, MouseButtonBinding> = emptyMap(),
        /** Mouse buttons whose mapped touch point follows relative mouse motion (aim/drag). */
        val dragMouseButtons: Set<Int> = setOf(MotionEvent.BUTTON_SECONDARY)
    )

    sealed class TouchCommand {
        data class Down(val pointer: Int, val x: Float, val y: Float) : TouchCommand()
        data class Move(val pointer: Int, val x: Float, val y: Float) : TouchCommand()
        data class Up(val pointer: Int) : TouchCommand()
        /** Retained for API compatibility; joystick movement is now emitted as touch frames. */
        data class Joystick(val x: Float, val y: Float) : TouchCommand()
        data class AimDelta(val dx: Float, val dy: Float) : TouchCommand()
        data class Wheel(val horizontal: Float, val vertical: Float) : TouchCommand()
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
    private val activeMousePointers = mutableMapOf<Int, MouseButtonBinding>()
    private val activeKeyPointers = mutableMapOf<Int, Int>()
    private var currentConfig = config
    private var enabled = true
    private var lastMouseX = Float.NaN
    private var lastMouseY = Float.NaN
    private var smoothedDx = 0f
    private var smoothedDy = 0f
    private var joystickX = 0f
    private var joystickY = 0f
    private var joystickActive = false

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

    @Synchronized
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return false
        return onMappedKeyCode(event.keyCode, event.action == KeyEvent.ACTION_DOWN)
    }

    /** Common keyboard entry point, also used by deterministic unit tests. */
    @Synchronized
    fun onMappedKeyCode(keyCode: Int, pressed: Boolean): Boolean {
        if (!enabled) return false
        processKey(keyCode, pressed)
        return keyCode in currentConfig.keyBindings || keyCode in currentConfig.movementKeys
    }

    /** Linux evdev KEY_* event from the explicitly selected input devices. */
    @Synchronized
    fun onRawKey(codeName: String, pressed: Boolean): Boolean {
        val keyCode = androidKeyCode(codeName) ?: return false
        return onMappedKeyCode(keyCode, pressed)
    }

    /** Linux evdev BTN_* event. Only the mouse buttons supported by MappingEditor are used. */
    @Synchronized
    fun onRawMouseButton(codeName: String, pressed: Boolean): Boolean {
        if (!enabled) return false
        val keyCode = when (codeName) {
            "BTN_LEFT" -> KeyEvent.KEYCODE_BUTTON_1
            "BTN_RIGHT" -> KeyEvent.KEYCODE_BUTTON_2
            "BTN_MIDDLE" -> KeyEvent.KEYCODE_BUTTON_3
            "BTN_SIDE" -> KeyEvent.KEYCODE_BUTTON_4
            "BTN_EXTRA" -> KeyEvent.KEYCODE_BUTTON_5
            else -> return false
        }
        val buttonMask = when (codeName) {
            "BTN_LEFT" -> MotionEvent.BUTTON_PRIMARY
            "BTN_RIGHT" -> MotionEvent.BUTTON_SECONDARY
            "BTN_MIDDLE" -> MotionEvent.BUTTON_TERTIARY
            "BTN_SIDE" -> MotionEvent.BUTTON_BACK
            "BTN_EXTRA" -> MotionEvent.BUTTON_FORWARD
            else -> return false
        }
        val binding = currentConfig.keyBindings[keyCode]?.takeIf { it.enabled }
            ?: currentConfig.mouseButtons[buttonMask]?.takeIf { it.enabled }
            ?: return false
        if (pressed) {
            if (activeMouseButtons.add(buttonMask)) {
                val point = normalizedPoint(binding.x, binding.y)
                val active = binding.copy(x = point.first, y = point.second)
                activeMousePointers[buttonMask] = active
                sink.onCommand(TouchCommand.Down(binding.pointerId, point.first, point.second))
            }
        } else if (activeMouseButtons.remove(buttonMask)) {
            val active = activeMousePointers.remove(buttonMask)
            if (active != null) sink.onCommand(TouchCommand.Up(active.pointerId))
        }
        return true
    }

    /** Relative mouse movement from EV_REL, grouped by SYN_REPORT by the parser. */
    @Synchronized
    fun onRawMouseMotion(dx: Int, dy: Int): Boolean {
        if (!enabled) return false
        dispatchRelativeMotion(dx.toFloat(), dy.toFloat())
        return true
    }

    @Synchronized
    fun onRawWheel(horizontal: Int, vertical: Int): Boolean {
        if (!enabled || (horizontal == 0 && vertical == 0)) return false
        sink.onCommand(
            TouchCommand.Wheel(
                horizontal * currentConfig.wheelSensitivity,
                vertical * currentConfig.wheelSensitivity
            )
        )
        return true
    }

    /** Mouse movement, hover, relative motion and wheel received by a focused APX window. */
    @Synchronized
    fun onMouseMotion(event: MotionEvent): Boolean {
        if (!enabled || !event.isFromSource(InputDevice.SOURCE_MOUSE)) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_MOVE -> {
                val dx: Float
                val dy: Float
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
                dispatchRelativeMotion(dx, dy)
            }
            MotionEvent.ACTION_SCROLL -> {
                val horizontal = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
                val vertical = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                onRawWheel(horizontal.toInt(), vertical.toInt())
            }
        }
        return true
    }

    @Synchronized
    fun onMouseButtons(event: MotionEvent): Boolean {
        if (!enabled || event.actionMasked !in setOf(
                MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE
            )
        ) return false
        val buttonCode = event.actionButton
        val keyCode = androidButtonKeyCode(buttonCode) ?: return false
        val binding = currentConfig.keyBindings[keyCode]?.takeIf { it.enabled }
            ?: currentConfig.mouseButtons[buttonCode]?.takeIf { it.enabled }
            ?: return false
        applyMouseButton(buttonCode, binding, event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS)
        return true
    }

    @Synchronized
    fun syncMouseButtonState(event: MotionEvent): Boolean {
        if (!enabled || !event.isFromSource(InputDevice.SOURCE_MOUSE)) return false
        var handled = false
        currentConfig.mouseButtons.forEach { (buttonCode, binding) ->
            if (!binding.enabled) return@forEach
            val isDown = event.buttonState and buttonCode != 0
            val wasDown = buttonCode in activeMouseButtons
            if (isDown != wasDown) {
                applyMouseButton(buttonCode, binding, isDown)
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

    private fun processKey(keyCode: Int, down: Boolean) {
        if (down) {
            // Android reports ACTION_DOWN repeatedly while a key is held. Do not send duplicate DOWN frames.
            if (!pressedKeys.add(keyCode)) return
            val binding = currentConfig.keyBindings[keyCode]?.takeIf { it.enabled }
            if (binding != null) {
                val point = normalizedPoint(binding.x, binding.y)
                activeKeyPointers[keyCode] = binding.pointerId
                sink.onCommand(TouchCommand.Down(binding.pointerId, point.first, point.second))
            } else if (keyCode in currentConfig.movementKeys) {
                updateJoystick()
            }
        } else {
            if (!pressedKeys.remove(keyCode)) return
            activeKeyPointers.remove(keyCode)?.let { sink.onCommand(TouchCommand.Up(it)) }
            if (keyCode in currentConfig.movementKeys) updateJoystick()
        }
    }

    private fun applyMouseButton(buttonCode: Int, binding: MouseButtonBinding, down: Boolean) {
        if (down) {
            if (activeMouseButtons.add(buttonCode)) {
                val point = normalizedPoint(binding.x, binding.y)
                activeMousePointers[buttonCode] = binding.copy(x = point.first, y = point.second)
                sink.onCommand(TouchCommand.Down(binding.pointerId, point.first, point.second))
            }
        } else if (activeMouseButtons.remove(buttonCode)) {
            activeMousePointers.remove(buttonCode)?.let { sink.onCommand(TouchCommand.Up(it.pointerId)) }
        }
    }

    private fun dispatchRelativeMotion(rawDx: Float, rawDy: Float) {
        var dx = rawDx * currentConfig.aimSensitivityX.coerceIn(0.1f, 3f)
        var dy = rawDy * currentConfig.aimSensitivityY.coerceIn(0.1f, 3f) *
            if (currentConfig.invertMouseY) -1f else 1f
        dx = dx.coerceIn(-currentConfig.maxAimStepPx, currentConfig.maxAimStepPx)
        dy = dy.coerceIn(-currentConfig.maxAimStepPx, currentConfig.maxAimStepPx)

        if (currentConfig.smoothMouse) {
            smoothedDx = smoothedDx * 0.35f + dx * 0.65f
            smoothedDy = smoothedDy * 0.35f + dy * 0.65f
            dx = smoothedDx
            dy = smoothedDy
        }
        if (dx == 0f && dy == 0f) return
        sink.onCommand(TouchCommand.AimDelta(dx, dy))
        moveActiveMousePointers(dx, dy)
    }

    /** W/A/S/D become a real held joystick pointer rather than an unhandled placeholder command. */
    private fun updateJoystick() {
        var x = 0f
        var y = 0f
        currentConfig.movementKeys.forEach { keyCode ->
            if (keyCode in pressedKeys) {
                val direction = currentConfig.joystickDirections[keyCode] ?: return@forEach
                x += direction.x
                y += direction.y
            }
        }

        val length = hypot(x, y)
        if (length > 1f) {
            x /= length
            y /= length
        }
        if (x == 0f && y == 0f) {
            if (joystickActive) sink.onCommand(TouchCommand.Up(JOYSTICK_POINTER_ID))
            joystickActive = false
            joystickX = 0f
            joystickY = 0f
            return
        }
        if (x == joystickX && y == joystickY && joystickActive) return

        val maxX = (currentConfig.screenWidth - 1).coerceAtLeast(0).toFloat()
        val maxY = (currentConfig.screenHeight - 1).coerceAtLeast(0).toFloat()
        val minDimension = min(maxX, maxY)
        val centerX = currentConfig.joystickCenterX.coerceIn(0f, 1f) * maxX
        val centerY = currentConfig.joystickCenterY.coerceIn(0f, 1f) * maxY
        val radius = currentConfig.joystickRadius.coerceIn(0.01f, 0.45f) * minDimension

        if (!joystickActive) {
            sink.onCommand(TouchCommand.Down(JOYSTICK_POINTER_ID, centerX, centerY))
            joystickActive = true
        }
        val targetX = (centerX + x * radius).coerceIn(0f, maxX)
        val targetY = (centerY + y * radius).coerceIn(0f, maxY)
        sink.onCommand(TouchCommand.Move(JOYSTICK_POINTER_ID, targetX, targetY))
        joystickX = x
        joystickY = y
    }

    private fun moveActiveMousePointers(dx: Float, dy: Float) {
        val maxX = (currentConfig.screenWidth - 1).coerceAtLeast(0).toFloat()
        val maxY = (currentConfig.screenHeight - 1).coerceAtLeast(0).toFloat()
        activeMousePointers.toMap().forEach { (buttonCode, binding) ->
            // Keep click/fire buttons anchored; only the configured drag button follows the mouse.
            if (buttonCode !in currentConfig.dragMouseButtons) return@forEach
            val nextX = (binding.x + dx).coerceIn(0f, maxX)
            val nextY = (binding.y + dy).coerceIn(0f, maxY)
            if (nextX != binding.x || nextY != binding.y) {
                activeMousePointers[buttonCode] = binding.copy(x = nextX, y = nextY)
                sink.onCommand(TouchCommand.Move(binding.pointerId, nextX, nextY))
            }
        }
    }

    private fun normalizedPoint(x: Float, y: Float): Pair<Float, Float> {
        val width = (currentConfig.screenWidth - 1).coerceAtLeast(0).toFloat()
        val height = (currentConfig.screenHeight - 1).coerceAtLeast(0).toFloat()
        return x.coerceIn(0f, 1f) * width to y.coerceIn(0f, 1f) * height
    }

    private fun releaseAll() {
        activeMousePointers.values.toList().forEach { sink.onCommand(TouchCommand.Up(it.pointerId)) }
        activeMousePointers.clear()
        activeMouseButtons.clear()
        activeKeyPointers.values.toList().forEach { sink.onCommand(TouchCommand.Up(it)) }
        activeKeyPointers.clear()
        pressedKeys.clear()
        if (joystickActive) sink.onCommand(TouchCommand.Up(JOYSTICK_POINTER_ID))
        joystickActive = false
        joystickX = 0f
        joystickY = 0f
        lastMouseX = Float.NaN
        lastMouseY = Float.NaN
        smoothedDx = 0f
        smoothedDy = 0f
    }

    private fun androidButtonKeyCode(button: Int): Int? = when (button) {
        MotionEvent.BUTTON_PRIMARY -> KeyEvent.KEYCODE_BUTTON_1
        MotionEvent.BUTTON_SECONDARY -> KeyEvent.KEYCODE_BUTTON_2
        MotionEvent.BUTTON_TERTIARY -> KeyEvent.KEYCODE_BUTTON_3
        MotionEvent.BUTTON_BACK -> KeyEvent.KEYCODE_BUTTON_4
        MotionEvent.BUTTON_FORWARD -> KeyEvent.KEYCODE_BUTTON_5
        else -> null
    }

    private fun androidKeyCode(evdevCode: String): Int? {
        if (!evdevCode.startsWith("KEY_")) return null
        val suffix = evdevCode.removePrefix("KEY_")
        val androidSuffix = when (suffix) {
            "ESC" -> "ESCAPE"
            "BACKSPACE" -> "DEL"
            "DELETE" -> "FORWARD_DEL"
            "LEFTSHIFT" -> "SHIFT_LEFT"
            "RIGHTSHIFT" -> "SHIFT_RIGHT"
            "LEFTCTRL" -> "CTRL_LEFT"
            "RIGHTCTRL" -> "CTRL_RIGHT"
            "LEFTALT" -> "ALT_LEFT"
            "RIGHTALT" -> "ALT_RIGHT"
            "LEFTMETA" -> "META_LEFT"
            "RIGHTMETA" -> "META_RIGHT"
            "CAPSLOCK" -> "CAPS_LOCK"
            "PAGEUP" -> "PAGE_UP"
            "PAGEDOWN" -> "PAGE_DOWN"
            "UP" -> "DPAD_UP"
            "DOWN" -> "DPAD_DOWN"
            "LEFT" -> "DPAD_LEFT"
            "RIGHT" -> "DPAD_RIGHT"
            "KPENTER" -> "NUMPAD_ENTER"
            else -> suffix
        }
        val code = KeyEvent.keyCodeFromString("KEYCODE_$androidSuffix")
        return code.takeIf { it != KeyEvent.KEYCODE_UNKNOWN }
    }

    companion object {
        private const val JOYSTICK_POINTER_ID = 0
    }
}
