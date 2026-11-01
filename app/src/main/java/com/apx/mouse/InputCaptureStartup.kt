package com.apx.mouse

/** Converts an immediate getevent process exit into an actionable startup error. */
internal object InputCaptureStartup {
    fun failure(exitCode: Int, output: String): String {
        val detail = output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .takeLast(4)
            .joinToString(" | ")
            .take(500)
        val lower = detail.lowercase()
        val reason = when {
            "permission denied" in lower || "operation not permitted" in lower ->
                "Không có quyền đọc thiết bị input. Hãy kiểm tra quyền Shizuku và quyền truy cập /dev/input."
            "no such file" in lower || "cannot open" in lower && "not found" in lower ->
                "Thiết bị input đã chọn không còn tồn tại. Hãy quét và chọn lại thiết bị."
            else -> "getevent thoát ngay (mã $exitCode)" +
                if (detail.isNotBlank()) ": $detail" else ". Kiểm tra Shizuku và thiết bị đã chọn."
        }
        return "ERROR: $reason"
    }
}
