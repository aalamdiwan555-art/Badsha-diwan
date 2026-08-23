package com.autopilot.driver.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.autopilot.driver.data.remote.AutopilotAccountRepository
import com.autopilot.driver.data.remote.AutopilotSessionStore
import com.autopilot.driver.data.remote.ScenarioMode
import com.autopilot.driver.data.remote.SupabaseRestClient
import com.buzbuz.smartautoclicker.R
import com.autopilot.driver.home.AutopilotHomeActivity
import kotlinx.coroutines.launch

/**
 * First-login mode picker. The list is read-only; only the administrator can
 * create or edit the scenarios that appear here.
 */
class ModeSelectionActivity : ComponentActivity() {

    private lateinit var modeList: LinearLayout
    private lateinit var statusText: TextView

    private val accountRepository by lazy {
        AutopilotAccountRepository(
            client = SupabaseRestClient(),
            sessionStore = AutopilotSessionStore(applicationContext),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mode_selection)
        modeList = findViewById(R.id.mode_list)
        statusText = findViewById(R.id.mode_status)
        statusText.setText(R.string.mode_loading)
        loadModes()
    }

    private fun loadModes() {
        lifecycleScope.launch {
            runCatching { accountRepository.loadSavedAccount() }
                .onSuccess { result ->
                    if (result == null || result.availableModes.isEmpty()) {
                        statusText.setText(R.string.mode_empty)
                    } else {
                        statusText.visibility = View.GONE
                        result.availableModes.forEach(::addMode)
                    }
                }
                .onFailure { statusText.text = it.message ?: getString(R.string.auth_generic_error) }
        }
    }

    private fun addMode(mode: ScenarioMode) {
        val button = Button(this).apply {
            text = mode.name
            contentDescription = mode.description ?: mode.name
            setOnClickListener { selectMode(this, mode) }
        }
        modeList.addView(button)
    }

    private fun selectMode(button: Button, mode: ScenarioMode) {
        modeList.isEnabled = false
        button.isEnabled = false
        lifecycleScope.launch {
            runCatching { accountRepository.selectMode(mode) }
                .onSuccess { openClicker() }
                .onFailure {
                    modeList.isEnabled = true
                    button.isEnabled = true
                    statusText.visibility = View.VISIBLE
                    statusText.text = it.message ?: getString(R.string.mode_save_error)
                }
        }
    }

    private fun openClicker() {
        startActivity(Intent(this, AutopilotHomeActivity::class.java))
        finish()
    }
}