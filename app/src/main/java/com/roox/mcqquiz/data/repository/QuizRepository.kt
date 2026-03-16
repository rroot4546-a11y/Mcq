package com.roox.mcqquiz.data.repository

import androidx.lifecycle.LiveData
import com.roox.mcqquiz.data.database.QuizDao
import com.roox.mcqquiz.data.model.Question
import com.roox.mcqquiz.data.model.Quiz

class QuizRepository(private val quizDao: QuizDao) {

    val allQuizzes: LiveData<List<Quiz>> = quizDao.getAllQuizzes()

    suspend fun insertQuiz(quiz: Quiz): Long = quizDao.insertQuiz(quiz)
    suspend fun insertQuestions(questions: List<Question>) = quizDao.insertQuestions(questions)
    suspend fun updateQuiz(quiz: Quiz) = quizDao.updateQuiz(quiz)
    suspend fun deleteQuiz(quiz: Quiz) = quizDao.deleteQuiz(quiz)

    fun getQuestionsForQuiz(quizId: Int): LiveData<List<Question>> = quizDao.getQuestionsForQuiz(quizId)
    suspend fun getQuestionsForQuizSync(quizId: Int): List<Question> = quizDao.getQuestionsForQuizSync(quizId)
    suspend fun getNextUnansweredQuestion(quizId: Int): Question? = quizDao.getNextUnansweredQuestion(quizId)
    suspend fun getIncorrectQuestions(quizId: Int): List<Question> = quizDao.getIncorrectQuestions(quizId)
    suspend fun updateQuestion(question: Question) = quizDao.updateQuestion(question)

    suspend fun getQuestionCount(quizId: Int): Int = quizDao.getQuestionCount(quizId)
    suspend fun getAnsweredCount(quizId: Int): Int = quizDao.getAnsweredCount(quizId)
    suspend fun getCorrectCount(quizId: Int): Int = quizDao.getCorrectCount(quizId)
    suspend fun getTotalAnswered(): Int = quizDao.getTotalAnswered()
    suspend fun getTotalCorrect(): Int = quizDao.getTotalCorrect()
}
