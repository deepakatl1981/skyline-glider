package com.skyline.glider.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.random.Random

/**
 * The simulation. Owns world state and advances it one frame at a time.
 *
 * It knows nothing about Compose layout, audio files or persistence — it only
 * raises [GameEvent]s and exposes read-only state the renderer draws from.
 */
class GameEngine(seed: Long = System.nanoTime()) {

    private val rng = Random(seed)
    private val gen = LevelGen(rng)

    var onEvent: (GameEvent) -> Unit = {}

    // ---- Observable state (read by HUD composables) ---------------------
    var phase by mutableStateOf(Phase.READY)
        private set
    var uiDistance by mutableIntStateOf(0)
        private set
    var uiCoins by mutableIntStateOf(0)
        private set
    var uiScore by mutableIntStateOf(0)
        private set
    var uiMagnet by mutableFloatStateOf(0f)
        private set
    var uiShield by mutableFloatStateOf(0f)
        private set
    var uiBoost by mutableFloatStateOf(0f)
        private set
    var uiSpeed by mutableFloatStateOf(0f)
        private set
    var canRevive by mutableStateOf(true)
        private set
    val toasts = mutableStateListOf<Toast>()

    // ---- World -----------------------------------------------------------
    val obstacles = ArrayList<Obstacle>(96)
    val pickups = ArrayList<Pickup>(256)
    val gaps = ArrayList<Gap>(24)
    val winds = ArrayList<WindZone>(12)
    val particles = ArrayList<Particle>(256)

    val stats = RunStats()

    // ---- Player ----------------------------------------------------------
    var time = 0f
        private set
    var distance = 0f
        private set
    var speed = Cfg.BASE_SPEED
        private set
    var lane = 0
        private set
    var laneF = 0f
        private set
    var windOffset = 0f
        private set
    var y = 0f
        private set
    var vy = 0f
        private set
    var state = PState.RUN
        private set
    var glideTime = 0f
        private set
    var glideUsed = false
        private set
    private var slideTimer = 0f
    private var queueSlideOnLand = false

    var magnetT = 0f
        private set
    var shieldT = 0f
        private set
    var boostT = 0f
        private set
    var invulnT = 0f
        private set
    var runCycle = 0f
        private set
    var tilt = 0f
        private set
    var shake = 0f
        private set
    var deathT = 0f
        private set
    /**
     * Lateral position in metres. Clamped at the parapet: crosswind can shove
     * the player into obstacles, but never silently off the roof.
     */
    val playerX: Float
        get() = ((laneF + windOffset) * Cfg.LANE_WIDTH).coerceIn(-Cfg.FALL_OFF_X, Cfg.FALL_OFF_X)
    val playerHeight: Float get() = if (state == PState.SLIDE) Cfg.SLIDE_HEIGHT else Cfg.PLAYER_HEIGHT
    val difficulty: Float get() = (distance / 1800f).coerceIn(0f, 1f)
    val activeWind: WindZone? get() = winds.firstOrNull { distance >= it.z0 && distance <= it.z1 }

    // =====================================================================
    // Lifecycle
    // =====================================================================

    fun startRun() {
        obstacles.clear(); pickups.clear(); gaps.clear(); winds.clear(); particles.clear()
        toasts.clear()
        gen.reset()
        stats.reset()
        time = 0f; distance = 0f; speed = Cfg.BASE_SPEED
        lane = 0; laneF = 0f; windOffset = 0f
        y = 0f; vy = 0f; state = PState.RUN
        glideTime = 0f; glideUsed = false; slideTimer = 0f; queueSlideOnLand = false
        magnetT = 0f; shieldT = 0f; boostT = 0f; invulnT = 0f
        deathT = 0f; shake = 0f; tilt = 0f
        canRevive = true
        gen.fill(Cfg.SPAWN_AHEAD, 0f, speed, obstacles, pickups, gaps, winds)
        syncUi()
        phase = Phase.PLAYING
    }

    fun pause() { if (phase == Phase.PLAYING) phase = Phase.PAUSED }
    fun resume() { if (phase == Phase.PAUSED) phase = Phase.PLAYING }

    /** Second chance: clears the road ahead and hands out temporary immunity. */
    fun revive() {
        if (!canRevive) return
        canRevive = false
        obstacles.removeAll { it.wz - distance < 70f }
        gaps.removeAll { it.z1 - distance < 40f }
        winds.removeAll { it.z0 - distance < 40f }
        lane = 0; laneF = 0f; windOffset = 0f
        y = 0f; vy = 0f; state = PState.RUN
        invulnT = 3f
        shieldT = max(shieldT, 4f)
        deathT = 0f
        phase = Phase.PLAYING
        onEvent(GameEvent.REVIVE)
        toast("BACK IN THE AIR", Palette.Neon)
    }

    // =====================================================================
    // Input
    // =====================================================================

    fun swipeLeft() = shiftLane(-1)
    fun swipeRight() = shiftLane(1)

    fun swipeUp() {
        if (phase != Phase.PLAYING) return
        when (state) {
            PState.RUN, PState.SLIDE -> {
                state = PState.JUMP
                vy = Cfg.JUMP_V
                slideTimer = 0f
                glideUsed = false
                stats.jumps++
                spawnDust(6)
                onEvent(GameEvent.JUMP)
            }
            PState.JUMP -> if (!glideUsed) openWingsuit()
            PState.GLIDE -> Unit
        }
    }

    fun swipeDown() {
        if (phase != Phase.PLAYING) return
        when (state) {
            PState.RUN -> startSlide()
            PState.JUMP, PState.GLIDE -> {
                // Dive: cut the glide and slam back down, sliding on impact.
                state = PState.JUMP
                vy = Cfg.FAST_FALL_V
                queueSlideOnLand = true
            }
            PState.SLIDE -> slideTimer = Cfg.SLIDE_TIME
        }
    }

    private fun startSlide() {
        state = PState.SLIDE
        slideTimer = Cfg.SLIDE_TIME
        stats.slides++
        spawnDust(8)
        onEvent(GameEvent.SLIDE)
    }

    private fun openWingsuit() {
        state = PState.GLIDE
        glideUsed = true
        glideTime = 0f
        vy = max(vy, Cfg.GLIDE_LIFT)
        onEvent(GameEvent.GLIDE_OPEN)
        repeat(10) {
            particles += Particle(
                playerX, y + 0.9f, 0f,
                (rng.nextFloat() - 0.5f) * 4f, rng.nextFloat() * 2f, -rng.nextFloat() * 3f,
                0.5f, 0.5f, Palette.Neon, 0.12f, gravity = -2f
            )
        }
    }

    private fun shiftLane(dir: Int) {
        if (phase != Phase.PLAYING) return
        val next = (lane + dir).coerceIn(-1, 1)
        // Swiping into the gust also sheds accumulated crosswind drift.
        if (abs(windOffset) > 0.05f && sign(windOffset) != sign(dir.toFloat())) {
            windOffset *= 0.55f
        }
        if (next != lane) {
            lane = next
            tilt = dir * 14f
            onEvent(GameEvent.LANE_CHANGE)
        }
    }

    // =====================================================================
    // Simulation
    // =====================================================================

    fun update(rawDt: Float) {
        val dt = rawDt.coerceIn(0f, Cfg.MAX_FRAME_DT)
        time += dt
        stepParticles(dt)
        stepToasts(dt)

        if (phase != Phase.PLAYING) {
            if (phase == Phase.OVER) deathT += dt
            shake = max(0f, shake - dt * 3.5f)
            return
        }

        speed = min(Cfg.MAX_SPEED, Cfg.BASE_SPEED + distance * Cfg.SPEED_PER_METRE)
        var vz = speed
        if (boostT > 0f) vz *= Cfg.BOOST_MULT
        if (state == PState.GLIDE) vz *= Cfg.GLIDE_FORWARD_MULT

        distance += vz * dt
        stats.distance = distance
        gen.fill(distance + Cfg.SPAWN_AHEAD, difficulty, speed, obstacles, pickups, gaps, winds)

        stepTimers(dt)
        stepLateral(dt, vz)
        stepVertical(dt, vz)

        val tunnel = max(1.0f, vz * dt * 0.6f + 0.55f)
        stepPickups(dt, tunnel)
        stepObstacles(tunnel)

        runCycle += dt * (if (state == PState.RUN) 2.0f + speed * 0.32f else 6f)
        tilt *= (1f - min(1f, dt * 7f))
        shake = max(0f, shake - dt * 3.5f)

        if (boostT > 0f && rng.nextFloat() < dt * 40f) spawnSpeedMote()

        syncUi()
    }

    private fun stepTimers(dt: Float) {
        if (magnetT > 0f) magnetT = max(0f, magnetT - dt)
        if (shieldT > 0f) shieldT = max(0f, shieldT - dt)
        if (boostT > 0f) boostT = max(0f, boostT - dt)
        if (invulnT > 0f) invulnT = max(0f, invulnT - dt)
    }

    private fun stepLateral(dt: Float, vz: Float) {
        // Ease toward the target lane.
        val target = lane.toFloat()
        val delta = target - laneF
        val step = Cfg.LANE_SHIFT_SPEED * dt
        laneF = if (abs(delta) <= step) target else laneF + sign(delta) * step

        val zone = activeWind
        if (zone != null) {
            windOffset += zone.dir * zone.strength * dt
            stats.windMetres += vz * dt
            if (rng.nextFloat() < dt * 26f) spawnWindStreak(zone.dir)
        } else {
            windOffset -= windOffset * min(1f, dt * 3.2f)
            if (abs(windOffset) < 0.004f) windOffset = 0f
        }
        windOffset = windOffset.coerceIn(-2f, 2f)
    }

    private fun stepVertical(dt: Float, vz: Float) {
        when (state) {
            PState.RUN -> { y = 0f; vy = 0f }

            PState.SLIDE -> {
                y = 0f; vy = 0f
                slideTimer -= dt
                if (slideTimer <= 0f) state = PState.RUN
                if (rng.nextFloat() < dt * 30f) spawnDust(1)
            }

            PState.JUMP -> {
                vy += Cfg.GRAVITY * dt
                y += vy * dt
                if (y <= 0f) land()
            }

            PState.GLIDE -> {
                glideTime += dt
                vy = max(vy + Cfg.GLIDE_GRAVITY * dt, Cfg.GLIDE_MIN_VY)
                y += vy * dt
                stats.glideMetres += vz * dt
                if (rng.nextFloat() < dt * 22f) {
                    particles += Particle(
                        playerX + (rng.nextFloat() - 0.5f), y + 0.7f, 0.4f,
                        0f, -0.4f, -6f, 0.45f, 0.45f, Palette.Neon.copy(alpha = 0.7f), 0.08f, gravity = 0f
                    )
                }
                if (glideTime >= Cfg.GLIDE_MAX_TIME) state = PState.JUMP
                if (y <= 0f) land()
            }
        }

        // Grounded over open air means a very long drop.
        if (y <= 0f && (state == PState.RUN || state == PState.SLIDE) && overGap()) {
            die(DeathCause.FALL)
        }
    }

    private fun land() {
        y = 0f
        vy = 0f
        glideTime = 0f
        glideUsed = false
        if (overGap()) { die(DeathCause.FALL); return }
        state = if (queueSlideOnLand) { queueSlideOnLand = false; slideTimer = Cfg.SLIDE_TIME; PState.SLIDE }
        else PState.RUN
        spawnDust(7)
        onEvent(GameEvent.LAND)
    }

    private fun overGap(): Boolean =
        gaps.any { distance > it.z0 - 0.35f && distance < it.z1 + 0.15f }

    private fun stepPickups(dt: Float, tunnel: Float) {
        val px = playerX
        val py = y + playerHeight * 0.5f
        var i = 0
        while (i < pickups.size) {
            val p = pickups[i]
            if (!p.alive) { i++; continue }
            val rz = p.wz - distance

            if (magnetT > 0f && rz in 0f..Cfg.MAGNET_RADIUS) {
                val k = min(1f, dt * 6.5f)
                p.x += (px - p.x) * k
                p.y += (py - p.y) * k
                p.wz -= rz * min(1f, dt * 3.4f)
                p.pull = min(1f, p.pull + dt * 2f)
            }

            val nz = p.wz - distance
            if (abs(nz) < tunnel &&
                abs(p.x - px) < 0.95f &&
                abs(p.y - py) < 1.25f
            ) {
                collect(p)
            }
            i++
        }
    }

    private fun collect(p: Pickup) {
        p.alive = false
        when (p.kind) {
            PickupKind.COIN -> {
                stats.coins += Cfg.COIN_VALUE
                onEvent(GameEvent.COIN)
                burst(p.x, p.y, Palette.Gold, 5)
            }
            PickupKind.MAGNET -> {
                magnetT = Cfg.MAGNET_TIME; stats.powerups++
                onEvent(GameEvent.POWERUP); toast("MAGNET", Palette.Magnet)
                burst(p.x, p.y, Palette.Magnet, 12)
            }
            PickupKind.SHIELD -> {
                shieldT = Cfg.SHIELD_TIME; stats.powerups++
                onEvent(GameEvent.POWERUP); toast("SHIELD", Palette.Shield)
                burst(p.x, p.y, Palette.Shield, 12)
            }
            PickupKind.BOOST -> {
                boostT = Cfg.BOOST_TIME; stats.powerups++
                onEvent(GameEvent.POWERUP); toast("SPEED BOOST", Palette.Boost)
                burst(p.x, p.y, Palette.Boost, 12)
            }
        }
    }

    private fun stepObstacles(tunnel: Float) {
        val px = playerX
        val pLow = y
        val pHigh = y + playerHeight
        for (o in obstacles) {
            if (!o.alive) continue
            val rz = o.wz - distance
            if (rz > 6f || rz < -3f) continue

            val ox = o.x(time)
            val bob = o.bob(time)
            val dx = abs(px - ox)

            if (abs(rz) < tunnel &&
                dx < o.halfW + Cfg.PLAYER_HALF_W &&
                pHigh > o.yLow + bob &&
                pLow < o.yHigh + bob
            ) {
                impact(o)
                continue
            }

            if (rz < 0f && !o.scored) {
                o.scored = true
                if (o.kind == ObstacleKind.DRONE) stats.dronesPassed++
                if (dx < o.halfW + Cfg.NEAR_MISS_X) {
                    stats.nearMisses++
                    onEvent(GameEvent.NEAR_MISS)
                    if (stats.nearMisses % 5 == 0) toast("CLOSE ONE!", Palette.Magenta)
                }
            }
        }
    }

    private fun impact(o: Obstacle) {
        if (invulnT > 0f) return
        if (shieldT > 0f) {
            shieldT = 0f
            invulnT = Cfg.INVULN_AFTER_SHIELD
            o.alive = false
            shake = 1f
            burst(o.x(time), o.yHigh * 0.5f, Palette.Shield, 22)
            onEvent(GameEvent.SHIELD_BREAK)
            toast("SHIELD DOWN", Palette.Shield)
            return
        }
        die(DeathCause.CRASH)
    }

    enum class DeathCause { CRASH, FALL, EDGE }

    var deathCause = DeathCause.CRASH
        private set
    private fun die(cause: DeathCause) {
        if (phase == Phase.OVER) return
        deathCause = cause
        phase = Phase.OVER
        deathT = 0f
        shake = 1.2f
        burst(playerX, y + 0.9f, Palette.Magenta, 26)
        onEvent(GameEvent.DEATH)
        syncUi()
    }

    // =====================================================================
    // Particles, toasts, housekeeping
    // =====================================================================

    private fun burst(x: Float, yy: Float, color: Color, n: Int) {
        repeat(n) {
            particles += Particle(
                x, yy, 0f,
                (rng.nextFloat() - 0.5f) * 7f,
                rng.nextFloat() * 6f,
                (rng.nextFloat() - 0.3f) * 6f,
                0.65f, 0.65f, color, 0.09f + rng.nextFloat() * 0.07f
            )
        }
    }

    private fun spawnDust(n: Int) {
        repeat(n) {
            particles += Particle(
                playerX + (rng.nextFloat() - 0.5f) * 0.8f, 0.05f, 0f,
                (rng.nextFloat() - 0.5f) * 2f, rng.nextFloat() * 1.6f, -2f - rng.nextFloat() * 4f,
                0.4f, 0.4f, Palette.Dust, 0.1f, gravity = -3f
            )
        }
    }

    private fun spawnSpeedMote() {
        particles += Particle(
            (rng.nextFloat() - 0.5f) * 7f, rng.nextFloat() * 3.2f, 22f + rng.nextFloat() * 30f,
            0f, 0f, -55f, 0.6f, 0.6f, Palette.Boost.copy(alpha = 0.8f), 0.07f, gravity = 0f
        )
    }

    private fun spawnWindStreak(dir: Float) {
        particles += Particle(
            -dir * (3.5f + rng.nextFloat() * 2f), 0.3f + rng.nextFloat() * 3.2f,
            8f + rng.nextFloat() * 34f,
            dir * (9f + rng.nextFloat() * 6f), 0f, -14f,
            0.75f, 0.75f, Palette.Wind, 0.07f, gravity = 0f
        )
    }

    private fun stepParticles(dt: Float) {
        var i = 0
        while (i < particles.size) {
            val p = particles[i]
            p.life -= dt
            if (p.life <= 0f) { particles.removeAt(i); continue }
            p.vy += p.gravity * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.z += p.vz * dt
            i++
        }
        if (particles.size > 320) {
            repeat(particles.size - 320) { if (particles.isNotEmpty()) particles.removeAt(0) }
        }
    }

    private fun stepToasts(dt: Float) {
        var i = 0
        while (i < toasts.size) {
            val t = toasts[i]
            t.life -= dt
            if (t.life <= 0f) toasts.removeAt(i) else i++
        }
    }

    fun toast(text: String, color: Color) {
        if (toasts.size > 3) toasts.removeAt(0)
        toasts.add(Toast(text, color))
    }

    /** Called by the renderer once per frame after drawing; keeps lists bounded. */
    fun cull() {
        val behind = distance + Cfg.DESPAWN_BEHIND
        obstacles.removeAll { it.wz < behind || !it.alive }
        pickups.removeAll { it.wz < behind || !it.alive }
        gaps.removeAll { it.z1 < behind }
        winds.removeAll { it.z1 < behind }
    }

    private fun syncUi() {
        uiDistance = distance.roundToInt()
        uiCoins = stats.coins
        uiScore = uiDistance * Cfg.SCORE_PER_METRE + stats.coins * Cfg.SCORE_PER_COIN
        uiMagnet = magnetT
        uiShield = shieldT
        uiBoost = boostT
        uiSpeed = speed
    }
}
