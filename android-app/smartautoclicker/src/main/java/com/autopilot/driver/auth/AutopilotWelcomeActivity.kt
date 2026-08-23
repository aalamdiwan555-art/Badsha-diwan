package com.autopilot.driver.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.autopilot.driver.data.remote.AutopilotAccountRepository
import com.autopilot.driver.data.remote.AppUpdateChecker
import com.autopilot.driver.data.remote.AutopilotSessionStore
import com.autopilot.driver.data.remote.AuthResult
import com.autopilot.driver.data.remote.SupabaseRestClient
import com.autopilot.driver.home.AdminDashboardActivity
import com.buzbuz.smartautoclicker.R
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.launch

/**
 * Authentication entry point for Autopilot.
 *
 * The existing clicker engine remains behind this screen. Users never need to
 * edit local scenarios to reach it; remote account and mode controls are the
 * source of truth for the new product flow.
 */
class AutopilotWelcomeActivity : ComponentActivity() {

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var actionButton: Button
    private lateinit var switchModeButton: Button
    private lateinit var resetPasswordButton: Button
    private lateinit var statusText: TextView
    private var isSignUp = false

    private val accountRepository by lazy {
        AutopilotAccountRepository(
            client = SupabaseRestClient(),
            sessionStore = AutopilotSessionStore(applicationContext),
        )
    }
    private val updateChecker by lazy { AppUpdateChecker() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_autopilot_welcome)

        emailInput = findViewById(R.id.email_input)
        passwordInput = findViewById(R.id.password_input)
        actionButton = findViewById(R.id.auth_action)
        switchModeButton = findViewById(R.id.auth_mode_switch)
        resetPasswordButton = findViewById(R.id.auth_reset_password)
        statusText = findViewById(R.id.auth_status)

        actionButton.setOnClickListener { authenticate() }
        resetPasswordButton.setOnClickListener { requestPasswordReset() }
        switchModeButton.setOnClickListener {
            isSignUp = !isSignUp
            updateMode()
        }
        updateMode()

        checkForRequiredUpdate()
    }

    private fun checkForRequiredUpdate() {
        setLoading(true)
        lifecycleScope.launch {
            runCatching { updateChecker.check() }
                .onSuccess { result ->
                    setLoading(false)
                    if (result.isUpdateRequired) {
                        showUpdateRequiredDialog(result.updateUrl)
                    } else {
                        loadSavedAccount()
                    }
                }
                .onFailure {
                    setLoading(false)
                    showUpdateVerificationFailedDialog()
                }
        }
    }

    private fun loadSavedAccount() {
        lifecycleScope.launch {
            runCatching { accountRepository.loadSavedAccount() }
                .getOrNull()
                ?.let { openNextScreen(it) }
        }
    }

    private fun showUpdateRequiredDialog(updateUrl: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.update_required_title)
            .setMessage(R.string.update_required_message)
            .setCancelable(false)
            .setPositiveButton(R.string.update_required_action) { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl)))
            }
            .show()
    }

    private fun showUpdateVerificationFailedDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.update_check_failed_title)
            .setMessage(R.string.update_check_failed_message)
            .setCancelable(false)
            .setPositiveButton(R.string.update_check_retry) { _, _ ->
                checkForRequiredUpdate()
            }
            .show()
    }

    private fun authenticate() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()
        if (email.isBlank() || password.isBlank()) {
            showStatus(getString(R.string.auth_missing_fields))
            return
        }
        if (password.length < 6) {
            showStatus(getString(R.string.auth_password_too_short))
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            val result = runCatching {
                if (isSignUp) accountRepository.signUp(email, password)
                else accountRepository.signIn(email, password)
            }
            result
                .onSuccess { authResult ->
                    setLoading(false)
                    when (authResult) {
                        is AuthResult.Authenticated -> openNextScreen(authResult)
                        is AuthResult.EmailConfirmationRequired ->
                            showStatus(getString(R.string.auth_email_confirmation))
                    }
                }
                .onFailure {
                    setLoading(false)
                    showStatus(
                        if (it is com.autopilot.driver.data.remote.BannedAccountException) {
                            getString(R.string.auth_account_disabled)
                        } else {
                            it.message ?: getString(R.string.auth_generic_error)
                        },
                    )
                }
        }
    }

    private fun openNextScreen(result: AuthResult.Authenticated) {
        val destination = if (result.profile.role == "admin") {
            AdminDashboardActivity::class.java
        } else if (result.profile.selectedScenarioId == null) {
            ModeSelectionActivity::class.java
        } else {
            com.autopilot.driver.home.AutopilotHomeActivity::class.java
        }
        startActivity(Intent(this, destination))
        finish()
    }

    private fun requestPasswordReset() {
        val email = emailInput.text.toString().trim()
        if (email.isBlank()) {
            showStatus(getString(R.string.auth_reset_email_required))
            emailInput.requestFocus()
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            runCatching { accountRepository.requestPasswordReset(email) }
                .onSuccess {
                    setLoading(false)
                    showStatus(getString(R.string.auth_reset_sent))
                }
                .onFailure {
                    setLoading(false)
                    showStatus(it.message ?: getString(R.string.auth_generic_error))
                }
        }
    }

    private fun updateMode() {
        actionButton.setText(if (isSignUp) R.string.create_account else R.string.sign_in)
        switchModeButton.setText(
            if (isSignUp) R.string.switch_to_sign_in else R.string.switch_to_sign_up,
        )
    }

    private fun setLoading(loading: Boolean) {
        emailInput.isEnabled = !loading
        passwordInput.isEnabled = !loading
        switchModeButton.isEnabled = !loading
        resetPasswordButton.isEnabled = !loading
        actionButton.isEnabled = !loading
        actionButton.text = if (loading) getString(R.string.auth_loading)
        else if (isSignUp) getString(R.string.create_account) else getString(R.string.sign_in)
        if (loading) statusText.visibility = View.INVISIBLE
    }

    private fun showStatus(message: String) {
        statusText.text = message
        statusText.visibility = View.VISIBLE
    }
}