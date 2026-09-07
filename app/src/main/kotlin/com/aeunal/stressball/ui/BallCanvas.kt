package com.aeunal.stressball.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.aeunal.stressball.core.GameView
import com.aeunal.stressball.ui.theme.BallColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws the stress ball: a red rubber sphere split into petals, with a green
 * gear cap on top that turns with the ball. The sphere spins about its
 * vertical axis, so the seams sweep sideways across the face and the cap's
 * teeth march around the rim.
 */
@Composable
fun BallCanvas(
    view: GameView,
    direction: Float,
    modifier: Modifier = Modifier,
) {
    var angle by remember { mutableFloatStateOf(0f) }
    val omega by rememberUpdatedState(view.state.omega)
    val dir by rememberUpdatedState(direction)

    // Integrate the engine's angular velocity into a display angle every frame.
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = (now - last) / 1e9
                    angle = ((angle + (omega * dir * dt).toFloat()) % (2f * PI.toFloat()))
                }
                last = now
            }
        }
    }

    val petalOpen by animateFloatAsState(
        targetValue = if (view.petalsOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "petals",
    )
    val overdrive by animateFloatAsState(
        targetValue = if (view.overdriveActive) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "overdrive",
    )
    val heat = view.heat.toFloat()
    val speed = (view.rpm / 600.0).coerceIn(0.0, 1.0).toFloat()

    Canvas(modifier = modifier) {
        val r = min(size.width, size.height) * 0.36f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawGlow(c, r, heat, overdrive)
        drawShadow(c, r)
        drawStreaks(c, r, angle, speed)
        drawBody(c, r)
        drawMeridians(c, r, angle)
        drawEquator(c, r, petalOpen)
        drawBottomRing(c, r)
        drawCap(c, r, angle)
    }
}

private fun DrawScope.drawGlow(c: Offset, r: Float, heat: Float, overdrive: Float) {
    val heatAlpha = ((heat - 0.6f) / 0.4f).coerceIn(0f, 1f) * 0.55f
    if (heatAlpha > 0f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(BallColors.Heat.copy(alpha = heatAlpha), Color.Transparent),
                center = c,
                radius = r * 1.6f,
            ),
            radius = r * 1.6f,
            center = c,
        )
    }
    if (overdrive > 0f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, BallColors.Overdrive.copy(alpha = 0.5f * overdrive), Color.Transparent),
                center = c,
                radius = r * 1.45f,
            ),
            radius = r * 1.45f,
            center = c,
        )
    }
}

private fun DrawScope.drawShadow(c: Offset, r: Float) {
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent),
            center = Offset(c.x, c.y + r * 1.15f),
            radius = r * 0.9f,
        ),
        topLeft = Offset(c.x - r * 0.9f, c.y + r * 0.95f),
        size = Size(r * 1.8f, r * 0.4f),
    )
}

private fun DrawScope.drawStreaks(c: Offset, r: Float, angle: Float, speed: Float) {
    if (speed < 0.15f) return
    val alpha = (speed - 0.15f) / 0.85f
    val stroke = Stroke(width = r * 0.05f)
    for (i in 0 until 4) {
        val start = Math.toDegrees((angle * 0.5f + i * PI / 2).toDouble()).toFloat()
        drawArc(
            color = Color.White.copy(alpha = 0.18f * alpha),
            startAngle = start,
            sweepAngle = 40f + 50f * alpha,
            useCenter = false,
            topLeft = Offset(c.x - r * 1.12f, c.y - r * 1.12f),
            size = Size(r * 2.24f, r * 2.24f),
            style = stroke,
        )
    }
}

private fun DrawScope.drawBody(c: Offset, r: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(BallColors.RedLight, BallColors.Red, BallColors.RedDark),
            center = Offset(c.x - r * 0.35f, c.y - r * 0.4f),
            radius = r * 1.5f,
        ),
        radius = r,
        center = c,
    )
    // Rim darkening for a rounder look.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.35f)),
            center = c,
            radius = r,
        ),
        radius = r,
        center = c,
    )
}

/** Vertical seams between petals, moving as the ball rotates about its vertical axis. */
private fun DrawScope.drawMeridians(c: Offset, r: Float, angle: Float) {
    val seams = 6
    val stroke = Stroke(width = r * 0.035f)
    for (k in 0 until seams) {
        val phi = angle + k * (2f * PI.toFloat() / seams)
        val depth = cos(phi)            // > 0: on the front face
        if (depth <= 0.05f) continue
        val a = abs(sin(phi)) * r       // horizontal semi-axis of the meridian ellipse
        val alpha = (depth * 0.9f).coerceIn(0f, 0.9f)
        val startAngle = if (sin(phi) >= 0f) -90f else 90f
        drawArc(
            color = BallColors.Seam.copy(alpha = alpha),
            startAngle = startAngle,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(c.x - a, c.y - r),
            size = Size(2f * a, 2f * r),
            style = stroke,
        )
    }
}

/** The horizontal split. When petals open, the gap widens and glows green from inside. */
private fun DrawScope.drawEquator(c: Offset, r: Float, open: Float) {
    val gap = r * (0.03f + 0.16f * open)
    val y = c.y + r * 0.05f
    if (open > 0f) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(BallColors.GreenDark, BallColors.GreenLight.copy(alpha = 0.9f), BallColors.GreenDark),
                startY = y - gap,
                endY = y + gap,
            ),
            topLeft = Offset(c.x - r * 0.98f, y - gap),
            size = Size(r * 1.96f, 2f * gap),
        )
    }
    drawLine(
        color = BallColors.Seam,
        start = Offset(c.x - r * 0.985f, y - gap),
        end = Offset(c.x + r * 0.985f, y - gap),
        strokeWidth = r * 0.03f,
    )
    drawLine(
        color = BallColors.Seam,
        start = Offset(c.x - r * 0.985f, y + gap),
        end = Offset(c.x + r * 0.985f, y + gap),
        strokeWidth = r * 0.03f,
    )
}

private fun DrawScope.drawBottomRing(c: Offset, r: Float) {
    drawOval(
        color = BallColors.GreenDark,
        topLeft = Offset(c.x - r * 0.28f, c.y + r * 0.84f),
        size = Size(r * 0.56f, r * 0.22f),
    )
}

/** Green gear cap on top, seen at an angle. Teeth march around the rim as the ball spins. */
private fun DrawScope.drawCap(c: Offset, r: Float, angle: Float) {
    val capY = c.y - r * 0.9f
    val rx = r * 0.32f
    val ry = r * 0.12f
    // Neck
    drawRect(
        color = BallColors.GreenDark,
        topLeft = Offset(c.x - rx * 0.8f, capY - ry * 0.4f),
        size = Size(rx * 1.6f, ry * 1.6f),
    )
    // Teeth (six around the rim). Draw back teeth first so the disc covers them.
    val teeth = 6
    val toothW = rx * 0.34f
    val toothH = ry * 1.5f
    for (pass in 0..1) {
        for (k in 0 until teeth) {
            val t = angle + k * (2f * PI.toFloat() / teeth)
            val depth = sin(t)                       // > 0: front of the cap
            val isFront = depth >= 0f
            if ((pass == 0) == isFront) continue
            val x = c.x + rx * cos(t)
            val y = capY + ry * depth
            val shade = if (isFront) BallColors.GreenLight else BallColors.Green
            drawRect(
                color = shade,
                topLeft = Offset(x - toothW / 2f, y - toothH),
                size = Size(toothW, toothH * 1.3f),
            )
        }
        if (pass == 0) {
            // Disc between back and front teeth.
            drawOval(
                brush = Brush.verticalGradient(
                    colors = listOf(BallColors.GreenLight, BallColors.Green),
                    startY = capY - ry,
                    endY = capY + ry,
                ),
                topLeft = Offset(c.x - rx, capY - ry),
                size = Size(2f * rx, 2f * ry),
            )
            drawOval(
                color = BallColors.GreenDark,
                topLeft = Offset(c.x - rx * 0.45f, capY - ry * 0.45f),
                size = Size(rx * 0.9f, ry * 0.9f),
            )
            // A single marker dot on the disc so rotation is readable even at low RPM.
            val m = angle
            drawCircle(
                color = BallColors.RedLight,
                radius = rx * 0.08f,
                center = Offset(c.x + rx * 0.7f * cos(m), capY + ry * 0.7f * sin(m)),
            )
        }
    }
}
