package com.fuyi.overlay

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * OpenAI 兼容的 /chat/completions 客户端。
 * 回调在后台线程，调用方需要自己切回主线程。
 */
object Translator {

    private val pool = Executors.newFixedThreadPool(2)

    fun translate(prefs: Prefs, text: String, callback: (String?, String?) -> Unit) {
        val base = prefs.apiBase.trim().trimEnd('/')
        val key = prefs.apiKey.trim()
        val model = prefs.model.trim()

        if (base.isEmpty() || key.isEmpty() || model.isEmpty()) {
            callback(null, "请先在设置里填写 API 地址、密钥和模型")
            return
        }

        pool.execute {
            var connection: HttpURLConnection? = null
            try {
                val payload = JSONObject().apply {
                    put("model", model)
                    put("temperature", 0.3)
                    put("stream", false)
                    put("max_tokens", 400)
                    put("messages", JSONArray().apply {
                        put(JSONObject().put("role", "system").put("content", systemPrompt(prefs.glossary)))
                        put(JSONObject().put("role", "user").put("content", text))
                    })
                }

                val endpoint = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
                val conn = URL(endpoint).openConnection() as HttpURLConnection
                connection = conn
                conn.requestMethod = "POST"
                conn.connectTimeout = 15000
                conn.readTimeout = 60000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("Authorization", "Bearer $key")

                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() } ?: ""

                if (code !in 200..299) {
                    callback(null, "HTTP $code：${body.take(180)}")
                    return@execute
                }

                val content = JSONObject(body)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .optString("content", "")
                    .trim()

                if (content.isEmpty()) {
                    callback(null, "接口返回了空内容")
                } else {
                    callback(content, null)
                }
            } catch (e: Exception) {
                callback(null, "网络错误：${e.message ?: e.javaClass.simpleName}")
            } finally {
                try {
                    connection?.disconnect()
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun systemPrompt(glossary: String): String {
        val g = glossary.trim().ifEmpty { "（无）" }
        return """
            你是资深日文游戏本地化译者，把用户给的日文游戏文本翻译成简体中文。
            规则：
            1. 只输出译文，不解释、不加引号、不注音。
            2. 人名、地名、技能名前后统一，严格遵守术语表。
            3. 保留角色语气（敬语/粗鲁/傲娇/古风），口语自然。
            4. 菜单、选项、技能名尽量短。
            5. 拟声词用中文习惯表达。
            6. 原文含英文缩写、错误码时保留原文。
            7. 如果原文几乎没有日文（只有数字、英文、符号），原样返回，不要逐行翻译。
            8. 不要复述原文，不要输出编号或清单，除非原文本身就是清单。
            术语表（原文=译文）：
            $g
        """.trimIndent()
    }
}
