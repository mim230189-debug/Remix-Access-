package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.RemoteSession
import com.example.data.model.RemoteSessionStatus
import com.example.data.model.WebRtcSignal
import com.example.service.RemoteSessionRuntimeSnapshot
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.StatusDangerRed
import com.example.ui.theme.StatusOnlineGreen
import com.example.ui.viewmodel.UiState
import java.text.DateFormat

@Composable
fun SessionsScreen(
  sessionsState: UiState<List<RemoteSession>>,
  signals: List<WebRtcSignal>,
  runtimeState: RemoteSessionRuntimeSnapshot,
  onAcceptSession: (RemoteSession) -> Unit,
  onRejectSession: (RemoteSession) -> Unit,
  onTerminateSession: (RemoteSession) -> Unit,
  onSimulateWebRequest: () -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    when (sessionsState) {
      is UiState.Loading -> {
        item {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            CircularProgressIndicator()
          }
        }
      }

      is UiState.Error -> {
        item {
          Card(
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
          ) {
            Text(
              text = sessionsState.message,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.padding(16.dp),
            )
          }
        }
      }

      is UiState.Success -> {
        val sessions = sessionsState.data
        val pending = sessions.filter { it.parsedStatus == RemoteSessionStatus.PENDING }
        val active = sessions.filter { it.parsedStatus == RemoteSessionStatus.ACTIVE }
        val history = sessions.filter {
          it.parsedStatus == RemoteSessionStatus.TERMINATED ||
            it.parsedStatus == RemoteSessionStatus.REJECTED
        }

        if (sessions.isEmpty()) {
          item {
            Card(
              shape = MaterialTheme.shapes.large,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.Sensors,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(40.dp),
                )
                Text(
                  text = stringResource(R.string.sessions_empty),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                  onClick = onSimulateWebRequest,
                  modifier = Modifier.testTag("sessions_create_request_button"),
                ) {
                  Text(stringResource(R.string.dashboard_test_request))
                }
              }
            }
          }
        }

        if (pending.isNotEmpty()) {
          item {
            Text(
              text = stringResource(R.string.sessions_incoming_title),
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.secondary,
            )
          }
          items(pending, key = { it.id }) { session ->
            PendingSessionCard(
              session = session,
              onAccept = { onAcceptSession(session) },
              onReject = { onRejectSession(session) },
            )
          }
        }

        if (active.isNotEmpty()) {
          item {
            Text(
              text = stringResource(R.string.sessions_active_title),
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          items(active, key = { it.id }) { session ->
            ActiveSessionDetailCard(
              session = session,
              runtimeState = runtimeState,
              signals = signals,
              onTerminate = { onTerminateSession(session) },
            )
          }
        }

        if (history.isNotEmpty()) {
          item {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = stringResource(R.string.sessions_history_title),
                style = MaterialTheme.typography.titleMedium,
              )
            }
          }
          items(history, key = { it.id }) { session ->
            HistorySessionCard(session = session)
          }
        }
      }
    }
  }
}

@Composable
private fun PendingSessionCard(
  session: RemoteSession,
  onAccept: () -> Unit,
  onReject: () -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.large,
    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
        Text(text = session.controllerLabel, style = MaterialTheme.typography.titleMedium)
        SessionStatusChip(session.parsedStatus)
      }
      Text(
        text = "Origin: ${session.controllerOrigin}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = "Requested Mode: ${session.requestedMode}",
        style = MonoTextStyle,
        color = MaterialTheme.colorScheme.secondary,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
          onClick = onAccept,
          modifier = Modifier
            .weight(1f)
            .testTag("accept_session_${session.id}"),
        ) {
          Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(stringResource(R.string.sessions_accept))
        }
        OutlinedButton(
          onClick = onReject,
          modifier = Modifier
            .weight(1f)
            .testTag("reject_session_${session.id}"),
        ) {
          Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(stringResource(R.string.sessions_reject))
        }
      }
    }
  }
}

@Composable
private fun ActiveSessionDetailCard(
  session: RemoteSession,
  runtimeState: RemoteSessionRuntimeSnapshot,
  signals: List<WebRtcSignal>,
  onTerminate: () -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.large,
    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
          Text(text = session.controllerLabel, style = MaterialTheme.typography.titleMedium)
          Text(
            text = "Session ID: ${session.id}",
            style = MonoTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        SessionStatusChip(session.parsedStatus)
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StreamFlagBadge("Screen", session.screenSharingActive || runtimeState.screenSharingActive)
        StreamFlagBadge("Camera", session.cameraActive || runtimeState.cameraStreamingActive)
        StreamFlagBadge("Mic", session.microphoneActive || runtimeState.microphoneStreamingActive)
        StreamFlagBadge("Support", session.remoteSupportActive || runtimeState.remoteSupportActive)
      }

      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = "WebRTC Transport: ${runtimeState.webRtcState.name}",
            style = MonoTextStyle,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = "Firestore Signals Exchanged: ${signals.size}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          signals.takeLast(3).forEach { sig ->
            Text(
              text = "• ${sig.senderRole} → ${sig.signalType}",
              style = MonoTextStyle,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      Button(
        onClick = onTerminate,
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("terminate_session_${session.id}"),
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

@Composable
private fun HistorySessionCard(session: RemoteSession) {
  val dateStr = session.updatedAt?.toDate()?.let {
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(it)
  } ?: "Just now"

  Card(
    shape = MaterialTheme.shapes.medium,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = session.controllerLabel, style = MaterialTheme.typography.titleSmall)
        Text(
          text = session.terminationReason ?: session.requestedMode,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = dateStr,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      SessionStatusChip(session.parsedStatus)
    }
  }
}

@Composable
private fun SessionStatusChip(status: RemoteSessionStatus) {
  val (color, label) = when (status) {
    RemoteSessionStatus.PENDING -> MaterialTheme.colorScheme.secondary to "PENDING"
    RemoteSessionStatus.ACTIVE -> StatusOnlineGreen to "ACTIVE"
    RemoteSessionStatus.REJECTED -> StatusDangerRed to "REJECTED"
    RemoteSessionStatus.TERMINATED -> MaterialTheme.colorScheme.onSurfaceVariant to "ENDED"
  }
  Surface(
    color = color.copy(alpha = 0.14f),
    shape = RoundedCornerShape(50.dp),
    border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = color,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
  }
}

@Composable
private fun StreamFlagBadge(label: String, active: Boolean) {
  val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
  Surface(
    color = if (active) tint.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
    shape = RoundedCornerShape(8.dp),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = tint,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
  }
}
