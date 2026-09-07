package com.aeunal.stressball.core

import kotlinx.serialization.json.Json

/** JSON (de)serialisation of [GameState]. Tolerant of unknown/missing keys. */
object SaveCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun encode(state: GameState): String = json.encodeToString(GameState.serializer(), state)

    /** Returns null if [text] is blank or cannot be parsed. */
    fun decode(text: String?): GameState? {
        if (text.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(GameState.serializer(), text) }
            .getOrNull()
            ?.sanitised()
    }

    /** Clamp any values a hand-edited or corrupted save could have put out of range. */
    private fun GameState.sanitised(): GameState = copy(
        points = points.coerceAtLeast(0.0).finiteOr(0.0),
        pointsThisRun = pointsThisRun.coerceAtLeast(0.0).finiteOr(0.0),
        totalPointsEarned = totalPointsEarned.coerceAtLeast(0.0).finiteOr(0.0),
        omega = omega.coerceAtLeast(0.0).finiteOr(0.0),
        upgrades = upgrades.filterKeys { it in Upgrades.byId }
            .mapValues { (id, lvl) -> lvl.coerceIn(0, Upgrades.get(id).maxLevel) },
        zen = zen.coerceAtLeast(0),
        combo = combo.coerceAtLeast(0.0).finiteOr(0.0),
        overdriveCooldown = overdriveCooldown.coerceAtLeast(0.0).finiteOr(0.0),
        overdriveRemaining = overdriveRemaining.coerceAtLeast(0.0).finiteOr(0.0),
        achievements = achievements.filter { it in Achievements.byId }.toSet(),
    )

    private fun Double.finiteOr(fallback: Double) = if (isFinite()) this else fallback
}
