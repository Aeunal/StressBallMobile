package com.aeunal.stressball.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * One finger-motion sample, already interpreted in ball terms.
 *
 * Two ways a finger can drive the ball, and both are measured every sample:
 *
 * - **Twist** ([twistOmega]): the finger's angular velocity around the ball
 *   centre, as when turning the cap seen from above. Positive is clockwise on
 *   screen, which is clockwise seen from above the cap.
 * - **Drag** ([dragOmega]): the finger pushing the front face sideways, like
 *   rolling a real ball under your thumb. The surface at the contact point
 *   moves with the finger, so the implied ball speed is `v_x / (R·z)` where
 *   `z` is the depth of the contact point on the sphere. Sign follows the
 *   surface: a rightward swipe moves the front face right, which is
 *   counter-clockwise seen from above, hence negative.
 *
 * [circularity] (0..1) says how much the motion looks like a circle around
 * the centre rather than a straight stroke, so the engine can blend the two
 * interpretations. A stroke across the face has a straight velocity heading
 * while still sweeping angle around the centre; a circle turns its heading
 * exactly as fast as it sweeps angle.
 *
 * [vx]/[vy] are the finger velocity used for this sample; feed them back as
 * the previous velocity of the next sample.
 */
data class FingerSample(
    val twistOmega: Double,
    val dragOmega: Double,
    val circularity: Double,
    val vx: Double,
    val vy: Double,
) {
    companion object {
        val NONE = FingerSample(0.0, 0.0, 0.0, 0.0, 0.0)
    }
}

object SpinInput {
    /** Inside this fraction of the radius the angle around the centre is meaningless. */
    const val DEAD_ZONE = 0.12
    /** Beyond this fraction of the radius the finger is off the ball: no surface drag. */
    const val OUTSIDE = 1.25
    /** Minimum contact depth (fraction of the radius) so a touch near the rim cannot demand infinite speed. */
    const val MIN_DEPTH = 0.4
    /** Below this speed (radii per second) the velocity heading is too noisy to measure curvature. */
    const val MIN_HEADING_SPEED = 0.3

    /**
     * Interprets a finger moving from (x0, y0) to (x1, y1) in [dt] seconds.
     * Coordinates are relative to the ball centre, in any unit as long as
     * [radius] uses the same one; y points down as on a screen.
     * [vx0]/[vy0] are the finger velocity of the previous sample (0 if none).
     */
    fun sample(
        x0: Double, y0: Double,
        x1: Double, y1: Double,
        vx0: Double, vy0: Double,
        dt: Double,
        radius: Double,
    ): FingerSample {
        if (!(dt > 0.0) || !(radius > 0.0)) return FingerSample.NONE
        val vx1 = (x1 - x0) / dt
        val vy1 = (y1 - y0) / dt

        // Twist: angle swept around the centre.
        val d0 = hypot(x0, y0)
        val d1 = hypot(x1, y1)
        val dead = DEAD_ZONE * radius
        val twistValid = d0 > dead && d1 > dead
        val twist = if (twistValid) wrapAngle(atan2(y1, x1) - atan2(y0, x0)) / dt else 0.0

        // Curvature: how fast the velocity heading turns, compared with the sweep.
        val minSpeed = MIN_HEADING_SPEED * radius
        val speed0 = hypot(vx0, vy0)
        val speed1 = hypot(vx1, vy1)
        val headingRate = if (speed0 > minSpeed && speed1 > minSpeed) {
            atan2(vx0 * vy1 - vy0 * vx1, vx0 * vx1 + vy0 * vy1) / dt
        } else {
            0.0
        }
        val circularity = if (twistValid && abs(twist) > 1e-9 && sign(headingRate) == sign(twist)) {
            min(1.0, abs(headingRate) / abs(twist))
        } else {
            0.0
        }

        // Drag: the front face moves with the finger.
        val mx = (x0 + x1) / 2.0
        val my = (y0 + y1) / 2.0
        val rho = hypot(mx, my) / radius
        val drag = if (rho > OUTSIDE) {
            0.0
        } else {
            val clamped = min(rho, 1.0)
            val depth = sqrt(max(MIN_DEPTH * MIN_DEPTH, 1.0 - clamped * clamped))
            -vx1 / (radius * depth)
        }

        return FingerSample(twist, drag, circularity, vx1, vy1)
    }

    private fun wrapAngle(a: Double): Double {
        var x = a
        while (x > Math.PI) x -= TWO_PI
        while (x < -Math.PI) x += TWO_PI
        return x
    }
}
