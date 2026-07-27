package com.skyline.glider.core

import androidx.compose.ui.graphics.Color
import kotlin.math.sin

enum class Phase { READY, PLAYING, PAUSED, OVER }

enum class PState { RUN, JUMP, GLIDE, SLIDE }

enum class ObstacleKind {
    /** Low box on the roof — jump or glide over it. */
    AC_UNIT,

    /** Scaffolding bar spanning the roof — slide under it. */
    PIPE_BAR,

    /** Tall tank — change lane. */
    WATER_TOWER,

    /** Narrow mast — change lane. */
    ANTENNA,

    /** Patrol drone that drifts sideways at chest height — slide or glide over. */
    DRONE
}

enum class PickupKind { COIN, MAGNET, SHIELD, BOOST }

class Obstacle(
    val wz: Float,
    val lane: Int,
    val kind: ObstacleKind,
    /** Phase offset so drones in the same cluster don't move in lockstep. */
    val phase: Float = 0f,
    /** Lateral sweep amplitude, metres. Drones only. */
    val swing: Float = 0f,
    /** Widths wider than a single lane (used by PIPE_BAR spans). */
    val widthOverride: Float = 0f
) {
    var alive = true
    var scored = false

    val baseX: Float = lane * Cfg.LANE_WIDTH

    fun x(t: Float): Float =
        if (kind == ObstacleKind.DRONE) baseX + sin(t * 1.45f + phase) * swing else baseX

    fun bob(t: Float): Float =
        if (kind == ObstacleKind.DRONE) sin(t * 2.3f + phase * 1.7f) * 0.24f else 0f

    val halfW: Float
        get() = if (widthOverride > 0f) widthOverride else when (kind) {
            ObstacleKind.AC_UNIT -> 0.85f
            ObstacleKind.PIPE_BAR -> 1.05f
            ObstacleKind.WATER_TOWER -> 0.78f
            ObstacleKind.ANTENNA -> 0.34f
            ObstacleKind.DRONE -> 0.62f
        }

    /** Bottom of the collision box, metres above the roof. */
    val yLow: Float
        get() = when (kind) {
            ObstacleKind.AC_UNIT -> 0f
            ObstacleKind.PIPE_BAR -> 1.15f
            ObstacleKind.WATER_TOWER -> 0f
            ObstacleKind.ANTENNA -> 0f
            ObstacleKind.DRONE -> 0.95f
        }

    val yHigh: Float
        get() = when (kind) {
            ObstacleKind.AC_UNIT -> 1.0f
            ObstacleKind.PIPE_BAR -> 3.8f
            ObstacleKind.WATER_TOWER -> 3.1f
            ObstacleKind.ANTENNA -> 2.5f
            ObstacleKind.DRONE -> 1.85f
        }

    /** Depth along z, used by the renderer's 3D boxes. */
    val depth: Float
        get() = when (kind) {
            ObstacleKind.AC_UNIT -> 1.3f
            ObstacleKind.PIPE_BAR -> 0.5f
            ObstacleKind.WATER_TOWER -> 1.5f
            ObstacleKind.ANTENNA -> 0.5f
            ObstacleKind.DRONE -> 1.0f
        }
}

class Pickup(
    /** Mutable so the magnet can reel it in along the track. */
    var wz: Float,
    val kind: PickupKind,
    var x: Float,
    var y: Float
) {
    var alive = true
    /** How far along the magnet has dragged this pickup, 0..1. */
    var pull = 0f
}

/** A missing stretch of rooftop. If the player is grounded here, they fall. */
class Gap(val z0: Float, val z1: Float)

/** A crosswind that shoves the player sideways while they're inside it. */
class WindZone(val z0: Float, val z1: Float, val dir: Float, val strength: Float)

class Particle(
    var x: Float,
    var y: Float,
    var z: Float,
    var vx: Float,
    var vy: Float,
    var vz: Float,
    var life: Float,
    val maxLife: Float,
    val color: Color,
    val size: Float,
    val gravity: Float = -9f
)

/** Everything measured during a single run; folded into missions when it ends. */
class RunStats {
    var distance = 0f
    var coins = 0
    var glideMetres = 0f
    var windMetres = 0f
    var jumps = 0
    var slides = 0
    var powerups = 0
    var dronesPassed = 0
    var nearMisses = 0

    fun reset() {
        distance = 0f; coins = 0; glideMetres = 0f; windMetres = 0f
        jumps = 0; slides = 0; powerups = 0; dronesPassed = 0; nearMisses = 0
    }
}

/** Transient banner text ("NEAR MISS!", "MISSION COMPLETE") shown mid-run. */
class Toast(val text: String, val color: Color, var life: Float = 1.6f)
