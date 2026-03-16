package com.roox.mcqquiz

import android.app.Application
import com.roox.mcqquiz.data.database.QuizDatabase
import com.roox.mcqquiz.data.repository.QuizRepository

class MCQApplication : Application() {
    val database by lazy { QuizDatabase.getDatabase(this) }
    val repository by lazy { QuizRepository(database.quizDao()) }
}
