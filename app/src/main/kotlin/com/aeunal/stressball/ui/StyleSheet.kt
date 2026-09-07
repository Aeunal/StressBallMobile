package com.aeunal.stressball.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aeunal.stressball.R
import com.aeunal.stressball.core.CosmeticDef
import com.aeunal.stressball.core.CosmeticSlot
import com.aeunal.stressball.core.Cosmetics
import com.aeunal.stressball.core.GameView
import com.aeunal.stressball.core.Language
import com.aeunal.stressball.core.NumberFormat
import com.aeunal.stressball.ui.theme.BallColors

/** Cosmetics bought with points, plus the language setting. */
@Composable
fun StyleSheet(
    view: GameView,
    language: Language?,
    onBuy: (String) -> Unit,
    onEquip: (String) -> Unit,
    onLanguage: (Language?) -> Unit,
) {
    val text = LocalGameText.current
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.style_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.points_short, NumberFormat.compact(view.points, 0)),
                style = MaterialTheme.typography.titleMedium,
                color = BallColors.GreenLight,
            )
        }

        for (slot in CosmeticSlot.entries) {
            Spacer(Modifier.height(16.dp))
            Text(
                text.slotName(slot),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))
            val equippedId = Cosmetics.equipped(view.state, slot).id
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(Cosmetics.forSlot(slot), key = { it.id }) { def ->
                    Swatch(
                        def = def,
                        name = text.cosmeticName(def.id),
                        owned = Cosmetics.isOwned(view.state, def.id),
                        equipped = def.id == equippedId,
                        affordable = view.points >= def.cost,
                        onBuy = { onBuy(def.id) },
                        onEquip = { onEquip(def.id) },
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.language_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val effective = language ?: DEFAULT_LANGUAGE
            FilterChip(
                selected = effective == Language.TR,
                onClick = { onLanguage(Language.TR) },
                label = { Text(stringResource(R.string.language_tr)) },
            )
            FilterChip(
                selected = effective == Language.EN,
                onClick = { onLanguage(Language.EN) },
                label = { Text(stringResource(R.string.language_en)) },
            )
        }
    }
}

@Composable
private fun Swatch(
    def: CosmeticDef,
    name: String,
    owned: Boolean,
    equipped: Boolean,
    affordable: Boolean,
    onBuy: () -> Unit,
    onEquip: () -> Unit,
) {
    val color = Color(def.color.toInt())
    val outline = if (equipped) BallColors.GreenLight else MaterialTheme.colorScheme.outline
    Column(
        modifier = Modifier
            .width(104.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .border(2.dp, outline, RoundedCornerShape(16.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(lerp(color, Color.White, 0.35f), color, lerp(color, Color.Black, 0.45f)),
                        center = androidx.compose.ui.geometry.Offset(20f, 18f),
                        radius = 80f,
                    ),
                    CircleShape,
                ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        when {
            equipped -> Text(
                stringResource(R.string.equipped_label),
                style = MaterialTheme.typography.labelSmall,
                color = BallColors.GreenLight,
                fontWeight = FontWeight.SemiBold,
            )
            owned -> Button(onClick = onEquip, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                Text(stringResource(R.string.equip_button), style = MaterialTheme.typography.labelSmall)
            }
            else -> Button(
                onClick = onBuy,
                enabled = affordable,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) {
                Text(NumberFormat.compact(def.cost, 0), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}
