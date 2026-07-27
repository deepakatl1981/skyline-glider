package com.skyline.glider.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.skyline.glider.core.RunStats
import java.util.Calendar
import kotlin.math.max
import kotlin.random.Random

/** Result of folding a finished run into the save file. */
data class RunResult(
    val coinsEarned: Int,
    val score: Int,
    val distance: Int,
    val newBestScore: Boolean,
    val newBestDistance: Boolean,
    val missionsCompleted: List<String>
)

/**
 * All persistent progression, backed by SharedPreferences and exposed as
 * Compose state so menus redraw the moment something changes.
 */
class SaveStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---- Wallet & records -----------------------------------------------
    var coins by mutableIntStateOf(prefs.getInt(K_COINS, 0))
        private set
    var bestScore by mutableIntStateOf(prefs.getInt(K_BEST_SCORE, 0))
        private set
    var bestDistance by mutableIntStateOf(prefs.getInt(K_BEST_DIST, 0))
        private set
    var totalRuns by mutableIntStateOf(prefs.getInt(K_RUNS, 0))
        private set
    var lifetimeCoins by mutableIntStateOf(prefs.getInt(K_LIFETIME_COINS, 0))
        private set
    // ---- Cosmetics -------------------------------------------------------
    var unlocked by mutableStateOf(
        prefs.getStringSet(K_UNLOCKED, null)?.toSet() ?: setOf(CHARACTERS.first().id)
    )
        private set
    var selectedId by mutableStateOf(prefs.getString(K_SELECTED, CHARACTERS.first().id)!!)
        private set
    val selected: GliderCharacter get() = characterById(selectedId)

    // ---- Missions --------------------------------------------------------
    var missions by mutableStateOf(decodeMissions(prefs.getString(K_MISSIONS, "") ?: ""))
        private set
    private var missionsCleared = prefs.getInt(K_MISSIONS_CLEARED, 0)

    // ---- Daily reward ----------------------------------------------------
    var dailyStreak by mutableIntStateOf(prefs.getInt(K_DAILY_STREAK, 0))
        private set
    private var lastClaimDay = prefs.getLong(K_DAILY_DAY, -1L)

    // ---- Options ---------------------------------------------------------
    var soundOn by mutableStateOf(prefs.getBoolean(K_SOUND, true))
        private set
    var hapticsOn by mutableStateOf(prefs.getBoolean(K_HAPTICS, true))
        private set
    init {
        if (missions.isEmpty()) rollMissions()
    }

    // =====================================================================
    // Wallet
    // =====================================================================

    fun addCoins(n: Int) {
        if (n <= 0) return
        coins += n
        lifetimeCoins += n
        prefs.edit().putInt(K_COINS, coins).putInt(K_LIFETIME_COINS, lifetimeCoins).apply()
    }

    fun spend(n: Int): Boolean {
        if (coins < n) return false
        coins -= n
        prefs.edit().putInt(K_COINS, coins).apply()
        return true
    }

    // =====================================================================
    // Cosmetics
    // =====================================================================

    fun isUnlocked(id: String) = id in unlocked

    fun buy(character: GliderCharacter): Boolean {
        if (isUnlocked(character.id)) return true
        if (!spend(character.price)) return false
        unlocked = unlocked + character.id
        prefs.edit().putStringSet(K_UNLOCKED, unlocked).apply()
        select(character.id)
        return true
    }

    fun select(id: String) {
        if (!isUnlocked(id)) return
        selectedId = id
        prefs.edit().putString(K_SELECTED, id).apply()
    }

    // =====================================================================
    // Runs & missions
    // =====================================================================

    fun recordRun(stats: RunStats, score: Int): RunResult {
        val dist = stats.distance.toInt()
        val newBestScore = score > bestScore
        val newBestDist = dist > bestDistance
        if (newBestScore) bestScore = score
        if (newBestDist) bestDistance = dist
        totalRuns += 1
        addCoins(stats.coins)

        val completed = ArrayList<String>()
        val updated = missions.map { m ->
            if (m.claimed || m.done) m else {
                val def = m.def
                val gain = if (def == null) 0 else stats.valueFor(def.metric)
                val next = m.copy(progress = m.progress + gain)
                if (next.done) completed += next.text
                next
            }
        }
        missions = updated

        prefs.edit()
            .putInt(K_BEST_SCORE, bestScore)
            .putInt(K_BEST_DIST, bestDistance)
            .putInt(K_RUNS, totalRuns)
            .putString(K_MISSIONS, encodeMissions(updated))
            .apply()

        return RunResult(stats.coins, score, dist, newBestScore, newBestDist, completed)
    }

    /** Claims a finished mission, banks the reward and replaces it with a new one. */
    fun claimMission(index: Int): Int {
        val m = missions.getOrNull(index) ?: return 0
        if (!m.done || m.claimed) return 0
        val reward = m.reward
        addCoins(reward)
        missionsCleared += 1

        val replacement = newMission(missions.filterIndexed { i, _ -> i != index }.map { it.defId })
        missions = missions.toMutableList().also { it[index] = replacement }

        prefs.edit()
            .putInt(K_MISSIONS_CLEARED, missionsCleared)
            .putString(K_MISSIONS, encodeMissions(missions))
            .apply()
        return reward
    }

    fun rollMissions() {
        val fresh = ArrayList<MissionState>(3)
        repeat(3) { fresh += newMission(fresh.map { m -> m.defId }) }
        missions = fresh
        prefs.edit().putString(K_MISSIONS, encodeMissions(fresh)).apply()
    }

    private fun newMission(exclude: List<String>): MissionState {
        val pool = MISSION_POOL.filter { it.id !in exclude }.ifEmpty { MISSION_POOL }
        val def = pool[Random.nextInt(pool.size)]
        val tier = (missionsCleared / 6).coerceIn(0, def.tiers.lastIndex)
        return MissionState(def.id, def.tiers[tier], 0, false)
    }

    // =====================================================================
    // Daily reward
    // =====================================================================

    fun dailyAvailable(): Boolean = today() != lastClaimDay

    /** Which slot (0..6) the next claim will land on. */
    fun dailySlot(): Int {
        val streak = if (today() - lastClaimDay > 1L) 0 else dailyStreak
        return streak % DAILY_REWARDS.size
    }

    fun claimDaily(): Int {
        if (!dailyAvailable()) return 0
        val t = today()
        dailyStreak = if (lastClaimDay >= 0 && t - lastClaimDay == 1L) dailyStreak + 1 else 1
        val slot = (dailyStreak - 1) % DAILY_REWARDS.size
        val reward = DAILY_REWARDS[slot]
        lastClaimDay = t
        addCoins(reward)
        prefs.edit()
            .putInt(K_DAILY_STREAK, dailyStreak)
            .putLong(K_DAILY_DAY, t)
            .apply()
        return reward
    }

    private fun today(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis / 86_400_000L
    }

    // =====================================================================
    // Options
    // =====================================================================

    fun toggleSound() {
        soundOn = !soundOn
        prefs.edit().putBoolean(K_SOUND, soundOn).apply()
    }

    fun toggleHaptics() {
        hapticsOn = !hapticsOn
        prefs.edit().putBoolean(K_HAPTICS, hapticsOn).apply()
    }

    // =====================================================================
    // Encoding
    // =====================================================================

    private fun encodeMissions(list: List<MissionState>): String =
        list.joinToString(";") { "${it.defId}:${it.target}:${it.progress}:${if (it.claimed) 1 else 0}" }

    private fun decodeMissions(raw: String): List<MissionState> {
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull { chunk ->
            val parts = chunk.split(":")
            if (parts.size != 4) return@mapNotNull null
            val def = missionDef(parts[0]) ?: return@mapNotNull null
            val target = parts[1].toIntOrNull() ?: def.tiers.first()
            MissionState(
                defId = def.id,
                target = max(1, target),
                progress = parts[2].toIntOrNull() ?: 0,
                claimed = parts[3] == "1"
            )
        }
    }

    private companion object {
        const val FILE = "skyline_glider"
        const val K_COINS = "coins"
        const val K_LIFETIME_COINS = "lifetime_coins"
        const val K_BEST_SCORE = "best_score"
        const val K_BEST_DIST = "best_distance"
        const val K_RUNS = "total_runs"
        const val K_UNLOCKED = "unlocked"
        const val K_SELECTED = "selected"
        const val K_MISSIONS = "missions"
        const val K_MISSIONS_CLEARED = "missions_cleared"
        const val K_DAILY_STREAK = "daily_streak"
        const val K_DAILY_DAY = "daily_day"
        const val K_SOUND = "sound"
        const val K_HAPTICS = "haptics"
    }
}
