package com.aeunal.stressball.core

/** A one-off challenge. Unlocking one grants a permanent Zen. Text lives in [GameText]. */
data class AchievementDef(
    val id: String,
    val isMet: (GameState) -> Boolean,
)

object Achievements {
    val all: List<AchievementDef> = listOf(
        AchievementDef("first_spin") { it.bestRpm >= 60 },
        AchievementDef("hummingbird") { it.bestRpm >= 300 },
        AchievementDef("on_fire") { it.bestRpm >= 900 },
        AchievementDef("turbine") { it.bestRpm >= 1000 },
        AchievementDef("ludicrous") { it.bestRpm >= 3000 },
        AchievementDef("plasma") { it.bestRpm >= 4000 },
        AchievementDef("thousand_turns") { it.totalRevolutions >= 1_000 },
        AchievementDef("million_turns") { it.totalRevolutions >= 1_000_000 },
        AchievementDef("pocket_money") { it.totalPointsEarned >= 10_000 },
        AchievementDef("millionaire") { it.totalPointsEarned >= 1_000_000 },
        AchievementDef("billionaire") { it.totalPointsEarned >= 1_000_000_000 },
        AchievementDef("in_bloom") { it.level(Upgrades.PETALS) >= 1 },
        AchievementDef("hands_free") { it.level(Upgrades.MOTOR) >= 1 },
        AchievementDef("stylist") { it.ownedCosmetics.size >= 3 },
        AchievementDef("zen_master") { it.prestigeCount >= 1 },
        AchievementDef("marathon") { it.playTimeSeconds >= 3600 },
    )

    val byId: Map<String, AchievementDef> = all.associateBy { it.id }

    /** Returns the ids of achievements met by [state] that are not yet unlocked. */
    fun newlyMet(state: GameState): List<String> =
        all.filter { it.id !in state.achievements && it.isMet(state) }.map { it.id }
}
