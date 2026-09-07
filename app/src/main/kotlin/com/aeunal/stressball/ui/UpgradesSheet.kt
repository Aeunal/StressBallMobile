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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aeunal.stressball.core.GameView
import com.aeunal.stressball.core.NumberFormat
import com.aeunal.stressball.core.Stats
import com.aeunal.stressball.core.UpgradeCategory
import com.aeunal.stressball.core.UpgradeDef
import com.aeunal.stressball.core.Upgrades
import com.aeunal.stressball.ui.theme.BallColors

@Composable
fun UpgradesSheet(
    view: GameView,
    onBuy: (String) -> Unit,
    onPrestige: () -> Unit,
) {
    val categories = UpgradeCategory.entries
    var tab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Upgrades", style = MaterialTheme.typography.headlineMedium)
            Text(
                "${NumberFormat.compact(view.points, 0)} pts",
                style = MaterialTheme.typography.titleMedium,
                color = BallColors.GreenLight,
            )
        }
        Spacer(Modifier.height(8.dp))
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
            categories.forEachIndexed { i, c ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(c.title) })
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
                Text(def.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Lv $level / ${def.maxLevel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                def.tagline,
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
                    Text("Now: ${def.describe(level)}", style = MaterialTheme.typography.bodySmall)
                    if (!maxed) {
                        Text(
                            "Next: ${def.describe(level + 1)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = BallColors.GreenLight,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Button(onClick = onBuy, enabled = affordable) {
                    Text(
                        if (maxed) "MAX" else NumberFormat.compact(cost, 0),
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
            Text("☯ Zen reset", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Give up this run for permanent +10% income per Zen. " +
                    "Zen so far: ${view.state.zen} (x${NumberFormat.compact(view.zenMultiplier, 2)}).",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "This run: ${NumberFormat.compact(view.state.pointsThisRun)} pts. " +
                    "Next Zen at ${NumberFormat.compact(next, 0)} pts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onPrestige, enabled = gain > 0, modifier = Modifier.fillMaxWidth()) {
                Text(if (gain > 0) "Reset for $gain Zen" else "Earn 1M points this run to unlock")
            }
        }
    }
}
