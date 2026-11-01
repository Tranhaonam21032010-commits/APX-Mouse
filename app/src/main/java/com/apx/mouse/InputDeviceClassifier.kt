package com.apx.mouse

/**
 * Deterministic classifier for getevent -pl capability blocks.
 *
 * Classification uses Linux input capability codes, not event-node order or
 * assumptions that every keyboard exposes a particular key such as KEY_W.
 */
internal object InputDeviceClassifier {
    data class Device(
        val path: String,
        val name: String,
        val isKeyboard: Boolean,
        val isMouse: Boolean
    )

    private val deviceHeader = Regex("""add device\\s+\\d+:\\s+(/dev/input/event\\d+)""")
    private val namePattern = Regex(""""name:\\s*"([^"]+)"""")
    private val capabilityLine = Regex("""^\\s*(KEY|REL)\\s*\\(000[12]\\):\\s*(.*)$""")
    private val hexToken = Regex("""(?i)\\b[0-9a-f]{4,8}\\b""")

    // Linux input-event-codes.h: KEY_A..KEY_Z are non-contiguous ranges.
    private val letterKeyCodes = (
        16..25 + 30..38 + 44..50
    ).toSet()

    fun parse(raw: String): List<Device> {
        val result = mutableListOf<Device>()
        var path: String? = null
        var name = "Thiết bị không tên"
        val block = StringBuilder()

        fun flush() {
            val currentPath = path ?: return
            val lines = block.toString().lineSequence().toList()
            val keyCapabilities = lines.filter { capabilityLine.find(it)?.groupValues?.get(1) == "KEY" }
            val relCapabilities = lines.filter { capabilityLine.find(it)?.groupValues?.get(1) == "REL" }

            val keyText = keyCapabilities.joinToString(" ") { capabilityLine.find(it)!!.groupValues[2] }
            val relText = relCapabilities.joinToString(" ") { capabilityLine.find(it)!!.groupValues[2] }
            val keyCodes = hexToken.findAll(keyText).mapNotNull { it.value.toIntOrNull(16) }.toSet()
            val relCodes = hexToken.findAll(relText).mapNotNull { it.value.toIntOrNull(16) }.toSet()

            val namedLetterKeys = Regex("""\\bKEY_[A-Z]\\b""").findAll(keyText).count()
            val numericLetterKeys = keyCodes.count { it in letterKeyCodes }
            val keyboard = maxOf(namedLetterKeys, numericLetterKeys) >= 3

            val hasLeftButton = keyText.contains("BTN_LEFT") || 0x110 in keyCodes
            val hasRelativeX = relText.contains("REL_X") || 0x00 in relCodes
            val hasRelativeY = relText.contains("REL_Y") || 0x01 in relCodes
            val mouse = hasLeftButton && hasRelativeX && hasRelativeY

            if (keyboard || mouse) result += Device(currentPath, name, keyboard, mouse)
        }

        raw.lineSequence().forEach { line ->
            val header = deviceHeader.find(line)
            if (header != null) {
                flush()
                path = header.groupValues[1]
                name = "Thiết bị không tên"
                block.setLength(0)
            }
            if (path != null) {
                block.append(line).append('\\n')
                namePattern.find(line)?.let { name = it.groupValues[1] }
            }
        }
        flush()
        return result.distinctBy { it.path }.sortedBy { it.path }
    }
}
