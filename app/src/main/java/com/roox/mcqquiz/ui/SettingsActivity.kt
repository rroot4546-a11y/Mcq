package com.roox.mcqquiz.ui

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.roox.mcqquiz.R
import com.roox.mcqquiz.service.AiProvider
import com.roox.mcqquiz.service.AiService
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var radioGemini: RadioButton
    private lateinit var radioOllama: RadioButton
    private lateinit var layoutGemini: LinearLayout
    private lateinit var layoutOllama: LinearLayout
    private lateinit var etApiKey: TextInputEditText
    private lateinit var etOllamaUrl: TextInputEditText
    private lateinit var etOllamaModel: TextInputEditText
    private lateinit var btnSave: Button
    private lateinit var btnTest: Button
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        radioGemini = findViewById(R.id.radioGemini)
        radioOllama = findViewById(R.id.radioOllama)
        layoutGemini = findViewById(R.id.layoutGemini)
        layoutOllama = findViewById(R.id.layoutOllama)
        etApiKey = findViewById(R.id.etApiKey)
        etOllamaUrl = findViewById(R.id.etOllamaUrl)
        etOllamaModel = findViewById(R.id.etOllamaModel)
        btnSave = findViewById(R.id.btnSave)
        btnTest = findViewById(R.id.btnTest)
        tvStatus = findViewById(R.id.tvStatus)

        val prefs = getSharedPreferences("mcq_prefs", MODE_PRIVATE)

        // Load saved settings
        val savedProvider = prefs.getString("ai_provider", "gemini")
        etApiKey.setText(prefs.getString("gemini_api_key", ""))
        etOllamaUrl.setText(prefs.getString("ollama_url", "https://"))
        etOllamaModel.setText(prefs.getString("ollama_model", "llama3"))

        if (savedProvider == "ollama") {
            radioOllama.isChecked = true
            layoutGemini.visibility = View.GONE
            layoutOllama.visibility = View.VISIBLE
        } else {
            radioGemini.isChecked = true
            layoutGemini.visibility = View.VISIBLE
            layoutOllama.visibility = View.GONE
        }

        radioGemini.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                layoutGemini.visibility = View.VISIBLE
                layoutOllama.visibility = View.GONE
            }
        }

        radioOllama.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                layoutGemini.visibility = View.GONE
                layoutOllama.visibility = View.VISIBLE
            }
        }

        btnSave.setOnClickListener { saveSettings(prefs) }
        btnTest.setOnClickListener { testConnection(prefs) }
    }

    private fun saveSettings(prefs: android.content.SharedPreferences) {
        val provider = if (radioOllama.isChecked) "ollama" else "gemini"
        prefs.edit()
            .putString("ai_provider", provider)
            .putString("gemini_api_key", etApiKey.text.toString().trim())
            .putString("ollama_url", etOllamaUrl.text.toString().trim())
            .putString("ollama_model", etOllamaModel.text.toString().trim())
            .apply()

        tvStatus.text = "✅ Settings saved!"
        Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show()
    }

    private fun testConnection(prefs: android.content.SharedPreferences) {
        saveSettings(prefs)
        tvStatus.text = "🔄 Testing connection..."
        btnTest.isEnabled = false

        lifecycleScope.launch {
            val service = AiService.fromPrefs(prefs)
            val success = service.testConnection()
            if (success) {
                val name = if (radioOllama.isChecked) "Ollama" else "Gemini"
                tvStatus.text = "✅ $name connection successful!"
            } else {
                tvStatus.text = "❌ Connection failed. Check your settings."
            }
            btnTest.isEnabled = true
        }
    }
}
