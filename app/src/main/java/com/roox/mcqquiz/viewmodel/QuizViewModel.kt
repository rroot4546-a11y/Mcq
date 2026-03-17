package com.roox.mcqquiz.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.*
import com.roox.mcqquiz.MCQApplication
import com.roox.mcqquiz.data.model.Question
import com.roox.mcqquiz.data.model.Quiz
import com.roox.mcqquiz.service.AiService
import com.roox.mcqquiz.service.PdfParserService
import kotlinx.coroutines.launch

class QuizViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as MCQApplication).repository
    private val pdfParser = PdfParserService(application)

    val allQuizzes: LiveData<List<Quiz>> = repository.allQuizzes

    private val _currentQuestion = MutableLiveData<Question?>()
    val currentQuestion: LiveData<Question?> = _currentQuestion

    private val _questionIndex = MutableLiveData(0)
    val questionIndex: LiveData<Int> = _questionIndex

    private val _totalQuestions = MutableLiveData(0)
    val totalQuestions: LiveData<Int> = _totalQuestions

    private val _answerResult = MutableLiveData<AnswerResult?>()
    val answerResult: LiveData<AnswerResult?> = _answerResult

    private val _aiExplanation = MutableLiveData<String>()
    val aiExplanation: LiveData<String> = _aiExplanation

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private var currentQuestions: List<Question> = emptyList()
    private var currentQuizMode: QuizMode = QuizMode.STUDY

    private val _stats = MutableLiveData<QuizStats>()
    val stats: LiveData<QuizStats> = _stats

    enum class QuizMode { STUDY, EXAM, REVIEW }

    data class AnswerResult(
        val isCorrect: Boolean,
        val correctAnswer: String,
        val explanation: String,
        val selectedAnswer: String
    )

    data class QuizStats(
        val totalAnswered: Int,
        val totalCorrect: Int,
        val accuracy: Float
    )

    fun importPdf(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val quiz = Quiz(title = fileName.removeSuffix(".pdf"), sourceFileName = fileName)
                val quizId = repository.insertQuiz(quiz).toInt()
                val questions = pdfParser.parsePdf(uri, quizId)

                if (questions.isEmpty()) {
                    _errorMessage.value = "No questions found in PDF. Try a different format."
                    return@launch
                }

                repository.insertQuestions(questions)
                repository.updateQuiz(quiz.copy(id = quizId, totalQuestions = questions.size))
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Error importing PDF: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun startQuiz(quizId: Int, mode: QuizMode = QuizMode.STUDY) {
        viewModelScope.launch {
            currentQuizMode = mode
            currentQuestions = when (mode) {
                QuizMode.REVIEW -> repository.getIncorrectQuestions(quizId)
                else -> repository.getQuestionsForQuizSync(quizId)
            }
            _totalQuestions.value = currentQuestions.size
            _questionIndex.value = 0
            if (currentQuestions.isNotEmpty()) {
                _currentQuestion.value = currentQuestions[0]
            }
            _answerResult.value = null
            _aiExplanation.value = ""
        }
    }

    fun submitAnswer(selectedAnswer: String) {
        val question = _currentQuestion.value ?: return

        // Normalize: trim whitespace, take first letter, uppercase
        val selected = selectedAnswer.trim().uppercase().firstOrNull()?.toString() ?: ""
        val correct = question.correctAnswer.trim().uppercase().firstOrNull()?.toString() ?: ""

        Log.d("MCQQuiz", "Selected: '$selected' | Correct: '$correct' | Raw correct: '${question.correctAnswer}'")

        val isCorrect = selected == correct && correct.isNotEmpty()

        val result = AnswerResult(
            isCorrect = isCorrect,
            correctAnswer = question.correctAnswer.trim().uppercase(),
            explanation = question.explanation,
            selectedAnswer = selected
        )

        viewModelScope.launch {
            repository.updateQuestion(question.copy(
                userAnswer = selected,
                isAnsweredCorrectly = isCorrect
            ))
        }

        // Always show result in STUDY and REVIEW modes
        when (currentQuizMode) {
            QuizMode.STUDY -> _answerResult.value = result
            QuizMode.EXAM -> { /* Show results only at end */ }
            QuizMode.REVIEW -> _answerResult.value = result
        }
    }

    fun nextQuestion() {
        val idx = (_questionIndex.value ?: 0) + 1
        if (idx < currentQuestions.size) {
            _questionIndex.value = idx
            _currentQuestion.value = currentQuestions[idx]
            _answerResult.value = null
            _aiExplanation.value = ""
        }
    }

    fun previousQuestion() {
        val idx = (_questionIndex.value ?: 0) - 1
        if (idx >= 0) {
            _questionIndex.value = idx
            _currentQuestion.value = currentQuestions[idx]
            _answerResult.value = null
            _aiExplanation.value = ""
        }
    }

    fun requestAiExplanation(prefs: android.content.SharedPreferences) {
        val question = _currentQuestion.value ?: return
        viewModelScope.launch {
            try {
                _isLoading.postValue(true)
                val aiService = AiService.fromPrefs(prefs)
                val options = listOfNotNull(
                    "A) ${question.optionA}",
                    "B) ${question.optionB}",
                    "C) ${question.optionC}",
                    "D) ${question.optionD}",
                    question.optionE?.let { "E) $it" }
                )
                val explanation = aiService.getExplanation(
                    question.questionText, options, question.correctAnswer
                )
                _aiExplanation.postValue(explanation)
                repository.updateQuestion(question.copy(aiExplanation = explanation))
            } catch (e: Exception) {
                Log.e("MCQQuiz", "AI Error: ${e.message}", e)
                _aiExplanation.postValue("❌ Error: ${e.message}\n\nCheck your AI settings.")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun loadStats() {
        viewModelScope.launch {
            val totalAnswered = repository.getTotalAnswered()
            val totalCorrect = repository.getTotalCorrect()
            val accuracy = if (totalAnswered > 0) totalCorrect.toFloat() / totalAnswered * 100 else 0f
            _stats.value = QuizStats(totalAnswered, totalCorrect, accuracy)
        }
    }

    fun deleteQuiz(quiz: Quiz) {
        viewModelScope.launch {
            repository.deleteQuiz(quiz)
        }
    }
}
