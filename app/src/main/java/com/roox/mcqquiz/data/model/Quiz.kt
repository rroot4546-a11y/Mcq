package com.roox.mcqquiz.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quizzes")
data class Quiz(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val sourceFileName: String,
    val totalQuestions: Int = 0,
    val answeredQuestions: Int = 0,
    val correctAnswers: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
