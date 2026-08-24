package com.autopilot.driver.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.autopilot.driver.data.remote.AuthResult
import com.autopilot.driver.data.remote.AutopilotAccountRepository
import com.autopilot.driver.data.remote.AutopilotSessionStore
import com.autopilot.driver.data.remote.ScenarioMode
import com.autopilot.driver.data.remote.SupabaseRestClient
import com.autopilot.driver.home.AdminDashboardActivity
import com.autopilot.driver.home.AutopilotHomeActivity
import com.buzbuz.smartautoclicker.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ModeSelectionActivity : AppCompatActivity() {

    private val accountRepository by lazy {
        AutopilotAccountRepository(
            client = SupabaseRestClient(),
            sessionStore = AutopilotSessionStore(applicationContext),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mode_selection)

        val container = findViewById<LinearLayout>(R.id.mode_container)
        val statusText = findViewById<TextView>(R.id.mode_status)
        val isAdminText = findViewById<TextView>(R.id.mode_is_admin)
        val adminButton = findViewById<Button>(R.id.mode_admin)

        statusText.setText(R.string.mode_loading)
        lifecycleScope.launch {
            runCatching { accountRepository.loadSavedAccount() }
                .onSuccess { result ->
                    if (result !is AuthResult.Authenticated) {
                        statusText.setText(R.string.mode_auth_required)
                        return@onSuccess
                    }
                    val profile = result.profile
                    val modes = result.availableModes
                    if (profile.role == "admin") {
                        isAdminText.visibility = View.VISIBLE
                        adminButton.visibility = View.VISIBLE
                        adminButton.setOnClickListener {
                            startActivity(Intent(this@ModeSelectionActivity, AdminDashboardActivity::class.java))
                        }
                    }
                    container.removeAllViews()
                    if (modes.isEmpty()) {
                        statusText.setText(R.string.mode_empty)
                    } else {
                        statusText.text = getString(R.string.mode_count, modes.size)
                        modes.forEach { mode -> addModeButton(container, mode) }
                    }
                }
                .onFailure { statusText.text = it.message ?: getString(R.string.auth_generic_error) }
        }
    }

    private fun addModeButton(container: LinearLayout, mode: ScenarioMode) {
        container.addView(Button(this).apply {
            text = getString(R.string.mode_button, mode.name, mode.version)
            setOnClickListener {
                lifecycleScope.launch {
                    accountRepository.selectScenario(mode.id, mode.name)
                    startActivity(Intent(this@ModeSelectionActivity, AutopilotHomeActivity::class.java))
                    finish()
                }
            }
        })
    }
}
