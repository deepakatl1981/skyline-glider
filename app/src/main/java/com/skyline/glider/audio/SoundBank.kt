package com.skyline.glider.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.skyline.glider.core.GameEvent
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Chiptune SFX with zero binary assets: every sound is synthesised into a WAV
 * in the cache directory on first launch, then played through [SoundPool].
 */
class SoundBank(context: Context) {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(10)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids = HashMap<GameEvent, Int>()
    private val ready = HashSet<Int>()
    private val lastPlayed = HashMap<GameEvent, Long>()

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }.getOrNull()

    var soundEnabled = true
    var hapticsEnabled = true

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) ready += sampleId
        }
        runCatching { buildBank(context) }
    }

    private fun buildBank(context: Context) {
        val dir = File(context.cacheDir, "sfx").apply { mkdirs() }

        fun register(event: GameEvent, name: String, pcm: ShortArray) {
            val f = File(dir, "$name.wav")
            if (!f.exists() || f.length() < 64) writeWav(f, pcm)
            ids[event] = pool.load(f.absolutePath, 1)
        }

        register(GameEvent.JUMP, "jump", sweep(120, 430f, 900f, 0.42f, decay = 5f))
        register(GameEvent.LAND, "land", sweep(90, 220f, 90f, 0.34f, decay = 9f, square = true))
        register(GameEvent.SLIDE, "slide", noise(190, 0.26f, decay = 7f))
        register(GameEvent.LANE_CHANGE, "lane", sweep(48, 880f, 1100f, 0.22f, decay = 16f))
        register(GameEvent.COIN, "coin", arpeggio(intArrayOf(1180, 1760), 45, 0.3f))
        register(GameEvent.POWERUP, "power", arpeggio(intArrayOf(660, 880, 1320, 1760), 70, 0.32f))
        register(GameEvent.GLIDE_OPEN, "glide", mix(sweep(280, 300f, 1250f, 0.3f, decay = 3f), noise(280, 0.12f, decay = 3f)))
        register(GameEvent.NEAR_MISS, "near", sweep(70, 1500f, 1900f, 0.2f, decay = 14f))
        register(GameEvent.SHIELD_BREAK, "shield", sweep(300, 760f, 180f, 0.36f, decay = 4f, square = true))
        register(GameEvent.DEATH, "death", sweep(520, 520f, 70f, 0.42f, decay = 2.4f, square = true))
        register(GameEvent.REVIVE, "revive", arpeggio(intArrayOf(330, 494, 660, 988), 90, 0.34f))
    }

    // =====================================================================
    // Playback
    // =====================================================================

    fun play(event: GameEvent) {
        if (soundEnabled) {
            val now = System.currentTimeMillis()
            val gap = if (event == GameEvent.COIN) 35L else 55L
            if (now - (lastPlayed[event] ?: 0L) >= gap) {
                lastPlayed[event] = now
                ids[event]?.let { id ->
                    if (id in ready) {
                        val rate = if (event == GameEvent.COIN) 0.94f + Random.nextFloat() * 0.16f else 1f
                        runCatching { pool.play(id, 0.85f, 0.85f, 1, 0, rate) }
                    }
                }
            }
        }
        haptic(event)
    }

    private fun haptic(event: GameEvent) {
        if (!hapticsEnabled) return
        val ms = when (event) {
            GameEvent.LANE_CHANGE -> 8L
            GameEvent.JUMP, GameEvent.SLIDE -> 12L
            GameEvent.GLIDE_OPEN, GameEvent.POWERUP -> 20L
            GameEvent.SHIELD_BREAK -> 40L
            GameEvent.DEATH -> 90L
            else -> return
        }
        val v = vibrator ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        }
    }

    fun release() {
        runCatching { pool.release() }
    }

    // =====================================================================
    // Tiny synthesiser
    // =====================================================================

    private fun sweep(
        ms: Int,
        fromHz: Float,
        toHz: Float,
        volume: Float,
        decay: Float = 6f,
        square: Boolean = false
    ): ShortArray {
        val n = SAMPLE_RATE * ms / 1000
        val out = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toFloat() / n
            val f = fromHz + (toHz - fromHz) * t
            phase += 2.0 * PI * f / SAMPLE_RATE
            val raw = if (square) (if (sin(phase) >= 0) 1.0 else -1.0) else sin(phase)
            val env = exp(-decay * t.toDouble()) * fadeIn(t)
            out[i] = (raw * env * volume * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun noise(ms: Int, volume: Float, decay: Float = 6f): ShortArray {
        val n = SAMPLE_RATE * ms / 1000
        val out = ShortArray(n)
        var last = 0.0
        for (i in 0 until n) {
            val t = i.toFloat() / n
            // Low-passed white noise reads as "whoosh" rather than "static".
            last = last * 0.72 + (Random.nextDouble() * 2 - 1) * 0.28
            val env = exp(-decay * t.toDouble()) * fadeIn(t)
            out[i] = (last * env * volume * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun arpeggio(notes: IntArray, noteMs: Int, volume: Float): ShortArray {
        val parts = notes.map { hz -> sweep(noteMs, hz.toFloat(), hz * 1.02f, volume, decay = 5.5f) }
        val total = parts.sumOf { it.size }
        val out = ShortArray(total)
        var o = 0
        for (p in parts) { p.copyInto(out, o); o += p.size }
        return out
    }

    private fun mix(a: ShortArray, b: ShortArray): ShortArray {
        val n = maxOf(a.size, b.size)
        val out = ShortArray(n)
        for (i in 0 until n) {
            val v = (a.getOrElse(i) { 0 }).toInt() + (b.getOrElse(i) { 0 }).toInt()
            out[i] = v.coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun fadeIn(t: Float): Double = if (t < 0.02f) (t / 0.02f).toDouble() else 1.0

    private fun writeWav(file: File, pcm: ShortArray) {
        val dataBytes = pcm.size * 2
        val buf = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray())
        buf.putInt(36 + dataBytes)
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(16)                    // PCM chunk size
        buf.putShort(1)                   // format = PCM
        buf.putShort(1)                   // channels = mono
        buf.putInt(SAMPLE_RATE)
        buf.putInt(SAMPLE_RATE * 2)       // byte rate
        buf.putShort(2)                   // block align
        buf.putShort(16)                  // bits per sample
        buf.put("data".toByteArray())
        buf.putInt(dataBytes)
        for (s in pcm) buf.putShort(s)
        FileOutputStream(file).use { it.write(buf.array()) }
    }

    private companion object {
        const val SAMPLE_RATE = 22050
    }
}
