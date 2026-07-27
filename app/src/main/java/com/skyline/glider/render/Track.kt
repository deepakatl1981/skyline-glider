package com.skyline.glider.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.skyline.glider.core.Cfg
import com.skyline.glider.core.GameEngine
import com.skyline.glider.core.Palette
import com.skyline.glider.core.Proj
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private const val NEAR_Z = -5.5f
private const val HW = Cfg.ROOF_HALF_WIDTH
private const val WALL_DROP = -46f

/**
 * Draws the rooftop course: the chasm below, every solid slab of roof between
 * gaps, parapets along the edges, lane markings and crosswind overlays.
 */
fun DrawScope.drawTrack(e: GameEngine, p: Proj, time: Float) {
    val far = Cfg.DRAW_DISTANCE
    val d = e.distance

    drawChasm(p)

    // Slice the visible range into solid roof intervals around the gaps.
    val cuts = e.gaps
        .map { it.z0 - d to it.z1 - d }
        .filter { it.second > NEAR_Z && it.first < far }
        .sortedBy { it.first }

    val slabs = ArrayList<Pair<Float, Float>>(cuts.size + 1)
    var z = NEAR_Z
    for ((g0, g1) in cuts) {
        if (g0 > z) slabs += z to min(g0, far)
        z = max(z, g1)
        if (z >= far) break
    }
    if (z < far) slabs += z to far

    // Far to near so nearer geometry paints over the vanishing point.
    for (i in slabs.indices.reversed()) {
        val (z0, z1) = slabs[i]
        if (z1 <= z0) continue
        drawSlab(p, z0, z1, d, time)
    }

    // The wall you glide toward on the far side of every gap.
    for ((g0, g1) in cuts) {
        if (g1 < NEAR_Z || g1 > far) continue
        drawGapFace(p, g0, g1)
    }

    drawWindZones(e, p, time)
}

private fun DrawScope.drawChasm(p: Proj) {
    val top = p.horizonY
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Palette.Void, Color(0xFF0A0820), Color(0xFF120C26), Color(0xFF1C1030)),
            startY = top,
            endY = size.height
        ),
        topLeft = Offset(0f, top),
        size = Size(size.width, size.height - top)
    )
    // Street glow far below, so the gaps feel like real drops.
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0x00FF9A4D), Color(0x2AFF9A4D)),
            startY = size.height * 0.72f,
            endY = size.height
        ),
        topLeft = Offset(0f, size.height * 0.72f),
        size = Size(size.width, size.height * 0.28f)
    )
}

private fun DrawScope.drawSlab(p: Proj, z0: Float, z1: Float, distance: Float, time: Float) {
    // Roof surface.
    quad(
        Offset(p.x(-HW, z1), p.y(0f, z1)), Offset(p.x(HW, z1), p.y(0f, z1)),
        Offset(p.x(HW, z0), p.y(0f, z0)), Offset(p.x(-HW, z0), p.y(0f, z0)),
        Brush.verticalGradient(
            listOf(Palette.RoofFar, Palette.RoofNear),
            startY = p.y(0f, z1),
            endY = p.y(0f, z0)
        )
    )

    // Speed ticks: a light band every 6 m, aligned to world space so they scroll.
    val step = 6f
    var t = (ceil((z0 + distance) / step) * step) - distance
    while (t < z1) {
        if (t > z0) {
            val a = (1f - t / Cfg.DRAW_DISTANCE).coerceIn(0f, 1f) * 0.30f
            quad(
                Offset(p.x(-HW, t + 0.45f), p.y(0f, t + 0.45f)), Offset(p.x(HW, t + 0.45f), p.y(0f, t + 0.45f)),
                Offset(p.x(HW, t), p.y(0f, t)), Offset(p.x(-HW, t), p.y(0f, t)),
                Palette.RoofEdge, alpha = a
            )
        }
        t += step
    }

    // Lane separators.
    for (lx in floatArrayOf(-Cfg.LANE_WIDTH * 0.5f, Cfg.LANE_WIDTH * 0.5f)) {
        laneLine(p, lx, z0, z1, Palette.Neon, 0.05f, 0.26f)
    }
    for (lx in floatArrayOf(-Cfg.LANE_WIDTH * 1.5f, Cfg.LANE_WIDTH * 1.5f)) {
        laneLine(p, lx, z0, z1, Palette.RoofEdge, 0.05f, 0.34f)
    }

    // Parapets along both edges — the visual "you fall here" boundary.
    for (side in intArrayOf(-1, 1)) {
        val x0 = side * HW
        val x1 = side * (HW + 0.28f)
        val hgt = 0.42f
        // inner face
        quad(
            Offset(p.x(x0, z1), p.y(hgt, z1)), Offset(p.x(x0, z0), p.y(hgt, z0)),
            Offset(p.x(x0, z0), p.y(0f, z0)), Offset(p.x(x0, z1), p.y(0f, z1)),
            Palette.Wall
        )
        // cap
        quad(
            Offset(p.x(x0, z1), p.y(hgt, z1)), Offset(p.x(x1, z1), p.y(hgt, z1)),
            Offset(p.x(x1, z0), p.y(hgt, z0)), Offset(p.x(x0, z0), p.y(hgt, z0)),
            Palette.RoofEdge
        )
        // neon strip on the cap
        quad(
            Offset(p.x(x0, z1), p.y(hgt + 0.02f, z1)), Offset(p.x(x0 + side * 0.08f, z1), p.y(hgt + 0.02f, z1)),
            Offset(p.x(x0 + side * 0.08f, z0), p.y(hgt + 0.02f, z0)), Offset(p.x(x0, z0), p.y(hgt + 0.02f, z0)),
            Palette.Magenta, alpha = 0.75f
        )
    }

    // Outer building walls dropping away from the near edge of the slab.
    for (side in intArrayOf(-1, 1)) {
        val x = side * (HW + 0.28f)
        quad(
            Offset(p.x(x, z1), p.y(0.42f, z1)), Offset(p.x(x, z0), p.y(0.42f, z0)),
            Offset(p.x(x, z0), p.y(WALL_DROP, z0)), Offset(p.x(x, z1), p.y(WALL_DROP, z1)),
            Palette.WallDark
        )
    }
}

private fun DrawScope.laneLine(
    p: Proj, x: Float, z0: Float, z1: Float, color: Color, halfWidth: Float, alpha: Float
) {
    quad(
        Offset(p.x(x - halfWidth, z1), p.y(0.01f, z1)), Offset(p.x(x + halfWidth, z1), p.y(0.01f, z1)),
        Offset(p.x(x + halfWidth, z0), p.y(0.01f, z0)), Offset(p.x(x - halfWidth, z0), p.y(0.01f, z0)),
        color, alpha = alpha
    )
}

/** The facade of the next building, seen across a gap. */
private fun DrawScope.drawGapFace(p: Proj, g0: Float, g1: Float) {
    quad(
        Offset(p.x(-HW - 0.28f, g1), p.y(0.42f, g1)), Offset(p.x(HW + 0.28f, g1), p.y(0.42f, g1)),
        Offset(p.x(HW + 0.28f, g1), p.y(WALL_DROP, g1)), Offset(p.x(-HW - 0.28f, g1), p.y(WALL_DROP, g1)),
        Brush.verticalGradient(
            listOf(Palette.Wall, Palette.WallDark),
            startY = p.y(0.42f, g1),
            endY = p.y(-14f, g1)
        )
    )
    // Lit windows on the facing wall.
    val rows = 6
    val cols = 5
    for (r in 0 until rows) for (c in 0 until cols) {
        val n = hash01((g1 * 10f).toInt() * 131 + r * 17 + c)
        if (n > 0.45f) continue
        val wx = -HW + 0.6f + c * (2f * (HW - 0.6f) / (cols - 1))
        val wy = -1.2f - r * 2.1f
        val a = Offset(p.x(wx - 0.35f, g1), p.y(wy + 0.5f, g1))
        val b = Offset(p.x(wx + 0.35f, g1), p.y(wy - 0.5f, g1))
        drawRect(
            Color(0xFFFFC97A),
            topLeft = Offset(a.x, a.y),
            size = Size(b.x - a.x, b.y - a.y),
            alpha = 0.5f + n
        )
    }
    // Warning chevrons on the near lip.
    quad(
        Offset(p.x(-HW, g0), p.y(0.02f, g0)), Offset(p.x(HW, g0), p.y(0.02f, g0)),
        Offset(p.x(HW, g0 - 0.7f), p.y(0.02f, g0 - 0.7f)), Offset(p.x(-HW, g0 - 0.7f), p.y(0.02f, g0 - 0.7f)),
        Palette.Amber, alpha = 0.85f
    )
}

private fun DrawScope.drawWindZones(e: GameEngine, p: Proj, time: Float) {
    val d = e.distance
    for (wz in e.winds) {
        val z0 = max(wz.z0 - d, NEAR_Z)
        val z1 = min(wz.z1 - d, Cfg.DRAW_DISTANCE)
        if (z1 <= z0) continue

        quad(
            Offset(p.x(-HW, z1), p.y(0.02f, z1)), Offset(p.x(HW, z1), p.y(0.02f, z1)),
            Offset(p.x(HW, z0), p.y(0.02f, z0)), Offset(p.x(-HW, z0), p.y(0.02f, z0)),
            Palette.Wind, alpha = 0.13f
        )

        // Chevrons sliding in the direction of the gust.
        val spacing = 7f
        val drift = (time * 9f) % spacing
        var t = floor(z0 / spacing) * spacing + drift
        while (t < z1) {
            if (t > z0) {
                val a = (1f - t / Cfg.DRAW_DISTANCE).coerceIn(0f, 1f) * 0.55f
                for (k in -1..1) {
                    val cx = k * Cfg.LANE_WIDTH + wz.dir * 0.5f
                    tri(
                        Offset(p.x(cx + wz.dir * 0.9f, t), p.y(0.03f, t)),
                        Offset(p.x(cx - wz.dir * 0.5f, t + 1.1f), p.y(0.03f, t + 1.1f)),
                        Offset(p.x(cx - wz.dir * 0.5f, t - 1.1f), p.y(0.03f, t - 1.1f)),
                        Palette.Wind, alpha = a
                    )
                }
            }
            t += spacing
        }
    }
}
