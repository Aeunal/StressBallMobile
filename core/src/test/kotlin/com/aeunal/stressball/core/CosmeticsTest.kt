package com.aeunal.stressball.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CosmeticsTest {
    @Test
    fun `ids are unique and each slot has exactly one free default`() {
        assertEquals(Cosmetics.all.size, Cosmetics.all.map { it.id }.toSet().size)
        for (slot in CosmeticSlot.entries) {
            assertEquals(1, Cosmetics.forSlot(slot).count { it.isDefault }, "slot $slot")
            assertTrue(Cosmetics.forSlot(slot).size >= 4)
        }
        for (def in Cosmetics.all) assertTrue(def.id.startsWith(def.slot.name.lowercase()), def.id)
    }

    @Test
    fun `equipped falls back to the default when the saved id is unknown, unowned or in the wrong slot`() {
        val fresh = GameState()
        assertEquals("body_red", Cosmetics.equipped(fresh, CosmeticSlot.BODY).id)
        assertEquals("cap_green", Cosmetics.equipped(fresh, CosmeticSlot.CAP).id)

        val unowned = GameState(equipped = mapOf("BODY" to "body_gold"))
        assertEquals("body_red", Cosmetics.equipped(unowned, CosmeticSlot.BODY).id)

        val wrongSlot = GameState(ownedCosmetics = setOf("cap_navy"), equipped = mapOf("BODY" to "cap_navy"))
        assertEquals("body_red", Cosmetics.equipped(wrongSlot, CosmeticSlot.BODY).id)

        val owned = GameState(ownedCosmetics = setOf("body_gold"), equipped = mapOf("BODY" to "body_gold"))
        assertEquals("body_gold", Cosmetics.equipped(owned, CosmeticSlot.BODY).id)
        assertTrue(Cosmetics.isOwned(owned, "body_gold"))
        assertTrue(Cosmetics.isOwned(owned, "body_red"))
        assertFalse(Cosmetics.isOwned(owned, "body_void"))
        assertFalse(Cosmetics.isOwned(owned, "nothing"))
    }
}
