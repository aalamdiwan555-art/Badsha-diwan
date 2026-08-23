package com.autopilot.driver.home

import android.content.Intent
import android.provider.Settings
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.autopilot.driver.auth.ModeSelectionActivity
import com.autopilot.driver.ads.BannerAdController
import com.autopilot.driver.ads.InterstitialAdController
import com.autopilot.driver.ads.RewardAdController
import com.autopilot.driver.data.remote.AutopilotAccountRepository
import com.autopilot.driver.data.remote.AutopilotSessionStore
import com.autopilot.driver.data.remote.AuthResult
import com.autopilot.driver.data.remote.RemoteModeInstaller
import com.autopilot.driver.data.remote.ScenarioMode
import com.autopilot.driver.data.remote.SupabaseRestClient
import com.autopilot.driver.data.remote.UserProfile
import com.buzbuz.smartautoclicker.R
import com.buzbuz.smartautoclicker.core.common.accessibility.domain.LocalAccessibilityServiceConnection
import com.buzbuz.smartautoclicker.core.dumb.domain.IDumbRepository
import com.buzbuz.smartautoclicker.scenarios.ScenarioActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Authenticated user home. It deliberately exposes only the user controls:
 * start, stop, and choose the administrator-provided Mode.
 */
@AndroidEntryPoint
class AutopilotHomeActivity : ComponentActivity() {

    @Inject
    lateinit var serviceConnection: LocalAccessibilityServiceConnection

    @Inject
    lateinit var dumbRepository: IDumbRepository

    private lateinit var modeValue: TextView
    private lateinit var subscriptionValue: TextView
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var rewardButton: Button
    private lateinit var rewardProgress: TextView
    private lateinit var bannerContainer: FrameLayout
    private val rewardAdController = RewardAdController()
    private val interstitialAdController = InterstitialAdController()
    private val bannerAdController = BannerAdController()
    private var hasActiveAccess = false
    private var hasLoadedProfile = false
    private var selectedMode: ScenarioMode? = null
    private var rewardAdsForOneDay = 20
    private var profileRequestInFlight = false
    private var activeClickSessionId: String? = null
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshProfile = object : Runnable {
        override fun run() {
            loadProfile(showLoading = false)
            refreshHandler.postDelayed(this, PROFILE_REFRESH_INTERVAL_MS)
        }
    }

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
        rewardProgress = findViewById(R.id.home_reward_progress)
        bannerContainer = findViewById(R.id.home_banner_container)
        rewardButton = findViewById(R.id.home_reward_ad)
        rewardButton.setOnClickListener { showRewardAd() }

        startButton = findViewById(R.id.home_start)
        startButton.setOnClickListener { startClicker() }
        findViewById<Button>(R.id.home_stop).setOnClickListener {
            serviceConnection.getLocalService()?.stopScenario()
            finishClickSession()
            stopService(Intent(this, com.autopilot.driver.service.AutopilotFloatingBannerService::class.java))
            statusText.setText(R.string.home_stop_status)
        }
        findViewById<Button>(R.id.home_switch_mode).setOnClickListener {
            startActivity(Intent(this, ModeSelectionActivity::class.java))
        }
        findViewById<Button>(R.id.home_profile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

    }

    override fun onStart() {
        super.onStart()
        loadProfile()
        refreshHandler.removeCallbacks(refreshProfile)
        refreshHandler.postDelayed(refreshProfile, PROFILE_REFRESH_INTERVAL_MS)
    }

    override fun onStop() {
        refreshHandler.removeCallbacks(refreshProfile)
        super.onStop()
    }

    override fun onDestroy() {
        bannerAdController.destroy(bannerContainer)
        finishClickSession()
        stopService(Intent(this, com.autopilot.driver.service.AutopilotFloatingBannerService::class.java))
        super.onDestroy()
    }

    private fun loadProfile(showLoading: Boolean = true) {
        if (profileRequestInFlight) return
        profileRequestInFlight = true
        hasLoadedProfile = false
        hasActiveAccess = false
        selectedMode = null
        startButton.isEnabled = false
        if (showLoading) {
            rewardProgress.visibility = View.GONE
            rewardButton.visibility = View.GONE
            statusText.setText(R.string.home_loading)
        }
        lifecycleScope.launch {
            runCatching { accountRepository.loadSavedAccount() }
                .onSuccess { result ->
                    when (result) {
                        is AuthResult.Authenticated -> {
                            val settings = runCatching {
                                accountRepository.loadAppSettings()
                            }.getOrNull()
                            rewardAdsForOneDay = settings?.rewardAdsForOneDay ?: 20
                            hasLoadedProfile = true
                            hasActiveAccess = result.profile.hasActiveAccess()
                            selectedMode = result.availableModes.firstOrNull {
                                it.id == result.profile.selectedScenarioId
                            }
                            modeValue.text = selectedMode?.name
                                ?: getString(R.string.home_no_mode)
                            subscriptionValue.text = result.profile.accessLabel()
                            startButton.isEnabled = hasActiveAccess &&
                                result.profile.selectedScenarioId != null
                            statusText.text = if (result.profile.isAdFree) {
                                getString(R.string.home_ad_free)
                            } else {
                                getString(R.string.home_free_access)
                            }
                            val adVisibility = if (result.profile.isAdFree) {
                                View.GONE
                            } else {
                                View.VISIBLE
                            }
                            if (result.profile.isAdFree) {
                                bannerAdController.hide(bannerContainer)
                            } else {
                                bannerAdController.show(
                                    activity = this@AutopilotHomeActivity,
                                    container = bannerContainer,
                                    isAdFree = false,
                                    onEvent = { event ->
                                        lifecycleScope.launch {
                                            runCatching {
                                                accountRepository.logAdEvent("banner", event)
                                            }
                                        }
                                    },
                                )
                            }
                            rewardProgress.visibility = adVisibility
                            rewardButton.visibility = adVisibility
                            rewardProgress.text = getString(
                                R.string.home_reward_progress,
                                result.profile.adsWatchedToday,
                                rewardAdsForOneDay,
                            )
                            rewardButton.isEnabled = !result.profile.isAdFree &&
                                result.profile.adsWatchedToday < rewardAdsForOneDay
                            interstitialAdController.maybeShow(
                                activity = this@AutopilotHomeActivity,
                                isAdFree = result.profile.isAdFree,
                                intervalMinutes = settings?.interstitialIntervalMinutes ?: 2,
                                onShown = {
                                    lifecycleScope.launch {
                                        runCatching {
                                            accountRepository.logAdEvent("interstitial", "shown")
                                        }
                                    }
                                },
                                onError = {
                                    lifecycleScope.launch {
                                        runCatching {
                                            accountRepository.logAdEvent("interstitial", "failed")
                                        }
                                    }
                                },
                            )
                        }
                        null -> finish()
                    }
                }
                .onFailure { statusText.text = it.message ?: getString(R.string.auth_generic_error) }
                .also { profileRequestInFlight = false }
        }
    }

    private fun showRewardAd() {
        if (!hasLoadedProfile) return
        rewardButton.isEnabled = false
        statusText.setText(R.string.home_reward_loading)
        rewardAdController.show(
            activity = this,
            onStarted = {
                lifecycleScope.launch {
                    runCatching { accountRepository.logAdEvent("rewarded", "started") }
                }
            },
            onRewarded = {
                lifecycleScope.launch {
                    runCatching { accountRepository.logAdEvent("rewarded", "playback_completed") }
                }
                statusText.setText(R.string.home_reward_claiming)
                lifecycleScope.launch {
                    runCatching { accountRepository.claimRewardAd() }
                        .onSuccess { claim ->
                            rewardAdsForOneDay = claim.rewardAdsForOneDay
                            statusText.setText(
                                if (claim.unlocked) {
                                    R.string.home_reward_success
                                } else {
                                    getString(
                                        R.string.home_reward_ad_counted,
                                        claim.adsWatchedToday,
                                        claim.rewardAdsForOneDay,
                                    )
                                },
                            )
                            loadProfile()
                        }
                        .onFailure {
                            rewardButton.isEnabled = true
                            statusText.text = it.message ?: getString(R.string.auth_generic_error)
                        }
                }
            },
            onError = {
                lifecycleScope.launch {
                    runCatching { accountRepository.logAdEvent("rewarded", "failed") }
                }
                rewardButton.isEnabled = true
                statusText.text = it
            },
        )
    }

    private fun startClicker() {
        if (!hasLoadedProfile || !hasActiveAccess) {
            statusText.setText(R.string.home_subscription_required)
            return
        }
        if (modeValue.text == getString(R.string.home_no_mode)) {
            statusText.setText(R.string.home_start_status)
            return
        }
        val mode = selectedMode
        if (mode == null) {
            statusText.setText(R.string.home_start_status)
            return
        }
        statusText.setText(R.string.home_loading)
        lifecycleScope.launch {
            runCatching { RemoteModeInstaller(dumbRepository).install(mode) }
                .onSuccess { localScenarioName ->
                    activeClickSessionId = runCatching {
                        accountRepository.startClickSession(mode.id)
                    }.getOrNull()
                    if (Settings.canDrawOverlays(this@AutopilotHomeActivity)) {
                        startService(
                            Intent(
                                this@AutopilotHomeActivity,
                                com.autopilot.driver.service.AutopilotFloatingBannerService::class.java,
                            ),
                        )
                    } else {
                        statusText.setText(R.string.home_overlay_permission_required)
                    }
                    startActivity(
                        Intent(this@AutopilotHomeActivity, ScenarioActivity::class.java)
                            .putExtra(ScenarioActivity.EXTRA_AUTOPILOT_SCENARIO_NAME, localScenarioName),
                    )
                }
                .onFailure {
                    statusText.text = it.message ?: getString(R.string.auth_generic_error)
                }
        }
    }

    private fun finishClickSession() {
        val sessionId = activeClickSessionId ?: return
        activeClickSessionId = null
        lifecycleScope.launch {
            runCatching { accountRepository.finishClickSession(sessionId) }
        }
    }

    private fun UserProfile.hasActiveAccess(): Boolean {
        if (subscriptionStatus == "lifetime") return true
        val expiry = subscriptionExpiresAt ?: return false
        return runCatching { Instant.parse(expiry).isAfter(Instant.now()) }.getOrDefault(false)
    }

    private fun UserProfile.accessLabel(): String {
        if (subscriptionStatus == "lifetime") {
            return getString(R.string.home_lifetime_access)
        }
        if (!hasActiveAccess()) {
            return getString(R.string.home_access_expired)
        }
        return getString(R.string.home_subscription_value, subscriptionStatus)
    }

    private companion object {
        const val PROFILE_REFRESH_INTERVAL_MS = 3_000L
    }
}