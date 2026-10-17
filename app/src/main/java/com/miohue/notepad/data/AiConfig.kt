package com.miohue.notepad.data

import android.content.Context
import android.content.SharedPreferences

/**
 * AI 助手配置。
 *
 * 设计参考 Kori 的 `AiPane`（provider + API Key + baseUrl + model 四要素），
 * 但本项目只有 okhttp 依赖、不引入 Koog，所以自己实现两种线上协议：
 *
 * - [ApiStyle.OPENAI]：OpenAI 兼容的 `POST {baseUrl}/chat/completions`。
 *   OpenAI / DeepSeek / 硅基流动 / 月之暗面 / 通义 / 智谱 / Ollama / LM Studio 都属这一类。
 * - [ApiStyle.ANTHROPIC]：Anthropic 兼容的 `POST {baseUrl}/messages`（`x-api-key` + `anthropic-version`）。
 *   官方的 Claude，以及各类「Anthropic 兼容」中转服务都属这一类。
 *
 * 两种协议都提供「自定义」入口，用户可填任意自建/中转地址。
 *
 * 未配置密钥时，[com.miohue.notepad.ui.AiAssistantPanel] 只提供离线工具，并提示去设置里填写。
 */
object AiConfig {

    const val PREFS = "notepad_prefs"
    private const val KEY_ENABLED = "ai_enabled"
    private const val KEY_PROVIDER = "ai_provider"
    private const val KEY_BASE_URL = "ai_base_url"
    private const val KEY_API_KEY = "ai_api_key"
    private const val KEY_MODEL = "ai_model"

    /** 线上协议类型。决定请求路径、鉴权头与报文结构。 */
    enum class ApiStyle { OPENAI, ANTHROPIC }

    /**
     * 预设服务商。
     *
     * [baseUrl] 指向 API 版本根路径（客户端会拼 `/chat/completions` 或 `/messages`），
     * [models] 为内置候选（可离线使用）；联网时可用「刷新模型列表」从服务端拉取真实清单。
     */
    enum class Provider(
        val id: String,
        val display: String,
        val baseUrl: String,
        val defaultModel: String,
        val models: List<String>,
        val apiStyle: ApiStyle = ApiStyle.OPENAI,
        val needsKey: Boolean = true
    ) {
        OPENAI(
            "openai", "OpenAI",
            "https://api.openai.com/v1",
            "gpt-4o-mini",
            listOf("gpt-4o", "gpt-4o-mini", "gpt-4.1", "gpt-4.1-mini", "o4-mini")
        ),
        ANTHROPIC(
            "anthropic", "Anthropic Claude",
            "https://api.anthropic.com/v1",
            "claude-sonnet-4-20250514",
            listOf(
                "claude-sonnet-4-20250514",
                "claude-opus-4-20250514",
                "claude-3-7-sonnet-20250219",
                "claude-3-5-haiku-20241022"
            ),
            apiStyle = ApiStyle.ANTHROPIC
        ),
        DEEPSEEK(
            "deepseek", "DeepSeek",
            "https://api.deepseek.com/v1",
            "deepseek-chat",
            listOf("deepseek-chat", "deepseek-reasoner")
        ),
        SILICONFLOW(
            "siliconflow", "SiliconFlow",
            "https://api.siliconflow.cn/v1",
            "deepseek-ai/DeepSeek-V3",
            listOf(
                "deepseek-ai/DeepSeek-V3",
                "deepseek-ai/DeepSeek-R1",
                "Qwen/Qwen2.5-72B-Instruct",
                "Qwen/Qwen2.5-7B-Instruct"
            )
        ),
        MOONSHOT(
            "moonshot", "Moonshot",
            "https://api.moonshot.cn/v1",
            "moonshot-v1-8k",
            listOf("moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k")
        ),
        DASHSCOPE(
            "dashscope", "通义千问",
            "https://dashscope.aliyuncs.com/compatible-mode/v1",
            "qwen-plus",
            listOf("qwen-plus", "qwen-turbo", "qwen-max", "qwen-long")
        ),
        ZHIPU(
            "zhipu", "智谱 GLM",
            "https://open.bigmodel.cn/api/paas/v4",
            "glm-4-flash",
            listOf("glm-4-plus", "glm-4-air", "glm-4-flash")
        ),
        OLLAMA(
            "ollama", "Ollama (本地)",
            "http://localhost:11434/v1",
            "llama3.1",
            listOf("llama3.1", "qwen2.5", "gemma2", "mistral"),
            needsKey = false
        ),
        LMSTUDIO(
            "lmstudio", "LM Studio (本地)",
            "http://localhost:1234/v1",
            "local-model",
            listOf("local-model"),
            needsKey = false
        ),
        CUSTOM_OPENAI(
            "custom_openai", "自定义 OpenAI 兼容",
            "",
            "",
            emptyList()
        ),
        CUSTOM_ANTHROPIC(
            "custom_anthropic", "自定义 Anthropic 兼容",
            "",
            "",
            emptyList(),
            apiStyle = ApiStyle.ANTHROPIC
        );

        /** 是否为自定义（需要用户必须填 baseUrl，且不受预设默认值影响）。 */
        val isCustom: Boolean
            get() = this == CUSTOM_OPENAI || this == CUSTOM_ANTHROPIC

        companion object {
            /** 兼容旧版本写入的 "custom" id。 */
            fun fromId(id: String?): Provider = entries.firstOrNull { it.id == id }
                ?: if (id == "custom") CUSTOM_OPENAI else OPENAI
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()

    fun provider(context: Context): Provider =
        Provider.fromId(prefs(context).getString(KEY_PROVIDER, Provider.OPENAI.id))

    /** baseUrl：用户未填写时回退到预设值。 */
    fun baseUrl(context: Context): String {
        val saved = prefs(context).getString(KEY_BASE_URL, "").orEmpty().trim()
        return saved.ifBlank { provider(context).baseUrl }
    }

    fun apiKey(context: Context): String =
        prefs(context).getString(KEY_API_KEY, "").orEmpty().trim()

    /** model：用户未填写时回退到预设默认模型。 */
    fun model(context: Context): String {
        val saved = prefs(context).getString(KEY_MODEL, "").orEmpty().trim()
        return saved.ifBlank { provider(context).defaultModel }
    }

    /**
     * 是否已可用于真实请求。
     * 本地服务（Ollama / LM Studio）不需要密钥，只要 baseUrl + model 有值即可。
     */
    fun isConfigured(context: Context): Boolean {
        val p = provider(context)
        val hasUrl = baseUrl(context).isNotBlank()
        val hasModel = model(context).isNotBlank()
        val hasKey = !p.needsKey || apiKey(context).isNotBlank()
        return hasUrl && hasModel && hasKey
    }

    fun save(
        context: Context,
        providerId: String,
        baseUrl: String,
        apiKey: String,
        model: String
    ) {
        prefs(context).edit()
            .putString(KEY_PROVIDER, providerId)
            .putString(KEY_BASE_URL, baseUrl.trim())
            .putString(KEY_API_KEY, apiKey.trim())
            .putString(KEY_MODEL, model.trim())
            .apply()
    }

    /**
     * 缓存「刷新模型列表」拉到的模型，供下次进入设置页直接展示。
     * key 带 baseUrl 与协议，避免切换服务商后串台。
     */
    fun cachedModels(context: Context, providerId: String, baseUrl: String): List<String> {
        val raw = prefs(context).getString("ai_models_${providerId}_${baseUrl.hashCode()}", "").orEmpty()
        return if (raw.isBlank()) emptyList() else raw.split('\n').filter { it.isNotBlank() }
    }

    fun cacheModels(context: Context, providerId: String, baseUrl: String, models: List<String>) {
        prefs(context).edit()
            .putString("ai_models_${providerId}_${baseUrl.hashCode()}", models.joinToString("\n"))
            .apply()
    }
}
