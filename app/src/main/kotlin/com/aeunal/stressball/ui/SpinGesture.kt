package com.aeunal.stressball.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import com.aeunal.stressball.core.FingerSample
import com.aeunal.stressball.core.SpinInput

/**
 * Touch handling for the ball.
 *
 * One finger grips the ball: circling twists it, stroking across the face
 * rolls it, holding still brakes it. [onMove] receives each motion sample
 * already interpreted by [SpinInput]; [onUp] always follows [onDown].
 *
 * Two fingers squeeze it: [onPinch] reports how far the fingers have closed
 * relative to where they started (0 = not yet, 1 = to half the starting
 * distance), which the engine treats as the turbo. Squeezing needs an actual
 * pinching motion, so a second finger that merely rests on the screen does
 * nothing. The moment a second finger lands the grip is released, and the
 * gesture stays in pinch mode until every finger lifts, so a lingering
 * finger after a pinch never brakes the ball by accident.
 */
fun Modifier.spinGesture(
    ballRadius: () -> Float,
    onDown: () -> Unit,
    onMove: (FingerSample) -> Unit,
    onUp: () -> Unit,
    onPinch: (weight: Float) -> Unit,
    onPinchEnd: () -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = false)
        val center = Offset(size.width / 2f, size.height / 2f)
        var gripping = true
        var pinching = false
        var pinchStartDistance = 0f
        var prevPos = first.position
        var prevTime = first.uptimeMillis
        var prev = FingerSample.NONE
        var gripId = first.id
        first.consume()
        onDown()
        try {
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break

                if (pressed.size >= 2) {
                    val a = pressed[0].position
                    val b = pressed[1].position
                    val distance = (a - b).getDistance()
                    if (!pinching) {
                        pinching = true
                        pinchStartDistance = distance.coerceAtLeast(1f)
                        if (gripping) {
                            gripping = false
                            onUp()
                        }
                    }
                    val closed = (pinchStartDistance - distance) / (PINCH_FULL_FRACTION * pinchStartDistance)
                    onPinch(closed.coerceIn(0f, 1f))
                } else if (pinching) {
                    // Down to one finger after a pinch: end the squeeze, ignore the leftover finger.
                    pinching = false
                    onPinchEnd()
                } else if (gripping) {
                    val change = pressed.firstOrNull { it.id == gripId } ?: pressed[0].also { gripId = it.id }
                    handleGripMove(change, center, ballRadius(), prevPos, prevTime, prev)?.let { (sample, pos, time) ->
                        onMove(sample)
                        prev = sample
                        prevPos = pos
                        prevTime = time
                    }
                }
                pressed.forEach { it.consume() }
            }
        } finally {
            if (pinching) onPinchEnd()
            if (gripping) onUp()
        }
    }
}

/** Fraction of the starting finger distance that counts as a full squeeze. */
private const val PINCH_FULL_FRACTION = 0.5f

private data class GripStep(val sample: FingerSample, val pos: Offset, val time: Long)

private fun handleGripMove(
    change: PointerInputChange,
    center: Offset,
    radius: Float,
    prevPos: Offset,
    prevTime: Long,
    prev: FingerSample,
): GripStep? {
    val pos = change.position
    val time = change.uptimeMillis
    val dtMs = time - prevTime
    if (dtMs <= 0 || pos == prevPos || radius <= 0f) return null
    val sample = SpinInput.sample(
        x0 = (prevPos.x - center.x).toDouble(), y0 = (prevPos.y - center.y).toDouble(),
        x1 = (pos.x - center.x).toDouble(), y1 = (pos.y - center.y).toDouble(),
        vx0 = prev.vx, vy0 = prev.vy,
        dt = dtMs / 1000.0,
        radius = radius.toDouble(),
    )
    return GripStep(sample, pos, time)
}
