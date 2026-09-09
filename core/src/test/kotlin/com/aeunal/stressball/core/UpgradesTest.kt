package com.aeunal.stressball.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpgradesTest {
    @Test
    fun `ids are unique, costs grow, and every category is populated`() {
        assertEquals(Upgrades.all.size, Upgrades.all.map { it.id }.toSet().size)
        assertTrue(Upgrades.all.size >= 20, "a lot more upgrades: ${Upgrades.all.size}")
        for (def in Upgrades.all) {
            for (level in 0..def.maxLevel) assertTrue(def.costAt(level) > 0)
            if (def.maxLevel > 1) assertTrue(def.costAt(1) > def.costAt(0), def.id)
        }
        for (cat in UpgradeCategory.entries) assertTrue(Upgrades.all.any { it.category == cat }, cat.name)
        assertTrue(Upgrades.ofScope(UpgradeScope.ACCOUNT).all { it.category == UpgradeCategory.GARAGE })
        assertTrue(Upgrades.ofScope(UpgradeScope.BALL).none { it.category == UpgradeCategory.GARAGE })
    }

    @Test
    fun `stats are monotonic in the helpful direction`() {
        for (l in 0 until 20) {
            assertTrue(Stats.gearRatio(l + 1) > Stats.gearRatio(l))
            assertTrue(Stats.gripRate(l + 1) > Stats.gripRate(l))
            assertTrue(Stats.gripAccel(l + 1) > Stats.gripAccel(l))
            assertTrue(Stats.tractionRatio(l + 1) > Stats.tractionRatio(l))
            assertTrue(Stats.viscousFriction(l + 1, 0) < Stats.viscousFriction(l, 0))
            assertTrue(Stats.constantFriction(l + 1, 0) < Stats.constantFriction(l, 0))
            assertTrue(Stats.constantFriction(0, l + 1) < Stats.constantFriction(0, l))
            assertTrue(Stats.aeroDrag(l + 1) < Stats.aeroDrag(l))
            assertTrue(Stats.motorRpm(l + 1) > Stats.motorRpm(l))
            assertTrue(Stats.pointsPerRev(l + 1) > Stats.pointsPerRev(l))
            assertTrue(Stats.rpmCap(l + 1) > Stats.rpmCap(l))
            assertTrue(Stats.cryoFactor(l + 1) > Stats.cryoFactor(l))
            assertTrue(Stats.offlineEfficiency(l + 1) >= Stats.offlineEfficiency(l))
            assertTrue(Stats.offlineEfficiency(l) <= 0.9)
            assertTrue(Stats.turboCooldown(l + 1) < Stats.turboCooldown(l))
            assertTrue(Stats.nitroFactor(l + 1) > Stats.nitroFactor(l))
            assertTrue(Stats.kersFraction(l + 1) >= Stats.kersFraction(l))
            assertTrue(Stats.kersFraction(l) <= 1.0)
            assertTrue(Stats.heatSinkFactor(l + 1) < Stats.heatSinkFactor(l))
            assertTrue(Stats.comboDecayFactor(l + 1) < Stats.comboDecayFactor(l))
            assertTrue(Stats.rackEfficiency(l + 1) >= Stats.rackEfficiency(l))
            assertTrue(Stats.rackEfficiency(l) <= 1.0)
            assertTrue(Stats.interestPerSecond(l + 1) > Stats.interestPerSecond(l))
            assertTrue(Stats.sparkInterval(l + 1) < Stats.sparkInterval(l))
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
    fun `effect tiers reach beyond plasma`() {
        assertEquals(0, Stats.fxTier(0.0))
        assertEquals(1, Stats.fxTier(350.0))
        assertEquals(4, Stats.fxTier(4000.0))
        assertEquals(5, Stats.fxTier(8000.0))
        assertEquals(6, Stats.fxTier(16000.0))
        assertEquals(7, Stats.fxTier(40000.0))
        assertTrue(Stats.fxTiers.zipWithNext().all { (a, b) -> a < b })
        val maxCap = Stats.rpmCap(30) * Stats.cryoFactor(5) * BallTypes.all.maxOf { it.traits.cap } * Stats.turboCapMultiplier(8)
        assertTrue(maxCap > Stats.fxTiers.last(), "the top tier must be reachable: $maxCap")
    }

    @Test
    fun `stock cap is reachable with turbo, not without`() {
        assertTrue(Stats.rpmCap(0) < Stats.fxTiers[1], "flames should need cooling")
        assertTrue(Stats.rpmCap(0) * Stats.turboCapMultiplier(0) > Stats.fxTiers[0], "sparks reachable on a stock ball with turbo")
    }
}
