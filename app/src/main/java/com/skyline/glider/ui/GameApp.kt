package com.skyline.glider.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.skyline.glider.audio.SoundBank
import com.skyline.glider.core.Palette
import com.skyline.glider.data.SaveStore

enum class Screen { MENU, GAME, SHOP, MISSIONS }

@Composable
fun GameApp(save: SaveStore, sfx: SoundBank) {
    var screen by remember { mutableStateOf(Screen.MENU) }

    LaunchedEffect(save.soundOn, save.hapticsOn) {
        sfx.soundEnabled = save.soundOn
        sfx.hapticsEnabled = save.hapticsOn
    }

    BackHandler(enabled = screen != Screen.MENU) {
        screen = Screen.MENU
    }

    Box(Modifier.fillMaxSize().background(Palette.Ink)) {
        when (screen) {
            Screen.MENU -> MenuScreen(
                save = save,
                onPlay = { screen = Screen.GAME },
                onShop = { screen = Screen.SHOP },
                onMissions = { screen = Screen.MISSIONS }
            )

            // The key() effect of a distinct composable identity: leaving GAME
            // disposes the engine, so PLAY always starts a genuinely fresh run.
            Screen.GAME -> GameScreen(save, sfx) { screen = Screen.MENU }

            Screen.SHOP -> ShopScreen(save) { screen = Screen.MENU }

            Screen.MISSIONS -> MissionsScreen(save) { screen = Screen.MENU }
        }
    }
}
