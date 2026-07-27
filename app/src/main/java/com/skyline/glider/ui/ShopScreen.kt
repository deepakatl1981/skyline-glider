package com.skyline.glider.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skyline.glider.core.Palette
import com.skyline.glider.data.CHARACTERS
import com.skyline.glider.data.GliderCharacter
import com.skyline.glider.data.SaveStore
import com.skyline.glider.render.drawHeroPortrait

@Composable
fun ShopScreen(save: SaveStore, onBack: () -> Unit) {
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

    MenuBackdrop {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScreenTitle("GLIDERS", Palette.Gold)
                CoinChip(save.coins)
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "Skins are cosmetic — every glider handles the same.",
                color = Palette.TextDim,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(14.dp))

            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(CHARACTERS) { ch ->
                    CharacterCard(
                        ch = ch,
                        time = t,
                        unlocked = save.isUnlocked(ch.id),
                        selected = save.selectedId == ch.id,
                        affordable = save.coins >= ch.price,
                        onClick = {
                            if (save.isUnlocked(ch.id)) save.select(ch.id) else save.buy(ch)
                        }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            NeonButton("BACK", Modifier.fillMaxWidth(), accent = Palette.TextDim, onClick = onBack)
        }
    }
}

@Composable
private fun CharacterCard(
    ch: GliderCharacter,
    time: Float,
    unlocked: Boolean,
    selected: Boolean,
    affordable: Boolean,
    onClick: () -> Unit
) {
    val accent = if (selected) ch.accent else Palette.TextDim

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xCC110D2A))
            .border(
                if (selected) 2.dp else 1.dp,
                accent.copy(alpha = if (selected) 0.9f else 0.28f),
                RoundedCornerShape(18.dp)
            )
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(76.dp)) {
            drawHeroPortrait(ch, time, locked = !unlocked)
        }

        Spacer(Modifier.size(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                ch.name.uppercase(),
                color = if (unlocked) Palette.Text else Palette.TextDim,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            Text(ch.tagline, color = Palette.TextDim, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            when {
                selected -> Tag("EQUIPPED", ch.accent)
                unlocked -> Tag("TAP TO EQUIP", Palette.Neon)
                affordable -> Tag("${ch.price} COINS", Palette.Gold)
                else -> Tag("${ch.price} COINS", Palette.TextDim)
            }
        }
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
    }
}
