package com.aeunal.stressball.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpinInputTest {
    private val radius = 100.0
    private val dt = 1.0 / 120.0

    /** Runs a path of points through the sampler, returning every sample after the first two. */
    private fun run(points: List<Pair<Double, Double>>): List<FingerSample> {
        val out = ArrayList<FingerSample>()
        var prev = FingerSample.NONE
        for (i in 1 until points.size) {
            val (x0, y0) = points[i - 1]
            val (x1, y1) = points[i]
            val s = SpinInput.sample(x0, y0, x1, y1, prev.vx, prev.vy, dt, radius)
            if (i >= 2) out += s
            prev = s
        }
        return out
    }

    @Test
    fun `a clockwise circle around the centre is a pure twist`() {
        val omega = 2.0 * 2.0 * PI // 2 rev/s clockwise on screen (y down)
        val points = (0..60).map { i ->
            val a = omega * i * dt
            0.7 * radius * cos(a) to 0.7 * radius * sin(a)
        }
        for (s in run(points)) {
            assertEquals(omega, s.twistOmega, 0.05)
            assertTrue(s.circularity > 0.95, "circularity ${s.circularity}")
        }
    }

    @Test
    fun `a counter-clockwise circle has negative twist and still reads as a circle`() {
        val omega = -6.0
        val points = (0..60).map { i ->
            val a = omega * i * dt
            0.8 * radius * cos(a) to 0.8 * radius * sin(a)
        }
        for (s in run(points)) {
            assertEquals(omega, s.twistOmega, 0.05)
            assertTrue(s.circularity > 0.95)
        }
    }

    @Test
    fun `a straight swipe across the upper face is a drag, not a twist`() {
        // Moving right at 300 px/s along y = -0.5 R: sweeps angle clockwise, but the heading never turns.
        val v = 300.0
        val points = (0..40).map { i -> (-0.8 * radius + v * i * dt) to (-0.5 * radius) }
        for (s in run(points)) {
            assertTrue(s.twistOmega > 0.0, "above the centre a rightward stroke sweeps clockwise")
            assertEquals(0.0, s.circularity, 1e-9)
            assertTrue(s.dragOmega < 0.0, "front face moving right is counter-clockwise from above")
        }
        // At the centre line the implied speed is v / R; higher on the face the surface moves slower.
        val centreStroke = run((0..40).map { i -> (-0.8 * radius + v * i * dt) to 0.0 })
        assertEquals(-v / radius, centreStroke.last().dragOmega, 0.1)
        assertTrue(kotlin.math.abs(run(points).last().dragOmega) > v / radius)
    }

    @Test
    fun `a swipe through the centre ignores the dead zone for twist and keeps the drag`() {
        val v = 400.0
        val points = (0..30).map { i -> (-0.2 * radius + v * i * dt) to 0.0 }
        val mid = run(points).first { kotlin.math.abs(it.vx) > 0 && it.twistOmega == 0.0 }
        assertEquals(0.0, mid.circularity)
        assertEquals(-v / radius, mid.dragOmega, 0.1)
    }

    @Test
    fun `off the ball there is no drag but twisting still works`() {
        val omega = 5.0
        val points = (0..40).map { i ->
            val a = omega * i * dt
            1.6 * radius * cos(a) to 1.6 * radius * sin(a)
        }
        for (s in run(points)) {
            assertEquals(0.0, s.dragOmega)
            assertEquals(omega, s.twistOmega, 0.05)
            assertTrue(s.circularity > 0.95)
        }
    }

    @Test
    fun `a contact near the rim cannot demand infinite speed`() {
        val s = SpinInput.sample(0.0, -0.99 * radius, 20.0, -0.99 * radius, 0.0, 0.0, dt, radius)
        val impliedAtCentre = -(20.0 / dt) / radius
        assertTrue(kotlin.math.abs(s.dragOmega) <= kotlin.math.abs(impliedAtCentre) / SpinInput.MIN_DEPTH + 1e-6)
    }

    @Test
    fun `degenerate input is harmless`() {
        assertEquals(FingerSample.NONE, SpinInput.sample(1.0, 1.0, 2.0, 2.0, 0.0, 0.0, 0.0, radius))
        assertEquals(FingerSample.NONE, SpinInput.sample(1.0, 1.0, 2.0, 2.0, 0.0, 0.0, dt, 0.0))
    }
}
