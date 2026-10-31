package com.apx.mouse

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.MotionEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Public Android API probe for physical keyboards and pointing devices.
 * This is deliberately read-only and does not claim privileged input access.
 */
object DeviceCapabilityProbe {
    fun run(context: Context): String {
        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
        val ids = inputManager.inputDeviceIds
        val report = StringBuilder()
        report.appendLine("APX MOUSE — BETA DEVICE REPORT")
        report.appendLine("Time: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
        report.appendLine("Android API: " + android.os.Build.VERSION.SDK_INT)
        report.appendLine("Manufacturer: " + android.os.Build.MANUFACTURER)
        report.appendLine("Model: " + android.os.Build.MODEL)
        report.appendLine("Input devices detected: " + ids.size)
        report.appendLine()

        if (ids.isEmpty()) report.appendLine("Không phát hiện thiết bị input.")
        ids.forEachIndexed { index, id ->
            val device = inputManager.getInputDevice(id) ?: return@forEachIndexed
            val sources = buildList {
                if (device.supportsSource(InputDevice.SOURCE_MOUSE)) add("MOUSE")
                if (device.supportsSource(InputDevice.SOURCE_KEYBOARD)) add("KEYBOARD")
                if (device.supportsSource(InputDevice.SOURCE_TOUCHSCREEN)) add("TOUCHSCREEN")
                if (device.supportsSource(InputDevice.SOURCE_STYLUS)) add("STYLUS")
                if (device.supportsSource(InputDevice.SOURCE_GAMEPAD)) add("GAMEPAD")
                if (device.supportsSource(InputDevice.SOURCE_JOYSTICK)) add("JOYSTICK")
            }
            report.appendLine("[$index] ${device.name}")
            report.appendLine("  id=${device.id}, descriptor=${device.descriptor}")
            report.appendLine("  vendorId=${device.vendorId}, productId=${device.productId}")
            report.appendLine("  sources=${sources.joinToString().ifBlank { "UNKNOWN" }}")
            report.appendLine("  virtual=${device.isVirtual}, external=${if (android.os.Build.VERSION.SDK_INT >= 29) device.isExternal else "unknown (API < 29)"}")
            report.appendLine("  motionRanges=${device.motionRanges.joinToString { it.axis.toString() }}")
            report.appendLine()
        }

        report.appendLine("=== BETA STATUS ===")
        report.appendLine("Hardware enumeration: available via public Android API.")
        report.appendLine("Shizuku permission: checked separately in the main screen.")
        report.appendLine("Raw evdev read / touch injection: NOT tested by this report.")
        report.appendLine("No root access, system setting changes, or input injection performed.")
        return report.toString()
    }
}
