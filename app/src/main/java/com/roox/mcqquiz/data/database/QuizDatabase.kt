package com.roox.mcqquiz.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.roox.mcqquiz.data.model.Question
import com.roox.mcqquiz.data.model.Quiz

@Database(entities = [Quiz::class, Question::class], version = 1, exportSchema = false)
abstract class QuizDatabase : RoomDatabase() {
    abstract fun quizDao(): QuizDao

    companion object {
        @Volatile
        private var INSTANCE: QuizDatabase? = null

        fun getDatabase(context: Context): QuizDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    QuizDatabase::class.java,
                    "mcq_quiz_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
