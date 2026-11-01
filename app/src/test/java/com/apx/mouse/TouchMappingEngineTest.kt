package com.apx.mouse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchMappingEngineTest {
    @Test fun diagonalsAreMappedToEightDistinctDirections() {
        val mapper = JoystickDirectionMapper()
        val cases = listOf(
            setOf(51) to JoystickDirection.UP,
            setOf(47) to JoystickDirection.DOWN,
            setOf(29) to JoystickDirection.LEFT,
            setOf(32) to JoystickDirection.RIGHT,
            setOf(51, 29) to JoystickDirection.UP_LEFT,
            setOf(29, 47) to JoystickDirection.DOWN_LEFT,
            setOf(47, 32) to JoystickDirection.DOWN_RIGHT,
            setOf(51, 32) to JoystickDirection.UP_RIGHT
        )
        cases.forEach { (keys, expected) -> assertEquals(expected, mapper.resolve(keys)) }
    }

    @Test fun oppositeKeysCancelAndNoKeysAreCenter() {
        val mapper = JoystickDirectionMapper()
        assertEquals(JoystickDirection.CENTER, mapper.resolve(setOf(51, 47)))
        assertEquals(JoystickDirection.CENTER, mapper.resolve(emptySet()))
    }

    @Test fun touchSlotsNeverExceedConfiguredCapacityAndReleaseCanBeReused() {
        val slots = TouchSlotAllocator(4)
        val ids = (1..4).mapNotNull { slots.acquire(it) }
        assertEquals(4, ids.distinct().size)
        assertFalse(slots.acquire(5) != null)
        assertTrue(slots.release(2))
        assertTrue(slots.acquire(5) != null)
    }

    @Test fun dragBindingKeepsPointerUntilExplicitRelease() {
        val slots = TouchSlotAllocator(4)
        val pointer = slots.acquire(100) ?: error("slot expected")
        assertEquals(pointer, slots.pointerFor(100))
        assertEquals(pointer, slots.pointerFor(100))
        assertTrue(slots.release(100))
        assertEquals(null, slots.pointerFor(100))
    }
}
