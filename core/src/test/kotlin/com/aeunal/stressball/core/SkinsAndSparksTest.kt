package com.aeunal.stressball.core

import com.aeunal.stressball.core.TestStates.oiled
import com.aeunal.stressball.core.TestStates.single
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkinsAndSparksTest {
    private val dt = 1.0 / 60.0

    private fun run(engine: GameEngine, seconds: Double) {
        var t = 0.0
        while (t < seconds) {
            engine.tick(dt)
            t += dt
        }
    }

    // ------------------------------------------------------------------
    // Gems and skins
    // ------------------------------------------------------------------

    /** Income with the Zen from freshly unlocked achievements factored out. */
    private fun skinIncome(e: GameEngine) = e.view().pointsPerSecond / Stats.zenMultiplier(e.state.zen)

    @Test
    fun `skins are bought with gems, one per slot is active, and buffs apply`() {
        val e = GameEngine(single(omega = rpmToOmega(60.0)))
        assertFalse(e.buySkin("rings"), "no gems")
        e.topUpGems()
        e.topUpGems()
        assertEquals(2 * Stats.GEM_TOP_UP, e.state.gems)
        assertTrue(e.buySkin("stripes"))
        assertEquals(2 * Stats.GEM_TOP_UP - Skins.get("stripes").gems, e.state.gems)
        assertEquals("stripes", e.view().outerSkin?.id)
        assertEquals(1.10, skinIncome(e), 1e-9)

        assertTrue(e.buySkin("ember"))
        assertEquals("ember", e.view().interiorSkin?.id)
        assertEquals(1.10 * 1.10, skinIncome(e), 1e-9)

        e.load(e.state.copy(gems = 100))
        assertTrue(e.buySkin("spots"))
        assertEquals("spots", e.view().outerSkin?.id, "buying replaces the slot")
        assertEquals(1.10, skinIncome(e), 1e-9, "stripes no longer active")
        assertTrue(e.equipSkin("stripes"))
        assertEquals(1.10 * 1.10, skinIncome(e), 1e-9)
        assertFalse(e.buySkin("stripes"), "already owned")
        assertFalse(e.equipSkin("void"), "not owned")
        e.unequipSkin(SkinSlot.OUTER)
        assertNull(e.view().outerSkin)
        assertTrue("stylist" in e.state.achievements)
    }

    @Test
    fun `skin catalogue is well formed`() {
        assertEquals(Skins.all.size, Skins.all.map { it.id }.toSet().size)
        for (slot in SkinSlot.entries) assertTrue(Skins.forSlot(slot).size >= 4)
        for (def in Skins.all) {
            assertTrue(def.gems > 0)
            assertTrue(def.buff != SkinBuff.NONE, "${def.id} must do something")
        }
        val combined = SkinBuff(income = 1.1, grip = 1.2) * SkinBuff(income = 1.5, cap = 1.3)
        assertEquals(1.65, combined.income, 1e-9)
        assertEquals(1.2, combined.grip, 1e-9)
        assertEquals(1.3, combined.cap, 1e-9)
    }

    @Test
    fun `clockwork core raises the motor floor and crystal core the combo ceiling`() {
        val e = GameEngine(single(upgrades = mapOf(Upgrades.MOTOR to 2, Upgrades.RESONANCE to 2), gems = 200))
        e.buySkin("clockwork")
        run(e, 10.0)
        assertEquals(Stats.motorRpm(2) * 1.3, e.view().rpm, 1e-3)
        e.buySkin("crystal")
        e.load(e.state.copy(balls = e.state.balls.map { it.copy(omega = rpmToOmega(500.0), upgrades = it.upgrades + oiled) }))
        run(e, 60.0)
        assertTrue(e.active.combo > Stats.maxCombo(2), "combo can exceed the stock ceiling: ${e.active.combo}")
    }

    // ------------------------------------------------------------------
    // Golden sparks
    // ------------------------------------------------------------------

    @Test
    fun `a spark appears after the timer, stays for a window, then the timer restarts`() {
        val e = GameEngine(single())
        assertNull(e.tapSpark(), "nothing to tap yet")
        run(e, Stats.FIRST_SPARK_SECONDS + 0.1)
        assertTrue(e.view().sparkActive)
        run(e, Stats.SPARK_WINDOW + 0.1)
        assertFalse(e.view().sparkActive, "missed")
        assertEquals(Stats.sparkInterval(0), e.state.sparkTimer, 0.2)
    }

    @Test
    fun `tapping a spark grants a buff and restarts the timer`() {
        val kinds = HashSet<BuffKind>()
        for (seed in 0 until 40) {
            val e = GameEngine(single(omega = rpmToOmega(60.0), upgrades = oiled), Random(seed))
            e.load(e.state.copy(sparkRemaining = 5.0))
            val kind = e.tapSpark()
            assertNotNull(kind)
            kinds += kind
            assertFalse(e.view().sparkActive)
            assertEquals(1, e.state.sparksTapped)
            assertTrue("lucky" in e.state.achievements)
            when (kind) {
                BuffKind.FRENZY -> {
                    assertEquals(kind, e.view().buff)
                    assertEquals(Stats.FRENZY_MULT, e.view().totalMultiplier / Stats.zenMultiplier(e.state.zen), 1e-9)
                    run(e, Stats.frenzyDuration(0) + 0.1)
                    assertNull(e.view().buff, "frenzy expires")
                }
                BuffKind.JACKPOT -> {
                    assertTrue(e.state.points >= Stats.JACKPOT_MIN_POINTS)
                    assertNull(e.view().buff)
                }
                BuffKind.RECHARGE -> {
                    assertEquals(1.0, e.active.turboCharge)
                    e.setTurbo(1.0)
                    run(e, 1.0)
                    assertEquals(1.0, e.active.turboCharge, 1e-9, "fuel is free while recharged")
                }
                BuffKind.WILD_GRIP -> {
                    val plain = GameEngine(single(omega = rpmToOmega(400.0), upgrades = oiled))
                    plain.setFinger(true); e.load(e.state.copy(balls = e.state.balls.map { it.copy(omega = rpmToOmega(400.0)) })); e.setFinger(true)
                    run(plain, 0.5); run(e, 0.5)
                    assertTrue(e.view().rpm < plain.view().rpm - 50.0, "wild grip brakes harder")
                }
            }
        }
        assertEquals(BuffKind.entries.toSet(), kinds, "all buffs reachable")
    }

    @Test
    fun `lucky charm shortens the interval and lengthens frenzy`() {
        assertTrue(Stats.sparkInterval(5) < Stats.sparkInterval(0))
        assertTrue(Stats.frenzyDuration(5) > Stats.frenzyDuration(0))
    }
}
