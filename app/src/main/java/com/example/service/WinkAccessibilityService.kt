package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent

class WinkAccessibilityService : AccessibilityService() {

  override fun onServiceConnected() {
    super.onServiceConnected()
    instance = this
    RemoteSessionRuntimeState.update {
      it.copy(accessibilityEnabled = true)
    }
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    // No passive window content scraping; purely owner-triggered global support actions
  }

  override fun onInterrupt() {
    RemoteSessionRuntimeState.update {
      it.copy(
        accessibilityEnabled = false,
        remoteSupportActive = false,
        lastSupportAction = "Accessibility service interrupted",
      )
    }
  }

  override fun onDestroy() {
    if (instance === this) {
      instance = null
    }
    RemoteSessionRuntimeState.update {
      it.copy(
        accessibilityEnabled = false,
        remoteSupportActive = false,
      )
    }
    super.onDestroy()
  }

  fun performSupportedGlobalAction(action: Int, label: String): Boolean {
    val snapshot = RemoteSessionRuntimeState.snapshot.value
    if (!snapshot.remoteSupportActive || snapshot.activeSessionId == null) {
      return false
    }
    val success = performGlobalAction(action)
    RemoteSessionRuntimeState.update {
      it.copy(lastSupportAction = if (success) "Executed $label" else "Failed $label")
    }
    return success
  }

  fun performSupportedTap(xPx: Float, yPx: Float): Boolean {
    val snapshot = RemoteSessionRuntimeState.snapshot.value
    if (!snapshot.remoteSupportActive || snapshot.activeSessionId == null) {
      return false
    }
    val path = Path().apply { moveTo(xPx, yPx) }
    val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
    val gesture = GestureDescription.Builder().addStroke(stroke).build()
    val dispatched = dispatchGesture(gesture, null, null)
    RemoteSessionRuntimeState.update {
      it.copy(lastSupportAction = if (dispatched) "Tap dispatched (${xPx.toInt()}, ${yPx.toInt()})" else "Tap rejected")
    }
    return dispatched
  }

  companion object {
    @Volatile
    var instance: WinkAccessibilityService? = null
      private set

    fun isEnabledInSettings(context: Context): Boolean {
      val expected = "${context.packageName}/${WinkAccessibilityService::class.java.name}"
      val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
      ) ?: return false
      val splitter = TextUtils.SimpleStringSplitter(':')
      splitter.setString(enabledServices)
      while (splitter.hasNext()) {
        if (splitter.next().equals(expected, ignoreCase = true)) {
          return true
        }
      }
      return false
    }

    fun openAccessibilitySettingsIntent(): Intent =
      Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  }
}
