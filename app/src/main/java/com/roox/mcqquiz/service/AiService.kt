package com.roox.mcqquiz.service

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Unified AI service supporting multiple providers:
 *  - google_oauth  : Gemini via Google OAuth ID token (free)
 *  - openrouter    : OpenRouter aggregator (original)
 *  - gemini_direct : Gemini via AI Studio API key
 *  - openai        : OpenAI direct
 *  - anthropic     : Anthropic direct
 */
class AiService(
    private val provider: String,
    private val apiKey: String = "",
    private val model: String = "",
    private val idToken: String = ""
) {
    companion object {
        private const val TAG = "AiService"

        private const val OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
        private const val GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"
        private const val ANTHROPIC_URL = "https://api.anthropic.com/v1/messages"
        private const val ANTHROPIC_VERSION = "2023-06-01"

        fun fromPrefs(prefs: android.content.SharedPreferences): AiService {
            val provider = prefs.getString("active_provider", "openrouter") ?: "openrouter"
            return when (provider) {
                "google_oauth" -> AiService(
                    provider = "google_oauth",
                    model = "gemini-2.0-flash",
                    idToken = prefs.getString("google_id_token", "") ?: ""
                )
                "openrouter" -> AiService(
                    provider = "openrouter",
                    apiKey = prefs.getString("openrouter_api_key", "") ?: "",
                    model = prefs.getString("openrouter_model", "google/gemini-2.0-flash-001") ?: "google/gemini-2.0-flash-001"
                )
                "gemini_direct" -> AiService(
                    provider = "gemini_direct",
                    apiKey = prefs.getString("gemini_direct_api_key", "") ?: "",
                    model = prefs.getString("gemini_direct_model", "gemini-2.0-flash") ?: "gemini-2.0-flash"
                )
                "openai" -> AiService(
                    provider = "openai",
                    apiKey = prefs.getString("openai_api_key", "") ?: "",
                    model = prefs.getString("openai_model", "gpt-4o") ?: "gpt-4o"
                )
                "anthropic" -> AiService(
                    provider = "anthropic",
                    apiKey = prefs.getString("anthropic_api_key", "") ?: "",
                    model = prefs.getString("anthropic_model", "claude-sonnet-4-5") ?: "claude-sonnet-4-5"
                )
                else -> AiService(
                    provider = "openrouter",
                    apiKey = prefs.getString("openrouter_api_key", "") ?: "",
                    model = prefs.getString("openrouter_model", "google/gemini-2.0-flash-001") ?: "google/gemini-2.0-flash-001"
                )
            }
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun getExplanation(
        questionText: String,
        options: List<String>,
        correctAnswer: String
    ): String {
        val prompt = buildPrompt(questionText, options, correctAnswer)
        return try {
            dispatch(prompt)
        } catch (e: Exception) {
            Log.e(TAG, "getExplanation error", e)
            "Error: ${e.message}"
        }
    }

    suspend fun testConnection(): Boolean {
        return try {
            val response = dispatch("Reply with OK if you can read this.")
            response.isNotBlank() && !response.startsWith("Error") && !response.startsWith("No ")
        } catch (e: Exception) {
            Log.e(TAG, "testConnection error", e)
            false
        }
    }

    private suspend fun dispatch(prompt: String): String = when (provider) {
        "google_oauth" -> callGemini(prompt, useKey = false)
        "gemini_direct" -> callGemini(prompt, useKey = true)
        "openrouter" -> callOpenRouter(prompt)
        "openai" -> callOpenAI(prompt)
        "anthropic" -> callAnthropic(prompt)
        else -> "Unknown provider: $provider"
    }

    // ─── Gemini (OAuth or API key) ─────────────────────────────────────────────

    private suspend fun callGemini(prompt: String, useKey: Boolean): String = suspendCoroutine { cont ->
        val token = if (useKey) apiKey else idToken
        if (token.isBlank()) {
            cont.resume(
                if (useKey) "No Gemini API key configured. Go to Settings."
                else "Not signed in with Google. Go to Settings and sign in."
            )
            return@suspendCoroutine
        }

        val effectiveModel = model.ifBlank { "gemini-2.0-flash" }
        val url = if (useKey) {
            "$GEMINI_BASE/$effectiveModel:generateContent?key=$token"
        } else {
            "$GEMINI_BASE/$effectiveModel:generateContent"
        }

        val bodyJson = gson.toJson(mapOf(
            "contents" to listOf(mapOf("parts" to listOf(mapOf("text" to prompt))))
        ))

        val requestBuilder = Request.Builder()
            .url(url)
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")

        if (!useKey) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        httpClient.newCall(requestBuilder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Gemini network error", e)
                cont.resume("Network error: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Gemini ${response.code}: $body")
                        cont.resume(geminiErrorMsg(response.code))
                        return
                    }
                    val json = JsonParser.parseString(body).asJsonObject
                    val candidates = json.getAsJsonArray("candidates")
                    if (candidates != null && candidates.size() > 0) {
                        val parts = candidates.get(0).asJsonObject
                            .getAsJsonObject("content")
                            .getAsJsonArray("parts")
                        cont.resume(parts.get(0).asJsonObject.get("text").asString)
                    } else {
                        cont.resume("No response from Gemini.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Gemini parse error", e)
                    cont.resume("Parse error: ${e.message}")
                }
            }
        })
    }

    private fun geminiErrorMsg(code: Int): String = when (code) {
        400 -> "Bad request. Check your prompt or model name."
        401 -> "Authentication failed. Re-sign-in or check your API key."
        403 -> "Access denied. Check your Google Cloud project permissions."
        429 -> "Rate limited. Please wait and try again."
        else -> "Gemini API error ($code)."
    }

    // ─── OpenRouter ────────────────────────────────────────────────────────────

    private suspend fun callOpenRouter(prompt: String): String = suspendCoroutine { cont ->
        if (apiKey.isBlank()) {
            cont.resume("No OpenRouter API key configured. Go to Settings.")
            return@suspendCoroutine
        }

        val bodyJson = gson.toJson(mapOf(
            "model" to model,
            "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
            "max_tokens" to 2000
        ))

        val request = Request.Builder()
            .url(OPENROUTER_URL)
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://github.com/rroot4546-a11y/Mcq")
            .addHeader("X-Title", "MCQ Quiz App")
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "OpenRouter network error", e)
                cont.resume("Network error: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        Log.e(TAG, "OpenRouter ${response.code}: $body")
                        val msg = when (response.code) {
                            401 -> "Invalid API key. Check your OpenRouter key."
                            402 -> "Insufficient credits. Add credits at openrouter.ai"
                            429 -> "Rate limited. Please wait and try again."
                            else -> "OpenRouter API error (${response.code})"
                        }
                        cont.resume(msg)
                        return
                    }
                    val json = JsonParser.parseString(body).asJsonObject
                    val choices = json.getAsJsonArray("choices")
                    if (choices != null && choices.size() > 0) {
                        val message = choices.get(0).asJsonObject.getAsJsonObject("message")
                        cont.resume(message.get("content").asString)
                    } else {
                        cont.resume("No response from OpenRouter.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "OpenRouter parse error", e)
                    cont.resume("Parse error: ${e.message}")
                }
            }
        })
    }

    // ─── OpenAI ────────────────────────────────────────────────────────────────

    private suspend fun callOpenAI(prompt: String): String = suspendCoroutine { cont ->
        if (apiKey.isBlank()) {
            cont.resume("No OpenAI API key configured. Go to Settings.")
            return@suspendCoroutine
        }

        val bodyJson = gson.toJson(mapOf(
            "model" to model,
            "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
            "max_tokens" to 2000
        ))

        val request = Request.Builder()
            .url(OPENAI_URL)
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "OpenAI network error", e)
                cont.resume("Network error: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        Log.e(TAG, "OpenAI ${response.code}: $body")
                        val msg = when (response.code) {
                            401 -> "Invalid OpenAI API key."
                            429 -> "Rate limited or quota exceeded."
                            else -> "OpenAI API error (${response.code})"
                        }
                        cont.resume(msg)
                        return
                    }
                    val json = JsonParser.parseString(body).asJsonObject
                    val choices = json.getAsJsonArray("choices")
                    if (choices != null && choices.size() > 0) {
                        val message = choices.get(0).asJsonObject.getAsJsonObject("message")
                        cont.resume(message.get("content").asString)
                    } else {
                        cont.resume("No response from OpenAI.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "OpenAI parse error", e)
                    cont.resume("Parse error: ${e.message}")
                }
            }
        })
    }

    // ─── Anthropic ─────────────────────────────────────────────────────────────

    private suspend fun callAnthropic(prompt: String): String = suspendCoroutine { cont ->
        if (apiKey.isBlank()) {
            cont.resume("No Anthropic API key configured. Go to Settings.")
            return@suspendCoroutine
        }

        val bodyJson = gson.toJson(mapOf(
            "model" to model,
            "max_tokens" to 2000,
            "messages" to listOf(mapOf("role" to "user", "content" to prompt))
        ))

        val request = Request.Builder()
            .url(ANTHROPIC_URL)
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", ANTHROPIC_VERSION)
            .addHeader("Content-Type", "application/json")
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Anthropic network error", e)
                cont.resume("Network error: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Anthropic ${response.code}: $body")
                        val msg = when (response.code) {
                            401 -> "Invalid Anthropic API key."
                            429 -> "Rate limited. Please wait and try again."
                            else -> "Anthropic API error (${response.code})"
                        }
                        cont.resume(msg)
                        return
                    }
                    val json = JsonParser.parseString(body).asJsonObject
                    val content = json.getAsJsonArray("content")
                    if (content != null && content.size() > 0) {
                        cont.resume(content.get(0).asJsonObject.get("text").asString)
                    } else {
                        cont.resume("No response from Anthropic.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Anthropic parse error", e)
                    cont.resume("Parse error: ${e.message}")
                }
            }
        })
    }

    // ─── Prompt builder ────────────────────────────────────────────────────────

    private fun buildPrompt(
        questionText: String,
        options: List<String>,
        correctAnswer: String
    ): String = buildString {
        append("You are a senior internal medicine consultant preparing a resident for the Iraqi Board exam.\n")
        append("Use the latest editions of Harrison's Principles of Internal Medicine and Davidson's Principles and Practice of Medicine as your primary references.\n\n")
        append("Question: $questionText\n\n")
        append("Options:\n")
        options.forEach { append("$it\n") }
        append("\nCorrect Answer: $correctAnswer\n\n")
        append("Provide a structured explanation:\n\n")
        append("CORRECT ANSWER & WHY:\n")
        append("Explain why this is correct, citing Harrison's/Davidson's.\n\n")
        append("WHY OTHER OPTIONS ARE WRONG:\n")
        append("Briefly explain each wrong option.\n\n")
        append("PATHOPHYSIOLOGY:\n")
        append("Key mechanism in 2-3 sentences.\n\n")
        append("CLINICAL PEARL:\n")
        append("One high-yield fact for board exams.\n\n")
        append("REFERENCE:\n")
        append("Cite the relevant Harrison's/Davidson's chapter.\n\n")
        append("Keep it concise, clear, and board-focused.")
    }
}
