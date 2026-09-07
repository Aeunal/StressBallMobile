package com.aeunal.stressball.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeunal.stressball.R
import com.aeunal.stressball.core.GameView
import com.aeunal.stressball.core.Language
import com.aeunal.stressball.core.NumberFormat
import com.aeunal.stressball.game.GameEvent
import com.aeunal.stressball.game.GameViewModel
import com.aeunal.stressball.ui.theme.BallColors
import kotlin.math.min

private enum class Sheet { NONE, UPGRADES, STYLE, STATS }

/** Must match the radius [BallCanvas] draws with, so touch geometry lines up with the picture. */
const val BALL_RADIUS_FRACTION = 0.36f

@Composable
fun GameScreen(viewModel: GameViewModel) {
    val language by viewModel.language.collectAsStateWithLifecycle()
    ProvideAppLanguage(language) {
        GameContent(viewModel = viewModel, language = language)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameContent(viewModel: GameViewModel, language: Language?) {
    val view by viewModel.view.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    var sheet by remember { mutableStateOf(Sheet.NONE) }
    var offlineDialog by remember { mutableStateOf<GameEvent.Offline?>(null) }
    var prestigeDialog by remember { mutableStateOf(false) }
    var ballRadius by remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current
    val text = LocalGameText.current

    // Drain one-shot events into the right surface.
    LaunchedEffect(events, context, text) {
        val event = events.firstOrNull() ?: return@LaunchedEffect
        when (event) {
            is GameEvent.Offline -> offlineDialog = event
            is GameEvent.AchievementUnlocked -> {
                viewModel.consumeEvent(event)
                snackbar.showSnackbar(context.getString(R.string.achievement_toast, text.achievementTitle(event.id)))
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (!loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Header(view)
            Spacer(Modifier.height(8.dp))
            RpmMeter(view)
            Spacer(Modifier.height(6.dp))
            TurboBar(view)

            BallCanvas(
                view = view,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onSizeChanged { ballRadius = min(it.width, it.height) * BALL_RADIUS_FRACTION }
                    .spinGesture(
                        ballRadius = { ballRadius },
                        onDown = viewModel::onFingerDown,
                        onMove = viewModel::onFingerMove,
                        onUp = viewModel::onFingerUp,
                        onPinch = viewModel::onPinch,
                        onPinchEnd = viewModel::onPinchEnd,
                    ),
            )

            Hint(view)
            Spacer(Modifier.height(12.dp))
            ActionBar(
                onUpgrades = { sheet = Sheet.UPGRADES },
                onStyle = { sheet = Sheet.STYLE },
                onStats = { sheet = Sheet.STATS },
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (sheet != Sheet.NONE) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { sheet = Sheet.NONE },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            when (sheet) {
                Sheet.UPGRADES -> UpgradesSheet(
                    view = view,
                    onBuy = viewModel::buy,
                    onPrestige = { prestigeDialog = true },
                )
                Sheet.STYLE -> StyleSheet(
                    view = view,
                    language = language,
                    onBuy = viewModel::buyCosmetic,
                    onEquip = viewModel::equipCosmetic,
                    onLanguage = viewModel::setLanguage,
                )
                Sheet.STATS -> StatsSheet(view)
                Sheet.NONE -> Unit
            }
        }
    }

    offlineDialog?.let { event ->
        val r = event.report
        AlertDialog(
            onDismissRequest = { viewModel.consumeEvent(event); offlineDialog = null },
            confirmButton = {
                TextButton(onClick = { viewModel.consumeEvent(event); offlineDialog = null }) {
                    Text(stringResource(R.string.ok))
                }
            },
            title = { Text(stringResource(R.string.welcome_back_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.welcome_back_body,
                        NumberFormat.duration(r.secondsAway),
                        NumberFormat.duration(r.secondsCredited),
                        (r.efficiency * 100).toInt(),
                        NumberFormat.compact(r.pointsEarned),
                    ),
                )
            },
        )
    }

    if (prestigeDialog) {
        AlertDialog(
            onDismissRequest = { prestigeDialog = false },
            confirmButton = {
                Button(onClick = {
                    prestigeDialog = false
                    sheet = Sheet.NONE
                    viewModel.prestige()
                }) { Text(stringResource(R.string.zen_reset_confirm, view.zenOnReset)) }
            },
            dismissButton = {
                TextButton(onClick = { prestigeDialog = false }) { Text(stringResource(R.string.keep_spinning)) }
            },
            title = { Text(stringResource(R.string.zen_reset_title)) },
            text = { Text(stringResource(R.string.zen_reset_body, view.zenOnReset)) },
        )
    }
}

@Composable
private fun Header(view: GameView) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            Text(
                text = NumberFormat.compact(view.points, 0),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(
                    R.string.rate_format,
                    NumberFormat.compact(view.pointsPerSecond),
                    NumberFormat.compact(view.totalMultiplier, 2),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "☯ ${view.state.zen}",
                style = MaterialTheme.typography.titleMedium,
                color = BallColors.GreenLight,
            )
            Text(
                text = stringResource(R.string.zen_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RpmMeter(view: GameView) {
    val fill = (view.rpm / view.rpmCap).coerceIn(0.0, 1.0).toFloat()
    val barColor = when {
        view.turboOverheating -> BallColors.Heat
        view.turboBoosting -> BallColors.Overdrive
        fill > 0.85f -> BallColors.Heat
        else -> BallColors.GreenLight
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.rpm_format, NumberFormat.integer(view.rpm)),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.cap_format, NumberFormat.integer(view.rpmCap)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (view.state.bestRpm > 0) {
                    Text(
                        text = stringResource(R.string.best_format, NumberFormat.integer(view.state.bestRpm)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { fill },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (view.petalsOpen) {
                StatusPill(stringResource(R.string.badge_petals, NumberFormat.compact(view.petalMultiplier, 1)), BallColors.GreenLight)
            }
            if (view.comboMultiplier > 1.001) {
                StatusPill(stringResource(R.string.badge_resonance, NumberFormat.compact(view.comboMultiplier, 2)), BallColors.Heat)
            }
            if (view.turboBoosting) StatusPill(stringResource(R.string.badge_turbo), BallColors.Overdrive)
            if (view.turboOverheating) StatusPill(stringResource(R.string.badge_overheat), BallColors.Heat)
            if (view.motorRpm > 0 && view.rpm <= view.motorRpm + 0.5 && !view.fingerTouching) {
                StatusPill(stringResource(R.string.badge_motor), MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Fuel gauge for the squeeze turbo: fills with fuel, drains while the ball is
 * pinched, sits grey through the cooldown, then refills.
 */
@Composable
private fun TurboBar(view: GameView) {
    val charge = view.turboCharge.toFloat().coerceIn(0f, 1f)
    val incomeMult = 1.0 + view.turboWeight * (com.aeunal.stressball.core.Stats.TURBO_INCOME_MULT - 1.0)
    val status = when {
        view.turboBoosting -> stringResource(R.string.turbo_active, NumberFormat.compact(incomeMult, 2))
        view.turboOverheating -> stringResource(R.string.turbo_empty)
        view.turboCooldown > 0.0 -> stringResource(R.string.turbo_cooldown, NumberFormat.duration(view.turboCooldown))
        charge < 1f -> stringResource(R.string.turbo_refilling)
        else -> stringResource(R.string.turbo_ready)
    }
    val color = when {
        view.turboOverheating -> BallColors.Heat
        view.turboBoosting -> BallColors.Overdrive
        view.turboCooldown > 0.0 -> MaterialTheme.colorScheme.outline
        else -> BallColors.Red
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.turbo_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(status, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { charge },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun Hint(view: GameView) {
    val revolutions = view.state.totalRevolutions
    val idle = !view.fingerTouching && view.turboWeight == 0.0
    val message = when {
        revolutions < 30 -> stringResource(R.string.hint_draw_circles)
        view.fingerTouching && view.rpm < 5 -> stringResource(R.string.hint_keep_circling)
        idle && view.rpm > 200 && revolutions < 400 -> stringResource(R.string.hint_hold_to_brake)
        idle && revolutions in 400.0..1500.0 -> stringResource(R.string.hint_pinch_turbo)
        else -> ""
    }
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().height(20.dp),
    )
}

@Composable
private fun ActionBar(
    onUpgrades: () -> Unit,
    onStyle: () -> Unit,
    onStats: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalButton(onClick = onUpgrades, modifier = Modifier.weight(1f).height(56.dp)) {
            Icon(Icons.Filled.Build, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.upgrades_button))
        }
        FilledTonalButton(onClick = onStyle, modifier = Modifier.height(56.dp)) {
            Icon(Icons.Filled.Star, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.style_button))
        }
        FilledTonalButton(onClick = onStats, modifier = Modifier.height(56.dp)) {
            Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.stats_button))
        }
    }
}
