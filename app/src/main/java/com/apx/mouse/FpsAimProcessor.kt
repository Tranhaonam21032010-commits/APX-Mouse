package com.apx.mouse

import kotlin.math.hypot

/**
 * Converts one relative mouse sample into a bounded touch target around the
 * configured FPS-camera center. This is a conservative APX implementation;
 * K7XD's undocumented resistance and edge-reset curves are intentionally not
 * guessed here.
 */
internal class FpsAimProcessor(
    private val centerX: Float,
    private val centerY: Float,
    private val radius: Float,
    private val sensitivityX: Float = 1f,
    private val sensitivityY: Float = 1f,
    private val invertX: Boolean = false,
    private val invertY: Boolean = false,
    private val maxStepPx: Float = 120f
) {
    data class Point(val x: Float, val y: Float)

    fun target(rawDx: Float, rawDy: Float): Point? {
        if (!rawDx.isFinite() || !rawDy.isFinite() ||
            !centerX.isFinite() || !centerY.isFinite() ||
            !radius.isFinite() || radius <= 0f
        ) return null

        var dx = rawDx * sensitivityX.coerceIn(0.1f, 3f) * if (invertX) -1f else 1f
        var dy = rawDy * sensitivityY.coerceIn(0.1f, 3f) * if (invertY) -1f else 1f
        val stepLimit = maxStepPx.takeIf { it.isFinite() && it > 0f } ?: 120f
        dx = dx.coerceIn(-stepLimit, stepLimit)
        dy = dy.coerceIn(-stepLimit, stepLimit)

        val distance = hypot(dx, dy)
        if (distance <= 0f) return null
        if (distance > radius) {
            val scale = radius / distance
            dx *= scale
            dy *= scale
        }
        return Point(centerX + dx, centerY + dy)
    }
}
