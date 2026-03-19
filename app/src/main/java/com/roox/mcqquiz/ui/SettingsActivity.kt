package com.roox.mcqquiz.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.roox.mcqquiz.R
import com.roox.mcqquiz.service.AiService
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SettingsActivity"
        // Replace with your actual Web Client ID from Google Cloud Console
        private const val WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"

        private const val PREF_ACTIVE_PROVIDER = "active_provider"
        private const val PREF_GOOGLE_ID_TOKEN = "google_id_token"
        private const val PREF_GOOGLE_EMAIL = "google_user_email"
        private const val PREF_OPENROUTER_KEY = "openrouter_api_key"
        private const val PREF_OPENROUTER_MODEL = "openrouter_model"
        private const val PREF_GEMINI_KEY = "gemini_direct_api_key"
        private const val PREF_GEMINI_MODEL = "gemini_direct_model"
        private const val PREF_OPENAI_KEY = "openai_api_key"
        private const val PREF_OPENAI_MODEL = "openai_model"
        private const val PREF_ANTHROPIC_KEY = "anthropic_api_key"
        private const val PREF_ANTHROPIC_MODEL = "anthropic_model"

        val OPENROUTER_MODELS = listOf(
            "google/gemini-2.0-flash-001",
            "google/gemini-2.5-pro-preview",
            "anthropic/claude-sonnet-4",
            "anthropic/claude-opus-4",
            "anthropic/claude-haiku-4-5",
            "openai/gpt-4o",
            "openai/gpt-4o-mini",
            "openai/o3-mini",
            "deepseek/deepseek-r1",
            "deepseek/deepseek-v3",
            "meta-llama/llama-3.3-70b-instruct",
            "qwen/qwen-2.5-72b-instruct",
            "mistralai/mistral-large",
            "cohere/command-r-plus-08-2024",
            "x-ai/grok-2",
            "nvidia/llama-3.1-nemotron-70b-instruct",
            "google/gemini-2.0-flash-thinking-exp",
            "amazon/nova-pro-v1",
            "microsoft/phi-4",
            "inflection/inflection-3-productivity"
        )
        val GEMINI_DIRECT_MODELS = listOf(
            "gemini-2.0-flash",
            "gemini-2.5-pro-preview",
            "gemini-1.5-flash"
        )
        val OPENAI_MODELS = listOf(
            "gpt-4o",
            "gpt-4o-mini",
            "o3-mini",
            "gpt-4-turbo"
        )
        val ANTHROPIC_MODELS = listOf(
            "claude-opus-4-5",
            "claude-sonnet-4-5",
            "claude-haiku-4-5"
        )
    }

    private lateinit var prefs: SharedPreferences

    // Active provider selector
    private lateinit var rgActiveProvider: RadioGroup
    private lateinit var rbGoogleOAuth: RadioButton
    private lateinit var rbOpenRouter: RadioButton
    private lateinit var rbGeminiDirect: RadioButton
    private lateinit var rbOpenAI: RadioButton
    private lateinit var rbAnthropic: RadioButton

    // Google OAuth section
    private lateinit var headerGoogleOAuth: View
    private lateinit var bodyGoogleOAuth: View
    private lateinit var tvGoogleExpandIcon: TextView
    private lateinit var tvGoogleActiveCheck: TextView
    private lateinit var layoutSignedOut: View
    private lateinit var layoutSignedIn: View
    private lateinit var btnGoogleSignIn: MaterialButton
    private lateinit var btnGoogleSignOut: MaterialButton
    private lateinit var tvSignedInEmail: TextView

    // OpenRouter section
    private lateinit var headerOpenRouter: View
    private lateinit var bodyOpenRouter: View
    private lateinit var tvOpenRouterExpandIcon: TextView
    private lateinit var tvOpenRouterActiveCheck: TextView
    private lateinit var etOpenRouterKey: TextInputEditText
    private lateinit var spinnerOpenRouterModel: Spinner
    private lateinit var btnSaveOpenRouter: MaterialButton

    // Gemini Direct section
    private lateinit var headerGeminiDirect: View
    private lateinit var bodyGeminiDirect: View
    private lateinit var tvGeminiDirectExpandIcon: TextView
    private lateinit var tvGeminiDirectActiveCheck: TextView
    private lateinit var etGeminiDirectKey: TextInputEditText
    private lateinit var spinnerGeminiDirectModel: Spinner
    private lateinit var btnSaveGeminiDirect: MaterialButton

    // OpenAI section
    private lateinit var headerOpenAI: View
    private lateinit var bodyOpenAI: View
    private lateinit var tvOpenAIExpandIcon: TextView
    private lateinit var tvOpenAIActiveCheck: TextView
    private lateinit var etOpenAIKey: TextInputEditText
    private lateinit var spinnerOpenAIModel: Spinner
    private lateinit var btnSaveOpenAI: MaterialButton

    // Anthropic section
    private lateinit var headerAnthropic: View
    private lateinit var bodyAnthropic: View
    private lateinit var tvAnthropicExpandIcon: TextView
    private lateinit var tvAnthropicActiveCheck: TextView
    private lateinit var etAnthropicKey: TextInputEditText
    private lateinit var spinnerAnthropicModel: Spinner
    private lateinit var btnSaveAnthropic: MaterialButton

    // Bottom
    private lateinit var btnTestConnection: MaterialButton
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("mcq_prefs", MODE_PRIVATE)

        bindViews()
        setupSpinners()
        loadPrefs()
        setupAccordions()
        setupActiveProviderRadio()
        setupSaveButtons()
        setupGoogleSignIn()

        btnTestConnection.setOnClickListener { testActiveProvider() }
    }

    private fun bindViews() {
        rgActiveProvider = findViewById(R.id.rgActiveProvider)
        rbGoogleOAuth = findViewById(R.id.rbGoogleOAuth)
        rbOpenRouter = findViewById(R.id.rbOpenRouter)
        rbGeminiDirect = findViewById(R.id.rbGeminiDirect)
        rbOpenAI = findViewById(R.id.rbOpenAI)
        rbAnthropic = findViewById(R.id.rbAnthropic)

        headerGoogleOAuth = findViewById(R.id.headerGoogleOAuth)
        bodyGoogleOAuth = findViewById(R.id.bodyGoogleOAuth)
        tvGoogleExpandIcon = findViewById(R.id.tvGoogleExpandIcon)
        tvGoogleActiveCheck = findViewById(R.id.tvGoogleActiveCheck)
        layoutSignedOut = findViewById(R.id.layoutSignedOut)
        layoutSignedIn = findViewById(R.id.layoutSignedIn)
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn)
        btnGoogleSignOut = findViewById(R.id.btnGoogleSignOut)
        tvSignedInEmail = findViewById(R.id.tvSignedInEmail)

        headerOpenRouter = findViewById(R.id.headerOpenRouter)
        bodyOpenRouter = findViewById(R.id.bodyOpenRouter)
        tvOpenRouterExpandIcon = findViewById(R.id.tvOpenRouterExpandIcon)
        tvOpenRouterActiveCheck = findViewById(R.id.tvOpenRouterActiveCheck)
        etOpenRouterKey = findViewById(R.id.etOpenRouterKey)
        spinnerOpenRouterModel = findViewById(R.id.spinnerOpenRouterModel)
        btnSaveOpenRouter = findViewById(R.id.btnSaveOpenRouter)

        headerGeminiDirect = findViewById(R.id.headerGeminiDirect)
        bodyGeminiDirect = findViewById(R.id.bodyGeminiDirect)
        tvGeminiDirectExpandIcon = findViewById(R.id.tvGeminiDirectExpandIcon)
        tvGeminiDirectActiveCheck = findViewById(R.id.tvGeminiDirectActiveCheck)
        etGeminiDirectKey = findViewById(R.id.etGeminiDirectKey)
        spinnerGeminiDirectModel = findViewById(R.id.spinnerGeminiDirectModel)
        btnSaveGeminiDirect = findViewById(R.id.btnSaveGeminiDirect)

        headerOpenAI = findViewById(R.id.headerOpenAI)
        bodyOpenAI = findViewById(R.id.bodyOpenAI)
        tvOpenAIExpandIcon = findViewById(R.id.tvOpenAIExpandIcon)
        tvOpenAIActiveCheck = findViewById(R.id.tvOpenAIActiveCheck)
        etOpenAIKey = findViewById(R.id.etOpenAIKey)
        spinnerOpenAIModel = findViewById(R.id.spinnerOpenAIModel)
        btnSaveOpenAI = findViewById(R.id.btnSaveOpenAI)

        headerAnthropic = findViewById(R.id.headerAnthropic)
        bodyAnthropic = findViewById(R.id.bodyAnthropic)
        tvAnthropicExpandIcon = findViewById(R.id.tvAnthropicExpandIcon)
        tvAnthropicActiveCheck = findViewById(R.id.tvAnthropicActiveCheck)
        etAnthropicKey = findViewById(R.id.etAnthropicKey)
        spinnerAnthropicModel = findViewById(R.id.spinnerAnthropicModel)
        btnSaveAnthropic = findViewById(R.id.btnSaveAnthropic)

        btnTestConnection = findViewById(R.id.btnTestConnection)
        tvStatus = findViewById(R.id.tvStatus)
    }

    private fun setupSpinners() {
        spinnerOpenRouterModel.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, OPENROUTER_MODELS)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerGeminiDirectModel.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, GEMINI_DIRECT_MODELS)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerOpenAIModel.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, OPENAI_MODELS)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerAnthropicModel.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ANTHROPIC_MODELS)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun loadPrefs() {
        etOpenRouterKey.setText(prefs.getString(PREF_OPENROUTER_KEY, ""))
        spinnerOpenRouterModel.setSelection(
            OPENROUTER_MODELS.indexOf(prefs.getString(PREF_OPENROUTER_MODEL, OPENROUTER_MODELS[0])).coerceAtLeast(0)
        )

        etGeminiDirectKey.setText(prefs.getString(PREF_GEMINI_KEY, ""))
        spinnerGeminiDirectModel.setSelection(
            GEMINI_DIRECT_MODELS.indexOf(prefs.getString(PREF_GEMINI_MODEL, GEMINI_DIRECT_MODELS[0])).coerceAtLeast(0)
        )

        etOpenAIKey.setText(prefs.getString(PREF_OPENAI_KEY, ""))
        spinnerOpenAIModel.setSelection(
            OPENAI_MODELS.indexOf(prefs.getString(PREF_OPENAI_MODEL, OPENAI_MODELS[0])).coerceAtLeast(0)
        )

        etAnthropicKey.setText(prefs.getString(PREF_ANTHROPIC_KEY, ""))
        spinnerAnthropicModel.setSelection(
            ANTHROPIC_MODELS.indexOf(prefs.getString(PREF_ANTHROPIC_MODEL, ANTHROPIC_MODELS[0])).coerceAtLeast(0)
        )

        // Update active provider radio
        when (prefs.getString(PREF_ACTIVE_PROVIDER, "openrouter")) {
            "google_oauth" -> rbGoogleOAuth.isChecked = true
            "openrouter" -> rbOpenRouter.isChecked = true
            "gemini_direct" -> rbGeminiDirect.isChecked = true
            "openai" -> rbOpenAI.isChecked = true
            "anthropic" -> rbAnthropic.isChecked = true
            else -> rbOpenRouter.isChecked = true
        }
        updateActiveCheckmarks()

        // Google sign-in state
        val email = prefs.getString(PREF_GOOGLE_EMAIL, null)
        if (!email.isNullOrBlank()) {
            layoutSignedOut.visibility = View.GONE
            layoutSignedIn.visibility = View.VISIBLE
            tvSignedInEmail.text = "Signed in as: $email"
        }
    }

    private fun setupAccordions() {
        setupToggle(headerGoogleOAuth, bodyGoogleOAuth, tvGoogleExpandIcon)
        setupToggle(headerOpenRouter, bodyOpenRouter, tvOpenRouterExpandIcon)
        setupToggle(headerGeminiDirect, bodyGeminiDirect, tvGeminiDirectExpandIcon)
        setupToggle(headerOpenAI, bodyOpenAI, tvOpenAIExpandIcon)
        setupToggle(headerAnthropic, bodyAnthropic, tvAnthropicExpandIcon)

        // Auto-expand the active provider's section
        when (prefs.getString(PREF_ACTIVE_PROVIDER, "openrouter")) {
            "google_oauth" -> expand(bodyGoogleOAuth, tvGoogleExpandIcon)
            "openrouter" -> expand(bodyOpenRouter, tvOpenRouterExpandIcon)
            "gemini_direct" -> expand(bodyGeminiDirect, tvGeminiDirectExpandIcon)
            "openai" -> expand(bodyOpenAI, tvOpenAIExpandIcon)
            "anthropic" -> expand(bodyAnthropic, tvAnthropicExpandIcon)
        }
    }

    private fun setupToggle(header: View, body: View, icon: TextView) {
        header.setOnClickListener {
            if (body.visibility == View.VISIBLE) {
                body.visibility = View.GONE
                icon.text = "\u25BC"
            } else {
                body.visibility = View.VISIBLE
                icon.text = "\u25B2"
            }
        }
    }

    private fun expand(body: View, icon: TextView) {
        body.visibility = View.VISIBLE
        icon.text = "\u25B2"
    }

    private fun setupActiveProviderRadio() {
        rgActiveProvider.setOnCheckedChangeListener { _, checkedId ->
            val provider = when (checkedId) {
                R.id.rbGoogleOAuth -> "google_oauth"
                R.id.rbOpenRouter -> "openrouter"
                R.id.rbGeminiDirect -> "gemini_direct"
                R.id.rbOpenAI -> "openai"
                R.id.rbAnthropic -> "anthropic"
                else -> "openrouter"
            }
            prefs.edit().putString(PREF_ACTIVE_PROVIDER, provider).apply()
            updateActiveCheckmarks()
            tvStatus.text = "Active provider set to: $provider"
        }
    }

    private fun updateActiveCheckmarks() {
        val active = prefs.getString(PREF_ACTIVE_PROVIDER, "openrouter")
        tvGoogleActiveCheck.text = if (active == "google_oauth") "✅" else ""
        tvOpenRouterActiveCheck.text = if (active == "openrouter") "✅" else ""
        tvGeminiDirectActiveCheck.text = if (active == "gemini_direct") "✅" else ""
        tvOpenAIActiveCheck.text = if (active == "openai") "✅" else ""
        tvAnthropicActiveCheck.text = if (active == "anthropic") "✅" else ""
    }

    private fun setupSaveButtons() {
        btnSaveOpenRouter.setOnClickListener {
            val key = etOpenRouterKey.text.toString().trim()
            val model = OPENROUTER_MODELS[spinnerOpenRouterModel.selectedItemPosition]
            prefs.edit()
                .putString(PREF_OPENROUTER_KEY, key)
                .putString(PREF_OPENROUTER_MODEL, model)
                .apply()
            tvStatus.text = "OpenRouter settings saved."
            Toast.makeText(this, "OpenRouter saved", Toast.LENGTH_SHORT).show()
        }

        btnSaveGeminiDirect.setOnClickListener {
            val key = etGeminiDirectKey.text.toString().trim()
            val model = GEMINI_DIRECT_MODELS[spinnerGeminiDirectModel.selectedItemPosition]
            prefs.edit()
                .putString(PREF_GEMINI_KEY, key)
                .putString(PREF_GEMINI_MODEL, model)
                .apply()
            tvStatus.text = "Gemini Direct settings saved."
            Toast.makeText(this, "Gemini Direct saved", Toast.LENGTH_SHORT).show()
        }

        btnSaveOpenAI.setOnClickListener {
            val key = etOpenAIKey.text.toString().trim()
            val model = OPENAI_MODELS[spinnerOpenAIModel.selectedItemPosition]
            prefs.edit()
                .putString(PREF_OPENAI_KEY, key)
                .putString(PREF_OPENAI_MODEL, model)
                .apply()
            tvStatus.text = "OpenAI settings saved."
            Toast.makeText(this, "OpenAI saved", Toast.LENGTH_SHORT).show()
        }

        btnSaveAnthropic.setOnClickListener {
            val key = etAnthropicKey.text.toString().trim()
            val model = ANTHROPIC_MODELS[spinnerAnthropicModel.selectedItemPosition]
            prefs.edit()
                .putString(PREF_ANTHROPIC_KEY, key)
                .putString(PREF_ANTHROPIC_MODEL, model)
                .apply()
            tvStatus.text = "Anthropic settings saved."
            Toast.makeText(this, "Anthropic saved", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupGoogleSignIn() {
        btnGoogleSignIn.setOnClickListener { launchGoogleSignIn() }
        btnGoogleSignOut.setOnClickListener {
            prefs.edit()
                .remove(PREF_GOOGLE_ID_TOKEN)
                .remove(PREF_GOOGLE_EMAIL)
                .apply()
            layoutSignedOut.visibility = View.VISIBLE
            layoutSignedIn.visibility = View.GONE
            tvStatus.text = "Signed out of Google."
        }
    }

    private fun launchGoogleSignIn() {
        tvStatus.text = "Signing in..."
        btnGoogleSignIn.isEnabled = false

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(WEB_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val credentialManager = CredentialManager.create(this)

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(this@SettingsActivity, request)
                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleCred.idToken
                    val email = googleCred.id

                    prefs.edit()
                        .putString(PREF_GOOGLE_ID_TOKEN, idToken)
                        .putString(PREF_GOOGLE_EMAIL, email)
                        .apply()

                    tvSignedInEmail.text = "Signed in as: $email"
                    layoutSignedOut.visibility = View.GONE
                    layoutSignedIn.visibility = View.VISIBLE
                    tvStatus.text = "Signed in as $email. Google AI is ready."
                } else {
                    tvStatus.text = "Sign-in failed: unexpected credential type."
                }
            } catch (e: GetCredentialCancellationException) {
                tvStatus.text = "Sign-in cancelled."
            } catch (e: Exception) {
                Log.e(TAG, "Google sign-in error", e)
                tvStatus.text = "Sign-in error: ${e.message}"
            } finally {
                btnGoogleSignIn.isEnabled = true
            }
        }
    }

    private fun testActiveProvider() {
        tvStatus.text = "Testing connection..."
        btnTestConnection.isEnabled = false

        lifecycleScope.launch {
            try {
                val service = AiService.fromPrefs(prefs)
                val success = service.testConnection()
                tvStatus.text = if (success) "Connected! AI is ready." else "Connection failed. Check your credentials."
            } catch (e: Exception) {
                tvStatus.text = "Error: ${e.message}"
            } finally {
                btnTestConnection.isEnabled = true
            }
        }
    }
}
