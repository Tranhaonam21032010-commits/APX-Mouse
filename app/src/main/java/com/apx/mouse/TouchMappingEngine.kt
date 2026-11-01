package com.apx.mouse

/**
 * Converts the WASD key state into one of the eight joystick sectors.
 * Android key codes: W=51, A=29, S=47, D=32.
 */
enum class JoystickDirection {
    CENTER, UP, UP_RIGHT, RIGHT, DOWN_RIGHT, DOWN, DOWN_LEFT, LEFT, UP_LEFT
}

class JoystickDirectionMapper(
    private val keyW: Int = 51,
    private val keyA: Int = 29,
    private val keyS: Int = 47,
    private val keyD: Int = 32
) {
    fun resolve(pressedKeys: Set<Int>): JoystickDirection {
        val horizontal = (if (keyD in pressedKeys) 1 else 0) -
            (if (keyA in pressedKeys) 1 else 0)
        val vertical = (if (keyS in pressedKeys) 1 else 0) -
            (if (keyW in pressedKeys) 1 else 0)

        return when {
            horizontal == 0 && vertical == 0 -> JoystickDirection.CENTER
            horizontal == 0 && vertical < 0 -> JoystickDirection.UP
            horizontal > 0 && vertical < 0 -> JoystickDirection.UP_RIGHT
            horizontal > 0 && vertical == 0 -> JoystickDirection.RIGHT
            horizontal > 0 && vertical > 0 -> JoystickDirection.DOWN_RIGHT
            horizontal == 0 && vertical > 0 -> JoystickDirection.DOWN
            horizontal < 0 && vertical > 0 -> JoystickDirection.DOWN_LEFT
            horizontal < 0 && vertical == 0 -> JoystickDirection.LEFT
            else -> JoystickDirection.UP_LEFT
        }
    }
}

/**
 * Allocates stable pointer IDs for independent held/drag actions.
 * Defaults to four simultaneous contacts; five can be enabled only after
 * the device/backend has reported that capacity as supported.
 */
class TouchSlotAllocator(
    val capacity: Int = MIN_STABLE_CONTACTS
) {
    companion object {
        const val MIN_STABLE_CONTACTS = 4
        const val MAX_CONTACTS = 5
    }

    private val actionToPointer = LinkedHashMap<Int, Int>()
    private val freePointers = ArrayDeque<Int>().apply {
        repeat(capacity.coerceIn(1, MAX_CONTACTS)) { addLast(it) }
    }

    init {
        require(capacity in 1..MAX_CONTACTS) { "Touch capacity must be between 1 and 5" }
    }

    @Synchronized
    fun acquire(actionId: Int): Int? {
        actionToPointer[actionId]?.let { return it }
        if (freePointers.isEmpty()) return null
        return freePointers.removeFirst().also { actionToPointer[actionId] = it }
    }

    @Synchronized
    fun pointerFor(actionId: Int): Int? = actionToPointer[actionId]

    @Synchronized
    fun release(actionId: Int): Boolean {
        val pointer = actionToPointer.remove(actionId) ?: return false
        freePointers.addLast(pointer)
        return true
    }

    @Synchronized
    fun releaseAll() {
        actionToPointer.clear()
        freePointers.clear()
        repeat(capacity) { freePointers.addLast(it) }
    }

    @Synchronized
    fun activeCount(): Int = actionToPointer.size
}
