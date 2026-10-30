
package com.apx.mouse

import android.content.Context
import android.view.InputDevice
import android.view.MotionEvent
import android.view.KeyEvent

class MouseService(private val context: Context) {

    fun handleMouse(event: MotionEvent): String {
        return when (event.actionMasked) {
            MotionEvent.ACTION_MOVE ->
                "Mouse X: ${event.x}, Y: ${event.y}"

            MotionEvent.ACTION_DOWN ->
                "Mouse Pressed"

            MotionEvent.ACTION_UP ->
                "Mouse Released"

            else -> "Mouse Event"
        }
    }

    fun handleKeyboard(event: KeyEvent): String {
        return when (event.action) {
            KeyEvent.ACTION_DOWN ->
                "Key Pressed: ${event.keyCode}"

            KeyEvent.ACTION_UP ->
                "Key Released: ${event.keyCode}"

            else -> "Keyboard Event"
        }
    }
}
