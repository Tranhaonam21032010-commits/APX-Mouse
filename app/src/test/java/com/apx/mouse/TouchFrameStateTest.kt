package com.apx.mouse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TouchFrameStateTest {
    @Test fun firstPointerUsesDownAndLastPointerUsesUp() {
        val state = TouchFrameState()
        val down = state.down(1, 120f, 240f)!!
        assertEquals(TouchFrameState.ACTION_DOWN, down.action)
        assertEquals(listOf(1), down.pointers.map { it.id })

        val up = state.up(1)!!
        assertEquals(TouchFrameState.ACTION_UP, up.action)
        assertEquals(1, state.activeCount())
        assertEquals(0, state.activeCount())
    }

    @Test fun secondPointerUsesIndexedPointerDownAndUpActions() {
        val state = TouchFrameState()
        state.down(2, 10f, 20f)
        val secondDown = state.down(7, 30f, 40f)!!
        assertEquals(TouchFrameState.ACTION_POINTER_DOWN or (1 shl 8), secondDown.action)
        assertEquals(listOf(2, 7), secondDown.pointers.map { it.id })

        val secondUp = state.up(7)!!
        assertEquals(TouchFrameState.ACTION_POINTER_UP or (1 shl 8), secondUp.action)
        assertEquals(listOf(2, 7), secondUp.pointers.map { it.id })
        assertEquals(1, state.activeCount())
    }

    @Test fun moveUpdatesOnlyRequestedPointerAndRejectsUnknownPointers() {
        val state = TouchFrameState()
        state.down(1, 10f, 10f)
        state.down(2, 20f, 20f)
        val move = state.move(2, 99f, 101f)!!
        assertEquals(TouchFrameState.ACTION_MOVE, move.action)
        assertEquals(10f, move.pointers[0].x)
        assertEquals(99f, move.pointers[1].x)
        assertNull(state.move(3, 1f, 1f))
    }

    @Test fun invalidCoordinatesDuplicateIdsAndCapacityAreRejected() {
        val state = TouchFrameState(maxPointers = 2)
        state.down(1, 1f, 1f)
        assertNull(state.down(1, 2f, 2f))
        assertNull(state.down(2, Float.NaN, 2f))
        state.down(2, 2f, 2f)
        assertNull(state.down(3, 3f, 3f))
    }

    @Test fun cancelReleasesAllPointers() {
        val state = TouchFrameState()
        state.down(1, 1f, 1f)
        state.down(2, 2f, 2f)
        val cancel = state.cancel()!!
        assertEquals(TouchFrameState.ACTION_CANCEL, cancel.action)
        assertEquals(2, cancel.pointers.size)
        assertEquals(0, state.activeCount())
        assertNull(state.cancel())
    }
}
