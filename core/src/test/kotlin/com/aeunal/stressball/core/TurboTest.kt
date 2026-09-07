package com.aeunal.stressball.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TurboTest {
    private val dt = 1.0 / 60.0

    private fun run(engine: GameEngine, seconds: Double) {
        var t = 0.0
        while (t < seconds) {
            engine.tick(dt)
            t += dt
        }
    }

    /** A well-oiled ball so friction does not muddy the turbo numbers. */
    private fun oiled(rpm: Double = 100.0) = GameEngine(
        GameState(omega = rpmToOmega(rpm), upgrades = mapOf(Upgrades.BEARINGS to 30, Upgrades.FLYWHEEL to 20)),
    )

    @Test
    fun `stock turbo is very short and squeezing adds momentum fast`() {
        assertTrue(Stats.turboCapacity(0) <= 1.5, "stock fuel should last about a second")
        val e = oiled()
        e.setTurbo(1.0)
        run(e, 0.5)
        assertTrue(e.view().turboBoosting)
        assertTrue(e.view().rpm > 250.0, "half a second of full squeeze should add a lot, got ${e.view().rpm}")
        run(e, Stats.turboCapacity(0))
        assertEquals(0.0, e.state.turboCharge, 1e-9, "fuel burnt out")
        assertTrue(e.view().turboOverheating)
    }

    @Test
    fun `squeeze weight scales fuel burn, thrust, cap and income`() {
        val full = oiled(); full.setTurbo(1.0)
        val half = oiled(); half.setTurbo(0.5)
        run(full, 0.5); run(half, 0.5)
        assertEquals(1.0 - full.state.turboCharge, 2.0 * (1.0 - half.state.turboCharge), 1e-6, "half squeeze burns half the fuel")
        assertTrue(full.view().rpm > half.view().rpm, "more squeeze, more thrust")
        val base = Stats.rpmCap(0)
        assertEquals(base * (1.0 + 0.5 * (Stats.turboCapMultiplier(0) - 1.0)), half.view().rpmCap, 1e-9)
        assertEquals(base * Stats.turboCapMultiplier(0), full.view().rpmCap, 1e-9)

        val idle = oiled(60.0).view().pointsPerSecond
        val boosted = oiled(60.0).also { it.setTurbo(0.5) }.view().pointsPerSecond
        assertEquals(idle * (1.0 + 0.5 * (Stats.TURBO_INCOME_MULT - 1.0)), boosted, 1e-9)
    }

    @Test
    fun `a fast pinch kicks harder than a slow one at the same weight`() {
        val slow = oiled(); slow.setTurbo(0.6, squeezeRate = 0.0)
        val fast = oiled(); fast.setTurbo(0.6, squeezeRate = 5.0)
        run(slow, 0.2); run(fast, 0.2)
        assertTrue(fast.view().rpm > slow.view().rpm + 50.0, "fast ${fast.view().rpm} vs slow ${slow.view().rpm}")
        assertEquals(slow.state.turboCharge, fast.state.turboCharge, 1e-9, "the kick is free of extra fuel")
    }

    @Test
    fun `fuel refills only after a cooldown that every squeeze restarts`() {
        val e = oiled()
        e.setTurbo(1.0)
        run(e, 0.6)
        val afterUse = e.state.turboCharge
        assertTrue(afterUse < 1.0)
        e.setTurbo(0.0)
        assertEquals(Stats.turboCooldown(0), e.state.turboCooldown, 1e-6)
        run(e, Stats.turboCooldown(0) - 0.5)
        assertEquals(afterUse, e.state.turboCharge, 1e-9, "no refill during the cooldown")
        run(e, 1.0)
        assertTrue(e.state.turboCharge > afterUse, "refilling once the cooldown passed")
        // A new squeeze restarts the cooldown.
        e.setTurbo(0.3)
        e.tick(dt)
        assertEquals(Stats.turboCooldown(0), e.state.turboCooldown, 1e-6)
        e.setTurbo(0.0)
        run(e, Stats.turboCooldown(0) + Stats.turboRefill(0) + 0.5)
        assertEquals(1.0, e.state.turboCharge, 1e-6)
        assertEquals(0.0, e.state.turboCooldown, 1e-9)
    }

    @Test
    fun `squeezing an empty tank brakes in proportion`() {
        val e = oiled(300.0)
        e.load(e.state.copy(turboCharge = 0.0))
        e.setTurbo(1.0)
        run(e, 1.0)
        val full = e.view().rpm
        val g = oiled(300.0)
        g.load(g.state.copy(turboCharge = 0.0))
        g.setTurbo(0.3)
        run(g, 1.0)
        assertTrue(full < 300.0 - 200.0, "full squeeze empty should brake hard, at $full")
        assertTrue(g.view().rpm > full, "lighter squeeze brakes less")
        assertFalse(e.view().turboBoosting)
        assertTrue(e.view().turboOverheating)
    }

    @Test
    fun `releasing the squeeze bleeds speed back to the base cap`() {
        val e = oiled()
        e.setTurbo(1.0)
        run(e, 1.0)
        assertTrue(e.view().rpm > Stats.rpmCap(0))
        e.setTurbo(0.0)
        run(e, 0.1)
        assertTrue(e.view().rpm > Stats.rpmCap(0), "no snap")
        run(e, 3.0)
        assertTrue(e.view().rpm <= Stats.rpmCap(0) + 1e-6)
    }

    @Test
    fun `turbo upgrade improves every coefficient`() {
        for (l in 0 until 8) {
            assertTrue(Stats.turboCapacity(l + 1) > Stats.turboCapacity(l))
            assertTrue(Stats.turboCooldown(l + 1) < Stats.turboCooldown(l))
            assertTrue(Stats.turboRefill(l + 1) < Stats.turboRefill(l))
            assertTrue(Stats.turboCapMultiplier(l + 1) > Stats.turboCapMultiplier(l))
            assertTrue(Stats.turboAccel(l + 1) > Stats.turboAccel(l))
            assertTrue(Stats.turboSqueezeGain(l + 1) > Stats.turboSqueezeGain(l))
        }
    }

    @Test
    fun `input is clamped and sanitised`() {
        val e = GameEngine()
        e.setTurbo(7.0, -3.0)
        assertEquals(1.0, e.turboWeight)
        assertEquals(0.0, e.squeezeRate)
        e.setTurbo(Double.NaN, Double.NaN)
        assertEquals(0.0, e.turboWeight)
        assertEquals(0.0, e.squeezeRate)
    }
}
