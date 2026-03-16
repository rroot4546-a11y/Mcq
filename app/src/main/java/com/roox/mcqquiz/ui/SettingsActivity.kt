package com.roox.mcqquiz.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.roox.mcqquiz.R
import com.roox.mcqquiz.service.AiService
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var etApiKey: TextInputEditText
    private lateinit var btnSave: Button
    private lateinit var btnTest: Button
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        etApiKey = findViewById(R.id.etApiKey)
        btnSave = findViewById(R.id.btnSave)
        btnTest = findViewById(R.id.btnTest)
        tvStatus = findViewById(R.id.tvStatus)

        // Load saved key
        val prefs = getSharedPreferences("mcq_prefs", MODE_PRIVATE)
        val savedKey = prefs.getString("gemini_api_key", "") ?: ""
        if (savedKey.isNotBlank()) {
            etApiKey.setText(savedKey)
            tvStatus.text = "✅ API key saved"
        }

        btnSave.setOnClickListener {
            val key = etApiKey.text.toString().trim()
            if (key.isBlank()) {
                Toast.makeText(this, "Please enter your API key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putString("gemini_api_key", key).apply()
            tvStatus.text = "✅ API key saved"
            Toast.makeText(this, "API key saved!", Toast.LENGTH_SHORT).show()
        }

        btnTest.setOnClickListener {
            val key = etApiKey.text.toString().trim()
            if (key.isBlank()) {
                Toast.makeText(this, "Enter an API key first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            tvStatus.text = "🔄 Testing connection..."
            btnTest.isEnabled = false

            lifecycleScope.launch {
                val service = AiService(key)
                val success = service.testConnection()
                if (success) {
                    tvStatus.text = "✅ Connection successful! Gemini is working."
                    // Auto-save on successful test
                    prefs.edit().putString("gemini_api_key", key).apply()
                } else {
                    tvStatus.text = "❌ Connection failed. Check your API key."
                }
                btnTest.isEnabled = true
            }
        }
    }
}
