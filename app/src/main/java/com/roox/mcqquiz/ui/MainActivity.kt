package com.roox.mcqquiz.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.view.View
import com.roox.mcqquiz.R
import com.roox.mcqquiz.data.model.Quiz
import com.roox.mcqquiz.viewmodel.QuizViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: QuizViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnImport: Button
    private lateinit var btnStats: Button
    private lateinit var btnSettings: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: View

    private val pdfPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val fileName = uri.lastPathSegment ?: "Unknown.pdf"
                viewModel.importPdf(uri, fileName)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[QuizViewModel::class.java]

        recyclerView = findViewById(R.id.recyclerQuizzes)
        btnImport = findViewById(R.id.btnImportPdf)
        btnStats = findViewById(R.id.btnStats)
        progressBar = findViewById(R.id.progressBar)
        tvEmpty = findViewById(R.id.tvEmpty)

        recyclerView.layoutManager = LinearLayoutManager(this)

        btnImport.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/pdf"
            }
            pdfPicker.launch(intent)
        }

        btnSettings = findViewById(R.id.btnSettings)

        btnStats.setOnClickListener {
            showStatsDialog()
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        viewModel.allQuizzes.observe(this) { quizzes ->
            if (quizzes.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                tvEmpty.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                recyclerView.adapter = QuizListAdapter(quizzes,
                    onStudy = { quiz -> startQuizActivity(quiz.id, "STUDY") },
                    onExam = { quiz -> startQuizActivity(quiz.id, "EXAM") },
                    onReview = { quiz -> startQuizActivity(quiz.id, "REVIEW") },
                    onDelete = { quiz -> viewModel.deleteQuiz(quiz) }
                )
            }
        }

        viewModel.isLoading.observe(this) { loading ->
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(this) { error ->
            error?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        }
    }

    private fun startQuizActivity(quizId: Int, mode: String) {
        val intent = Intent(this, QuizActivity::class.java).apply {
            putExtra("QUIZ_ID", quizId)
            putExtra("QUIZ_MODE", mode)
        }
        startActivity(intent)
    }

    private fun showStatsDialog() {
        viewModel.loadStats()
        viewModel.stats.observe(this) { stats ->
            AlertDialog.Builder(this)
                .setTitle("Your Statistics")
                .setMessage("""
                    Total Questions Answered: ${stats.totalAnswered}
                    Correct Answers: ${stats.totalCorrect}
                    Accuracy: ${"%.1f".format(stats.accuracy)}%
                """.trimIndent())
                .setPositiveButton("OK", null)
                .show()
        }
    }

    // RecyclerView Adapter
    inner class QuizListAdapter(
        private val quizzes: List<Quiz>,
        private val onStudy: (Quiz) -> Unit,
        private val onExam: (Quiz) -> Unit,
        private val onReview: (Quiz) -> Unit,
        private val onDelete: (Quiz) -> Unit
    ) : RecyclerView.Adapter<QuizListAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvQuizTitle)
            val tvInfo: TextView = view.findViewById(R.id.tvQuizInfo)
            val btnStudy: Button = view.findViewById(R.id.btnStudyMode)
            val btnExam: Button = view.findViewById(R.id.btnExamMode)
            val btnReview: Button = view.findViewById(R.id.btnReviewMode)
            val btnDelete: Button = view.findViewById(R.id.btnDeleteQuiz)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_quiz, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val quiz = quizzes[position]
            holder.tvTitle.text = quiz.title
            holder.tvInfo.text = "${quiz.totalQuestions} questions | ${quiz.correctAnswers}/${quiz.answeredQuestions} correct"
            holder.btnStudy.setOnClickListener { onStudy(quiz) }
            holder.btnExam.setOnClickListener { onExam(quiz) }
            holder.btnReview.setOnClickListener { onReview(quiz) }
            holder.btnDelete.setOnClickListener { onDelete(quiz) }
        }

        override fun getItemCount() = quizzes.size
    }
}
