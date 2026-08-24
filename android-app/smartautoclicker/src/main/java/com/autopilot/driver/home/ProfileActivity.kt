package com.autopilot.driver.home

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.autopilot.driver.auth.AutopilotWelcomeActivity
import com.autopilot.driver.data.remote.AuthResult
import com.autopilot.driver.data.remote.AutopilotAccountRepository
import com.autopilot.driver.data.remote.AutopilotSessionStore
import com.autopilot.driver.data.remote.SupabaseRestClient
import com.buzbuz.smartautoclicker.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileActivity : AppCompatActivity() {

    private val accountRepository by lazy {
        AutopilotAccountRepository(
            client = SupabaseRestClient(),
            sessionStore = AutopilotSessionStore(applicationContext),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        val infoText = findViewById<TextView>(R.id.profile_info)
        val logoutButton = findViewById<Button>(R.id.profile_logout)
        lifecycleScope.launch {
            runCatching { accountRepository.loadSavedAccount() }
                .onSuccess { result ->
                    if (result !is AuthResult.Authenticated) {
                        infoText.setText(R.string.profile_auth_required)
                        logoutButton.setOnClickListener {
                            startActivity(Intent(this@ProfileActivity, AutopilotWelcomeActivity::class.java))
                            finish()
                        }
                        return@onSuccess
                    }
                    val profile = result.profile
                    infoText.text = getString(
                        R.string.profile_info,
                        profile.email,
                        profile.role,
                        profile.subscriptionStatus,
                        if (profile.isBanned) getString(R.string.profile_banned) else "",
                    )
                    logoutButton.setOnClickListener {
                        accountRepository.signOut()
                        startActivity(Intent(this@ProfileActivity, AutopilotWelcomeActivity::class.java))
                        finishAffinity()
                    }
                }
                .onFailure { infoText.text = it.message ?: getString(R.string.auth_generic_error) }
        }
    }
}
