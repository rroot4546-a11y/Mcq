package com.roox.mcqquiz.service

import android.content.Context
import android.net.Uri
import com.roox.mcqquiz.data.model.Question
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

class PdfParserService(private val context: Context) {

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

        return parseQuestionsFromText(fullText, quizId)
    }

    private fun parseQuestionsFromText(text: String, quizId: Int): List<Question> {
        // Strategy: split into question blocks, then parse each block
        // Handles multi-line questions and various formats

        val questions = mutableListOf<Question>()

        // Normalize line endings
        val normalized = text.replace("\r\n", "\n").replace("\r", "\n")

        // Split into blocks at question numbers (e.g. "16.1." or "1." or "Q1.")
        val questionRegex = Regex("""(?:^|\n)(\d+\.?\d*)\.\s""")
        val matches = questionRegex.findAll(normalized).toList()

        if (matches.isEmpty()) {
            // Try alternate format: "Q1." or "Question 1:"
            return parseAlternateFormat(normalized, quizId)
        }

        for (i in matches.indices) {
            val start = matches[i].range.first
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else normalized.length
            val block = normalized.substring(start, end).trim()
            val qNumber = matches[i].groupValues[1]

            val parsed = parseBlock(block, qNumber, quizId)
            if (parsed != null) {
                questions.add(parsed)
            }
        }

        // Try to find answers section (some PDFs have questions first, then answers)
        if (questions.isNotEmpty() && questions.all { it.correctAnswer.isBlank() }) {
            fillAnswersFromText(questions, normalized)
        }

        return questions
    }

    private fun parseBlock(block: String, qNumber: String, quizId: Int): Question? {
        val lines = block.lines()
        if (lines.isEmpty()) return null

        // Collect question text: everything from after the number until first option
        val optionRegex = Regex("""^\s*([A-E])[.)]\s+(.+)""")
        val answerRegex = Regex("""Answer:\s*([A-E])""", RegexOption.IGNORE_CASE)

        val questionLines = mutableListOf<String>()
        val options = mutableMapOf<String, String>()
        var currentOption: String? = null
        var correctAnswer = ""
        val explanationLines = mutableListOf<String>()
        var foundAnswer = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            // Check if this is an answer line
            val answerMatch = answerRegex.find(trimmed)
            if (answerMatch != null) {
                correctAnswer = answerMatch.groupValues[1]
                val afterAnswer = trimmed.substring(answerMatch.range.last + 1).trim()
                if (afterAnswer.isNotBlank()) {
                    explanationLines.add(afterAnswer)
                }
                foundAnswer = true
                continue
            }

            if (foundAnswer) {
                explanationLines.add(trimmed)
                continue
            }

            // Check if this is an option line
            val optMatch = optionRegex.find(trimmed)
            if (optMatch != null) {
                currentOption = optMatch.groupValues[1]
                options[currentOption] = optMatch.groupValues[2].trim()
                continue
            }

            // If we're in an option, this line continues it
            if (currentOption != null) {
                options[currentOption] = (options[currentOption] ?: "") + " " + trimmed
                continue
            }

            // Otherwise it's part of the question text
            // Remove the leading question number from first line
            val cleaned = if (questionLines.isEmpty()) {
                trimmed.replace(Regex("""^\d+\.?\d*\.\s*"""), "")
            } else {
                trimmed
            }
            if (cleaned.isNotBlank()) {
                questionLines.add(cleaned)
            }
        }

        // Need at least question text and 2 options
        val questionText = questionLines.joinToString(" ").trim()
        if (questionText.isBlank() || options.size < 2) return null

        return Question(
            quizId = quizId,
            questionNumber = qNumber,
            questionText = questionText,
            optionA = options["A"] ?: "",
            optionB = options["B"] ?: "",
            optionC = options["C"] ?: "",
            optionD = options["D"] ?: "",
            optionE = options["E"],
            correctAnswer = correctAnswer,
            explanation = explanationLines.joinToString(" ").trim()
        )
    }

    private fun fillAnswersFromText(questions: MutableList<Question>, text: String) {
        // Look for answer patterns: "16.1. Answer: B" or "1. B"
        val answerPattern = Regex("""(\d+\.?\d*)\.\s*(?:Answer:\s*)?([A-E])[.)]*\s*(.*)""", RegexOption.IGNORE_CASE)

        for (match in answerPattern.findAll(text)) {
            val qNum = match.groupValues[1]
            val answer = match.groupValues[2]
            val explanation = match.groupValues[3].trim()

            val idx = questions.indexOfFirst { it.questionNumber == qNum && it.correctAnswer.isBlank() }
            if (idx >= 0) {
                questions[idx] = questions[idx].copy(
                    correctAnswer = answer,
                    explanation = explanation
                )
            }
        }
    }

    private fun parseAlternateFormat(text: String, quizId: Int): List<Question> {
        val questions = mutableListOf<Question>()

        // Try "Question X:" or "Q X:" format
        val altRegex = Regex("""(?:Question|Q)\s*(\d+)[.:]\s*(.+?)(?=(?:Question|Q)\s*\d+[.:]|\z)""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

        for (match in altRegex.findAll(text)) {
            val qNum = match.groupValues[1]
            val block = match.groupValues[2].trim()
            val parsed = parseBlock("$qNum. $block", qNum, quizId)
            if (parsed != null) {
                questions.add(parsed)
            }
        }

        return questions
    }
}
