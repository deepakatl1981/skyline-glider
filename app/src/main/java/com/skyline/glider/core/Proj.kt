package com.skyline.glider.core

import kotlin.math.max

/**
 * Pinhole projection for the pseudo-3D rooftop track.
 *
 * The camera sits behind and above the player looking down the skyline.
 * Scale falls off as `CAM_DEPTH / (CAM_DEPTH + z)`, which is what gives the
 * runner its Subway-Surfers-style vanishing point.
 */
class Proj(val w: Float, val h: Float) {

    val cx = w * 0.5f
    val horizonY = h * 0.355f
    val groundY = h * 0.855f

    /** Pixels per metre at the player's depth (z = 0). */
    val ppm = w * 0.101f

    private val span = groundY - horizonY

    fun scale(z: Float): Float {
        val zz = max(z, -Cfg.CAM_DEPTH + 3f)
        return Cfg.CAM_DEPTH / (Cfg.CAM_DEPTH + zz)
    }

    fun x(worldX: Float, z: Float): Float = cx + worldX * scale(z) * ppm

    fun y(worldY: Float, z: Float): Float {
        val s = scale(z)
        return horizonY + span * s - worldY * s * ppm
    }

    /** Convert a length in metres to pixels at depth z. */
    fun len(metres: Float, z: Float): Float = metres * scale(z) * ppm
}
