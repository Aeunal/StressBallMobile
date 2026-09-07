package com.aeunal.stressball.core

/** A customisable part of the ball. Names are persisted in [GameState.equipped]. */
enum class CosmeticSlot { BODY, CAP }

/**
 * A purchasable look. [color] is 0xAARRGGBB. Items with a cost of zero are the
 * defaults and are always owned. Player-facing names live in [GameText].
 */
data class CosmeticDef(
    val id: String,
    val slot: CosmeticSlot,
    val cost: Double,
    val color: Long,
) {
    val isDefault: Boolean get() = cost <= 0.0
}

/** The cosmetics catalogue. Ids are part of the save format: never rename them. */
object Cosmetics {
    val all: List<CosmeticDef> = listOf(
        CosmeticDef("body_red", CosmeticSlot.BODY, 0.0, 0xFFE0243A),
        CosmeticDef("body_ocean", CosmeticSlot.BODY, 2_000.0, 0xFF1E6FD9),
        CosmeticDef("body_mint", CosmeticSlot.BODY, 5_000.0, 0xFF2ECC9A),
        CosmeticDef("body_gold", CosmeticSlot.BODY, 25_000.0, 0xFFE0A81A),
        CosmeticDef("body_violet", CosmeticSlot.BODY, 100_000.0, 0xFF7B3FE4),
        CosmeticDef("body_carbon", CosmeticSlot.BODY, 500_000.0, 0xFF3A3F47),
        CosmeticDef("body_pearl", CosmeticSlot.BODY, 2_000_000.0, 0xFFE8E4DC),
        CosmeticDef("body_void", CosmeticSlot.BODY, 10_000_000.0, 0xFF14101F),

        CosmeticDef("cap_green", CosmeticSlot.CAP, 0.0, 0xFF1F5F4A),
        CosmeticDef("cap_navy", CosmeticSlot.CAP, 2_000.0, 0xFF1B2F6B),
        CosmeticDef("cap_orange", CosmeticSlot.CAP, 8_000.0, 0xFFD9601E),
        CosmeticDef("cap_chrome", CosmeticSlot.CAP, 50_000.0, 0xFFB8BEC8),
        CosmeticDef("cap_rose", CosmeticSlot.CAP, 200_000.0, 0xFFC9457A),
        CosmeticDef("cap_onyx", CosmeticSlot.CAP, 1_000_000.0, 0xFF15161A),
    )

    val byId: Map<String, CosmeticDef> = all.associateBy { it.id }

    fun get(id: String): CosmeticDef = byId[id] ?: error("Unknown cosmetic id: $id")

    fun forSlot(slot: CosmeticSlot): List<CosmeticDef> = all.filter { it.slot == slot }

    fun defaultFor(slot: CosmeticSlot): CosmeticDef = forSlot(slot).first { it.isDefault }

    fun isOwned(state: GameState, id: String): Boolean {
        val def = byId[id] ?: return false
        return def.isDefault || id in state.ownedCosmetics
    }

    /** The look currently applied to [slot]; falls back to the default if the saved id is unknown or unowned. */
    fun equipped(state: GameState, slot: CosmeticSlot): CosmeticDef {
        val id = state.equipped[slot.name] ?: return defaultFor(slot)
        val def = byId[id] ?: return defaultFor(slot)
        return if (def.slot == slot && isOwned(state, id)) def else defaultFor(slot)
    }
}
