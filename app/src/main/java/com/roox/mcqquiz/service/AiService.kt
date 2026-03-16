package com.roox.mcqquiz.service

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AiService(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // Gemini API endpoint
    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

    data class GeminiRequest(
        val contents: List<Content>
    )

    data class Content(
        val parts: List<Part>
    )

    data class Part(
        val text: String
    )

    data class GeminiResponse(
        val candidates: List<Candidate>?
    )

    data class Candidate(
        val content: ContentResponse?
    )

    data class ContentResponse(
        val parts: List<PartResponse>?
    )

    data class PartResponse(
        val text: String?
    )

    suspend fun getExplanation(
        questionText: String,
        options: List<String>,
        correctAnswer: String
    ): String = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                Explain the following multiple-choice medical question in detail.
                Provide a clear, structured explanation suitable for medical board exam students.
                
                Include:
                1. Why the correct answer is right
                2. Why each incorrect option is wrong
                3. Key clinical pearls
                4. Relevant pathophysiology
                
                Question: $questionText
                Options: ${options.joinToString(", ")}
                Correct Answer: $correctAnswer
                
                Provide the explanation in English, professionally formatted.
            """.trimIndent()

            val request = GeminiRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt))))
            )

            val jsonBody = gson.toJson(request)
            val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url("$baseUrl?key=$apiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string() ?: return@withContext "No response from AI"

            if (!response.isSuccessful) {
                return@withContext "AI service error: ${response.code}"
            }

            val geminiResponse = gson.fromJson(responseBody, GeminiResponse::class.java)
            geminiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "No explanation available"

        } catch (e: Exception) {
            "AI explanation unavailable: ${e.message}"
        }
    }

    suspend fun generatePracticeQuestions(topic: String): String = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                Generate 3 multiple-choice medical questions about: $topic
                
                Format each question as:
                Q: [question text]
                A) [option]
                B) [option]
                C) [option]
                D) [option]
                Answer: [correct letter]
                Explanation: [brief explanation]
                
                Make questions suitable for medical board exam preparation.
            """.trimIndent()

            val request = GeminiRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt))))
            )

            val jsonBody = gson.toJson(request)
            val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url("$baseUrl?key=$apiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string() ?: return@withContext "No response"

            val geminiResponse = gson.fromJson(responseBody, GeminiResponse::class.java)
            geminiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Could not generate questions"

        } catch (e: Exception) {
            "Error generating questions: ${e.message}"
        }
    }
}
