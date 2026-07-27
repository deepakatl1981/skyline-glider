package com.skyline.glider.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skyline.glider.core.Palette

@Composable
fun NeonButton(
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = Palette.Neon,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val a = if (enabled) 1f else 0.35f
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        accent.copy(alpha = 0.22f * a),
                        accent.copy(alpha = 0.08f * a)
                    )
                )
            )
            .border(2.dp, accent.copy(alpha = 0.75f * a), RoundedCornerShape(18.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 26.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                color = Palette.Text.copy(alpha = a),
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.6.sp,
                textAlign = TextAlign.Center
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = accent.copy(alpha = 0.85f * a),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    accent: Color = Palette.Neon,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xE60D0A24))
            .border(1.5.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun CoinChip(coins: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xAA1A1436))
            .border(1.dp, Palette.Gold.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Palette.Gold)
                .size(14.dp)
        )
        Text(
            coins.toString(),
            color = Palette.Gold,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
fun StatLine(label: String, value: String, accent: Color = Palette.Text) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Palette.TextDim, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(value, color = accent, fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun ProgressBar(fraction: Float, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0x33FFFFFF))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.7f), accent)))
        )
    }
}

@Composable
fun ScreenTitle(text: String, accent: Color = Palette.Neon) {
    Text(
        text,
        color = Palette.Text,
        fontSize = 26.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 3.sp
    )
}
