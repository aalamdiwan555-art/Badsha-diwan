package com.autopilot.driver.home

import android.content.Intent
import android.os.Bundle
import android.widget.Button
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

class ProfileActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private lateinit var emailValue: TextView
    private lateinit var accessValue: TextView
    private lateinit var rewardValue: TextView
    private lateinit var adFreeValue: TextView

    private val accountRepository by lazy {
        AutopilotAccountRepository(
            client = SupabaseRestClient(),
            sessionStore = AutopilotSessionStore(applicationContext),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        statusText = findViewById(R.id.profile_status)
        emailValue = findViewById(R.id.profile_email_value)
        accessValue = findViewById(R.id.profile_access_value)
        rewardValue = findViewById(R.id.profile_reward_value)
        adFreeValue = findViewById(R.id.profile_ad_free_value)

        findViewById<Button>(R.id.profile_logout).setOnClickListener {
            accountRepository.signOut()
            startActivity(Intent(this, AutopilotWelcomeActivity::class.java))
            finishAffinity()
        }

        loadProfile()
    }

    private fun loadProfile() {
        statusText.setText(R.string.profile_loading)
        lifecycleScope.launch {
            runCatching { accountRepository.loadSavedAccount() }
                .onSuccess { result ->
                    when (result) {
                        is AuthResult.Authenticated -> {
                            emailValue.text = result.profile.email
                            accessValue.text = result.profile.subscriptionStatus
                            rewardValue.text = getString(
                                R.string.profile_ads_today,
                                result.profile.adsWatchedToday,
                            )
                            adFreeValue.setText(
                                if (result.profile.isAdFree) {
                                    R.string.profile_ad_free
                                } else {
                                    R.string.profile_standard_ads
                                },
                            )
                            statusText.text = ""
                        }
                        null -> {
                            startActivity(Intent(this@ProfileActivity, AutopilotWelcomeActivity::class.java))
                            finish()
                        }
                    }
                }
                .onFailure {
                    statusText.text = it.message ?: getString(R.string.auth_generic_error)
                }
        }
    }
}