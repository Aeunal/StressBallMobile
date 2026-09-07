package com.aeunal.stressball.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpgradesTest {
    @Test
    fun `ids are unique and descriptions render for every level`() {
        assertEquals(Upgrades.all.size, Upgrades.all.map { it.id }.toSet().size)
        for (def in Upgrades.all) {
            for (level in 0..def.maxLevel) {
                assertTrue(def.describe(level).isNotBlank(), "${def.id}@$level")
                assertTrue(def.costAt(level) > 0)
            }
            assertTrue(def.costAt(1) > def.costAt(0))
        }
    }

    @Test
    fun `stats are monotonic in the helpful direction`() {
        for (l in 0 until 20) {
            assertTrue(Stats.gearRatio(l + 1) > Stats.gearRatio(l))
            assertTrue(Stats.coupling(l + 1) > Stats.coupling(l))
            assertTrue(Stats.viscousFriction(l + 1, 0) < Stats.viscousFriction(l, 0))
            assertTrue(Stats.constantFriction(0, l + 1) < Stats.constantFriction(0, l))
            assertTrue(Stats.motorRpm(l + 1) > Stats.motorRpm(l))
            assertTrue(Stats.pointsPerRev(l + 1) > Stats.pointsPerRev(l))
            assertTrue(Stats.rpmCap(l + 1) > Stats.rpmCap(l))
            assertTrue(Stats.offlineEfficiency(l + 1) >= Stats.offlineEfficiency(l))
            assertTrue(Stats.offlineEfficiency(l) <= 0.9)
        }
        assertTrue(Stats.overdriveCooldown(2) < Stats.overdriveCooldown(1))
        assertEquals(0L, Stats.zenFor(999_999.0))
        assertEquals(1L, Stats.zenFor(1_000_000.0))
        assertEquals(3L, Stats.zenFor(9_000_000.0))
    }
}
