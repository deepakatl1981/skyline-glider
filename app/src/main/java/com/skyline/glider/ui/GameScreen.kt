package com.skyline.glider.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skyline.glider.audio.SoundBank
import com.skyline.glider.core.Cfg
import com.skyline.glider.core.GameEngine
import com.skyline.glider.core.Palette
import com.skyline.glider.core.Phase
import com.skyline.glider.core.Proj
import com.skyline.glider.data.SaveStore
import com.skyline.glider.render.CityArt
import com.skyline.glider.render.drawBackdrop
import com.skyline.glider.render.drawEntities
import com.skyline.glider.render.drawHero
import com.skyline.glider.render.drawParticles
import com.skyline.glider.render.drawTrack
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun GameScreen(save: SaveStore, sfx: SoundBank, onExit: () -> Unit) {

    val engine = remember { GameEngine() }
    val art = remember { CityArt() }
    val character = save.selected

    var tick by remember { mutableIntStateOf(0) }
    var committed by remember { mutableStateOf(false) }
    var bestAtStart by remember { mutableIntStateOf(save.bestScore) }

    val threshold = with(LocalDensity.current) { Cfg.SWIPE_THRESHOLD_DP.dp.toPx() }
    val keyboardFocus = remember { FocusRequester() }
    val heldKeys = remember { mutableSetOf<Key>() }

    // --- Game loop --------------------------------------------------------
    LaunchedEffect(Unit) {
        engine.onEvent = { sfx.play(it) }
        bestAtStart = save.bestScore
        engine.startRun()
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                engine.update(dt)
                engine.cull()
                tick++
            }
        }
    }

    LaunchedEffect(PauseSignal.counter) {
        if (PauseSignal.counter > 0) engine.pause()
    }

    // Overlay buttons steal focus, so reclaim it whenever play resumes.
    LaunchedEffect(engine.phase) {
        if (engine.phase == Phase.PLAYING) runCatching { keyboardFocus.requestFocus() }
    }

    fun commit() {
        if (!committed) {
            save.recordRun(engine.stats, engine.uiScore)
            committed = true
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Ink)
            .focusRequester(keyboardFocus)
            .focusable()
            .onKeyEvent { event ->
                // Physical keyboard / emulator support. A key only fires once per
                // press, so holding "up" can't burn the wingsuit the moment you jump.
                val pressed = event.key
                if (event.type == KeyEventType.KeyUp) {
                    heldKeys.remove(pressed)
                    return@onKeyEvent false
                }
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (!heldKeys.add(pressed)) return@onKeyEvent true
                when (pressed) {
                    Key.DirectionLeft, Key.A -> { engine.swipeLeft(); true }
                    Key.DirectionRight, Key.D -> { engine.swipeRight(); true }
                    Key.DirectionUp, Key.W, Key.Spacebar -> { engine.swipeUp(); true }
                    Key.DirectionDown, Key.S -> { engine.swipeDown(); true }
                    Key.Escape, Key.P -> {
                        if (engine.phase == Phase.PLAYING) engine.pause() else engine.resume()
                        true
                    }
                    else -> false
                }
            }
    ) {

        // --- World --------------------------------------------------------
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(threshold) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dx = 0f
                        var dy = 0f
                        var fired = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val delta = change.positionChange()
                            dx += delta.x
                            dy += delta.y
                            if (!fired && (abs(dx) > threshold || abs(dy) > threshold)) {
                                fired = true
                                if (abs(dx) > abs(dy)) {
                                    if (dx > 0) engine.swipeRight() else engine.swipeLeft()
                                } else {
                                    if (dy > 0) engine.swipeDown() else engine.swipeUp()
                                }
                            }
                            change.consume()
                        }
                        // A plain tap is a jump — friendlier than nothing happening.
                        if (!fired) engine.swipeUp()
                    }
                }
        ) {
            // Reading the frame counter is what makes Compose redraw every frame.
            val frame = tick
            val p = Proj(size.width, size.height)
            val shakeAmp = engine.shake * size.width * 0.012f * (if (frame >= 0) 1f else 0f)
            val sx = sin(engine.time * 61f) * shakeAmp
            val sy = sin(engine.time * 47f) * shakeAmp

            translate(sx, sy) {
                drawBackdrop(art, p, engine.distance, engine.time)
                drawTrack(engine, p, engine.time)
                drawEntities(engine, p, engine.time) {
                    drawHero(engine, p, character, engine.time)
                }
                drawParticles(engine, p)
            }

            drawSpeedVignette(engine)
        }

        // --- HUD ----------------------------------------------------------
        Hud(engine, Modifier.align(Alignment.TopCenter))

        WindIndicator(engine, Modifier.align(Alignment.CenterEnd))

        // --- Toasts -------------------------------------------------------
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(top = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (t in engine.toasts) {
                Text(
                    t.text,
                    color = t.color.copy(alpha = (t.life / 1.6f).coerceIn(0f, 1f)),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }
        }

        // --- Pause button --------------------------------------------------
        if (engine.phase == Phase.PLAYING) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x66120E28))
                    .border(1.dp, Palette.Neon.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .clickable { engine.pause() },
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(2) {
                        Box(
                            Modifier
                                .width(4.dp)
                                .height(16.dp)
                                .background(Palette.Neon)
                        )
                    }
                }
            }
        }

        // --- Pause overlay --------------------------------------------------
        AnimatedVisibility(engine.phase == Phase.PAUSED, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xCC07061A)),
                contentAlignment = Alignment.Center
            ) {
                Panel(Modifier.padding(28.dp)) {
                    ScreenTitle("PAUSED")
                    Spacer(Modifier.height(4.dp))
                    StatLine("Distance", "${engine.uiDistance} m", Palette.Neon)
                    StatLine("Coins", "${engine.uiCoins}", Palette.Gold)
                    Spacer(Modifier.height(8.dp))
                    NeonButton("RESUME", Modifier.fillMaxWidth()) { engine.resume() }
                    NeonButton("RESTART", Modifier.fillMaxWidth(), accent = Palette.Magenta) {
                        commit()
                        committed = false
                        bestAtStart = save.bestScore
                        engine.startRun()
                    }
                    NeonButton("QUIT TO MENU", Modifier.fillMaxWidth(), accent = Palette.TextDim) {
                        commit()
                        onExit()
                    }
                }
            }
        }

        // --- Game over ------------------------------------------------------
        AnimatedVisibility(engine.phase == Phase.OVER, enter = fadeIn(), exit = fadeOut()) {
            GameOverPanel(
                engine = engine,
                save = save,
                bestAtStart = bestAtStart,
                onRevive = { if (save.spend(Cfg.REVIVE_COST)) engine.revive() },
                onRetry = {
                    commit()
                    committed = false
                    bestAtStart = save.bestScore
                    engine.startRun()
                },
                onMenu = {
                    commit()
                    onExit()
                }
            )
        }
    }
}

// =========================================================================
// HUD
// =========================================================================

@Composable
private fun Hud(engine: GameEngine, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(start = 18.dp, top = 18.dp, end = 74.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    "${engine.uiDistance} m",
                    color = Palette.Text,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    "SCORE ${engine.uiScore}",
                    color = Palette.TextDim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                )
            }
            Spacer(Modifier.width(16.dp))
            CoinChip(engine.uiCoins)
        }

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PowerPill("MAG", engine.uiMagnet, Cfg.MAGNET_TIME, Palette.Magnet)
            PowerPill("SHD", engine.uiShield, Cfg.SHIELD_TIME, Palette.Shield)
            PowerPill("SPD", engine.uiBoost, Cfg.BOOST_TIME, Palette.Boost)
        }
    }
}

@Composable
private fun PowerPill(label: String, remaining: Float, max: Float, color: Color) {
    if (remaining <= 0f) return
    Column(
        Modifier
            .width(62.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x99120E28))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(Modifier.height(3.dp))
        ProgressBar(remaining / max, color)
    }
}

@Composable
private fun WindIndicator(engine: GameEngine, modifier: Modifier = Modifier) {
    val wind = engine.activeWind ?: return
    Column(
        modifier.padding(end = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (wind.dir > 0) "WIND  →" else "←  WIND",
            color = Palette.Wind,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center
        )
        Text(
            "swipe into it",
            color = Palette.Wind.copy(alpha = 0.6f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// =========================================================================
// Game over
// =========================================================================

@Composable
private fun GameOverPanel(
    engine: GameEngine,
    save: SaveStore,
    bestAtStart: Int,
    onRevive: () -> Unit,
    onRetry: () -> Unit,
    onMenu: () -> Unit
) {
    val newBest = engine.uiScore > bestAtStart
    val cause = when (engine.deathCause) {
        GameEngine.DeathCause.CRASH -> "You clipped the skyline."
        GameEngine.DeathCause.FALL -> "The rooftop ran out."
        GameEngine.DeathCause.EDGE -> "The wind took you over the edge."
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xD907061A)),
        contentAlignment = Alignment.Center
    ) {
        Panel(Modifier.padding(26.dp), accent = Palette.Magenta) {
            ScreenTitle(if (newBest) "NEW BEST!" else "RUN OVER", Palette.Magenta)
            Text(cause, color = Palette.TextDim, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            StatLine("Distance", "${engine.uiDistance} m", Palette.Neon)
            StatLine("Coins", "+${engine.uiCoins}", Palette.Gold)
            StatLine("Near misses", "${engine.stats.nearMisses}", Palette.Magenta)
            StatLine("Glided", "${engine.stats.glideMetres.toInt()} m", Palette.Shield)
            StatLine("Score", "${engine.uiScore}", Palette.Text)
            StatLine("Best", "${maxOf(bestAtStart, engine.uiScore)}", Palette.TextDim)
            Spacer(Modifier.height(8.dp))

            if (engine.canRevive) {
                NeonButton(
                    "SECOND WIND",
                    Modifier.fillMaxWidth(),
                    accent = Palette.Shield,
                    subtitle = "${Cfg.REVIVE_COST} coins",
                    enabled = save.coins >= Cfg.REVIVE_COST,
                    onClick = onRevive
                )
            }
            NeonButton("RUN AGAIN", Modifier.fillMaxWidth(), onClick = onRetry)
            NeonButton("MENU", Modifier.fillMaxWidth(), accent = Palette.TextDim, onClick = onMenu)
        }
    }
}

// =========================================================================
// Full-screen effects
// =========================================================================

private fun DrawScope.drawSpeedVignette(engine: GameEngine) {
    // Corner darkening keeps the eye on the centre lane.
    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, Color(0x00000000), Color(0x99000000)),
            center = Offset(size.width * 0.5f, size.height * 0.55f),
            radius = size.minDimension * 0.95f
        )
    )

    // Boost adds warm streaks racing past the camera.
    if (engine.boostT > 0f) {
        val n = 14
        for (i in 0 until n) {
            val t = ((engine.time * 2.4f + i * 0.37f) % 1f)
            val side = if (i % 2 == 0) 0.06f else 0.94f
            val x = size.width * (side + (if (i % 2 == 0) -0.05f else 0.05f) * t)
            val h = size.height * (0.10f + 0.22f * t)
            val y = size.height * (0.25f + t * 0.6f)
            drawRect(
                Palette.Boost.copy(alpha = 0.28f * (1f - t)),
                topLeft = Offset(x, y),
                size = Size(size.width * 0.006f, h)
            )
        }
    }

    // A red pulse at the screen edge when the shield has just broken.
    if (engine.invulnT > 0f) {
        drawRect(
            Brush.radialGradient(
                listOf(Color.Transparent, Palette.Magenta.copy(alpha = 0.22f * engine.invulnT)),
                center = Offset(size.width * 0.5f, size.height * 0.5f),
                radius = size.minDimension * 0.8f
            )
        )
    }
}
