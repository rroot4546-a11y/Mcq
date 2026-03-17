package com.roox.mcqquiz.service

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

enum class AiProvider { GEMINI, GOOGLE, OLLAMA, CUSTOM }

class AiService(
    private val provider: AiProvider,
    private val apiKey: String = "",
    private val ollamaUrl: String = "",
    private val ollamaModel: String = "llama3",
    private val customUrl: String = "",
    private val customModel: String = "",
    private val customApiKey: String = ""
) {
    companion object {
        private const val TAG = "AiService"
        private const val GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

        fun fromPrefs(prefs: android.content.SharedPreferences): AiService {
            val provider = when (prefs.getString("ai_provider", "gemini")) {
                "google" -> AiProvider.GOOGLE
                "ollama" -> AiProvider.OLLAMA
                "custom" -> AiProvider.CUSTOM
                else -> AiProvider.GEMINI
            }
            return AiService(
                provider = provider,
                apiKey = prefs.getString("gemini_api_key", "") ?: "",
                ollamaUrl = prefs.getString("ollama_url", "") ?: "",
                ollamaModel = prefs.getString("ollama_model", "llama3") ?: "llama3",
                customUrl = prefs.getString("custom_api_url", "") ?: "",
                customModel = prefs.getString("custom_model", "") ?: "",
                customApiKey = prefs.getString("custom_api_key", "") ?: ""
            )
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun getExplanation(
        questionText: String,
        options: List<String>,
        correctAnswer: String
    ): String {
        val prompt = buildPrompt(questionText, options, correctAnswer)
        return try {
            when (provider) {
                AiProvider.GEMINI, AiProvider.GOOGLE -> callGeminiHttp(prompt)
                AiProvider.OLLAMA -> callOllama(prompt)
                AiProvider.CUSTOM -> callCustom(prompt)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getExplanation error", e)
            "❌ Error: ${e.message}"
        }
    }

    suspend fun testConnection(): Boolean {
        return try {
            val response = when (provider) {
                AiProvider.GEMINI, AiProvider.GOOGLE -> callGeminiHttp("Reply with OK")
                AiProvider.OLLAMA -> callOllama("Reply with OK")
                AiProvider.CUSTOM -> callCustom("Reply with OK")
            }
            response.isNotBlank() && !response.startsWith("❌")
        } catch (e: Exception) {
            Log.e(TAG, "testConnection error", e)
            false
        }
    }

    private fun buildPrompt(
        questionText: String,
        options: List<String>,
        correctAnswer: String
    ): String = buildString {
        append("You are a medical education expert. Explain this MCQ:\n\n")
        append("Question: $questionText\n\n")
        append("Options:\n")
        options.forEach { append("$it\n") }
        append("\nCorrect Answer: $correctAnswer\n\n")
        append("Provide a detailed explanation covering:\n")
        append("1. Why the correct answer is right\n")
        append("2. Why each wrong answer is incorrect\n")
        append("3. Key clinical pearls for board exams\n")
        append("4. Relevant pathophysiology\n")
        append("\nKeep it concise but thorough. Use bullet points.")
    }

    /**
     * Direct HTTP call to Gemini API — no SDK, no crashes
     */
    private suspend fun callGeminiHttp(prompt: String): String = suspendCoroutine { cont ->
        if (apiKey.isBlank()) {
            cont.resume("⚠️ No API key.\n\nGo to Settings → Gemini API Key → enter key from aistudio.google.com/apikey")
            return@suspendCoroutine
        }

        val url = "$GEMINI_URL?key=$apiKey"
        val body = """{"contents":[{"parts":[{"text":${gson.toJson(prompt)}}]}]}"""

        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Gemini HTTP error", e)
                cont.resume("❌ Network error: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Gemini HTTP ${response.code}: $responseBody")
                        cont.resume("❌ Gemini error (${response.code}): Check your API key in Settings")
                        return
                    }
                    val json = JsonParser.parseString(responseBody).asJsonObject
                    val candidates = json.getAsJsonArray("candidates")
                    if (candidates != null && candidates.size() > 0) {
                        val parts = candidates.get(0).asJsonObject
                            .getAsJsonObject("content")
                            .getAsJsonArray("parts")
                        val content = parts.get(0).asJsonObject.get("text").asString
                        cont.resume(content)
                    } else {
                        cont.resume("No response from Gemini")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Gemini parse error", e)
                    cont.resume("❌ Parse error: ${e.message}")
                }
            }
        })
    }

    private suspend fun callOllama(prompt: String): String = suspendCoroutine { cont ->
        if (ollamaUrl.isBlank()) {
            cont.resume("⚠️ No Ollama URL configured. Go to Settings.")
            return@suspendCoroutine
        }

        val baseUrl = ollamaUrl.trimEnd('/')
        val url = "$baseUrl/api/generate"
        val body = gson.toJson(mapOf("model" to ollamaModel, "prompt" to prompt, "stream" to false))

        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resume("❌ Ollama error: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        cont.resume("❌ Ollama error (${response.code}): $responseBody")
                        return
                    }
                    val json = gson.fromJson(responseBody, Map::class.java)
                    cont.resume(json["response"]?.toString() ?: "No response")
                } catch (e: Exception) {
                    cont.resume("❌ Parse error: ${e.message}")
                }
            }
        })
    }

    private suspend fun callCustom(prompt: String): String = suspendCoroutine { cont ->
        if (customUrl.isBlank()) {
            cont.resume("⚠️ No Custom API URL configured. Go to Settings.")
            return@suspendCoroutine
        }

        val url = customUrl.trimEnd('/')
        val messagesJson = gson.toJson(mapOf(
            "model" to customModel,
            "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
            "max_tokens" to 2000
        ))

        val requestBuilder = Request.Builder()
            .url(url)
            .post(messagesJson.toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")

        if (customApiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $customApiKey")
        }

        httpClient.newCall(requestBuilder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resume("❌ Custom API error: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        cont.resume("❌ API error (${response.code}): $responseBody")
                        return
                    }
                    val json = JsonParser.parseString(responseBody).asJsonObject
                    val choices = json.getAsJsonArray("choices")
                    if (choices != null && choices.size() > 0) {
                        val message = choices[0].asJsonObject.getAsJsonObject("message")
                        cont.resume(message.get("content").asString)
                    } else {
                        val text = json.get("text")?.asString
                            ?: json.get("response")?.asString
                            ?: responseBody
                        cont.resume(text)
                    }
                } catch (e: Exception) {
                    cont.resume("❌ Parse error: ${e.message}")
                }
            }
        })
    }
}
