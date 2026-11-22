// AiTranslatorSettingsScreen.kt

package com.mochyy.mochymusic.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mochyy.mochymusic.constants.AiTranslatorConfig
import com.mochyy.mochymusic.constants.CustomAiModelKey
import com.mochyy.mochymusic.constants.CustomAiBaseUrlKey
import com.mochyy.mochymusic.constants.CustomAiApiKey
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.AiProviderKey
import com.mochyy.mochymusic.constants.DeeplApiKey
import com.mochyy.mochymusic.constants.OpenRouterApiKey
import com.mochyy.mochymusic.constants.OpenRouterBaseUrlKey
import com.mochyy.mochymusic.constants.OpenRouterModelKey
import com.mochyy.mochymusic.constants.TranslateLanguageKey
import com.mochyy.mochymusic.constants.TranslateLyricsKey
import com.mochyy.mochymusic.ui.component.DefaultDialog
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.component.TextFieldDialog
import com.mochyy.mochymusic.ui.player.menu.LanguagePickerSheet
import com.mochyy.mochymusic.ui.player.menu.rememberTranslationTargets
import com.mochyy.mochymusic.utils.rememberPreference

// the route the lyrics section's AI translator row opens
const val AiTranslatorSettingsRoute = "ai_translator_settings"

// the values AiProviderKey takes; the lyrics translator reads them
private const val ProviderOpenRouter = "OpenRouter"
private const val ProviderCustom = "Custom"
private const val ProviderDeepL = "DeepL"

private enum class AiTranslatorDialog { Provider, Endpoint, Model, Key }

// settings > now playing > lyrics > ai translator: which service translates lyrics, and its endpoint, model and key
@Composable
fun AiTranslatorSettingsScreen(navController: NavController) {
    val (provider, setProvider) = rememberPreference(AiProviderKey, defaultValue = ProviderOpenRouter)
    val (legacyEndpoint) = rememberPreference(OpenRouterBaseUrlKey, defaultValue = "")
    val (customEndpoint, setCustomEndpoint) = rememberPreference(CustomAiBaseUrlKey, defaultValue = "")
    val (openRouterModel, setOpenRouterModel) = rememberPreference(OpenRouterModelKey, defaultValue = "")
    val (customModel, setCustomModel) = rememberPreference(CustomAiModelKey, defaultValue = AiTranslatorConfig.ModelUnset)
    val (openRouterKey, setOpenRouterKey) = rememberPreference(OpenRouterApiKey, defaultValue = "")
    val (customKey, setCustomKey) = rememberPreference(CustomAiApiKey, defaultValue = "")
    val (deeplKey, setDeeplKey) = rememberPreference(DeeplApiKey, defaultValue = "")
    val (targetLanguage, setTargetLanguage) = rememberPreference(TranslateLanguageKey, defaultValue = "en")
    val (translateAutomatically, setTranslateAutomatically) = rememberPreference(TranslateLyricsKey, defaultValue = false)

    val targets = rememberTranslationTargets()
    val isDeepL = provider == ProviderDeepL
    val isCustom = provider == ProviderCustom
    // every provider keeps its own key, model and address (AiTranslatorConfig decides what a request uses);
    // the address the two used to share only ever counts as Custom's
    val endpoint = customEndpoint.ifBlank { legacyEndpoint }
    val model = if (isCustom) AiTranslatorConfig.customModel(customModel) else openRouterModel
    val apiKey = when {
        isDeepL -> deeplKey
        isCustom -> customKey
        else -> openRouterKey
    }
    val targetName = targets.firstOrNull { it.first == targetLanguage }?.second ?: targetLanguage

    var dialog by remember { mutableStateOf<AiTranslatorDialog?>(null) }
    var pickingLanguage by remember { mutableStateOf(false) }

    SubSettingsScaffold(
        title = stringResource(R.string.settings_ai_translator),
        onBack = { navController.navigateUp() },
    ) {
        val items = buildList {
            add(
                SettingsItem(
                    title = { Text(stringResource(R.string.ai_translator_provider)) },
                    descriptionText = stringResource(providerLabelRes(provider)),
                    icon = painterResource(R.drawable.translate),
                    onClick = { dialog = AiTranslatorDialog.Provider },
                ),
            )
            // openrouter and deepl send to their own addresses; only a custom translator has one to set
            if (isCustom) {
                add(
                    SettingsItem(
                        title = { Text(stringResource(R.string.ai_translator_endpoint)) },
                        descriptionText = endpoint.ifBlank { stringResource(R.string.ai_translator_endpoint_hint) },
                        icon = painterResource(R.drawable.link),
                        onClick = { dialog = AiTranslatorDialog.Endpoint },
                    ),
                )
            }
            if (!isDeepL) {
                add(
                    SettingsItem(
                        title = { Text(stringResource(R.string.ai_translator_model)) },
                        descriptionText = model.ifBlank { stringResource(R.string.ai_translator_model_default) },
                        icon = painterResource(R.drawable.lyrics),
                        onClick = { dialog = AiTranslatorDialog.Model },
                    ),
                )
            }
            add(
                SettingsItem(
                    title = { Text(stringResource(R.string.ai_translator_key)) },
                    // the key is never shown, only whether one is saved
                    descriptionText = stringResource(if (apiKey.isBlank()) R.string.ai_translator_key_empty else R.string.ai_translator_key_saved),
                    icon = painterResource(R.drawable.key),
                    onClick = { dialog = AiTranslatorDialog.Key },
                ),
            )
            add(
                SettingsItem(
                    title = { Text(stringResource(R.string.ai_translator_target)) },
                    descriptionText = targetName,
                    icon = painterResource(R.drawable.translate),
                    onClick = { pickingLanguage = true },
                ),
            )
            add(
                switchItem(
                    key = "Translate lyrics automatically",
                    title = stringResource(R.string.settings_translate_auto),
                    description = stringResource(R.string.settings_translate_auto_desc),
                    icon = painterResource(R.drawable.translate),
                    checked = translateAutomatically,
                    onCheckedChange = setTranslateAutomatically,
                ),
            )
        }
        SettingsSection(null, items)
        Spacer(Modifier.height(140.dp))
    }

    when (dialog) {
        AiTranslatorDialog.Provider -> ProviderDialog(
            selected = provider,
            onSelect = {
                setProvider(it)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        AiTranslatorDialog.Endpoint -> TextFieldDialog(
            title = { Text(stringResource(R.string.ai_translator_endpoint)) },
            initialTextFieldValue = TextFieldValue(endpoint, TextRange(endpoint.length)),
            placeholder = { Text(stringResource(R.string.ai_translator_endpoint_hint)) },
            // https only, so the key never travels in clear; there's no default to fall back to
            isInputValid = { it.trim().startsWith("https://") },
            keyboardType = KeyboardType.Uri,
            onDone = {
                setCustomEndpoint(it.trim())
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        AiTranslatorDialog.Model -> TextFieldDialog(
            title = { Text(stringResource(R.string.ai_translator_model)) },
            initialTextFieldValue = TextFieldValue(model, TextRange(model.length)),
            placeholder = { Text(stringResource(R.string.ai_translator_model_default)) },
            // blank lets the provider pick its own model
            isInputValid = { true },
            onDone = {
                if (isCustom) setCustomModel(it.trim()) else setOpenRouterModel(it.trim())
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        AiTranslatorDialog.Key -> KeyDialog(
            current = apiKey,
            onDone = { value ->
                when {
                    isDeepL -> setDeeplKey(value.trim())
                    isCustom -> setCustomKey(value.trim())
                    else -> setOpenRouterKey(value.trim())
                }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }

    if (pickingLanguage) {
        LanguagePickerSheet(
            title = stringResource(R.string.ai_translator_target),
            selected = targetLanguage,
            onPick = { setTargetLanguage(it) },
            onDismiss = { pickingLanguage = false },
        )
    }
}

private fun providerLabelRes(provider: String): Int = when (provider) {
    ProviderCustom -> R.string.ai_translator_provider_custom
    ProviderDeepL -> R.string.ai_translator_provider_deepl
    else -> R.string.ai_translator_provider_openrouter
}

@Composable
private fun ProviderDialog(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    DefaultDialog(
        onDismiss = onDismiss,
        horizontalAlignment = Alignment.Start,
        title = { Text(stringResource(R.string.ai_translator_provider)) },
    ) {
        listOf(ProviderOpenRouter, ProviderCustom, ProviderDeepL).forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.RadioButton) { onSelect(option) }
                    .padding(vertical = 4.dp),
            ) {
                RadioButton(selected = option == selected, onClick = { onSelect(option) })
                Text(stringResource(providerLabelRes(option)), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun KeyDialog(current: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(current) }
    DefaultDialog(
        onDismiss = onDismiss,
        horizontalAlignment = Alignment.Start,
        title = { Text(stringResource(R.string.ai_translator_key)) },
        buttons = {
            TextButton(onClick = { onDone("") }) { Text(stringResource(R.string.ai_translator_key_clear)) }
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
            TextButton(onClick = { onDone(value) }) { Text(stringResource(android.R.string.ok)) }
        },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            label = { Text(stringResource(R.string.ai_translator_key)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
