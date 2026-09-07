package com.aeunal.stressball.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.PI
import kotlin.math.atan2

/**
 * Turns finger circles around the centre of the composable into angular
 * velocity samples (rad/s, signed: positive = clockwise on screen).
 *
 * The narrator's idea in one gesture: keep drawing circles on the ball and it
 * keeps spinning; draw faster and it spins faster.
 */
fun Modifier.spinGesture(
    onSpin: (omegaRadPerSec: Double) -> Unit,
    onLift: () -> Unit,
): Modifier = pointerInput(Unit) {
    val center = Offset(size.width / 2f, size.height / 2f)
    // Ignore samples too close to the centre where the angle is ill-defined.
    val deadZone = minOf(size.width, size.height) * 0.06f

    detectDragGestures(
        onDragEnd = { onLift() },
        onDragCancel = { onLift() },
    ) { change, _ ->
        val prev = change.previousPosition - center
        val cur = change.position - center
        val dtMs = change.uptimeMillis - change.previousUptimeMillis
        if (dtMs > 0 && prev.getDistance() > deadZone && cur.getDistance() > deadZone) {
            var dTheta = atan2(cur.y, cur.x) - atan2(prev.y, prev.x)
            if (dTheta > PI) dTheta -= 2 * PI.toFloat()
            if (dTheta < -PI) dTheta += 2 * PI.toFloat()
            onSpin(dTheta.toDouble() / (dtMs / 1000.0))
        }
        change.consume()
    }
}
