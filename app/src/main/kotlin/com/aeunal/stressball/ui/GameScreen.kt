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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeunal.stressball.core.Achievements
import com.aeunal.stressball.core.GameView
import com.aeunal.stressball.core.NumberFormat
import com.aeunal.stressball.game.GameEvent
import com.aeunal.stressball.game.GameViewModel
import com.aeunal.stressball.ui.theme.BallColors

private enum class Sheet { NONE, UPGRADES, STATS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(viewModel: GameViewModel) {
    val view by viewModel.view.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val direction by viewModel.spinDirection.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    var sheet by remember { mutableStateOf(Sheet.NONE) }
    var offlineDialog by remember { mutableStateOf<GameEvent.Offline?>(null) }
    var prestigeDialog by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    // Drain one-shot events into the right surface.
    LaunchedEffect(events) {
        val event = events.firstOrNull() ?: return@LaunchedEffect
        when (event) {
            is GameEvent.Offline -> offlineDialog = event
            is GameEvent.AchievementUnlocked -> {
                val def = Achievements.byId[event.id]
                viewModel.consumeEvent(event)
                if (def != null) snackbar.showSnackbar("Achievement: ${def.title} (+1 Zen)")
            }
            GameEvent.OverdriveFired -> {
                viewModel.consumeEvent(event)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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

            BallCanvas(
                view = view,
                direction = direction,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .spinGesture(
                        onSpin = viewModel::onFingerSpin,
                        onLift = viewModel::onFingerLift,
                    ),
            )

            Hint(view)
            Spacer(Modifier.height(12.dp))
            ActionBar(
                view = view,
                onOverdrive = viewModel::overdrive,
                onUpgrades = { sheet = Sheet.UPGRADES },
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
                TextButton(onClick = { viewModel.consumeEvent(event); offlineDialog = null }) { Text("Nice") }
            },
            title = { Text("Welcome back") },
            text = {
                Text(
                    "You were away for ${NumberFormat.duration(r.secondsAway)}. " +
                        "Gyro Memory kept the motor turning for ${NumberFormat.duration(r.secondsCredited)} " +
                        "at ${(r.efficiency * 100).toInt()}% efficiency and earned " +
                        "${NumberFormat.compact(r.pointsEarned)} points.",
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
                }) { Text("Reset for ${view.zenOnReset} Zen") }
            },
            dismissButton = { TextButton(onClick = { prestigeDialog = false }) { Text("Keep spinning") } },
            title = { Text("Zen reset") },
            text = {
                Text(
                    "Let go of all points, upgrades and speed. In return you gain ${view.zenOnReset} Zen, " +
                        "each permanently adding +10% to all income. Achievements and records stay.",
                )
            },
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
                text = "${NumberFormat.compact(view.pointsPerSecond)} / s  •  x${NumberFormat.compact(view.totalMultiplier, 2)}",
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
                text = "Zen",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RpmMeter(view: GameView) {
    val heat = view.heat.toFloat()
    val barColor = when {
        view.overdriveActive -> BallColors.Overdrive
        heat > 0.85f -> BallColors.Heat
        else -> BallColors.GreenLight
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "${NumberFormat.integer(view.rpm)} RPM",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "max ${NumberFormat.integer(view.rpmCap)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (view.state.bestRpm > 0) {
                    Text(
                        text = "best ${NumberFormat.integer(view.state.bestRpm)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { heat },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (view.petalsOpen) StatusPill("Petals open x${NumberFormat.compact(view.petalMultiplier, 1)}", BallColors.GreenLight)
            if (view.comboMultiplier > 1.001) StatusPill("Resonance x${NumberFormat.compact(view.comboMultiplier, 2)}", BallColors.Heat)
            if (view.overdriveActive) StatusPill("OVERDRIVE x2", BallColors.Overdrive)
            if (view.motorRpm > 0 && view.rpm <= view.motorRpm + 0.5) StatusPill("Motor idling", MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
    val text = when {
        view.state.totalRevolutions < 30 -> "Draw circles on the ball to spin it"
        view.rpm < 5 && view.motorRpm == 0.0 -> "Keep circling. Faster circles, more RPM"
        else -> ""
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().height(20.dp),
    )
}

@Composable
private fun ActionBar(
    view: GameView,
    onOverdrive: () -> Unit,
    onUpgrades: () -> Unit,
    onStats: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onOverdrive,
            enabled = view.overdriveReady,
            modifier = Modifier.weight(1f).height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BallColors.Red,
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = when {
                    !view.overdriveUnlocked -> "Overdrive (locked)"
                    view.overdriveReady -> "OVERDRIVE"
                    else -> "Overdrive in ${NumberFormat.duration(view.overdriveCooldown)}"
                },
                fontWeight = FontWeight.Bold,
            )
        }
        FilledTonalButton(onClick = onUpgrades, modifier = Modifier.height(56.dp)) {
            Icon(Icons.Filled.Build, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Upgrades")
        }
        FilledTonalButton(onClick = onStats, modifier = Modifier.height(56.dp)) {
            Icon(Icons.Filled.Info, contentDescription = "Stats")
        }
    }
}
