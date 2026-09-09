package com.aeunal.stressball.ui

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import com.aeunal.stressball.core.BuffKind
import com.aeunal.stressball.core.GameView
import com.aeunal.stressball.core.SkinStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val TAU = (2.0 * PI).toFloat()

/**
 * Draws the stress ball.
 *
 * From the side it is a rubber sphere split into petals with a gear cap on
 * top, spinning about its vertical axis: the seams sweep across the face and
 * the cap's teeth march around the rim in the same direction (clockwise seen
 * from above for a positive angular velocity). With the Gimbal Mount the ball
 * flips to show its cap from above: the cap becomes a full gear in the
 * centre and the seams become spokes. The flip is animated by [GameView.topView].
 *
 * While the turbo is squeezed the ball is pressed into a disc. Skins add
 * patterns on the shell and a glow from inside. Above the effect tiers in
 * [com.aeunal.stressball.core.Stats.fxTiers] it throws sparks, wears a corona
 * of flame, crackles with lightning, wraps itself in plasma, bends light like
 * a singularity, detonates in shockwaves and finally glitches out of phase.
 * Effects are additive-blended so they glow instead of painting over the ball.
 */
@Composable
fun BallCanvas(
    view: GameView,
    modifier: Modifier = Modifier,
) {
    val omega by rememberUpdatedState(view.ball.omega)
    val latest by rememberUpdatedState(view)
    var angle by remember { mutableFloatStateOf(0f) }
    var frame by remember { mutableIntStateOf(0) }
    val rim = remember { RimGeometry() }
    val sparks = remember { SparkSystem() }
    val bolts = remember { LightningSystem() }
    val stars = remember { StarSystem() }
    val shocks = remember { ShockwaveSystem() }

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
                        stars.update(dt, latest, rim)
                        shocks.update(dt, latest, rim)
                    }
                    frame++
                }
                last = now
            }
        }
    }

    // The disc morph follows the squeeze itself, so the ball flattens exactly
    // as far as the fingers close, with a little spring so it feels like rubber.
    val squash by animateFloatAsState(
        targetValue = view.turboWeight.toFloat().coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "squash",
    )
    val pitch by animateFloatAsState(
        targetValue = if (view.topView) 1f else 0f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "pitch",
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
    val frenzy by animateFloatAsState(
        targetValue = if (view.buff == BuffKind.FRENZY) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "frenzy",
    )

    val bodyColor = Color(view.bodyColor.toInt())
    val capColor = Color(view.capColor.toInt())
    val auraColor = Color(view.auraColor.toInt())
    val outer = view.outerSkin
    val interior = view.interiorSkin
    val palette = remember(bodyColor, capColor, auraColor, outer?.id, interior?.id) {
        Palette(
            bodyColor, capColor, auraColor,
            outerStyle = outer?.style, outerColor = outer?.let { Color(it.color.toInt()) },
            interiorStyle = interior?.style, interiorColor = interior?.let { Color(it.color.toInt()) },
        )
    }

    Canvas(modifier = modifier) {
        if (frame < 0) return@Canvas // read so the canvas redraws every frame
        val r = min(size.width, size.height) * 0.36f
        val c = Offset(size.width / 2f, size.height / 2f)
        val sx = 1f + 0.32f * squash
        val sy = 1f - 0.42f * squash
        rim.set(c, r * sx, r * sy, r)

        val tier = view.fxTier
        val progress = view.fxTierProgress.toFloat()
        val time = rim.time
        val heat = view.heat.toFloat()
        val side = 1f - pitch

        // --- behind the ball -------------------------------------------
        drawAura(rim, tier, progress, heat, time, palette.aura)
        if (frenzy > 0f) drawFrenzyAura(rim, frenzy, time)
        if (side > 0f) drawShadow(c, r, sx, side)
        if (side > 0f) drawBottomRing(c, r, palette, side)
        if (tier >= 2) drawFlames(rim, angle, view.direction, tierIntensity(tier, 2, progress), coolness = if (tier >= 4) 0.7f else if (tier >= 3) 0.25f else 0f, tint = palette.aura)
        if (tier >= 4) drawPlasmaBack(rim, angle, time, tierIntensity(tier, 4, progress))
        if (tier >= 5) drawSingularityBack(rim, angle, time, tierIntensity(tier, 5, progress))
        if (tier >= 6) { shocks.draw(this); drawSupernovaBloom(rim, time, tierIntensity(tier, 6, progress)) }
        if (palette.outerStyle == SkinStyle.RINGS) drawSaturnRing(rim, pitch, palette, back = true)
        if (tier <= 1) drawStreaks(c, r, angle, (view.rpm / 600.0).coerceIn(0.0, 1.0).toFloat())

        // --- the ball ------------------------------------------------------
        scale(scaleX = sx, scaleY = sy, pivot = c) {
            if (tier >= 7) drawQuantumGhosts(c, r, palette, time, tierIntensity(tier, 7, progress))
            drawBody(c, r, palette, tier)
            drawOuterSkin(c, r, angle, pitch, time, palette)
            if (side > 0f) {
                drawMeridians(c, r, angle, palette, side)
                drawEquator(c, r, petalOpen, palette, side)
            }
            if (pitch > 0f) drawSpokes(c, r, angle, petalOpen, palette, pitch)
            drawInteriorGlow(c, r, angle, pitch, time, palette, view.rpm.toFloat())
            drawCap(c, r, angle, pitch, time, palette)
            if (tier >= 7) drawQuantumGlitch(c, r, palette, time, tierIntensity(tier, 7, progress))
        }

        // --- in front ----------------------------------------------------
        if (palette.outerStyle == SkinStyle.RINGS) drawSaturnRing(rim, pitch, palette, back = false)
        if (grip > 0f) drawGripRing(rim, grip, wild = view.buff == BuffKind.WILD_GRIP)
        if (tier >= 1) sparks.draw(this, tier, palette.aura)
        if (tier >= 3) bolts.draw(this)
        if (tier >= 4) drawPlasmaFront(rim, angle, time, tierIntensity(tier, 4, progress))
        if (tier >= 5) stars.draw(this)
        if (tier >= 6) drawSupernovaStreaks(rim, angle, time, tierIntensity(tier, 6, progress))
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

/** Deterministic pseudo-random in 0..1 for a seed, so patterns are stable frame to frame. */
private fun hash(seed: Int): Float {
    var x = seed * 374761393 + 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0x7fffffff) / 2147483647f
}

private class Palette(
    body: Color,
    cap: Color,
    val aura: Color,
    val outerStyle: SkinStyle?,
    val outerColor: Color?,
    val interiorStyle: SkinStyle?,
    val interiorColor: Color?,
) {
    val body: Color = body
    val bodyLight: Color = lerp(body, Color.White, 0.35f)
    val bodyDark: Color = lerp(body, Color.Black, 0.45f)
    val seam: Color = lerp(body, Color.Black, 0.75f)
    val cap: Color = cap
    val capLight: Color = lerp(cap, Color.White, 0.25f)
    val capDark: Color = lerp(cap, Color.Black, 0.45f)
    val marker: Color = lerp(body, Color.White, 0.3f)
    /** What shows through the petal gap and around the cap base. */
    val inner: Color = interiorColor ?: capLight
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

/** A point on the unit sphere by longitude/latitude, projected for the side view and the top view. */
private class SpherePoint(val lon: Float, val lat: Float)

/** Projects a sphere point for the current pitch. Returns null when it is on the hidden side. */
private fun project(p: SpherePoint, c: Offset, r: Float, angle: Float, pitch: Float): Pair<Offset, Float>? {
    val az = p.lon + angle
    // Side view: the axis is vertical on screen.
    val sideDepth = cos(p.lat) * cos(az)
    val sideX = c.x + r * cos(p.lat) * sin(az)
    val sideY = c.y - r * sin(p.lat)
    // Top view: looking down the axis, the upper hemisphere is visible.
    val topDepth = sin(p.lat)
    val topX = c.x + r * cos(p.lat) * cos(az)
    val topY = c.y + r * cos(p.lat) * sin(az)
    val depth = sideDepth * (1f - pitch) + topDepth * pitch
    if (depth <= 0.05f) return null
    return Offset(sideX * (1f - pitch) + topX * pitch, sideY * (1f - pitch) + topY * pitch) to depth
}

private val skinAnchors: List<SpherePoint> = List(14) { i ->
    SpherePoint(lon = hash(i * 7 + 1) * TAU, lat = (hash(i * 13 + 3) - 0.5f) * 2.4f)
}

// ---------------------------------------------------------------------------
// Ball
// ---------------------------------------------------------------------------

private fun DrawScope.drawShadow(c: Offset, r: Float, sx: Float, alpha: Float) {
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color.Black.copy(alpha = 0.45f * alpha), Color.Transparent),
            center = Offset(c.x, c.y + r * 1.15f),
            radius = r * 0.9f * sx,
        ),
        topLeft = Offset(c.x - r * 0.9f * sx, c.y + r * 0.95f),
        size = Size(r * 1.8f * sx, r * 0.4f),
    )
}

/** The green base ring the toy stands on, peeking out from behind the bottom of the ball. */
private fun DrawScope.drawBottomRing(c: Offset, r: Float, p: Palette, alpha: Float) {
    drawOval(
        color = p.capDark.copy(alpha = alpha),
        topLeft = Offset(c.x - r * 0.3f, c.y + r * 0.8f),
        size = Size(r * 0.6f, r * 0.26f),
    )
    drawOval(
        color = p.cap.copy(alpha = 0.7f * alpha),
        topLeft = Offset(c.x - r * 0.24f, c.y + r * 0.86f),
        size = Size(r * 0.48f, r * 0.14f),
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
        val rimLight = when {
            tier >= 6 -> Color.White
            tier >= 5 -> Color(0xFFB8F1FF)
            tier >= 4 -> Color(0xFFD9C4FF)
            tier >= 3 -> Color(0xFFBFDCFF)
            else -> Color(0xFFFFB56B)
        }
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
 * Vertical seams between petals (side view). For an increasing angle they
 * travel from the right edge to the left edge across the front face, which is
 * what a ball turning clockwise (seen from above) looks like.
 */
private fun DrawScope.drawMeridians(c: Offset, r: Float, angle: Float, p: Palette, alpha: Float) {
    val seams = 6
    val stroke = Stroke(width = r * 0.035f)
    for (k in 0 until seams) {
        val phi = angle + k * (TAU / seams)
        val depth = cos(phi)
        if (depth <= 0.05f) continue
        val a = abs(sin(phi)) * r
        val startAngle = if (sin(phi) >= 0f) 90f else -90f
        drawArc(
            color = p.seam.copy(alpha = (depth * 0.9f).coerceIn(0f, 0.9f) * alpha),
            startAngle = startAngle,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(c.x - a, c.y - r),
            size = Size(2f * a, 2f * r),
            style = stroke,
        )
    }
}

/** The horizontal split (side view). When petals open, the gap widens and the interior shows. */
private fun DrawScope.drawEquator(c: Offset, r: Float, open: Float, p: Palette, alpha: Float) {
    val gap = r * (0.03f + 0.16f * open)
    val y = c.y + r * 0.05f
    if (open > 0f) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(p.capDark.copy(alpha = alpha), p.inner.copy(alpha = 0.9f * alpha), p.capDark.copy(alpha = alpha)),
                startY = y - gap,
                endY = y + gap,
            ),
            topLeft = Offset(c.x - r * 0.98f, y - gap),
            size = Size(r * 1.96f, 2f * gap),
        )
    }
    val seam = p.seam.copy(alpha = alpha)
    drawLine(color = seam, start = Offset(c.x - r * 0.985f, y - gap), end = Offset(c.x + r * 0.985f, y - gap), strokeWidth = r * 0.03f)
    drawLine(color = seam, start = Offset(c.x - r * 0.985f, y + gap), end = Offset(c.x + r * 0.985f, y + gap), strokeWidth = r * 0.03f)
}

/** Seams seen from above: spokes from the cap to the rim, turning with the ball. */
private fun DrawScope.drawSpokes(c: Offset, r: Float, angle: Float, open: Float, p: Palette, alpha: Float) {
    val seams = 6
    for (k in 0 until seams) {
        val t = angle + k * (TAU / seams)
        val inner = Offset(c.x + r * 0.34f * cos(t), c.y + r * 0.34f * sin(t))
        val outerP = Offset(c.x + r * 0.985f * cos(t), c.y + r * 0.985f * sin(t))
        if (open > 0f) {
            drawLine(p.inner.copy(alpha = 0.6f * open * alpha), inner, outerP, strokeWidth = r * (0.03f + 0.12f * open), cap = StrokeCap.Round, blendMode = BlendMode.Plus)
        }
        drawLine(p.seam.copy(alpha = 0.85f * alpha), inner, outerP, strokeWidth = r * 0.035f)
    }
    drawCircle(color = p.seam.copy(alpha = 0.5f * alpha), radius = r * 0.985f, center = c, style = Stroke(width = r * 0.03f))
}

/**
 * Gear cap. From the side it sits on top, an ellipse seen at an angle whose
 * front teeth move right to left for an increasing angle; from above it is a
 * full gear in the centre. Geometry interpolates with [pitch].
 */
private fun DrawScope.drawCap(c: Offset, r: Float, angle: Float, pitch: Float, time: Float, p: Palette) {
    val capY = c.y - r * 0.9f * (1f - pitch)
    val rx = r * 0.32f
    val ry = r * 0.12f + r * 0.20f * pitch
    if (pitch < 1f) {
        drawRect(
            color = p.capDark.copy(alpha = 1f - pitch),
            topLeft = Offset(c.x - rx * 0.8f, capY - ry * 0.4f),
            size = Size(rx * 1.6f, ry * 1.6f),
        )
    }
    val teeth = 6
    val toothW = rx * 0.34f
    val toothH = ry * 1.5f * (1f - pitch) + rx * 0.3f * pitch
    for (pass in 0..1) {
        for (k in 0 until teeth) {
            val t = angle + k * (TAU / teeth)
            val depth = sin(t)
            val isFront = depth >= 0f
            if ((pass == 0) == isFront) continue
            val x = c.x + rx * cos(t)
            val y = capY + ry * depth
            val shade = if (isFront || pitch > 0.5f) p.capLight else p.cap
            // Side view: teeth stand up. Top view: teeth point outwards.
            val degrees = pitch * (Math.toDegrees(t.toDouble()).toFloat() + 90f)
            rotate(degrees = degrees, pivot = Offset(x, y)) {
                drawRect(
                    color = shade,
                    topLeft = Offset(x - toothW / 2f, y - toothH),
                    size = Size(toothW, toothH * 1.3f),
                )
            }
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
            drawInteriorSignature(c, capY, rx, ry, angle, pitch, time, p)
            // A marker dot so rotation is readable even at low RPM.
            drawCircle(
                color = p.marker,
                radius = rx * 0.08f,
                center = Offset(c.x + rx * 0.7f * cos(angle), capY + ry * 0.7f * sin(angle)),
            )
        }
    }
}

/** Faint ring while the finger grips the ball; green and stronger under Wild Grip. */
private fun DrawScope.drawGripRing(g: RimGeometry, strength: Float, wild: Boolean) {
    val color = if (wild) Color(0xFF7CF7C0).copy(alpha = 0.45f * strength) else Color.White.copy(alpha = 0.18f * strength)
    drawOval(
        color = color,
        topLeft = Offset(g.cx - g.rx * 1.04f, g.cy - g.ry * 1.04f),
        size = Size(g.rx * 2.08f, g.ry * 2.08f),
        style = Stroke(width = g.r * (if (wild) 0.035f else 0.02f)),
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
// Skins
// ---------------------------------------------------------------------------

/** Patterns painted on the shell, clipped to the ball, moving with the surface. */
private fun DrawScope.drawOuterSkin(c: Offset, r: Float, angle: Float, pitch: Float, time: Float, p: Palette) {
    val style = p.outerStyle ?: return
    val color = p.outerColor ?: Color.White
    val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c.x - r, c.y - r, c.x + r, c.y + r)) }
    clipPath(clip) {
        when (style) {
            SkinStyle.STRIPES -> {
                for (k in 0 until 2) {
                    val phi = angle + k * (TAU / 2)
                    val depth = cos(phi)
                    if (depth > 0.05f && pitch < 1f) {
                        val a = abs(sin(phi)) * r
                        drawArc(
                            color = color.copy(alpha = 0.55f * depth * (1f - pitch)),
                            startAngle = if (sin(phi) >= 0f) 90f else -90f, sweepAngle = 180f, useCenter = false,
                            topLeft = Offset(c.x - a, c.y - r), size = Size(2f * a, 2f * r),
                            style = Stroke(width = r * 0.14f),
                        )
                    }
                    if (pitch > 0f) {
                        val t = angle + k * (TAU / 2)
                        drawLine(
                            color.copy(alpha = 0.55f * pitch),
                            Offset(c.x - r * cos(t), c.y - r * sin(t)), Offset(c.x + r * cos(t), c.y + r * sin(t)),
                            strokeWidth = r * 0.14f,
                        )
                    }
                }
            }
            SkinStyle.SPOTS -> for ((i, a) in skinAnchors.withIndex()) {
                val (pt, depth) = project(a, c, r, angle, pitch) ?: continue
                drawCircle(color.copy(alpha = 0.85f), radius = r * (0.06f + 0.05f * hash(i)) * (0.5f + 0.5f * depth), center = pt)
            }
            SkinStyle.HEX -> for (a in skinAnchors) {
                val (pt, depth) = project(a, c, r, angle, pitch) ?: continue
                val size = r * 0.12f * (0.5f + 0.5f * depth)
                val path = Path()
                for (i in 0 until 6) {
                    val t = i * TAU / 6f
                    val v = Offset(pt.x + size * cos(t), pt.y + size * sin(t) * (0.4f + 0.6f * depth))
                    if (i == 0) path.moveTo(v.x, v.y) else path.lineTo(v.x, v.y)
                }
                path.close()
                drawPath(path, color.copy(alpha = 0.6f * depth), style = Stroke(width = r * 0.018f), blendMode = BlendMode.Plus)
            }
            SkinStyle.STARS -> for ((i, a) in skinAnchors.withIndex()) {
                val (pt, depth) = project(a, c, r, angle, pitch) ?: continue
                val twinkle = 0.5f + 0.5f * sin(time * (4f + hash(i) * 3f) + i)
                val s = r * 0.06f * (0.4f + 0.6f * depth) * (0.7f + 0.3f * twinkle)
                val col = color.copy(alpha = (0.4f + 0.6f * twinkle) * depth)
                drawLine(col, Offset(pt.x - s, pt.y), Offset(pt.x + s, pt.y), strokeWidth = r * 0.012f, blendMode = BlendMode.Plus)
                drawLine(col, Offset(pt.x, pt.y - s), Offset(pt.x, pt.y + s), strokeWidth = r * 0.012f, blendMode = BlendMode.Plus)
                drawCircle(col, radius = r * 0.012f, center = pt, blendMode = BlendMode.Plus)
            }
            SkinStyle.CRACKS -> for ((i, a) in skinAnchors.withIndex()) {
                if (i % 2 == 1) continue
                val (pt, depth) = project(a, c, r, angle, pitch) ?: continue
                val path = Path().apply { moveTo(pt.x, pt.y) }
                var x = pt.x
                var y = pt.y
                for (s in 1..4) {
                    x += r * (hash(i * 31 + s) - 0.5f) * 0.28f * depth
                    y += r * (hash(i * 47 + s) - 0.5f) * 0.28f
                    path.lineTo(x, y)
                }
                val glow = 0.6f + 0.4f * sin(time * 3f + i)
                drawPath(path, color.copy(alpha = 0.5f * glow * depth), style = Stroke(width = r * 0.05f, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
                drawPath(path, Color.Black.copy(alpha = 0.85f * depth), style = Stroke(width = r * 0.016f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            SkinStyle.RINGS -> Unit // drawn outside the clip, around the ball
            else -> Unit
        }
    }
}

/** Saturn rings: a tilted golden ring around the ball; half behind, half in front. */
private fun DrawScope.drawSaturnRing(g: RimGeometry, pitch: Float, p: Palette, back: Boolean) {
    val color = p.outerColor ?: Color(0xFFFFD36A)
    val rx = g.rx * 1.55f
    val ry = g.ry * (0.36f + 1.19f * pitch)
    val stroke = Stroke(width = g.r * 0.12f)
    val start = if (back) 180f else 0f
    drawArc(
        brush = Brush.linearGradient(listOf(lerp(color, Color.White, 0.3f), color, lerp(color, Color.Black, 0.4f))),
        startAngle = start, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(g.cx - rx, g.cy - ry), size = Size(2f * rx, 2f * ry),
        style = stroke, alpha = 0.85f,
    )
    drawArc(
        color = Color.Black.copy(alpha = 0.35f),
        startAngle = start, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(g.cx - rx, g.cy - ry), size = Size(2f * rx, 2f * ry),
        style = Stroke(width = g.r * 0.015f),
    )
}

/** Light from inside: a glow at the cap base and (through the seams) around the equator. */
private fun DrawScope.drawInteriorGlow(c: Offset, r: Float, angle: Float, pitch: Float, time: Float, p: Palette, rpm: Float) {
    val color = p.interiorColor ?: return
    val pulse = 0.75f + 0.25f * sin(time * 4f)
    val strength = (0.25f + 0.35f * (rpm / 2000f).coerceIn(0f, 1f)) * pulse
    val capY = c.y - r * 0.9f * (1f - pitch)
    drawCircle(
        brush = Brush.radialGradient(listOf(color.copy(alpha = strength), Color.Transparent), center = Offset(c.x, capY + r * 0.1f), radius = r * 0.7f),
        radius = r * 0.7f, center = Offset(c.x, capY + r * 0.1f), blendMode = BlendMode.Plus,
    )
    if (p.interiorStyle == SkinStyle.EMBER) {
        for (i in 0 until 7) {
            val phase = (time * (0.4f + 0.3f * hash(i)) + hash(i + 50)) % 1f
            val x = c.x + (hash(i + 90) - 0.5f) * 1.6f * r + sin(time * 3f + i) * r * 0.05f
            val y = c.y + r * 0.05f - phase * r * 0.7f
            drawCircle(color.copy(alpha = (1f - phase) * 0.8f), radius = r * 0.02f * (1f + hash(i)), center = Offset(x, y), blendMode = BlendMode.Plus)
        }
    }
}

/** A signature element per interior skin, drawn on the cap disc. */
private fun DrawScope.drawInteriorSignature(c: Offset, capY: Float, rx: Float, ry: Float, angle: Float, pitch: Float, time: Float, p: Palette) {
    val style = p.interiorStyle ?: return
    val color = p.interiorColor ?: return
    val centre = Offset(c.x, capY)
    when (style) {
        SkinStyle.CRYSTAL -> for (k in 0 until 3) {
            val t = -angle * 1.5f + k * (TAU / 3)
            drawLine(color.copy(alpha = 0.8f), centre, Offset(c.x + rx * 0.42f * cos(t), capY + ry * 0.42f * sin(t)), strokeWidth = rx * 0.05f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
        }
        SkinStyle.VOID -> {
            drawOval(Color(0xFF05040A), topLeft = Offset(c.x - rx * 0.3f, capY - ry * 0.3f), size = Size(rx * 0.6f, ry * 0.6f))
            for (k in 0 until 8) {
                val t = -angle * 2f + k * (TAU / 8)
                drawCircle(color.copy(alpha = 0.7f), radius = rx * 0.03f, center = Offset(c.x + rx * 0.36f * cos(t), capY + ry * 0.36f * sin(t)), blendMode = BlendMode.Plus)
            }
        }
        SkinStyle.STORM -> {
            val bucket = (time * 4f).toInt()
            if (hash(bucket) > 0.45f) {
                val path = Path()
                val t0 = hash(bucket + 7) * TAU
                var x = c.x + rx * 0.4f * cos(t0)
                var y = capY + ry * 0.4f * sin(t0)
                path.moveTo(x, y)
                for (s in 1..4) {
                    x += (hash(bucket * 9 + s) - 0.5f) * rx * 0.5f
                    y += (hash(bucket * 11 + s) - 0.5f) * ry * 0.5f
                    path.lineTo(x, y)
                }
                drawPath(path, color.copy(alpha = 0.5f), style = Stroke(width = rx * 0.08f, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
                drawPath(path, Color.White.copy(alpha = 0.9f), style = Stroke(width = rx * 0.02f, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
            }
        }
        SkinStyle.PRISM -> rotate(degrees = Math.toDegrees((angle * 2f + time).toDouble()).toFloat(), pivot = centre) {
            drawOval(
                brush = Brush.sweepGradient(listOf(Color(0xFFFF3DDB), Color(0xFFFFD36A), Color(0xFF3DE0FF), Color(0xFF7CF7C0), Color(0xFFFF3DDB)), centre),
                topLeft = Offset(c.x - rx * 0.42f, capY - ry * 0.42f), size = Size(rx * 0.84f, ry * 0.84f),
                style = Stroke(width = rx * 0.06f), alpha = 0.85f, blendMode = BlendMode.Plus,
            )
        }
        SkinStyle.CLOCKWORK -> for (k in 0 until 10) {
            val t = angle * 2f + k * (TAU / 10)
            val x = c.x + rx * 0.36f * cos(t)
            val y = capY + ry * 0.36f * sin(t)
            drawCircle(color.copy(alpha = 0.9f), radius = rx * 0.035f, center = Offset(x, y))
        }
        SkinStyle.EMBER -> drawOval(
            brush = Brush.radialGradient(listOf(color.copy(alpha = 0.6f + 0.3f * sin(time * 7f)), Color.Transparent), centre, rx * 0.45f),
            topLeft = Offset(c.x - rx * 0.45f, capY - ry * 0.45f), size = Size(rx * 0.9f, ry * 0.9f), blendMode = BlendMode.Plus,
        )
        else -> Unit
    }
}

// ---------------------------------------------------------------------------
// Effects
// ---------------------------------------------------------------------------

private fun DrawScope.drawAura(g: RimGeometry, tier: Int, progress: Float, heat: Float, time: Float, tint: Color) {
    if (tier < 2) {
        val a = ((heat - 0.55f) / 0.45f).coerceIn(0f, 1f) * 0.5f
        if (a > 0f) auraOval(g, 1.6f, tint.copy(alpha = a))
    }
    if (tier == 2) auraOval(g, 1.7f, Color(0xFFFF7A2A).copy(alpha = 0.35f + 0.1f * noise(time * 11f)))
    if (tier in 3..4) auraOval(g, 1.8f, Color(0xFF5FA8FF).copy(alpha = (0.25f + 0.12f * noise(time * 23f)) * tierIntensity(tier, 3, progress)))
    if (tier == 4) auraOval(g, 2.0f, Color(0xFF9B5CFF).copy(alpha = 0.35f + 0.1f * sin(time * 9f)))
    if (tier >= 5) auraOval(g, 2.2f, Color(0xFF0A0614).copy(alpha = 0.6f * tierIntensity(tier, 5, progress)))
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

private fun DrawScope.drawFrenzyAura(g: RimGeometry, strength: Float, time: Float) {
    val pulse = 0.8f + 0.2f * sin(time * 6f)
    auraOval(g, 1.9f, Color(0xFFFFD36A).copy(alpha = 0.3f * strength * pulse))
    drawOval(
        color = Color(0xFFFFE28A).copy(alpha = 0.5f * strength),
        topLeft = Offset(g.cx - g.rx * 1.15f, g.cy - g.ry * 1.15f),
        size = Size(g.rx * 2.3f, g.ry * 2.3f),
        style = Stroke(width = g.r * 0.03f),
        blendMode = BlendMode.Plus,
    )
}

/**
 * A corona of flame around the rim, drawn behind the ball so only the tongues
 * that stick out are visible. Tongues lick upwards and flicker; the pattern
 * drifts against the direction of spin so the surface appears to rush past.
 */
private fun DrawScope.drawFlames(g: RimGeometry, angle: Float, direction: Int, intensity: Float, coolness: Float, tint: Color) {
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
    val hot = lerp(lerp(Color(0xFFFF6A1A), tint, 0.25f), Color(0xFF7CB8FF), coolness)
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

/** Singularity: a dark event horizon halo and an Einstein ring of bent light. */
private fun DrawScope.drawSingularityBack(g: RimGeometry, angle: Float, time: Float, intensity: Float) {
    drawOval(
        color = Color.Black.copy(alpha = 0.75f * intensity),
        topLeft = Offset(g.cx - g.rx * 1.14f, g.cy - g.ry * 1.14f),
        size = Size(g.rx * 2.28f, g.ry * 2.28f),
        style = Stroke(width = g.r * 0.26f),
    )
    rotate(degrees = Math.toDegrees((angle * 0.35f + time * 0.6f).toDouble()).toFloat(), pivot = g.center) {
        drawOval(
            brush = Brush.sweepGradient(listOf(Color.Transparent, Color(0xFFB8F1FF), Color.White, Color(0xFFB8F1FF), Color.Transparent, Color(0xFF9B5CFF), Color.Transparent), g.center),
            topLeft = Offset(g.cx - g.rx * 1.22f, g.cy - g.ry * 1.22f),
            size = Size(g.rx * 2.44f, g.ry * 2.44f),
            alpha = 0.9f * intensity,
            style = Stroke(width = g.r * 0.035f),
            blendMode = BlendMode.Plus,
        )
    }
    for (k in 0 until 2) {
        val start = Math.toDegrees((-angle * 0.5f + time * 0.9f + k * PI).toDouble()).toFloat()
        drawArc(
            color = Color.White.copy(alpha = 0.55f * intensity),
            startAngle = start, sweepAngle = 70f, useCenter = false,
            topLeft = Offset(g.cx - g.rx * 1.36f, g.cy - g.ry * 1.36f), size = Size(g.rx * 2.72f, g.ry * 2.72f),
            style = Stroke(width = g.r * 0.02f, cap = StrokeCap.Round), blendMode = BlendMode.Plus,
        )
    }
}

/** Supernova: a white-hot bloom that the ball sits inside. */
private fun DrawScope.drawSupernovaBloom(g: RimGeometry, time: Float, intensity: Float) {
    val pulse = 0.85f + 0.15f * sin(time * 14f)
    auraOval(g, 1.5f, Color.White.copy(alpha = 0.55f * intensity * pulse))
    auraOval(g, 2.4f, Color(0xFFFFB347).copy(alpha = 0.3f * intensity))
}

/** Supernova: radial light streaks, slowly wheeling. */
private fun DrawScope.drawSupernovaStreaks(g: RimGeometry, angle: Float, time: Float, intensity: Float) {
    val n = 14
    for (i in 0 until n) {
        val t = i * TAU / n + angle * 0.1f + time * 0.3f
        val flicker = 0.5f + 0.5f * noise(i * 2.3f + time * 6f)
        val inner = g.point(t, 1.12f)
        val outer = g.point(t, 1.9f + 0.6f * flicker)
        drawLine(
            brush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.7f * intensity * flicker), Color.Transparent), inner, outer),
            start = inner, end = outer,
            strokeWidth = g.r * 0.022f, cap = StrokeCap.Round, blendMode = BlendMode.Plus,
        )
    }
}

/** Quantum: chromatic ghosts of the ball jittering out of phase. */
private fun DrawScope.drawQuantumGhosts(c: Offset, r: Float, p: Palette, time: Float, intensity: Float) {
    val tints = listOf(Color(0xFFFF3D6E), Color(0xFF3DFF8A), Color(0xFF3D8AFF))
    for ((k, tint) in tints.withIndex()) {
        val dx = noise(time * 31f + k * 7f) * r * 0.12f
        val dy = noise(time * 27f + k * 11f + 4f) * r * 0.08f
        drawCircle(
            color = lerp(p.body, tint, 0.6f).copy(alpha = 0.28f * intensity),
            radius = r,
            center = Offset(c.x + dx, c.y + dy),
            blendMode = BlendMode.Plus,
        )
    }
}

/** Quantum: horizontal slices of the ball displaced sideways, like a broken signal. */
private fun DrawScope.drawQuantumGlitch(c: Offset, r: Float, p: Palette, time: Float, intensity: Float) {
    val bucket = (time * 12f).toInt()
    for (k in 0 until 3) {
        if (hash(bucket * 3 + k) < 0.45f) continue
        val y0 = c.y - r + hash(bucket * 5 + k) * 2f * r
        val h = r * (0.04f + 0.1f * hash(bucket * 7 + k))
        val dx = (hash(bucket * 11 + k) - 0.5f) * r * 0.5f * intensity
        clipRect(left = c.x - r * 1.3f, top = y0, right = c.x + r * 1.3f, bottom = y0 + h) {
            translate(left = dx) {
                drawCircle(
                    brush = Brush.radialGradient(listOf(p.bodyLight, p.body, p.bodyDark), center = Offset(c.x - r * 0.35f, c.y - r * 0.4f), radius = r * 1.5f),
                    radius = r, center = c,
                )
            }
        }
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
            4 -> 70f
            else -> 50f
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

    fun draw(scope: DrawScope, tier: Int, tint: Color) {
        for (i in 0 until count) {
            val age = 1f - life[i] / maxLife[i]
            val alpha = (1f - age) * (1f - age)
            val color = if (tier >= 4) {
                lerp(Color(0xFFCFEBFF), Color(0xFF7CB8FF), age)
            } else {
                lerp(lerp(Color(0xFFFFF3C0), lerp(Color(0xFFFFB03A), tint, 0.3f), age), Color(0xFFE23A12), age * age)
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
        val iter = bolts.iterator()
        while (iter.hasNext()) {
            val b = iter.next()
            b.life -= dt
            if (b.life <= 0f) iter.remove()
        }
        val tier = view.fxTier
        val progress = view.fxTierProgress.toFloat()
        val rate = when (tier) {
            3 -> 3f + 10f * progress
            4 -> 9f
            5, 6 -> 6f
            7 -> 12f
            else -> 0f
        }
        accumulator += rate * dt
        while (accumulator >= 1f && bolts.size < 12) {
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
        val len = sqrt(dx * dx + dy * dy)
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

/** Singularity: starlight spiralling in from far away and vanishing at the horizon. */
private class StarSystem {
    private val max = 160
    private val phi = FloatArray(max)
    private val dist = FloatArray(max)
    private val speed = FloatArray(max)
    private val spin = FloatArray(max)
    private var count = 0
    private var accumulator = 0f

    fun update(dt: Float, view: GameView, g: RimGeometry) {
        val tier = view.fxTier
        val rate = if (tier >= 5) 40f + 40f * view.fxTierProgress.toFloat() else 0f
        accumulator += rate * dt
        while (accumulator >= 1f && count < max) {
            val i = count++
            phi[i] = Random.nextFloat() * TAU
            dist[i] = 2.4f + Random.nextFloat() * 0.6f
            speed[i] = 0.6f + Random.nextFloat() * 0.8f
            spin[i] = (if (view.direction >= 0) 1f else -1f) * (0.8f + Random.nextFloat() * 1.2f)
            accumulator -= 1f
        }
        if (accumulator > 4f) accumulator = 0f
        var i = 0
        while (i < count) {
            // Faster and tighter the closer it gets.
            val pull = 1f / dist[i]
            dist[i] -= speed[i] * pull * 1.6f * dt
            phi[i] += spin[i] * pull * dt
            if (dist[i] <= 1.06f) {
                val last = count - 1
                phi[i] = phi[last]; dist[i] = dist[last]; speed[i] = speed[last]; spin[i] = spin[last]
                count--
                continue
            }
            i++
        }
    }

    fun draw(scope: DrawScope) {
        // Geometry is re-derived from the scope's size so it matches the ball.
        val r = min(scope.size.width, scope.size.height) * 0.36f
        val c = Offset(scope.size.width / 2f, scope.size.height / 2f)
        for (i in 0 until count) {
            val d = dist[i]
            val fade = ((d - 1.06f) / 0.5f).coerceIn(0f, 1f)
            val head = Offset(c.x + r * d * cos(phi[i]), c.y + r * d * sin(phi[i]))
            val tail = Offset(c.x + r * (d + 0.12f / d) * cos(phi[i] - 0.08f * spin[i]), c.y + r * (d + 0.12f / d) * sin(phi[i] - 0.08f * spin[i]))
            scope.drawLine(
                Color.White.copy(alpha = 0.85f * (1f - 0.6f * fade)),
                tail, head,
                strokeWidth = r * 0.012f, cap = StrokeCap.Round, blendMode = BlendMode.Plus,
            )
        }
    }
}

/** Supernova: shockwave rings racing outward from the ball. */
private class ShockwaveSystem {
    private val age = FloatArray(6)
    private var count = 0
    private var timer = 0f
    private var rx = 0f
    private var ry = 0f
    private var cx = 0f
    private var cy = 0f
    private var r = 0f

    fun update(dt: Float, view: GameView, g: RimGeometry) {
        rx = g.rx; ry = g.ry; cx = g.cx; cy = g.cy; r = g.r
        var i = 0
        while (i < count) {
            age[i] += dt
            if (age[i] >= 1.2f) { age[i] = age[count - 1]; count--; continue }
            i++
        }
        if (view.fxTier >= 6) {
            timer -= dt
            if (timer <= 0f && count < age.size) {
                age[count++] = 0f
                timer = 0.55f + Random.nextFloat() * 0.3f
            }
        } else {
            timer = 0f
        }
    }

    fun draw(scope: DrawScope) {
        for (i in 0 until count) {
            val t = age[i] / 1.2f
            val scale = 1f + t * 1.8f
            val alpha = (1f - t) * (1f - t)
            scope.drawOval(
                color = lerp(Color.White, Color(0xFFFFB347), t).copy(alpha = 0.8f * alpha),
                topLeft = Offset(cx - rx * scale, cy - ry * scale),
                size = Size(rx * 2f * scale, ry * 2f * scale),
                style = Stroke(width = r * (0.08f - 0.06f * t)),
                blendMode = BlendMode.Plus,
            )
        }
    }
}
