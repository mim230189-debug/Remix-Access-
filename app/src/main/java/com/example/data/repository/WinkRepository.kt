package com.example.data.repository

import android.content.Context
import android.os.Build
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.DeviceConnectionStatus
import com.example.data.model.RegisteredDevice
import com.example.data.model.RemoteSession
import com.example.data.model.RemoteSessionStatus
import com.example.data.model.SessionRequestedMode
import com.example.data.model.SignalSenderRole
import com.example.data.model.ThemePreference
import com.example.data.model.UserAccount
import com.example.data.model.WebRtcSignal
import com.example.data.model.WebRtcSignalType
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class WinkRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

  fun observeUserAccount(userId: String): Flow<UserAccount?> = flow {
    val uid = requireUserId()
    check(uid == userId) { "Authenticated user mismatch." }
    val path = "users/$uid"
    emitAll(
      db.collection("users").document(uid)
        .snapshots()
        .map { snapshot ->
          snapshot.toObject(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
          logFlowError(error, OperationType.GET, path, uid)
          throw error
        }
    )
  }

  fun observeDevices(userId: String): Flow<List<RegisteredDevice>> = flow {
    val uid = requireUserId()
    check(uid == userId) { "Authenticated user mismatch." }
    val path = "users/$uid/devices"
    emitAll(
      db.collection("users").document(uid).collection("devices")
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(RegisteredDevice::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
          logFlowError(error, OperationType.LIST, path, uid)
          throw error
        }
    )
  }

  fun observeSessions(userId: String): Flow<List<RemoteSession>> = flow {
    val uid = requireUserId()
    check(uid == userId) { "Authenticated user mismatch." }
    val path = "users/$uid/sessions"
    emitAll(
      db.collection("users").document(uid).collection("sessions")
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(RemoteSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
          logFlowError(error, OperationType.LIST, path, uid)
          throw error
        }
    )
  }

  fun observeSignals(userId: String, sessionId: String): Flow<List<WebRtcSignal>> = flow {
    val uid = requireUserId()
    check(uid == userId) { "Authenticated user mismatch." }
    require(sessionId.isNotBlank()) { "Session ID must not be blank." }
    val path = "users/$uid/sessions/$sessionId/signals"
    emitAll(
      db.collection("users").document(uid)
        .collection("sessions").document(sessionId)
        .collection("signals")
        .orderBy("createdAt", Query.Direction.ASCENDING)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(WebRtcSignal::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
          logFlowError(error, OperationType.LIST, path, uid)
          throw error
        }
    )
  }

  suspend fun ensureUserAccount(
    userId: String,
    email: String?,
    displayName: String?,
  ): Result<Unit> {
    val path = "users/$userId"
    return try {
      val uid = requireUserId()
      check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
      val docRef = db.collection("users").document(uid)
      val existing = docRef.get().await()
      val cleanEmail = email?.trim()?.take(200)?.takeIf { it.isNotEmpty() }
      val cleanName = displayName?.trim()?.take(100)?.takeIf { it.isNotEmpty() }
      if (!existing.exists()) {
        val payload = mapOf(
          "userId" to uid,
          "email" to cleanEmail,
          "displayName" to cleanName,
          "themePreference" to ThemePreference.SYSTEM.name,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ).filterValues { it != null }
        docRef.set(payload).await()
      } else {
        val updates = mapOf(
          "email" to cleanEmail,
          "displayName" to cleanName,
          "updatedAt" to FieldValue.serverTimestamp(),
        ).filterValues { it != null }
        docRef.update(updates).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  suspend fun updateThemePreference(userId: String, preference: ThemePreference): Result<Unit> {
    val path = "users/$userId"
    return try {
      val uid = requireUserId()
      check(uid == userId) { "Signed-in user changed." }
      db.collection("users").document(uid).update(
        mapOf(
          "themePreference" to preference.name,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun registerOrSyncDevice(
    userId: String,
    deviceId: String,
    deviceName: String,
    connectionStatus: DeviceConnectionStatus,
    remoteSupportEnabled: Boolean,
    fcmToken: String?,
  ): Result<Unit> {
    val path = "users/$userId/devices/$deviceId"
    return try {
      val uid = requireUserId()
      check(uid == userId) { "Signed-in user changed." }
      require(deviceId.isNotBlank() && deviceId.length <= 128) { "Invalid device ID." }
      val cleanName = deviceName.trim().ifBlank { "Android Device" }.take(80)
      val manufacturer = Build.MANUFACTURER.orEmpty().ifBlank { "Android" }.take(60)
      val model = Build.MODEL.orEmpty().ifBlank { "Device" }.take(60)
      val osVersion = "Android ${Build.VERSION.RELEASE ?: "Unknown"} (API ${Build.VERSION.SDK_INT})".take(30)
      val appVersion = "1.0"
      val cleanToken = fcmToken?.trim()?.take(512)?.takeIf { it.isNotEmpty() }

      val docRef = db.collection("users").document(uid).collection("devices").document(deviceId)
      val existing = docRef.get().await()
      if (!existing.exists()) {
        val payload = mapOf(
          "userId" to uid,
          "deviceName" to cleanName,
          "manufacturer" to manufacturer,
          "model" to model,
          "osVersion" to osVersion,
          "appVersion" to appVersion,
          "connectionStatus" to connectionStatus.name,
          "screenShareSupported" to true,
          "cameraSupported" to true,
          "microphoneSupported" to true,
          "remoteSupportEnabled" to remoteSupportEnabled,
          "fcmToken" to cleanToken,
          "lastSeenAt" to FieldValue.serverTimestamp(),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ).filterValues { it != null }
        docRef.set(payload).await()
      } else {
        val updates = mapOf(
          "deviceName" to cleanName,
          "osVersion" to osVersion,
          "appVersion" to appVersion,
          "connectionStatus" to connectionStatus.name,
          "screenShareSupported" to true,
          "cameraSupported" to true,
          "microphoneSupported" to true,
          "remoteSupportEnabled" to remoteSupportEnabled,
          "fcmToken" to cleanToken,
          "lastSeenAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ).filterValues { it != null }
        docRef.update(updates).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  suspend fun removeDevice(userId: String, deviceId: String): Result<Unit> {
    val path = "users/$userId/devices/$deviceId"
    return try {
      val uid = requireUserId()
      check(uid == userId) { "Signed-in user changed." }
      require(deviceId.isNotBlank()) { "Device ID must not be blank." }
      db.collection("users").document(uid).collection("devices").document(deviceId).delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path)
      Result.failure(e)
    }
  }

  suspend fun createControllerSessionRequest(
    userId: String,
    deviceId: String,
    controllerLabel: String,
    controllerOrigin: String,
    requestedMode: SessionRequestedMode,
  ): Result<String> {
    val path = "users/$userId/sessions"
    return try {
      val uid = requireUserId()
      check(uid == userId) { "Signed-in user changed." }
      require(deviceId.isNotBlank()) { "Register device before creating session." }
      val docRef = db.collection("users").document(uid).collection("sessions").document()
      val payload = mapOf(
        "userId" to uid,
        "deviceId" to deviceId,
        "controllerLabel" to controllerLabel.trim().ifBlank { "Web Dashboard" }.take(80),
        "controllerOrigin" to controllerOrigin.trim().ifBlank { "https://dashboard.winkremote.app" }.take(120),
        "requestedMode" to requestedMode.name,
        "status" to RemoteSessionStatus.PENDING.name,
        "screenSharingActive" to false,
        "cameraActive" to false,
        "microphoneActive" to false,
        "remoteSupportActive" to false,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      docRef.set(payload).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  suspend fun acceptSession(
    userId: String,
    sessionId: String,
    screenSharingActive: Boolean,
    cameraActive: Boolean,
    microphoneActive: Boolean,
    remoteSupportActive: Boolean,
  ): Result<Unit> {
    val path = "users/$userId/sessions/$sessionId"
    return try {
      val uid = requireUserId()
      check(uid == userId) { "Signed-in user changed." }
      require(sessionId.isNotBlank()) { "Session ID must not be blank." }
      db.collection("users").document(uid).collection("sessions").document(sessionId).update(
        mapOf(
          "status" to RemoteSessionStatus.ACTIVE.name,
          "screenSharingActive" to screenSharingActive,
          "cameraActive" to cameraActive,
          "microphoneActive" to microphoneActive,
          "remoteSupportActive" to remoteSupportActive,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun rejectSession(
    userId: String,
    sessionId: String,
    reason: String = "Declined by device owner",
  ): Result<Unit> {
    val path = "users/$userId/sessions/$sessionId"
    return try {
      val uid = requireUserId()
      check(uid == userId) { "Signed-in user changed." }
      require(sessionId.isNotBlank()) { "Session ID must not be blank." }
      val updates = mapOf(
        "status" to RemoteSessionStatus.REJECTED.name,
        "terminationReason" to reason.trim().take(120).takeIf { it.isNotEmpty() },
        "updatedAt" to FieldValue.serverTimestamp(),
      ).filterValues { it != null }
      db.collection("users").document(uid).collection("sessions").document(sessionId).update(updates).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun updateSessionMediaToggles(
    userId: String,
    sessionId: String,
    screenSharingActive: Boolean,
    cameraActive: Boolean,
    microphoneActive: Boolean,
    remoteSupportActive: Boolean,
  ): Result<Unit> {
    val path = "users/$userId/sessions/$sessionId"
    return try {
      val uid = requireUserId()
      check(uid == userId) { "Signed-in user changed." }
      require(sessionId.isNotBlank()) { "Session ID must not be blank." }
      db.collection("users").document(uid).collection("sessions").document(sessionId).update(
        mapOf(
          "screenSharingActive" to screenSharingActive,
          "cameraActive" to cameraActive,
          "microphoneActive" to microphoneActive,
          "remoteSupportActive" to remoteSupportActive,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun terminateSession(
    userId: String,
    sessionId: String,
    reason: String = "Terminated by device owner",
  ): Result<Unit> {
    val path = "users/$userId/sessions/$sessionId"
    return try {
      val uid = requireUserId()
      check(uid == userId) { "Signed-in user changed." }
      require(sessionId.isNotBlank()) { "Session ID must not be blank." }
      val updates = mapOf(
        "status" to RemoteSessionStatus.TERMINATED.name,
        "screenSharingActive" to false,
        "cameraActive" to false,
        "microphoneActive" to false,
        "remoteSupportActive" to false,
        "terminationReason" to reason.trim().take(120).takeIf { it.isNotEmpty() },
        "updatedAt" to FieldValue.serverTimestamp(),
      ).filterValues { it != null }
      db.collection("users").document(uid).collection("sessions").document(sessionId).update(updates).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun sendSignal(
    userId: String,
    sessionId: String,
    senderRole: SignalSenderRole,
    signalType: WebRtcSignalType,
    sdpOrCandidate: String,
    sdpMid: String? = null,
    sdpMLineIndex: Int? = null,
  ): Result<Unit> {
    val path = "users/$userId/sessions/$sessionId/signals"
    return try {
      val uid = requireUserId()
      check(uid == userId) { "Signed-in user changed." }
      require(sessionId.isNotBlank()) { "Session ID must not be blank." }
      val cleanPayload = sdpOrCandidate.trim().take(16384)
      require(cleanPayload.isNotEmpty()) { "Signal payload must not be empty." }
      val docRef = db.collection("users").document(uid)
        .collection("sessions").document(sessionId)
        .collection("signals").document()
      val payload = mapOf(
        "userId" to uid,
        "sessionId" to sessionId,
        "senderRole" to senderRole.name,
        "signalType" to signalType.name,
        "sdpOrCandidate" to cleanPayload,
        "sdpMid" to sdpMid?.take(64)?.takeIf { it.isNotEmpty() },
        "sdpMLineIndex" to sdpMLineIndex?.takeIf { it in 0..32 },
        "createdAt" to FieldValue.serverTimestamp(),
      ).filterValues { it != null }
      docRef.set(payload).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  private fun logFlowError(
    error: Throwable,
    operationType: OperationType,
    path: String,
    uid: String,
  ) {
    val firestoreError = generateSequence(error) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val exceptionToLog = firestoreError ?: (error as? Exception)
    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
      handleFirestoreError(exceptionToLog, operationType, path)
    }
  }
}
