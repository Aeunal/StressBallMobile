package com.aeunal.stressball.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpgradesTest {
    @Test
    fun `ids are unique and costs grow`() {
        assertEquals(Upgrades.all.size, Upgrades.all.map { it.id }.toSet().size)
        for (def in Upgrades.all) {
            for (level in 0..def.maxLevel) assertTrue(def.costAt(level) > 0)
            assertTrue(def.costAt(1) > def.costAt(0))
        }
    }

    @Test
    fun `stats are monotonic in the helpful direction`() {
        for (l in 0 until 20) {
            assertTrue(Stats.gearRatio(l + 1) > Stats.gearRatio(l))
            assertTrue(Stats.gripRate(l + 1) > Stats.gripRate(l))
            assertTrue(Stats.gripAccel(l + 1) > Stats.gripAccel(l))
            assertTrue(Stats.tractionRatio(l + 1) > Stats.tractionRatio(l))
            assertTrue(Stats.turboCooldown(l + 1) < Stats.turboCooldown(l))
            assertTrue(Stats.viscousFriction(l + 1, 0) < Stats.viscousFriction(l, 0))
            assertTrue(Stats.constantFriction(l + 1, 0) < Stats.constantFriction(l, 0))
            assertTrue(Stats.constantFriction(0, l + 1) < Stats.constantFriction(0, l))
            assertTrue(Stats.motorRpm(l + 1) > Stats.motorRpm(l))
            assertTrue(Stats.pointsPerRev(l + 1) > Stats.pointsPerRev(l))
            assertTrue(Stats.rpmCap(l + 1) > Stats.rpmCap(l))
            assertTrue(Stats.offlineEfficiency(l + 1) >= Stats.offlineEfficiency(l))
            assertTrue(Stats.offlineEfficiency(l) <= 0.9)
            assertTrue(Stats.turboCapacity(l + 1) > Stats.turboCapacity(l))
            assertTrue(Stats.turboRefill(l + 1) < Stats.turboRefill(l))
            assertTrue(Stats.turboAccel(l + 1) > Stats.turboAccel(l))
        }
        assertEquals(0L, Stats.zenFor(999_999.0))
        assertEquals(1L, Stats.zenFor(1_000_000.0))
        assertEquals(3L, Stats.zenFor(9_000_000.0))
    }

    @Test
    fun `geared target is one-to-one for slow turns and fully geared for fast circles`() {
        val level = 4
        val ratio = Stats.gearRatio(level)
        assertEquals(2.0, Stats.gearedTarget(2.0, level), 1e-9)
        assertEquals(-2.0, Stats.gearedTarget(-2.0, level), 1e-9)
        assertEquals(12.0 * ratio, Stats.gearedTarget(12.0, level), 1e-9)
        assertEquals(-12.0 * ratio, Stats.gearedTarget(-12.0, level), 1e-9)
        val mid = Stats.gearedTarget(6.0, level)
        assertTrue(mid > 6.0 && mid < 6.0 * ratio, "blend region should be in between, got $mid")
        assertEquals(12.0, Stats.gearedTarget(12.0, 0), 1e-9)
    }

    @Test
    fun `effect tiers`() {
        assertEquals(0, Stats.fxTier(0.0))
        assertEquals(0, Stats.fxTier(349.0))
        assertEquals(1, Stats.fxTier(350.0))
        assertEquals(2, Stats.fxTier(900.0))
        assertEquals(3, Stats.fxTier(2000.0))
        assertEquals(4, Stats.fxTier(9999.0))
        assertTrue(Stats.fxTiers.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun `stock cap is reachable with turbo, not without`() {
        assertTrue(Stats.rpmCap(0) < Stats.fxTiers[1], "flames should need cooling")
        assertTrue(Stats.rpmCap(0) * Stats.turboCapMultiplier(0) > Stats.fxTiers[0], "sparks reachable on a stock ball with turbo")
    }
}
