package com.aeunal.stressball.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.PI
import kotlin.math.atan2

/**
 * Turns a finger on the ball into engine input.
 *
 * The finger "grips" the ball from the moment it touches down: circling it
 * spins the ball, and keeping it still brakes. [onMove] receives the finger's
 * angular velocity around the centre of the composable in rad/s, positive for
 * a clockwise circle on screen. [onUp] always follows [onDown], including when
 * the gesture is cancelled.
 */
fun Modifier.spinGesture(
    onDown: () -> Unit,
    onMove: (omegaRadPerSec: Double) -> Unit,
    onUp: () -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val center = Offset(size.width / 2f, size.height / 2f)
        // Ignore samples too close to the centre where the angle is ill-defined.
        val deadZone = minOf(size.width, size.height) * 0.06f
        var prevPos = down.position
        var prevTime = down.uptimeMillis
        down.consume()
        onDown()
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val pos = change.position
                val time = change.uptimeMillis
                val dtMs = time - prevTime
                if (dtMs > 0 && pos != prevPos) {
                    val prev = prevPos - center
                    val cur = pos - center
                    if (prev.getDistance() > deadZone && cur.getDistance() > deadZone) {
                        var dTheta = atan2(cur.y, cur.x) - atan2(prev.y, prev.x)
                        if (dTheta > PI) dTheta -= 2f * PI.toFloat()
                        if (dTheta < -PI) dTheta += 2f * PI.toFloat()
                        onMove(dTheta.toDouble() / (dtMs / 1000.0))
                    }
                    prevPos = pos
                    prevTime = time
                }
                change.consume()
            }
        } finally {
            onUp()
        }
    }
}
