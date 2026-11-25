package com.juwon.toastdesk

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object Api {

    data class Settings(
        val provider: String,
        val baseUrl: String,
        val model: String,
        val apiKey: String
    )

    data class Reply(val label: String, val text: String, val why: String)
    data class Result(val replies: List<Reply>, val tip: String?)

    class ApiError(val code: Int, val body: String) :
        Exception("HTTP $code: ${body.take(600)}")

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun isGemini(provider: String) = provider.startsWith("Gemini", ignoreCase = true)

    fun call(s: Settings, system: String, user: String): Result {
        val raw: String
        if (isGemini(s.provider)) {
            val base = if (s.baseUrl.isBlank()) "https://generativelanguage.googleapis.com/v1beta"
                       else s.baseUrl.trimEnd('/')
            val url = "$base/models/${s.model}:generateContent"
            val body = JSONObject()
                .put("systemInstruction", JSONObject().put(
                    "parts", JSONArray().put(JSONObject().put("text", system))))
                .put("contents", JSONArray().put(
                    JSONObject().put("role", "user").put(
                        "parts", JSONArray().put(JSONObject().put("text", user)))))
                .put("generationConfig", JSONObject()
                    .put("temperature", 0.9)
                    .put("responseMimeType", "application/json"))
            val resp = post(Request.Builder().url(url)
                .header("x-goog-api-key", s.apiKey)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build())
            raw = try {
                val j = JSONObject(resp)
                val cands = j.optJSONArray("candidates")
                    ?: throw ApiError(200, "Unexpected response: ${resp.take(300)}")
                val parts = cands.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                    ?: throw ApiError(200, "Unexpected response: ${resp.take(300)}")
                val sb = StringBuilder()
                for (i in 0 until parts.length()) {
                    sb.append(parts.optJSONObject(i)?.optString("text", ""))
                }
                sb.toString()
            } catch (e: ApiError) { throw e } catch (e: Exception) {
                throw ApiError(200, "Unexpected response: ${resp.take(300)}")
            }
        } else {
            val base = if (s.baseUrl.isBlank()) "https://api.x.ai/v1" else s.baseUrl.trimEnd('/')
            val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
            val body = JSONObject()
                .put("model", s.model)
                .put("temperature", 0.9)
                .put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user)))
            val resp = post(Request.Builder().url(url)
                .header("Authorization", "Bearer ${s.apiKey}")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build())
            raw = try {
                val j = JSONObject(resp)
                val choices = j.optJSONArray("choices")
                    ?: throw ApiError(200, "Unexpected response: ${resp.take(300)}")
                choices.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                    ?: throw ApiError(200, "Unexpected response: ${resp.take(300)}")
            } catch (e: ApiError) { throw e } catch (e: Exception) {
                throw ApiError(200, "Unexpected response: ${resp.take(300)}")
            }
        }
        return parse(raw)
    }

    private fun post(req: Request): String {
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                var msg = text.ifBlank { resp.message }
                try {
                    val j = JSONObject(text)
                    val err = j.optJSONObject("error")
                    val m = err?.optString("message")
                    if (!m.isNullOrBlank()) msg = m
                } catch (e: Exception) { }
                throw ApiError(resp.code, msg)
            }
            return text
        }
    }

    fun parse(textIn: String): Result {
        var t = textIn.trim()
        if (t.contains("```")) {
            t = t.replace("```json", "```")
            val parts = t.split("```")
            if (parts.size >= 2) t = parts[1].trim()
        }
        val start = t.indexOf('{')
        val end = t.lastIndexOf('}')
        val json = if (start >= 0 && end > start) t.substring(start, end + 1) else t
        val replies = ArrayList<Reply>()
        try {
            val j = JSONObject(json)
            val arr = j.optJSONArray("replies")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val txt = o.optString("text", "").trim()
                    if (txt.isEmpty()) continue
                    replies.add(Reply(
                        o.optString("label", "Reply ${i + 1}"),
                        txt,
                        o.optString("why", "")))
                }
            }
            val tip = j.optString("tip", "").trim().ifBlank { null }
            if (replies.isNotEmpty()) return Result(replies, tip)
        } catch (e: Exception) { }
        // fallback: show the model's raw answer as one reply so nothing is lost
        if (textIn.isNotBlank()) {
            return Result(listOf(Reply("Reply", textIn.trim(), "")), null)
        }
        throw ApiError(200, "The model returned an empty answer. Check the model name in Settings.")
    }

    fun freeOpenRouterModels(): List<String> {
        val req = Request.Builder().url("https://openrouter.ai/api/v1/models").build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw ApiError(resp.code, text.ifBlank { resp.message })
            val j = JSONObject(text)
            val arr = j.optJSONArray("data") ?: return emptyList()
            val out = ArrayList<String>()
            for (k in 0 until arr.length()) {
                val o = arr.optJSONObject(k) ?: continue
                val id = o.optString("id", "")
                if (id.endsWith(":free")) out.add(id)
            }
            return out.sorted()
        }
    }
}
