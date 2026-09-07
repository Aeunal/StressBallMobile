package com.aeunal.stressball.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import com.aeunal.stressball.core.GameView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private const val TAU = (2.0 * PI).toFloat()

/**
 * Draws the stress ball: a rubber sphere split into petals with a gear cap on
 * top, spinning about its vertical axis. The seams sweep across the face and
 * the cap's teeth march around the rim in the same direction (clockwise seen
 * from above for a positive angular velocity).
 *
 * While the turbo is held the ball is squashed into a disc, as if pressed
 * from the top and bottom. Above the effect tiers in [com.aeunal.stressball.core.Stats.fxTiers]
 * it throws sparks, wears a corona of flame, crackles with lightning and
 * finally wraps itself in plasma. Effects are additive-blended so they glow
 * instead of painting over the ball.
 */
@Composable
fun BallCanvas(
    view: GameView,
    modifier: Modifier = Modifier,
) {
    val omega by rememberUpdatedState(view.state.omega)
    val latest by rememberUpdatedState(view)
    var angle by remember { mutableFloatStateOf(0f) }
    var frame by remember { mutableIntStateOf(0) }
    val rim = remember { RimGeometry() }
    val sparks = remember { SparkSystem() }
    val bolts = remember { LightningSystem() }

    // Integrate the engine's angular velocity into a display angle and step
    // the particle systems once per frame.
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1e9).toFloat().coerceAtMost(0.05f)
                    angle = wrapAngle(angle + (omega * dt).toFloat())
                    rim.time += dt
                    if (rim.ready) {
                        sparks.update(dt, latest, rim)
                        bolts.update(dt, latest, rim)
                    }
                    frame++
                }
                last = now
            }
        }
    }

    val squash by animateFloatAsState(
        targetValue = if (view.turboBoosting) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "squash",
    )
    val petalOpen by animateFloatAsState(
        targetValue = if (view.petalsOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "petals",
    )
    val overheat by animateFloatAsState(
        targetValue = if (view.turboOverheating) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "overheat",
    )
    val grip by animateFloatAsState(
        targetValue = if (view.fingerTouching) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "grip",
    )

    val bodyColor = Color(view.bodyColor.toInt())
    val capColor = Color(view.capColor.toInt())
    val palette = remember(bodyColor, capColor) { Palette(bodyColor, capColor) }

    Canvas(modifier = modifier) {
        @Suppress("UNUSED_EXPRESSION")
        frame // read so the canvas redraws every frame
        val r = min(size.width, size.height) * 0.36f
        val c = Offset(size.width / 2f, size.height / 2f)
        val sx = 1f + 0.32f * squash
        val sy = 1f - 0.42f * squash
        rim.set(c, r * sx, r * sy, r)

        val tier = view.fxTier
        val progress = view.fxTierProgress.toFloat()
        val time = rim.time
        val heat = view.heat.toFloat()

        drawAura(rim, tier, progress, heat, time)
        drawShadow(c, r, sx)
        if (tier >= 2) drawFlames(rim, angle, view.direction, tierIntensity(tier, 2, progress), coolness = if (tier >= 4) 0.7f else if (tier >= 3) 0.25f else 0f)
        if (tier >= 4) drawPlasmaBack(rim, angle, time, tierIntensity(tier, 4, progress))
        if (tier <= 1) drawStreaks(c, r, angle, (view.rpm / 600.0).coerceIn(0.0, 1.0).toFloat())

        scale(scaleX = sx, scaleY = sy, pivot = c) {
            drawBody(c, r, palette, tier)
            drawMeridians(c, r, angle, palette)
            drawEquator(c, r, petalOpen, palette)
            drawBottomRing(c, r, palette)
            drawCap(c, r, angle, palette)
        }

        if (grip > 0f) drawGripRing(rim, grip)
        if (tier >= 1) sparks.draw(this, tier)
        if (tier >= 3) bolts.draw(this)
        if (tier >= 4) drawPlasmaFront(rim, angle, time, tierIntensity(tier, 4, progress))
        if (overheat > 0f) drawOverheat(rim, overheat, time)
    }
}

/** 1 within a tier once it is fully established, ramping in over its first quarter. */
private fun tierIntensity(tier: Int, ownTier: Int, progress: Float): Float =
    if (tier > ownTier) 1f else (progress * 4f).coerceIn(0f, 1f) * 0.6f + 0.4f

private fun wrapAngle(a: Float): Float {
    var x = a % TAU
    if (x < 0f) x += TAU
    return x
}

/** Cheap 1-D value noise in -1..1, smooth in x. */
private fun noise(x: Float): Float =
    (sin(x * 1.3f) + 0.5f * sin(x * 2.9f + 1.7f) + 0.25f * sin(x * 5.7f + 0.3f)) / 1.75f

private class Palette(body: Color, cap: Color) {
    val body: Color = body
    val bodyLight: Color = lerp(body, Color.White, 0.35f)
    val bodyDark: Color = lerp(body, Color.Black, 0.45f)
    val seam: Color = lerp(body, Color.Black, 0.75f)
    val cap: Color = cap
    val capLight: Color = lerp(cap, Color.White, 0.25f)
    val capDark: Color = lerp(cap, Color.Black, 0.45f)
    val marker: Color = lerp(body, Color.White, 0.3f)
}

/** The ball's outline as drawn (an ellipse while squashed), shared with the particle systems. */
private class RimGeometry {
    var cx = 0f
    var cy = 0f
    var rx = 0f
    var ry = 0f
    var r = 0f
    var time = 0f
    val ready: Boolean get() = r > 0f
    val center: Offset get() = Offset(cx, cy)

    fun set(c: Offset, rx: Float, ry: Float, r: Float) {
        cx = c.x; cy = c.y; this.rx = rx; this.ry = ry; this.r = r
    }

    fun point(phi: Float, scale: Float = 1f): Offset =
        Offset(cx + rx * scale * cos(phi), cy + ry * scale * sin(phi))
}

// ---------------------------------------------------------------------------
// Ball
// ---------------------------------------------------------------------------

private fun DrawScope.drawShadow(c: Offset, r: Float, sx: Float) {
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent),
            center = Offset(c.x, c.y + r * 1.15f),
            radius = r * 0.9f * sx,
        ),
        topLeft = Offset(c.x - r * 0.9f * sx, c.y + r * 0.95f),
        size = Size(r * 1.8f * sx, r * 0.4f),
    )
}

private fun DrawScope.drawBody(c: Offset, r: Float, p: Palette, tier: Int) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(p.bodyLight, p.body, p.bodyDark),
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
    // Rim light from the effects around it.
    if (tier >= 2) {
        val rimLight = if (tier >= 4) Color(0xFFD9C4FF) else if (tier >= 3) Color(0xFFBFDCFF) else Color(0xFFFFB56B)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Transparent, rimLight.copy(alpha = 0.55f)),
                center = c,
                radius = r,
            ),
            radius = r,
            center = c,
            blendMode = BlendMode.Plus,
        )
    }
}

/**
 * Vertical seams between petals. For an increasing angle they travel from the
 * right edge to the left edge across the front face, which is what a ball
 * turning clockwise (seen from above) looks like.
 */
private fun DrawScope.drawMeridians(c: Offset, r: Float, angle: Float, p: Palette) {
    val seams = 6
    val stroke = Stroke(width = r * 0.035f)
    for (k in 0 until seams) {
        val phi = angle + k * (TAU / seams)
        val depth = cos(phi)            // > 0: on the front face
        if (depth <= 0.05f) continue
        val a = abs(sin(phi)) * r       // horizontal semi-axis of the meridian ellipse
        val alpha = (depth * 0.9f).coerceIn(0f, 0.9f)
        // sin > 0 puts the seam on the left half, sin < 0 on the right half.
        val startAngle = if (sin(phi) >= 0f) 90f else -90f
        drawArc(
            color = p.seam.copy(alpha = alpha),
            startAngle = startAngle,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(c.x - a, c.y - r),
            size = Size(2f * a, 2f * r),
            style = stroke,
        )
    }
}

/** The horizontal split. When petals open, the gap widens and glows from inside. */
private fun DrawScope.drawEquator(c: Offset, r: Float, open: Float, p: Palette) {
    val gap = r * (0.03f + 0.16f * open)
    val y = c.y + r * 0.05f
    if (open > 0f) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(p.capDark, p.capLight.copy(alpha = 0.9f), p.capDark),
                startY = y - gap,
                endY = y + gap,
            ),
            topLeft = Offset(c.x - r * 0.98f, y - gap),
            size = Size(r * 1.96f, 2f * gap),
        )
    }
    drawLine(color = p.seam, start = Offset(c.x - r * 0.985f, y - gap), end = Offset(c.x + r * 0.985f, y - gap), strokeWidth = r * 0.03f)
    drawLine(color = p.seam, start = Offset(c.x - r * 0.985f, y + gap), end = Offset(c.x + r * 0.985f, y + gap), strokeWidth = r * 0.03f)
}

private fun DrawScope.drawBottomRing(c: Offset, r: Float, p: Palette) {
    drawOval(
        color = p.capDark,
        topLeft = Offset(c.x - r * 0.28f, c.y + r * 0.84f),
        size = Size(r * 0.56f, r * 0.22f),
    )
}

/**
 * Gear cap on top, seen at an angle. Teeth march around the rim: for an
 * increasing angle the front teeth move right to left, matching the seams.
 */
private fun DrawScope.drawCap(c: Offset, r: Float, angle: Float, p: Palette) {
    val capY = c.y - r * 0.9f
    val rx = r * 0.32f
    val ry = r * 0.12f
    // Neck
    drawRect(
        color = p.capDark,
        topLeft = Offset(c.x - rx * 0.8f, capY - ry * 0.4f),
        size = Size(rx * 1.6f, ry * 1.6f),
    )
    val teeth = 6
    val toothW = rx * 0.34f
    val toothH = ry * 1.5f
    for (pass in 0..1) {
        for (k in 0 until teeth) {
            val t = angle + k * (TAU / teeth)
            val depth = sin(t)                       // > 0: front of the cap
            val isFront = depth >= 0f
            if ((pass == 0) == isFront) continue
            val x = c.x + rx * cos(t)
            val y = capY + ry * depth
            drawRect(
                color = if (isFront) p.capLight else p.cap,
                topLeft = Offset(x - toothW / 2f, y - toothH),
                size = Size(toothW, toothH * 1.3f),
            )
        }
        if (pass == 0) {
            drawOval(
                brush = Brush.verticalGradient(
                    colors = listOf(p.capLight, p.cap),
                    startY = capY - ry,
                    endY = capY + ry,
                ),
                topLeft = Offset(c.x - rx, capY - ry),
                size = Size(2f * rx, 2f * ry),
            )
            drawOval(
                color = p.capDark,
                topLeft = Offset(c.x - rx * 0.45f, capY - ry * 0.45f),
                size = Size(rx * 0.9f, ry * 0.9f),
            )
            // A marker dot so rotation is readable even at low RPM.
            drawCircle(
                color = p.marker,
                radius = rx * 0.08f,
                center = Offset(c.x + rx * 0.7f * cos(angle), capY + ry * 0.7f * sin(angle)),
            )
        }
    }
}

/** Faint ring while the finger grips the ball. */
private fun DrawScope.drawGripRing(g: RimGeometry, strength: Float) {
    drawOval(
        color = Color.White.copy(alpha = 0.18f * strength),
        topLeft = Offset(g.cx - g.rx * 1.04f, g.cy - g.ry * 1.04f),
        size = Size(g.rx * 2.08f, g.ry * 2.08f),
        style = Stroke(width = g.r * 0.02f),
    )
}

private fun DrawScope.drawStreaks(c: Offset, r: Float, angle: Float, speed: Float) {
    if (speed < 0.2f) return
    val alpha = (speed - 0.2f) / 0.8f
    val stroke = Stroke(width = r * 0.05f)
    for (i in 0 until 4) {
        val start = Math.toDegrees((angle * 0.5f + i * PI / 2).toDouble()).toFloat()
        drawArc(
            color = Color.White.copy(alpha = 0.16f * alpha),
            startAngle = start,
            sweepAngle = 40f + 50f * alpha,
            useCenter = false,
            topLeft = Offset(c.x - r * 1.12f, c.y - r * 1.12f),
            size = Size(r * 2.24f, r * 2.24f),
            style = stroke,
        )
    }
}

// ---------------------------------------------------------------------------
// Effects
// ---------------------------------------------------------------------------

private fun DrawScope.drawAura(g: RimGeometry, tier: Int, progress: Float, heat: Float, time: Float) {
    // Heat shimmer as the ball approaches its limit, before flames take over.
    if (tier < 2) {
        val a = ((heat - 0.55f) / 0.45f).coerceIn(0f, 1f) * 0.5f
        if (a > 0f) auraOval(g, 1.6f, Color(0xFFFFB347).copy(alpha = a))
    }
    if (tier == 2) auraOval(g, 1.7f, Color(0xFFFF7A2A).copy(alpha = 0.35f + 0.1f * noise(time * 11f)))
    if (tier >= 3) auraOval(g, 1.8f, Color(0xFF5FA8FF).copy(alpha = (0.25f + 0.12f * noise(time * 23f)) * tierIntensity(tier, 3, progress)))
    if (tier >= 4) auraOval(g, 2.0f, Color(0xFF9B5CFF).copy(alpha = 0.35f + 0.1f * sin(time * 9f)))
}

private fun DrawScope.auraOval(g: RimGeometry, scale: Float, color: Color) {
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = g.center,
            radius = g.rx * scale,
        ),
        topLeft = Offset(g.cx - g.rx * scale, g.cy - g.ry * scale),
        size = Size(g.rx * 2f * scale, g.ry * 2f * scale),
        blendMode = BlendMode.Plus,
    )
}

/**
 * A corona of flame around the rim, drawn behind the ball so only the tongues
 * that stick out are visible. Tongues lick upwards and flicker; the pattern
 * drifts against the direction of spin so the surface appears to rush past.
 */
private fun DrawScope.drawFlames(g: RimGeometry, angle: Float, direction: Int, intensity: Float, coolness: Float) {
    val n = 56
    val outer = Path()
    val inner = Path()
    for (i in 0 until n) {
        val phi = i * TAU / n
        val up = max(0f, -sin(phi))
        val flick = noise(i * 0.9f + g.time * 9f - angle * direction * 2f)
        val flick2 = noise(i * 1.7f + 3.1f + g.time * 13f)
        val h = g.r * (0.10f + 0.30f * intensity) * (0.55f + 0.45f * flick) * (1f + 0.8f * up)
        val h2 = h * 0.55f * (0.6f + 0.4f * flick2)
        val ox = cos(phi)
        val oy = sin(phi)
        val base = g.point(phi)
        val po = Offset(base.x + ox * h, base.y + oy * h)
        val pi = Offset(base.x + ox * h2, base.y + oy * h2)
        if (i == 0) { outer.moveTo(po.x, po.y); inner.moveTo(pi.x, pi.y) } else { outer.lineTo(po.x, po.y); inner.lineTo(pi.x, pi.y) }
    }
    outer.close()
    inner.close()
    val hot = lerp(Color(0xFFFF6A1A), Color(0xFF7CB8FF), coolness)
    val core = lerp(Color(0xFFFFD36A), Color(0xFFE6F4FF), coolness)
    drawPath(outer, hot.copy(alpha = 0.55f * intensity), blendMode = BlendMode.Plus)
    drawPath(inner, core.copy(alpha = 0.7f * intensity), blendMode = BlendMode.Plus)
}

private fun DrawScope.drawPlasmaBack(g: RimGeometry, angle: Float, time: Float, intensity: Float) {
    val colors = listOf(Color(0xFFFF3DDB), Color(0xFF7A3DFF), Color(0xFF3DE0FF), Color(0xFFFFFFFF), Color(0xFFFF3DDB))
    rotate(degrees = Math.toDegrees((angle * 0.7f).toDouble()).toFloat(), pivot = g.center) {
        drawOval(
            brush = Brush.sweepGradient(colors, g.center),
            topLeft = Offset(g.cx - g.rx * 1.2f, g.cy - g.ry * 1.2f),
            size = Size(g.rx * 2.4f, g.ry * 2.4f),
            alpha = 0.5f * intensity,
            style = Stroke(width = g.r * 0.18f),
            blendMode = BlendMode.Plus,
        )
    }
    rotate(degrees = -Math.toDegrees((angle * 1.3f + time * 2f).toDouble()).toFloat(), pivot = g.center) {
        drawOval(
            brush = Brush.sweepGradient(colors.reversed(), g.center),
            topLeft = Offset(g.cx - g.rx * 1.34f, g.cy - g.ry * 1.34f),
            size = Size(g.rx * 2.68f, g.ry * 2.68f),
            alpha = 0.35f * intensity,
            style = Stroke(width = g.r * 0.06f),
            blendMode = BlendMode.Plus,
        )
    }
}

/** Sinuous tendrils of light swimming around the ball. */
private fun DrawScope.drawPlasmaFront(g: RimGeometry, angle: Float, time: Float, intensity: Float) {
    val stroke = Stroke(width = g.r * 0.014f, cap = StrokeCap.Round)
    for (k in 0 until 3) {
        val path = Path()
        for (i in 0..72) {
            val phi = i * TAU / 72f
            val m = 1.10f + 0.12f * sin(3f * phi + time * (3f + k) + k * 2.1f - angle * 0.5f) +
                0.05f * sin(7f * phi - time * 5f + k)
            val p = g.point(phi, m)
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        val color = if (k == 1) Color(0xFFB8F1FF) else Color(0xFFE9D5FF)
        drawPath(path, color.copy(alpha = 0.4f * intensity), style = stroke, blendMode = BlendMode.Plus)
    }
}

/** Pulsing hot ring while the turbo is held empty. */
private fun DrawScope.drawOverheat(g: RimGeometry, strength: Float, time: Float) {
    val pulse = 0.7f + 0.3f * sin(time * 18f)
    drawOval(
        color = Color(0xFFFF4D1F).copy(alpha = 0.45f * strength * pulse),
        topLeft = Offset(g.cx - g.rx * 1.08f, g.cy - g.ry * 1.08f),
        size = Size(g.rx * 2.16f, g.ry * 2.16f),
        style = Stroke(width = g.r * 0.08f),
        blendMode = BlendMode.Plus,
    )
}

// ---------------------------------------------------------------------------
// Particles
// ---------------------------------------------------------------------------

/** Sparks thrown off the rim; they arc under gravity and cool from white to red. */
private class SparkSystem {
    private val max = 320
    private val x = FloatArray(max)
    private val y = FloatArray(max)
    private val vx = FloatArray(max)
    private val vy = FloatArray(max)
    private val life = FloatArray(max)
    private val maxLife = FloatArray(max)
    private val size = FloatArray(max)
    private var count = 0
    private var emitAccumulator = 0f

    fun update(dt: Float, view: GameView, g: RimGeometry) {
        val tier = view.fxTier
        val progress = view.fxTierProgress.toFloat()
        val rate = when (tier) {
            0 -> 0f
            1 -> 40f + 180f * progress
            2 -> 140f
            3 -> 90f
            else -> 70f
        }
        emitAccumulator += rate * dt
        while (emitAccumulator >= 1f && count < max) {
            emit(view.direction, g)
            emitAccumulator -= 1f
        }
        if (emitAccumulator > 4f) emitAccumulator = 0f

        val gravity = g.r * 2.5f
        var i = 0
        while (i < count) {
            life[i] -= dt
            if (life[i] <= 0f) {
                val last = count - 1
                x[i] = x[last]; y[i] = y[last]; vx[i] = vx[last]; vy[i] = vy[last]
                life[i] = life[last]; maxLife[i] = maxLife[last]; size[i] = size[last]
                count--
                continue
            }
            vy[i] += gravity * dt
            vx[i] *= 1f - 0.8f * dt
            vy[i] *= 1f - 0.3f * dt
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            i++
        }
    }

    private fun emit(direction: Int, g: RimGeometry) {
        val phi = Random.nextFloat() * TAU
        val p = g.point(phi, 1f)
        val ox = cos(phi)
        val oy = sin(phi)
        val outward = g.r * (1.2f + Random.nextFloat() * 2.2f)
        val along = direction * g.r * (0.6f + Random.nextFloat() * 0.8f)
        val i = count++
        x[i] = p.x
        y[i] = p.y
        vx[i] = ox * outward - oy * along
        vy[i] = oy * outward + ox * along
        life[i] = 0.25f + Random.nextFloat() * 0.45f
        maxLife[i] = life[i]
        size[i] = g.r * (0.012f + Random.nextFloat() * 0.02f)
    }

    fun draw(scope: DrawScope, tier: Int) {
        for (i in 0 until count) {
            val age = 1f - life[i] / maxLife[i]
            val alpha = (1f - age) * (1f - age)
            val color = if (tier >= 4) {
                lerp(Color(0xFFCFEBFF), Color(0xFF7CB8FF), age)
            } else {
                lerp(lerp(Color(0xFFFFF3C0), Color(0xFFFFB03A), age), Color(0xFFE23A12), age * age)
            }
            scope.drawLine(
                color = color.copy(alpha = alpha),
                start = Offset(x[i] - vx[i] * 0.035f, y[i] - vy[i] * 0.035f),
                end = Offset(x[i], y[i]),
                strokeWidth = size[i] * (1.6f - age),
                cap = StrokeCap.Round,
                blendMode = BlendMode.Plus,
            )
        }
    }
}

private class Bolt(val points: FloatArray, var life: Float, val maxLife: Float, val width: Float)

/** Short-lived jagged arcs leaping from the rim into the air around the ball. */
private class LightningSystem {
    private val bolts = ArrayList<Bolt>()
    private var accumulator = 0f

    fun update(dt: Float, view: GameView, g: RimGeometry) {
        val it = bolts.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.life -= dt
            if (b.life <= 0f) it.remove()
        }
        val tier = view.fxTier
        val progress = view.fxTierProgress.toFloat()
        val rate = when (tier) {
            3 -> 3f + 10f * progress
            4 -> 9f
            else -> 0f
        }
        accumulator += rate * dt
        while (accumulator >= 1f && bolts.size < 10) {
            bolts += spawn(g)
            accumulator -= 1f
        }
        if (accumulator > 3f) accumulator = 0f
    }

    private fun spawn(g: RimGeometry): Bolt {
        val phi = Random.nextFloat() * TAU
        val a = g.point(phi, 1.02f)
        val spread = (Random.nextFloat() - 0.5f) * 1.6f
        val b = g.point(phi + spread, 1.3f + Random.nextFloat() * 0.35f)
        val pts = ArrayList<Offset>()
        pts += a
        subdivide(a, b, 4, pts)
        val arr = FloatArray(pts.size * 2)
        for ((i, p) in pts.withIndex()) { arr[2 * i] = p.x; arr[2 * i + 1] = p.y }
        return Bolt(
            points = arr,
            life = 0.07f + Random.nextFloat() * 0.1f,
            maxLife = 0.17f,
            width = g.r * 0.014f * (0.7f + Random.nextFloat() * 0.6f),
        )
    }

    /** Midpoint displacement; appends every point after [a] up to and including [b]. */
    private fun subdivide(a: Offset, b: Offset, depth: Int, out: MutableList<Offset>) {
        if (depth == 0) {
            out += b
            return
        }
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len = kotlin.math.sqrt(dx * dx + dy * dy)
        val jitter = (Random.nextFloat() - 0.5f) * 2f * len * 0.28f
        val mid = Offset((a.x + b.x) / 2f - dy / len * jitter, (a.y + b.y) / 2f + dx / len * jitter)
        subdivide(a, mid, depth - 1, out)
        subdivide(mid, b, depth - 1, out)
    }

    fun draw(scope: DrawScope) {
        for (b in bolts) {
            val alpha = (b.life / b.maxLife).coerceIn(0f, 1f)
            val path = Path()
            path.moveTo(b.points[0], b.points[1])
            var i = 2
            while (i < b.points.size) {
                path.lineTo(b.points[i], b.points[i + 1])
                i += 2
            }
            scope.drawPath(
                path,
                color = Color(0xFF63B3FF).copy(alpha = 0.35f * alpha),
                style = Stroke(width = b.width * 4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                blendMode = BlendMode.Plus,
            )
            scope.drawPath(
                path,
                color = Color.White.copy(alpha = 0.95f * alpha),
                style = Stroke(width = b.width, cap = StrokeCap.Round, join = StrokeJoin.Round),
                blendMode = BlendMode.Plus,
            )
        }
    }
}
