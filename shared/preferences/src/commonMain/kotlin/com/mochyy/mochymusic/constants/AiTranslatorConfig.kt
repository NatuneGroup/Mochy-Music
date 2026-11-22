package com.mochyy.mochymusic.constants

/** OpenRouter's own chat completions address; the OpenRouter choice always sends here. */
const val OpenRouterEndpoint = "https://openrouter.ai/api/v1/chat/completions"

/**
 * Where a lyrics translation goes and with which key, decided in one place from the chosen provider,
 * so the provider shown in settings and the server a key is sent to can't disagree. An unusable
 * choice (no key, or Custom with no address) has a blank [apiKey], and the caller then uses the
 * keyless translator instead.
 */
data class AiTranslatorConfig(val provider: String, val apiKey: String, val baseUrl: String, val model: String) {
    val usable: Boolean get() = apiKey.isNotBlank()

    // the key never appears in logs or crash reports
    override fun toString() = "AiTranslatorConfig(provider=$provider, baseUrl=$baseUrl, model=$model, hasKey=$usable)"

    companion object {
        const val OpenRouter = "OpenRouter"
        const val Custom = "Custom"
        const val DeepL = "DeepL"

        /** What a never-saved Custom model reads as, so a saved blank ("let the provider pick") stays blank. */
        const val ModelUnset = "\u0000unset"

        /**
         * Custom's model as settings show it and requests use it: the saved value, blank included; only if
         * none was ever saved, the model the old shared fields held, and that only when the old shared
         * address was set (a Custom setup), never OpenRouter's own model.
         */
        fun customModel(saved: String, legacyBaseUrl: String, legacyModel: String): String =
            if (saved != ModelUnset) saved else if (legacyBaseUrl.isNotBlank()) legacyModel else ""

        /**
         * [legacyBaseUrl] is the address field OpenRouter and Custom used to share. It is only ever
         * used as Custom's address (that's what it was for), never for OpenRouter, and no key comes
         * with it: a Custom translator set up before keys were split asks for its key again.
         */
        fun resolve(
            provider: String,
            openRouterKey: String,
            openRouterModel: String,
            customBaseUrl: String,
            customKey: String,
            customModel: String,
            legacyBaseUrl: String,
            legacyModel: String,
            deeplKey: String,
        ): AiTranslatorConfig = when (provider) {
            OpenRouter -> AiTranslatorConfig(provider, openRouterKey.trim(), OpenRouterEndpoint, openRouterModel)
            Custom -> {
                val url = customBaseUrl.ifBlank { legacyBaseUrl }.trim()
                // no address means nowhere safe to send the key
                AiTranslatorConfig(provider, if (url.isBlank()) "" else customKey.trim(), url, customModel(customModel, legacyBaseUrl, legacyModel))
            }
            DeepL -> AiTranslatorConfig(provider, deeplKey.trim(), "", "")
            // a provider no longer offered gets no key, rather than borrowing another provider's
            else -> AiTranslatorConfig(provider, "", "", "")
        }
    }
}
