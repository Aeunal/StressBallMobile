package com.aeunal.stressball.core

import com.aeunal.stressball.core.TestStates.single
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GarageTest {
    private val dt = 1.0 / 60.0

    private fun run(engine: GameEngine, seconds: Double) {
        var t = 0.0
        while (t < seconds) {
            engine.tick(dt)
            t += dt
        }
    }

    @Test
    fun `chests cost more each time, add a ball and stop at the garage limit`() {
        val e = GameEngine(single(points = 1e12), Random(7))
        assertEquals(Stats.chestCost(1), e.chestCost(), 1e-9)
        var spent = 0.0
        while (e.canOpenChest()) {
            val before = e.state.points
            val cost = e.chestCost()
            val ball = e.openChest()
            assertNotNull(ball)
            spent += cost
            assertEquals(before - cost, e.state.points, 1e-3)
            assertEquals(ball.id, e.state.balls.last().id)
            assertTrue(ball.typeId in BallTypes.byId)
        }
        assertEquals(Stats.MAX_BALLS, e.state.balls.size)
        assertNull(e.openChest())
        assertTrue(Stats.chestCost(2) > 2 * Stats.chestCost(1), "cost escalates steeply")
        assertEquals(Stats.MAX_BALLS, e.state.chestsOpened + 1)
        assertTrue("collector" in e.state.achievements)
        assertTrue("garage_full" in e.state.achievements)
        assertEquals(e.state.balls.map { it.id }.toSet().size, e.state.balls.size, "ids are unique")
    }

    @Test
    fun `chest odds sum to one, favour commons and improve with lucky chest`() {
        val stock = BallTypes.odds(0)
        assertEquals(1.0, stock.values.sum(), 1e-9)
        assertTrue(stock.getValue(Rarity.COMMON) > 0.4)
        assertTrue(stock.getValue(Rarity.EXOTIC) < 0.01)
        val lucky = BallTypes.odds(5)
        assertTrue(lucky.getValue(Rarity.COMMON) < stock.getValue(Rarity.COMMON))
        assertTrue(lucky.getValue(Rarity.LEGENDARY) > stock.getValue(Rarity.LEGENDARY))
        assertEquals(1.0, lucky.values.sum(), 1e-9)

        val counts = HashMap<Rarity, Int>()
        val random = Random(42)
        repeat(20_000) { counts.merge(BallTypes.roll(random, 0).rarity, 1, Int::plus) }
        assertTrue(counts.getValue(Rarity.COMMON) > counts.getValue(Rarity.RARE))
        assertTrue(counts.getValue(Rarity.RARE) > counts.getValue(Rarity.LEGENDARY))
        assertTrue(counts.getOrDefault(Rarity.EXOTIC, 0) in 30..250, "exotic ~0.5%: ${counts[Rarity.EXOTIC]}")
    }

    @Test
    fun `every rarity has balls and ids are unique`() {
        assertEquals(BallTypes.all.size, BallTypes.all.map { it.id }.toSet().size)
        for (r in Rarity.entries) assertTrue(BallTypes.ofRarity(r).isNotEmpty(), r.name)
        assertEquals(Rarity.COMMON, BallTypes.get(BallTypes.DEFAULT).rarity)
        assertEquals(BallTypes.DEFAULT, BallTypes.get("nope").id, "unknown ids fall back to the default")
    }

    @Test
    fun `switching balls plays a different one and keeps the other's state`() {
        val e = GameEngine(single(points = 1e12, omega = 10.0), Random(1))
        val second = e.openChest()!!
        assertTrue(e.switchBall(second.id))
        assertEquals(second.id, e.active.id)
        assertEquals(0.0, e.active.omega)
        assertFalse(e.switchBall(second.id), "already active")
        assertFalse(e.switchBall("nope"))
        assertTrue(e.switchBall("b1"))
        assertEquals(10.0, e.active.omega, 1e-9)
    }

    @Test
    fun `garage balls earn a rack share of their motor income while another ball is played`() {
        val e = GameEngine(single(points = 1e12), Random(1))
        val second = e.openChest()!!
        e.switchBall(second.id)
        e.load(e.state.copy(points = 1e12))
        repeat(5) { assertTrue(e.buy(Upgrades.MOTOR)) }
        e.switchBall("b1")
        val start = e.state.points
        run(e, 10.0)
        val expected = rpmToOmega(Stats.motorRpm(5)) / TWO_PI * Stats.pointsPerRev(0) *
            BallTypes.get(second.typeId).traits.income * Stats.rackEfficiency(0) * Stats.zenMultiplier(e.state.zen) * 10.0
        assertEquals(expected, e.state.points - start, expected * 0.02)
        assertEquals(expected / 10.0, e.view().garageIncomePerSecond, expected * 0.002)

        e.load(e.state.copy(accountUpgrades = mapOf(Upgrades.RACK to 7)))
        val stronger = e.view().garageIncomePerSecond
        assertTrue(stronger > expected / 10.0 * 2.0, "rack level 7 pays 100%")
    }

    @Test
    fun `selling refunds rarity value plus half the investment, never the last ball`() {
        val e = GameEngine(single(points = 1e12), Random(3))
        val second = e.openChest()!!
        e.switchBall(second.id)
        assertTrue(e.buy(Upgrades.GEAR)); assertTrue(e.buy(Upgrades.GEAR))
        val invested = e.active.invested
        assertTrue(invested > 0.0)
        val before = e.state.points
        val expected = BallTypes.get(second.typeId).rarity.sellValue + 0.5 * invested
        assertEquals(expected, e.view().balls.first { it.id == second.id }.sellValue, 1e-9)
        assertTrue(e.sellBall(second.id))
        assertEquals(before + expected, e.state.points, 1e-6)
        assertEquals("b1", e.active.id, "selling the active ball switches to another")
        assertEquals(1, e.state.balls.size)
        assertFalse(e.sellBall("b1"), "the last ball stays")
        assertTrue("trader" in e.state.achievements)
    }

    @Test
    fun `ball traits change the physics`() {
        val stock = GameEngine(single(omega = rpmToOmega(300.0)))
        val glacier = GameEngine(single(omega = rpmToOmega(300.0), typeId = "glacier"))
        run(stock, 2.0); run(glacier, 2.0)
        assertTrue(glacier.view().rpm > stock.view().rpm + 30.0, "glacier has less drag")

        val solar = GameEngine(single(omega = rpmToOmega(60.0), typeId = "solar")).view()
        assertEquals(1.5, solar.pointsPerSecond, 1e-9)
        assertEquals(Stats.rpmCap(0) * 1.2, solar.baseRpmCap, 1e-9)
        assertTrue("legendary" in GameEngine(single(typeId = "solar")).also { it.tick(dt) }.state.achievements)
    }

    @Test
    fun `offline progress includes the garage`() {
        val start = 1_000_000L
        val e = GameEngine(single(points = 1e12, lastSavedEpochMs = start), Random(1))
        val second = e.openChest()!!
        e.switchBall(second.id)
        repeat(3) { e.buy(Upgrades.MOTOR) }
        e.switchBall("b1")
        e.load(e.state.copy(points = 0.0, lastSavedEpochMs = start))
        val zenBefore = e.state.zen
        val report = e.applyOfflineProgress(start + 3600_000L)
        assertNotNull(report)
        val perSecond = rpmToOmega(Stats.motorRpm(3)) / TWO_PI * BallTypes.get(second.typeId).traits.income *
            Stats.rackEfficiency(0) * Stats.zenMultiplier(zenBefore)
        assertEquals(perSecond * 3600.0 * Stats.offlineEfficiency(0), report.pointsEarned, 1e-6)
    }
}
