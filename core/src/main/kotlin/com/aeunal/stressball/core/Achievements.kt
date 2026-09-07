package com.aeunal.stressball.core

/** A one-off challenge. Unlocking one grants a permanent Zen. */
data class AchievementDef(
    val id: String,
    val title: String,
    val description: String,
    val isMet: (GameState) -> Boolean,
)

object Achievements {
    val all: List<AchievementDef> = listOf(
        AchievementDef("first_spin", "First Spin", "Reach 60 RPM.") { it.bestRpm >= 60 },
        AchievementDef("hummingbird", "Hummingbird", "Reach 300 RPM.") { it.bestRpm >= 300 },
        AchievementDef("turbine", "Turbine", "Reach 1,000 RPM.") { it.bestRpm >= 1000 },
        AchievementDef("ludicrous", "Ludicrous Speed", "Reach 3,000 RPM.") { it.bestRpm >= 3000 },
        AchievementDef("thousand_turns", "A Thousand Turns", "Spin 1,000 revolutions in total.") { it.totalRevolutions >= 1_000 },
        AchievementDef("million_turns", "Million Turns", "Spin 1,000,000 revolutions in total.") { it.totalRevolutions >= 1_000_000 },
        AchievementDef("pocket_money", "Pocket Money", "Earn 10K points.") { it.totalPointsEarned >= 10_000 },
        AchievementDef("millionaire", "Millionaire", "Earn 1M points.") { it.totalPointsEarned >= 1_000_000 },
        AchievementDef("billionaire", "Billionaire", "Earn 1B points.") { it.totalPointsEarned >= 1_000_000_000 },
        AchievementDef("in_bloom", "In Bloom", "Own a Petal Shell.") { it.level(Upgrades.PETALS) >= 1 },
        AchievementDef("hands_free", "Hands Free", "Own a Micro Motor.") { it.level(Upgrades.MOTOR) >= 1 },
        AchievementDef("zen_master", "Zen Master", "Perform a Zen reset.") { it.prestigeCount >= 1 },
        AchievementDef("marathon", "Marathon", "Play for a total of one hour.") { it.playTimeSeconds >= 3600 },
    )

    val byId: Map<String, AchievementDef> = all.associateBy { it.id }

    /** Returns the ids of achievements met by [state] that are not yet unlocked. */
    fun newlyMet(state: GameState): List<String> =
        all.filter { it.id !in state.achievements && it.isMet(state) }.map { it.id }
}
