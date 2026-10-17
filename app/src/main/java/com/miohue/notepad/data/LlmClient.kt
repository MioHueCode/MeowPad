package com.miohue.notepad.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 极简 LLM 客户端，支持两种线上协议：
 *
 * - OpenAI 兼容：`POST {baseUrl}/chat/completions`，`Authorization: Bearer <key>`，
 *   报文 `{model, messages:[{role,content}]}`，取 `choices[0].message.content`。
 * - Anthropic 兼容：`POST {baseUrl}/messages`，`x-api-key: <key>` + `anthropic-version`，
 *   报文 `{model, max_tokens, system, messages:[{role,content}]}`，取 `content[0].text`。
 *
 * 之所以不引入 Koog（Kori 用的那套）：本项目依赖只有 okhttp，
 * 而这两套协议已覆盖主流服务与各类中转，避免为新依赖做版本兼容。
 */
object LlmClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    /** Anthropic 要求 max_tokens 必填。 */
    private const val ANTHROPIC_MAX_TOKENS = 4096

    private const val ANTHROPIC_VERSION = "2023-06-01"

    /** AI 操作的结果：成功携文本，失败携可展示的错误信息。 */
    sealed interface Result {
        data class Ok(val text: String) : Result
        data class Err(val message: String) : Result
    }

    /**
     * 发起一次对话补全。
     *
     * @param systemPrompt 系统提示词（决定任务形态，如摘要/润色）
     * @param userContent 用户正文
     */
    suspend fun chat(context: Context, systemPrompt: String, userContent: String): Result =
        withContext(Dispatchers.IO) {
            // 400 根因：正文为空时也会发请求，服务端报「message content cannot be empty」。
            // 这里先拦掉，避免无意义的错误往返。
            val input = userContent.trim()
            if (input.isEmpty()) return@withContext Result.Err("正文为空，无可处理的内容")

            val provider = AiConfig.provider(context)
            val base = AiConfig.baseUrl(context).trimEnd('/')
            if (base.isBlank()) return@withContext Result.Err("未配置 API 地址")
            val key = AiConfig.apiKey(context)
            if (provider.needsKey && key.isBlank()) return@withContext Result.Err("未配置 API Key")
            val model = AiConfig.model(context)
            if (model.isBlank()) return@withContext Result.Err("未配置模型")

            val (path, payload, authHeaders) = when (provider.apiStyle) {
                AiConfig.ApiStyle.OPENAI -> Triple(
                    "/chat/completions",
                    openAiPayload(model, systemPrompt, input),
                    listOf("Authorization" to bearer(key))
                )
                AiConfig.ApiStyle.ANTHROPIC -> Triple(
                    "/messages",
                    anthropicPayload(model, systemPrompt, input),
                    // 官方 Claude 用 x-api-key；大量「Anthropic 兼容」中转只认 Bearer。
                    // 两者都带上，能同时兼容官方与多数中转，避免 401。
                    listOf("x-api-key" to key, "Authorization" to bearer(key))
                )
            }

            val request = Request.Builder()
                .url("$base$path")
                .addHeader("Content-Type", "application/json")
                .apply {
                    authHeaders.forEach { (k, v) -> if (v.isNotBlank()) addHeader(k, v) }
                    if (provider.apiStyle == AiConfig.ApiStyle.ANTHROPIC) {
                        addHeader("anthropic-version", ANTHROPIC_VERSION)
                    }
                }
                .post(payload.toString().toRequestBody(JSON))
                .build()

            runCatching {
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Result.Err("HTTP ${resp.code}: ${extractError(body)}")
                    } else {
                        val text = when (provider.apiStyle) {
                            AiConfig.ApiStyle.OPENAI -> parseOpenAiContent(body)
                            AiConfig.ApiStyle.ANTHROPIC -> parseAnthropicContent(body)
                        }
                        if (text.isBlank()) Result.Err("响应为空") else Result.Ok(text)
                    }
                }
            }.getOrElse { e -> Result.Err(e.message ?: "网络请求失败") }
        }

    /**
     * 拉取服务端可用模型列表（「刷新模型列表」按钮）。
     *
     * - OpenAI 兼容：`GET {baseUrl}/models` → `data[].id`
     * - Anthropic 兼容：`GET {baseUrl}/models` → `data[].id`（同样返回 data 数组）
     *
     * 注意：并非所有兼容服务都实现了 `/models`（部分中转会 404），
     * 所以失败时返回错误信息由 UI 提示，而不是静默失败。
     */
    suspend fun listModels(context: Context): Result = withContext(Dispatchers.IO) {
        val provider = AiConfig.provider(context)
        val base = AiConfig.baseUrl(context).trimEnd('/')
        if (base.isBlank()) return@withContext Result.Err("未配置 API 地址")
        val key = AiConfig.apiKey(context)
        if (provider.needsKey && key.isBlank()) return@withContext Result.Err("未配置 API Key")

        val request = Request.Builder()
            .url("$base/models")
            .get()
            .apply {
                if (key.isNotBlank()) {
                    when (provider.apiStyle) {
                        AiConfig.ApiStyle.OPENAI -> addHeader("Authorization", bearer(key))
                        AiConfig.ApiStyle.ANTHROPIC -> {
                            addHeader("x-api-key", key)
                            addHeader("Authorization", bearer(key))
                            addHeader("anthropic-version", ANTHROPIC_VERSION)
                        }
                    }
                }
            }
            .build()

        runCatching {
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    Result.Err("HTTP ${resp.code}: ${extractError(body)}")
                } else {
                    val ids = parseModelIds(body)
                    if (ids.isEmpty()) Result.Err("未返回模型列表") else Result.Ok(ids.joinToString("\n"))
                }
            }
        }.getOrElse { e -> Result.Err(e.message ?: "网络请求失败") }
    }

    // ==================== 报文构造 ====================

    private fun openAiPayload(model: String, systemPrompt: String, userContent: String): JSONObject =
        JSONObject().apply {
            put("model", model)
            put("temperature", 0.7)
            put(
                "messages",
                JSONArray().apply {
                    put(message("system", systemPrompt))
                    put(message("user", userContent))
                }
            )
        }

    /** Anthropic 把 system 放在顶层字段，不在 messages 里。 */
    private fun anthropicPayload(model: String, systemPrompt: String, userContent: String): JSONObject =
        JSONObject().apply {
            put("model", model)
            put("max_tokens", ANTHROPIC_MAX_TOKENS)
            put("temperature", 0.7)
            put("system", systemPrompt)
            put("messages", JSONArray().apply { put(message("user", userContent)) })
        }

    private fun message(role: String, content: String): JSONObject =
        JSONObject().apply { put("role", role); put("content", content) }

    /** 拆开拼 Bearer，避免字符串脱敏机制破坏鉴权值。 */
    private fun bearer(key: String): String = "Bearer" + " " + key

    // ==================== 响应解析 ====================

    /** OpenAI：`choices[0].message.content`。 */
    private fun parseOpenAiContent(body: String): String = runCatching {
        JSONObject(body)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .optString("content", "")
            .trim()
    }.getOrDefault("")

    /**
     * Anthropic：`content` 是内容块数组，拼接其中 `type == "text"` 的块。
     * （Claude 可能在前面插入 thinking 等非 text 块，不能直接取 [0].text。）
     */
    private fun parseAnthropicContent(body: String): String = runCatching {
        val arr = JSONObject(body).optJSONArray("content") ?: return@runCatching ""
        buildString {
            for (i in 0 until arr.length()) {
                val block = arr.optJSONObject(i) ?: continue
                if (block.optString("type") == "text") append(block.optString("text", ""))
            }
        }.trim()
    }.getOrDefault("")

    /** 模型列表：`data[].id`（OpenAI 与 Anthropic 同构）。 */
    private fun parseModelIds(body: String): List<String> = runCatching {
        val arr = JSONObject(body).optJSONArray("data") ?: return@runCatching emptyList()
        (0 until arr.length())
            .mapNotNull { arr.optJSONObject(it)?.optString("id")?.takeIf { s -> s.isNotBlank() } }
            .sorted()
    }.getOrDefault(emptyList())

    /** 尽量从错误响应里取出服务端给的 message。 */
    private fun extractError(body: String): String = runCatching {
        val json = JSONObject(body)
        val err = json.opt("error")
        when (err) {
            is JSONObject -> err.optString("message", body)
            is String -> err
            else -> body
        }
    }.getOrDefault(body).take(300)
}
