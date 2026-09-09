package com.aeunal.stressball.core

/** A one-off challenge. Unlocking one grants a permanent Zen. Text lives in [GameText]. */
data class AchievementDef(
    val id: String,
    val isMet: (GameState) -> Boolean,
)

object Achievements {
    private fun GameState.bestRpm() = balls.maxOf { it.bestRpm }
    private fun GameState.revolutions() = balls.sumOf { it.totalRevolutions }
    private fun GameState.anyBall(p: (BallState) -> Boolean) = balls.any(p)

    val all: List<AchievementDef> = listOf(
        AchievementDef("first_spin") { it.bestRpm() >= 60 },
        AchievementDef("hummingbird") { it.bestRpm() >= 300 },
        AchievementDef("on_fire") { it.bestRpm() >= 900 },
        AchievementDef("turbine") { it.bestRpm() >= 1000 },
        AchievementDef("ludicrous") { it.bestRpm() >= 3000 },
        AchievementDef("plasma") { it.bestRpm() >= 4000 },
        AchievementDef("singularity") { it.bestRpm() >= 8000 },
        AchievementDef("supernova") { it.bestRpm() >= 16000 },
        AchievementDef("quantum") { it.bestRpm() >= 32000 },
        AchievementDef("thousand_turns") { it.revolutions() >= 1_000 },
        AchievementDef("million_turns") { it.revolutions() >= 1_000_000 },
        AchievementDef("pocket_money") { it.totalPointsEarned >= 10_000 },
        AchievementDef("millionaire") { it.totalPointsEarned >= 1_000_000 },
        AchievementDef("billionaire") { it.totalPointsEarned >= 1_000_000_000 },
        AchievementDef("in_bloom") { it.anyBall { b -> b.level(Upgrades.PETALS) >= 1 } },
        AchievementDef("hands_free") { it.anyBall { b -> b.level(Upgrades.MOTOR) >= 1 } },
        AchievementDef("top_down") { it.anyBall { b -> b.level(Upgrades.GIMBAL) >= 1 } },
        AchievementDef("maxed") { it.balls.any { b -> b.upgrades.any { (id, l) -> l >= Upgrades.get(id).maxLevel } } },
        AchievementDef("stylist") { it.ownedSkins.size >= 3 },
        AchievementDef("collector") { it.balls.size >= 3 },
        AchievementDef("garage_full") { it.balls.size >= Stats.MAX_BALLS },
        AchievementDef("legendary") { it.anyBall { b -> BallTypes.get(b.typeId).rarity >= Rarity.LEGENDARY } },
        AchievementDef("exotic") { it.anyBall { b -> BallTypes.get(b.typeId).rarity == Rarity.EXOTIC } },
        AchievementDef("trader") { it.ballsSold >= 1 },
        AchievementDef("lucky") { it.sparksTapped >= 1 },
        AchievementDef("gem_hoarder") { it.gems >= 50 },
        AchievementDef("zen_master") { it.balls.sumOf { b -> b.prestigeCount } >= 1 },
        AchievementDef("marathon") { it.playTimeSeconds >= 3600 },
    )

    val byId: Map<String, AchievementDef> = all.associateBy { it.id }

    /** Returns the ids of achievements met by [state] that are not yet unlocked. */
    fun newlyMet(state: GameState): List<String> =
        all.filter { it.id !in state.achievements && it.isMet(state) }.map { it.id }
}
