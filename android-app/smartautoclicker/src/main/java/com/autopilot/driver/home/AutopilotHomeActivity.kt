package com.autopilot.driver.home

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.autopilot.driver.auth.AutopilotWelcomeActivity
import com.autopilot.driver.auth.ModeSelectionActivity
import com.autopilot.driver.data.remote.AuthResult
import com.autopilot.driver.data.remote.AutopilotAccountRepository
import com.autopilot.driver.data.remote.AutopilotSessionStore
import com.autopilot.driver.data.remote.SupabaseRestClient
import com.buzbuz.smartautoclicker.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AutopilotHomeActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private val accountRepository by lazy {
        AutopilotAccountRepository(
            client = SupabaseRestClient(),
            sessionStore = AutopilotSessionStore(applicationContext),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        statusText = findViewById(R.id.home_status)
        findViewById<Button>(R.id.home_change_mode).setOnClickListener {
            startActivity(Intent(this, ModeSelectionActivity::class.java))
        }
        findViewById<Button>(R.id.home_profile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        findViewById<Button>(R.id.home_logout).setOnClickListener {
            accountRepository.signOut()
            startActivity(Intent(this, AutopilotWelcomeActivity::class.java))
            finishAffinity()
        }
        loadHome()
    }

    private fun loadHome() {
        lifecycleScope.launch {
            runCatching { accountRepository.loadSavedAccount() }
                .onSuccess { result ->
                    if (result !is AuthResult.Authenticated) {
                        statusText.setText(R.string.home_auth_required)
                        return@onSuccess
                    }
                    val profile = result.profile
                    val mode = result.selectedMode
                    statusText.text = getString(
                        R.string.home_status,
                        profile.email,
                        profile.subscriptionStatus,
                        mode?.name ?: getString(R.string.home_no_mode),
                    )
                }
                .onFailure { statusText.text = it.message ?: getString(R.string.auth_generic_error) }
        }
    }
}
