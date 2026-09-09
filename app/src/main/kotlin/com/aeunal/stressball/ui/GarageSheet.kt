package com.aeunal.stressball.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aeunal.stressball.R
import com.aeunal.stressball.core.BallSummary
import com.aeunal.stressball.core.BallTypes
import com.aeunal.stressball.core.GameView
import com.aeunal.stressball.core.NumberFormat
import com.aeunal.stressball.core.Rarity
import com.aeunal.stressball.core.Stats
import com.aeunal.stressball.core.Upgrades
import com.aeunal.stressball.ui.theme.BallColors

/** Colour used for a rarity's label and glow. */
fun rarityColor(rarity: Rarity): Color = when (rarity) {
    Rarity.COMMON -> Color(0xFFB9B9C8)
    Rarity.RARE -> Color(0xFF63B3FF)
    Rarity.VERY_RARE -> Color(0xFF7CF7C0)
    Rarity.LEGENDARY -> Color(0xFFFFB347)
    Rarity.MYTHIC -> Color(0xFFFF3DDB)
    Rarity.EXOTIC -> Color(0xFFB8F1FF)
}

/** A small shaded sphere for lists and dialogs. */
@Composable
fun BallIcon(typeId: String, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val type = BallTypes.get(typeId)
    val body = Color(type.body.toInt())
    val cap = Color(type.cap.toInt())
    Box(modifier.size(size), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .size(size)
                .background(
                    Brush.radialGradient(
                        colors = listOf(lerp(body, Color.White, 0.35f), body, lerp(body, Color.Black, 0.45f)),
                        center = androidx.compose.ui.geometry.Offset(size.value * 1.1f, size.value * 1.0f),
                        radius = size.value * 4.5f,
                    ),
                    CircleShape,
                ),
        )
        Box(
            Modifier
                .padding(top = size * 0.02f)
                .size(width = size * 0.34f, height = size * 0.14f)
                .background(lerp(cap, Color.White, 0.15f), RoundedCornerShape(50)),
        )
    }
}

/** The garage: every ball the player owns, plus the chest. */
@Composable
fun GarageSheet(
    view: GameView,
    onSwitch: (String) -> Unit,
    onSell: (BallSummary) -> Unit,
    onOpenChest: () -> Unit,
) {
    val text = LocalGameText.current
    val luck = view.state.accountLevel(Upgrades.CHEST_LUCK)
    val legendaryOdds = BallTypes.odds(luck).filterKeys { it >= Rarity.LEGENDARY }.values.sum() * 100.0

    LazyColumn(
        modifier = Modifier.fillMaxWidth().height(560.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.garage_title), style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.points_short, NumberFormat.compact(view.points, 0)),
                    style = MaterialTheme.typography.titleMedium,
                    color = BallColors.GreenLight,
                )
            }
            if (view.garageIncomePerSecond > 0.0) {
                Text(
                    stringResource(R.string.garage_income, NumberFormat.compact(view.garageIncomePerSecond)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BallColors.GreenDark),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.chest_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.chest_body, view.state.balls.size, Stats.MAX_BALLS),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        stringResource(R.string.chest_odds, NumberFormat.compact(legendaryOdds, 1)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onOpenChest, enabled = view.canOpenChest, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (view.garageFull) stringResource(R.string.chest_full)
                            else stringResource(R.string.chest_open, NumberFormat.compact(view.chestCost, 0)),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        items(view.balls, key = { it.id }) { ball ->
            BallCard(
                ball = ball,
                name = text.ballTypeName(ball.typeId),
                rarityName = text.rarityName(ball.rarity),
                traits = text.traitsDescription(BallTypes.get(ball.typeId).traits),
                canSell = view.balls.size > 1,
                onSwitch = { onSwitch(ball.id) },
                onSell = { onSell(ball) },
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun BallCard(
    ball: BallSummary,
    name: String,
    rarityName: String,
    traits: String,
    canSell: Boolean,
    onSwitch: () -> Unit,
    onSell: () -> Unit,
) {
    val accent = rarityColor(ball.rarity)
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (ball.isActive) lerp(MaterialTheme.colorScheme.surfaceVariant, accent, 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            BallIcon(ball.typeId, 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(rarityName, style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.SemiBold)
                }
                Text(traits, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(
                        R.string.ball_stats,
                        NumberFormat.integer(ball.rpm),
                        NumberFormat.compact(ball.pointsPerSecond),
                        ball.upgradeLevels,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ball.isActive) {
                        Text(
                            stringResource(R.string.ball_active),
                            style = MaterialTheme.typography.labelMedium,
                            color = BallColors.GreenLight,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        Button(onClick = onSwitch, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                            Text(stringResource(R.string.ball_switch), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    OutlinedButton(onClick = onSell, enabled = canSell, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                        Text(stringResource(R.string.ball_sell, NumberFormat.compact(ball.sellValue, 0)), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
