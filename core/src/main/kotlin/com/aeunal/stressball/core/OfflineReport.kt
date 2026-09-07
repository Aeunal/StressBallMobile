package com.aeunal.stressball.core

/** What happened while the app was closed; shown to the player on resume. */
data class OfflineReport(
    val secondsAway: Double,
    val secondsCredited: Double,
    val pointsEarned: Double,
    val efficiency: Double,
)
