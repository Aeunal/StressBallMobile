package com.aeunal.stressball.core

/** Builders for single-ball profiles so physics tests stay short. */
object TestStates {
    /** A profile with one ball ("b1") in the given condition. Twist tests want [topView] = true. */
    fun single(
        omega: Double = 0.0,
        upgrades: Map<String, Int> = emptyMap(),
        points: Double = 0.0,
        zen: Long = 0,
        gems: Long = 0,
        typeId: String = BallTypes.DEFAULT,
        topView: Boolean = false,
        turboCharge: Double = 1.0,
        pointsThisRun: Double = 0.0,
        totalPointsEarned: Double = 0.0,
        bestRpm: Double = 0.0,
        lastSavedEpochMs: Long = 0L,
        accountUpgrades: Map<String, Int> = emptyMap(),
    ): GameState = GameState(
        points = points,
        zen = zen,
        gems = gems,
        totalPointsEarned = totalPointsEarned,
        balls = listOf(
            BallState(
                id = "b1", typeId = typeId, omega = omega, upgrades = upgrades, topView = topView,
                turboCharge = turboCharge, pointsThisRun = pointsThisRun, bestRpm = bestRpm,
            ),
        ),
        activeBallId = "b1",
        accountUpgrades = accountUpgrades,
        lastSavedEpochMs = lastSavedEpochMs,
    )

    /** A ball that barely loses speed on its own, so a test can isolate one force. */
    val oiled: Map<String, Int> = mapOf(Upgrades.BEARINGS to 30, Upgrades.FLYWHEEL to 20)
}
