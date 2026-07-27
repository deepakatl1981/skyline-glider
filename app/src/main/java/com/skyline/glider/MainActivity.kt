package com.skyline.glider

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.skyline.glider.audio.SoundBank
import com.skyline.glider.data.SaveStore
import com.skyline.glider.ui.GameApp
import com.skyline.glider.ui.PauseSignal

class MainActivity : ComponentActivity() {

    private lateinit var save: SaveStore
    private lateinit var sfx: SoundBank

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge, immersive, screen stays awake during a run.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        save = SaveStore(this)
        sfx = SoundBank(this)
        sfx.soundEnabled = save.soundOn
        sfx.hapticsEnabled = save.hapticsOn

        setContent { GameApp(save, sfx) }
    }

    override fun onPause() {
        super.onPause()
        PauseSignal.signal()
    }

    override fun onDestroy() {
        super.onDestroy()
        sfx.release()
    }
}
