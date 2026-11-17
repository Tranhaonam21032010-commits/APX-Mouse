package com.apx.mouse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeInputEventParserTest {
    @Test fun groupsRelativeAxesUntilSynReport() {
        val parser=NativeInputEventParser()
        assertEquals(listOf(EvdevInputEventParser.Event.Relative("native:0",5,-2,0,0)),
            parser.parseBatch(intArrayOf(0,2,0,5,0,2,1,-2,0,0,0,0)))
    }
    @Test fun mapsKeyboardAndMouseButtons() {
        val parser=NativeInputEventParser()
        assertEquals(listOf(
            EvdevInputEventParser.Event.Key("native:0","KEY_W",true,false),
            EvdevInputEventParser.Event.MouseButton("native:0","BTN_LEFT",true),
            EvdevInputEventParser.Event.MouseButton("native:0","BTN_LEFT",false)
        ),parser.parseBatch(intArrayOf(0,1,17,1,0,1,272,1,0,1,272,0)))
    }
    @Test fun ignoresTouchscreenAbsoluteAndBtnTouch() {
        val parser=NativeInputEventParser()
        assertTrue(parser.parseBatch(intArrayOf(1,3,0x35,120,1,1,330,1,1,0,0,0)).isEmpty())
    }
    @Test fun separatesPendingRelativeFramesByDevice() {
        val parser=NativeInputEventParser()
        assertTrue(parser.parseBatch(intArrayOf(0,2,0,10)).isEmpty())
        assertEquals(listOf(EvdevInputEventParser.Event.Relative("native:1",20,0,0,0)),
            parser.parseBatch(intArrayOf(1,2,0,20,1,0,0,0)))
        assertEquals(listOf(EvdevInputEventParser.Event.Relative("native:0",10,0,0,0)),
            parser.parseBatch(intArrayOf(0,0,0,0)))
    }
    @Test fun resetDiscardsPartialFrame() {
        val parser=NativeInputEventParser()
        parser.parseBatch(intArrayOf(0,2,0,5)); parser.reset()
        assertTrue(parser.parseBatch(intArrayOf(0,0,0,0)).isEmpty())
    }
}
