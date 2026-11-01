package com.apx.mouse

import org.junit.Assert.assertEquals
import org.junit.Test

class InputDeviceClassifierTest {
    @Test fun separatesKeyboardAndMouseByCapabilities() {
        val inventory = """
            add device 1: /dev/input/event2
              name: "USB Keyboard"
              events:
                KEY (0001): 001e 0011 001f 0020 0039 (KEY_A KEY_W KEY_S KEY_D KEY_SPACE)
            add device 2: /dev/input/event5
              name: "USB Optical Mouse"
              events:
                KEY (0001): 0110 0111 0112 (BTN_LEFT BTN_RIGHT BTN_MIDDLE)
                REL (0002): 0000 0001 (REL_X REL_Y)
        """.trimIndent()

        val devices = InputDeviceClassifier.parse(inventory)
        assertEquals(2, devices.size)
        assertEquals("USB Keyboard", devices[0].name)
        assertEquals(true, devices[0].isKeyboard)
        assertEquals(false, devices[0].isMouse)
        assertEquals("USB Optical Mouse", devices[1].name)
        assertEquals(false, devices[1].isKeyboard)
        assertEquals(true, devices[1].isMouse)
    }

    @Test fun recognizesKeyboardWhenGeteventWrapsKeyCapabilitiesAcrossLines() {
        val inventory = """
            add device 1: /dev/input/event8
              name: "USB Keyboard"
              events:
                KEY (0001): 0000 0001 0002 0003
                          0004 0005 001e 0011 001f 0020
                          (KEY_RESERVED KEY_ESC KEY_1 KEY_2 KEY_3 KEY_4 KEY_A KEY_W KEY_S KEY_D)
        """.trimIndent()

        val device = InputDeviceClassifier.parse(inventory).single()
        assertEquals(true, device.isKeyboard)
    }

    @Test fun comboDeviceIsReportedAsComboWithoutRandomClassification() {
        val inventory = """
            add device 1: /dev/input/event3
              name: "2.4G Receiver"
              events:
                KEY (0001): 001e 0011 001f 0020 0110 0111 (KEY_A KEY_W KEY_S KEY_D BTN_LEFT BTN_RIGHT)
                REL (0002): 0000 0001 (REL_X REL_Y)
        """.trimIndent()

        val device = InputDeviceClassifier.parse(inventory).single()
        assertEquals(true, device.isKeyboard)
        assertEquals(true, device.isMouse)
    }

    @Test fun fallsBackToProcInputInventoryWhenGeteventCapabilitiesAreMissing() {
        val inventory = """
            --- INPUT DEVICES ---
            I: Bus=0003 Vendor=046d Product=c31c Version=0111
            N: Name="USB Keyboard"
            H: Handlers=sysrq kbd leds event6
            B: EV=120013
            B: KEY=10000 7f 0 0 0 0

            I: Bus=0003 Vendor=046d Product=c077 Version=0111
            N: Name="USB Optical Mouse"
            H: Handlers=mouse0 event9
            B: EV=17
            B: REL=903
        """.trimIndent()

        val devices = InputDeviceClassifier.parse(inventory)
        assertEquals(2, devices.size)
        assertEquals("/dev/input/event6", devices[0].path)
        assertEquals(true, devices[0].isKeyboard)
        assertEquals("/dev/input/event9", devices[1].path)
        assertEquals(true, devices[1].isMouse)
    }

    @Test fun doesNotTreatTouchscreenOrMediaKeysAsKeyboardOrMouse() {
        val inventory = """
            add device 1: /dev/input/event1
              name: "Touchscreen"
              events:
                KEY (0001): 014a (BTN_TOUCH)
                ABS (0003): 0035 0036 (ABS_MT_POSITION_X ABS_MT_POSITION_Y)
            add device 2: /dev/input/event4
              name: "Media Buttons"
              events:
                KEY (0001): 00e2 00cd 00b5 (KEY_MUTE KEY_PLAYPAUSE KEY_NEXTSONG)
        """.trimIndent()

        assertEquals(emptyList<InputDeviceClassifier.Device>(), InputDeviceClassifier.parse(inventory))
    }

    @Test fun recognizesNumericCapabilitiesWhenNamesAreNotPrinted() {
        val inventory = """
            add device 1: /dev/input/event7
              name: "Generic HID"
              events:
                KEY (0001): 001e 0011 001f 0020 0039 0110 0111
                REL (0002): 0000 0001
        """.trimIndent()

        val device = InputDeviceClassifier.parse(inventory).single()
        assertEquals(true, device.isKeyboard)
        assertEquals(true, device.isMouse)
    }
}
