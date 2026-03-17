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

class AiService(
    private val apiKey: String = "",
    private val model: String = "google/gemini-2.0-flash-001"
) {
    companion object {
        private const val TAG = "AiService"
        private const val OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"

        fun fromPrefs(prefs: android.content.SharedPreferences): AiService {
            return AiService(
                apiKey = prefs.getString("openrouter_api_key", "") ?: "",
                model = prefs.getString("openrouter_model", "google/gemini-2.0-flash-001") ?: "google/gemini-2.0-flash-001"
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
            callOpenRouter(prompt)
        } catch (e: Exception) {
            Log.e(TAG, "getExplanation error", e)
            "❌ Error: ${e.message}"
        }
    }

    suspend fun testConnection(): Boolean {
        return try {
            val response = callOpenRouter("Reply with OK if you can read this.")
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
        append("You are a senior internal medicine consultant preparing a resident for the Iraqi Board exam.\n")
        append("Use the latest editions of Harrison's Principles of Internal Medicine and Davidson's Principles and Practice of Medicine as your primary references.\n\n")
        append("Question: $questionText\n\n")
        append("Options:\n")
        options.forEach { append("$it\n") }
        append("\nCorrect Answer: $correctAnswer\n\n")
        append("Provide a structured explanation:\n\n")
        append("📌 CORRECT ANSWER & WHY:\n")
        append("Explain why this is correct, citing Harrison's/Davidson's.\n\n")
        append("❌ WHY OTHER OPTIONS ARE WRONG:\n")
        append("Briefly explain each wrong option.\n\n")
        append("🔬 PATHOPHYSIOLOGY:\n")
        append("Key mechanism in 2-3 sentences.\n\n")
        append("🏥 CLINICAL PEARL:\n")
        append("One high-yield fact for board exams.\n\n")
        append("📚 REFERENCE:\n")
        append("Cite the relevant Harrison's/Davidson's chapter.\n\n")
        append("Keep it concise, clear, and board-focused.")
    }

    private suspend fun callOpenRouter(prompt: String): String = suspendCoroutine { cont ->
        if (apiKey.isBlank()) {
            cont.resume("⚠️ No API key configured.\n\nGo to ⚙️ Settings → enter your OpenRouter API key.\n\nGet one free at: openrouter.ai/keys")
            return@suspendCoroutine
        }

        val body = gson.toJson(mapOf(
            "model" to model,
            "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
            "max_tokens" to 2000
        ))

        val request = Request.Builder()
            .url(OPENROUTER_URL)
            .post(body.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://github.com/rroot4546-a11y/Mcq")
            .addHeader("X-Title", "MCQ Quiz App")
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "OpenRouter HTTP error", e)
                cont.resume("❌ Network error: ${e.message}\n\nCheck your internet connection.")
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        Log.e(TAG, "OpenRouter ${response.code}: $responseBody")
                        val msg = when (response.code) {
                            401 -> "❌ Invalid API key. Check your key in Settings."
                            402 -> "❌ Insufficient credits. Add credits at openrouter.ai"
                            429 -> "❌ Rate limited. Please wait a moment and try again."
                            else -> "❌ API error (${response.code})"
                        }
                        cont.resume(msg)
                        return
                    }
                    val json = JsonParser.parseString(responseBody).asJsonObject
                    val choices = json.getAsJsonArray("choices")
                    if (choices != null && choices.size() > 0) {
                        val message = choices.get(0).asJsonObject.getAsJsonObject("message")
                        cont.resume(message.get("content").asString)
                    } else {
                        cont.resume("No response from AI model.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error", e)
                    cont.resume("❌ Parse error: ${e.message}")
                }
            }
        })
    }
}
