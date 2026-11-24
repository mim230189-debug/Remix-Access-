package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.local.DevicePreferencesStore
import com.example.data.model.RemoteSessionStatus
import com.example.data.model.SessionRequestedMode
import com.example.data.model.ThemePreference
import com.example.data.repository.WinkRepository
import com.example.service.RemoteSessionRuntimeState
import com.example.service.RemoteSessionService
import com.example.service.WinkAccessibilityService
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.screens.AccountScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.SessionsScreen
import com.example.ui.screens.SupportScreen
import com.example.ui.theme.WinkRemoteAccessTheme
import com.example.ui.viewmodel.UiState
import com.example.ui.viewmodel.WinkViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

object WinkNavRoutes {
  const val ROUTE_DASHBOARD = "dashboard"
  const val ROUTE_SESSIONS = "sessions"
  const val ROUTE_SUPPORT = "support"
  const val ROUTE_ACCOUNT = "account"
}

private data class NavDestinationItem(
  val route: String,
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
)

@Composable
fun WinkAppRoot() {
  val context = LocalContext.current
  val prefsStore = remember(context) { DevicePreferencesStore(context.applicationContext) }
  val localTheme by prefsStore.themePreferenceFlow.collectAsStateWithLifecycle(
    initialValue = ThemePreference.SYSTEM,
  )

  var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
  DisposableEffect(Unit) {
    val listener = FirebaseAuth.AuthStateListener { auth ->
      currentUser = auth.currentUser
    }
    Firebase.auth.addAuthStateListener(listener)
    onDispose { Firebase.auth.removeAuthStateListener(listener) }
  }

  WinkRemoteAccessTheme(themePreference = localTheme) {
    val user = currentUser
    if (user == null) {
      AuthScreen(
        onAuthSuccess = { currentUser = Firebase.auth.currentUser },
      )
    } else {
      key(user.uid) {
        WinkAuthenticatedShell(
          currentUserId = user.uid,
          ownerEmail = user.email,
          ownerDisplayName = user.displayName,
          onSignedOut = {
            RemoteSessionService.stopAll(context)
            RemoteSessionRuntimeState.resetAll()
            currentUser = Firebase.auth.currentUser
          },
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WinkAuthenticatedShell(
  currentUserId: String,
  ownerEmail: String?,
  ownerDisplayName: String?,
  onSignedOut: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  val viewModel: WinkViewModel = viewModel(
    key = currentUserId,
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY]) {
          "APPLICATION_KEY missing from CreationExtras"
        }
        val databaseId = app.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        WinkViewModel(
          repository = WinkRepository(db),
          preferencesStore = DevicePreferencesStore(app),
          currentUserId = currentUserId,
          ownerEmail = ownerEmail,
          ownerDisplayName = ownerDisplayName,
        )
      }
    },
  )

  val deviceId by viewModel.deviceId.collectAsStateWithLifecycle()
  val deviceName by viewModel.deviceName.collectAsStateWithLifecycle()
  val devicesState by viewModel.devicesState.collectAsStateWithLifecycle()
  val sessionsState by viewModel.sessionsState.collectAsStateWithLifecycle()
  val signals by viewModel.activeSessionSignals.collectAsStateWithLifecycle()
  val runtimeState by viewModel.runtimeState.collectAsStateWithLifecycle()
  val localTheme by viewModel.localThemePreference.collectAsStateWithLifecycle()
  val useBackCamera by viewModel.selectedCameraBack.collectAsStateWithLifecycle()
  val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

  var cameraGranted by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  var micGranted by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  var notificationsGranted by remember {
    mutableStateOf(
      Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    )
  }

  fun refreshPermissionsAndReconcile() {
    cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
      PackageManager.PERMISSION_GRANTED
    micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED
    notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
      PackageManager.PERMISSION_GRANTED
    val accEnabled = WinkAccessibilityService.isEnabledInSettings(context)
    viewModel.reconcilePermissions(cameraGranted, micGranted, accEnabled)
  }

  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    refreshPermissionsAndReconcile()
  }

  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { granted ->
    notificationsGranted = granted
  }

  LaunchedEffect(Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsGranted) {
      notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  // Official Android MediaProjection screen-sharing consent launcher
  val mediaProjectionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult(),
  ) { result ->
    val projectionData = result.data
    if (result.resultCode == Activity.RESULT_OK && projectionData != null) {
      viewModel.ensureOrAcceptActiveSession { sessionId ->
        RemoteSessionService.startMediaCapture(
          context = context,
          sessionId = sessionId,
          projectionData = projectionData,
          cameraActive = runtimeState.cameraStreamingActive,
          microphoneActive = runtimeState.microphoneStreamingActive,
        )
        viewModel.syncActiveMediaFlags(
          sessionId = sessionId,
          screenSharingActive = true,
          cameraActive = runtimeState.cameraStreamingActive,
          microphoneActive = runtimeState.microphoneStreamingActive,
          remoteSupportActive = runtimeState.remoteSupportActive,
        )
        viewModel.postMessage("Screen sharing live over encrypted WebRTC")
      }
    } else {
      viewModel.postMessage("Screen sharing consent declined")
    }
  }

  // Explicit CameraX permission launcher
  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { granted ->
    cameraGranted = granted
    if (granted) {
      viewModel.ensureOrAcceptActiveSession { sessionId ->
        RemoteSessionService.startMediaCapture(
          context = context,
          sessionId = sessionId,
          projectionData = null,
          cameraActive = true,
          microphoneActive = runtimeState.microphoneStreamingActive,
        )
        viewModel.syncActiveMediaFlags(
          sessionId = sessionId,
          screenSharingActive = runtimeState.screenSharingActive,
          cameraActive = true,
          microphoneActive = runtimeState.microphoneStreamingActive,
          remoteSupportActive = runtimeState.remoteSupportActive,
        )
        viewModel.postMessage("CameraX live feed active")
      }
    } else {
      viewModel.postMessage("Camera permission denied — camera remains off")
    }
  }

  // Separate explicit Microphone permission launcher
  val micPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { granted ->
    micGranted = granted
    if (granted) {
      viewModel.ensureOrAcceptActiveSession { sessionId ->
        RemoteSessionService.startMediaCapture(
          context = context,
          sessionId = sessionId,
          projectionData = null,
          cameraActive = runtimeState.cameraStreamingActive,
          microphoneActive = true,
        )
        viewModel.syncActiveMediaFlags(
          sessionId = sessionId,
          screenSharingActive = runtimeState.screenSharingActive,
          cameraActive = runtimeState.cameraStreamingActive,
          microphoneActive = true,
          remoteSupportActive = runtimeState.remoteSupportActive,
        )
        viewModel.postMessage("Microphone audio streaming enabled")
      }
    } else {
      viewModel.postMessage("Microphone permission denied")
    }
  }

  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(snackbarMessage) {
    val msg = snackbarMessage ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(msg)
    viewModel.clearMessage()
  }

  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route ?: WinkNavRoutes.ROUTE_DASHBOARD

  val pendingCount = (sessionsState as? UiState.Success)?.data
    ?.count { it.parsedStatus == RemoteSessionStatus.PENDING } ?: 0

  val navItems = remember {
    listOf(
      NavDestinationItem(
        WinkNavRoutes.ROUTE_DASHBOARD,
        R.string.nav_dashboard,
        Icons.Filled.Dashboard,
        Icons.Outlined.Dashboard,
        "nav_dashboard",
      ),
      NavDestinationItem(
        WinkNavRoutes.ROUTE_SESSIONS,
        R.string.nav_sessions,
        Icons.Filled.Sensors,
        Icons.Outlined.Sensors,
        "nav_sessions",
      ),
      NavDestinationItem(
        WinkNavRoutes.ROUTE_SUPPORT,
        R.string.nav_support,
        Icons.Filled.SupportAgent,
        Icons.Outlined.SupportAgent,
        "nav_support",
      ),
      NavDestinationItem(
        WinkNavRoutes.ROUTE_ACCOUNT,
        R.string.nav_account,
        Icons.Filled.AccountCircle,
        Icons.Outlined.AccountCircle,
        "nav_account",
      ),
    )
  }

  fun navigateToTab(route: String) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) {
        saveState = true
      }
      launchSingleTop = true
      restoreState = true
    }
  }

  fun startScreenSharingWithConsent() {
    val projectionManager =
      context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
  }

  fun startCameraWithConsent() {
    if (cameraGranted) {
      viewModel.ensureOrAcceptActiveSession { sessionId ->
        RemoteSessionService.startMediaCapture(
          context = context,
          sessionId = sessionId,
          projectionData = null,
          cameraActive = true,
          microphoneActive = runtimeState.microphoneStreamingActive,
        )
        viewModel.syncActiveMediaFlags(
          sessionId = sessionId,
          screenSharingActive = runtimeState.screenSharingActive,
          cameraActive = true,
          microphoneActive = runtimeState.microphoneStreamingActive,
          remoteSupportActive = runtimeState.remoteSupportActive,
        )
      }
    } else {
      cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  fun startMicrophoneWithConsent() {
    if (micGranted) {
      viewModel.ensureOrAcceptActiveSession { sessionId ->
        RemoteSessionService.startMediaCapture(
          context = context,
          sessionId = sessionId,
          projectionData = null,
          cameraActive = runtimeState.cameraStreamingActive,
          microphoneActive = true,
        )
        viewModel.syncActiveMediaFlags(
          sessionId = sessionId,
          screenSharingActive = runtimeState.screenSharingActive,
          cameraActive = runtimeState.cameraStreamingActive,
          microphoneActive = true,
          remoteSupportActive = runtimeState.remoteSupportActive,
        )
      }
    } else {
      micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavRail = maxWidth >= 600.dp

    Scaffold(
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      snackbarHost = { SnackbarHost(snackbarHostState) },
      topBar = {
        CenterAlignedTopAppBar(
          title = {
            Text(
              text = stringResource(R.string.app_name),
              style = MaterialTheme.typography.titleLarge,
            )
          },
          actions = {
            IconButton(
              onClick = {
                val next = if (localTheme == ThemePreference.DARK) {
                  ThemePreference.LIGHT
                } else {
                  ThemePreference.DARK
                }
                viewModel.setThemePreference(next)
              },
              modifier = Modifier.testTag("topbar_theme_toggle"),
            ) {
              Icon(
                imageVector = Icons.Filled.Sensors,
                contentDescription = "Toggle theme",
                tint = if (runtimeState.screenSharingActive || runtimeState.cameraStreamingActive) {
                  MaterialTheme.colorScheme.secondary
                } else {
                  MaterialTheme.colorScheme.primary
                },
              )
            }
          },
          colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
          ),
        )
      },
      bottomBar = {
        if (!useNavRail) {
          NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            navItems.forEach { item ->
              val selected = currentRoute == item.route
              NavigationBarItem(
                selected = selected,
                onClick = { navigateToTab(item.route) },
                icon = {
                  BadgedBox(
                    badge = {
                      if (item.route == WinkNavRoutes.ROUTE_SESSIONS && pendingCount > 0) {
                        Badge { Text("$pendingCount") }
                      }
                    },
                  ) {
                    Icon(
                      imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                      contentDescription = stringResource(item.labelRes),
                    )
                  }
                },
                label = { Text(stringResource(item.labelRes)) },
                modifier = Modifier.testTag(item.testTag),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        if (useNavRail) {
          NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.surface,
          ) {
            navItems.forEach { item ->
              val selected = currentRoute == item.route
              NavigationRailItem(
                selected = selected,
                onClick = { navigateToTab(item.route) },
                icon = {
                  BadgedBox(
                    badge = {
                      if (item.route == WinkNavRoutes.ROUTE_SESSIONS && pendingCount > 0) {
                        Badge { Text("$pendingCount") }
                      }
                    },
                  ) {
                    Icon(
                      imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                      contentDescription = stringResource(item.labelRes),
                    )
                  }
                },
                label = { Text(stringResource(item.labelRes)) },
                modifier = Modifier.testTag(item.testTag),
              )
            }
          }
        }

        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
          contentAlignment = Alignment.TopCenter,
        ) {
          NavHost(
            navController = navController,
            startDestination = WinkNavRoutes.ROUTE_DASHBOARD,
            modifier = Modifier
              .widthIn(max = 920.dp)
              .fillMaxSize(),
          ) {
            composable(WinkNavRoutes.ROUTE_DASHBOARD) {
              DashboardScreen(
                deviceId = deviceId,
                deviceName = deviceName,
                ownerEmail = ownerEmail,
                devicesState = devicesState,
                sessionsState = sessionsState,
                runtimeState = runtimeState,
                useBackCamera = useBackCamera,
                onSyncDevice = { viewModel.registerOrSyncDevice(silent = false) },
                onCopyDeviceId = { viewModel.postMessage(context.getString(R.string.dashboard_copied_id)) },
                onSimulateWebRequest = {
                  viewModel.createIncomingControllerRequest(SessionRequestedMode.FULL_SUPPORT)
                },
                onAcceptSession = { session -> viewModel.acceptSession(session) },
                onRejectSession = { session -> viewModel.rejectSession(session) },
                onTerminateSession = { session ->
                  RemoteSessionService.stopAll(context)
                  viewModel.terminateSession(session)
                },
                onStartScreenSharing = { startScreenSharingWithConsent() },
                onStopScreenSharing = {
                  RemoteSessionService.stopScreenShare(context)
                  runtimeState.activeSessionId?.let { sid ->
                    viewModel.syncActiveMediaFlags(
                      sessionId = sid,
                      screenSharingActive = false,
                      cameraActive = runtimeState.cameraStreamingActive,
                      microphoneActive = runtimeState.microphoneStreamingActive,
                      remoteSupportActive = runtimeState.remoteSupportActive,
                    )
                  }
                },
                onStartCamera = { startCameraWithConsent() },
                onStopCamera = {
                  RemoteSessionRuntimeState.update { it.copy(cameraStreamingActive = false) }
                  runtimeState.activeSessionId?.let { sid ->
                    viewModel.syncActiveMediaFlags(
                      sessionId = sid,
                      screenSharingActive = runtimeState.screenSharingActive,
                      cameraActive = false,
                      microphoneActive = runtimeState.microphoneStreamingActive,
                      remoteSupportActive = runtimeState.remoteSupportActive,
                    )
                  }
                  if (!runtimeState.screenSharingActive && !runtimeState.microphoneStreamingActive) {
                    RemoteSessionService.stopAll(context)
                  }
                  viewModel.postMessage("Camera stopped")
                },
                onSwitchCamera = { viewModel.toggleCameraLens() },
                onStartMicrophone = { startMicrophoneWithConsent() },
                onStopMicrophone = {
                  RemoteSessionRuntimeState.update { it.copy(microphoneStreamingActive = false) }
                  runtimeState.activeSessionId?.let { sid ->
                    viewModel.syncActiveMediaFlags(
                      sessionId = sid,
                      screenSharingActive = runtimeState.screenSharingActive,
                      cameraActive = runtimeState.cameraStreamingActive,
                      microphoneActive = false,
                      remoteSupportActive = runtimeState.remoteSupportActive,
                    )
                  }
                  if (!runtimeState.screenSharingActive && !runtimeState.cameraStreamingActive) {
                    RemoteSessionService.stopAll(context)
                  }
                  viewModel.postMessage("Microphone stopped")
                },
                onOpenSessionsTab = { navigateToTab(WinkNavRoutes.ROUTE_SESSIONS) },
              )
            }

            composable(WinkNavRoutes.ROUTE_SESSIONS) {
              SessionsScreen(
                sessionsState = sessionsState,
                signals = signals,
                runtimeState = runtimeState,
                onAcceptSession = { session -> viewModel.acceptSession(session) },
                onRejectSession = { session -> viewModel.rejectSession(session) },
                onTerminateSession = { session ->
                  RemoteSessionService.stopAll(context)
                  viewModel.terminateSession(session)
                },
                onSimulateWebRequest = {
                  viewModel.createIncomingControllerRequest(SessionRequestedMode.SCREEN_AND_AUDIO)
                },
              )
            }

            composable(WinkNavRoutes.ROUTE_SUPPORT) {
              SupportScreen(
                runtimeState = runtimeState,
                cameraPermissionGranted = cameraGranted,
                micPermissionGranted = micGranted,
                notificationsGranted = notificationsGranted,
                onToggleRemoteSupport = { enabled ->
                  if (enabled) {
                    viewModel.ensureOrAcceptActiveSession { sid ->
                      viewModel.syncActiveMediaFlags(
                        sessionId = sid,
                        screenSharingActive = runtimeState.screenSharingActive,
                        cameraActive = runtimeState.cameraStreamingActive,
                        microphoneActive = runtimeState.microphoneStreamingActive,
                        remoteSupportActive = true,
                      )
                      viewModel.postMessage("Remote support enabled for active session")
                    }
                  } else {
                    RemoteSessionRuntimeState.update { it.copy(remoteSupportActive = false) }
                    runtimeState.activeSessionId?.let { sid ->
                      viewModel.syncActiveMediaFlags(
                        sessionId = sid,
                        screenSharingActive = runtimeState.screenSharingActive,
                        cameraActive = runtimeState.cameraStreamingActive,
                        microphoneActive = runtimeState.microphoneStreamingActive,
                        remoteSupportActive = false,
                      )
                    }
                  }
                },
                onPerformSupportAction = { action, label ->
                  val svc = WinkAccessibilityService.instance
                  if (svc == null) {
                    viewModel.postMessage("Accessibility service is not connected")
                  } else if (!svc.performSupportedGlobalAction(action, label)) {
                    viewModel.postMessage("Enable Remote Navigation Actions during an active session first")
                  }
                },
              )
            }

            composable(WinkNavRoutes.ROUTE_ACCOUNT) {
              AccountScreen(
                ownerDisplayName = ownerDisplayName,
                ownerEmail = ownerEmail,
                ownerUid = currentUserId,
                deviceId = deviceId,
                deviceName = deviceName,
                themePreference = localTheme,
                onThemeSelected = { pref -> viewModel.setThemePreference(pref) },
                onUpdateDeviceName = { name -> viewModel.updateDeviceName(name) },
                onRemoveDevice = {
                  RemoteSessionService.stopAll(context)
                  viewModel.removeCurrentDevice()
                },
                onSignOut = {
                  RemoteSessionService.stopAll(context)
                  signOutUser(credentialManager, scope, onSignedOut)
                },
              )
            }
          }
        }
      }
    }
  }
}
