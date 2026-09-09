package com.aeunal.stressball.core

import kotlinx.serialization.Serializable
import kotlin.math.abs

/** How hard a ball is to pull from a chest, and what it is worth. Order matters: later is rarer. */
enum class Rarity(val weight: Double, val sellValue: Double) {
    COMMON(50.0, 5_000.0),
    RARE(27.0, 25_000.0),
    VERY_RARE(14.0, 100_000.0),
    LEGENDARY(6.0, 500_000.0),
    MYTHIC(2.5, 2_500_000.0),
    EXOTIC(0.5, 10_000_000.0),
}

/** Temporary bonuses granted by tapping a golden spark. */
enum class BuffKind { FRENZY, JACKPOT, RECHARGE, WILD_GRIP }

/**
 * One ball ("run"). The player owns several and plays one at a time; the
 * others idle in the garage and still earn.
 *
 * Angular velocity ([omega]) is in rad/s and signed: positive is clockwise
 * seen from above the cap, which is also a clockwise finger circle on screen.
 */
@Serializable
data class BallState(
    /** Unique within the profile, e.g. "b3". */
    val id: String,
    /** [BallTypeDef.id]; decides look, rarity and traits. */
    val typeId: String,
    val omega: Double = 0.0,
    /** Ball-scoped upgrade levels, keyed by [UpgradeDef.id]. */
    val upgrades: Map<String, Int> = emptyMap(),
    /** Resonance combo, 0..maxCombo. */
    val combo: Double = 0.0,
    /** Turbo fuel, 0..1. */
    val turboCharge: Double = 1.0,
    /** Seconds before the turbo starts refilling again after use. */
    val turboCooldown: Double = 0.0,
    /** Points earned by this ball since its last Zen reset. Drives Zen gain. */
    val pointsThisRun: Double = 0.0,
    val totalRevolutions: Double = 0.0,
    val bestRpm: Double = 0.0,
    val prestigeCount: Int = 0,
    /** Points spent on this ball's upgrades; half comes back when it is sold. */
    val invested: Double = 0.0,
    /** True while the ball is viewed from above (circling drives it) instead of from the side (swiping). */
    val topView: Boolean = false,
) {
    fun level(id: String): Int = upgrades[id] ?: 0

    /** Unsigned speed in revolutions per minute. */
    val rpm: Double get() = omegaToRpm(abs(omega))

    /** +1 clockwise (seen from above), -1 counter-clockwise. Never 0 so the UI has a direction. */
    val direction: Int get() = if (omega < 0.0) -1 else 1
}

/**
 * Complete, serializable snapshot of a player's profile: currencies, the
 * garage of balls, account-wide upgrades, skins, achievements and timers.
 * Everything the game needs to resume is in here, so a save/restore cycle is
 * invisible to the player.
 */
@Serializable
data class GameState(
    /** Spendable currency ("Spin Points"), shared by all balls. */
    val points: Double = 0.0,
    /** Premium currency, earned by maxing upgrades. Buys skins. */
    val gems: Long = 0,
    /** Prestige currency. Each Zen permanently multiplies point income. */
    val zen: Long = 0,
    /** Lifetime points across all balls and resets. */
    val totalPointsEarned: Double = 0.0,
    val balls: List<BallState> = listOf(BallState(id = "b1", typeId = BallTypes.DEFAULT)),
    val activeBallId: String = "b1",
    /** Counter for the next ball id. */
    val nextBallNumber: Int = 2,
    /** Account-scoped upgrade levels, keyed by [UpgradeDef.id]. */
    val accountUpgrades: Map<String, Int> = emptyMap(),
    val ownedSkins: Set<String> = emptySet(),
    val outerSkin: String? = null,
    val interiorSkin: String? = null,
    /** Ids of unlocked achievements. */
    val achievements: Set<String> = emptySet(),
    /** Total seconds the game has been ticking. */
    val playTimeSeconds: Double = 0.0,
    /** Wall-clock time (epoch ms) of the last save; used for offline progress. */
    val lastSavedEpochMs: Long = 0L,
    val chestsOpened: Int = 0,
    val ballsSold: Int = 0,
    val sparksTapped: Int = 0,
    /** Seconds until the next golden spark appears (while none is showing). */
    val sparkTimer: Double = Stats.FIRST_SPARK_SECONDS,
    /** Seconds the current golden spark stays on screen; 0 = none showing. */
    val sparkRemaining: Double = 0.0,
    val buff: BuffKind? = null,
    val buffRemaining: Double = 0.0,
    /** Save format version, bumped on incompatible changes. */
    val version: Int = CURRENT_VERSION,
) {
    val active: BallState get() = balls.firstOrNull { it.id == activeBallId } ?: balls.first()

    fun ball(id: String): BallState? = balls.firstOrNull { it.id == id }

    fun accountLevel(id: String): Int = accountUpgrades[id] ?: 0

    /** Level of [id] wherever it lives: on the active ball or on the account. */
    fun level(id: String): Int =
        if (Upgrades.get(id).scope == UpgradeScope.ACCOUNT) accountLevel(id) else active.level(id)

    val activeBuff: BuffKind? get() = if (buffRemaining > 0.0) buff else null

    companion object {
        const val CURRENT_VERSION = 4
    }
}

const val TWO_PI: Double = 2.0 * Math.PI

fun omegaToRpm(omega: Double): Double = omega * 60.0 / TWO_PI
fun rpmToOmega(rpm: Double): Double = rpm * TWO_PI / 60.0
