package com.skyline.glider.core

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Streams the rooftop course ahead of the player, one hand-authored "pattern"
 * at a time.
 *
 * Two rules keep it fair:
 *  - every pattern leaves at least one survivable line through it;
 *  - spacing is expressed in *seconds of reaction time*, not metres, so the
 *    course keeps breathing room as the run accelerates toward 34 m/s.
 */
class LevelGen(private val rng: Random) {

    private enum class Pattern {
        SINGLE_BLOCK, DOUBLE_BLOCK, SLIDE_BAR, ROOF_GAP,
        WIND_CORRIDOR, DRONE_PATROL, COIN_TRAIL, WEAVE
    }

    /** Nothing spawns before this, so the player always gets a clean start. */
    var cursor = 55f
        private set

    fun reset() {
        cursor = 55f
    }

    /**
     * Fills the world with patterns until the course reaches [targetZ].
     * [difficulty] is 0f at the start of a run and saturates at 1f.
     */
    fun fill(
        targetZ: Float,
        difficulty: Float,
        speed: Float,
        obstacles: MutableList<Obstacle>,
        pickups: MutableList<Pickup>,
        gaps: MutableList<Gap>,
        winds: MutableList<WindZone>
    ) {
        var guard = 0
        while (cursor < targetZ && guard++ < 40) {
            cursor = emit(cursor, difficulty, speed, obstacles, pickups, gaps, winds)
        }
    }

    private fun pick(difficulty: Float): Pattern {
        // Weights shift toward hazards as the run gets longer.
        val weights = linkedMapOf(
            Pattern.COIN_TRAIL to 16f - 8f * difficulty,
            Pattern.SINGLE_BLOCK to 16f,
            Pattern.DOUBLE_BLOCK to 8f + 8f * difficulty,
            Pattern.SLIDE_BAR to 9f + 5f * difficulty,
            Pattern.ROOF_GAP to 8f + 8f * difficulty,
            Pattern.DRONE_PATROL to 6f + 11f * difficulty,
            Pattern.WIND_CORRIDOR to 5f + 8f * difficulty,
            Pattern.WEAVE to 3f + 12f * difficulty
        )
        val total = weights.values.sum()
        var roll = rng.nextFloat() * total
        for ((p, w) in weights) {
            roll -= w
            if (roll <= 0f) return p
        }
        return Pattern.SINGLE_BLOCK
    }

    private fun emit(
        z: Float,
        difficulty: Float,
        speed: Float,
        obstacles: MutableList<Obstacle>,
        pickups: MutableList<Pickup>,
        gaps: MutableList<Gap>,
        winds: MutableList<WindZone>
    ): Float {
        // Breathing room between patterns, in metres, derived from reaction time.
        val restSeconds = 0.95f - 0.35f * difficulty
        val rest = max(12f, restSeconds * speed) + rng.nextFloat() * 0.35f * speed

        // Spacing between hazards *inside* a pattern.
        val stride = max(9f, 0.5f * speed)

        val blockerKind = { if (rng.nextFloat() < 0.55f) ObstacleKind.WATER_TOWER else ObstacleKind.ANTENNA }

        return when (pick(difficulty)) {

            Pattern.COIN_TRAIL -> {
                val lane = randLane()
                val n = 6 + rng.nextInt(6)
                for (i in 0 until n) {
                    pickups += Pickup(z + i * 2.2f, PickupKind.COIN, lane * Cfg.LANE_WIDTH, 1.0f)
                }
                maybePowerUp(z + n * 2.2f + 3f, lane, pickups, difficulty)
                z + n * 2.2f + rest
            }

            Pattern.SINGLE_BLOCK -> {
                val lane = randLane()
                obstacles += Obstacle(z, lane, if (rng.nextFloat() < 0.5f) ObstacleKind.AC_UNIT else blockerKind())
                // Reward the dodge with coins in a neighbouring lane.
                val safe = otherLane(lane)
                for (i in 0 until 5) {
                    pickups += Pickup(z + i * 2.1f, PickupKind.COIN, safe * Cfg.LANE_WIDTH, 1.0f)
                }
                z + rest
            }

            Pattern.DOUBLE_BLOCK -> {
                val free = randLane()
                for (lane in -1..1) {
                    if (lane == free) continue
                    obstacles += Obstacle(z, lane, blockerKind())
                }
                for (i in 0 until 5) {
                    pickups += Pickup(z - 4f + i * 2.1f, PickupKind.COIN, free * Cfg.LANE_WIDTH, 1.0f)
                }
                maybePowerUp(z + 6f, free, pickups, difficulty)
                z + rest
            }

            Pattern.SLIDE_BAR -> {
                // One bar spanning the whole roof: the only way through is a slide.
                obstacles += Obstacle(z, 0, ObstacleKind.PIPE_BAR, widthOverride = Cfg.ROOF_HALF_WIDTH)
                val lane = randLane()
                for (i in 0 until 4) {
                    pickups += Pickup(z - 2f + i * 2.0f, PickupKind.COIN, lane * Cfg.LANE_WIDTH, 0.55f)
                }
                z + rest
            }

            Pattern.ROOF_GAP -> {
                // Scaled to speed so it always demands a jump but never an
                // impossible one (a plain jump covers ~0.8 s of travel).
                val len = max(6.5f, speed * (0.38f + difficulty * 0.13f)) + rng.nextFloat() * 2.5f
                gaps += Gap(z, z + len)
                // Coin arc across the void: the payoff for a clean wingsuit line.
                val steps = 7
                for (i in 0 until steps) {
                    val t = i / (steps - 1f)
                    val arc = 1.1f + 2.6f * kotlin.math.sin(t * Math.PI.toFloat())
                    pickups += Pickup(z - 1.5f + t * (len + 3f), PickupKind.COIN, 0f, arc)
                }
                if (rng.nextFloat() < 0.35f) {
                    obstacles += Obstacle(z + len + stride, randLane(), ObstacleKind.AC_UNIT)
                }
                z + len + rest
            }

            Pattern.WIND_CORRIDOR -> {
                // Roughly two seconds inside the gust, whatever the speed.
                val len = max(20f, speed * 1.5f) + rng.nextFloat() * 0.8f * speed
                val dir = if (rng.nextBoolean()) 1f else -1f
                winds += WindZone(z, z + len, dir, 0.42f + difficulty * 0.34f)
                // Coins sit upwind, so leaning into the gust pays.
                val lane = if (dir > 0f) -1 else 1
                var i = 0
                while (i * 2.4f < len) {
                    pickups += Pickup(z + 2f + i * 2.4f, PickupKind.COIN, lane * Cfg.LANE_WIDTH, 1.0f)
                    i++
                }
                if (difficulty > 0.35f) {
                    obstacles += Obstacle(
                        z + len * 0.6f, randLane(), ObstacleKind.DRONE,
                        phase = rng.nextFloat() * 6.28f, swing = 1.6f
                    )
                }
                z + len + rest
            }

            Pattern.DRONE_PATROL -> {
                val n = 1 + rng.nextInt(if (difficulty > 0.5f) 3 else 2)
                var cur = z
                for (i in 0 until n) {
                    obstacles += Obstacle(
                        wz = cur,
                        lane = randLane(),
                        kind = ObstacleKind.DRONE,
                        phase = rng.nextFloat() * 6.28f,
                        swing = 1.4f + rng.nextFloat() * 1.4f
                    )
                    cur += stride * (0.9f + rng.nextFloat() * 0.5f)
                }
                val lane = randLane()
                for (i in 0 until 4) {
                    pickups += Pickup(cur + i * 2.1f, PickupKind.COIN, lane * Cfg.LANE_WIDTH, 1.0f)
                }
                cur + rest
            }

            Pattern.WEAVE -> {
                // Alternating blockers that force a left-right-left rhythm.
                var lane = randLane()
                val steps = 3 + rng.nextInt(2)
                val gapBetween = stride * 1.25f
                for (i in 0 until steps) {
                    obstacles += Obstacle(z + i * gapBetween, lane, blockerKind())
                    val next = otherLane(lane)
                    for (c in 0 until 3) {
                        pickups += Pickup(
                            z + i * gapBetween + 2f + c * 2f,
                            PickupKind.COIN, next * Cfg.LANE_WIDTH, 1.0f
                        )
                    }
                    lane = next
                }
                if (rng.nextFloat() < 0.4f) {
                    obstacles += Obstacle(
                        z + steps * gapBetween - stride * 0.5f, 0,
                        ObstacleKind.PIPE_BAR, widthOverride = Cfg.ROOF_HALF_WIDTH
                    )
                }
                z + steps * gapBetween + rest
            }
        }
    }

    private fun maybePowerUp(z: Float, lane: Int, pickups: MutableList<Pickup>, difficulty: Float) {
        if (rng.nextFloat() > 0.13f + difficulty * 0.05f) return
        val kind = when (rng.nextInt(3)) {
            0 -> PickupKind.MAGNET
            1 -> PickupKind.SHIELD
            else -> PickupKind.BOOST
        }
        pickups += Pickup(z, kind, lane * Cfg.LANE_WIDTH, 1.25f)
    }

    private fun randLane(): Int = rng.nextInt(3) - 1

    private fun otherLane(lane: Int): Int {
        val options = (-1..1).filter { it != lane }
        return options[rng.nextInt(options.size)]
    }

    companion object {
        fun laneOf(x: Float): Int = (x / Cfg.LANE_WIDTH).roundToInt().coerceIn(-1, 1)
    }
}
