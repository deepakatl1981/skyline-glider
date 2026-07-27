package com.skyline.glider.core

/** Everything the engine wants the outside world (audio, haptics, UI) to react to. */
enum class GameEvent {
    JUMP,
    GLIDE_OPEN,
    SLIDE,
    LANE_CHANGE,
    COIN,
    POWERUP,
    NEAR_MISS,
    SHIELD_BREAK,
    LAND,
    DEATH,
    REVIVE
}
