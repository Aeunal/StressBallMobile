package com.aeunal.stressball.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aeunal.stressball.R
import com.aeunal.stressball.core.GameView
import com.aeunal.stressball.core.NumberFormat
import com.aeunal.stressball.core.Stats
import com.aeunal.stressball.core.UpgradeCategory
import com.aeunal.stressball.core.UpgradeDef
import com.aeunal.stressball.core.UpgradeScope
import com.aeunal.stressball.core.Upgrades
import com.aeunal.stressball.ui.theme.BallColors

@Composable
fun UpgradesSheet(
    view: GameView,
    onBuy: (String) -> Unit,
    onPrestige: () -> Unit,
) {
    val text = LocalGameText.current
    val categories = UpgradeCategory.entries
    var tab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.upgrades_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.points_short, NumberFormat.compact(view.points, 0)),
                style = MaterialTheme.typography.titleMedium,
                color = BallColors.GreenLight,
            )
        }
        Text(
            stringResource(R.string.upgrades_hint, Stats.GEMS_PER_MAX),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        ScrollableTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface, edgePadding = 12.dp) {
            categories.forEachIndexed { i, c ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(text.category(c)) })
            }
        }
        val defs = Upgrades.all.filter { it.category == categories[tab] }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(440.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(defs, key = { it.id }) { def ->
                UpgradeCard(def = def, view = view, onBuy = { onBuy(def.id) })
            }
            if (categories[tab] == UpgradeCategory.SPECIAL) {
                item { ZenCard(view = view, onPrestige = onPrestige) }
            }
        }
    }
}

@Composable
private fun UpgradeCard(def: UpgradeDef, view: GameView, onBuy: () -> Unit) {
    val text = LocalGameText.current
    val level = view.state.level(def.id)
    val maxed = level >= def.maxLevel
    val cost = def.costAt(level)
    val affordable = !maxed && view.points >= cost

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text.upgradeName(def.id), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.level_format, level, def.maxLevel) +
                        if (def.scope == UpgradeScope.ACCOUNT) "  •  " + stringResource(R.string.scope_account) else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text.upgradeTagline(def.id),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.now_format, text.upgradeEffect(def.id, level)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (!maxed) {
                        Text(
                            stringResource(R.string.next_format, text.upgradeEffect(def.id, level + 1)),
                            style = MaterialTheme.typography.bodySmall,
                            color = BallColors.GreenLight,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Button(onClick = onBuy, enabled = affordable) {
                    Text(
                        if (maxed) stringResource(R.string.max_label) else NumberFormat.compact(cost, 0),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ZenCard(view: GameView, onPrestige: () -> Unit) {
    val gain = view.zenOnReset
    val next = Stats.ZEN_UNIT_POINTS * ((gain + 1) * (gain + 1)).toDouble()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BallColors.GreenDark),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.zen_card_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.zen_card_body, view.state.zen, NumberFormat.compact(view.zenMultiplier, 2)),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(
                    R.string.zen_card_run,
                    NumberFormat.compact(view.ball.pointsThisRun),
                    NumberFormat.compact(next, 0),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onPrestige, enabled = gain > 0, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (gain > 0) stringResource(R.string.zen_card_button, gain)
                    else stringResource(R.string.zen_card_locked),
                )
            }
        }
    }
}
