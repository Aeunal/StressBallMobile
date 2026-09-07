package com.aeunal.stressball.core

import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * Complete, serializable snapshot of a player's progress.
 *
 * Everything the game needs to resume is in here, including transient-looking
 * values such as the current angular velocity, so that a save/restore cycle is
 * invisible to the player.
 *
 * Angular velocity ([omega]) is stored in radians per second and is signed:
 * positive means clockwise when looking down at the cap, which is also the
 * direction of a clockwise finger circle on the screen.
 */
@Serializable
data class GameState(
    /** Spendable currency ("Spin Points"). */
    val points: Double = 0.0,
    /** Points earned since the last Zen reset. Drives Zen gain. */
    val pointsThisRun: Double = 0.0,
    /** Lifetime points across all resets. */
    val totalPointsEarned: Double = 0.0,
    /** Current signed angular velocity of the ball in rad/s. */
    val omega: Double = 0.0,
    /** Purchased upgrade levels, keyed by [UpgradeDef.id]. */
    val upgrades: Map<String, Int> = emptyMap(),
    /** Prestige currency. Each Zen permanently multiplies point income. */
    val zen: Long = 0,
    /** Number of Zen resets performed. */
    val prestigeCount: Int = 0,
    /** Highest RPM ever reached (lifetime). */
    val bestRpm: Double = 0.0,
    /** Lifetime revolutions of the ball. */
    val totalRevolutions: Double = 0.0,
    /** Resonance combo, 0..maxCombo. Builds while spinning fast. */
    val combo: Double = 0.0,
    /** Turbo charge, 0 (empty) .. 1 (full). Depletes while the turbo is held. */
    val turboCharge: Double = 1.0,
    /** Total seconds the game has been ticking. */
    val playTimeSeconds: Double = 0.0,
    /** Wall-clock time (epoch ms) of the last save; used for offline progress. */
    val lastSavedEpochMs: Long = 0L,
    /** Ids of unlocked achievements. */
    val achievements: Set<String> = emptySet(),
    /** Ids of purchased cosmetics. Free defaults are always owned implicitly. */
    val ownedCosmetics: Set<String> = emptySet(),
    /** Equipped cosmetic id per [CosmeticSlot] name. Missing slots use the default. */
    val equipped: Map<String, String> = emptyMap(),
    /** Save format version, bumped on incompatible changes. */
    val version: Int = CURRENT_VERSION,
) {
    fun level(id: String): Int = upgrades[id] ?: 0

    /** Unsigned speed in revolutions per minute. */
    val rpm: Double get() = omegaToRpm(abs(omega))

    /** +1 clockwise (seen from above), -1 counter-clockwise. Never 0 so the UI has a direction. */
    val direction: Int get() = if (omega < 0.0) -1 else 1

    companion object {
        const val CURRENT_VERSION = 2
    }
}

const val TWO_PI: Double = 2.0 * Math.PI

fun omegaToRpm(omega: Double): Double = omega * 60.0 / TWO_PI
fun rpmToOmega(rpm: Double): Double = rpm * TWO_PI / 60.0
