package com.apx.mouse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseKeyValidatorTest {
    @Test
    fun acceptsProvidedSampleKeys() {
        assertTrue(LicenseKeyValidator.isValid("APX-VIP-2026"))
        assertTrue(LicenseKeyValidator.isValid("APX-PRO-9999"))
    }

    @Test
    fun trimsSurroundingWhitespace() {
        assertTrue(LicenseKeyValidator.isValid("  APX-VIP-2026  "))
    }

    @Test
    fun rejectsUnknownOrEmptyKeys() {
        assertFalse(LicenseKeyValidator.isValid(""))
        assertFalse(LicenseKeyValidator.isValid("APX-INVALID"))
    }

    @Test
    fun keyMatchingRemainsCaseSensitive() {
        assertFalse(LicenseKeyValidator.isValid("apx-vip-2026"))
    }
}
