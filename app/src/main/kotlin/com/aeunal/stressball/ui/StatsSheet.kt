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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aeunal.stressball.R
import com.aeunal.stressball.core.Achievements
import com.aeunal.stressball.core.GameView
import com.aeunal.stressball.core.NumberFormat
import com.aeunal.stressball.ui.theme.BallColors

@Composable
fun StatsSheet(view: GameView) {
    val text = LocalGameText.current
    val s = view.state
    val rows = listOf(
        stringResource(R.string.stat_best_rpm) to NumberFormat.integer(s.balls.maxOf { it.bestRpm }),
        stringResource(R.string.stat_revolutions) to NumberFormat.compact(s.balls.sumOf { it.totalRevolutions }),
        stringResource(R.string.stat_lifetime_points) to NumberFormat.compact(s.totalPointsEarned),
        stringResource(R.string.stat_run_points) to NumberFormat.compact(view.ball.pointsThisRun),
        stringResource(R.string.stat_balls) to "${s.balls.size} (${s.chestsOpened} ${stringResource(R.string.stat_chests)}, ${s.ballsSold} ${stringResource(R.string.stat_sold)})",
        stringResource(R.string.stat_gems) to s.gems.toString(),
        stringResource(R.string.stat_zen) to "${s.zen} (x${NumberFormat.compact(view.zenMultiplier, 2)})",
        stringResource(R.string.stat_resets) to s.balls.sumOf { it.prestigeCount }.toString(),
        stringResource(R.string.stat_sparks) to s.sparksTapped.toString(),
        stringResource(R.string.stat_play_time) to NumberFormat.duration(s.playTimeSeconds),
        stringResource(R.string.stat_achievements) to "${s.achievements.size} / ${Achievements.all.size}",
    )

    LazyColumn(
        modifier = Modifier.fillMaxWidth().height(560.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    ) {
        item {
            Text(stringResource(R.string.stats_title), style = MaterialTheme.typography.headlineMedium)
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
            Text(stringResource(R.string.challenges_title), style = MaterialTheme.typography.titleMedium)
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
                    Text(text.achievementTitle(def.id), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text.achievementDescription(def.id),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (unlocked) "✓" else stringResource(R.string.challenge_reward),
                    color = if (unlocked) BallColors.GreenLight else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
