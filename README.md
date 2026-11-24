# Wink Remote Access (Android Client)

**Wink Remote Access** is a consent-first Android remote access and support agent built with **Kotlin**, **Jetpack Compose**, **Material 3**, **Firebase Auth & Firestore**, **Firebase Cloud Messaging**, **CameraX**, **MediaProjection**, and **WebRTC**.

---

## Architecture Overview

- **Authentication (`ui/auth/`)**: Uses **Google Sign-In via Jetpack Credential Manager** (`GetSignInWithGoogleOption` + silent `GetGoogleIdOption` startup check) integrated with Firebase Authentication.
- **Firestore Data Layer (`data/repository/WinkRepository.kt`)**: Connects to the provisioned named Enterprise Firestore database (`R.string.firestore_database_id`) using real-time `snapshots()` flows and `Result<T>` mutations.
- **Foreground Media & WebRTC Service (`service/RemoteSessionService.kt`, `webrtc/WebRtcSessionManager.kt`)**:
  - Uses Android `MediaProjectionManager` to present the official system screen-sharing consent dialog.
  - Streams display frames via `org.webrtc.ScreenCapturerAndroid`, STUN ICE (`stun.l.google.com:19302`), and a WebRTC `DataChannel`, exchanging SDP offers/answers and ICE candidates through Firestore.
  - Displays a persistent high-priority foreground notification with a one-tap **Stop Sharing** action and automatically cleans up when stopped via Android's status-bar projection chip or lock screen (`MediaProjection.Callback.onStop()`).
- **CameraX & Microphone (`ui/components/CameraXPreviewCard.kt`)**:
  - Requests `CAMERA` and `RECORD_AUDIO` permissions separately and explicitly.
  - Displays an embedded live `CAMERA LIVE` preview badge with front/rear lens switching and immediate **Stop Camera** / **Stop Mic** controls.
- **Remote Support (`service/WinkAccessibilityService.kt`)**:
  - Operates strictly when the device owner manually enables **Wink Remote Access** inside Android Accessibility Settings (`Settings.ACTION_ACCESSIBILITY_SETTINGS`) and toggles support on during an active session.
  - Automatically disconnects active streams if permissions are revoked.

---

## Firestore Schema & Security Rules

All data is strictly isolated per authenticated owner (`request.auth.uid == userId`) in `/firestore.rules`:

- `/users/{userId}` — Owner profile & theme preference (`SYSTEM`, `LIGHT`, `DARK`)
- `/users/{userId}/devices/{deviceId}` — Registered Android devices (`WINK-XXXX-XXXX`), OS metadata, connection status (`ONLINE`, `IDLE`, `IN_SESSION`, `OFFLINE`), capability flags, and optional `fcmToken`
- `/users/{userId}/sessions/{sessionId}` — Controller session requests (`PENDING`, `ACTIVE`, `REJECTED`, `TERMINATED`) and real-time media flags (`screenSharingActive`, `cameraActive`, `microphoneActive`, `remoteSupportActive`)
- `/users/{userId}/sessions/{sessionId}/signals/{signalId}` — Append-only WebRTC `OFFER`, `ANSWER`, and `ICE_CANDIDATE` signaling messages

---

## Firebase Integration Instructions (Web Controller Companion)

1. **Firestore Database**: Point your Web Controller Dashboard's Firebase Web SDK to the same Firebase project (`app/google-services.json`) and named Firestore database ID (`app/src/main/res/values/firebase_applet_config.xml`).
2. **Initiate Session from Web**: Create a document under `/users/{uid}/sessions/{sessionId}` with `status: "PENDING"`, `deviceId`, `controllerLabel`, `controllerOrigin`, `requestedMode`, and all media boolean flags set to `false`.
3. **WebRTC Handshake**: Once the Android device owner taps **Accept** and approves **Screen Sharing**, listen to `/users/{uid}/sessions/{sessionId}/signals` for `senderRole == "DEVICE"` (`OFFER` and `ICE_CANDIDATE`), then write `senderRole == "CONTROLLER"` (`ANSWER` and `ICE_CANDIDATE`) back to `signals`.
4. **Optional Firebase Console Features**:
   - **Email/Password Authentication & Password Reset**: Enable *Email/Password* in **Firebase Console → Authentication → Sign-in method** if your web dashboard also authenticates via email/password alongside Google Sign-In.
   - **Firebase Cloud Messaging (FCM)**: Configure your Cloud Messaging server key / HTTP v1 credentials in **Firebase Console → Cloud Messaging** to wake backgrounded Android devices via `WinkMessagingService` using each device's stored `fcmToken`.
