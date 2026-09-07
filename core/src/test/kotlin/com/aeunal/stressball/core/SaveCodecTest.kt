package com.aeunal.stressball.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SaveCodecTest {
    @Test
    fun `round trips a state`() {
        val state = GameState(
            points = 1234.5, pointsThisRun = 2000.0, totalPointsEarned = 99999.0, omega = -12.3,
            upgrades = mapOf(Upgrades.GEAR to 3, Upgrades.MOTOR to 1), zen = 4, prestigeCount = 1,
            bestRpm = 812.0, totalRevolutions = 1e6, combo = 0.4, turboCharge = 0.35, turboCooldown = 2.5,
            playTimeSeconds = 500.0, lastSavedEpochMs = 42L,
            achievements = setOf("first_spin"),
            ownedCosmetics = setOf("body_ocean"), equipped = mapOf("BODY" to "body_ocean"),
        )
        assertEquals(state, SaveCodec.decode(SaveCodec.encode(state)))
    }

    @Test
    fun `tolerates missing and unknown keys, including fields from version 1 saves`() {
        val decoded = SaveCodec.decode("""{"points": 10, "overdriveCooldown": 12.0, "overdriveRemaining": 1.0, "version": 1}""")
        assertEquals(GameState(points = 10.0), decoded)
    }

    @Test
    fun `rejects garbage and sanitises bad values`() {
        assertNull(SaveCodec.decode("not json"))
        assertNull(SaveCodec.decode(""))
        assertNull(SaveCodec.decode(null))
        val fixed = SaveCodec.decode(
            """{"points": -5, "omega": 1e999, "turboCharge": 7, "turboCooldown": -4, "upgrades": {"gear": 999, "bogus": 3},
               "achievements": ["nope", "first_spin"],
               "ownedCosmetics": ["body_red", "cap_onyx", "fake"],
               "equipped": {"BODY": "body_ocean", "CAP": "cap_onyx", "HAT": "cap_onyx"}}""",
        )!!
        assertEquals(0.0, fixed.points)
        assertEquals(0.0, fixed.omega)
        assertEquals(1.0, fixed.turboCharge)
        assertEquals(0.0, fixed.turboCooldown)
        assertEquals(mapOf(Upgrades.GEAR to Upgrades.get(Upgrades.GEAR).maxLevel), fixed.upgrades)
        assertEquals(setOf("first_spin"), fixed.achievements)
        assertEquals(setOf("cap_onyx"), fixed.ownedCosmetics, "defaults and unknown ids are dropped")
        assertEquals(mapOf("CAP" to "cap_onyx"), fixed.equipped, "unowned body and unknown slot are dropped")
        assertEquals(GameState.CURRENT_VERSION, fixed.version)
    }

    @Test
    fun `negative omega survives`() {
        assertEquals(-3.0, SaveCodec.decode("""{"omega": -3.0}""")!!.omega)
    }
}
