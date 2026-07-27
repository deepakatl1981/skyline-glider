package com.skyline.glider.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.skyline.glider.core.Cfg
import com.skyline.glider.core.GameEngine
import com.skyline.glider.core.Obstacle
import com.skyline.glider.core.ObstacleKind
import com.skyline.glider.core.Palette
import com.skyline.glider.core.Pickup
import com.skyline.glider.core.PickupKind
import com.skyline.glider.core.Proj
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val NEAR_CLIP = -4.5f

/**
 * Painter's-algorithm pass over obstacles and pickups (far to near), with the
 * hero slotted in at z = 0 so things genuinely pass in front of and behind him.
 */
fun DrawScope.drawEntities(e: GameEngine, p: Proj, time: Float, hero: DrawScope.() -> Unit) {
    var oi = e.obstacles.size - 1
    var pi = e.pickups.size - 1
    var heroDrawn = false

    while (oi >= 0 || pi >= 0) {
        val oz = if (oi >= 0) e.obstacles[oi].wz else Float.NEGATIVE_INFINITY
        val pz = if (pi >= 0) e.pickups[pi].wz else Float.NEGATIVE_INFINITY
        val takeObstacle = oz >= pz
        val wz = if (takeObstacle) oz else pz
        val rz = wz - e.distance

        if (!heroDrawn && rz < 0f) { hero(); heroDrawn = true }

        if (rz in NEAR_CLIP..Cfg.DRAW_DISTANCE) {
            if (takeObstacle) drawObstacle(p, e.obstacles[oi], rz, time)
            else drawPickup(p, e.pickups[pi], rz, time)
        }

        if (takeObstacle) oi-- else pi--
    }

    if (!heroDrawn) hero()
}

private fun fade(rz: Float): Float =
    ((Cfg.DRAW_DISTANCE - rz) / 28f).coerceIn(0f, 1f)

// =========================================================================
// Obstacles
// =========================================================================

private fun DrawScope.drawObstacle(p: Proj, o: Obstacle, rz: Float, time: Float) {
    val a = fade(rz)
    if (a <= 0.01f) return
    val x = o.x(time)
    when (o.kind) {
        ObstacleKind.AC_UNIT -> drawAcUnit(p, x, rz, a)
        ObstacleKind.WATER_TOWER -> drawWaterTower(p, x, rz, a)
        ObstacleKind.ANTENNA -> drawAntenna(p, x, rz, a, time)
        ObstacleKind.PIPE_BAR -> drawPipeBar(p, o.halfW, rz, a)
        ObstacleKind.DRONE -> drawDrone(p, x, rz, o.bob(time), a, time, o.phase)
    }
}

private fun DrawScope.drawAcUnit(p: Proj, x: Float, z: Float, a: Float) {
    box3d(
        p, x, z, width = 1.7f, height = 0.95f, depth = 1.3f,
        front = Color(0xFF46536F), top = Color(0xFF66779B), side = Color(0xFF333D55),
        alpha = a
    )
    // Grille slats on the front face.
    val zf = z - 0.65f
    for (i in 0 until 4) {
        val yy = 0.18f + i * 0.18f
        quad(
            Offset(p.x(x - 0.6f, zf), p.y(yy + 0.05f, zf)), Offset(p.x(x + 0.6f, zf), p.y(yy + 0.05f, zf)),
            Offset(p.x(x + 0.6f, zf), p.y(yy, zf)), Offset(p.x(x - 0.6f, zf), p.y(yy, zf)),
            Color(0xFF232B3D), alpha = a
        )
    }
    // Warning strip along the top lip.
    quad(
        Offset(p.x(x - 0.85f, zf), p.y(0.99f, zf)), Offset(p.x(x + 0.85f, zf), p.y(0.99f, zf)),
        Offset(p.x(x + 0.85f, zf), p.y(0.9f, zf)), Offset(p.x(x - 0.85f, zf), p.y(0.9f, zf)),
        Palette.Amber, alpha = a * 0.9f
    )
}

private fun DrawScope.drawWaterTower(p: Proj, x: Float, z: Float, a: Float) {
    val legTop = 0.9f
    val legW = 0.55f
    for (s in intArrayOf(-1, 1)) {
        drawLine(
            Color(0xFF2A2440).copy(alpha = a),
            Offset(p.x(x + s * legW, z), p.y(0f, z)),
            Offset(p.x(x + s * legW * 0.6f, z), p.y(legTop, z)),
            p.len(0.12f, z), StrokeCap.Round
        )
    }
    box3d(
        p, x, z, width = 1.45f, height = 1.75f, depth = 1.4f, yBase = legTop,
        front = Color(0xFF6A4A38), top = Color(0xFF8A6248), side = Color(0xFF4C3527),
        alpha = a
    )
    // Conical cap.
    val zf = z - 0.7f
    tri(
        Offset(p.x(x, zf), p.y(legTop + 2.35f, zf)),
        Offset(p.x(x - 0.85f, zf), p.y(legTop + 1.72f, zf)),
        Offset(p.x(x + 0.85f, zf), p.y(legTop + 1.72f, zf)),
        Color(0xFF9A6E50), alpha = a
    )
    // Hoop bands.
    for (yy in floatArrayOf(legTop + 0.45f, legTop + 1.2f)) {
        quad(
            Offset(p.x(x - 0.73f, zf), p.y(yy + 0.08f, zf)), Offset(p.x(x + 0.73f, zf), p.y(yy + 0.08f, zf)),
            Offset(p.x(x + 0.73f, zf), p.y(yy, zf)), Offset(p.x(x - 0.73f, zf), p.y(yy, zf)),
            Color(0xFF3B2A1E), alpha = a
        )
    }
    quad(
        Offset(p.x(x - 0.73f, zf), p.y(legTop + 0.95f, zf)), Offset(p.x(x + 0.73f, zf), p.y(legTop + 0.95f, zf)),
        Offset(p.x(x + 0.73f, zf), p.y(legTop + 0.82f, zf)), Offset(p.x(x - 0.73f, zf), p.y(legTop + 0.82f, zf)),
        Palette.Magenta, alpha = a * 0.8f
    )
}

private fun DrawScope.drawAntenna(p: Proj, x: Float, z: Float, a: Float, time: Float) {
    box3d(
        p, x, z, width = 0.22f, height = 2.5f, depth = 0.22f,
        front = Color(0xFF3E4A66), top = Color(0xFF5C6C92), side = Color(0xFF2A3247),
        alpha = a
    )
    val zf = z - 0.11f
    for (i in 0 until 4) {
        val yy = 0.35f + i * 0.55f
        val w = 0.62f - i * 0.1f
        drawLine(
            Color(0xFF5C6C92).copy(alpha = a),
            Offset(p.x(x - w, zf), p.y(yy, zf)),
            Offset(p.x(x + w, zf), p.y(yy, zf)),
            p.len(0.07f, z), StrokeCap.Round
        )
    }
    val blink = 0.35f + 0.65f * (0.5f + 0.5f * sin(time * 5f))
    val tip = Offset(p.x(x, zf), p.y(2.62f, zf))
    drawCircle(Color(0xFFFF3D3D), p.len(0.3f, z), tip, alpha = a * 0.28f * blink)
    drawCircle(Color(0xFFFF5A5A), p.len(0.11f, z), tip, alpha = a * blink)
}

private fun DrawScope.drawPipeBar(p: Proj, halfW: Float, z: Float, a: Float) {
    val zf = z - 0.25f
    // Uprights.
    for (s in intArrayOf(-1, 1)) {
        box3d(
            p, s * halfW, z, width = 0.28f, height = 3.4f, depth = 0.4f,
            front = Color(0xFF4A5470), top = Color(0xFF6A779B), side = Color(0xFF333B52),
            alpha = a
        )
    }
    // The bar you slide under.
    quad(
        Offset(p.x(-halfW, zf), p.y(1.7f, zf)), Offset(p.x(halfW, zf), p.y(1.7f, zf)),
        Offset(p.x(halfW, zf), p.y(1.18f, zf)), Offset(p.x(-halfW, zf), p.y(1.18f, zf)),
        Color(0xFF2E3448), alpha = a
    )
    // Hazard stripes.
    val n = 9
    for (i in 0 until n) {
        if (i % 2 == 1) continue
        val x0 = -halfW + i * (2 * halfW / n)
        val x1 = x0 + (2 * halfW / n)
        quad(
            Offset(p.x(x0, zf), p.y(1.66f, zf)), Offset(p.x(x1, zf), p.y(1.66f, zf)),
            Offset(p.x(x1, zf), p.y(1.24f, zf)), Offset(p.x(x0, zf), p.y(1.24f, zf)),
            Palette.Amber, alpha = a
        )
    }
    // Scaffolding above, so it reads as "cannot jump this".
    for (i in 0..4) {
        val x0 = -halfW + i * (2 * halfW / 4)
        drawLine(
            Color(0xFF4A5470).copy(alpha = a * 0.8f),
            Offset(p.x(x0, zf), p.y(1.7f, zf)),
            Offset(p.x(x0 - 0.6f, zf), p.y(3.4f, zf)),
            p.len(0.06f, z)
        )
    }
    quad(
        Offset(p.x(-halfW, zf), p.y(3.45f, zf)), Offset(p.x(halfW, zf), p.y(3.45f, zf)),
        Offset(p.x(halfW, zf), p.y(3.25f, zf)), Offset(p.x(-halfW, zf), p.y(3.25f, zf)),
        Color(0xFF4A5470), alpha = a
    )
}

private fun DrawScope.drawDrone(
    p: Proj, x: Float, z: Float, bob: Float, a: Float, time: Float, phase: Float
) {
    val cy = 1.4f + bob
    val cx = p.x(x, z)
    val cyPx = p.y(cy, z)
    val unit = p.len(1f, z)

    // Search beam onto the roof.
    tri(
        Offset(cx, cyPx),
        Offset(p.x(x - 0.85f, z), p.y(0f, z)),
        Offset(p.x(x + 0.85f, z), p.y(0f, z)),
        Palette.Magenta, alpha = a * 0.16f
    )

    // Rotor arms and blur discs.
    for (s in intArrayOf(-1, 1)) {
        val ax = cx + s * unit * 0.55f
        drawLine(
            Color(0xFF2B3350).copy(alpha = a),
            Offset(cx, cyPx), Offset(ax, cyPx - unit * 0.22f), unit * 0.07f, StrokeCap.Round
        )
        val spin = 0.55f + 0.45f * abs(cos(time * 18f + phase + s))
        drawOval(
            Color(0xFF8FA8FF).copy(alpha = a * 0.35f),
            topLeft = Offset(ax - unit * 0.3f, cyPx - unit * 0.28f),
            size = Size(unit * 0.6f, unit * 0.11f * spin)
        )
    }

    // Body.
    drawOval(
        Color(0xFF39406B).copy(alpha = a),
        topLeft = Offset(cx - unit * 0.42f, cyPx - unit * 0.2f),
        size = Size(unit * 0.84f, unit * 0.4f)
    )
    drawOval(
        Color(0xFF525C93).copy(alpha = a),
        topLeft = Offset(cx - unit * 0.42f, cyPx - unit * 0.2f),
        size = Size(unit * 0.84f, unit * 0.16f)
    )
    // Eye.
    val blink = 0.45f + 0.55f * (0.5f + 0.5f * sin(time * 6f + phase))
    drawCircle(Color(0xFFFF3D3D), unit * 0.26f, Offset(cx, cyPx + unit * 0.02f), alpha = a * 0.30f * blink)
    drawCircle(Color(0xFFFF6B6B), unit * 0.1f, Offset(cx, cyPx + unit * 0.02f), alpha = a * blink)
}

// =========================================================================
// Pickups
// =========================================================================

private fun DrawScope.drawPickup(p: Proj, pu: Pickup, rz: Float, time: Float) {
    val a = fade(rz)
    if (a <= 0.01f) return
    val cx = p.x(pu.x, rz)
    val cy = p.y(pu.y, rz)
    val unit = p.len(1f, rz)

    when (pu.kind) {
        PickupKind.COIN -> {
            val spin = abs(cos(time * 3.4f + pu.wz * 0.6f))
            val rw = unit * 0.30f * (0.18f + 0.82f * spin)
            val rh = unit * 0.30f
            drawCircle(Palette.Gold, rh * 1.9f, Offset(cx, cy), alpha = a * 0.16f)
            drawOval(
                Color(0xFFB07C12).copy(alpha = a),
                topLeft = Offset(cx - rw, cy - rh), size = Size(rw * 2, rh * 2)
            )
            drawOval(
                Palette.Gold.copy(alpha = a),
                topLeft = Offset(cx - rw * 0.78f, cy - rh * 0.78f),
                size = Size(rw * 1.56f, rh * 1.56f)
            )
            if (spin > 0.45f) {
                drawOval(
                    Color(0xFFFFF0B8).copy(alpha = a * 0.85f),
                    topLeft = Offset(cx - rw * 0.28f, cy - rh * 0.45f),
                    size = Size(rw * 0.34f, rh * 0.9f)
                )
            }
        }

        PickupKind.MAGNET, PickupKind.SHIELD, PickupKind.BOOST -> {
            val color = when (pu.kind) {
                PickupKind.MAGNET -> Palette.Magnet
                PickupKind.SHIELD -> Palette.Shield
                else -> Palette.Boost
            }
            val pulse = 0.85f + 0.15f * sin(time * 4f + pu.wz)
            val r = unit * 0.42f * pulse
            drawCircle(color, r * 2.1f, Offset(cx, cy), alpha = a * 0.18f)
            drawCircle(Color(0xFF15122E).copy(alpha = a * 0.92f), r, Offset(cx, cy))
            drawCircle(color, r, Offset(cx, cy), alpha = a, style = Stroke(width = unit * 0.06f))
            when (pu.kind) {
                PickupKind.MAGNET -> magnetGlyph(cx, cy, r * 0.62f, color, a)
                PickupKind.SHIELD -> shieldGlyph(cx, cy, r * 0.68f, color, a)
                else -> boltGlyph(cx, cy, r * 0.7f, color, a)
            }
        }
    }
}

private fun DrawScope.magnetGlyph(cx: Float, cy: Float, r: Float, color: Color, a: Float) {
    drawArc(
        color = color.copy(alpha = a),
        startAngle = 180f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(cx - r, cy - r * 0.9f),
        size = Size(r * 2, r * 1.8f),
        style = Stroke(width = r * 0.5f)
    )
    for (s in intArrayOf(-1, 1)) {
        drawRect(
            color.copy(alpha = a),
            topLeft = Offset(cx + s * r - if (s < 0) 0f else r * 0.25f, cy),
            size = Size(r * 0.25f, r * 0.62f)
        )
    }
}

private fun DrawScope.shieldGlyph(cx: Float, cy: Float, r: Float, color: Color, a: Float) {
    val path = Path().apply {
        moveTo(cx, cy - r)
        lineTo(cx + r * 0.82f, cy - r * 0.45f)
        lineTo(cx + r * 0.62f, cy + r * 0.62f)
        lineTo(cx, cy + r)
        lineTo(cx - r * 0.62f, cy + r * 0.62f)
        lineTo(cx - r * 0.82f, cy - r * 0.45f)
        close()
    }
    drawPath(path, color, alpha = a)
}

private fun DrawScope.boltGlyph(cx: Float, cy: Float, r: Float, color: Color, a: Float) {
    val path = Path().apply {
        moveTo(cx + r * 0.25f, cy - r)
        lineTo(cx - r * 0.55f, cy + r * 0.12f)
        lineTo(cx - r * 0.02f, cy + r * 0.12f)
        lineTo(cx - r * 0.25f, cy + r)
        lineTo(cx + r * 0.58f, cy - r * 0.18f)
        lineTo(cx + r * 0.04f, cy - r * 0.18f)
        close()
    }
    drawPath(path, color, alpha = a)
}

// =========================================================================
// Particles
// =========================================================================

fun DrawScope.drawParticles(e: GameEngine, p: Proj) {
    for (pt in e.particles) {
        if (pt.z < NEAR_CLIP || pt.z > Cfg.DRAW_DISTANCE) continue
        val a = (pt.life / pt.maxLife).coerceIn(0f, 1f)
        val r = p.len(pt.size, pt.z)
        if (r < 0.4f) continue
        drawCircle(pt.color, r, Offset(p.x(pt.x, pt.z), p.y(pt.y, pt.z)), alpha = a * 0.9f)
    }
}
