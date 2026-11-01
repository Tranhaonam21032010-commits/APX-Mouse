package com.apx.mouse

/**
 * Deterministic classifier for getevent -pl capability blocks.
 * Uses Linux input capability codes rather than event-node order or one hardcoded key.
 */
internal object InputDeviceClassifier {
    data class Device(
        val path: String,
        val name: String,
        val isKeyboard: Boolean,
        val isMouse: Boolean
    )

    private val deviceHeader = Regex("""add device\s+\d+:\s+(/dev/input/event\d+)""")
    private val capabilityLine = Regex("""^\s*(KEY|REL)\s*\(000[12]\):\s*(.*)$""")
    private val hexToken = Regex("""(?i)\b[0-9a-f]{4,8}\b""")
    private val letterKeyCodes = listOf(16..25, 30..38, 44..50).flatMap { it.toList() }.toSet()

    fun parse(raw: String): List<Device> {
        val result = mutableListOf<Device>()
        var path: String? = null
        var name = "Thiết bị không tên"
        val block = StringBuilder()

        fun flush() {
            val currentPath = path ?: return
            val keyLines = mutableListOf<String>()
            val relLines = mutableListOf<String>()
            var activeCapability: String? = null
            block.toString().lineSequence().forEach { line ->
                val capability = capabilityLine.find(line)
                if (capability != null) {
                    activeCapability = capability.groupValues[1]
                    val values = capability.groupValues[2]
                    if (activeCapability == "KEY") keyLines += values
                    if (activeCapability == "REL") relLines += values
                } else if (anyCapabilityLine.matches(line)) {
                    activeCapability = null
                } else if (activeCapability != null && capabilityContinuation.matches(line)) {
                    when (activeCapability) {
                        "KEY" -> keyLines += line.trim()
                        "REL" -> relLines += line.trim()
                    }
                }
            }
            val keyText = keyLines.joinToString(" ")
            val relText = relLines.joinToString(" ")
            val keyCodes = hexToken.findAll(keyText).mapNotNull { it.value.toIntOrNull(16) }.toSet()
            val relCodes = hexToken.findAll(relText).mapNotNull { it.value.toIntOrNull(16) }.toSet()

            val namedLetterKeys = Regex("""\bKEY_[A-Z]\b""").findAll(keyText).count()
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
                block.append(line).append('\n')
                if (line.contains("name:")) {
                    name = line.substringAfter("name:").trim().removeSurrounding("\"")
                        .ifBlank { "Thiết bị không tên" }
                }
            }
        }
        flush()
        return result.distinctBy { it.path }.sortedBy { it.path }
    }
}
