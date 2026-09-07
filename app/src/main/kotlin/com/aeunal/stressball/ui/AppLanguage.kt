package com.aeunal.stressball.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.aeunal.stressball.core.EnglishText
import com.aeunal.stressball.core.GameText
import com.aeunal.stressball.core.GameTexts
import com.aeunal.stressball.core.Language
import java.util.Locale

/** The engine's text in the language the UI is showing. */
val LocalGameText = staticCompositionLocalOf<GameText> { EnglishText }

/** Language the device is set to, mapped to one the game ships (English otherwise). */
fun deviceLanguage(): Language = Language.fromCode(Locale.getDefault().language) ?: Language.EN

/**
 * Applies an in-app language choice. With [override] null the device language
 * is used. String resources are re-resolved through a context configured for
 * the chosen locale, and [LocalGameText] follows the same choice.
 */
@Composable
fun ProvideAppLanguage(override: Language?, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val effective = override ?: deviceLanguage()
    val context = remember(base, override) {
        if (override == null) {
            base
        } else {
            val config = Configuration(base.resources.configuration)
            config.setLocale(Locale.forLanguageTag(override.code))
            base.createConfigurationContext(config)
        }
    }
    CompositionLocalProvider(
        LocalContext provides context,
        LocalConfiguration provides context.resources.configuration,
        LocalResources provides context.resources,
        LocalGameText provides GameTexts.of(effective),
        content = content,
    )
}
