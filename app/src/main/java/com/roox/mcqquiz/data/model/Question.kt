package com.roox.mcqquiz.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "questions")
data class Question(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val quizId: Int,
    val questionNumber: String,
    val questionText: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val optionE: String? = null,
    val correctAnswer: String,
    val explanation: String = "",
    val aiExplanation: String = "",
    val userAnswer: String? = null,
    val isAnsweredCorrectly: Boolean? = null
)
