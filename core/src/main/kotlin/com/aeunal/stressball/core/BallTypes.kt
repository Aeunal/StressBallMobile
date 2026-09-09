package com.aeunal.stressball.core

import kotlin.random.Random

/** Multipliers a ball type applies on top of its upgrades. 1.0 = stock. */
data class BallTraits(
    /** Point income. */
    val income: Double = 1.0,
    /** Bearing friction (lower is better). */
    val drag: Double = 1.0,
    /** Grip acceleration. */
    val grip: Double = 1.0,
    /** Turbo fuel capacity. */
    val turbo: Double = 1.0,
    /** RPM cap. */
    val cap: Double = 1.0,
)

/** A named ball pulled from a chest. Colours are 0xAARRGGBB. Names live in [GameText]. */
data class BallTypeDef(
    val id: String,
    val rarity: Rarity,
    val body: Long,
    val cap: Long,
    /** Tint for this ball's effects. */
    val aura: Long,
    val traits: BallTraits = BallTraits(),
)

/** The ball catalogue. Ids are part of the save format: never rename them. */
object BallTypes {
    const val DEFAULT = "rubber_red"

    val all: List<BallTypeDef> = listOf(
        // Common
        BallTypeDef("rubber_red", Rarity.COMMON, 0xFFE0243A, 0xFF1F5F4A, 0xFFFFB347),
        BallTypeDef("ocean", Rarity.COMMON, 0xFF1E6FD9, 0xFF10304F, 0xFF7CB8FF),
        BallTypeDef("mint", Rarity.COMMON, 0xFF2ECC9A, 0xFF12503B, 0xFFB8FFE0),
        BallTypeDef("amber", Rarity.COMMON, 0xFFE0A81A, 0xFF5A3D0C, 0xFFFFD36A, BallTraits(income = 1.05)),
        // Rare
        BallTypeDef("carbon", Rarity.RARE, 0xFF3A3F47, 0xFF15161A, 0xFFB8BEC8, BallTraits(drag = 0.85)),
        BallTypeDef("chrome", Rarity.RARE, 0xFFB8BEC8, 0xFF5C6470, 0xFFFFFFFF, BallTraits(grip = 1.2)),
        BallTypeDef("neon", Rarity.RARE, 0xFFFF2BD6, 0xFF3A0A33, 0xFFFF7CE8, BallTraits(income = 1.15)),
        BallTypeDef("marble", Rarity.RARE, 0xFFE8E4DC, 0xFF6B6B7A, 0xFFDDE6FF, BallTraits(cap = 1.1)),
        // Very rare
        BallTypeDef("obsidian", Rarity.VERY_RARE, 0xFF14101F, 0xFF3F2A6B, 0xFF9B5CFF, BallTraits(drag = 0.7, income = 1.2)),
        BallTypeDef("aurora", Rarity.VERY_RARE, 0xFF2AD4C4, 0xFF2A3AD4, 0xFF9CFFF0, BallTraits(income = 1.3)),
        BallTypeDef("jade", Rarity.VERY_RARE, 0xFF1F8F5A, 0xFF0A3A22, 0xFF7CFFB8, BallTraits(grip = 1.3, turbo = 1.2)),
        // Legendary
        BallTypeDef("solar", Rarity.LEGENDARY, 0xFFFF8A1A, 0xFF7A2E00, 0xFFFFE066, BallTraits(income = 1.5, cap = 1.2)),
        BallTypeDef("glacier", Rarity.LEGENDARY, 0xFFBFEFFF, 0xFF3A7FA8, 0xFFFFFFFF, BallTraits(drag = 0.55)),
        BallTypeDef("dragon", Rarity.LEGENDARY, 0xFF8E1424, 0xFF2A0A0A, 0xFFFF4D1F, BallTraits(turbo = 1.6, income = 1.3)),
        // Mythic
        BallTypeDef("nebula", Rarity.MYTHIC, 0xFF4B1FA8, 0xFFFF3DDB, 0xFF3DE0FF, BallTraits(income = 2.0, cap = 1.3)),
        BallTypeDef("phoenix", Rarity.MYTHIC, 0xFFFF5A1A, 0xFFFFD36A, 0xFFFFF3C0, BallTraits(turbo = 2.0, drag = 0.7, income = 1.5)),
        // Exotic
        BallTypeDef("singularity", Rarity.EXOTIC, 0xFF05040A, 0xFFFFFFFF, 0xFFB8F1FF, BallTraits(income = 3.0, cap = 1.5, drag = 0.5)),
        BallTypeDef("chronos", Rarity.EXOTIC, 0xFFC9A227, 0xFF2E2A1F, 0xFFFFF7C0, BallTraits(income = 2.5, grip = 2.0, turbo = 2.0)),
    )

    val byId: Map<String, BallTypeDef> = all.associateBy { it.id }

    fun get(id: String): BallTypeDef = byId[id] ?: byId.getValue(DEFAULT)

    fun ofRarity(rarity: Rarity): List<BallTypeDef> = all.filter { it.rarity == rarity }

    /** Chest odds per rarity for a given Lucky Chest level, normalised to sum to 1. */
    fun odds(luckLevel: Int): Map<Rarity, Double> {
        val raw = Rarity.entries.associateWith { Stats.chestWeight(it, luckLevel) }
        val total = raw.values.sum()
        return raw.mapValues { it.value / total }
    }

    /** Pulls a ball from a chest. */
    fun roll(random: Random, luckLevel: Int): BallTypeDef {
        val odds = odds(luckLevel)
        var r = random.nextDouble()
        var rarity = Rarity.COMMON
        for (entry in Rarity.entries) {
            val p = odds.getValue(entry)
            if (r < p) { rarity = entry; break }
            r -= p
            rarity = entry
        }
        val pool = ofRarity(rarity)
        return pool[random.nextInt(pool.size)]
    }
}
