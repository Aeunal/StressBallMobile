package com.aeunal.stressball.core

/** Derived, read-only numbers the UI renders every frame. */
data class GameView(
    val state: GameState,
    val rpm: Double,
    val rpmCap: Double,
    val pointsPerSecond: Double,
    val pointsPerRev: Double,
    val totalMultiplier: Double,
    val petalsOpen: Boolean,
    val petalMultiplier: Double,
    val comboMultiplier: Double,
    val zenMultiplier: Double,
    val overdriveActive: Boolean,
    val overdriveUnlocked: Boolean,
    val overdriveReady: Boolean,
    val overdriveCooldown: Double,
    val motorRpm: Double,
    val zenOnReset: Long,
) {
    val points: Double get() = state.points
    val heat: Double get() = (rpm / rpmCap).coerceIn(0.0, 1.0)
}
