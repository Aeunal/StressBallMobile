package com.aeunal.stressball.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aeunal.stressball.core.Achievements
import com.aeunal.stressball.core.GameView
import com.aeunal.stressball.core.NumberFormat
import com.aeunal.stressball.ui.theme.BallColors

@Composable
fun StatsSheet(view: GameView) {
    val s = view.state
    val rows = listOf(
        "Best RPM" to NumberFormat.integer(s.bestRpm),
        "Total revolutions" to NumberFormat.compact(s.totalRevolutions),
        "Lifetime points" to NumberFormat.compact(s.totalPointsEarned),
        "Points this run" to NumberFormat.compact(s.pointsThisRun),
        "Zen" to "${s.zen} (x${NumberFormat.compact(view.zenMultiplier, 2)})",
        "Zen resets" to s.prestigeCount.toString(),
        "Play time" to NumberFormat.duration(s.playTimeSeconds),
        "Achievements" to "${s.achievements.size} / ${Achievements.all.size}",
    )

    LazyColumn(
        modifier = Modifier.fillMaxWidth().height(520.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    ) {
        item {
            Text("Stats", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
        }
        items(rows) { (label, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value)
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            Text("Challenges", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
        }
        items(Achievements.all, key = { it.id }) { def ->
            val unlocked = def.id in s.achievements
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(def.title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        def.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (unlocked) "✓" else "+1 Zen",
                    color = if (unlocked) BallColors.GreenLight else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
