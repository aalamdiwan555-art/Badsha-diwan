package com.autopilot.driver.ads

import com.autopilot.driver.data.remote.AutopilotAccountRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AdEventLogger(
    private val accountRepository: AutopilotAccountRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {
    fun logEvent(adType: String, eventType: String) {
        scope.launch {
            runCatching { accountRepository.logAdEvent(adType, eventType) }
        }
    }
}