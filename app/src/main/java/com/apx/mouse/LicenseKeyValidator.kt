package com.apx.mouse

/** Local demo-key validation only. This is not a secure online licensing system. */
object LicenseKeyValidator {
    private val sampleKeys = setOf("APX-VIP-2026", "APX-PRO-9999")

    fun isValid(key: String): Boolean = key.trim() in sampleKeys
}
