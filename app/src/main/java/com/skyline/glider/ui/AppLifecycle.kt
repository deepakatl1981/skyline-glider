package com.skyline.glider.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Bridge between the Activity lifecycle and the composable game loop, so the
 * run auto-pauses when the app goes to the background.
 */
object PauseSignal {
    var counter by mutableIntStateOf(0)
        private set

    fun signal() {
        counter++
    }
}
