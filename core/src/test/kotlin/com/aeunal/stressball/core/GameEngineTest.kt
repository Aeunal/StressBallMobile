package com.aeunal.stressball.core

import com.aeunal.stressball.core.TestStates.oiled
import com.aeunal.stressball.core.TestStates.single
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

    /** Circles the ball from the top view for [seconds]. */
    private fun twist(engine: GameEngine, seconds: Double, fingerOmega: Double) {
        engine.setFinger(true, twistOmega = fingerOmega)
        run(engine, seconds)
        engine.setFinger(false)
    }

    private fun top(omega: Double = 0.0, upgrades: Map<String, Int> = emptyMap()) =
        GameEngine(single(omega = omega, upgrades = upgrades, topView = true))

    // ------------------------------------------------------------------
    // Rest and friction
    // ------------------------------------------------------------------

    @Test
    fun `ball at rest stays at rest and earns nothing`() {
        val e = GameEngine(single())
        run(e, 5.0)
        assertEquals(0.0, e.active.omega)
        assertEquals(0.0, e.state.points)
        assertEquals(5.0, e.state.playTimeSeconds, 0.05)
    }

    @Test
    fun `a stock ball bleeds speed quickly`() {
        val e = GameEngine(single(omega = rpmToOmega(120.0)))
        run(e, 3.0)
        assertTrue(e.view().rpm < 40.0, "stock ball should lose most of 120 RPM in 3 s, has ${e.view().rpm}")
        run(e, 30.0)
        assertEquals(0.0, e.active.omega, 1e-9)
    }

    @Test
    fun `oiled bearings and a flywheel keep it spinning far longer`() {
        val stock = GameEngine(single(omega = rpmToOmega(300.0)))
        val tuned = GameEngine(single(omega = rpmToOmega(300.0), upgrades = mapOf(Upgrades.BEARINGS to 8, Upgrades.FLYWHEEL to 4)))
        run(stock, 10.0)
        run(tuned, 10.0)
        assertTrue(tuned.view().rpm > 3 * stock.view().rpm + 50, "tuned ${tuned.view().rpm} vs stock ${stock.view().rpm}")
    }

    @Test
    fun `every bearings level is felt`() {
        var previous = Double.MAX_VALUE
        for (level in 0..5) {
            val e = GameEngine(single(omega = rpmToOmega(300.0), upgrades = mapOf(Upgrades.BEARINGS to level)))
            run(e, 1.0)
            val lost = 300.0 - e.view().rpm
            assertTrue(lost < previous * 0.9, "level $level should shed at least 10% less speed than level ${level - 1}")
            previous = lost
        }
    }

    @Test
    fun `air drag is the wall at very high speed and the aero shell lowers it`() {
        val slow = GameEngine(single(omega = rpmToOmega(400.0), upgrades = oiled))
        run(slow, 1.0)
        assertTrue(slow.view().rpm > 395.0, "air drag is negligible at 400 RPM: ${slow.view().rpm}")

        val fast = GameEngine(single(omega = rpmToOmega(12000.0), upgrades = oiled + (Upgrades.COOLING to 30)))
        val aero = GameEngine(single(omega = rpmToOmega(12000.0), upgrades = oiled + (Upgrades.COOLING to 30) + (Upgrades.AERO to 10)))
        run(fast, 2.0); run(aero, 2.0)
        assertTrue(fast.view().rpm < 11000.0, "air drag bites at 12,000 RPM: ${fast.view().rpm}")
        assertTrue(aero.view().rpm > fast.view().rpm + 200, "aero ${aero.view().rpm} vs stock ${fast.view().rpm}")
    }

    // ------------------------------------------------------------------
    // Finger, top view (twist)
    // ------------------------------------------------------------------

    @Test
    fun `a fast finger circle spins the ball up towards the geared speed but never past it`() {
        val e = top()
        val finger = rpmToOmega(120.0)
        twist(e, 3.0, finger)
        val rpm = e.view().rpm
        assertTrue(rpm > 90.0, "expected close to 120 RPM, got $rpm")
        assertTrue(rpm <= 120.0 + 1e-6, "must not exceed finger speed, got $rpm")
        assertTrue(e.state.points > 0.0)
        assertEquals(1, e.view().direction)
    }

    @Test
    fun `circling does nothing from the side view, swiping does nothing from the top`() {
        val side = GameEngine(single())
        side.setFinger(true, twistOmega = rpmToOmega(120.0))
        run(side, 2.0)
        assertEquals(0.0, side.active.omega, 1e-9)

        val above = top()
        above.setFinger(true, dragOmega = -rpmToOmega(120.0))
        run(above, 2.0)
        assertEquals(0.0, above.active.omega, 1e-9)
    }

    @Test
    fun `a slow turn moves the ball one-to-one even with a tall gear`() {
        val e = top(upgrades = mapOf(Upgrades.GEAR to 6))
        val finger = 2.0
        e.setFinger(true, twistOmega = finger)
        run(e, 2.0)
        assertEquals(finger, e.active.omega, 0.25)
        e.setFinger(true, twistOmega = -finger)
        run(e, 2.0)
        assertEquals(-finger, e.active.omega, 0.25)
    }

    @Test
    fun `a fast circle engages the gear`() {
        val plain = top()
        val geared = top(upgrades = mapOf(Upgrades.GEAR to 2))
        val finger = rpmToOmega(100.0)
        twist(plain, 4.0, finger)
        twist(geared, 4.0, finger)
        assertTrue(geared.view().rpm > plain.view().rpm * 1.5, "geared ${geared.view().rpm} vs plain ${plain.view().rpm}")
    }

    @Test
    fun `holding the ball still brakes it to a stop`() {
        val e = GameEngine(single(omega = rpmToOmega(400.0), upgrades = oiled))
        run(e, 1.0)
        assertTrue(e.view().rpm > 350.0, "tuned ball should barely slow on its own")
        e.setFinger(true)
        run(e, 2.0)
        assertTrue(e.view().rpm < 100.0, "holding should brake hard, still at ${e.view().rpm}")
        run(e, 3.0)
        assertEquals(0.0, e.active.omega, 1e-9)
    }

    @Test
    fun `grip tape brakes and accelerates harder`() {
        val weak = GameEngine(single(omega = rpmToOmega(400.0)))
        val strong = GameEngine(single(omega = rpmToOmega(400.0), upgrades = mapOf(Upgrades.GRIP to 5)))
        weak.setFinger(true); strong.setFinger(true)
        run(weak, 0.5); run(strong, 0.5)
        assertTrue(strong.view().rpm < weak.view().rpm - 50)

        val weakUp = top(); val strongUp = top(upgrades = mapOf(Upgrades.GRIP to 5))
        weakUp.setFinger(true, twistOmega = rpmToOmega(120.0)); strongUp.setFinger(true, twistOmega = rpmToOmega(120.0))
        run(weakUp, 0.25); run(strongUp, 0.25)
        assertTrue(strongUp.view().rpm > weakUp.view().rpm + 20)
    }

    @Test
    fun `finger can reverse the spin direction`() {
        val e = top(omega = rpmToOmega(60.0))
        twist(e, 3.0, -rpmToOmega(120.0))
        assertTrue(e.active.omega < 0.0)
        assertEquals(-1, e.view().direction)
        assertTrue(e.view().rpm > 60.0, "income and rpm are unsigned")
    }

    @Test
    fun `the kinetic harvester turns finger braking into points`() {
        val plain = GameEngine(single(omega = rpmToOmega(600.0), upgrades = oiled + (Upgrades.COOLING to 2)))
        val kers = GameEngine(single(omega = rpmToOmega(600.0), upgrades = oiled + (Upgrades.COOLING to 2) + (Upgrades.KERS to 10)))
        plain.setFinger(true); kers.setFinger(true)
        run(plain, 3.0); run(kers, 3.0)
        assertTrue(kers.state.points > plain.state.points * 1.5, "kers ${kers.state.points} vs plain ${plain.state.points}")
    }

    // ------------------------------------------------------------------
    // Swipes, side view (drag)
    // ------------------------------------------------------------------

    @Test
    fun `swiping with the spin speeds it up and against it slows it down`() {
        val with = GameEngine(single(omega = -10.0, upgrades = oiled))
        with.setFinger(true, dragOmega = -20.0)
        run(with, 1.0)
        assertTrue(with.active.omega < -15.0, "faster swipe in the same direction adds speed: ${with.active.omega}")

        val against = GameEngine(single(omega = -10.0, upgrades = oiled))
        against.setFinger(true, dragOmega = 20.0)
        run(against, 1.0)
        assertTrue(against.active.omega > 0.0, "swipe against the spin reverses it: ${against.active.omega}")
    }

    @Test
    fun `a swipe slower than the ball brakes unless the tread over-rolls it`() {
        val stock = GameEngine(single(omega = -20.0, upgrades = oiled))
        stock.setFinger(true, dragOmega = -12.0)
        run(stock, 1.0)
        assertTrue(abs(stock.active.omega) < 13.0, "slow swipe drags the ball down to its own speed: ${stock.active.omega}")

        val tread = GameEngine(single(omega = -20.0, upgrades = oiled + (Upgrades.TRACTION to 4)))
        tread.setFinger(true, dragOmega = -12.0)
        run(tread, 1.0)
        assertEquals(-12.0 * Stats.tractionRatio(4), tread.active.omega, 0.5, "tread multiplies the implied speed")
        assertTrue(abs(tread.active.omega) > 20.0, "so the same slow swipe now speeds it up")
    }

    @Test
    fun `top view needs the gimbal mount`() {
        val e = GameEngine(single())
        assertFalse(e.setTopView(true))
        assertFalse(e.active.topView)
        e.load(single(points = 1e6))
        assertTrue(e.buy(Upgrades.GIMBAL))
        assertTrue(e.setTopView(true))
        assertTrue(e.view().topView)
        assertTrue(e.setTopView(false))
        assertFalse(e.view().topView)
    }

    // ------------------------------------------------------------------
    // Motor and cap
    // ------------------------------------------------------------------

    @Test
    fun `motor keeps the ball at its idle floor`() {
        val e = GameEngine(single(upgrades = mapOf(Upgrades.MOTOR to 1)))
        run(e, 10.0)
        assertEquals(Stats.motorRpm(1), e.view().rpm, 1e-3)
        assertTrue(e.state.points > 0.0)
    }

    @Test
    fun `motor keeps the direction the ball already had`() {
        val e = GameEngine(single(omega = -1.0, upgrades = mapOf(Upgrades.MOTOR to 2)))
        run(e, 10.0)
        assertTrue(e.active.omega < 0.0)
        assertEquals(Stats.motorRpm(2), e.view().rpm, 1e-3)
    }

    @Test
    fun `rpm never exceeds the cap without turbo, and cryo multiplies it`() {
        val e = top()
        twist(e, 5.0, rpmToOmega(5000.0))
        assertTrue(e.view().rpm <= Stats.rpmCap(0) + 1e-6)
        val cooled = top(upgrades = mapOf(Upgrades.COOLING to 2, Upgrades.GEAR to 20))
        twist(cooled, 5.0, rpmToOmega(5000.0))
        assertTrue(cooled.view().rpm > Stats.rpmCap(0))
        assertTrue(cooled.view().rpm <= Stats.rpmCap(2) + 1e-6)
        val cryo = GameEngine(single(upgrades = mapOf(Upgrades.COOLING to 2, Upgrades.CRYO to 2)))
        assertEquals(Stats.rpmCap(2) * Stats.cryoFactor(2), cryo.view().baseRpmCap, 1e-9)
    }

    // ------------------------------------------------------------------
    // Economy
    // ------------------------------------------------------------------

    @Test
    fun `income scales with rpm and points per rev`() {
        val v = GameEngine(single(omega = rpmToOmega(60.0))).view()
        assertEquals(1.0, v.pointsPerRev, 1e-9)
        assertEquals(1.0, v.pointsPerSecond, 1e-9) // 60 RPM = 1 rev/s
        val counter = GameEngine(single(omega = rpmToOmega(60.0), upgrades = mapOf(Upgrades.COUNTER to 3))).view()
        assertEquals(Stats.pointsPerRev(3), counter.pointsPerSecond, 1e-9)
    }

    @Test
    fun `petal shell multiplies income only above threshold`() {
        val below = GameEngine(single(omega = rpmToOmega(200.0), upgrades = mapOf(Upgrades.PETALS to 2))).view()
        val above = GameEngine(single(omega = rpmToOmega(400.0), upgrades = mapOf(Upgrades.PETALS to 2))).view()
        assertFalse(below.petalsOpen)
        assertTrue(above.petalsOpen)
        assertEquals(1.0, below.totalMultiplier, 1e-9)
        assertEquals(Stats.petalMultiplier(2), above.totalMultiplier, 1e-9)
    }

    @Test
    fun `resonance combo builds while fast, decays while slow, and the lock slows the decay`() {
        val e = GameEngine(single(upgrades = mapOf(Upgrades.RESONANCE to 2, Upgrades.MOTOR to 12)))
        run(e, 30.0)
        assertTrue(e.view().rpm >= Stats.RESONANCE_THRESHOLD_RPM, "motor 12 should exceed the threshold")
        assertTrue(e.active.combo > 0.5)
        assertTrue(e.active.combo <= Stats.maxCombo(2) + 1e-9)
        val combo = e.active.combo
        val plain = GameEngine(single(upgrades = mapOf(Upgrades.RESONANCE to 2)).let { s -> s.copy(balls = s.balls.map { it.copy(combo = combo) }) })
        val locked = GameEngine(single(upgrades = mapOf(Upgrades.RESONANCE to 2, Upgrades.COMBO_LOCK to 5)).let { s -> s.copy(balls = s.balls.map { it.copy(combo = combo) }) })
        run(plain, 1.0); run(locked, 1.0)
        assertTrue(locked.active.combo > plain.active.combo)
        run(plain, 10.0)
        assertEquals(0.0, plain.active.combo, 1e-9)
    }

    @Test
    fun `buying upgrades charges the geometric cost and records the investment`() {
        val def = Upgrades.get(Upgrades.GEAR)
        val e = GameEngine(single(points = def.costAt(0) + def.costAt(1)))
        assertTrue(e.buy(Upgrades.GEAR))
        assertTrue(e.buy(Upgrades.GEAR))
        assertFalse(e.buy(Upgrades.GEAR), "third level must be unaffordable")
        assertEquals(2, e.active.level(Upgrades.GEAR))
        assertEquals(0.0, e.state.points, 1e-9)
        assertEquals(def.costAt(0) + def.costAt(1), e.active.invested, 1e-9)
    }

    @Test
    fun `cannot buy beyond max level, and maxing pays gems`() {
        val def = Upgrades.get(Upgrades.GIMBAL)
        val e = GameEngine(single(points = 1e9))
        assertEquals(0L, e.state.gems)
        assertTrue(e.buy(Upgrades.GIMBAL))
        assertEquals(Stats.GEMS_PER_MAX, e.state.gems, "gimbal maxes at level ${def.maxLevel}")
        assertFalse(e.canBuy(Upgrades.GIMBAL))
        assertFalse(e.buy(Upgrades.GIMBAL))
        assertTrue("maxed" in e.state.achievements)
    }

    @Test
    fun `account upgrades live on the account, not the ball`() {
        val e = GameEngine(single(points = 1e9))
        assertTrue(e.buy(Upgrades.RACK))
        assertEquals(1, e.state.accountLevel(Upgrades.RACK))
        assertEquals(0, e.active.level(Upgrades.RACK))
        assertEquals(1, e.state.level(Upgrades.RACK))
        assertEquals(0.0, e.active.invested, "account upgrades are not part of a ball's resale value")
    }

    @Test
    fun `interest grows the balance`() {
        val e = GameEngine(single(points = 10_000.0, accountUpgrades = mapOf(Upgrades.INTEREST to 5)))
        run(e, 60.0)
        assertEquals(10_000.0 * (1.0 + 0.005), e.state.points, 10.0)
    }

    // ------------------------------------------------------------------
    // Prestige, zen, achievements
    // ------------------------------------------------------------------

    @Test
    fun `prestige converts the active ball's run into zen and resets it, keeping other balls`() {
        val e = GameEngine(single(points = 5.0, pointsThisRun = 4_000_000.0, totalPointsEarned = 4_000_000.0, upgrades = mapOf(Upgrades.GEAR to 5), omega = 10.0, bestRpm = 900.0))
        e.load(e.state.copy(points = 1e9))
        assertNotNull(e.openChest())
        e.load(e.state.copy(points = 5.0))
        assertEquals(2L, e.zenOnReset())
        assertTrue(e.prestige())
        assertTrue(e.state.zen >= 2L)
        assertEquals(1, e.active.prestigeCount)
        assertEquals(0.0, e.state.points)
        assertEquals(0.0, e.active.omega)
        assertTrue(e.active.upgrades.isEmpty())
        assertEquals(900.0, e.active.bestRpm)
        assertEquals(4_000_000.0, e.state.totalPointsEarned)
        assertEquals(2, e.state.balls.size, "the other ball survives")
        assertTrue("zen_master" in e.state.achievements)
        assertFalse(GameEngine(single(pointsThisRun = 10.0)).prestige())
    }

    @Test
    fun `zen multiplies income`() {
        val v0 = GameEngine(single(omega = rpmToOmega(60.0))).view()
        val v5 = GameEngine(single(omega = rpmToOmega(60.0), zen = 5)).view()
        assertEquals(v0.pointsPerSecond * Stats.zenMultiplier(5), v5.pointsPerSecond, 1e-9)
    }

    @Test
    fun `achievements unlock once and grant zen`() {
        val e = top()
        twist(e, 3.0, rpmToOmega(100.0))
        assertTrue("first_spin" in e.state.achievements)
        val zenAfter = e.state.zen
        assertTrue(zenAfter >= 1)
        twist(e, 2.0, rpmToOmega(100.0))
        assertEquals(zenAfter, e.state.zen, "achievement must not be granted twice")
    }

    // ------------------------------------------------------------------
    // Offline, robustness
    // ------------------------------------------------------------------

    @Test
    fun `offline progress credits motor income with efficiency and cap`() {
        val start = 1_000_000L
        val e = GameEngine(single(upgrades = mapOf(Upgrades.MOTOR to 3, Upgrades.GYRO to 1), lastSavedEpochMs = start, turboCharge = 0.2, omega = -5.0))
        val hours = 10.0
        val report = e.applyOfflineProgress(start + (hours * 3600 * 1000).toLong())
        assertNotNull(report)
        assertEquals(hours * 3600, report.secondsAway, 1e-6)
        assertEquals(Stats.offlineCapHours(1) * 3600.0, report.secondsCredited, 1e-6)
        val expectedRev = rpmToOmega(Stats.motorRpm(3)) / TWO_PI * report.secondsCredited
        assertEquals(expectedRev * Stats.pointsPerRev(0) * Stats.offlineEfficiency(1), report.pointsEarned, 1e-6)
        assertEquals(report.pointsEarned, e.state.points, 1e-9)
        assertEquals(Stats.motorRpm(3), e.view().rpm, 1e-9)
        assertTrue(e.active.omega < 0.0, "direction is kept")
        assertEquals(1.0, e.active.turboCharge, "turbo refills while away")
    }

    @Test
    fun `offline progress ignores short or missing absences`() {
        assertNull(GameEngine(single()).applyOfflineProgress(5_000L))
        val e = GameEngine(single(lastSavedEpochMs = 1_000L, upgrades = mapOf(Upgrades.MOTOR to 1)))
        assertNull(e.applyOfflineProgress(1_000L + 5_000L))
        assertEquals(0.0, e.state.points)
    }

    @Test
    fun `large tick is subdivided and stays stable`() {
        val e = GameEngine(single(omega = rpmToOmega(500.0)))
        e.tick(30.0)
        assertTrue(abs(e.active.omega) >= 0.0)
        assertTrue(e.active.omega.isFinite())
        assertTrue(e.state.points > 0.0)
        val nan = GameEngine(single())
        nan.tick(Double.NaN)
        nan.tick(-1.0)
        assertEquals(0.0, nan.state.playTimeSeconds)
    }

    @Test
    fun `view reports effect tiers from rpm`() {
        assertEquals(0, GameEngine(single(omega = rpmToOmega(100.0))).view().fxTier)
        assertEquals(1, GameEngine(single(omega = rpmToOmega(400.0))).view().fxTier)
        assertEquals(4, GameEngine(single(omega = -rpmToOmega(5000.0))).view().fxTier)
        assertEquals(7, GameEngine(single(omega = rpmToOmega(40000.0))).view().fxTier)
        val v = GameEngine(single(omega = rpmToOmega(625.0))).view()
        assertEquals(0.5, v.fxTierProgress, 1e-9)
    }
}
