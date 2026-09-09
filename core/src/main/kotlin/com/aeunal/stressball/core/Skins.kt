package com.aeunal.stressball.core

/** Where a skin goes. One of each slot can be active at a time. */
enum class SkinSlot { OUTER, INTERIOR }

/** How the app draws a skin. Outer styles are patterns on the shell; interior styles glow from inside. */
enum class SkinStyle { STRIPES, SPOTS, HEX, RINGS, STARS, CRACKS, EMBER, CRYSTAL, VOID, STORM, PRISM, CLOCKWORK }

/** Multipliers a skin applies. 1.0 = none. */
data class SkinBuff(
    val income: Double = 1.0,
    val grip: Double = 1.0,
    val cap: Double = 1.0,
    val turboFuel: Double = 1.0,
    val comboMax: Double = 1.0,
    val motor: Double = 1.0,
) {
    operator fun times(o: SkinBuff) = SkinBuff(
        income * o.income, grip * o.grip, cap * o.cap, turboFuel * o.turboFuel, comboMax * o.comboMax, motor * o.motor,
    )

    companion object {
        val NONE = SkinBuff()
    }
}

/** A skin bought with gems. Names and descriptions live in [GameText]. */
data class SkinDef(
    val id: String,
    val slot: SkinSlot,
    val style: SkinStyle,
    val gems: Long,
    /** Accent colour, 0xAARRGGBB. */
    val color: Long,
    val buff: SkinBuff,
)

/** The skin catalogue. Ids are part of the save format: never rename them. */
object Skins {
    val all: List<SkinDef> = listOf(
        SkinDef("stripes", SkinSlot.OUTER, SkinStyle.STRIPES, 6, 0xFFFFFFFF, SkinBuff(income = 1.10)),
        SkinDef("spots", SkinSlot.OUTER, SkinStyle.SPOTS, 8, 0xFF15161A, SkinBuff(grip = 1.15)),
        SkinDef("hex", SkinSlot.OUTER, SkinStyle.HEX, 12, 0xFF7CB8FF, SkinBuff(cap = 1.20)),
        SkinDef("rings", SkinSlot.OUTER, SkinStyle.RINGS, 15, 0xFFFFD36A, SkinBuff(income = 1.20)),
        SkinDef("stars", SkinSlot.OUTER, SkinStyle.STARS, 25, 0xFFFFF7C0, SkinBuff(income = 1.15, turboFuel = 1.10)),
        SkinDef("cracks", SkinSlot.OUTER, SkinStyle.CRACKS, 40, 0xFFFF6A1A, SkinBuff(income = 1.30)),

        SkinDef("ember", SkinSlot.INTERIOR, SkinStyle.EMBER, 8, 0xFFFF7A2A, SkinBuff(income = 1.10)),
        SkinDef("crystal", SkinSlot.INTERIOR, SkinStyle.CRYSTAL, 12, 0xFFB8F1FF, SkinBuff(comboMax = 1.50)),
        SkinDef("void", SkinSlot.INTERIOR, SkinStyle.VOID, 20, 0xFF9B5CFF, SkinBuff(cap = 1.15)),
        SkinDef("storm", SkinSlot.INTERIOR, SkinStyle.STORM, 25, 0xFF63B3FF, SkinBuff(income = 1.15)),
        SkinDef("prism", SkinSlot.INTERIOR, SkinStyle.PRISM, 35, 0xFFFF3DDB, SkinBuff(income = 1.25)),
        SkinDef("clockwork", SkinSlot.INTERIOR, SkinStyle.CLOCKWORK, 50, 0xFFC9A227, SkinBuff(motor = 1.30)),
    )

    val byId: Map<String, SkinDef> = all.associateBy { it.id }

    fun get(id: String): SkinDef = byId[id] ?: error("Unknown skin id: $id")

    fun forSlot(slot: SkinSlot): List<SkinDef> = all.filter { it.slot == slot }

    fun isOwned(state: GameState, id: String): Boolean = id in state.ownedSkins && id in byId

    fun equipped(state: GameState, slot: SkinSlot): SkinDef? {
        val id = (if (slot == SkinSlot.OUTER) state.outerSkin else state.interiorSkin) ?: return null
        val def = byId[id] ?: return null
        return if (def.slot == slot && isOwned(state, id)) def else null
    }

    /** Combined multipliers of the equipped skins. */
    fun buff(state: GameState): SkinBuff {
        val outer = equipped(state, SkinSlot.OUTER)?.buff ?: SkinBuff.NONE
        val interior = equipped(state, SkinSlot.INTERIOR)?.buff ?: SkinBuff.NONE
        return outer * interior
    }
}
