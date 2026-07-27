package com.skyline.glider.core

/**
 * Every tunable number in Skyline Glider lives here.
 *
 * World units are metres. The player is pinned at z = 0 and the world streams
 * toward them; `distance` is how far the player has travelled along the skyline.
 */
object Cfg {

    // ---- Track geometry -------------------------------------------------
    const val LANE_WIDTH = 2.2f          // metres between rooftop lanes
    const val ROOF_HALF_WIDTH = 3.9f     // rooftop edge, in metres from centre
    const val FALL_OFF_X = 3.45f         // beyond this the player slips off the edge

    // ---- Camera / projection -------------------------------------------
    const val CAM_DEPTH = 9.5f           // focal depth: smaller = wider FOV
    const val DRAW_DISTANCE = 135f
    const val SPAWN_AHEAD = 130f
    const val DESPAWN_BEHIND = -9f

    // ---- Speed ----------------------------------------------------------
    const val BASE_SPEED = 13.5f
    const val MAX_SPEED = 34f
    const val SPEED_PER_METRE = 0.0032f  // +1 m/s every ~312 m
    const val BOOST_MULT = 1.7f
    const val GLIDE_FORWARD_MULT = 1.12f

    // ---- Player physics --------------------------------------------------
    const val GRAVITY = -27f
    const val JUMP_V = 10.8f
    const val FAST_FALL_V = -20f
    const val GLIDE_GRAVITY = -4.6f
    const val GLIDE_MIN_VY = -2.6f
    const val GLIDE_LIFT = 4.2f          // upward kick when the wingsuit snaps open
    const val GLIDE_MAX_TIME = 2.7f
    const val SLIDE_TIME = 0.78f
    const val LANE_SHIFT_SPEED = 8.5f    // lanes traversed per second

    const val PLAYER_HEIGHT = 1.8f
    const val SLIDE_HEIGHT = 0.85f
    const val PLAYER_HALF_W = 0.38f

    // ---- Power-ups -------------------------------------------------------
    const val MAGNET_TIME = 9f
    const val SHIELD_TIME = 14f
    const val BOOST_TIME = 6.5f
    const val MAGNET_RADIUS = 11f
    const val INVULN_AFTER_SHIELD = 1.2f

    // ---- Economy ---------------------------------------------------------
    const val COIN_VALUE = 1
    const val SCORE_PER_METRE = 1
    const val SCORE_PER_COIN = 5
    const val REVIVE_COST = 250

    // ---- Feel ------------------------------------------------------------
    const val NEAR_MISS_X = 1.15f
    const val SWIPE_THRESHOLD_DP = 34f
    const val MAX_FRAME_DT = 0.05f
}
