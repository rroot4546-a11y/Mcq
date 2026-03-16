package com.roox.mcqquiz.data.database

import androidx.lifecycle.LiveData
import androidx.room.*
import com.roox.mcqquiz.data.model.Question
import com.roox.mcqquiz.data.model.Quiz

@Dao
interface QuizDao {
    // Quiz operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuiz(quiz: Quiz): Long

    @Query("SELECT * FROM quizzes ORDER BY createdAt DESC")
    fun getAllQuizzes(): LiveData<List<Quiz>>

    @Query("SELECT * FROM quizzes WHERE id = :quizId")
    suspend fun getQuizById(quizId: Int): Quiz?

    @Update
    suspend fun updateQuiz(quiz: Quiz)

    @Delete
    suspend fun deleteQuiz(quiz: Quiz)

    // Question operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<Question>)

    @Query("SELECT * FROM questions WHERE quizId = :quizId ORDER BY id")
    fun getQuestionsForQuiz(quizId: Int): LiveData<List<Question>>

    @Query("SELECT * FROM questions WHERE quizId = :quizId ORDER BY id")
    suspend fun getQuestionsForQuizSync(quizId: Int): List<Question>

    @Query("SELECT * FROM questions WHERE quizId = :quizId AND userAnswer IS NULL ORDER BY id LIMIT 1")
    suspend fun getNextUnansweredQuestion(quizId: Int): Question?

    @Query("SELECT * FROM questions WHERE quizId = :quizId AND isAnsweredCorrectly = 0 ORDER BY id")
    suspend fun getIncorrectQuestions(quizId: Int): List<Question>

    @Update
    suspend fun updateQuestion(question: Question)

    @Query("SELECT COUNT(*) FROM questions WHERE quizId = :quizId")
    suspend fun getQuestionCount(quizId: Int): Int

    @Query("SELECT COUNT(*) FROM questions WHERE quizId = :quizId AND userAnswer IS NOT NULL")
    suspend fun getAnsweredCount(quizId: Int): Int

    @Query("SELECT COUNT(*) FROM questions WHERE quizId = :quizId AND isAnsweredCorrectly = 1")
    suspend fun getCorrectCount(quizId: Int): Int

    // Statistics
    @Query("SELECT COUNT(*) FROM questions WHERE userAnswer IS NOT NULL")
    suspend fun getTotalAnswered(): Int

    @Query("SELECT COUNT(*) FROM questions WHERE isAnsweredCorrectly = 1")
    suspend fun getTotalCorrect(): Int
}
