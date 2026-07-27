package com.skyline.glider.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.skyline.glider.core.Proj
import kotlin.math.abs

/** Reusable path so the hot drawing loop doesn't allocate one per shape. */
private val scratch = Path()

fun DrawScope.quad(a: Offset, b: Offset, c: Offset, d: Offset, color: Color, alpha: Float = 1f) {
    scratch.reset()
    scratch.moveTo(a.x, a.y); scratch.lineTo(b.x, b.y)
    scratch.lineTo(c.x, c.y); scratch.lineTo(d.x, d.y)
    scratch.close()
    drawPath(scratch, color, alpha = alpha)
}

fun DrawScope.quad(a: Offset, b: Offset, c: Offset, d: Offset, brush: Brush, alpha: Float = 1f) {
    scratch.reset()
    scratch.moveTo(a.x, a.y); scratch.lineTo(b.x, b.y)
    scratch.lineTo(c.x, c.y); scratch.lineTo(d.x, d.y)
    scratch.close()
    drawPath(scratch, brush, alpha = alpha)
}

fun DrawScope.tri(a: Offset, b: Offset, c: Offset, color: Color, alpha: Float = 1f) {
    scratch.reset()
    scratch.moveTo(a.x, a.y); scratch.lineTo(b.x, b.y); scratch.lineTo(c.x, c.y)
    scratch.close()
    drawPath(scratch, color, alpha = alpha)
}

/**
 * Draws an axis-aligned world-space box in perspective: top face, one visible
 * side face, then the front face. Good enough to read as solid geometry
 * without a real 3D pipeline.
 */
fun DrawScope.box3d(
    p: Proj,
    centerX: Float,
    z: Float,
    width: Float,
    height: Float,
    depth: Float,
    yBase: Float = 0f,
    front: Color,
    top: Color,
    side: Color,
    alpha: Float = 1f
) {
    val zf = z - depth * 0.5f
    val zb = z + depth * 0.5f
    val l = centerX - width * 0.5f
    val r = centerX + width * 0.5f
    val y0 = yBase
    val y1 = yBase + height

    // Top face (visible because the camera looks slightly down).
    quad(
        Offset(p.x(l, zb), p.y(y1, zb)), Offset(p.x(r, zb), p.y(y1, zb)),
        Offset(p.x(r, zf), p.y(y1, zf)), Offset(p.x(l, zf), p.y(y1, zf)),
        top, alpha
    )

    // Whichever side face the camera can see.
    if (abs(centerX) > 0.05f) {
        val sx = if (centerX > 0f) l else r
        quad(
            Offset(p.x(sx, zf), p.y(y1, zf)), Offset(p.x(sx, zb), p.y(y1, zb)),
            Offset(p.x(sx, zb), p.y(y0, zb)), Offset(p.x(sx, zf), p.y(y0, zf)),
            side, alpha
        )
    }

    // Front face.
    quad(
        Offset(p.x(l, zf), p.y(y1, zf)), Offset(p.x(r, zf), p.y(y1, zf)),
        Offset(p.x(r, zf), p.y(y0, zf)), Offset(p.x(l, zf), p.y(y0, zf)),
        front, alpha
    )
}

/** A two-segment limb with a bend, used for the runner's arms and legs. */
fun DrawScope.limb(root: Offset, tip: Offset, bendX: Float, bendY: Float, color: Color, width: Float) {
    val mid = Offset((root.x + tip.x) * 0.5f + bendX, (root.y + tip.y) * 0.5f + bendY)
    drawLine(color, root, mid, width, StrokeCap.Round)
    drawLine(color, mid, tip, width, StrokeCap.Round)
}

fun DrawScope.glowCircle(center: Offset, radius: Float, color: Color, rings: Int = 3) {
    for (i in rings downTo 1) {
        val f = i / rings.toFloat()
        drawCircle(color, radius * (1f + f * 1.5f), center, alpha = 0.10f * (1f - f) + 0.05f)
    }
    drawCircle(color, radius, center)
}

fun DrawScope.ringStroke(center: Offset, radius: Float, color: Color, width: Float, alpha: Float = 1f) {
    drawCircle(color, radius, center, alpha = alpha, style = Stroke(width = width))
}

/** Deterministic 0..1 noise so procedural details never flicker between frames. */
fun hash01(seed: Int): Float {
    var x = seed * 374761393 + 668265263
    x = (x xor (x shr 13)) * 1274126177
    x = x xor (x shr 16)
    return (x and 0x7FFFFFFF) / 2147483647f
}
