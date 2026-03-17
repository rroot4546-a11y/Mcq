package com.roox.mcqquiz.ui

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.roox.mcqquiz.R
import com.roox.mcqquiz.service.AiService
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var etApiKey: TextInputEditText
    private lateinit var etModel: TextInputEditText
    private lateinit var btnSave: Button
    private lateinit var btnTest: Button
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        etApiKey = findViewById(R.id.etApiKey)
        etModel = findViewById(R.id.etModel)
        btnSave = findViewById(R.id.btnSave)
        btnTest = findViewById(R.id.btnTest)
        tvStatus = findViewById(R.id.tvStatus)

        val prefs = getSharedPreferences("mcq_prefs", MODE_PRIVATE)

        // Load saved
        etApiKey.setText(prefs.getString("openrouter_api_key", ""))
        etModel.setText(prefs.getString("openrouter_model", "google/gemini-2.0-flash-001"))

        btnSave.setOnClickListener { saveSettings(prefs) }
        btnTest.setOnClickListener { testConnection(prefs) }
    }

    private fun saveSettings(prefs: android.content.SharedPreferences) {
        val key = etApiKey.text.toString().trim()
        val model = etModel.text.toString().trim().ifBlank { "google/gemini-2.0-flash-001" }

        prefs.edit()
            .putString("openrouter_api_key", key)
            .putString("openrouter_model", model)
            .apply()

        tvStatus.text = "✅ Settings saved!"
        Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show()
    }

    private fun testConnection(prefs: android.content.SharedPreferences) {
        saveSettings(prefs)
        tvStatus.text = "🔄 Testing connection..."
        btnTest.isEnabled = false

        lifecycleScope.launch {
            try {
                val service = AiService.fromPrefs(prefs)
                val success = service.testConnection()
                tvStatus.text = if (success) "✅ Connected! AI is ready." else "❌ Connection failed. Check your API key."
            } catch (e: Exception) {
                tvStatus.text = "❌ Error: ${e.message}"
            } finally {
                btnTest.isEnabled = true
            }
        }
    }
}
