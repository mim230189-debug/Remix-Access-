package com.example.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class WebRtcTransportState {
  IDLE,
  INITIALIZING,
  OFFERING,
  CONNECTED,
  STOPPED,
  ERROR,
}

data class RemoteSessionRuntimeSnapshot(
  val activeSessionId: String? = null,
  val screenSharingActive: Boolean = false,
  val cameraStreamingActive: Boolean = false,
  val microphoneStreamingActive: Boolean = false,
  val accessibilityEnabled: Boolean = false,
  val remoteSupportActive: Boolean = false,
  val webRtcState: WebRtcTransportState = WebRtcTransportState.IDLE,
  val iceCandidateCount: Int = 0,
  val dataChannelOpen: Boolean = false,
  val statusMessage: String = "Standby — No active media capture",
  val lastSupportAction: String? = null,
)

object RemoteSessionRuntimeState {
  private val _snapshot = MutableStateFlow(RemoteSessionRuntimeSnapshot())
  val snapshot: StateFlow<RemoteSessionRuntimeSnapshot> = _snapshot.asStateFlow()

  private var onSystemProjectionRevoked: (() -> Unit)? = null

  fun setSystemProjectionRevokedListener(listener: (() -> Unit)?) {
    onSystemProjectionRevoked = listener
  }

  fun notifySystemProjectionStopped() {
    update {
      it.copy(
        screenSharingActive = false,
        webRtcState = if (!it.cameraStreamingActive && !it.microphoneStreamingActive) {
          WebRtcTransportState.STOPPED
        } else {
          it.webRtcState
        },
        statusMessage = "Screen sharing stopped by system or owner",
      )
    }
    onSystemProjectionRevoked?.invoke()
  }

  fun update(transform: (RemoteSessionRuntimeSnapshot) -> RemoteSessionRuntimeSnapshot) {
    _snapshot.update(transform)
  }

  fun resetAll() {
    _snapshot.update { current ->
      RemoteSessionRuntimeSnapshot(
        accessibilityEnabled = current.accessibilityEnabled,
        statusMessage = "Standby — No active media capture",
      )
    }
  }
}
