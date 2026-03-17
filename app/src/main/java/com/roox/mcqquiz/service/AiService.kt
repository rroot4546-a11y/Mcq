package com.roox.mcqquiz.service

import com.google.ai.client.generativeai.GenerativeModel
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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

    private val geminiModel by lazy {
        GenerativeModel(modelName = "gemini-2.0-flash", apiKey = apiKey)
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
        return when (provider) {
            AiProvider.GEMINI -> callGemini(prompt)
            AiProvider.GOOGLE -> callGemini(prompt) // Google sign-in also uses Gemini SDK
            AiProvider.OLLAMA -> callOllama(prompt)
            AiProvider.CUSTOM -> callCustom(prompt)
        }
    }

    suspend fun testConnection(): Boolean {
        return try {
            val response = when (provider) {
                AiProvider.GEMINI -> callGemini("Reply with OK")
                AiProvider.GOOGLE -> callGemini("Reply with OK")
                AiProvider.OLLAMA -> callOllama("Reply with OK")
                AiProvider.CUSTOM -> callCustom("Reply with OK")
            }
            response.isNotBlank() && !response.startsWith("Error")
        } catch (e: Exception) {
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

    private suspend fun callGemini(prompt: String): String {
        return try {
            if (apiKey.isBlank()) {
                return "⚠️ No API key configured.\n\nGo to Settings → choose Gemini API Key → enter your key from aistudio.google.com/apikey\n\nOr choose another AI provider."
            }
            val response = geminiModel.generateContent(prompt)
            response.text ?: "No explanation generated."
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private suspend fun callOllama(prompt: String): String = suspendCoroutine { cont ->
        val baseUrl = ollamaUrl.trimEnd('/')
        val url = "$baseUrl/api/generate"

        val body = gson.toJson(mapOf(
            "model" to ollamaModel,
            "prompt" to prompt,
            "stream" to false
        ))

        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resume("Error: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        cont.resume("Error (${response.code}): $responseBody")
                        return
                    }
                    val json = gson.fromJson(responseBody, Map::class.java)
                    cont.resume(json["response"]?.toString() ?: "No response")
                } catch (e: Exception) {
                    cont.resume("Error: ${e.message}")
                }
            }
        })
    }

    /**
     * Calls any OpenAI-compatible API (OpenRouter, Together, Groq, etc.)
     * POST to customUrl with:
     * { "model": "...", "messages": [{"role":"user","content":"..."}] }
     */
    private suspend fun callCustom(prompt: String): String = suspendCoroutine { cont ->
        val url = customUrl.trimEnd('/')

        val messagesJson = gson.toJson(mapOf(
            "model" to customModel,
            "messages" to listOf(
                mapOf("role" to "user", "content" to prompt)
            ),
            "max_tokens" to 2000
        ))

        val requestBuilder = Request.Builder()
            .url(url)
            .post(messagesJson.toRequestBody("application/json".toMediaType()))

        if (customApiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $customApiKey")
        }
        requestBuilder.addHeader("Content-Type", "application/json")

        httpClient.newCall(requestBuilder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resume("Error: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        cont.resume("Error (${response.code}): $responseBody")
                        return
                    }
                    // Parse OpenAI-style response
                    val json = JsonParser.parseString(responseBody).asJsonObject
                    val choices = json.getAsJsonArray("choices")
                    if (choices != null && choices.size() > 0) {
                        val message = choices[0].asJsonObject.getAsJsonObject("message")
                        cont.resume(message.get("content").asString)
                    } else {
                        // Fallback: try to get "text" or "response" field
                        val text = json.get("text")?.asString
                            ?: json.get("response")?.asString
                            ?: responseBody
                        cont.resume(text)
                    }
                } catch (e: Exception) {
                    cont.resume("Error: ${e.message}")
                }
            }
        })
    }

    companion object {
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
}
