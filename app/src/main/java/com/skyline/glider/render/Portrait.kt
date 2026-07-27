package com.skyline.glider.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.skyline.glider.data.GliderCharacter
import kotlin.math.sin

/**
 * A standalone gliding pose used on shop cards and the main menu — same visual
 * language as the in-game hero, but with no simulation behind it.
 */
fun DrawScope.drawHeroPortrait(ch: GliderCharacter, time: Float, locked: Boolean = false) {
    val w = size.width
    val h = size.height
    val unit = h * 0.34f
    val cx = w * 0.5f
    val cy = h * 0.56f + sin(time * 1.6f) * h * 0.03f
    val dim = if (locked) 0.32f else 1f

    fun mx(v: Float) = cx + v * unit
    fun my(v: Float) = cy - v * unit

    val suit = ch.suit.copy(alpha = dim)
    val accent = ch.accent.copy(alpha = dim)
    val wingColor = ch.wing.copy(alpha = dim)

    // Glow behind the figure.
    drawCircle(
        Brush.radialGradient(
            listOf(wingColor.copy(alpha = 0.22f * dim), Color.Transparent),
            center = Offset(cx, cy),
            radius = unit * 1.9f
        ),
        radius = unit * 1.9f,
        center = Offset(cx, cy)
    )

    val sho = Offset(mx(0.22f), my(0.18f))
    val hip = Offset(mx(-0.26f), my(-0.06f))
    val handTop = Offset(mx(0.60f), my(0.58f))
    val handBottom = Offset(mx(0.56f), my(-0.24f))
    val footTop = Offset(mx(-0.86f), my(0.24f))
    val footBottom = Offset(mx(-0.82f), my(-0.40f))

    fun membrane(hand: Offset, foot: Offset, alpha: Float) {
        val path = Path().apply {
            moveTo(sho.x, sho.y); lineTo(hand.x, hand.y)
            lineTo(foot.x, foot.y); lineTo(hip.x, hip.y); close()
        }
        drawPath(path, wingColor.copy(alpha = alpha * dim))
        drawPath(path, wingColor.copy(alpha = dim), style = Stroke(width = 2f))
    }

    membrane(handTop, footTop, 0.35f)
    membrane(handBottom, footBottom, 0.6f)

    limb(hip, footTop, -unit * 0.05f, -unit * 0.04f, suit, unit * 0.13f)
    limb(sho, handTop, unit * 0.04f, -unit * 0.05f, suit, unit * 0.11f)
    drawLine(suit, hip, sho, unit * 0.30f, StrokeCap.Round)
    drawLine(accent, Offset((hip.x + sho.x) / 2, (hip.y + sho.y) / 2), sho, unit * 0.10f, StrokeCap.Round)
    limb(hip, footBottom, -unit * 0.05f, unit * 0.04f, suit, unit * 0.13f)
    limb(sho, handBottom, unit * 0.04f, unit * 0.05f, suit, unit * 0.11f)

    // Head with helmet and visor.
    val hx = mx(0.44f)
    val hy = my(0.24f)
    val r = unit * 0.17f
    drawCircle(ch.skin.copy(alpha = dim), r, Offset(hx, hy))
    drawArc(
        color = suit,
        startAngle = 180f, sweepAngle = 180f, useCenter = true,
        topLeft = Offset(hx - r * 1.12f, hy - r * 1.12f),
        size = Size(r * 2.24f, r * 2.24f)
    )
    drawLine(accent, Offset(hx - r * 1.05f, hy - r * 0.1f), Offset(hx + r * 1.05f, hy - r * 0.1f), unit * 0.07f, StrokeCap.Round)
}
