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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aeunal.stressball.R
import com.aeunal.stressball.core.GameView
import com.aeunal.stressball.core.Language
import com.aeunal.stressball.core.SkinDef
import com.aeunal.stressball.core.SkinSlot
import com.aeunal.stressball.core.Skins
import com.aeunal.stressball.ui.theme.BallColors

/** Skins bought with gems (one outer, one interior active), plus the language setting. */
@Composable
fun SkinsSheet(
    view: GameView,
    language: Language?,
    onBuy: (String) -> Unit,
    onEquip: (String) -> Unit,
    onUnequip: (SkinSlot) -> Unit,
    onTopUp: () -> Unit,
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
            Text(stringResource(R.string.skins_title), style = MaterialTheme.typography.headlineMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.gems_format, view.gems),
                    style = MaterialTheme.typography.titleMedium,
                    color = BallColors.Overdrive,
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onTopUp, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                    Text(stringResource(R.string.gems_top_up), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text(
            stringResource(R.string.gems_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        for (slot in SkinSlot.entries) {
            val equipped = Skins.equipped(view.state, slot)
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text.slotName(slot), style = MaterialTheme.typography.titleMedium)
                if (equipped != null) {
                    OutlinedButton(onClick = { onUnequip(slot) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                        Text(stringResource(R.string.skin_unequip), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(Skins.forSlot(slot), key = { it.id }) { def ->
                    SkinCard(
                        def = def,
                        name = text.skinName(def.id),
                        description = text.skinDescription(def.id),
                        owned = Skins.isOwned(view.state, def.id),
                        equipped = def.id == equipped?.id,
                        affordable = view.gems >= def.gems,
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
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun SkinCard(
    def: SkinDef,
    name: String,
    description: String,
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
            .width(128.dp)
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
                        colors = listOf(lerp(color, Color.White, 0.4f), color, lerp(color, Color.Black, 0.6f)),
                        center = androidx.compose.ui.geometry.Offset(20f, 18f),
                        radius = 80f,
                    ),
                    CircleShape,
                ),
        )
        Spacer(Modifier.height(6.dp))
        Text(name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1)
        Text(
            description,
            style = MaterialTheme.typography.labelSmall,
            color = BallColors.GreenLight,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
        )
        Spacer(Modifier.height(6.dp))
        when {
            equipped -> Text(
                stringResource(R.string.skin_equipped),
                style = MaterialTheme.typography.labelSmall,
                color = BallColors.GreenLight,
                fontWeight = FontWeight.SemiBold,
            )
            owned -> Button(onClick = onEquip, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                Text(stringResource(R.string.skin_equip), style = MaterialTheme.typography.labelSmall)
            }
            else -> Button(
                onClick = onBuy,
                enabled = affordable,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) {
                Text(stringResource(R.string.gems_format, def.gems), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}
