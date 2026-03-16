package com.roox.mcqquiz.ui

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.roox.mcqquiz.R
import com.roox.mcqquiz.service.AiService
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var radioGemini: RadioButton
    private lateinit var radioOllama: RadioButton
    private lateinit var radioCustom: RadioButton
    private lateinit var layoutGemini: LinearLayout
    private lateinit var layoutOllama: LinearLayout
    private lateinit var layoutCustom: LinearLayout
    private lateinit var etApiKey: TextInputEditText
    private lateinit var etOllamaUrl: TextInputEditText
    private lateinit var etOllamaModel: TextInputEditText
    private lateinit var etCustomUrl: TextInputEditText
    private lateinit var etCustomModel: TextInputEditText
    private lateinit var etCustomApiKey: TextInputEditText
    private lateinit var btnSave: Button
    private lateinit var btnTest: Button
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        radioGemini = findViewById(R.id.radioGemini)
        radioOllama = findViewById(R.id.radioOllama)
        radioCustom = findViewById(R.id.radioCustom)
        layoutGemini = findViewById(R.id.layoutGemini)
        layoutOllama = findViewById(R.id.layoutOllama)
        layoutCustom = findViewById(R.id.layoutCustom)
        etApiKey = findViewById(R.id.etApiKey)
        etOllamaUrl = findViewById(R.id.etOllamaUrl)
        etOllamaModel = findViewById(R.id.etOllamaModel)
        etCustomUrl = findViewById(R.id.etCustomUrl)
        etCustomModel = findViewById(R.id.etCustomModel)
        etCustomApiKey = findViewById(R.id.etCustomApiKey)
        btnSave = findViewById(R.id.btnSave)
        btnTest = findViewById(R.id.btnTest)
        tvStatus = findViewById(R.id.tvStatus)

        val prefs = getSharedPreferences("mcq_prefs", MODE_PRIVATE)

        // Load saved settings
        val savedProvider = prefs.getString("ai_provider", "gemini")
        etApiKey.setText(prefs.getString("gemini_api_key", ""))
        etOllamaUrl.setText(prefs.getString("ollama_url", "http://localhost:11434"))
        etOllamaModel.setText(prefs.getString("ollama_model", "llama3"))
        etCustomUrl.setText(prefs.getString("custom_api_url", ""))
        etCustomModel.setText(prefs.getString("custom_model", ""))
        etCustomApiKey.setText(prefs.getString("custom_api_key", ""))

        showProviderLayout(savedProvider ?: "gemini")
        when (savedProvider) {
            "ollama" -> radioOllama.isChecked = true
            "custom" -> radioCustom.isChecked = true
            else -> radioGemini.isChecked = true
        }

        radioGemini.setOnCheckedChangeListener { _, checked -> if (checked) showProviderLayout("gemini") }
        radioOllama.setOnCheckedChangeListener { _, checked -> if (checked) showProviderLayout("ollama") }
        radioCustom.setOnCheckedChangeListener { _, checked -> if (checked) showProviderLayout("custom") }

        btnSave.setOnClickListener { saveSettings(prefs) }
        btnTest.setOnClickListener { testConnection(prefs) }
    }

    private fun showProviderLayout(provider: String) {
        layoutGemini.visibility = if (provider == "gemini") View.VISIBLE else View.GONE
        layoutOllama.visibility = if (provider == "ollama") View.VISIBLE else View.GONE
        layoutCustom.visibility = if (provider == "custom") View.VISIBLE else View.GONE
    }

    private fun getSelectedProvider(): String {
        return when {
            radioOllama.isChecked -> "ollama"
            radioCustom.isChecked -> "custom"
            else -> "gemini"
        }
    }

    private fun saveSettings(prefs: android.content.SharedPreferences) {
        prefs.edit()
            .putString("ai_provider", getSelectedProvider())
            .putString("gemini_api_key", etApiKey.text.toString().trim())
            .putString("ollama_url", etOllamaUrl.text.toString().trim())
            .putString("ollama_model", etOllamaModel.text.toString().trim())
            .putString("custom_api_url", etCustomUrl.text.toString().trim())
            .putString("custom_model", etCustomModel.text.toString().trim())
            .putString("custom_api_key", etCustomApiKey.text.toString().trim())
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
            val name = when (getSelectedProvider()) {
                "ollama" -> "Ollama"
                "custom" -> "Custom AI"
                else -> "Gemini"
            }
            tvStatus.text = if (success) "✅ $name connection successful!" else "❌ Connection failed. Check settings."
            btnTest.isEnabled = true
        }
    }
}
