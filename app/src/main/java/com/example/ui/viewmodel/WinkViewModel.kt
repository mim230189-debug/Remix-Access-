package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.DevicePreferencesStore
import com.example.data.model.DeviceConnectionStatus
import com.example.data.model.RegisteredDevice
import com.example.data.model.RemoteSession
import com.example.data.model.RemoteSessionStatus
import com.example.data.model.SessionRequestedMode
import com.example.data.model.ThemePreference
import com.example.data.model.UserAccount
import com.example.data.model.WebRtcSignal
import com.example.data.repository.WinkRepository
import com.example.service.RemoteSessionRuntimeSnapshot
import com.example.service.RemoteSessionRuntimeState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

class WinkViewModel(
  private val repository: WinkRepository,
  private val preferencesStore: DevicePreferencesStore,
  val currentUserId: String,
  val ownerEmail: String?,
  val ownerDisplayName: String?,
) : ViewModel() {

  private val _deviceId = MutableStateFlow("")
  val deviceId: StateFlow<String> = _deviceId.asStateFlow()

  val deviceName: StateFlow<String> = preferencesStore.deviceNameFlow
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), "Android Device")

  val localThemePreference: StateFlow<ThemePreference> = preferencesStore.themePreferenceFlow
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ThemePreference.SYSTEM)

  val runtimeState: StateFlow<RemoteSessionRuntimeSnapshot> = RemoteSessionRuntimeState.snapshot

  private val _snackbarMessage = MutableStateFlow<String?>(null)
  val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

  private val _selectedCameraBack = MutableStateFlow(false)
  val selectedCameraBack: StateFlow<Boolean> = _selectedCameraBack.asStateFlow()

  val userAccountState: StateFlow<UiState<UserAccount?>> = repository.observeUserAccount(currentUserId)
    .map<UserAccount?, UiState<UserAccount?>> { UiState.Success(it) }
    .catch { error ->
      val firestoreError = unwrapFirestoreException(error)
      if (firestoreError == null && error is CancellationException) throw error
      val root = firestoreError ?: error
      Log.w(TAG, "Error observing user account", root)
      emit(UiState.Error(root.message ?: "Failed to load account"))
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

  val devicesState: StateFlow<UiState<List<RegisteredDevice>>> = repository.observeDevices(currentUserId)
    .map<List<RegisteredDevice>, UiState<List<RegisteredDevice>>> { UiState.Success(it) }
    .catch { error ->
      val firestoreError = unwrapFirestoreException(error)
      if (firestoreError == null && error is CancellationException) throw error
      val root = firestoreError ?: error
      Log.w(TAG, "Error observing devices", root)
      emit(UiState.Error(root.message ?: "Failed to load devices"))
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

  val sessionsState: StateFlow<UiState<List<RemoteSession>>> = repository.observeSessions(currentUserId)
    .map<List<RemoteSession>, UiState<List<RemoteSession>>> { UiState.Success(it) }
    .catch { error ->
      val firestoreError = unwrapFirestoreException(error)
      if (firestoreError == null && error is CancellationException) throw error
      val root = firestoreError ?: error
      Log.w(TAG, "Error observing sessions", root)
      emit(UiState.Error(root.message ?: "Failed to load sessions"))
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

  @OptIn(ExperimentalCoroutinesApi::class)
  val activeSessionSignals: StateFlow<List<WebRtcSignal>> = sessionsState
    .flatMapLatest { state ->
      val active = (state as? UiState.Success)?.data
        ?.firstOrNull { it.parsedStatus == RemoteSessionStatus.ACTIVE }
      if (active == null || active.id.isBlank()) {
        flowOf(emptyList())
      } else {
        repository.observeSignals(currentUserId, active.id)
          .catch { error ->
            val firestoreError = unwrapFirestoreException(error)
            if (firestoreError == null && error is CancellationException) throw error
            emit(emptyList())
          }
      }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

  init {
    bootstrapAccountAndDevice()
    RemoteSessionRuntimeState.setSystemProjectionRevokedListener {
      viewModelScope.launch {
        val activeSession = currentActiveSession() ?: return@launch
        val snap = RemoteSessionRuntimeState.snapshot.value
        repository.updateSessionMediaToggles(
          userId = currentUserId,
          sessionId = activeSession.id,
          screenSharingActive = false,
          cameraActive = snap.cameraStreamingActive,
          microphoneActive = snap.microphoneStreamingActive,
          remoteSupportActive = snap.remoteSupportActive,
        )
        postMessage("Screen sharing stopped")
      }
    }
  }

  private fun bootstrapAccountAndDevice() {
    viewModelScope.launch {
      val resolvedId = preferencesStore.getOrCreateDeviceId()
      _deviceId.value = resolvedId
      val accountResult = repository.ensureUserAccount(currentUserId, ownerEmail, ownerDisplayName)
      if (accountResult.isFailure) {
        postMessage(accountResult.exceptionOrNull()?.localizedMessage ?: "Account sync failed")
      }
      registerOrSyncDevice(
        connectionStatus = DeviceConnectionStatus.ONLINE,
        remoteSupportEnabled = RemoteSessionRuntimeState.snapshot.value.accessibilityEnabled,
        silent = true,
      )
    }
  }

  fun registerOrSyncDevice(
    connectionStatus: DeviceConnectionStatus = DeviceConnectionStatus.ONLINE,
    remoteSupportEnabled: Boolean = RemoteSessionRuntimeState.snapshot.value.accessibilityEnabled,
    silent: Boolean = false,
  ) {
    viewModelScope.launch {
      val id = _deviceId.value.ifBlank {
        preferencesStore.getOrCreateDeviceId().also { _deviceId.value = it }
      }
      val name = deviceName.value
      val fcmToken = preferencesStore.getFcmToken()
      val result = repository.registerOrSyncDevice(
        userId = currentUserId,
        deviceId = id,
        deviceName = name,
        connectionStatus = connectionStatus,
        remoteSupportEnabled = remoteSupportEnabled,
        fcmToken = fcmToken,
      )
      if (!silent) {
        if (result.isSuccess) {
          postMessage("Device synced with web dashboard")
        } else {
          postMessage(result.exceptionOrNull()?.localizedMessage ?: "Failed to sync device")
        }
      }
    }
  }

  fun updateDeviceName(newName: String) {
    viewModelScope.launch {
      val clean = newName.trim().take(80)
      if (clean.isEmpty()) {
        postMessage("Device name cannot be empty")
        return@launch
      }
      preferencesStore.setDeviceName(clean)
      registerOrSyncDevice(silent = false)
    }
  }

  fun removeCurrentDevice() {
    viewModelScope.launch {
      val id = _deviceId.value
      if (id.isBlank()) return@launch
      currentActiveSession()?.let { active ->
        repository.terminateSession(currentUserId, active.id, "Device removed from account")
      }
      RemoteSessionRuntimeState.resetAll()
      val result = repository.removeDevice(currentUserId, id)
      if (result.isSuccess) {
        val nextId = preferencesStore.regenerateDeviceId()
        _deviceId.value = nextId
        postMessage("Device removed from account. New local ID generated.")
      } else {
        postMessage(result.exceptionOrNull()?.localizedMessage ?: "Failed to remove device")
      }
    }
  }

  fun setThemePreference(preference: ThemePreference) {
    viewModelScope.launch {
      preferencesStore.setThemePreference(preference)
      repository.updateThemePreference(currentUserId, preference)
    }
  }

  fun createIncomingControllerRequest(mode: SessionRequestedMode = SessionRequestedMode.FULL_SUPPORT) {
    viewModelScope.launch {
      val id = _deviceId.value.ifBlank {
        preferencesStore.getOrCreateDeviceId().also { _deviceId.value = it }
      }
      val ensureDevice = repository.registerOrSyncDevice(
        userId = currentUserId,
        deviceId = id,
        deviceName = deviceName.value,
        connectionStatus = DeviceConnectionStatus.ONLINE,
        remoteSupportEnabled = RemoteSessionRuntimeState.snapshot.value.accessibilityEnabled,
        fcmToken = preferencesStore.getFcmToken(),
      )
      if (ensureDevice.isFailure) {
        postMessage(ensureDevice.exceptionOrNull()?.localizedMessage ?: "Register device first")
        return@launch
      }
      val result = repository.createControllerSessionRequest(
        userId = currentUserId,
        deviceId = id,
        controllerLabel = "Personal Web Dashboard",
        controllerOrigin = "https://dashboard.winkremote.app",
        requestedMode = mode,
      )
      if (result.isSuccess) {
        postMessage("Incoming controller request received")
      } else {
        postMessage(result.exceptionOrNull()?.localizedMessage ?: "Failed to create request")
      }
    }
  }

  fun acceptSession(
    session: RemoteSession,
    screenSharingActive: Boolean = false,
    cameraActive: Boolean = false,
    microphoneActive: Boolean = false,
    remoteSupportActive: Boolean = false,
  ) {
    viewModelScope.launch {
      val result = repository.acceptSession(
        userId = currentUserId,
        sessionId = session.id,
        screenSharingActive = screenSharingActive,
        cameraActive = cameraActive,
        microphoneActive = microphoneActive,
        remoteSupportActive = remoteSupportActive,
      )
      if (result.isSuccess) {
        RemoteSessionRuntimeState.update {
          it.copy(
            activeSessionId = session.id,
            screenSharingActive = screenSharingActive,
            cameraStreamingActive = cameraActive,
            microphoneStreamingActive = microphoneActive,
            remoteSupportActive = remoteSupportActive,
            statusMessage = "Session approved by owner",
          )
        }
        registerOrSyncDevice(connectionStatus = DeviceConnectionStatus.IN_SESSION, silent = true)
        postMessage("Session approved — start media streams explicitly below")
      } else {
        postMessage(result.exceptionOrNull()?.localizedMessage ?: "Failed to accept session")
      }
    }
  }

  fun rejectSession(session: RemoteSession) {
    viewModelScope.launch {
      val result = repository.rejectSession(currentUserId, session.id)
      if (result.isSuccess) {
        postMessage("Controller request rejected")
      } else {
        postMessage(result.exceptionOrNull()?.localizedMessage ?: "Failed to reject request")
      }
    }
  }

  fun ensureOrAcceptActiveSession(onReady: (String) -> Unit) {
    viewModelScope.launch {
      val existingActive = currentActiveSession()
      if (existingActive != null) {
        onReady(existingActive.id)
        return@launch
      }
      val pending = currentPendingSessions().firstOrNull()
      if (pending != null) {
        val acceptRes = repository.acceptSession(
          userId = currentUserId,
          sessionId = pending.id,
          screenSharingActive = false,
          cameraActive = false,
          microphoneActive = false,
          remoteSupportActive = false,
        )
        if (acceptRes.isSuccess) {
          RemoteSessionRuntimeState.update { it.copy(activeSessionId = pending.id) }
          registerOrSyncDevice(connectionStatus = DeviceConnectionStatus.IN_SESSION, silent = true)
          onReady(pending.id)
        } else {
          postMessage(acceptRes.exceptionOrNull()?.localizedMessage ?: "Could not activate session")
        }
        return@launch
      }

      val id = _deviceId.value.ifBlank {
        preferencesStore.getOrCreateDeviceId().also { _deviceId.value = it }
      }
      repository.registerOrSyncDevice(
        userId = currentUserId,
        deviceId = id,
        deviceName = deviceName.value,
        connectionStatus = DeviceConnectionStatus.IN_SESSION,
        remoteSupportEnabled = RemoteSessionRuntimeState.snapshot.value.accessibilityEnabled,
        fcmToken = preferencesStore.getFcmToken(),
      )
      val created = repository.createControllerSessionRequest(
        userId = currentUserId,
        deviceId = id,
        controllerLabel = "Owner Console Session",
        controllerOrigin = "https://dashboard.winkremote.app",
        requestedMode = SessionRequestedMode.FULL_SUPPORT,
      )
      val newSessionId = created.getOrNull()
      if (newSessionId != null) {
        repository.acceptSession(
          userId = currentUserId,
          sessionId = newSessionId,
          screenSharingActive = false,
          cameraActive = false,
          microphoneActive = false,
          remoteSupportActive = false,
        )
        RemoteSessionRuntimeState.update { it.copy(activeSessionId = newSessionId) }
        onReady(newSessionId)
      } else {
        postMessage(created.exceptionOrNull()?.localizedMessage ?: "Failed to start session")
      }
    }
  }

  fun syncActiveMediaFlags(
    sessionId: String,
    screenSharingActive: Boolean,
    cameraActive: Boolean,
    microphoneActive: Boolean,
    remoteSupportActive: Boolean,
  ) {
    viewModelScope.launch {
      RemoteSessionRuntimeState.update {
        it.copy(
          activeSessionId = sessionId,
          screenSharingActive = screenSharingActive,
          cameraStreamingActive = cameraActive,
          microphoneStreamingActive = microphoneActive,
          remoteSupportActive = remoteSupportActive,
        )
      }
      repository.updateSessionMediaToggles(
        userId = currentUserId,
        sessionId = sessionId,
        screenSharingActive = screenSharingActive,
        cameraActive = cameraActive,
        microphoneActive = microphoneActive,
        remoteSupportActive = remoteSupportActive,
      )
    }
  }

  fun terminateSession(session: RemoteSession, reason: String = "Terminated by device owner") {
    viewModelScope.launch {
      RemoteSessionRuntimeState.resetAll()
      val result = repository.terminateSession(currentUserId, session.id, reason)
      registerOrSyncDevice(connectionStatus = DeviceConnectionStatus.ONLINE, silent = true)
      if (result.isSuccess) {
        postMessage("Session terminated")
      } else {
        postMessage(result.exceptionOrNull()?.localizedMessage ?: "Failed to terminate session")
      }
    }
  }

  fun toggleCameraLens() {
    _selectedCameraBack.value = !_selectedCameraBack.value
  }

  fun reconcilePermissions(
    cameraGranted: Boolean,
    microphoneGranted: Boolean,
    accessibilityEnabled: Boolean,
  ) {
    val snap = RemoteSessionRuntimeState.snapshot.value
    RemoteSessionRuntimeState.update {
      it.copy(accessibilityEnabled = accessibilityEnabled)
    }

    var changed = false
    var nextCamera = snap.cameraStreamingActive
    var nextMic = snap.microphoneStreamingActive
    var nextSupport = snap.remoteSupportActive
    var revokeReason: String? = null

    if (snap.cameraStreamingActive && !cameraGranted) {
      nextCamera = false
      changed = true
      revokeReason = "Camera permission revoked by owner"
    }
    if (snap.microphoneStreamingActive && !microphoneGranted) {
      nextMic = false
      changed = true
      revokeReason = "Microphone permission revoked by owner"
    }
    if (snap.remoteSupportActive && !accessibilityEnabled) {
      nextSupport = false
      changed = true
      revokeReason = "Accessibility permission disabled by owner"
    }

    if (changed) {
      viewModelScope.launch {
        val active = currentActiveSession()
        if (active != null && !snap.screenSharingActive && !nextCamera && !nextMic && !nextSupport) {
          RemoteSessionRuntimeState.resetAll()
          repository.terminateSession(currentUserId, active.id, revokeReason ?: "Permission revoked")
          registerOrSyncDevice(connectionStatus = DeviceConnectionStatus.ONLINE, silent = true)
          postMessage("Session disconnected: ${revokeReason ?: "permission revoked"}")
        } else if (active != null) {
          syncActiveMediaFlags(
            sessionId = active.id,
            screenSharingActive = snap.screenSharingActive,
            cameraActive = nextCamera,
            microphoneActive = nextMic,
            remoteSupportActive = nextSupport,
          )
          postMessage(revokeReason ?: "Stream stopped after permission change")
        }
      }
    }
  }

  fun postMessage(message: String) {
    _snackbarMessage.value = message
  }

  fun clearMessage() {
    _snackbarMessage.value = null
  }

  private fun currentActiveSession(): RemoteSession? =
    (sessionsState.value as? UiState.Success)?.data
      ?.firstOrNull { it.parsedStatus == RemoteSessionStatus.ACTIVE }

  private fun currentPendingSessions(): List<RemoteSession> =
    (sessionsState.value as? UiState.Success)?.data
      ?.filter { it.parsedStatus == RemoteSessionStatus.PENDING }
      ?: emptyList()

  private fun unwrapFirestoreException(error: Throwable): FirebaseFirestoreException? =
    generateSequence(error) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()

  override fun onCleared() {
    RemoteSessionRuntimeState.setSystemProjectionRevokedListener(null)
    super.onCleared()
  }

  private companion object {
    const val TAG = "WinkViewModel"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}
