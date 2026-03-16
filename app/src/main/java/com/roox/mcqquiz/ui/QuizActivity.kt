package com.roox.mcqquiz.ui

import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import com.roox.mcqquiz.R
import com.roox.mcqquiz.viewmodel.QuizViewModel

class QuizActivity : AppCompatActivity() {

    private lateinit var viewModel: QuizViewModel
    private lateinit var tvQuestionNumber: TextView
    private lateinit var tvQuestionText: TextView
    private lateinit var btnOptionA: MaterialButton
    private lateinit var btnOptionB: MaterialButton
    private lateinit var btnOptionC: MaterialButton
    private lateinit var btnOptionD: MaterialButton
    private lateinit var btnOptionE: MaterialButton
    private lateinit var tvResult: TextView
    private lateinit var tvExplanation: TextView
    private lateinit var tvAiExplanation: TextView
    private lateinit var btnAiExplain: MaterialButton
    private lateinit var btnNext: MaterialButton
    private lateinit var btnPrevious: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var progressQuiz: ProgressBar
    private lateinit var tvProgress: TextView

    private var selectedAnswer: String? = null
    private var hasAnswered = false
    private lateinit var prefs: SharedPreferences

    // Store default button style to reset later
    private var defaultStrokeColor: ColorStateList? = null
    private var defaultTextColor: ColorStateList? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz)

        viewModel = ViewModelProvider(this)[QuizViewModel::class.java]
        prefs = getSharedPreferences("mcq_prefs", MODE_PRIVATE)

        initViews()

        val quizId = intent.getIntExtra("QUIZ_ID", -1)
        val mode = when (intent.getStringExtra("QUIZ_MODE")) {
            "EXAM" -> QuizViewModel.QuizMode.EXAM
            "REVIEW" -> QuizViewModel.QuizMode.REVIEW
            else -> QuizViewModel.QuizMode.STUDY
        }

        if (quizId == -1) { finish(); return }

        viewModel.startQuiz(quizId, mode)
        observeViewModel()
    }

    private fun initViews() {
        tvQuestionNumber = findViewById(R.id.tvQuestionNumber)
        tvQuestionText = findViewById(R.id.tvQuestionText)
        btnOptionA = findViewById(R.id.btnOptionA)
        btnOptionB = findViewById(R.id.btnOptionB)
        btnOptionC = findViewById(R.id.btnOptionC)
        btnOptionD = findViewById(R.id.btnOptionD)
        btnOptionE = findViewById(R.id.btnOptionE)
        tvResult = findViewById(R.id.tvResult)
        tvExplanation = findViewById(R.id.tvExplanation)
        tvAiExplanation = findViewById(R.id.tvAiExplanation)
        btnAiExplain = findViewById(R.id.btnAiExplain)
        btnNext = findViewById(R.id.btnNext)
        btnPrevious = findViewById(R.id.btnPrevious)
        progressBar = findViewById(R.id.progressLoading)
        progressQuiz = findViewById(R.id.progressQuiz)
        tvProgress = findViewById(R.id.tvProgress)

        // Save default button colors for reset
        defaultStrokeColor = btnOptionA.strokeColor
        defaultTextColor = btnOptionA.textColors

        val optionClickListener = View.OnClickListener { v ->
            if (hasAnswered) return@OnClickListener

            selectedAnswer = when (v.id) {
                R.id.btnOptionA -> "A"
                R.id.btnOptionB -> "B"
                R.id.btnOptionC -> "C"
                R.id.btnOptionD -> "D"
                R.id.btnOptionE -> "E"
                else -> null
            }
            selectedAnswer?.let {
                hasAnswered = true
                highlightSelected(v as MaterialButton)
                viewModel.submitAnswer(it)
            }
        }

        btnOptionA.setOnClickListener(optionClickListener)
        btnOptionB.setOnClickListener(optionClickListener)
        btnOptionC.setOnClickListener(optionClickListener)
        btnOptionD.setOnClickListener(optionClickListener)
        btnOptionE.setOnClickListener(optionClickListener)

        btnNext.setOnClickListener { viewModel.nextQuestion() }
        btnPrevious.setOnClickListener { viewModel.previousQuestion() }

        btnAiExplain.setOnClickListener {
            val provider = prefs.getString("ai_provider", "gemini")
            val hasConfig = when (provider) {
                "ollama" -> prefs.getString("ollama_url", "")?.isNotBlank() == true
                "custom" -> prefs.getString("custom_api_url", "")?.isNotBlank() == true
                else -> prefs.getString("gemini_api_key", "")?.isNotBlank() == true
            }
            if (!hasConfig) {
                Toast.makeText(this, "Please configure AI in Settings first", Toast.LENGTH_LONG).show()
            } else {
                viewModel.requestAiExplanation(prefs)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.currentQuestion.observe(this) { question ->
            question?.let {
                tvQuestionNumber.text = "Question ${it.questionNumber}"
                tvQuestionText.text = it.questionText
                btnOptionA.text = "A)  ${it.optionA}"
                btnOptionB.text = "B)  ${it.optionB}"
                btnOptionC.text = "C)  ${it.optionC}"
                btnOptionD.text = "D)  ${it.optionD}"

                if (it.optionE != null) {
                    btnOptionE.visibility = View.VISIBLE
                    btnOptionE.text = "E)  ${it.optionE}"
                } else {
                    btnOptionE.visibility = View.GONE
                }

                // RESET everything for new question
                resetAllButtons()
                hasAnswered = false
                selectedAnswer = null
                tvResult.visibility = View.GONE
                tvExplanation.visibility = View.GONE
                tvAiExplanation.visibility = View.GONE
                btnAiExplain.visibility = View.GONE
            }
        }

        viewModel.questionIndex.observe(this) { idx ->
            val total = viewModel.totalQuestions.value ?: 0
            if (total > 0) {
                progressQuiz.max = total
                progressQuiz.progress = idx + 1
                tvProgress.text = "${idx + 1} / $total"
            }
            btnPrevious.isEnabled = idx > 0
        }

        viewModel.answerResult.observe(this) { result ->
            result?.let {
                tvResult.visibility = View.VISIBLE

                if (it.isCorrect) {
                    tvResult.text = "✅ Correct!"
                    tvResult.setTextColor(ContextCompat.getColor(this, R.color.correct_green))
                } else {
                    tvResult.text = "❌ Wrong — Correct answer: ${it.correctAnswer}"
                    tvResult.setTextColor(ContextCompat.getColor(this, R.color.wrong_red))
                    // Highlight the wrong selected answer in red
                    highlightWrongAnswer(it.selectedAnswer)
                }

                // Always highlight correct answer in green
                highlightCorrectAnswer(it.correctAnswer)

                if (it.explanation.isNotBlank()) {
                    tvExplanation.visibility = View.VISIBLE
                    tvExplanation.text = it.explanation
                }

                btnAiExplain.visibility = View.VISIBLE
                disableOptions()
            }
        }

        viewModel.aiExplanation.observe(this) { explanation ->
            if (!explanation.isNullOrBlank()) {
                tvAiExplanation.visibility = View.VISIBLE
                tvAiExplanation.text = "🤖 AI Explanation:\n\n$explanation"
            }
        }

        viewModel.isLoading.observe(this) { loading ->
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            btnAiExplain.isEnabled = !loading
        }
    }

    private fun resetAllButtons() {
        val allButtons = listOf(btnOptionA, btnOptionB, btnOptionC, btnOptionD, btnOptionE)
        allButtons.forEach { btn ->
            btn.isEnabled = true
            btn.alpha = 1f
            btn.strokeColor = defaultStrokeColor ?: ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.primary)
            )
            btn.setTextColor(defaultTextColor ?: ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.primary)
            ))
            btn.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, android.R.color.transparent)
            )
            btn.iconTint = null
        }
    }

    private fun disableOptions() {
        val allButtons = listOf(btnOptionA, btnOptionB, btnOptionC, btnOptionD, btnOptionE)
        allButtons.forEach { it.isEnabled = false }
    }

    private fun highlightSelected(button: MaterialButton) {
        button.strokeWidth = 4
    }

    private fun highlightCorrectAnswer(correct: String) {
        val btn = getButtonForAnswer(correct) ?: return
        btn.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.correct_green_bg)
        )
        btn.setTextColor(ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.correct_green_text)
        ))
        btn.strokeColor = ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.correct_green)
        )
        btn.strokeWidth = 3
    }

    private fun highlightWrongAnswer(selected: String) {
        val btn = getButtonForAnswer(selected) ?: return
        btn.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.wrong_red_bg)
        )
        btn.setTextColor(ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.wrong_red_text)
        ))
        btn.strokeColor = ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.wrong_red)
        )
        btn.strokeWidth = 3
    }

    private fun getButtonForAnswer(answer: String): MaterialButton? {
        return when (answer.uppercase()) {
            "A" -> btnOptionA
            "B" -> btnOptionB
            "C" -> btnOptionC
            "D" -> btnOptionD
            "E" -> btnOptionE
            else -> null
        }
    }
}
