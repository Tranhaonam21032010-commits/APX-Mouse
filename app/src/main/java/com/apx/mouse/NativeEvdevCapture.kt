package com.apx.mouse

/**
 * Optional native raw-input reader. It grabs only explicitly selected event nodes.
 */
object NativeEvdevCapture {
    interface Callback {
        fun onNativeInputEventBatch(eventTuples: IntArray)
        fun onNativeInputCaptureEnded(errorCode: Int)
    }

    init { System.loadLibrary("apx_input") }

    external fun startNative(eventPaths: Array<String>, callback: Callback): Int
    external fun stopNative()
    external fun isRunningNative(): Boolean
}
