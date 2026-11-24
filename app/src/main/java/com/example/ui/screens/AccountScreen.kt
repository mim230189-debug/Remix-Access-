package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ThemePreference
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.StatusDangerRed

@Composable
fun AccountScreen(
  ownerDisplayName: String?,
  ownerEmail: String?,
  ownerUid: String,
  deviceId: String,
  deviceName: String,
  themePreference: ThemePreference,
  onThemeSelected: (ThemePreference) -> Unit,
  onUpdateDeviceName: (String) -> Unit,
  onRemoveDevice: () -> Unit,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  var editedDeviceName by remember(deviceName) { mutableStateOf(deviceName) }
  var showRemoveDialog by remember { mutableStateOf(false) }
  var docsExpanded by remember { mutableStateOf(true) }

  val databaseId = remember(context) {
    context.getString(R.string.firestore_database_id)
  }

  if (showRemoveDialog) {
    AlertDialog(
      onDismissRequest = { showRemoveDialog = false },
      title = { Text("Remove Registered Device?") },
      text = {
        Text("This deletes $deviceId from your Firestore account, terminates any active session, and generates a fresh device identifier.")
      },
      confirmButton = {
        Button(
          onClick = {
            showRemoveDialog = false
            onRemoveDevice()
          },
          colors = ButtonDefaults.buttonColors(containerColor = StatusDangerRed, contentColor = Color.White),
          modifier = Modifier.testTag("confirm_remove_device_button"),
        ) {
          Text("Remove Device")
        }
      },
      dismissButton = {
        TextButton(onClick = { showRemoveDialog = false }) {
          Text("Cancel")
        }
      },
    )
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // 1. Account Card
    item {
      Card(
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(
              imageVector = Icons.Default.AccountCircle,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(44.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = ownerDisplayName?.takeIf { it.isNotBlank() } ?: "Device Owner",
                style = MaterialTheme.typography.titleLarge,
              )
              Text(
                text = ownerEmail ?: "Google Account Authenticated",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small,
          ) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
              Text(
                text = "OWNER AUTH UID",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = ownerUid,
                style = MonoTextStyle,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }

          OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("sign_out_button"),
            shape = MaterialTheme.shapes.small,
          ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.account_sign_out))
          }
        }
      }
    }

    // 2. Theme Selector Card
    item {
      Card(
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = stringResource(R.string.account_theme_title),
            style = MaterialTheme.typography.titleMedium,
          )
          val options = listOf(
            Triple(ThemePreference.SYSTEM, "System", Icons.Default.SettingsBrightness),
            Triple(ThemePreference.LIGHT, "Light", Icons.Default.LightMode),
            Triple(ThemePreference.DARK, "Dark", Icons.Default.DarkMode),
          )
          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (pref, label, icon) ->
              SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                onClick = { onThemeSelected(pref) },
                selected = themePreference == pref,
                icon = {
                  Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                modifier = Modifier.testTag("theme_option_${pref.name.lowercase()}"),
              ) {
                Text(label)
              }
            }
          }
        }
      }
    }

    // 3. Device Registration Management
    item {
      Card(
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = stringResource(R.string.account_device_management),
            style = MaterialTheme.typography.titleMedium,
          )
          OutlinedTextField(
            value = editedDeviceName,
            onValueChange = { editedDeviceName = it.take(80) },
            label = { Text("Device Display Name") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("device_name_input"),
          )
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(
              onClick = { onUpdateDeviceName(editedDeviceName) },
              modifier = Modifier
                .weight(1f)
                .testTag("save_device_name_button"),
            ) {
              Text("Save Name")
            }
            OutlinedButton(
              onClick = { showRemoveDialog = true },
              colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDangerRed),
              border = BorderStroke(1.dp, StatusDangerRed.copy(alpha = 0.6f)),
              modifier = Modifier
                .weight(1f)
                .testTag("remove_device_button"),
            ) {
              Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Remove")
            }
          }
        }
      }
    }

    // 4. Firebase & Web Controller Setup Documentation
    item {
      Card(
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = stringResource(R.string.account_setup_docs_title),
                style = MaterialTheme.typography.titleMedium,
              )
            }
            TextButton(
              onClick = { docsExpanded = !docsExpanded },
              modifier = Modifier.testTag("toggle_setup_docs_button"),
            ) {
              Text(if (docsExpanded) "Hide" else "Show")
              Icon(
                imageVector = if (docsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
              )
            }
          }

          AnimatedVisibility(visible = docsExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              DocBlock(
                title = "1. Provisioned Firestore Endpoint",
                body = "Database ID: $databaseId\nAll Web Controller reads/writes must target this named Enterprise database.",
              )
              DocBlock(
                title = "2. Web Controller Firestore Collections",
                body = "• /users/{uid}/devices/{deviceId} — Device presence, OS info, FCM token\n• /users/{uid}/sessions/{sessionId} — Create PENDING request, observe ACTIVE/TERMINATED status\n• /users/{uid}/sessions/{sessionId}/signals/{signalId} — WebRTC OFFER, ANSWER, and ICE_CANDIDATE signaling",
              )
              DocBlock(
                title = "3. Optional Email/Password & FCM Setup",
                body = "Google Sign-In via Credential Manager is provisioned out of the box. To also enable Email/Password login, password reset emails, or Firebase Cloud Messaging push triggers from your web dashboard, open Firebase Console -> Authentication (Sign-in method) and Cloud Messaging.",
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DocBlock(title: String, body: String) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    shape = MaterialTheme.shapes.small,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
      )
      Text(
        text = body,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
