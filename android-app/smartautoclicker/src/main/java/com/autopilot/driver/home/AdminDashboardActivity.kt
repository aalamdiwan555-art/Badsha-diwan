package com.autopilot.driver.home

import android.content.Intent
import android.os.Bundle
import android.widget.Button
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

        findViewById<Button>(R.id.admin_refresh).setOnClickListener { loadDashboard() }
        findViewById<Button>(R.id.admin_logout).setOnClickListener {
            accountRepository.signOut()
            startActivity(Intent(this, AutopilotWelcomeActivity::class.java))
            finishAffinity()
        }
        loadDashboard()
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
                            modeList.addView(TextView(this@AdminDashboardActivity).apply {
                                text = getString(R.string.admin_mode_item, mode.name, mode.version)
                                setTextColor(getColor(android.R.color.white))
                                textSize = 16f
                                setPadding(0, 12, 0, 12)
                            })
                        }
                        statusText.setText(R.string.admin_read_only_status)
                    }
                }
                .onFailure { statusText.text = it.message ?: getString(R.string.auth_generic_error) }
        }
    }

    private fun openUserHome() {
        startActivity(Intent(this, AutopilotHomeActivity::class.java))
        finish()
    }
}