package com.autopilot.driver.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.autopilot.driver.data.remote.AutopilotAccountRepository
import com.autopilot.driver.data.remote.AutopilotSessionStore
import com.autopilot.driver.data.remote.RemoteModeInstaller
import com.autopilot.driver.data.remote.ScenarioMode
import com.autopilot.driver.data.remote.SupabaseRestClient
import com.buzbuz.smartautoclicker.R
import com.autopilot.driver.home.AutopilotHomeActivity
import com.buzbuz.smartautoclicker.core.dumb.domain.IDumbRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * First-login mode picker. The list is read-only; only the administrator can
 * create or edit the scenarios that appear here.
 */
@AndroidEntryPoint
class ModeSelectionActivity : ComponentActivity() {

    @Inject
    lateinit var dumbRepository: IDumbRepository

    private lateinit var modeList: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var retryButton: Button
    private var selectedModeId: String? = null

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
        retryButton = findViewById(R.id.mode_retry)
        retryButton.setOnClickListener { loadModes() }
        statusText.setText(R.string.mode_loading)
        loadModes()
    }

    private fun loadModes() {
        retryButton.visibility = View.GONE
        modeList.removeAllViews()
        statusText.visibility = View.VISIBLE
        statusText.setText(R.string.mode_loading)
        lifecycleScope.launch {
            runCatching { accountRepository.loadSavedAccount() }
                .onSuccess { result ->
                    if (result == null || result.availableModes.isEmpty()) {
                        statusText.setText(R.string.mode_empty)
                        retryButton.visibility = View.VISIBLE
                    } else {
                        statusText.visibility = View.GONE
                        selectedModeId = result.profile.selectedScenarioId
                        result.availableModes.forEach(::addMode)
                        result.profile.selectedScenarioName?.let {
                            statusText.visibility = View.VISIBLE
                            statusText.text = getString(R.string.mode_current, it)
                        }
                    }
                }
                .onFailure {
                    statusText.text = it.message ?: getString(R.string.auth_generic_error)
                    retryButton.visibility = View.VISIBLE
                }
        }
    }

    private fun addMode(mode: ScenarioMode) {
        val button = Button(this).apply {
            text = if (mode.id == selectedModeId) {
                getString(R.string.mode_selected, mode.name)
            } else {
                mode.name
            }
            contentDescription = mode.description ?: mode.name
            setOnClickListener { selectMode(this, mode) }
        }
        modeList.addView(button)
    }

    private fun selectMode(button: Button, mode: ScenarioMode) {
        setModeButtonsEnabled(false)
        statusText.visibility = View.VISIBLE
        statusText.setText(R.string.mode_loading)
        lifecycleScope.launch {
            runCatching {
                // Install first so a failed or malformed remote payload never
                // becomes the user's selected server-side mode.
                RemoteModeInstaller(dumbRepository).install(mode)
                accountRepository.selectMode(mode)
            }
                .onSuccess {
                    selectedModeId = mode.id
                    Toast.makeText(
                        this@ModeSelectionActivity,
                        getString(R.string.mode_switched, mode.name),
                        Toast.LENGTH_SHORT,
                    ).show()
                    openClicker()
                }
                .onFailure {
                    setModeButtonsEnabled(true)
                    statusText.visibility = View.VISIBLE
                    statusText.text = it.message ?: getString(R.string.mode_save_error)
                }
        }
    }

    private fun setModeButtonsEnabled(enabled: Boolean) {
        for (index in 0 until modeList.childCount) {
            modeList.getChildAt(index).isEnabled = enabled
        }
    }

    private fun openClicker() {
        startActivity(Intent(this, AutopilotHomeActivity::class.java))
        finish()
    }
}