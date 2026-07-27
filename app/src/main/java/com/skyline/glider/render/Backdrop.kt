package com.skyline.glider.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.skyline.glider.core.Palette
import com.skyline.glider.core.Proj
import kotlin.math.sin
import kotlin.random.Random

/**
 * The static-but-scrolling city behind the track: sky gradient, sun, stars and
 * three parallax layers of procedurally generated buildings.
 *
 * Generated once (it's deterministic), then drawn every frame.
 */
class CityArt(seed: Long = 20260727L) {

    class Bld(val x: Float, val w: Float, val h: Float, val id: Int, val spire: Boolean)

    class Layer(
        val buildings: List<Bld>,
        val tile: Float,          // width of one repeat, in screen widths
        val parallax: Float,      // metres travelled -> screen widths scrolled
        val color: Color,
        val windowColor: Color,
        val windowChance: Float,
        val maxHeightFrac: Float  // fraction of the sky band
    )

    val stars: List<Triple<Float, Float, Float>>
    val layers: List<Layer>

    init {
        val rng = Random(seed)

        stars = List(90) {
            Triple(rng.nextFloat(), rng.nextFloat() * 0.55f, 0.5f + rng.nextFloat() * 1.6f)
        }

        fun makeLayer(
            tile: Float, parallax: Float, color: Color, win: Color,
            winChance: Float, minH: Float, maxH: Float, count: Int
        ): Layer {
            val list = ArrayList<Bld>(count)
            var x = 0f
            var i = 0
            while (x < tile && i < count) {
                val w = (0.055f + rng.nextFloat() * 0.09f) * tile
                val h = minH + rng.nextFloat() * (maxH - minH)
                list += Bld(x, w, h, rng.nextInt(9999), rng.nextFloat() < 0.18f)
                x += w + (0.005f + rng.nextFloat() * 0.035f) * tile
                i++
            }
            return Layer(list, tile, parallax, color, win, winChance, maxH)
        }

        layers = listOf(
            makeLayer(2.4f, 0.0022f, Color(0xFF4B3D8C), Color(0xFF9A86E0), 0.05f, 0.10f, 0.44f, 60),
            makeLayer(1.8f, 0.0052f, Color(0xFF322A6B), Color(0xFFFFC93C), 0.14f, 0.16f, 0.62f, 46),
            makeLayer(1.35f, 0.0105f, Color(0xFF1D1846), Color(0xFFFFD98A), 0.22f, 0.22f, 0.86f, 34)
        )
    }
}

fun DrawScope.drawBackdrop(art: CityArt, p: Proj, distance: Float, time: Float) {
    val w = size.width
    val h = size.height
    val horizon = p.horizonY

    // --- Sky ------------------------------------------------------------
    drawRect(
        brush = Brush.verticalGradient(
            0.00f to Color(0xFF07061A),
            0.28f to Color(0xFF1B1147),
            0.55f to Color(0xFF4A1C63),
            0.78f to Color(0xFF9C2F63),
            0.93f to Color(0xFFE2603F),
            1.00f to Color(0xFFFFA24B),
            startY = 0f,
            endY = horizon
        ),
        topLeft = Offset(0f, 0f),
        size = Size(w, horizon)
    )

    // --- Stars ----------------------------------------------------------
    for ((i, s) in art.stars.withIndex()) {
        val (sx, sy, sr) = s
        val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(time * 1.7f + i * 1.3f))
        val fade = (1f - sy / 0.55f).coerceIn(0f, 1f)
        drawCircle(
            Color.White,
            sr,
            Offset(sx * w, sy * horizon),
            alpha = 0.55f * twinkle * fade
        )
    }

    // --- Sun sinking into the haze ---------------------------------------
    val sunX = w * 0.72f
    val sunY = horizon - h * 0.045f
    val sunR = w * 0.115f
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0x66FFB367), Color(0x00FFB367)),
            center = Offset(sunX, sunY),
            radius = sunR * 3.2f
        ),
        radius = sunR * 3.2f,
        center = Offset(sunX, sunY)
    )
    drawCircle(Color(0xFFFFD08A), sunR, Offset(sunX, sunY))
    drawCircle(Color(0xFFFFF0C4), sunR * 0.72f, Offset(sunX, sunY))

    // --- Parallax skyline -------------------------------------------------
    for (layer in art.layers) {
        val tilePx = layer.tile * w
        val scroll = (distance * layer.parallax * w) % tilePx
        val base = horizon + h * 0.006f
        val band = horizon * 0.92f

        for (rep in -1..1) {
            val originX = rep * tilePx - scroll
            for (b in layer.buildings) {
                // Building x/w are stored in screen-width units, so tilePx == tile * w.
                val px = originX + b.x * w
                val pw = b.w * w
                if (px + pw < -w * 0.1f || px > w * 1.1f) continue
                val ph = b.h * band
                drawRect(layer.color, Offset(px, base - ph), Size(pw, ph + h * 0.02f))

                if (b.spire) {
                    tri(
                        Offset(px + pw * 0.5f, base - ph - band * 0.09f),
                        Offset(px + pw * 0.12f, base - ph),
                        Offset(px + pw * 0.88f, base - ph),
                        layer.color
                    )
                    drawCircle(Palette.Magenta, pw * 0.06f, Offset(px + pw * 0.5f, base - ph - band * 0.1f),
                        alpha = 0.45f + 0.55f * (0.5f + 0.5f * sin(time * 2.4f + b.id)))
                }

                if (layer.windowChance > 0f) {
                    val cols = (pw / (w * 0.016f)).toInt().coerceIn(1, 7)
                    val rows = (ph / (h * 0.020f)).toInt().coerceIn(1, 24)
                    val cw = pw / (cols + 1f)
                    val rh = ph / (rows + 1f)
                    for (c in 0 until cols) for (r in 0 until rows) {
                        val n = hash01(b.id * 733 + c * 61 + r * 7)
                        if (n > layer.windowChance) continue
                        val flicker = if (hash01(b.id + c * 13 + r) > 0.96f)
                            (0.4f + 0.6f * (0.5f + 0.5f * sin(time * 3f + r + c))) else 1f
                        drawRect(
                            layer.windowColor,
                            Offset(px + cw * (c + 0.7f), base - ph + rh * (r + 0.7f)),
                            Size(cw * 0.5f, rh * 0.45f),
                            alpha = 0.85f * flicker
                        )
                    }
                }
            }
        }
    }

    // --- Haze band that fuses the skyline into the track -------------------
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0x00FF8A3D), Color(0x33FF8A3D), Color(0x00070619)),
            startY = horizon - h * 0.05f,
            endY = horizon + h * 0.05f
        ),
        topLeft = Offset(0f, horizon - h * 0.05f),
        size = Size(w, h * 0.10f)
    )
}
