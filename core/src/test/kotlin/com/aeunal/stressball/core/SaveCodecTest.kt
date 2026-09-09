package com.aeunal.stressball.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SaveCodecTest {
    @Test
    fun `round trips a profile`() {
        val state = GameState(
            points = 1234.5, gems = 12, zen = 4, totalPointsEarned = 99999.0,
            balls = listOf(
                BallState("b1", "rubber_red", omega = -12.3, upgrades = mapOf(Upgrades.GEAR to 3), combo = 0.4, turboCharge = 0.35, turboCooldown = 2.5, pointsThisRun = 2000.0, totalRevolutions = 1e6, bestRpm = 812.0, prestigeCount = 1, invested = 500.0, topView = false),
                BallState("b3", "solar", upgrades = mapOf(Upgrades.MOTOR to 5, Upgrades.GIMBAL to 1), topView = true),
            ),
            activeBallId = "b3", nextBallNumber = 4,
            accountUpgrades = mapOf(Upgrades.RACK to 2),
            ownedSkins = setOf("stripes", "ember"), outerSkin = "stripes", interiorSkin = "ember",
            achievements = setOf("first_spin"), playTimeSeconds = 500.0, lastSavedEpochMs = 42L,
            chestsOpened = 2, ballsSold = 1, sparksTapped = 3, sparkTimer = 12.0, sparkRemaining = 0.0,
            buff = BuffKind.FRENZY, buffRemaining = 9.0,
        )
        assertEquals(state, SaveCodec.decode(SaveCodec.encode(state)))
    }

    @Test
    fun `migrates a version 3 save into a one-ball garage`() {
        val v3 = """{"points":150.5,"pointsThisRun":900.0,"totalPointsEarned":5000.0,"omega":-4.0,
            "upgrades":{"gear":2,"motor":1,"cooling":1},"zen":2,"prestigeCount":1,"bestRpm":700.0,
            "totalRevolutions":12345.0,"combo":0.3,"turboCharge":0.5,"turboCooldown":1.0,
            "playTimeSeconds":800.0,"lastSavedEpochMs":99,"achievements":["first_spin","nope"],
            "ownedCosmetics":["body_ocean"],"equipped":{"BODY":"body_ocean"},"version":3}"""
        val s = SaveCodec.decode(v3)!!
        assertEquals(150.5, s.points)
        assertEquals(2L, s.zen)
        assertEquals(5000.0, s.totalPointsEarned)
        assertEquals(1, s.balls.size)
        val b = s.active
        assertEquals("b1", b.id)
        assertEquals(BallTypes.DEFAULT, b.typeId)
        assertEquals(-4.0, b.omega)
        assertEquals(mapOf(Upgrades.GEAR to 2, Upgrades.MOTOR to 1, Upgrades.COOLING to 1), b.upgrades)
        assertEquals(900.0, b.pointsThisRun)
        assertEquals(700.0, b.bestRpm)
        assertEquals(1, b.prestigeCount)
        assertEquals(0.5, b.turboCharge)
        val expectedInvested = Upgrades.get(Upgrades.GEAR).let { it.costAt(0) + it.costAt(1) } +
            Upgrades.get(Upgrades.MOTOR).costAt(0) + Upgrades.get(Upgrades.COOLING).costAt(0)
        assertEquals(expectedInvested, b.invested, 1e-9)
        assertEquals(setOf("first_spin"), s.achievements)
        assertEquals(800.0, s.playTimeSeconds)
        assertEquals(99L, s.lastSavedEpochMs)
        assertEquals(2, s.nextBallNumber)
        assertEquals(GameState.CURRENT_VERSION, s.version)
    }

    @Test
    fun `migrates a version 1 save with no version field`() {
        val s = SaveCodec.decode("""{"points": 10, "omega": 3.0, "overdriveCooldown": 12.0}""")!!
        assertEquals(10.0, s.points)
        assertEquals(3.0, s.active.omega)
        assertEquals(1, s.balls.size)
    }

    @Test
    fun `rejects garbage and sanitises bad values`() {
        assertNull(SaveCodec.decode("not json"))
        assertNull(SaveCodec.decode(""))
        assertNull(SaveCodec.decode(null))
        val fixed = SaveCodec.decode(
            """{"version": 4, "points": -5, "gems": -3, "activeBallId": "zzz", "nextBallNumber": 1,
               "balls": [
                 {"id": "b7", "typeId": "nope", "omega": 1e999, "turboCharge": 7, "turboCooldown": -4,
                  "upgrades": {"gear": 999, "bogus": 3, "rack": 2}, "topView": true},
                 {"id": "b7", "typeId": "solar"},
                 {"id": "", "typeId": "solar"}
               ],
               "accountUpgrades": {"rack": 99, "gear": 1},
               "ownedSkins": ["stripes", "fake"], "outerSkin": "ember", "interiorSkin": "ember",
               "achievements": ["nope", "first_spin"], "buff": "FRENZY", "buffRemaining": 0, "sparkRemaining": 99}""",
        )!!
        assertEquals(0.0, fixed.points)
        assertEquals(0L, fixed.gems)
        assertEquals(1, fixed.balls.size, "duplicate and blank ids dropped")
        val b = fixed.active
        assertEquals("b7", b.id)
        assertEquals(BallTypes.DEFAULT, b.typeId)
        assertEquals(0.0, b.omega)
        assertEquals(1.0, b.turboCharge)
        assertEquals(0.0, b.turboCooldown)
        assertEquals(mapOf(Upgrades.GEAR to Upgrades.get(Upgrades.GEAR).maxLevel), b.upgrades, "account and unknown ids dropped from the ball")
        assertEquals(false, b.topView, "top view needs the gimbal")
        assertEquals(mapOf(Upgrades.RACK to Upgrades.get(Upgrades.RACK).maxLevel), fixed.accountUpgrades)
        assertEquals(setOf("stripes"), fixed.ownedSkins)
        assertNull(fixed.outerSkin, "ember is not an outer skin")
        assertNull(fixed.interiorSkin, "ember is not owned")
        assertEquals(setOf("first_spin"), fixed.achievements)
        assertNull(fixed.buff, "expired buff cleared")
        assertEquals(Stats.SPARK_WINDOW, fixed.sparkRemaining)
        assertEquals(8, fixed.nextBallNumber, "next id follows the highest existing one")
        assertEquals(GameState.CURRENT_VERSION, fixed.version)
    }

    @Test
    fun `an empty garage gets a default ball`() {
        val s = SaveCodec.decode("""{"version": 4, "balls": []}""")!!
        assertEquals(1, s.balls.size)
        assertEquals(BallTypes.DEFAULT, s.active.typeId)
        assertTrue(s.activeBallId == s.balls.first().id)
    }
}
