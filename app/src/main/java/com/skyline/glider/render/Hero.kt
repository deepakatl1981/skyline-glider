package com.skyline.glider.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import com.skyline.glider.core.GameEngine
import com.skyline.glider.core.PState
import com.skyline.glider.core.Palette
import com.skyline.glider.core.Phase
import com.skyline.glider.core.Proj
import com.skyline.glider.data.GliderCharacter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/**
 * The runner, drawn from primitives: torso, two-segment limbs, helmet, and a
 * wingsuit membrane that only appears while gliding.
 */
fun DrawScope.drawHero(e: GameEngine, p: Proj, ch: GliderCharacter, time: Float) {
    val unit = p.len(1f, 0f)
    val footX = p.x(e.playerX, 0f)
    val footY = p.y(e.y, 0f)

    drawShadow(e, p, unit)

    // Flicker while briefly invulnerable after a shield break.
    val alpha = if (e.invulnT > 0f && ((time * 18f).toInt() % 2 == 0)) 0.45f else 1f

    val dying = e.phase == Phase.OVER
    val tumble = if (dying) e.deathT * 520f else 0f
    val drop = if (dying) e.deathT * e.deathT * unit * 9f else 0f

    translate(0f, drop) {
        rotate(e.tilt + tumble, Offset(footX, footY - unit * 0.9f)) {
            when {
                dying -> pose(e, ch, footX, footY, unit, alpha, time, PState.JUMP, dead = true)
                else -> pose(e, ch, footX, footY, unit, alpha, time, e.state, dead = false)
            }
        }
    }

    drawAuras(e, p, ch, unit, footX, footY, time)
}

private fun DrawScope.drawShadow(e: GameEngine, p: Proj, unit: Float) {
    val h = e.y.coerceIn(0f, 5f)
    val shrink = 1f - h / 7f
    val a = (0.42f * (1f - h / 5.5f)).coerceIn(0.05f, 0.42f)
    val cx = p.x(e.playerX, 0f)
    val cy = p.y(0f, 0f)
    val rx = unit * 0.42f * shrink
    val ry = unit * 0.13f * shrink
    drawOval(
        Color.Black.copy(alpha = a),
        topLeft = Offset(cx - rx, cy - ry),
        size = Size(rx * 2, ry * 2)
    )
}

private fun DrawScope.pose(
    e: GameEngine,
    ch: GliderCharacter,
    fx: Float,
    fy: Float,
    unit: Float,
    alpha: Float,
    time: Float,
    state: PState,
    dead: Boolean
) {
    fun mx(v: Float) = fx + v * unit
    fun my(v: Float) = fy - v * unit

    val suit = ch.suit.copy(alpha = alpha)
    val accent = ch.accent.copy(alpha = alpha)
    val skin = ch.skin.copy(alpha = alpha)
    val limbW = unit * 0.13f
    val armW = unit * 0.11f

    when (state) {

        PState.RUN -> {
            val s1 = sin(e.runCycle)
            val s2 = sin(e.runCycle + Math.PI.toFloat())
            val bounce = abs(sin(e.runCycle)) * 0.06f
            val hip = Offset(mx(0f), my(0.92f + bounce))
            val sho = Offset(mx(0.05f), my(1.40f + bounce))

            // Back leg / arm first so the front pair overlaps them.
            limb(hip, Offset(mx(0.36f * s2), my(max(0f, s2) * 0.26f)), unit * 0.09f, 0f, suit.darken(0.75f), limbW)
            limb(sho, Offset(mx(-0.30f * s1), my(1.02f + bounce)), -unit * 0.06f, 0f, suit.darken(0.75f), armW)

            torso(hip, sho, suit, accent, unit, alpha)

            limb(hip, Offset(mx(0.36f * s1), my(max(0f, s1) * 0.26f)), unit * 0.10f, 0f, suit, limbW)
            limb(sho, Offset(mx(-0.30f * s2), my(1.02f + bounce)), -unit * 0.06f, 0f, suit, armW)

            head(mx(0.09f), my(1.60f + bounce), unit, skin, accent, alpha, ch)
            scarf(mx(0.02f), my(1.44f + bounce), unit, accent, time, 1f)
        }

        PState.SLIDE -> {
            val hip = Offset(mx(-0.05f), my(0.32f))
            val sho = Offset(mx(-0.48f), my(0.56f))
            limb(hip, Offset(mx(0.62f), my(0.10f)), unit * 0.06f, -unit * 0.1f, suit.darken(0.75f), limbW)
            torso(hip, sho, suit, accent, unit, alpha)
            limb(hip, Offset(mx(0.55f), my(0.30f)), unit * 0.06f, -unit * 0.1f, suit, limbW)
            limb(sho, Offset(mx(-0.05f), my(0.86f)), unit * 0.05f, 0f, suit, armW)
            head(mx(-0.62f), my(0.72f), unit, skin, accent, alpha, ch)
            scarf(mx(-0.5f), my(0.62f), unit, accent, time, 1.7f)
        }

        PState.JUMP -> {
            val rising = e.vy > 0f || dead
            val hip = Offset(mx(0f), my(0.86f))
            val sho = Offset(mx(0.03f), my(1.34f))
            val tuck = if (rising) 0.42f else 0.18f
            limb(hip, Offset(mx(0.30f), my(tuck)), unit * 0.16f, 0f, suit.darken(0.75f), limbW)
            torso(hip, sho, suit, accent, unit, alpha)
            limb(hip, Offset(mx(-0.16f), my(tuck + 0.1f)), unit * 0.14f, 0f, suit, limbW)
            limb(sho, Offset(mx(0.34f), my(if (rising) 1.72f else 1.20f)), unit * 0.06f, 0f, suit, armW)
            limb(sho, Offset(mx(-0.32f), my(if (rising) 1.66f else 1.10f)), -unit * 0.06f, 0f, suit, armW)
            head(mx(0.07f), my(1.54f), unit, skin, accent, alpha, ch)
            scarf(mx(0f), my(1.38f), unit, accent, time, 2.2f)
        }

        PState.GLIDE -> {
            // Pitched forward, arms swept, membrane stretched between limbs.
            val hip = Offset(mx(-0.18f), my(0.92f))
            val sho = Offset(mx(0.30f), my(1.14f))
            val handL = Offset(mx(0.62f), my(1.52f))
            val handR = Offset(mx(0.58f), my(0.72f))
            val footL = Offset(mx(-0.78f), my(1.16f))
            val footR = Offset(mx(-0.74f), my(0.56f))

            wing(sho, handL, hip, footL, ch.wing, alpha * 0.55f)
            wing(sho, handR, hip, footR, ch.wing, alpha * 0.75f)

            limb(hip, footL, -unit * 0.05f, -unit * 0.05f, suit.darken(0.78f), limbW)
            limb(sho, handL, unit * 0.04f, -unit * 0.06f, suit.darken(0.78f), armW)
            torso(hip, sho, suit, accent, unit, alpha)
            limb(hip, footR, -unit * 0.05f, unit * 0.05f, suit, limbW)
            limb(sho, handR, unit * 0.04f, unit * 0.06f, suit, armW)

            head(mx(0.50f), my(1.20f), unit, skin, accent, alpha, ch)

            // Trailing vapour ribbon.
            val ribbon = Path().apply {
                moveTo(mx(-0.2f), my(1.05f))
                cubicTo(
                    mx(-0.9f), my(1.25f + 0.12f * sin(time * 9f)),
                    mx(-1.5f), my(0.85f + 0.16f * sin(time * 7f)),
                    mx(-2.1f), my(1.05f)
                )
            }
            drawPath(ribbon, ch.wing.copy(alpha = alpha * 0.35f), style = Stroke(width = unit * 0.09f, cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.torso(hip: Offset, sho: Offset, suit: Color, accent: Color, unit: Float, alpha: Float) {
    drawLine(suit, hip, sho, unit * 0.30f, StrokeCap.Round)
    // Chest highlight strip.
    val mid = Offset((hip.x + sho.x) * 0.5f, (hip.y + sho.y) * 0.5f)
    drawLine(accent.copy(alpha = alpha * 0.9f), mid, sho, unit * 0.10f, StrokeCap.Round)
}

private fun DrawScope.head(
    cx: Float, cy: Float, unit: Float, skin: Color, accent: Color, alpha: Float, ch: GliderCharacter
) {
    val r = unit * 0.17f
    drawCircle(skin, r, Offset(cx, cy))
    // Helmet / goggles.
    drawArc(
        color = ch.suit.copy(alpha = alpha),
        startAngle = 180f, sweepAngle = 180f, useCenter = true,
        topLeft = Offset(cx - r * 1.12f, cy - r * 1.12f),
        size = Size(r * 2.24f, r * 2.24f)
    )
    drawLine(
        accent, Offset(cx - r * 1.05f, cy - r * 0.1f), Offset(cx + r * 1.05f, cy - r * 0.1f),
        unit * 0.07f, StrokeCap.Round
    )
    drawCircle(Color.White.copy(alpha = alpha * 0.85f), r * 0.22f, Offset(cx + r * 0.5f, cy - r * 0.1f))
}

private fun DrawScope.scarf(x: Float, y: Float, unit: Float, accent: Color, time: Float, wave: Float) {
    val path = Path().apply {
        moveTo(x, y)
        cubicTo(
            x - unit * 0.35f, y - unit * 0.12f * sin(time * 11f * wave),
            x - unit * 0.7f, y + unit * 0.18f * sin(time * 9f * wave),
            x - unit * 1.0f, y + unit * 0.05f
        )
    }
    drawPath(path, accent, style = Stroke(width = unit * 0.10f, cap = StrokeCap.Round))
}

private fun DrawScope.wing(sho: Offset, hand: Offset, hip: Offset, foot: Offset, color: Color, alpha: Float) {
    val path = Path().apply {
        moveTo(sho.x, sho.y)
        lineTo(hand.x, hand.y)
        lineTo(foot.x, foot.y)
        lineTo(hip.x, hip.y)
        close()
    }
    drawPath(
        path,
        Brush.linearGradient(
            listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.35f)),
            start = sho, end = foot
        )
    )
    drawPath(path, color.copy(alpha = alpha), style = Stroke(width = 2.5f))
}

// =========================================================================
// Power-up auras
// =========================================================================

private fun DrawScope.drawAuras(
    e: GameEngine, p: Proj, ch: GliderCharacter, unit: Float, fx: Float, fy: Float, time: Float
) {
    val center = Offset(fx, fy - unit * 0.85f)

    if (e.shieldT > 0f) {
        val expiring = e.shieldT < 3f && ((time * 10f).toInt() % 2 == 0)
        val a = if (expiring) 0.25f else 0.6f
        val r = unit * (0.95f + 0.04f * sin(time * 5f))
        drawCircle(Palette.Shield, r, center, alpha = a * 0.14f)
        ringStroke(center, r, Palette.Shield, unit * 0.045f, a)
        ringStroke(center, r * 0.86f, Palette.Shield, unit * 0.02f, a * 0.5f)
    }

    if (e.magnetT > 0f) {
        for (k in 0 until 3) {
            val ang = time * 3.2f + k * 2.09f
            val rx = unit * 0.85f * kotlin.math.cos(ang)
            val ry = unit * 0.28f * sin(ang)
            drawCircle(
                Palette.Magnet, unit * 0.07f,
                Offset(center.x + rx, center.y + ry),
                alpha = 0.85f
            )
        }
        ringStroke(center, unit * 0.9f, Palette.Magnet, unit * 0.02f, 0.35f)
    }

    if (e.boostT > 0f) {
        for (k in 0 until 4) {
            val t = k / 4f
            val a = (1f - t) * 0.5f
            drawOval(
                Palette.Boost.copy(alpha = a),
                topLeft = Offset(fx - unit * (0.5f + t * 1.6f), fy - unit * (0.6f + t * 0.15f)),
                size = Size(unit * (0.5f + t * 0.9f), unit * (0.25f + t * 0.2f))
            )
        }
    }
}

private fun Color.darken(f: Float): Color = Color(red * f, green * f, blue * f, alpha)
