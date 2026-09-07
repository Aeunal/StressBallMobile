package com.aeunal.stressball.core

/** Derived, read-only numbers the UI renders every frame. */
data class GameView(
    val state: GameState,
    /** Unsigned RPM. */
    val rpm: Double,
    /** +1 clockwise seen from above the cap, -1 counter-clockwise. */
    val direction: Int,
    /** Cap in effect right now (raised while the turbo is active). */
    val rpmCap: Double,
    /** Cap without the turbo. */
    val baseRpmCap: Double,
    val pointsPerSecond: Double,
    val pointsPerRev: Double,
    val totalMultiplier: Double,
    val petalsOpen: Boolean,
    val petalMultiplier: Double,
    val comboMultiplier: Double,
    val zenMultiplier: Double,
    /** Fuel, 0..1 */
    val turboCharge: Double,
    /** Seconds before fuel starts refilling. 0 while refilling or full. */
    val turboCooldown: Double,
    /** How hard the ball is being squeezed, 0..1. Drives the disc morph. */
    val turboWeight: Double,
    /** Squeezed with fuel left: thrust, raised cap, extra income. */
    val turboBoosting: Boolean,
    /** Squeezed with empty fuel: braking. */
    val turboOverheating: Boolean,
    val turboCapMultiplier: Double,
    val motorRpm: Double,
    val zenOnReset: Long,
    /** 0 none, 1 sparks, 2 flames, 3 lightning, 4 plasma. See [Stats.fxTiers]. */
    val fxTier: Int,
    val fingerTouching: Boolean,
    val bodyColor: Long,
    val capColor: Long,
) {
    val points: Double get() = state.points

    /** Speed as a fraction of the base cap, up to 1.5 while boosting. */
    val heat: Double get() = (rpm / baseRpmCap).coerceIn(0.0, 1.5)

    /** Progress into the current effect tier, 0..1, for smooth blending. */
    val fxTierProgress: Double
        get() {
            val tiers = Stats.fxTiers
            if (fxTier <= 0) return 0.0
            val start = tiers[fxTier - 1]
            val end = if (fxTier < tiers.size) tiers[fxTier] else start * 2.0
            return ((rpm - start) / (end - start)).coerceIn(0.0, 1.0)
        }
}
