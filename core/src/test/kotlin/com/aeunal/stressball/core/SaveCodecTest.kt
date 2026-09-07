package com.aeunal.stressball.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SaveCodecTest {
    @Test
    fun `round trips a state`() {
        val state = GameState(
            points = 1234.5, pointsThisRun = 2000.0, totalPointsEarned = 99999.0, omega = 12.3,
            upgrades = mapOf(Upgrades.GEAR to 3, Upgrades.MOTOR to 1), zen = 4, prestigeCount = 1,
            bestRpm = 812.0, totalRevolutions = 1e6, combo = 0.4, overdriveCooldown = 12.0,
            overdriveRemaining = 1.0, playTimeSeconds = 500.0, lastSavedEpochMs = 42L,
            achievements = setOf("first_spin"),
        )
        assertEquals(state, SaveCodec.decode(SaveCodec.encode(state)))
    }

    @Test
    fun `tolerates missing and unknown keys`() {
        val decoded = SaveCodec.decode("""{"points": 10, "someFutureField": true}""")
        assertEquals(GameState(points = 10.0), decoded)
    }

    @Test
    fun `rejects garbage and sanitises bad values`() {
        assertNull(SaveCodec.decode("not json"))
        assertNull(SaveCodec.decode(""))
        assertNull(SaveCodec.decode(null))
        val fixed = SaveCodec.decode("""{"points": -5, "omega": -1, "upgrades": {"gear": 999, "bogus": 3}, "achievements": ["nope", "first_spin"]}""")!!
        assertEquals(0.0, fixed.points)
        assertEquals(0.0, fixed.omega)
        assertEquals(mapOf(Upgrades.GEAR to Upgrades.get(Upgrades.GEAR).maxLevel), fixed.upgrades)
        assertEquals(setOf("first_spin"), fixed.achievements)
    }
}
