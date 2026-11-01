package com.apx.mouse

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputEngineTest {
    private class RecordingSink : InputEngine.CommandSink {
        val commands = mutableListOf<InputEngine.TouchCommand>()
        override fun onCommand(command: InputEngine.TouchCommand) {
            commands += command
        }
    }

    @Test fun mappedKeyboardKeyProducesOneDownAndOneUp() {
        val sink = RecordingSink()
        val engine = InputEngine(
            sink,
            InputEngine.Config(
                screenWidth = 1000,
                screenHeight = 500,
                keyBindings = mapOf(
                    KeyEvent.KEYCODE_SPACE to InputEngine.MouseButtonBinding(1, 0.8f, 0.25f)
                )
            )
        )

        assertTrue(engine.onMappedKeyCode(KeyEvent.KEYCODE_SPACE, true))
        // Repeated ACTION_DOWN while held must not create a second pointer.
        assertTrue(engine.onMappedKeyCode(KeyEvent.KEYCODE_SPACE, true))
        assertTrue(engine.onMappedKeyCode(KeyEvent.KEYCODE_SPACE, false))

        assertEquals(
            listOf(
                InputEngine.TouchCommand.Down(1, 799.2f, 124.75f),
                InputEngine.TouchCommand.Up(1)
            ),
            sink.commands
        )
    }

    @Test fun wasdCreatesHeldJoystickAndReleasesAfterLastDirection() {
        val sink = RecordingSink()
        val engine = InputEngine(
            sink,
            InputEngine.Config(screenWidth = 1000, screenHeight = 500)
        )

        assertTrue(engine.onMappedKeyCode(KeyEvent.KEYCODE_W, true))
        assertTrue(engine.onMappedKeyCode(KeyEvent.KEYCODE_D, true))
        assertTrue(engine.onMappedKeyCode(KeyEvent.KEYCODE_W, false))
        assertTrue(engine.onMappedKeyCode(KeyEvent.KEYCODE_D, false))

        assertEquals(InputEngine.TouchCommand.Down(0, 179.82f, 389.22f), sink.commands[0])
        assertTrue(sink.commands[1] is InputEngine.TouchCommand.Move)
        assertTrue(sink.commands[2] is InputEngine.TouchCommand.Move)
        assertEquals(InputEngine.TouchCommand.Move(0, 239.7f, 329.34f), sink.commands[2])
        assertEquals(InputEngine.TouchCommand.Up(0), sink.commands.last())
    }

    @Test fun mouseButtonHoldMovesItsMappedPointerAndThenReleases() {
        val sink = RecordingSink()
        val engine = InputEngine(
            sink,
            InputEngine.Config(
                screenWidth = 1000,
                screenHeight = 500,
                keyBindings = mapOf(
                    KeyEvent.KEYCODE_BUTTON_2 to InputEngine.MouseButtonBinding(2, 0.75f, 0.5f)
                )
            )
        )

        assertTrue(engine.onRawMouseButton("BTN_RIGHT", true))
        assertTrue(engine.onRawMouseMotion(12, -8))
        assertTrue(engine.onRawMouseButton("BTN_RIGHT", false))

        assertEquals(InputEngine.TouchCommand.Down(2, 749.25f, 249.5f), sink.commands[0])
        assertEquals(InputEngine.TouchCommand.AimDelta(12f, -8f), sink.commands[1])
        assertEquals(InputEngine.TouchCommand.Move(2, 761.25f, 241.5f), sink.commands[2])
        assertEquals(InputEngine.TouchCommand.Up(2), sink.commands[3])
    }

    @Test fun resetReleasesKeyboardAndJoystickPointers() {
        val sink = RecordingSink()
        val engine = InputEngine(
            sink,
            InputEngine.Config(
                screenWidth = 1000,
                screenHeight = 500,
                keyBindings = mapOf(
                    KeyEvent.KEYCODE_SPACE to InputEngine.MouseButtonBinding(1, 0.5f, 0.5f)
                )
            )
        )
        engine.onMappedKeyCode(KeyEvent.KEYCODE_SPACE, true)
        engine.onMappedKeyCode(KeyEvent.KEYCODE_W, true)
        engine.reset()

        assertFalse(engine.pressedKeyCodes().isNotEmpty())
        assertTrue(sink.commands.takeLast(2).contains(InputEngine.TouchCommand.Up(0)))
        assertTrue(sink.commands.takeLast(2).contains(InputEngine.TouchCommand.Up(1)))
    }
}
