package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.DevicePreferencesStore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WinkMessagingService : FirebaseMessagingService() {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onNewToken(token: String) {
    super.onNewToken(token)
    scope.launch {
      DevicePreferencesStore(applicationContext).setFcmToken(token)
    }
  }

  override fun onMessageReceived(message: RemoteMessage) {
    super.onMessageReceived(message)
    val controllerLabel = message.data["controllerLabel"] ?: "Personal Web Dashboard"
    val requestedMode = message.data["requestedMode"] ?: "Remote Session"
    showIncomingRequestNotification(controllerLabel, requestedMode)
  }

  private fun showIncomingRequestNotification(controllerLabel: String, requestedMode: String) {
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        ALERT_CHANNEL_ID,
        getString(R.string.notification_channel_alerts_name),
        NotificationManager.IMPORTANCE_HIGH,
      ).apply {
        description = getString(R.string.notification_channel_alerts_desc)
      }
      nm.createNotificationChannel(channel)
    }

    val openIntent = PendingIntent.getActivity(
      this,
      10,
      Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentTitle("Incoming Wink Session Request")
      .setContentText("$controllerLabel requested $requestedMode — tap to review")
      .setAutoCancel(true)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setContentIntent(openIntent)
      .build()

    nm.notify(ALERT_NOTIFICATION_ID, notification)
  }

  companion object {
    private const val ALERT_CHANNEL_ID = "wink_incoming_requests_channel"
    private const val ALERT_NOTIFICATION_ID = 4202
  }
}
