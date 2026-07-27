package com.skyline.glider.data

import androidx.compose.ui.graphics.Color
import com.skyline.glider.core.RunStats

// =========================================================================
// Characters / skins
// =========================================================================

data class GliderCharacter(
    val id: String,
    val name: String,
    val price: Int,
    val tagline: String,
    val suit: Color,
    val accent: Color,
    val skin: Color,
    val wing: Color
)

val CHARACTERS: List<GliderCharacter> = listOf(
    GliderCharacter(
        "nova", "Nova", 0, "Rooftop courier. Started it all.",
        suit = Color(0xFF2F6BFF), accent = Color(0xFF3BE8FF),
        skin = Color(0xFFE8B58A), wing = Color(0xFF3BE8FF)
    ),
    GliderCharacter(
        "kite", "Kite", 750, "Reads thermals like street signs.",
        suit = Color(0xFFFF6B3D), accent = Color(0xFFFFC93C),
        skin = Color(0xFF8A5A3C), wing = Color(0xFFFFC93C)
    ),
    GliderCharacter(
        "rook", "Rook", 1600, "Ex-drone pilot. Holds a grudge.",
        suit = Color(0xFF2F3A56), accent = Color(0xFF7CF6C8),
        skin = Color(0xFFC98F6E), wing = Color(0xFF7CF6C8)
    ),
    GliderCharacter(
        "vex", "Vex", 2800, "Paints the skyline on the way down.",
        suit = Color(0xFF7A2BFF), accent = Color(0xFFFF3D9A),
        skin = Color(0xFFF0C9A8), wing = Color(0xFFFF3D9A)
    ),
    GliderCharacter(
        "halo", "Halo", 4500, "Never once touched the street.",
        suit = Color(0xFFF2F4FF), accent = Color(0xFF3BE8FF),
        skin = Color(0xFF6B4A38), wing = Color(0xFFDDE6FF)
    ),
    GliderCharacter(
        "zephyr", "Zephyr", 7000, "The wind zones are her idea.",
        suit = Color(0xFF0F3D3E), accent = Color(0xFF9CFF57),
        skin = Color(0xFFE8C6A0), wing = Color(0xFF9CFF57)
    )
)

fun characterById(id: String): GliderCharacter =
    CHARACTERS.firstOrNull { it.id == id } ?: CHARACTERS.first()

// =========================================================================
// Missions
// =========================================================================

enum class Metric { DISTANCE, COINS, GLIDE, DRONES, SLIDES, JUMPS, POWERUPS, NEAR_MISS, WIND, RUNS }

data class MissionDef(
    val id: String,
    val metric: Metric,
    /** Progressive targets; harder tiers unlock as the player clears missions. */
    val tiers: List<Int>,
    val label: String,
    val rewardPerTier: Int
)

val MISSION_POOL: List<MissionDef> = listOf(
    MissionDef("dist", Metric.DISTANCE, listOf(1500, 4000, 9000), "Travel %d m across the skyline", 60),
    MissionDef("coins", Metric.COINS, listOf(150, 400, 900), "Collect %d coins", 55),
    MissionDef("glide", Metric.GLIDE, listOf(100, 300, 700), "Glide %d m in the wingsuit", 70),
    MissionDef("drones", Metric.DRONES, listOf(15, 40, 90), "Slip past %d drones", 80),
    MissionDef("slide", Metric.SLIDES, listOf(25, 60, 140), "Slide under %d obstacles", 50),
    MissionDef("jump", Metric.JUMPS, listOf(40, 100, 220), "Leap %d times", 45),
    MissionDef("power", Metric.POWERUPS, listOf(8, 20, 45), "Grab %d power-ups", 65),
    MissionDef("close", Metric.NEAR_MISS, listOf(20, 55, 120), "Pull off %d near misses", 90),
    MissionDef("wind", Metric.WIND, listOf(250, 650, 1400), "Ride %d m of wind zones", 75),
    MissionDef("runs", Metric.RUNS, listOf(5, 15, 40), "Finish %d runs", 40)
)

fun missionDef(id: String): MissionDef? = MISSION_POOL.firstOrNull { it.id == id }

data class MissionState(
    val defId: String,
    val target: Int,
    var progress: Int,
    var claimed: Boolean
) {
    val def: MissionDef? get() = missionDef(defId)
    val text: String get() = def?.label?.format(target) ?: defId
    val reward: Int
        get() {
            val d = def ?: return 50
            val tier = d.tiers.indexOf(target).coerceAtLeast(0)
            return d.rewardPerTier * (tier + 1)
        }
    val done: Boolean get() = progress >= target
    val fraction: Float get() = (progress.toFloat() / target).coerceIn(0f, 1f)
}

/** Pulls the value a single run contributed toward a given metric. */
fun RunStats.valueFor(metric: Metric): Int = when (metric) {
    Metric.DISTANCE -> distance.toInt()
    Metric.COINS -> coins
    Metric.GLIDE -> glideMetres.toInt()
    Metric.DRONES -> dronesPassed
    Metric.SLIDES -> slides
    Metric.JUMPS -> jumps
    Metric.POWERUPS -> powerups
    Metric.NEAR_MISS -> nearMisses
    Metric.WIND -> windMetres.toInt()
    Metric.RUNS -> 1
}

// =========================================================================
// Daily rewards
// =========================================================================

val DAILY_REWARDS: List<Int> = listOf(100, 150, 250, 400, 600, 900, 1500)
