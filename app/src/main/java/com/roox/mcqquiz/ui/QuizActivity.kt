package com.roox.mcqquiz.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var btnAiExplain: Button
    private lateinit var btnNext: Button
    private lateinit var btnPrevious: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var progressQuiz: ProgressBar

    private var selectedAnswer: String? = null
    private lateinit var prefs: SharedPreferences

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

        val optionClickListener = View.OnClickListener { v ->
            selectedAnswer = when (v.id) {
                R.id.btnOptionA -> "A"
                R.id.btnOptionB -> "B"
                R.id.btnOptionC -> "C"
                R.id.btnOptionD -> "D"
                R.id.btnOptionE -> "E"
                else -> null
            }
            selectedAnswer?.let { viewModel.submitAnswer(it) }
            disableOptions()
        }

        btnOptionA.setOnClickListener(optionClickListener)
        btnOptionB.setOnClickListener(optionClickListener)
        btnOptionC.setOnClickListener(optionClickListener)
        btnOptionD.setOnClickListener(optionClickListener)
        btnOptionE.setOnClickListener(optionClickListener)

        btnNext.setOnClickListener { viewModel.nextQuestion() }
        btnPrevious.setOnClickListener { viewModel.previousQuestion() }

        btnAiExplain.setOnClickListener {
            val apiKey = prefs.getString("gemini_api_key", "") ?: ""
            if (apiKey.isBlank()) {
                Toast.makeText(this, "Please set your Gemini API key in Settings", Toast.LENGTH_LONG).show()
            } else {
                viewModel.requestAiExplanation(apiKey)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.currentQuestion.observe(this) { question ->
            question?.let {
                tvQuestionNumber.text = "Question ${it.questionNumber}"
                tvQuestionText.text = it.questionText
                btnOptionA.text = "A) ${it.optionA}"
                btnOptionB.text = "B) ${it.optionB}"
                btnOptionC.text = "C) ${it.optionC}"
                btnOptionD.text = "D) ${it.optionD}"

                if (it.optionE != null) {
                    btnOptionE.visibility = View.VISIBLE
                    btnOptionE.text = "E) ${it.optionE}"
                } else {
                    btnOptionE.visibility = View.GONE
                }

                enableOptions()
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
            }
            btnPrevious.isEnabled = idx > 0
        }

        viewModel.answerResult.observe(this) { result ->
            result?.let {
                tvResult.visibility = View.VISIBLE
                tvResult.text = if (it.isCorrect) "✅ Correct!" else "❌ Incorrect. Answer: ${it.correctAnswer}"
                tvResult.setTextColor(getColor(if (it.isCorrect) android.R.color.holo_green_dark else android.R.color.holo_red_dark))

                if (it.explanation.isNotBlank()) {
                    tvExplanation.visibility = View.VISIBLE
                    tvExplanation.text = it.explanation
                }

                btnAiExplain.visibility = View.VISIBLE
                highlightCorrectAnswer(it.correctAnswer)
            }
        }

        viewModel.aiExplanation.observe(this) { explanation ->
            if (explanation.isNotBlank()) {
                tvAiExplanation.visibility = View.VISIBLE
                tvAiExplanation.text = "🤖 AI Explanation:\n\n$explanation"
            }
        }

        viewModel.isLoading.observe(this) { loading ->
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }
    }

    private fun enableOptions() {
        listOf(btnOptionA, btnOptionB, btnOptionC, btnOptionD, btnOptionE).forEach {
            it.isEnabled = true
            it.alpha = 1f
        }
    }

    private fun disableOptions() {
        listOf(btnOptionA, btnOptionB, btnOptionC, btnOptionD, btnOptionE).forEach {
            it.isEnabled = false
            it.alpha = 0.7f
        }
    }

    private fun highlightCorrectAnswer(correct: String) {
        val correctBtn = when (correct.uppercase()) {
            "A" -> btnOptionA
            "B" -> btnOptionB
            "C" -> btnOptionC
            "D" -> btnOptionD
            "E" -> btnOptionE
            else -> null
        }
        correctBtn?.setBackgroundColor(getColor(android.R.color.holo_green_light))
    }
}
