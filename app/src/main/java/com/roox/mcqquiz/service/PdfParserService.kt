package com.roox.mcqquiz.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.roox.mcqquiz.data.model.Question
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

class PdfParserService(private val context: Context) {

    companion object {
        private const val TAG = "PdfParser"
    }

    init {
        PDFBoxResourceLoader.init(context)
    }

    fun parsePdf(uri: Uri, quizId: Int): List<Question> {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Cannot open PDF file")

        val document = PDDocument.load(inputStream)
        val stripper = PDFTextStripper()
        val fullText = stripper.getText(document)
        document.close()
        inputStream.close()

        Log.d(TAG, "PDF text length: ${fullText.length}")

        return parseText(fullText, quizId)
    }

    private fun parseText(text: String, quizId: Int): List<Question> {
        val normalized = text.replace("\r\n", "\n").replace("\r", "\n")

        // Strategy: Find all numbered entries like "16.1." or "1."
        // Then separate questions from answers by checking if "Answer:" follows the number
        val entryRegex = Regex("""(?:^|\n)\s*(\d+\.?\d*)\.\s""")
        val entries = entryRegex.findAll(normalized).toList()

        if (entries.isEmpty()) {
            Log.d(TAG, "No numbered entries found")
            return emptyList()
        }

        // Split into blocks
        data class Block(val number: String, val content: String)
        val blocks = mutableListOf<Block>()

        for (i in entries.indices) {
            val start = entries[i].range.first
            val end = if (i + 1 < entries.size) entries[i + 1].range.first else normalized.length
            val content = normalized.substring(start, end).trim()
            val number = entries[i].groupValues[1]
            blocks.add(Block(number, content))
        }

        // Separate question blocks from answer blocks
        // Answer blocks contain "Answer:" near the start
        val questionBlocks = mutableMapOf<String, String>()  // number -> content
        val answerBlocks = mutableMapOf<String, String>()     // number -> content

        for (block in blocks) {
            val isAnswer = block.content.contains(Regex("""Answer\s*:\s*[A-E]""", RegexOption.IGNORE_CASE))
            if (isAnswer) {
                answerBlocks[block.number] = block.content
                Log.d(TAG, "Answer block: ${block.number}")
            } else {
                // Only treat as question if it has options (A. B. C. etc)
                val hasOptions = block.content.contains(Regex("""\n\s*[A-E][.)]\s"""))
                if (hasOptions) {
                    questionBlocks[block.number] = block.content
                    Log.d(TAG, "Question block: ${block.number}")
                }
            }
        }

        // Build questions
        val questions = mutableListOf<Question>()
        for ((number, content) in questionBlocks) {
            val question = parseQuestionContent(content, number, quizId)
            if (question != null) {
                // Find matching answer
                val answerContent = answerBlocks[number] ?: ""
                val (correctAnswer, explanation) = parseAnswerContent(answerContent)

                questions.add(question.copy(
                    correctAnswer = correctAnswer,
                    explanation = explanation
                ))
                Log.d(TAG, "Q$number: answer=$correctAnswer")
            }
        }

        Log.d(TAG, "Total questions parsed: ${questions.size}")
        return questions.sortedBy {
            it.questionNumber.replace(".", "").toDoubleOrNull() ?: 0.0
        }
    }

    private fun parseQuestionContent(content: String, number: String, quizId: Int): Question? {
        val lines = content.lines()
        if (lines.isEmpty()) return null

        val optionRegex = Regex("""^\s*([A-E])[.)]\s*(.+)""")

        val questionLines = mutableListOf<String>()
        val options = mutableMapOf<String, MutableList<String>>()
        var currentOption: String? = null

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            // Check for option
            val optMatch = optionRegex.find(trimmed)
            if (optMatch != null) {
                currentOption = optMatch.groupValues[1]
                options.getOrPut(currentOption) { mutableListOf() }
                    .add(optMatch.groupValues[2].trim())
                continue
            }

            // If we're inside an option, continuation line
            if (currentOption != null) {
                options[currentOption]?.add(trimmed)
                continue
            }

            // Otherwise it's question text — remove leading number
            val cleaned = if (questionLines.isEmpty()) {
                trimmed.replace(Regex("""^\d+\.?\d*\.\s*"""), "")
            } else {
                trimmed
            }
            if (cleaned.isNotBlank()) {
                questionLines.add(cleaned)
            }
        }

        val questionText = questionLines.joinToString(" ").trim()
        if (questionText.isBlank() || options.size < 2) return null

        return Question(
            quizId = quizId,
            questionNumber = number,
            questionText = questionText,
            optionA = options["A"]?.joinToString(" ")?.trim() ?: "",
            optionB = options["B"]?.joinToString(" ")?.trim() ?: "",
            optionC = options["C"]?.joinToString(" ")?.trim() ?: "",
            optionD = options["D"]?.joinToString(" ")?.trim() ?: "",
            optionE = options["E"]?.joinToString(" ")?.trim(),
            correctAnswer = "",
            explanation = ""
        )
    }

    private fun parseAnswerContent(content: String): Pair<String, String> {
        if (content.isBlank()) return Pair("", "")

        // Extract answer letter: "16.1. Answer: B." or "Answer: B"
        val answerMatch = Regex("""Answer\s*:\s*([A-E])""", RegexOption.IGNORE_CASE).find(content)
        val correctAnswer = answerMatch?.groupValues?.get(1)?.uppercase() ?: ""

        // Extract explanation: everything after "Answer: X."
        val explanation = if (answerMatch != null) {
            content.substring(answerMatch.range.last + 1)
                .replace(Regex("""^\s*[.)\s]+"""), "")  // remove trailing dot/paren
                .lines()
                .filter { it.trim().isNotBlank() }
                .joinToString(" ") { it.trim() }
                .trim()
        } else ""

        return Pair(correctAnswer, explanation)
    }
}
