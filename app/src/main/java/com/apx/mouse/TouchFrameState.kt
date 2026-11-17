package com.apx.mouse

/**
 * Builds valid Android multi-pointer frame descriptions without depending on
 * Android classes. All calls must be serialized by the owner.
 */
internal class TouchFrameState(private val maxPointers: Int = 10) {
    data class Pointer(val id: Int, val x: Float, val y: Float)
    data class Frame(val action: Int, val pointers: List<Pointer>, val releasedPointerId: Int? = null)

    private val active = LinkedHashMap<Int, Pointer>()

    fun down(id: Int, x: Float, y: Float): Frame? {
        if (!valid(id, x, y) || id in active || active.size >= maxPointers) return null
        val pointer = Pointer(id, x, y)
        active[id] = pointer
        val index = active.size - 1
        val action = if (index == 0) ACTION_DOWN else ACTION_POINTER_DOWN or
            (index shl ACTION_POINTER_INDEX_SHIFT)
        return Frame(action, active.values.toList())
    }

    fun move(id: Int, x: Float, y: Float): Frame? {
        if (!valid(id, x, y) || id !in active) return null
        active[id] = Pointer(id, x, y)
        return Frame(ACTION_MOVE, active.values.toList())
    }

    fun up(id: Int): Frame? {
        if (id !in active) return null
        val pointers = active.values.toList()
        val index = pointers.indexOfFirst { it.id == id }
        val action = if (pointers.size == 1) ACTION_UP else ACTION_POINTER_UP or
            (index shl ACTION_POINTER_INDEX_SHIFT)
        return Frame(action, pointers, releasedPointerId = id)
    }

    /** Commit the release only after Android accepted the corresponding UP frame. */
    fun finishUp(id: Int): Boolean = active.remove(id) != null

    fun cancel(): Frame? {
        if (active.isEmpty()) return null
        val frame = Frame(ACTION_CANCEL, active.values.toList())
        active.clear()
        return frame
    }

    fun activeCount(): Int = active.size

    private fun valid(id: Int, x: Float, y: Float): Boolean =
        id in 0..31 && x.isFinite() && y.isFinite() && x >= 0f && y >= 0f

    companion object {
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val ACTION_MOVE = 2
        const val ACTION_CANCEL = 3
        const val ACTION_POINTER_DOWN = 5
        const val ACTION_POINTER_UP = 6
        const val ACTION_POINTER_INDEX_SHIFT = 8
    }
}
