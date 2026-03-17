package com.roox.mcqquiz.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.material.textfield.TextInputEditText
import com.roox.mcqquiz.R
import com.roox.mcqquiz.service.AiService
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var radioGemini: RadioButton
    private lateinit var radioGoogle: RadioButton
    private lateinit var radioOllama: RadioButton
    private lateinit var radioCustom: RadioButton
    private lateinit var layoutGemini: LinearLayout
    private lateinit var layoutGoogle: LinearLayout
    private lateinit var layoutOllama: LinearLayout
    private lateinit var layoutCustom: LinearLayout
    private lateinit var etApiKey: TextInputEditText
    private lateinit var etOllamaUrl: TextInputEditText
    private lateinit var etOllamaModel: TextInputEditText
    private lateinit var etCustomUrl: TextInputEditText
    private lateinit var etCustomModel: TextInputEditText
    private lateinit var etCustomApiKey: TextInputEditText
    private lateinit var btnGoogleSignIn: Button
    private lateinit var tvGoogleStatus: TextView
    private lateinit var btnSave: Button
    private lateinit var btnTest: Button
    private lateinit var tvStatus: TextView

    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.result
            val email = account?.email ?: "Unknown"
            val token = account?.serverAuthCode ?: account?.idToken ?: ""
            val prefs = getSharedPreferences("mcq_prefs", MODE_PRIVATE)
            prefs.edit()
                .putString("google_email", email)
                .putString("google_token", token)
                .putString("ai_provider", "google")
                .apply()
            tvGoogleStatus.text = "✅ Signed in as: $email"
            tvStatus.text = "✅ Google account connected!"
        } catch (e: Exception) {
            tvGoogleStatus.text = "❌ Sign-in failed: ${e.message}"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        initGoogleSignIn()
        loadSettings()
        setupListeners()
    }

    private fun initViews() {
        radioGemini = findViewById(R.id.radioGemini)
        radioGoogle = findViewById(R.id.radioGoogle)
        radioOllama = findViewById(R.id.radioOllama)
        radioCustom = findViewById(R.id.radioCustom)
        layoutGemini = findViewById(R.id.layoutGemini)
        layoutGoogle = findViewById(R.id.layoutGoogle)
        layoutOllama = findViewById(R.id.layoutOllama)
        layoutCustom = findViewById(R.id.layoutCustom)
        etApiKey = findViewById(R.id.etApiKey)
        etOllamaUrl = findViewById(R.id.etOllamaUrl)
        etOllamaModel = findViewById(R.id.etOllamaModel)
        etCustomUrl = findViewById(R.id.etCustomUrl)
        etCustomModel = findViewById(R.id.etCustomModel)
        etCustomApiKey = findViewById(R.id.etCustomApiKey)
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn)
        tvGoogleStatus = findViewById(R.id.tvGoogleStatus)
        btnSave = findViewById(R.id.btnSave)
        btnTest = findViewById(R.id.btnTest)
        tvStatus = findViewById(R.id.tvStatus)
    }

    private fun initGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/generative-language"))
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("mcq_prefs", MODE_PRIVATE)
        val savedProvider = prefs.getString("ai_provider", "gemini")
        etApiKey.setText(prefs.getString("gemini_api_key", ""))
        etOllamaUrl.setText(prefs.getString("ollama_url", "http://localhost:11434"))
        etOllamaModel.setText(prefs.getString("ollama_model", "llama3"))
        etCustomUrl.setText(prefs.getString("custom_api_url", ""))
        etCustomModel.setText(prefs.getString("custom_model", ""))
        etCustomApiKey.setText(prefs.getString("custom_api_key", ""))

        val email = prefs.getString("google_email", "")
        if (!email.isNullOrBlank()) {
            tvGoogleStatus.text = "✅ Signed in as: $email"
        }

        showProviderLayout(savedProvider ?: "gemini")
        when (savedProvider) {
            "google" -> radioGoogle.isChecked = true
            "ollama" -> radioOllama.isChecked = true
            "custom" -> radioCustom.isChecked = true
            else -> radioGemini.isChecked = true
        }
    }

    private fun setupListeners() {
        radioGemini.setOnCheckedChangeListener { _, c -> if (c) showProviderLayout("gemini") }
        radioGoogle.setOnCheckedChangeListener { _, c -> if (c) showProviderLayout("google") }
        radioOllama.setOnCheckedChangeListener { _, c -> if (c) showProviderLayout("ollama") }
        radioCustom.setOnCheckedChangeListener { _, c -> if (c) showProviderLayout("custom") }

        btnGoogleSignIn.setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
        }

        val prefs = getSharedPreferences("mcq_prefs", MODE_PRIVATE)
        btnSave.setOnClickListener { saveSettings(prefs) }
        btnTest.setOnClickListener { testConnection(prefs) }
    }

    private fun showProviderLayout(provider: String) {
        layoutGemini.visibility = if (provider == "gemini") View.VISIBLE else View.GONE
        layoutGoogle.visibility = if (provider == "google") View.VISIBLE else View.GONE
        layoutOllama.visibility = if (provider == "ollama") View.VISIBLE else View.GONE
        layoutCustom.visibility = if (provider == "custom") View.VISIBLE else View.GONE
    }

    private fun getSelectedProvider(): String = when {
        radioGoogle.isChecked -> "google"
        radioOllama.isChecked -> "ollama"
        radioCustom.isChecked -> "custom"
        else -> "gemini"
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
                "google" -> "Google Gemini"
                "ollama" -> "Ollama"
                "custom" -> "Custom AI"
                else -> "Gemini"
            }
            tvStatus.text = if (success) "✅ $name works!" else "❌ Connection failed."
            btnTest.isEnabled = true
        }
    }
}
