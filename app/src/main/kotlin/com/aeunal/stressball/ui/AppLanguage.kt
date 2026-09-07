package com.aeunal.stressball.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.aeunal.stressball.core.GameText
import com.aeunal.stressball.core.GameTexts
import com.aeunal.stressball.core.Language
import com.aeunal.stressball.core.TurkishText
import java.util.Locale

/** The engine's text in the language the UI is showing. */
val LocalGameText = staticCompositionLocalOf<GameText> { TurkishText }

/** The language shown when the player has not chosen one. */
val DEFAULT_LANGUAGE: Language = Language.TR

/**
 * Applies the in-app language. With [override] null the game's default
 * ([DEFAULT_LANGUAGE]) is used regardless of the device locale. String
 * resources are re-resolved through a context configured for the chosen
 * locale, and [LocalGameText] follows the same choice.
 */
@Composable
fun ProvideAppLanguage(override: Language?, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val effective = override ?: DEFAULT_LANGUAGE
    val context = remember(base, effective) {
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(effective.code))
        base.createConfigurationContext(config)
    }
    CompositionLocalProvider(
        LocalContext provides context,
        LocalConfiguration provides context.resources.configuration,
        LocalResources provides context.resources,
        LocalGameText provides GameTexts.of(effective),
        content = content,
    )
}
