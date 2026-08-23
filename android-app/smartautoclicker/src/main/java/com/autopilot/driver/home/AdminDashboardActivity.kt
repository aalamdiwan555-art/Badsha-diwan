package com.autopilot.driver.home

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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
    private lateinit var userList: LinearLayout
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
        userList = findViewById(R.id.admin_user_list)
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
                                    text = getString(R.string.admin_edit)
                                    setOnClickListener { editMode(mode) }
                                })
                                addView(Button(this@AdminDashboardActivity).apply {
                                    text = getString(R.string.admin_delete)
                                    setOnClickListener { deleteMode(mode.id, mode.name) }
                                })
                            })
                        }
                        userList.removeAllViews()
                        accountRepository.loadUsers()
                            .filter { it.id != result.profile.id }
                            .forEach { user -> addUser(user) }
                        statusText.setText(R.string.admin_read_only_status)
                    }
                }
                .onFailure { statusText.text = it.message ?: getString(R.string.auth_generic_error) }
        }
    }

    private fun addUser(user: com.autopilot.driver.data.remote.UserProfile) {
        userList.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val label = TextView(this@AdminDashboardActivity).apply {
                text = getString(
                    R.string.admin_user_item,
                    user.email,
                    user.subscriptionStatus + if (user.isBanned) " · BANNED" else "",
                )
                setTextColor(getColor(android.R.color.white))
                textSize = 15f
                setPadding(0, 12, 0, 4)
            }
            addView(label)
            addView(LinearLayout(this@AdminDashboardActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(Button(this@AdminDashboardActivity).apply {
                    text = getString(R.string.admin_grant)
                    setOnClickListener { chooseGrantDuration(user) }
                })
                addView(Button(this@AdminDashboardActivity).apply {
                    text = getString(if (user.isAdFree) R.string.admin_remove_ad_free else R.string.admin_make_ad_free)
                    setOnClickListener { changeAdFree(user) }
                })
                addView(Button(this@AdminDashboardActivity).apply {
                    text = getString(if (user.isBanned) R.string.admin_unban else R.string.admin_ban)
                    setOnClickListener { changeBanStatus(user) }
                })
            })
        })
    }

    private fun changeBanStatus(user: com.autopilot.driver.data.remote.UserProfile) {
        if (user.isBanned) {
            updateBan(user, false, null)
            return
        }
        val reasonInput = EditText(this).apply { hint = getString(R.string.admin_ban_reason_hint) }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.admin_ban_title, user.email))
            .setView(reasonInput)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.admin_ban) { _, _ ->
                updateBan(user, true, reasonInput.text.toString())
            }
            .show()
    }

    private fun updateBan(
        user: com.autopilot.driver.data.remote.UserProfile,
        banned: Boolean,
        reason: String?,
    ) {
        lifecycleScope.launch {
            runCatching { accountRepository.setBanned(user.id, banned, reason) }
                .onSuccess { statusText.setText(if (banned) R.string.admin_ban_success else R.string.admin_unban_success); loadDashboard() }
                .onFailure { statusText.text = it.message ?: getString(R.string.auth_generic_error) }
        }
    }

    private fun chooseGrantDuration(user: com.autopilot.driver.data.remote.UserProfile) {
        val durations = intArrayOf(1, 2, 3, 7, 15, 30, 90, 365, 99999)
        val labels = arrayOf("1 day", "2 days", "3 days", "7 days", "15 days", "30 days", "90 days", "365 days", "Lifetime")
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.admin_grant_title, user.email))
            .setItems(labels) { _, which ->
                lifecycleScope.launch {
                    runCatching { accountRepository.grantSubscription(user.id, durations[which], null) }
                        .onSuccess { statusText.setText(R.string.admin_grant_success); loadDashboard() }
                        .onFailure { statusText.text = it.message ?: getString(R.string.auth_generic_error) }
                }
            }
            .show()
    }

    private fun changeAdFree(user: com.autopilot.driver.data.remote.UserProfile) {
        lifecycleScope.launch {
            runCatching { accountRepository.setAdFree(user.id, !user.isAdFree) }
                .onSuccess { statusText.setText(R.string.admin_ad_free_success); loadDashboard() }
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

    private fun editMode(mode: com.autopilot.driver.data.remote.ScenarioMode) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 0)
        }
        val nameInput = EditText(this).apply {
            setText(mode.name)
            hint = getString(R.string.admin_mode_name_hint)
        }
        val descriptionInput = EditText(this).apply {
            setText(mode.description.orEmpty())
            hint = getString(R.string.admin_mode_description_hint)
        }
        val jsonInput = EditText(this).apply {
            setText(mode.scenarioData.toString(2))
            hint = getString(R.string.admin_scenario_json_hint)
            minLines = 8
            gravity = android.view.Gravity.TOP
        }
        content.addView(nameInput)
        content.addView(descriptionInput)
        content.addView(jsonInput)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.admin_edit_mode, mode.name))
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.admin_save) { _, _ ->
                saveMode(mode.id, nameInput.text.toString(), descriptionInput.text.toString(), jsonInput.text.toString())
            }
            .show()
    }

    private fun saveMode(id: String, name: String, description: String, scenarioJson: String) {
        if (name.trim().isBlank()) {
            statusText.setText(R.string.admin_name_required)
            return
        }
        val scenarioData = runCatching { org.json.JSONObject(scenarioJson) }.getOrNull()
        if (scenarioData == null || !scenarioData.has("actions") || scenarioData.optJSONArray("actions") == null) {
            statusText.setText(R.string.admin_invalid_scenario)
            return
        }
        lifecycleScope.launch {
            runCatching {
                accountRepository.updateScenario(
                    scenarioId = id,
                    name = name,
                    description = description.takeIf { it.isNotBlank() },
                    scenarioData = scenarioData,
                )
            }.onSuccess {
                statusText.setText(R.string.admin_mode_updated)
                loadDashboard()
            }.onFailure {
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