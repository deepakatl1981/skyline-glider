package com.skyline.glider.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skyline.glider.core.Palette
import com.skyline.glider.core.Proj
import com.skyline.glider.data.DAILY_REWARDS
import com.skyline.glider.data.SaveStore
import com.skyline.glider.render.CityArt
import com.skyline.glider.render.drawBackdrop
import com.skyline.glider.render.drawHeroPortrait

/** Slowly drifting city used behind every non-gameplay screen. */
@Composable
fun MenuBackdrop(content: @Composable BoxScope.() -> Unit) {
    val art = remember { CityArt(90210L) }
    var t by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) t += (now - last) / 1_000_000_000f
                last = now
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Palette.Ink)) {
        Canvas(Modifier.fillMaxSize()) {
            val p = Proj(size.width, size.height)
            drawBackdrop(art, p, t * 6.5f, t)
            drawRect(
                Brush.verticalGradient(
                    listOf(Color(0x0007061A), Color(0xE607061A), Color(0xFF07061A)),
                    startY = p.horizonY - size.height * 0.06f,
                    endY = size.height
                ),
                topLeft = Offset(0f, p.horizonY - size.height * 0.06f),
                size = Size(size.width, size.height - p.horizonY + size.height * 0.06f)
            )
        }
        content()
    }
}

@Composable
fun MenuScreen(
    save: SaveStore,
    onPlay: () -> Unit,
    onShop: () -> Unit,
    onMissions: () -> Unit
) {
    var showDaily by remember { mutableStateOf(save.dailyAvailable()) }
    var portraitT by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) portraitT += (now - last) / 1_000_000_000f
                last = now
            }
        }
    }

    MenuBackdrop {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CoinChip(save.coins)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleChip("SFX", save.soundOn) { save.toggleSound() }
                    ToggleChip("HAPTIC", save.hapticsOn) { save.toggleHaptics() }
                }
            }

            Spacer(Modifier.height(26.dp))

            Text(
                "SKYLINE",
                color = Palette.Text,
                fontSize = 46.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 9.sp
            )
            Text(
                "G L I D E R",
                color = Palette.Neon,
                fontSize = 21.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 11.sp
            )

            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(170.dp)
            ) {
                drawHeroPortrait(save.selected, portraitT)
            }

            Text(
                save.selected.name.uppercase(),
                color = save.selected.accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp
            )
            Text(
                save.selected.tagline,
                color = Palette.TextDim,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(18.dp))

            NeonButton("PLAY", Modifier.fillMaxWidth(), onClick = onPlay)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NeonButton("SHOP", Modifier.weight(1f), accent = Palette.Gold, onClick = onShop)
                NeonButton(
                    "MISSIONS",
                    Modifier.weight(1f),
                    accent = Palette.Shield,
                    subtitle = if (save.missions.any { it.done }) "ready to claim" else null,
                    onClick = onMissions
                )
            }

            if (save.dailyAvailable()) {
                Spacer(Modifier.height(10.dp))
                NeonButton(
                    "DAILY REWARD",
                    Modifier.fillMaxWidth(),
                    accent = Palette.Magenta,
                    subtitle = "day ${save.dailySlot() + 1} of 7"
                ) { showDaily = true }
            }

            Spacer(Modifier.weight(1f))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MiniStat("BEST", "${save.bestScore}")
                MiniStat("FARTHEST", "${save.bestDistance} m")
                MiniStat("RUNS", "${save.totalRuns}")
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "swipe ← →  change rooftop     swipe ↑  jump, again to glide     swipe ↓  slide\n" +
                    "keyboard: arrows or WASD, space to jump, esc to pause",
                color = Palette.TextDim.copy(alpha = 0.75f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }

        if (showDaily) {
            DailyRewardSheet(save) { showDaily = false }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(label, color = Palette.TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
    }
}

@Composable
private fun ToggleChip(label: String, on: Boolean, onToggle: () -> Unit) {
    val c = if (on) Palette.Neon else Palette.TextDim
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x66120E28))
            .border(1.dp, c.copy(alpha = 0.5f), RoundedCornerShape(50))
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, color = c, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
    }
}

@Composable
private fun DailyRewardSheet(save: SaveStore, onClose: () -> Unit) {
    var claimed by remember { mutableStateOf(0) }
    val slot = save.dailySlot()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xD907061A))
            .clickable(enabled = claimed > 0) { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Panel(Modifier.padding(24.dp), accent = Palette.Magenta) {
            ScreenTitle("DAILY REWARD", Palette.Magenta)
            Text(
                if (claimed > 0) "Come back tomorrow to keep the streak alive."
                else "Seven days of rooftop generosity.",
                color = Palette.TextDim,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DAILY_REWARDS.forEachIndexed { i, amount ->
                    val isToday = i == slot
                    val past = i < slot
                    Column(
                        Modifier
                            .width(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isToday) Palette.Gold.copy(alpha = 0.22f) else Color(0x33FFFFFF))
                            .border(
                                1.dp,
                                if (isToday) Palette.Gold else Color(0x33FFFFFF),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "${i + 1}",
                            color = if (past) Palette.TextDim else Palette.Text,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (past) Palette.TextDim else Palette.Gold)
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "$amount",
                            color = if (isToday) Palette.Gold else Palette.TextDim,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            if (claimed > 0) {
                Text(
                    "+$claimed coins  ·  streak ${save.dailyStreak}",
                    color = Palette.Gold,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )
                NeonButton("NICE", Modifier.fillMaxWidth(), onClick = onClose)
            } else {
                NeonButton("CLAIM ${DAILY_REWARDS[slot]} COINS", Modifier.fillMaxWidth(), accent = Palette.Gold) {
                    claimed = save.claimDaily()
                }
            }
        }
    }
}
