package com.example.ui.screens

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.service.RemoteSessionRuntimeSnapshot
import com.example.service.WinkAccessibilityService
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.StatusOnlineGreen

@Composable
fun SupportScreen(
  runtimeState: RemoteSessionRuntimeSnapshot,
  cameraPermissionGranted: Boolean,
  micPermissionGranted: Boolean,
  notificationsGranted: Boolean,
  onToggleRemoteSupport: (Boolean) -> Unit,
  onPerformSupportAction: (Int, String) -> Unit,
) {
  val context = LocalContext.current

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // 1. AccessibilityService Remote Support Card
    item {
      Card(
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
          width = if (runtimeState.accessibilityEnabled) 1.5.dp else 1.dp,
          color = if (runtimeState.accessibilityEnabled) {
            MaterialTheme.colorScheme.primary
          } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
          },
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
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
              Icon(
                imageVector = Icons.Default.AccessibilityNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = stringResource(R.string.support_accessibility_title),
                style = MaterialTheme.typography.titleMedium,
              )
            }
            StatusChip(
              enabled = runtimeState.accessibilityEnabled,
              enabledLabel = "ENABLED",
              disabledLabel = "DISABLED",
            )
          }

          Text(
            text = stringResource(R.string.support_accessibility_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Allow Remote Navigation Actions",
                  style = MaterialTheme.typography.titleSmall,
                )
                Text(
                  text = if (runtimeState.accessibilityEnabled) {
                    "Ready during approved active sessions"
                  } else {
                    "Enable Wink Remote Access in Android Accessibility Settings first"
                  },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = runtimeState.remoteSupportActive && runtimeState.accessibilityEnabled,
                onCheckedChange = { checked -> onToggleRemoteSupport(checked) },
                enabled = runtimeState.accessibilityEnabled,
                modifier = Modifier.testTag("remote_support_switch"),
              )
            }
          }

          Button(
            onClick = {
              context.startActivity(WinkAccessibilityService.openAccessibilitySettingsIntent())
            },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("open_accessibility_settings_button"),
            shape = MaterialTheme.shapes.small,
          ) {
            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.support_open_settings))
          }

          if (runtimeState.accessibilityEnabled && runtimeState.remoteSupportActive) {
            Text(
              text = "Supported Remote Navigation Actions",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              FilledTonalButton(
                onClick = {
                  onPerformSupportAction(AccessibilityService.GLOBAL_ACTION_BACK, "Back")
                },
                modifier = Modifier
                  .weight(1f)
                  .testTag("support_action_back"),
              ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Back")
              }
              FilledTonalButton(
                onClick = {
                  onPerformSupportAction(AccessibilityService.GLOBAL_ACTION_HOME, "Home")
                },
                modifier = Modifier
                  .weight(1f)
                  .testTag("support_action_home"),
              ) {
                Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Home")
              }
              FilledTonalButton(
                onClick = {
                  onPerformSupportAction(AccessibilityService.GLOBAL_ACTION_RECENTS, "Recents")
                },
                modifier = Modifier
                  .weight(1f)
                  .testTag("support_action_recents"),
              ) {
                Icon(Icons.Default.ViewCarousel, contentDescription = "Recents", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Recents")
              }
            }
            if (runtimeState.lastSupportAction != null) {
              Text(
                text = "Last action: ${runtimeState.lastSupportAction}",
                style = MonoTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }

    // 2. Permission & Consent Guard Card
    item {
      Card(
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Security,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.secondary,
            )
            Text(
              text = stringResource(R.string.support_permissions_title),
              style = MaterialTheme.typography.titleMedium,
            )
          }

          Text(
            text = stringResource(R.string.support_permissions_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          PermissionAuditRow(
            title = "Screen Capture (MediaProjection)",
            detail = "Requested per session via official Android consent dialog.",
            granted = runtimeState.screenSharingActive,
            grantedLabel = "ACTIVE",
            pendingLabel = "STANDBY",
          )
          PermissionAuditRow(
            title = "Camera (CameraX)",
            detail = "Requested explicitly before opening CameraX video preview.",
            granted = cameraPermissionGranted,
            grantedLabel = "GRANTED",
            pendingLabel = "NOT GRANTED",
          )
          PermissionAuditRow(
            title = "Microphone (RECORD_AUDIO)",
            detail = "Requested separately from camera; muted by default.",
            granted = micPermissionGranted,
            grantedLabel = "GRANTED",
            pendingLabel = "NOT GRANTED",
          )
          PermissionAuditRow(
            title = "Notifications (POST_NOTIFICATIONS)",
            detail = "Displays persistent active-stream alert & incoming request banner.",
            granted = notificationsGranted,
            grantedLabel = "GRANTED",
            pendingLabel = "NOT GRANTED",
          )
          PermissionAuditRow(
            title = "Accessibility Service",
            detail = "Opt-in via Android Accessibility Settings only.",
            granted = runtimeState.accessibilityEnabled,
            grantedLabel = "ENABLED",
            pendingLabel = "DISABLED",
          )

          OutlinedButton(
            onClick = {
              val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
              }
              context.startActivity(intent)
            },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("open_app_permissions_settings"),
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.outlinedButtonColors(),
          ) {
            Text("Manage Permissions in Android Settings")
          }
        }
      }
    }
  }
}

@Composable
private fun PermissionAuditRow(
  title: String,
  detail: String,
  granted: Boolean,
  grantedLabel: String,
  pendingLabel: String,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    shape = MaterialTheme.shapes.small,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(
        modifier = Modifier
          .weight(1f)
          .padding(end = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Text(
          text = detail,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      StatusChip(
        enabled = granted,
        enabledLabel = grantedLabel,
        disabledLabel = pendingLabel,
      )
    }
  }
}

@Composable
private fun StatusChip(
  enabled: Boolean,
  enabledLabel: String,
  disabledLabel: String,
) {
  val tint = if (enabled) StatusOnlineGreen else MaterialTheme.colorScheme.secondary
  Surface(
    color = tint.copy(alpha = 0.14f),
    shape = RoundedCornerShape(50.dp),
    border = BorderStroke(1.dp, tint.copy(alpha = 0.45f)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = if (enabled) Icons.Default.CheckCircle else Icons.Outlined.WarningAmber,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(13.dp),
      )
      Text(
        text = if (enabled) enabledLabel else disabledLabel,
        style = MaterialTheme.typography.labelSmall,
        color = tint,
      )
    }
  }
}
