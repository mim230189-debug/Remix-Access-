package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ThemePreference {
  SYSTEM,
  LIGHT,
  DARK;

  companion object {
    fun fromRaw(raw: String?): ThemePreference =
      entries.firstOrNull { it.name == raw } ?: SYSTEM
  }
}

enum class DeviceConnectionStatus {
  ONLINE,
  IDLE,
  IN_SESSION,
  OFFLINE;

  companion object {
    fun fromRaw(raw: String?): DeviceConnectionStatus =
      entries.firstOrNull { it.name == raw } ?: IDLE
  }
}

enum class SessionRequestedMode {
  SCREEN,
  CAMERA,
  SCREEN_AND_AUDIO,
  FULL_SUPPORT;

  companion object {
    fun fromRaw(raw: String?): SessionRequestedMode =
      entries.firstOrNull { it.name == raw } ?: SCREEN
  }
}

enum class RemoteSessionStatus {
  PENDING,
  ACTIVE,
  REJECTED,
  TERMINATED;

  companion object {
    fun fromRaw(raw: String?): RemoteSessionStatus =
      entries.firstOrNull { it.name == raw } ?: PENDING
  }
}

enum class SignalSenderRole {
  DEVICE,
  CONTROLLER;

  companion object {
    fun fromRaw(raw: String?): SignalSenderRole =
      entries.firstOrNull { it.name == raw } ?: CONTROLLER
  }
}

enum class WebRtcSignalType {
  OFFER,
  ANSWER,
  ICE_CANDIDATE;

  companion object {
    fun fromRaw(raw: String?): WebRtcSignalType =
      entries.firstOrNull { it.name == raw } ?: ICE_CANDIDATE
  }
}

@Keep
@IgnoreExtraProperties
data class UserAccount(
  @DocumentId val id: String = "",
  val userId: String = "",
  val email: String? = null,
  val displayName: String? = null,
  val themePreference: String = ThemePreference.SYSTEM.name,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val parsedThemePreference: ThemePreference
    get() = ThemePreference.fromRaw(themePreference)
}

@Keep
@IgnoreExtraProperties
data class RegisteredDevice(
  @DocumentId val id: String = "",
  val userId: String = "",
  val deviceName: String = "",
  val manufacturer: String = "",
  val model: String = "",
  val osVersion: String = "",
  val appVersion: String = "",
  val connectionStatus: String = DeviceConnectionStatus.ONLINE.name,
  val screenShareSupported: Boolean = true,
  val cameraSupported: Boolean = true,
  val microphoneSupported: Boolean = true,
  val remoteSupportEnabled: Boolean = false,
  val fcmToken: String? = null,
  @ServerTimestamp val lastSeenAt: Timestamp? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val parsedConnectionStatus: DeviceConnectionStatus
    get() = DeviceConnectionStatus.fromRaw(connectionStatus)
}

@Keep
@IgnoreExtraProperties
data class RemoteSession(
  @DocumentId val id: String = "",
  val userId: String = "",
  val deviceId: String = "",
  val controllerLabel: String = "",
  val controllerOrigin: String = "",
  val requestedMode: String = SessionRequestedMode.SCREEN.name,
  val status: String = RemoteSessionStatus.PENDING.name,
  val screenSharingActive: Boolean = false,
  val cameraActive: Boolean = false,
  val microphoneActive: Boolean = false,
  val remoteSupportActive: Boolean = false,
  val terminationReason: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val parsedStatus: RemoteSessionStatus
    get() = RemoteSessionStatus.fromRaw(status)

  val parsedRequestedMode: SessionRequestedMode
    get() = SessionRequestedMode.fromRaw(requestedMode)
}

@Keep
@IgnoreExtraProperties
data class WebRtcSignal(
  @DocumentId val id: String = "",
  val userId: String = "",
  val sessionId: String = "",
  val senderRole: String = SignalSenderRole.DEVICE.name,
  val signalType: String = WebRtcSignalType.ICE_CANDIDATE.name,
  val sdpOrCandidate: String = "",
  val sdpMid: String? = null,
  val sdpMLineIndex: Int? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
) {
  val parsedSenderRole: SignalSenderRole
    get() = SignalSenderRole.fromRaw(senderRole)

  val parsedSignalType: WebRtcSignalType
    get() = WebRtcSignalType.fromRaw(signalType)
}
