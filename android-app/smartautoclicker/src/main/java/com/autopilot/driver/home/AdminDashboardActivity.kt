package com.autopilot.driver.home

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.autopilot.driver.auth.AutopilotWelcomeActivity
import com.autopilot.driver.data.remote.AutopilotAccountRepository
import com.autopilot.driver.data.remote.AutopilotSessionStore
import com.autopilot.driver.data.remote.AuthResult
import com.autopilot.driver.data.remote.SupabaseRestClient
import com.buzbuz.smartautoclicker.R
import kotlinx.coroutines.launch

/**
 * Administrator-only entry point.
 *
 * Mutating controls will be added behind server-authorized endpoints. This
 * screen deliberately never treats a client-side email check as permission.
 */
class AdminDashboardActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private lateinit var modeCount: TextView
    private lateinit var modeList: LinearLayout
    private lateinit var modeNameInput: EditText
    private lateinit var modeDescriptionInput: EditText

    private val accountRepository by lazy {
        AutopilotAccountRepository(
            client = SupabaseRestClient(),
            sessionStore = AutopilotSessionStore(applicationContext),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)
        statusText = findViewById(R.id.admin_status)
        modeCount = findViewById(R.id.admin_mode_count)
        modeList = findViewById(R.id.admin_mode_list)
        modeNameInput = findViewById(R.id.admin_mode_name)
        modeDescriptionInput = findViewById(R.id.admin_mode_description)

        findViewById<Button>(R.id.admin_refresh).setOnClickListener { loadDashboard() }
        findViewById<Button>(R.id.admin_create_mode).setOnClickListener { createMode() }
        findViewById<Button>(R.id.admin_logout).setOnClickListener {
            accountRepository.signOut()
            startActivity(Intent(this, AutopilotWelcomeActivity::class.java))
            finishAffinity()
        }
        loadDashboard()
    }

    private fun createMode() {
        val name = modeNameInput.text.toString().trim()
        if (name.isBlank()) {
            statusText.setText(R.string.admin_name_required)
            modeNameInput.requestFocus()
            return
        }

        modeNameInput.isEnabled = false
        modeDescriptionInput.isEnabled = false
        lifecycleScope.launch {
            runCatching {
                val current = accountRepository.loadSavedAccount()
                if (current !is AuthResult.Authenticated || current.profile.role != "admin") {
                    error(getString(R.string.admin_access_required))
                }
                if (current.availableModes.size >= MAX_MODES) {
                    error(getString(R.string.admin_mode_limit))
                }
                accountRepository.createScenario(
                    name = name,
                    description = modeDescriptionInput.text.toString().trim().takeIf { it.isNotBlank() },
                )
            }.onSuccess {
                modeNameInput.text.clear()
                modeDescriptionInput.text.clear()
                statusText.setText(R.string.admin_mode_created)
                loadDashboard()
            }.onFailure {
                statusText.text = it.message ?: getString(R.string.auth_generic_error)
            }
            modeNameInput.isEnabled = true
            modeDescriptionInput.isEnabled = true
        }
    }

    private fun loadDashboard() {
        statusText.setText(R.string.admin_loading)
        lifecycleScope.launch {
            runCatching { accountRepository.loadSavedAccount() }
                .onSuccess { result ->
                    if (result !is AuthResult.Authenticated || result.profile.role != "admin") {
                        openUserHome()
                    } else {
                        modeCount.text = getString(
                            R.string.admin_mode_count,
                            result.availableModes.size,
                        )
                        modeList.removeAllViews()
                        result.availableModes.forEach { mode ->
                            modeList.addView(LinearLayout(this@AdminDashboardActivity).apply {
                                orientation = LinearLayout.HORIZONTAL
                                val label = TextView(this@AdminDashboardActivity).apply {
                                    text = getString(R.string.admin_mode_item, mode.name, mode.version)
                                    setTextColor(getColor(android.R.color.white))
                                    textSize = 16f
                                    setPadding(0, 12, 12, 12)
                                }
                                addView(label, LinearLayout.LayoutParams(0, -2, 1f))
                                addView(Button(this@AdminDashboardActivity).apply {
                                    text = getString(R.string.admin_delete)
                                    setOnClickListener { deleteMode(mode.id, mode.name) }
                                })
                            })
                        }
                        statusText.setText(R.string.admin_read_only_status)
                    }
                }
                .onFailure { statusText.text = it.message ?: getString(R.string.auth_generic_error) }
        }
    }

    private fun deleteMode(id: String, name: String) {
        lifecycleScope.launch {
            runCatching { accountRepository.deleteScenario(id) }
                .onSuccess {
                    statusText.text = getString(R.string.admin_mode_deleted, name)
                    loadDashboard()
                }
                .onFailure {
                    statusText.text = it.message ?: getString(R.string.auth_generic_error)
                }
        }
    }

    private fun openUserHome() {
        startActivity(Intent(this, AutopilotHomeActivity::class.java))
        finish()
    }

    private companion object {
        const val MAX_MODES = 15
    }
}