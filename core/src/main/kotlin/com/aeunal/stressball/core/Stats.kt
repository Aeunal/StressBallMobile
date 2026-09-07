package com.aeunal.stressball.core

import kotlin.math.min
import kotlin.math.pow

/**
 * Pure formulas mapping upgrade levels to gameplay numbers.
 *
 * Keeping them here (rather than inside the engine) lets the shop show exact
 * "next level" effects and makes balancing a matter of editing one file.
 */
object Stats {
    // --- Manual spinning -------------------------------------------------
    /** Multiplier applied to the finger's angular velocity. */
    fun gearRatio(level: Int): Double = 1.0 + 0.5 * level

    /** How quickly the ball matches the (geared) finger speed, in 1/s. */
    fun coupling(level: Int): Double = 4.0 * (1.0 + 0.5 * level)

    // --- Friction --------------------------------------------------------
    const val BASE_VISCOUS = 0.12      // 1/s
    const val BASE_CONSTANT = 0.5      // rad/s^2

    fun inertia(flywheelLevel: Int): Double = 1.0 + 0.35 * flywheelLevel

    fun viscousFriction(bearingsLevel: Int, flywheelLevel: Int): Double =
        BASE_VISCOUS * 0.88.pow(bearingsLevel) / inertia(flywheelLevel)

    fun constantFriction(bearingsLevel: Int, flywheelLevel: Int): Double =
        BASE_CONSTANT * 0.92.pow(bearingsLevel) / inertia(flywheelLevel)

    // --- Idle ------------------------------------------------------------
    fun motorRpm(level: Int): Double = if (level <= 0) 0.0 else 20.0 * level * 1.08.pow(level)

    /** Fraction of the gap to the motor floor closed per second. */
    const val MOTOR_RESPONSE = 1.5

    fun offlineEfficiency(gyroLevel: Int): Double = min(0.9, 0.25 + 0.1 * gyroLevel)
    fun offlineCapHours(gyroLevel: Int): Int = 2 + gyroLevel

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

    fun zenMultiplier(zen: Long): Double = 1.0 + 0.1 * zen

    // --- Special ---------------------------------------------------------
    fun rpmCap(coolingLevel: Int): Double = 600.0 + 400.0 * coolingLevel

    fun overdriveRpm(level: Int): Double = if (level <= 0) 0.0 else 150.0 + 100.0 * level
    fun overdriveCooldown(level: Int): Double = if (level <= 0) Double.POSITIVE_INFINITY else 60.0 * 0.9.pow(level - 1)
    const val OVERDRIVE_DURATION = 8.0
    const val OVERDRIVE_INCOME_MULT = 2.0

    // --- Prestige --------------------------------------------------------
    const val ZEN_UNIT_POINTS = 1_000_000.0

    /** Zen that a reset would grant for [pointsThisRun]. */
    fun zenFor(pointsThisRun: Double): Long =
        if (pointsThisRun < ZEN_UNIT_POINTS) 0 else kotlin.math.floor(kotlin.math.sqrt(pointsThisRun / ZEN_UNIT_POINTS)).toLong()
}
