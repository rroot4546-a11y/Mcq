package com.roox.mcqquiz.service

import com.google.ai.client.generativeai.GenerativeModel

class AiService(private val apiKey: String) {

    private val model by lazy {
        GenerativeModel(
            modelName = "gemini-2.0-flash",
            apiKey = apiKey
        )
    }

    suspend fun getExplanation(
        questionText: String,
        options: List<String>,
        correctAnswer: String
    ): String {
        return try {
            val prompt = buildString {
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

            val response = model.generateContent(prompt)
            response.text ?: "No explanation generated."
        } catch (e: Exception) {
            "AI Error: ${e.message}"
        }
    }

    suspend fun testConnection(): Boolean {
        return try {
            val response = model.generateContent("Reply with OK")
            !response.text.isNullOrBlank()
        } catch (e: Exception) {
            false
        }
    }
}
