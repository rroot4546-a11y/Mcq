package com.roox.mcqquiz.service

import com.google.ai.client.generativeai.GenerativeModel
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

enum class AiProvider { GEMINI, OLLAMA }

class AiService(
    private val provider: AiProvider,
    private val apiKey: String = "",
    private val ollamaUrl: String = "",
    private val ollamaModel: String = "llama3"
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
            AiProvider.OLLAMA -> callOllama(prompt)
        }
    }

    suspend fun testConnection(): Boolean {
        return try {
            val response = when (provider) {
                AiProvider.GEMINI -> callGemini("Reply with OK")
                AiProvider.OLLAMA -> callOllama("Reply with OK")
            }
            response.isNotBlank()
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
            val response = geminiModel.generateContent(prompt)
            response.text ?: "No explanation generated."
        } catch (e: Exception) {
            "Gemini Error: ${e.message}"
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
                cont.resumeWithException(Exception("Ollama Error: ${e.message}"))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        cont.resume("Ollama Error (${response.code}): $responseBody")
                        return
                    }
                    val json = gson.fromJson(responseBody, Map::class.java)
                    val text = json["response"]?.toString() ?: "No response from Ollama"
                    cont.resume(text)
                } catch (e: Exception) {
                    cont.resumeWithException(Exception("Parse Error: ${e.message}"))
                }
            }
        })
    }

    companion object {
        fun fromPrefs(prefs: android.content.SharedPreferences): AiService {
            val provider = when (prefs.getString("ai_provider", "gemini")) {
                "ollama" -> AiProvider.OLLAMA
                else -> AiProvider.GEMINI
            }
            return AiService(
                provider = provider,
                apiKey = prefs.getString("gemini_api_key", "") ?: "",
                ollamaUrl = prefs.getString("ollama_url", "") ?: "",
                ollamaModel = prefs.getString("ollama_model", "llama3") ?: "llama3"
            )
        }
    }
}
