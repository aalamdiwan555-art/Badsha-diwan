package com.autopilot.driver

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class AutopilotService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}