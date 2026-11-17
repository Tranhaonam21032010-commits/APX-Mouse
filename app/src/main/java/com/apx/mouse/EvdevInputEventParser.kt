package com.apx.mouse

/**
 * Parses the human-readable output of "getevent -lt".
 *
 * Only EV_KEY and EV_REL are translated. EV_ABS (including touchscreen
 * coordinates) is deliberately ignored so this reader cannot turn finger
 * touches into synthetic touches.
 */
internal class EvdevInputEventParser {
    sealed class Event {
        data class Key(val device: String, val code: String, val pressed: Boolean, val repeat: Boolean) : Event()
        data class MouseButton(val device: String, val code: String, val pressed: Boolean) : Event()
        data class Relative(
            val device: String,
            val dx: Int,
            val dy: Int,
            val wheelX: Int,
            val wheelY: Int
        ) : Event()
    }

    private data class PendingRelative(
        var dx: Int = 0,
        var dy: Int = 0,
        var wheelX: Int = 0,
        var wheelY: Int = 0
    ) {
        fun isEmpty() = dx == 0 && dy == 0 && wheelX == 0 && wheelY == 0
    }

    private val pendingByDevice = mutableMapOf<String, PendingRelative>()

    fun parseLine(line: String): List<Event> {
        val match = EVENT_PATTERN.find(line.trim()) ?: return emptyList()
        val device = match.groupValues[1]
        val type = match.groupValues[2]
        val code = match.groupValues[3]
        val rawValue = match.groupValues[4]

        return when (type) {
            "EV_REL" -> {
                val value = parseSignedValue(rawValue) ?: return emptyList()
                val pending = pendingByDevice.getOrPut(device) { PendingRelative() }
                when (code) {
                    "REL_X" -> pending.dx = safeAdd(pending.dx, value)
                    "REL_Y" -> pending.dy = safeAdd(pending.dy, value)
                    "REL_HWHEEL" -> pending.wheelX = safeAdd(pending.wheelX, value)
                    "REL_WHEEL" -> pending.wheelY = safeAdd(pending.wheelY, value)
                    else -> Unit
                }
                emptyList()
            }
            "EV_KEY" -> parseKey(device, code, rawValue)
            "EV_SYN" -> if (code == "SYN_REPORT") flush(device) else emptyList()
            // In particular, do not forward EV_ABS / BTN_TOUCH from the phone screen.
            else -> emptyList()
        }
    }

    fun reset() {
        pendingByDevice.clear()
    }

    private fun parseKey(device: String, code: String, rawValue: String): List<Event> {
        val value = parseKeyValue(rawValue) ?: return emptyList()
        if (value !in 0..2) return emptyList()

        val pressed = value != 0
        val repeat = value == 2
        return when {
            code.startsWith("BTN_") && code in MOUSE_BUTTONS ->
                listOf(Event.MouseButton(device, code, pressed))
            code.startsWith("KEY_") && code !in IGNORED_KEYS ->
                listOf(Event.Key(device, code, pressed, repeat))
            else -> emptyList()
        }
    }

    private fun flush(device: String): List<Event> {
        val pending = pendingByDevice.remove(device) ?: return emptyList()
        if (pending.isEmpty()) return emptyList()
        return listOf(Event.Relative(
            device = device,
            dx = pending.dx,
            dy = pending.dy,
            wheelX = pending.wheelX,
            wheelY = pending.wheelY
        ))
    }

    private fun parseKeyValue(value: String): Int? = when (value.uppercase()) {
        "DOWN" -> 1
        "UP" -> 0
        "REPEAT" -> 2
        else -> parseSignedValue(value)
    }

    /** getevent -l prints values as zero-padded hexadecimal; signed REL values use two's complement. */
    private fun parseSignedValue(raw: String): Int? {
        val value = raw.trim()
        if (value.startsWith("-")) return value.toIntOrNull()
        val digits = value.removePrefix("0x").removePrefix("0X")
        if (digits.isEmpty() || digits.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) {
            return value.toIntOrNull()
        }
        return runCatching { digits.toLong(16).toInt() }.getOrNull()
    }

    private fun safeAdd(current: Int, delta: Int): Int =
        (current.toLong() + delta.toLong()).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

    companion object {
        private val EVENT_PATTERN = Regex(
            """^\[\s*[0-9]+(?:\.[0-9]+)?\s*]\s+(/dev/input/event[0-9]+):\s+(EV_[A-Z]+)\s+([A-Z0-9_]+)\s+([^\s]+)"""
        )
        private val MOUSE_BUTTONS = setOf("BTN_LEFT", "BTN_RIGHT", "BTN_MIDDLE", "BTN_SIDE", "BTN_EXTRA")
        private val IGNORED_KEYS = setOf("KEY_RESERVED", "KEY_UNKNOWN")
    }
}
