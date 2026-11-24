package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.WebRtcSignal
import com.example.data.model.WebRtcSignalType
import com.example.data.model.SignalSenderRole
import com.example.data.repository.WinkRepository
import com.example.webrtc.WebRtcSessionManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class RemoteSessionService : Service() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var signalsJob: Job? = null
  private var repository: WinkRepository? = null
  private var webRtcManager: WebRtcSessionManager? = null
  private var activeUserId: String? = null
  private var activeSessionId: String? = null

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    repository = WinkRepository(applicationContext)
    webRtcManager = WebRtcSessionManager(
      context = applicationContext,
      onLocalSignal = { type, payload, sdpMid, sdpMLineIndex ->
        val uid = activeUserId ?: return@WebRtcSessionManager
        val sid = activeSessionId ?: return@WebRtcSessionManager
        serviceScope.launch(Dispatchers.IO) {
          repository?.sendSignal(
            userId = uid,
            sessionId = sid,
            senderRole = SignalSenderRole.DEVICE,
            signalType = type,
            sdpOrCandidate = payload,
            sdpMid = sdpMid,
            sdpMLineIndex = sdpMLineIndex,
          )
        }
      },
      onProjectionStoppedBySystem = {
        serviceScope.launch {
          RemoteSessionRuntimeState.notifySystemProjectionStopped()
          val uid = activeUserId
          val sid = activeSessionId
          val snap = RemoteSessionRuntimeState.snapshot.value
          if (uid != null && sid != null) {
            repository?.updateSessionMediaToggles(
              userId = uid,
              sessionId = sid,
              screenSharingActive = false,
              cameraActive = snap.cameraStreamingActive,
              microphoneActive = snap.microphoneStreamingActive,
              remoteSupportActive = snap.remoteSupportActive,
            )
          }
          if (!snap.cameraStreamingActive && !snap.microphoneStreamingActive) {
            stopEverythingAndSelf()
          } else {
            refreshForegroundNotification()
          }
        }
      },
    )
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP_ALL -> {
        stopEverythingAndSelf()
        return START_NOT_STICKY
      }
      ACTION_STOP_SCREEN_SHARE -> {
        webRtcManager?.stop()
        RemoteSessionRuntimeState.update {
          it.copy(
            screenSharingActive = false,
            webRtcState = WebRtcTransportState.STOPPED,
            statusMessage = "Screen sharing stopped by owner",
          )
        }
        RemoteSessionRuntimeState.notifySystemProjectionStopped()
        val snap = RemoteSessionRuntimeState.snapshot.value
        if (!snap.cameraStreamingActive && !snap.microphoneStreamingActive) {
          stopEverythingAndSelf()
        } else {
          refreshForegroundNotification()
        }
        return START_NOT_STICKY
      }
      ACTION_START_SESSION_MEDIA -> {
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return START_NOT_STICKY
        val userId = Firebase.auth.currentUser?.uid ?: return START_NOT_STICKY
        activeUserId = userId
        activeSessionId = sessionId

        @Suppress("DEPRECATION")
        val projectionData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
        } else {
          intent.getParcelableExtra(EXTRA_PROJECTION_DATA)
        }
        val enableCamera = intent.getBooleanExtra(EXTRA_CAMERA_ACTIVE, false) &&
          ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val enableMic = intent.getBooleanExtra(EXTRA_MICROPHONE_ACTIVE, false) &&
          ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val enableScreen = projectionData != null

        promoteForeground(enableScreen, enableCamera, enableMic)

        RemoteSessionRuntimeState.update {
          it.copy(
            activeSessionId = sessionId,
            screenSharingActive = enableScreen,
            cameraStreamingActive = enableCamera,
            microphoneStreamingActive = enableMic,
            statusMessage = buildStatusSummary(enableScreen, enableCamera, enableMic),
          )
        }

        if (projectionData != null) {
          webRtcManager?.startScreenShareAndPeer(projectionData, enableMic)
          observeRemoteSignals(userId, sessionId)
        } else if (enableMic) {
          webRtcManager?.startAudioOnlyPeer()
          observeRemoteSignals(userId, sessionId)
        }
      }
      ACTION_UPDATE_MEDIA_INDICATORS -> {
        val snap = RemoteSessionRuntimeState.snapshot.value
        if (!snap.screenSharingActive && !snap.cameraStreamingActive && !snap.microphoneStreamingActive) {
          stopEverythingAndSelf()
        } else {
          promoteForeground(
            snap.screenSharingActive,
            snap.cameraStreamingActive,
            snap.microphoneStreamingActive,
          )
        }
      }
    }
    return START_NOT_STICKY
  }

  private fun observeRemoteSignals(userId: String, sessionId: String) {
    signalsJob?.cancel()
    signalsJob = serviceScope.launch {
      repository?.observeSignals(userId, sessionId)
        ?.catch { /* Handled cleanly by ViewModel / FirestoreErrorHandler */ }
        ?.collect { signals: List<WebRtcSignal> ->
          webRtcManager?.applyRemoteSignals(signals)
        }
    }
  }

  private fun promoteForeground(
    screenActive: Boolean,
    cameraActive: Boolean,
    micActive: Boolean,
  ) {
    val notification = buildNotification(screenActive, cameraActive, micActive)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      var types = 0
      if (screenActive) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
      if (cameraActive) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
      if (micActive) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
      if (types == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
      }
      startForeground(NOTIFICATION_ID, notification, types)
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun refreshForegroundNotification() {
    val snap = RemoteSessionRuntimeState.snapshot.value
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.notify(
      NOTIFICATION_ID,
      buildNotification(snap.screenSharingActive, snap.cameraStreamingActive, snap.microphoneStreamingActive),
    )
  }

  private fun buildNotification(
    screenActive: Boolean,
    cameraActive: Boolean,
    micActive: Boolean,
  ): Notification {
    val openIntent = PendingIntent.getActivity(
      this,
      0,
      Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val stopIntent = PendingIntent.getService(
      this,
      1,
      Intent(this, RemoteSessionService::class.java).apply { action = ACTION_STOP_ALL },
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val summary = buildStatusSummary(screenActive, cameraActive, micActive)

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_menu_view)
      .setContentTitle(getString(R.string.app_name))
      .setContentText(summary)
      .setOngoing(true)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setContentIntent(openIntent)
      .addAction(
        android.R.drawable.ic_media_pause,
        getString(R.string.notification_stop_sharing),
        stopIntent,
      )
      .build()
  }

  private fun buildStatusSummary(screen: Boolean, camera: Boolean, mic: Boolean): String {
    val active = buildList {
      if (screen) add("Screen")
      if (camera) add("Camera")
      if (mic) add("Mic")
    }
    return if (active.isEmpty()) {
      "Remote session active"
    } else {
      "Active streams: ${active.joinToString(" • ")}"
    }
  }

  private fun stopEverythingAndSelf() {
    signalsJob?.cancel()
    webRtcManager?.stop()
    RemoteSessionRuntimeState.update {
      it.copy(
        screenSharingActive = false,
        cameraStreamingActive = false,
        microphoneStreamingActive = false,
        webRtcState = WebRtcTransportState.STOPPED,
        statusMessage = "Standby — All streams stopped",
      )
    }
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val channel = NotificationChannel(
        CHANNEL_ID,
        getString(R.string.notification_channel_session_name),
        NotificationManager.IMPORTANCE_HIGH,
      ).apply {
        description = getString(R.string.notification_channel_session_desc)
      }
      nm.createNotificationChannel(channel)
    }
  }

  override fun onDestroy() {
    signalsJob?.cancel()
    webRtcManager?.stop()
    serviceScope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  companion object {
    const val CHANNEL_ID = "wink_active_session_channel"
    const val NOTIFICATION_ID = 4101
    const val ACTION_START_SESSION_MEDIA = "com.example.action.START_SESSION_MEDIA"
    const val ACTION_STOP_SCREEN_SHARE = "com.example.action.STOP_SCREEN_SHARE"
    const val ACTION_STOP_ALL = "com.example.action.STOP_ALL"
    const val ACTION_UPDATE_MEDIA_INDICATORS = "com.example.action.UPDATE_MEDIA_INDICATORS"
    const val EXTRA_SESSION_ID = "extra_session_id"
    const val EXTRA_PROJECTION_DATA = "extra_projection_data"
    const val EXTRA_CAMERA_ACTIVE = "extra_camera_active"
    const val EXTRA_MICROPHONE_ACTIVE = "extra_microphone_active"

    fun startMediaCapture(
      context: Context,
      sessionId: String,
      projectionData: Intent?,
      cameraActive: Boolean,
      microphoneActive: Boolean,
    ) {
      val intent = Intent(context, RemoteSessionService::class.java).apply {
        action = ACTION_START_SESSION_MEDIA
        putExtra(EXTRA_SESSION_ID, sessionId)
        putExtra(EXTRA_PROJECTION_DATA, projectionData)
        putExtra(EXTRA_CAMERA_ACTIVE, cameraActive)
        putExtra(EXTRA_MICROPHONE_ACTIVE, microphoneActive)
      }
      ContextCompat.startForegroundService(context, intent)
    }

    fun stopScreenShare(context: Context) {
      val intent = Intent(context, RemoteSessionService::class.java).apply {
        action = ACTION_STOP_SCREEN_SHARE
      }
      context.startService(intent)
    }

    fun stopAll(context: Context) {
      val intent = Intent(context, RemoteSessionService::class.java).apply {
        action = ACTION_STOP_ALL
      }
      context.startService(intent)
    }
  }
}
