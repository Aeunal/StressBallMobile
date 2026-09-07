package com.aeunal.stressball.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameEngineTest {

    private fun run(engine: GameEngine, seconds: Double, dt: Double = 1.0 / 60.0, finger: Double = 0.0) {
        var t = 0.0
        while (t < seconds) {
            if (finger > 0) engine.spin(finger)
            engine.tick(dt)
            t += dt
        }
    }

    @Test
    fun `ball at rest stays at rest and earns nothing`() {
        val e = GameEngine()
        run(e, 5.0)
        assertEquals(0.0, e.state.omega)
        assertEquals(0.0, e.state.points)
        assertEquals(5.0, e.state.playTimeSeconds, 0.05)
    }

    @Test
    fun `finger spin accelerates the ball towards the geared finger speed`() {
        val e = GameEngine()
        val finger = rpmToOmega(120.0)
        run(e, 3.0, finger = finger)
        val rpm = e.view().rpm
        assertTrue(rpm > 100.0, "expected close to 120 RPM, got $rpm")
        assertTrue(rpm <= 120.0 + 1e-6, "must not exceed finger speed, got $rpm")
        assertTrue(e.state.points > 0.0)
    }

    @Test
    fun `gear cap multiplies finger speed`() {
        val plain = GameEngine()
        val geared = GameEngine(GameState(upgrades = mapOf(Upgrades.GEAR to 2)))
        run(plain, 3.0, finger = rpmToOmega(100.0))
        run(geared, 3.0, finger = rpmToOmega(100.0))
        assertTrue(geared.view().rpm > plain.view().rpm * 1.5)
    }

    @Test
    fun `friction brings the ball to a stop`() {
        val e = GameEngine(GameState(omega = rpmToOmega(300.0)))
        run(e, 1.0)
        val afterOne = e.state.omega
        assertTrue(afterOne < rpmToOmega(300.0))
        run(e, 120.0)
        assertEquals(0.0, e.state.omega, 1e-9)
    }

    @Test
    fun `bearings and flywheel reduce decay`() {
        val stock = GameEngine(GameState(omega = rpmToOmega(300.0)))
        val tuned = GameEngine(GameState(omega = rpmToOmega(300.0), upgrades = mapOf(Upgrades.BEARINGS to 5, Upgrades.FLYWHEEL to 3)))
        run(stock, 10.0)
        run(tuned, 10.0)
        assertTrue(tuned.state.omega > stock.state.omega)
    }

    @Test
    fun `motor keeps the ball at its idle floor`() {
        val e = GameEngine(GameState(upgrades = mapOf(Upgrades.MOTOR to 1)))
        run(e, 10.0)
        assertEquals(Stats.motorRpm(1), e.view().rpm, 1e-3)
        assertTrue(e.state.points > 0.0)
    }

    @Test
    fun `rpm never exceeds the cooling cap`() {
        val e = GameEngine()
        run(e, 5.0, finger = rpmToOmega(5000.0))
        assertTrue(e.view().rpm <= Stats.rpmCap(0) + 1e-6)
        val cooled = GameEngine(GameState(upgrades = mapOf(Upgrades.COOLING to 2)))
        run(cooled, 5.0, finger = rpmToOmega(5000.0))
        assertTrue(cooled.view().rpm > Stats.rpmCap(0))
        assertTrue(cooled.view().rpm <= Stats.rpmCap(2) + 1e-6)
    }

    @Test
    fun `income scales with rpm and points per rev`() {
        val slow = GameEngine(GameState(omega = rpmToOmega(60.0), upgrades = mapOf(Upgrades.MOTOR to 50, Upgrades.COOLING to 15)))
        // Isolate income: use a huge motor floor so friction is irrelevant, then compare view rates.
        val v = GameEngine(GameState(omega = rpmToOmega(60.0))).view()
        assertEquals(1.0 * 1.0, v.pointsPerRev, 1e-9)
        assertEquals(1.0, v.pointsPerSecond, 1e-9) // 60 RPM = 1 rev/s
        val counter = GameEngine(GameState(omega = rpmToOmega(60.0), upgrades = mapOf(Upgrades.COUNTER to 3))).view()
        assertEquals(Stats.pointsPerRev(3), counter.pointsPerSecond, 1e-9)
        assertTrue(slow.view().pointsPerSecond > 0)
    }

    @Test
    fun `petal shell multiplies income only above threshold`() {
        val below = GameEngine(GameState(omega = rpmToOmega(200.0), upgrades = mapOf(Upgrades.PETALS to 2))).view()
        val above = GameEngine(GameState(omega = rpmToOmega(400.0), upgrades = mapOf(Upgrades.PETALS to 2))).view()
        assertFalse(below.petalsOpen)
        assertTrue(above.petalsOpen)
        assertEquals(1.0, below.totalMultiplier, 1e-9)
        assertEquals(Stats.petalMultiplier(2), above.totalMultiplier, 1e-9)
    }

    @Test
    fun `resonance combo builds while fast and decays while slow`() {
        val e = GameEngine(GameState(upgrades = mapOf(Upgrades.RESONANCE to 2, Upgrades.MOTOR to 10)))
        run(e, 30.0)
        assertTrue(e.view().rpm >= Stats.RESONANCE_THRESHOLD_RPM, "motor 10 should exceed the threshold")
        assertTrue(e.state.combo > 0.5)
        assertTrue(e.state.combo <= Stats.maxCombo(2) + 1e-9)
        e.load(e.state.copy(upgrades = mapOf(Upgrades.RESONANCE to 2), omega = 0.0))
        run(e, 10.0)
        assertEquals(0.0, e.state.combo, 1e-9)
    }

    @Test
    fun `buying upgrades charges the geometric cost`() {
        val def = Upgrades.get(Upgrades.GEAR)
        val e = GameEngine(GameState(points = def.costAt(0) + def.costAt(1)))
        assertTrue(e.buy(Upgrades.GEAR))
        assertTrue(e.buy(Upgrades.GEAR))
        assertFalse(e.buy(Upgrades.GEAR), "third level must be unaffordable")
        assertEquals(2, e.state.level(Upgrades.GEAR))
        assertEquals(0.0, e.state.points, 1e-9)
    }

    @Test
    fun `cannot buy beyond max level`() {
        val def = Upgrades.get(Upgrades.PETALS)
        val e = GameEngine(GameState(points = 1e300, upgrades = mapOf(Upgrades.PETALS to def.maxLevel)))
        assertFalse(e.canBuy(Upgrades.PETALS))
        assertFalse(e.buy(Upgrades.PETALS))
    }

    @Test
    fun `overdrive requires unlock and respects cooldown`() {
        val locked = GameEngine()
        assertFalse(locked.overdrive())
        val e = GameEngine(GameState(upgrades = mapOf(Upgrades.OVERDRIVE to 1)))
        assertTrue(e.overdrive())
        assertTrue(e.view().rpm > 100.0)
        assertTrue(e.view().overdriveActive)
        assertFalse(e.overdrive(), "must be on cooldown")
        run(e, Stats.overdriveCooldown(1) + 0.1)
        assertTrue(e.view().overdriveReady)
        assertTrue(e.overdrive())
    }

    @Test
    fun `prestige converts run points into zen and resets progress`() {
        val e = GameEngine(GameState(points = 5.0, pointsThisRun = 4_000_000.0, totalPointsEarned = 4_000_000.0, upgrades = mapOf(Upgrades.GEAR to 5), omega = 10.0, bestRpm = 900.0))
        assertEquals(2L, e.zenOnReset())
        assertTrue(e.prestige())
        assertTrue(e.state.zen >= 2L)
        assertEquals(1, e.state.prestigeCount)
        assertEquals(0.0, e.state.points)
        assertEquals(0.0, e.state.omega)
        assertTrue(e.state.upgrades.isEmpty())
        assertEquals(900.0, e.state.bestRpm)
        assertEquals(4_000_000.0, e.state.totalPointsEarned)
        assertTrue("zen_master" in e.state.achievements)
        assertFalse(GameEngine(GameState(pointsThisRun = 10.0)).prestige())
    }

    @Test
    fun `zen multiplies income`() {
        val v0 = GameEngine(GameState(omega = rpmToOmega(60.0))).view()
        val v5 = GameEngine(GameState(omega = rpmToOmega(60.0), zen = 5)).view()
        assertEquals(v0.pointsPerSecond * Stats.zenMultiplier(5), v5.pointsPerSecond, 1e-9)
    }

    @Test
    fun `achievements unlock once and grant zen`() {
        val e = GameEngine()
        run(e, 2.0, finger = rpmToOmega(100.0))
        assertTrue("first_spin" in e.state.achievements)
        val zenAfter = e.state.zen
        assertTrue(zenAfter >= 1)
        run(e, 2.0, finger = rpmToOmega(100.0))
        assertEquals(zenAfter, e.state.zen, "achievement must not be granted twice")
    }

    @Test
    fun `offline progress credits motor income with efficiency and cap`() {
        val start = 1_000_000L
        val e = GameEngine(GameState(upgrades = mapOf(Upgrades.MOTOR to 3, Upgrades.GYRO to 1), lastSavedEpochMs = start))
        val hours = 10.0
        val report = e.applyOfflineProgress(start + (hours * 3600 * 1000).toLong())
        assertNotNull(report)
        assertEquals(hours * 3600, report.secondsAway, 1e-6)
        assertEquals(Stats.offlineCapHours(1) * 3600.0, report.secondsCredited, 1e-6)
        val expectedRev = rpmToOmega(Stats.motorRpm(3)) / TWO_PI * report.secondsCredited
        assertEquals(expectedRev * Stats.pointsPerRev(0) * Stats.offlineEfficiency(1), report.pointsEarned, 1e-6)
        assertEquals(report.pointsEarned, e.state.points, 1e-9)
        assertEquals(Stats.motorRpm(3), e.view().rpm, 1e-9)
    }

    @Test
    fun `offline progress ignores short or missing absences`() {
        assertNull(GameEngine(GameState()).applyOfflineProgress(5_000L))
        val e = GameEngine(GameState(lastSavedEpochMs = 1_000L, upgrades = mapOf(Upgrades.MOTOR to 1)))
        assertNull(e.applyOfflineProgress(1_000L + 5_000L))
        assertEquals(0.0, e.state.points)
    }

    @Test
    fun `large tick is subdivided and stays stable`() {
        val e = GameEngine(GameState(omega = rpmToOmega(500.0)))
        e.tick(30.0)
        assertTrue(e.state.omega >= 0.0)
        assertTrue(e.state.omega.isFinite())
        assertTrue(e.state.points > 0.0)
        val nan = GameEngine()
        nan.tick(Double.NaN)
        nan.tick(-1.0)
        assertEquals(0.0, nan.state.playTimeSeconds)
    }
}
