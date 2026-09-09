package com.aeunal.stressball.core

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Pure formulas mapping upgrade levels to gameplay numbers.
 *
 * Keeping them here (rather than inside the engine) lets the shop show exact
 * "next level" effects and makes balancing a matter of editing one file.
 *
 * The physical model, all angular quantities in rad, rad/s, rad/s²:
 *
 * - The ball has moment of inertia `I = inertia(flywheel)` (stock = 1).
 * - Bearing friction is Coulomb + viscous: `α = -(c_c + c_v·|ω|)·sign(ω) / I`,
 *   scaled by the ball type's drag trait.
 * - Air drag grows with the square of speed: `α = -c_a·ω²·sign(ω)`, so it is
 *   nothing at 500 RPM and the wall at 15,000. Aero Shell lowers `c_a`.
 * - The finger is a slipping clutch: it pulls the ball towards its own
 *   implied speed with rate `gripRate`, but can transmit at most
 *   `gripAccel` of acceleration (the rubber only grips so hard). Braking with
 *   the finger recovers energy as points once the Kinetic Harvester is fitted.
 * - The turbo (a squeeze of the ball) is a thrust proportional to how hard
 *   it is squeezed, burning fuel at the same rate.
 *
 * Tuning intent: a fresh ball is stiff. It resists the finger, bleeds speed
 * fast and needs constant circling. Each upgrade level should be felt.
 */
object Stats {
    // --- Finger contact --------------------------------------------------

    /** Multiplier applied to a fast finger's twist (circling) speed. */
    fun gearRatio(level: Int): Double = 1.0 + 0.5 * level

    /** Below this finger speed (rad/s) the gear is bypassed: the ball follows 1:1. */
    const val GEAR_SLIP_START = 3.0
    /** Above this finger speed the full gear ratio applies. */
    const val GEAR_SLIP_FULL = 9.0

    /**
     * Ball speed a circling finger is asking for. Slow, deliberate turns move
     * the ball exactly as far as the finger moved (it feels like holding the
     * real thing); fast circles engage the gear and multiply.
     */
    fun gearedTarget(fingerOmega: Double, gearLevel: Int): Double {
        val speed = abs(fingerOmega)
        val t = ((speed - GEAR_SLIP_START) / (GEAR_SLIP_FULL - GEAR_SLIP_START)).coerceIn(0.0, 1.0)
        val blend = t * t * (3.0 - 2.0 * t) // smoothstep
        val ratio = 1.0 + (gearRatio(gearLevel) - 1.0) * blend
        return fingerOmega * ratio
    }

    /**
     * Multiplier on the speed a swipe across the face implies. Above 1 the
     * surface "over-rolls" under the finger, so even a swipe slower than the
     * ball still pushes it instead of braking.
     */
    fun tractionRatio(level: Int): Double = 1.0 + 0.25 * level

    /** Blended target speed for a finger sample. */
    fun fingerTarget(twistOmega: Double, dragOmega: Double, circularity: Double, gearLevel: Int, tractionLevel: Int): Double {
        val c = circularity.coerceIn(0.0, 1.0)
        return c * gearedTarget(twistOmega, gearLevel) + (1.0 - c) * dragOmega * tractionRatio(tractionLevel)
    }

    /** How quickly the ball closes the gap to the finger speed, in 1/s. */
    fun gripRate(level: Int): Double = 10.0 * (1.0 + 0.25 * level)

    /**
     * Largest angular acceleration (rad/s²) the finger can impart, in either
     * direction. Low levels make the ball feel heavy: it slips under a fast
     * finger and takes a moment to brake when held still.
     */
    fun gripAccel(level: Int): Double = 30.0 * (1.0 + 0.6 * level)

    /** Share of the speed lost to finger braking that comes back as points. */
    fun kersFraction(level: Int): Double = min(1.0, 0.1 * level)

    // --- Friction --------------------------------------------------------
    const val BASE_VISCOUS = 0.35      // 1/s
    const val BASE_CONSTANT = 1.2      // rad/s²
    /** Air drag coefficient, rad/s² per (rad/s)², before inertia. Nothing at 400 RPM, a wall past 4,000 without upgrades. */
    const val BASE_AERO = 4.0e-4

    fun inertia(flywheelLevel: Int): Double = 1.0 + 0.5 * flywheelLevel

    fun viscousFriction(bearingsLevel: Int, flywheelLevel: Int): Double =
        BASE_VISCOUS * 0.8.pow(bearingsLevel) / inertia(flywheelLevel)

    fun constantFriction(bearingsLevel: Int, flywheelLevel: Int): Double =
        BASE_CONSTANT * 0.85.pow(bearingsLevel) / inertia(flywheelLevel)

    fun aeroDrag(aeroLevel: Int): Double = BASE_AERO * 0.85.pow(aeroLevel)

    /** Drag as a percentage of the stock ball, for the shop. */
    fun dragPercent(bearingsLevel: Int): Double = 100.0 * 0.8.pow(bearingsLevel)
    fun aeroPercent(aeroLevel: Int): Double = 100.0 * 0.85.pow(aeroLevel)

    // --- Idle ------------------------------------------------------------
    fun motorRpm(level: Int): Double = if (level <= 0) 0.0 else 15.0 * level * 1.08.pow(level)

    /** Fraction of the gap to the motor floor closed per second. */
    const val MOTOR_RESPONSE = 1.5

    fun offlineEfficiency(gyroLevel: Int): Double = min(0.9, 0.25 + 0.1 * gyroLevel)
    fun offlineCapHours(gyroLevel: Int): Int = 2 + gyroLevel

    /** Share of a garage ball's motor income earned while it is not the active ball. */
    fun rackEfficiency(level: Int): Double = min(1.0, 0.35 + 0.1 * level)

    /** Interest on the point balance, per second. Level 1 = 0.1% per minute. */
    fun interestPerSecond(level: Int): Double = 0.001 * level / 60.0

    // --- Score -----------------------------------------------------------
    fun pointsPerRev(counterLevel: Int): Double = (1.0 + counterLevel) * 1.15.pow(counterLevel)

    const val PETAL_THRESHOLD_RPM = 300.0
    fun petalMultiplier(level: Int): Double = if (level <= 0) 1.0 else 1.0 + 0.5 * level

    const val RESONANCE_THRESHOLD_RPM = 120.0
    /** Combo points gained per second above the threshold. */
    const val COMBO_BUILD_RATE = 0.1
    /** Combo points lost per second below the threshold. */
    const val COMBO_DECAY_RATE = 0.5
    fun maxCombo(level: Int): Double = if (level <= 0) 0.0 else 0.5 * level
    fun comboDecayFactor(lockLevel: Int): Double = 0.8.pow(lockLevel)

    fun zenMultiplier(zen: Long): Double = 1.0 + 0.1 * zen

    // --- Speed limit -----------------------------------------------------
    fun rpmCap(coolingLevel: Int): Double = 500.0 + 500.0 * coolingLevel
    fun cryoFactor(level: Int): Double = 1.0 + 0.25 * level

    /** How fast speed above the cap bleeds off once the turbo is released, in 1/s. */
    const val OVER_CAP_DRAG = 3.0

    // --- Turbo (upgrade id "overdrive"): squeeze the ball ------------------
    /** Seconds of fuel at a full squeeze. Very short until upgraded. */
    fun turboCapacity(level: Int): Double = 1.2 + 0.5 * level
    /** Seconds after the last squeeze before fuel starts refilling. */
    fun turboCooldown(level: Int): Double = 6.0 * 0.85.pow(level)
    /** Seconds to refill from empty once the cooldown has passed. */
    fun turboRefill(level: Int): Double = 8.0 * 0.9.pow(level)
    /** The RPM cap is multiplied by up to this at a full squeeze. */
    fun turboCapMultiplier(level: Int): Double = 1.5 + 0.1 * level
    /** Thrust (rad/s²) at a full squeeze. */
    fun turboAccel(level: Int): Double = 80.0 + 25.0 * level
    /** Extra thrust (rad/s²) per unit of squeeze rate (1/s): a fast pinch kicks harder. */
    fun turboSqueezeGain(level: Int): Double = 15.0 + 3.0 * level
    fun nitroFactor(level: Int): Double = 1.0 + 0.3 * level
    /** Braking (rad/s²) at a full squeeze with empty fuel. */
    const val TURBO_OVERHEAT_BRAKE = 60.0
    fun heatSinkFactor(level: Int): Double = 0.85.pow(level)
    /** Income multiplier at a full squeeze. */
    const val TURBO_INCOME_MULT = 2.0

    // --- Golden sparks -----------------------------------------------------
    const val FIRST_SPARK_SECONDS = 90.0
    /** Seconds between sparks. */
    fun sparkInterval(luckyLevel: Int): Double = 300.0 * 0.9.pow(luckyLevel)
    /** Seconds a spark stays on screen. */
    const val SPARK_WINDOW = 12.0
    fun frenzyDuration(luckyLevel: Int): Double = 30.0 + 3.0 * luckyLevel
    const val FRENZY_MULT = 7.0
    /** A jackpot pays this many minutes of current income. */
    const val JACKPOT_MINUTES = 15.0
    const val JACKPOT_MIN_POINTS = 100.0
    const val RECHARGE_SECONDS = 20.0
    const val WILD_GRIP_SECONDS = 20.0
    const val WILD_GRIP_MULT = 3.0

    // --- Garage ----------------------------------------------------------
    const val MAX_BALLS = 8
    fun chestCost(ballsOwned: Int): Double = 25_000.0 * 3.0.pow((ballsOwned - 1).coerceAtLeast(0))
    fun chestWeight(rarity: Rarity, luckLevel: Int): Double =
        if (rarity == Rarity.COMMON) rarity.weight * 0.85.pow(luckLevel) else rarity.weight * (1.0 + 0.1 * luckLevel)
    fun sellValue(ball: BallState): Double = BallTypes.get(ball.typeId).rarity.sellValue + 0.5 * ball.invested
    /** Gems awarded when an upgrade reaches its maximum level. */
    const val GEMS_PER_MAX = 3L
    const val GEM_TOP_UP = 10L

    // --- Visual effect tiers ---------------------------------------------
    /** RPM at which each effect tier starts: sparks, flames, lightning, plasma, singularity, supernova, quantum. */
    val fxTiers: List<Double> = listOf(350.0, 900.0, 2000.0, 4000.0, 8000.0, 16000.0, 32000.0)

    /** 0 = none, 1 = sparks, 2 = flames, 3 = lightning, 4 = plasma, 5 = singularity, 6 = supernova, 7 = quantum. */
    fun fxTier(rpm: Double): Int = fxTiers.count { rpm >= it }

    // --- Prestige --------------------------------------------------------
    const val ZEN_UNIT_POINTS = 1_000_000.0

    /** Zen that a reset would grant for [pointsThisRun]. */
    fun zenFor(pointsThisRun: Double): Long =
        if (pointsThisRun < ZEN_UNIT_POINTS) 0 else floor(sqrt(pointsThisRun / ZEN_UNIT_POINTS)).toLong()
}
