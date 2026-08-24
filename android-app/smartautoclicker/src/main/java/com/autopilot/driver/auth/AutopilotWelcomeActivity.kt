package com.autopilot.driver.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.text.InputType
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.autopilot.driver.data.remote.AuthResult
import com.autopilot.driver.data.remote.AutopilotAccountRepository
import com.autopilot.driver.data.remote.AutopilotSessionStore
import com.autopilot.driver.data.remote.SupabaseRestClient
import com.autopilot.driver.home.AutopilotHomeActivity
import com.buzbuz.smartautoclicker.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AutopilotWelcomeActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var statusText: TextView

    private val accountRepository by lazy {
        AutopilotAccountRepository(
            client = SupabaseRestClient(),
            sessionStore = AutopilotSessionStore(applicationContext),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        emailInput = findViewById(R.id.auth_email)
        passwordInput = findViewById(R.id.auth_password)
        statusText = findViewById(R.id.auth_status)

        findViewById<Button>(R.id.auth_sign_in).setOnClickListener { signIn() }
        findViewById<Button>(R.id.auth_sign_up).setOnClickListener { signUp() }
        findViewById<Button>(R.id.auth_forgot).setOnClickListener { forgotPassword() }
        findViewById<Button>(R.id.auth_skip).setOnClickListener { openModeSelection() }

        lifecycleScope.launch {
            val saved = accountRepository.loadSavedAccount()
            if (saved is AuthResult.Authenticated) {
                openModeSelection()
            }
        }
    }

    private fun signIn() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()
        if (email.isBlank() || password.isBlank()) {
            statusText.setText(R.string.auth_fields_required)
            return
        }
        setLoading(true)
        lifecycleScope.launch {
            runCatching { accountRepository.signIn(email, password) }
                .onSuccess { result ->
                    when (result) {
                        is AuthResult.Authenticated -> openModeSelection()
                        is AuthResult.Error -> statusText.text = result.message
                        else -> statusText.setText(R.string.auth_generic_error)
                    }
                }
                .onFailure { statusText.text = it.message ?: getString(R.string.auth_generic_error) }
            setLoading(false)
        }
    }

    private fun signUp() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()
        if (email.isBlank() || password.length < 6) {
            statusText.setText(R.string.auth_weak_password)
            return
        }
        setLoading(true)
        lifecycleScope.launch {
            runCatching { accountRepository.signUp(email, password) }
                .onSuccess { result ->
                    when (result) {
                        is AuthResult.Authenticated -> openModeSelection()
                        is AuthResult.Error -> statusText.text = result.message
                        else -> statusText.setText(R.string.auth_check_email)
                    }
                }
                .onFailure { statusText.text = it.message ?: getString(R.string.auth_generic_error) }
            setLoading(false)
        }
    }

    private fun forgotPassword() {
        val email = emailInput.text.toString().trim()
        if (email.isBlank()) {
            statusText.setText(R.string.auth_enter_email)
            return
        }
        lifecycleScope.launch {
            runCatching { accountRepository.resetPassword(email) }
                .onSuccess { statusText.setText(R.string.auth_reset_sent) }
                .onFailure { statusText.text = it.message ?: getString(R.string.auth_generic_error) }
        }
    }

    private fun openModeSelection() {
        startActivity(Intent(this, ModeSelectionActivity::class.java))
        finish()
    }

    private fun setLoading(loading: Boolean) {
        emailInput.isEnabled = !loading
        passwordInput.isEnabled = !loading
        findViewById<Button>(R.id.auth_sign_in).isEnabled = !loading
        findViewById<Button>(R.id.auth_sign_up).isEnabled = !loading
    }
}
