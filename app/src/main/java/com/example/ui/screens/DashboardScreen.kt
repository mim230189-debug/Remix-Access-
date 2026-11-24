package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.automirrored.filled.StopScreenShare
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.DeviceConnectionStatus
import com.example.data.model.RegisteredDevice
import com.example.data.model.RemoteSession
import com.example.data.model.RemoteSessionStatus
import com.example.service.RemoteSessionRuntimeSnapshot
import com.example.ui.components.CameraXPreviewCard
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.StatusDangerRed
import com.example.ui.theme.StatusOnlineGreen
import com.example.ui.viewmodel.UiState

@Composable
fun DashboardScreen(
  deviceId: String,
  deviceName: String,
  ownerEmail: String?,
  devicesState: UiState<List<RegisteredDevice>>,
  sessionsState: UiState<List<RemoteSession>>,
  runtimeState: RemoteSessionRuntimeSnapshot,
  useBackCamera: Boolean,
  onSyncDevice: () -> Unit,
  onCopyDeviceId: () -> Unit,
  onSimulateWebRequest: () -> Unit,
  onAcceptSession: (RemoteSession) -> Unit,
  onRejectSession: (RemoteSession) -> Unit,
  onTerminateSession: (RemoteSession) -> Unit,
  onStartScreenSharing: () -> Unit,
  onStopScreenSharing: () -> Unit,
  onStartCamera: () -> Unit,
  onStopCamera: () -> Unit,
  onSwitchCamera: () -> Unit,
  onStartMicrophone: () -> Unit,
  onStopMicrophone: () -> Unit,
  onOpenSessionsTab: () -> Unit,
) {
  val clipboard = LocalClipboardManager.current
  val registeredDevice = (devicesState as? UiState.Success)?.data?.firstOrNull { it.id == deviceId }
    ?: (devicesState as? UiState.Success)?.data?.firstOrNull()
  val sessions = (sessionsState as? UiState.Success)?.data.orEmpty()
  val activeSession = sessions.firstOrNull { it.parsedStatus == RemoteSessionStatus.ACTIVE }
  val pendingSession = sessions.firstOrNull { it.parsedStatus == RemoteSessionStatus.PENDING }

  val connectionStatus = when {
    activeSession != null -> DeviceConnectionStatus.IN_SESSION
    registeredDevice != null -> registeredDevice.parsedConnectionStatus
    else -> DeviceConnectionStatus.IDLE
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // 1. Device Identity & Connection Hero Card
    item {
      Card(
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(
              Brush.linearGradient(
                colors = listOf(
                  MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                  MaterialTheme.colorScheme.secondary.copy(alpha = 0.07f),
                )
              )
            )
            .padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Box(
                modifier = Modifier
                  .size(42.dp)
                  .clip(MaterialTheme.shapes.small)
                  .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.PhoneAndroid,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
              Column {
                Text(
                  text = deviceName,
                  style = MaterialTheme.typography.titleLarge,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = ownerEmail ?: "Authenticated Owner",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
            ConnectionStatusPill(connectionStatus)
          }

          Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                  text = stringResource(R.string.dashboard_device_id_label),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = deviceId.ifBlank { "GENERATING..." },
                  style = MonoTextStyle.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.primary,
                )
              }
              IconButton(
                onClick = {
                  if (deviceId.isNotBlank()) {
                    clipboard.setText(AnnotatedString(deviceId))
                    onCopyDeviceId()
                  }
                },
                modifier = Modifier.testTag("copy_device_id_button"),
              ) {
                Icon(
                  imageVector = Icons.Default.ContentCopy,
                  contentDescription = stringResource(R.string.dashboard_copy_id),
                  tint = MaterialTheme.colorScheme.primary,
                )
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedButton(
              onClick = onSyncDevice,
              modifier = Modifier
                .weight(1f)
                .testTag("sync_device_button"),
              shape = MaterialTheme.shapes.small,
            ) {
              Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text(
                text = if (registeredDevice == null) {
                  stringResource(R.string.dashboard_register_device)
                } else {
                  stringResource(R.string.dashboard_sync_device)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            FilledTonalButton(
              onClick = onSimulateWebRequest,
              modifier = Modifier
                .weight(1f)
                .testTag("create_web_request_button"),
              shape = MaterialTheme.shapes.small,
            ) {
              Icon(Icons.Outlined.CloudQueue, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text(
                text = "Pair Web Ctrl",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    }

    // 2. Pending Incoming Request Alert Card
    if (pendingSession != null) {
      item {
        Card(
          shape = MaterialTheme.shapes.large,
          border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
          ),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Incoming Controller Request",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
              Surface(
                color = MaterialTheme.colorScheme.secondary,
                shape = RoundedCornerShape(50.dp),
              ) {
                Text(
                  text = pendingSession.requestedMode,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSecondary,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
              }
            }
            Text(
              text = "${pendingSession.controllerLabel} (${pendingSession.controllerOrigin}) wants to connect.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Button(
                onClick = { onAcceptSession(pendingSession) },
                modifier = Modifier
                  .weight(1f)
                  .testTag("dashboard_accept_session_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
              ) {
                Text(stringResource(R.string.sessions_accept))
              }
              OutlinedButton(
                onClick = { onRejectSession(pendingSession) },
                modifier = Modifier
                  .weight(1f)
                  .testTag("dashboard_reject_session_button"),
              ) {
                Text(stringResource(R.string.sessions_reject))
              }
            }
          }
        }
      }
    }

    // 3. Active Session Banner
    if (activeSession != null) {
      item {
        Card(
          shape = MaterialTheme.shapes.large,
          border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
          ),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "ACTIVE SESSION",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                )
                Text(
                  text = activeSession.controllerLabel,
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text = runtimeState.statusMessage,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              OutlinedButton(
                onClick = onOpenSessionsTab,
                modifier = Modifier.testTag("view_active_session_button"),
              ) {
                Text("Details")
              }
            }

            Button(
              onClick = { onTerminateSession(activeSession) },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("dashboard_terminate_session_button"),
              colors = ButtonDefaults.buttonColors(
                containerColor = StatusDangerRed,
                contentColor = Color.White,
              ),
            ) {
              Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text(stringResource(R.string.sessions_terminate))
            }
          }
        }
      }
    }

    // 4. Remote Screen Sharing Control Card (MediaProjection + WebRTC)
    item {
      MediaControlCard(
        icon = if (runtimeState.screenSharingActive) Icons.AutoMirrored.Filled.ScreenShare else Icons.Default.Devices,
        title = stringResource(R.string.dashboard_screen_share_title),
        description = stringResource(R.string.dashboard_screen_share_desc),
        isActive = runtimeState.screenSharingActive,
        activeLabel = "SCREEN SHARING LIVE • ${runtimeState.webRtcState.name}",
        accentColor = MaterialTheme.colorScheme.primary,
        startLabel = stringResource(R.string.dashboard_start_screen_share),
        stopLabel = stringResource(R.string.dashboard_stop_screen_share),
        startTestTag = "start_screen_share_button",
        stopTestTag = "stop_screen_share_button",
        onStart = onStartScreenSharing,
        onStop = onStopScreenSharing,
      ) {
        if (runtimeState.screenSharingActive) {
          Spacer(Modifier.height(8.dp))
          LinearProgressIndicator(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
          )
          Spacer(Modifier.height(6.dp))
          Text(
            text = "ICE candidates: ${runtimeState.iceCandidateCount} • DTLS-SRTP active",
            style = MonoTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // 5. CameraX Support Card
    item {
      MediaControlCard(
        icon = if (runtimeState.cameraStreamingActive) Icons.Default.Videocam else Icons.Default.VideocamOff,
        title = stringResource(R.string.dashboard_camera_title),
        description = stringResource(R.string.dashboard_camera_desc),
        isActive = runtimeState.cameraStreamingActive,
        activeLabel = "CAMERA ACTIVE • EXPLICITLY AUTHORIZED",
        accentColor = MaterialTheme.colorScheme.secondary,
        startLabel = stringResource(R.string.dashboard_start_camera),
        stopLabel = stringResource(R.string.dashboard_stop_camera),
        startTestTag = "start_camera_button",
        stopTestTag = "stop_camera_button",
        onStart = onStartCamera,
        onStop = onStopCamera,
      ) {
        if (runtimeState.cameraStreamingActive) {
          Spacer(Modifier.height(10.dp))
          CameraXPreviewCard(
            useBackCamera = useBackCamera,
            onSwitchCamera = onSwitchCamera,
          )
        }
      }
    }

    // 6. Microphone Support Card
    item {
      MediaControlCard(
        icon = if (runtimeState.microphoneStreamingActive) Icons.Default.Mic else Icons.Default.MicOff,
        title = stringResource(R.string.dashboard_mic_title),
        description = stringResource(R.string.dashboard_mic_desc),
        isActive = runtimeState.microphoneStreamingActive,
        activeLabel = "MICROPHONE STREAMING • LIVE AUDIO",
        accentColor = MaterialTheme.colorScheme.secondary,
        startLabel = stringResource(R.string.dashboard_start_mic),
        stopLabel = stringResource(R.string.dashboard_stop_mic),
        startTestTag = "start_mic_button",
        stopTestTag = "stop_mic_button",
        onStart = onStartMicrophone,
        onStop = onStopMicrophone,
      ) {
        if (runtimeState.microphoneStreamingActive) {
          Spacer(Modifier.height(8.dp))
          LinearProgressIndicator(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.secondary,
          )
        }
      }
    }
  }
}

@Composable
private fun ConnectionStatusPill(status: DeviceConnectionStatus) {
  val (bg, fg, label) = when (status) {
    DeviceConnectionStatus.ONLINE -> Triple(
      StatusOnlineGreen.copy(alpha = 0.16f),
      StatusOnlineGreen,
      "ONLINE",
    )
    DeviceConnectionStatus.IN_SESSION -> Triple(
      MaterialTheme.colorScheme.secondaryContainer,
      MaterialTheme.colorScheme.secondary,
      "IN SESSION",
    )
    DeviceConnectionStatus.IDLE -> Triple(
      MaterialTheme.colorScheme.surfaceVariant,
      MaterialTheme.colorScheme.onSurfaceVariant,
      "STANDBY",
    )
    DeviceConnectionStatus.OFFLINE -> Triple(
      MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
      MaterialTheme.colorScheme.error,
      "OFFLINE",
    )
  }

  Surface(
    color = bg,
    shape = RoundedCornerShape(50.dp),
    border = BorderStroke(1.dp, fg.copy(alpha = 0.5f)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Box(
        modifier = Modifier
          .size(8.dp)
          .clip(CircleShape)
          .background(fg),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
      )
    }
  }
}

@Composable
private fun MediaControlCard(
  icon: ImageVector,
  title: String,
  description: String,
  isActive: Boolean,
  activeLabel: String,
  accentColor: Color,
  startLabel: String,
  stopLabel: String,
  startTestTag: String,
  stopTestTag: String,
  onStart: () -> Unit,
  onStop: () -> Unit,
  activeContent: @Composable () -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.large,
    border = BorderStroke(
      width = if (isActive) 1.5.dp else 1.dp,
      color = if (isActive) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
    ),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Box(
          modifier = Modifier
            .size(40.dp)
            .clip(MaterialTheme.shapes.small)
            .background(accentColor.copy(alpha = 0.14f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(imageVector = icon, contentDescription = null, tint = accentColor)
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(text = title, style = MaterialTheme.typography.titleMedium)
          if (isActive) {
            Text(
              text = activeLabel,
              style = MaterialTheme.typography.labelSmall,
              color = accentColor,
            )
          }
        }
      }

      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      activeContent()

      if (isActive) {
        Button(
          onClick = onStop,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag(stopTestTag),
          colors = ButtonDefaults.buttonColors(
            containerColor = StatusDangerRed,
            contentColor = Color.White,
          ),
          shape = MaterialTheme.shapes.small,
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.StopScreenShare,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(8.dp))
          Text(stopLabel)
        }
      } else {
        Button(
          onClick = onStart,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag(startTestTag),
          colors = ButtonDefaults.buttonColors(
            containerColor = accentColor,
            contentColor = Color.White,
          ),
          shape = MaterialTheme.shapes.small,
        ) {
          Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(8.dp))
          Text(startLabel)
        }
      }
    }
  }
}
