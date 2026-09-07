package com.aeunal.stressball.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameEngineTest {

    private val dt = 1.0 / 60.0

    private fun run(engine: GameEngine, seconds: Double) {
        var t = 0.0
        while (t < seconds) {
            engine.tick(dt)
            t += dt
        }
    }

    private fun spin(engine: GameEngine, seconds: Double, fingerOmega: Double) {
        engine.setFinger(true, fingerOmega)
        run(engine, seconds)
        engine.setFinger(false)
    }

    // ------------------------------------------------------------------
    // Rest and friction
    // ------------------------------------------------------------------

    @Test
    fun `ball at rest stays at rest and earns nothing`() {
        val e = GameEngine()
        run(e, 5.0)
        assertEquals(0.0, e.state.omega)
        assertEquals(0.0, e.state.points)
        assertEquals(5.0, e.state.playTimeSeconds, 0.05)
    }

    @Test
    fun `a stock ball bleeds speed quickly`() {
        val e = GameEngine(GameState(omega = rpmToOmega(120.0)))
        run(e, 3.0)
        assertTrue(e.view().rpm < 40.0, "stock ball should lose most of 120 RPM in 3 s, has ${e.view().rpm}")
        run(e, 30.0)
        assertEquals(0.0, e.state.omega, 1e-9)
    }

    @Test
    fun `oiled bearings and a flywheel keep it spinning far longer`() {
        val stock = GameEngine(GameState(omega = rpmToOmega(300.0)))
        val tuned = GameEngine(GameState(omega = rpmToOmega(300.0), upgrades = mapOf(Upgrades.BEARINGS to 8, Upgrades.FLYWHEEL to 4)))
        run(stock, 10.0)
        run(tuned, 10.0)
        assertTrue(tuned.view().rpm > 3 * stock.view().rpm + 50, "tuned ${tuned.view().rpm} vs stock ${stock.view().rpm}")
    }

    @Test
    fun `every bearings level is felt`() {
        var previous = Double.MAX_VALUE
        for (level in 0..5) {
            val e = GameEngine(GameState(omega = rpmToOmega(300.0), upgrades = mapOf(Upgrades.BEARINGS to level)))
            run(e, 1.0)
            val lost = 300.0 - e.view().rpm
            assertTrue(lost < previous * 0.9, "level $level should shed at least 10% less speed than level ${level - 1}")
            previous = lost
        }
    }

    // ------------------------------------------------------------------
    // Finger
    // ------------------------------------------------------------------

    @Test
    fun `a fast finger spins the ball up towards the geared speed but never past it`() {
        val e = GameEngine()
        val finger = rpmToOmega(120.0) // 12.6 rad/s: fully geared, ratio 1 at level 0
        spin(e, 3.0, finger)
        val rpm = e.view().rpm
        assertTrue(rpm > 90.0, "expected close to 120 RPM, got $rpm")
        assertTrue(rpm <= 120.0 + 1e-6, "must not exceed finger speed, got $rpm")
        assertTrue(e.state.points > 0.0)
        assertEquals(1, e.view().direction)
    }

    @Test
    fun `a slow turn moves the ball one-to-one even with a tall gear`() {
        val e = GameEngine(GameState(upgrades = mapOf(Upgrades.GEAR to 6)))
        val finger = 2.0 // rad/s, below GEAR_SLIP_START
        e.setFinger(true, finger)
        run(e, 2.0)
        assertEquals(finger, e.state.omega, 0.25)
        e.setFinger(true, -finger)
        run(e, 2.0)
        assertEquals(-finger, e.state.omega, 0.25)
    }

    @Test
    fun `a fast circle engages the gear`() {
        val plain = GameEngine()
        val geared = GameEngine(GameState(upgrades = mapOf(Upgrades.GEAR to 2)))
        val finger = rpmToOmega(100.0)
        spin(plain, 4.0, finger)
        spin(geared, 4.0, finger)
        assertTrue(geared.view().rpm > plain.view().rpm * 1.5, "geared ${geared.view().rpm} vs plain ${plain.view().rpm}")
    }

    @Test
    fun `holding the ball still brakes it to a stop`() {
        val e = GameEngine(GameState(omega = rpmToOmega(400.0), upgrades = mapOf(Upgrades.BEARINGS to 30, Upgrades.FLYWHEEL to 20)))
        run(e, 1.0)
        assertTrue(e.view().rpm > 350.0, "tuned ball should barely slow on its own")
        e.setFinger(true, 0.0)
        run(e, 2.0)
        assertTrue(e.view().rpm < 100.0, "holding should brake hard, still at ${e.view().rpm}")
        run(e, 3.0)
        assertEquals(0.0, e.state.omega, 1e-9)
    }

    @Test
    fun `grip tape brakes and accelerates harder`() {
        val weak = GameEngine(GameState(omega = rpmToOmega(400.0)))
        val strong = GameEngine(GameState(omega = rpmToOmega(400.0), upgrades = mapOf(Upgrades.GRIP to 5)))
        weak.setFinger(true, 0.0); strong.setFinger(true, 0.0)
        run(weak, 0.5); run(strong, 0.5)
        assertTrue(strong.view().rpm < weak.view().rpm - 50)

        val weakUp = GameEngine(); val strongUp = GameEngine(GameState(upgrades = mapOf(Upgrades.GRIP to 5)))
        weakUp.setFinger(true, rpmToOmega(120.0)); strongUp.setFinger(true, rpmToOmega(120.0))
        run(weakUp, 0.25); run(strongUp, 0.25)
        assertTrue(strongUp.view().rpm > weakUp.view().rpm + 20)
    }

    @Test
    fun `finger can reverse the spin direction`() {
        val e = GameEngine(GameState(omega = rpmToOmega(60.0)))
        spin(e, 3.0, -rpmToOmega(120.0))
        assertTrue(e.state.omega < 0.0)
        assertEquals(-1, e.view().direction)
        assertTrue(e.view().rpm > 60.0, "income and rpm are unsigned")
    }

    // ------------------------------------------------------------------
    // Motor and cap
    // ------------------------------------------------------------------

    @Test
    fun `motor keeps the ball at its idle floor`() {
        val e = GameEngine(GameState(upgrades = mapOf(Upgrades.MOTOR to 1)))
        run(e, 10.0)
        assertEquals(Stats.motorRpm(1), e.view().rpm, 1e-3)
        assertTrue(e.state.points > 0.0)
    }

    @Test
    fun `motor keeps the direction the ball already had`() {
        val e = GameEngine(GameState(omega = -1.0, upgrades = mapOf(Upgrades.MOTOR to 2)))
        run(e, 10.0)
        assertTrue(e.state.omega < 0.0)
        assertEquals(Stats.motorRpm(2), e.view().rpm, 1e-3)
    }

    @Test
    fun `rpm never exceeds the cooling cap without turbo`() {
        val e = GameEngine()
        spin(e, 5.0, rpmToOmega(5000.0))
        assertTrue(e.view().rpm <= Stats.rpmCap(0) + 1e-6)
        val cooled = GameEngine(GameState(upgrades = mapOf(Upgrades.COOLING to 2, Upgrades.GEAR to 20)))
        spin(cooled, 5.0, rpmToOmega(5000.0))
        assertTrue(cooled.view().rpm > Stats.rpmCap(0))
        assertTrue(cooled.view().rpm <= Stats.rpmCap(2) + 1e-6)
    }

    // ------------------------------------------------------------------
    // Turbo
    // ------------------------------------------------------------------

    @Test
    fun `holding turbo adds momentum fast, raises the cap and drains the charge`() {
        val e = GameEngine(GameState(omega = rpmToOmega(100.0)))
        e.setTurbo(true)
        run(e, 1.0)
        assertTrue(e.view().rpm > 400.0, "turbo should add hundreds of RPM in a second, got ${e.view().rpm}")
        assertTrue(e.view().turboBoosting)
        assertTrue(e.state.turboCharge < 1.0 && e.state.turboCharge > 0.0)
        run(e, 1.0) // 2 s held in total, under the 2.5 s stock capacity
        val rpm = e.view().rpm
        assertTrue(rpm > Stats.rpmCap(0), "boost should exceed the base cap, got $rpm")
        assertTrue(rpm <= Stats.rpmCap(0) * Stats.turboCapMultiplier(0) + 1e-6)
        assertEquals(Stats.rpmCap(0) * Stats.turboCapMultiplier(0), e.view().rpmCap, 1e-9)
    }

    @Test
    fun `turbo income multiplier applies only while boosting`() {
        val e = GameEngine(GameState(omega = rpmToOmega(60.0)))
        val idle = e.view().pointsPerSecond
        e.setTurbo(true)
        assertEquals(idle * Stats.TURBO_INCOME_MULT, e.view().pointsPerSecond, 1e-9)
        e.setTurbo(false)
        assertEquals(idle, e.view().pointsPerSecond, 1e-9)
    }

    @Test
    fun `holding turbo after it is empty slows the ball`() {
        val e = GameEngine(GameState(omega = rpmToOmega(100.0), upgrades = mapOf(Upgrades.BEARINGS to 30, Upgrades.FLYWHEEL to 20)))
        e.setTurbo(true)
        run(e, Stats.turboCapacity(0) + 0.5)
        assertEquals(0.0, e.state.turboCharge, 1e-9)
        assertTrue(e.view().turboOverheating)
        val peak = e.view().rpm
        run(e, 1.0)
        assertTrue(e.view().rpm < peak - 200, "overheating should brake hard: $peak -> ${e.view().rpm}")
        assertEquals(0.0, e.state.turboCharge, 1e-9, "charge must not refill while held")
    }

    @Test
    fun `releasing turbo refills the charge and bleeds speed back to the cap`() {
        val e = GameEngine(GameState(omega = rpmToOmega(100.0), upgrades = mapOf(Upgrades.BEARINGS to 30, Upgrades.FLYWHEEL to 20)))
        e.setTurbo(true)
        run(e, 2.0)
        assertTrue(e.view().rpm > Stats.rpmCap(0))
        e.setTurbo(false)
        run(e, 0.1)
        assertTrue(e.view().rpm > Stats.rpmCap(0), "speed above the cap should bleed off, not snap")
        run(e, 3.0)
        assertTrue(e.view().rpm <= Stats.rpmCap(0) + 1e-6)
        run(e, Stats.turboRefill(0))
        assertEquals(1.0, e.state.turboCharge, 1e-6)
    }

    @Test
    fun `turbo upgrade lengthens the boost and shortens the refill`() {
        assertTrue(Stats.turboCapacity(3) > Stats.turboCapacity(0))
        assertTrue(Stats.turboRefill(3) < Stats.turboRefill(0))
        assertTrue(Stats.turboCapMultiplier(3) > Stats.turboCapMultiplier(0))
    }

    // ------------------------------------------------------------------
    // Economy
    // ------------------------------------------------------------------

    @Test
    fun `income scales with rpm and points per rev`() {
        val v = GameEngine(GameState(omega = rpmToOmega(60.0))).view()
        assertEquals(1.0, v.pointsPerRev, 1e-9)
        assertEquals(1.0, v.pointsPerSecond, 1e-9) // 60 RPM = 1 rev/s
        val counter = GameEngine(GameState(omega = rpmToOmega(60.0), upgrades = mapOf(Upgrades.COUNTER to 3))).view()
        assertEquals(Stats.pointsPerRev(3), counter.pointsPerSecond, 1e-9)
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
        val e = GameEngine(GameState(upgrades = mapOf(Upgrades.RESONANCE to 2, Upgrades.MOTOR to 12)))
        run(e, 30.0)
        assertTrue(e.view().rpm >= Stats.RESONANCE_THRESHOLD_RPM, "motor 12 should exceed the threshold")
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

    // ------------------------------------------------------------------
    // Cosmetics
    // ------------------------------------------------------------------

    @Test
    fun `cosmetics are bought with points, equipped, and survive prestige`() {
        val ocean = Cosmetics.get("body_ocean")
        val e = GameEngine(GameState(points = ocean.cost - 1))
        assertFalse(e.buyCosmetic("body_ocean"))
        e.load(e.state.copy(points = ocean.cost + 5))
        assertTrue(e.buyCosmetic("body_ocean"))
        assertEquals(5.0, e.state.points, 1e-9)
        assertEquals(ocean.color, e.view().bodyColor)
        assertFalse(e.buyCosmetic("body_ocean"), "already owned")
        assertTrue(e.equipCosmetic("body_red"), "defaults are always owned")
        assertEquals(Cosmetics.get("body_red").color, e.view().bodyColor)
        assertFalse(e.equipCosmetic("cap_onyx"), "not owned")
        assertFalse(e.equipCosmetic("nope"))

        e.load(e.state.copy(pointsThisRun = 1e6))
        assertTrue(e.prestige())
        assertTrue("body_ocean" in e.state.ownedCosmetics)
    }

    @Test
    fun `buying three looks unlocks the stylist achievement`() {
        val e = GameEngine(GameState(points = 1e9))
        e.buyCosmetic("body_ocean"); e.buyCosmetic("body_mint")
        assertFalse("stylist" in e.state.achievements)
        e.buyCosmetic("cap_navy")
        assertTrue("stylist" in e.state.achievements)
    }

    // ------------------------------------------------------------------
    // Prestige, zen, achievements
    // ------------------------------------------------------------------

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
        spin(e, 3.0, rpmToOmega(100.0))
        assertTrue("first_spin" in e.state.achievements)
        val zenAfter = e.state.zen
        assertTrue(zenAfter >= 1)
        spin(e, 2.0, rpmToOmega(100.0))
        assertEquals(zenAfter, e.state.zen, "achievement must not be granted twice")
    }

    // ------------------------------------------------------------------
    // Offline, robustness
    // ------------------------------------------------------------------

    @Test
    fun `offline progress credits motor income with efficiency and cap`() {
        val start = 1_000_000L
        val e = GameEngine(GameState(upgrades = mapOf(Upgrades.MOTOR to 3, Upgrades.GYRO to 1), lastSavedEpochMs = start, turboCharge = 0.2, omega = -5.0))
        val hours = 10.0
        val report = e.applyOfflineProgress(start + (hours * 3600 * 1000).toLong())
        assertNotNull(report)
        assertEquals(hours * 3600, report.secondsAway, 1e-6)
        assertEquals(Stats.offlineCapHours(1) * 3600.0, report.secondsCredited, 1e-6)
        val expectedRev = rpmToOmega(Stats.motorRpm(3)) / TWO_PI * report.secondsCredited
        assertEquals(expectedRev * Stats.pointsPerRev(0) * Stats.offlineEfficiency(1), report.pointsEarned, 1e-6)
        assertEquals(report.pointsEarned, e.state.points, 1e-9)
        assertEquals(Stats.motorRpm(3), e.view().rpm, 1e-9)
        assertTrue(e.state.omega < 0.0, "direction is kept")
        assertEquals(1.0, e.state.turboCharge, "turbo refills while away")
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
        assertTrue(abs(e.state.omega) >= 0.0)
        assertTrue(e.state.omega.isFinite())
        assertTrue(e.state.points > 0.0)
        val nan = GameEngine()
        nan.tick(Double.NaN)
        nan.tick(-1.0)
        assertEquals(0.0, nan.state.playTimeSeconds)
    }

    @Test
    fun `view reports effect tiers from rpm`() {
        assertEquals(0, GameEngine(GameState(omega = rpmToOmega(100.0))).view().fxTier)
        assertEquals(1, GameEngine(GameState(omega = rpmToOmega(400.0))).view().fxTier)
        assertEquals(4, GameEngine(GameState(omega = -rpmToOmega(5000.0))).view().fxTier)
        val v = GameEngine(GameState(omega = rpmToOmega(625.0))).view()
        assertEquals(0.5, v.fxTierProgress, 1e-9)
    }
}
