package com.apx.mouse

import org.junit.Assert.assertTrue
import org.junit.Test

class InputCaptureStartupTest {
    @Test fun reportsPermissionFailureInsteadOfSuccessfulCapture() {
        val result = InputCaptureStartup.failure(1, "could not open /dev/input/event4: Permission denied")
        assertTrue(result.startsWith("ERROR:"))
        assertTrue(result.contains("quyền"))
    }

    @Test fun reportsMissingDeviceClearly() {
        val result = InputCaptureStartup.failure(1, "No such file or directory")
        assertTrue(result.contains("không còn tồn tại"))
    }

    @Test fun preservesUsefulDiagnosticForUnknownFailure() {
        val result = InputCaptureStartup.failure(7, "unsupported option")
        assertTrue(result.contains("mã 7"))
        assertTrue(result.contains("unsupported option"))
    }
}