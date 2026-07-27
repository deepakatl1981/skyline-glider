package com.skyline.glider.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skyline.glider.core.Palette
import com.skyline.glider.data.MissionState
import com.skyline.glider.data.SaveStore

@Composable
fun MissionsScreen(save: SaveStore, onBack: () -> Unit) {
    var lastReward by remember { mutableStateOf(0) }

    MenuBackdrop {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 20.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScreenTitle("MISSIONS", Palette.Shield)
                CoinChip(save.coins)
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "Progress carries across runs. Clear one and a fresh mission takes its place.",
                color = Palette.TextDim,
                fontSize = 11.sp
            )

            Spacer(Modifier.height(18.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                save.missions.forEachIndexed { index, mission ->
                    MissionCard(mission) {
                        val reward = save.claimMission(index)
                        if (reward > 0) lastReward = reward
                    }
                }
            }

            if (lastReward > 0) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "+$lastReward coins banked",
                    color = Palette.Gold,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(Modifier.weight(1f))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NeonButton("REROLL ALL", Modifier.weight(1f), accent = Palette.Magenta) {
                    save.rollMissions()
                    lastReward = 0
                }
                NeonButton("BACK", Modifier.weight(1f), accent = Palette.TextDim, onClick = onBack)
            }
        }
    }
}

@Composable
private fun MissionCard(mission: MissionState, onClaim: () -> Unit) {
    val done = mission.done
    val accent = if (done) Palette.Gold else Palette.Shield

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xCC110D2A))
            .border(if (done) 2.dp else 1.dp, accent.copy(alpha = if (done) 0.9f else 0.25f), RoundedCornerShape(18.dp))
            .clickable(enabled = done) { onClaim() }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                mission.text,
                color = Palette.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Palette.Gold.copy(alpha = 0.16f))
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Text(
                    "+${mission.reward}",
                    color = Palette.Gold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        ProgressBar(mission.fraction, accent)

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "${mission.progress.coerceAtMost(mission.target)} / ${mission.target}",
                color = Palette.TextDim,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            if (done) {
                Text(
                    "TAP TO CLAIM",
                    color = Palette.Gold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.4.sp
                )
            }
        }
    }
}
