package com.apx.mouse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvdevInputEventParserTest {
    @Test fun parsesKeyboardDownAndUp() {
        val parser = EvdevInputEventParser()
        val down = parser.parseLine("[  12.000100] /dev/input/event4: EV_KEY KEY_W DOWN")
        val up = parser.parseLine("[  12.010100] /dev/input/event4: EV_KEY KEY_W UP")
        assertEquals(
            listOf(
                EvdevInputEventParser.Event.Key("/dev/input/event4", "KEY_W", true, false)
            ), down
        )
        assertEquals(
            listOf(
                EvdevInputEventParser.Event.Key("/dev/input/event4", "KEY_W", false, false)
            ), up
        )
    }

    @Test fun relativeAxesAreGroupedUntilSynReport() {
        val parser = EvdevInputEventParser()
        assertTrue(parser.parseLine("[ 1.000] /dev/input/event2: EV_REL REL_X 00000005").isEmpty())
        assertTrue(parser.parseLine("[ 1.001] /dev/input/event2: EV_REL REL_Y fffffffe").isEmpty())
        val result = parser.parseLine("[ 1.002] /dev/input/event2: EV_SYN SYN_REPORT 00000000")
        assertEquals(
            listOf(EvdevInputEventParser.Event.Relative("/dev/input/event2", 5, -2, 0, 0)),
            result
        )
    }

    @Test fun parsesMouseButtonsAndWheel() {
        val parser = EvdevInputEventParser()
        assertEquals(
            listOf(EvdevInputEventParser.Event.MouseButton("/dev/input/event2", "BTN_LEFT", true)),
            parser.parseLine("[ 1.0] /dev/input/event2: EV_KEY BTN_LEFT DOWN")
        )
        parser.parseLine("[ 1.1] /dev/input/event2: EV_REL REL_WHEEL 00000001")
        assertEquals(
            listOf(EvdevInputEventParser.Event.Relative("/dev/input/event2", 0, 0, 0, 1)),
            parser.parseLine("[ 1.2] /dev/input/event2: EV_SYN SYN_REPORT 00000000")
        )
    }

    @Test fun ignoresTouchscreenAbsoluteCoordinatesAndTouchButton() {
        val parser = EvdevInputEventParser()
        assertTrue(parser.parseLine("[ 2.0] /dev/input/event1: EV_ABS ABS_MT_POSITION_X 00000100").isEmpty())
        assertTrue(parser.parseLine("[ 2.1] /dev/input/event1: EV_KEY BTN_TOUCH DOWN").isEmpty())
        assertTrue(parser.parseLine("[ 2.2] /dev/input/event1: EV_SYN SYN_REPORT 00000000").isEmpty())
    }

    @Test fun rejectsMalformedLinesAndClearsPartialFrames() {
        val parser = EvdevInputEventParser()
        assertTrue(parser.parseLine("not a getevent line").isEmpty())
        parser.parseLine("[ 1.0] /dev/input/event2: EV_REL REL_X 00000005")
        parser.reset()
        assertTrue(parser.parseLine("[ 1.1] /dev/input/event2: EV_SYN SYN_REPORT 00000000").isEmpty())
    }
}
