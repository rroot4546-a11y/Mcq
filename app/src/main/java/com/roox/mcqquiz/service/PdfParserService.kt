package com.roox.mcqquiz.service

import android.content.Context
import android.net.Uri
import com.roox.mcqquiz.data.model.Question
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.util.regex.Pattern

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
        val questions = mutableListOf<Question>()

        // Pattern to match question blocks like "16.1." or "Q1." or "1."
        val questionPattern = Pattern.compile(
            """(\d+\.?\d*)\.\s*(.*?)(?=\d+\.?\d*\.\s|$)""",
            Pattern.DOTALL
        )

        // Split text into question-answer pairs
        val blocks = text.split(Regex("""\n(?=\d+\.?\d*\.\s)"""))

        var questionNum = 0
        var i = 0
        while (i < blocks.size) {
            val block = blocks[i].trim()
            if (block.isEmpty()) { i++; continue }

            // Check if this block contains "Answer:"
            if (block.contains("Answer:") || block.contains("answer:")) {
                i++; continue
            }

            // Try to extract question components
            val parsed = parseQuestionBlock(block, quizId, questionNum)
            if (parsed != null) {
                // Look for answer in next block
                val answerBlock = if (i + 1 < blocks.size) blocks[i + 1].trim() else ""
                val (correctAnswer, explanation) = extractAnswer(answerBlock, block)

                questions.add(parsed.copy(
                    correctAnswer = correctAnswer,
                    explanation = explanation
                ))
                questionNum++
                if (answerBlock.contains("Answer:") || answerBlock.contains("answer:")) {
                    i += 2
                } else {
                    i++
                }
            } else {
                i++
            }
        }

        // Fallback: try a simpler regex-based approach if the above didn't work well
        if (questions.isEmpty()) {
            return parseWithSimpleRegex(text, quizId)
        }

        return questions
    }

    private fun parseQuestionBlock(block: String, quizId: Int, questionNum: Int): Question? {
        val lines = block.lines().filter { it.isNotBlank() }
        if (lines.size < 3) return null

        val numberMatch = Regex("""^(\d+\.?\d*)\.\s*""").find(lines[0])
        val qNumber = numberMatch?.groupValues?.get(1) ?: questionNum.toString()
        val questionText = lines[0].replace(Regex("""^\d+\.?\d*\.\s*"""), "").trim()

        val options = mutableMapOf<String, String>()
        for (line in lines.drop(1)) {
            val optMatch = Regex("""^\s*([A-E])[.)]\s*(.+)""").find(line.trim())
            if (optMatch != null) {
                options[optMatch.groupValues[1]] = optMatch.groupValues[2].trim()
            }
        }

        if (options.size < 2) return null

        return Question(
            quizId = quizId,
            questionNumber = qNumber,
            questionText = questionText,
            optionA = options["A"] ?: "",
            optionB = options["B"] ?: "",
            optionC = options["C"] ?: "",
            optionD = options["D"] ?: "",
            optionE = options["E"],
            correctAnswer = "",
            explanation = ""
        )
    }

    private fun extractAnswer(answerBlock: String, questionBlock: String): Pair<String, String> {
        val answerMatch = Regex("""Answer:\s*([A-E])""", RegexOption.IGNORE_CASE).find(answerBlock)
            ?: Regex("""Answer:\s*([A-E])""", RegexOption.IGNORE_CASE).find(questionBlock)

        val correctAnswer = answerMatch?.groupValues?.get(1) ?: ""

        // Extract explanation (everything after the answer letter)
        val explanation = if (answerMatch != null) {
            answerBlock.substring(answerMatch.range.last + 1).trim()
                .replace(Regex("""^\.\s*"""), "")
                .trim()
        } else ""

        return Pair(correctAnswer, explanation)
    }

    private fun parseWithSimpleRegex(text: String, quizId: Int): List<Question> {
        val questions = mutableListOf<Question>()

        // Match patterns like "16.1. Question text\nA. option\nB. option..."
        val pattern = Regex(
            """(\d+\.?\d*)\.\s+(.+?)\s+A[.)]\s+(.+?)\s+B[.)]\s+(.+?)\s+C[.)]\s+(.+?)\s+D[.)]\s+(.+?)(?:\s+E[.)]\s+(.+?))?\s+\d+\.?\d*\.\s+Answer:\s*([A-E])\s*[.)]?\s*(.*)""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )

        val matches = pattern.findAll(text)
        for (match in matches) {
            questions.add(Question(
                quizId = quizId,
                questionNumber = match.groupValues[1],
                questionText = match.groupValues[2].trim(),
                optionA = match.groupValues[3].trim(),
                optionB = match.groupValues[4].trim(),
                optionC = match.groupValues[5].trim(),
                optionD = match.groupValues[6].trim(),
                optionE = match.groupValues[7].takeIf { it.isNotBlank() }?.trim(),
                correctAnswer = match.groupValues[8].trim(),
                explanation = match.groupValues[9].trim()
            ))
        }

        return questions
    }
}
