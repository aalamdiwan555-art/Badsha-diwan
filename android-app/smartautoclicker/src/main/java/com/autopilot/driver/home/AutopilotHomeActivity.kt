package com.autopilot.driver.home

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.autopilot.driver.auth.ModeSelectionActivity
import com.autopilot.driver.data.remote.AutopilotAccountRepository
import com.autopilot.driver.data.remote.AutopilotSessionStore
import com.autopilot.driver.data.remote.AuthResult
import com.autopilot.driver.data.remote.SupabaseRestClient
import com.buzbuz.smartautoclicker.R
import com.buzbuz.smartautoclicker.core.common.accessibility.domain.LocalAccessibilityServiceConnection
import com.buzbuz.smartautoclicker.scenarios.ScenarioActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Authenticated user home. It deliberately exposes only the user controls:
 * start, stop, and choose the administrator-provided Mode.
 */
@AndroidEntryPoint
class AutopilotHomeActivity : ComponentActivity() {

    @Inject
    lateinit var serviceConnection: LocalAccessibilityServiceConnection

    private lateinit var modeValue: TextView
    private lateinit var subscriptionValue: TextView
    private lateinit var statusText: TextView

    private val accountRepository by lazy {
        AutopilotAccountRepository(
            client = SupabaseRestClient(),
            sessionStore = AutopilotSessionStore(applicationContext),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_autopilot_home)

        modeValue = findViewById(R.id.home_mode_value)
        subscriptionValue = findViewById(R.id.home_subscription_value)
        statusText = findViewById(R.id.home_status)

        findViewById<Button>(R.id.home_start).setOnClickListener { startClicker() }
        findViewById<Button>(R.id.home_stop).setOnClickListener {
            serviceConnection.getLocalService()?.stopScenario()
            statusText.setText(R.string.home_stop_status)
        }
        findViewById<Button>(R.id.home_switch_mode).setOnClickListener {
            startActivity(Intent(this, ModeSelectionActivity::class.java))
        }

        loadProfile()
    }

    private fun loadProfile() {
        statusText.setText(R.string.home_loading)
        lifecycleScope.launch {
            runCatching { accountRepository.loadSavedAccount() }
                .onSuccess { result ->
                    when (result) {
                        is AuthResult.Authenticated -> {
                            modeValue.text = result.profile.selectedScenarioName
                                ?: getString(R.string.home_no_mode)
                            subscriptionValue.text = getString(
                                R.string.home_subscription_value,
                                result.profile.subscriptionStatus,
                            )
                            statusText.text = if (result.profile.isAdFree) {
                                getString(R.string.home_ad_free)
                            } else {
                                ""
                            }
                        }
                        null -> finish()
                    }
                }
                .onFailure { statusText.text = it.message ?: getString(R.string.auth_generic_error) }
        }
    }

    private fun startClicker() {
        if (modeValue.text == getString(R.string.home_no_mode)) {
            statusText.setText(R.string.home_start_status)
            return
        }
        startActivity(Intent(this, ScenarioActivity::class.java))
    }
}