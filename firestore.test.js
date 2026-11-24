const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_owner_123";
const BOB_UID = "bob_attacker_456";
const DEVICE_ID = "WINK-A1B2-C3D4";
const SESSION_ID = "sess_1001";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function devicePayload(uid, now) {
  return {
    userId: uid,
    deviceName: "Pixel 9 Pro",
    manufacturer: "Google",
    model: "Pixel 9 Pro",
    osVersion: "Android 15 (API 35)",
    appVersion: "1.0",
    connectionStatus: "ONLINE",
    screenShareSupported: true,
    cameraSupported: true,
    microphoneSupported: true,
    remoteSupportEnabled: false,
    lastSeenAt: now,
    createdAt: now,
    updatedAt: now,
  };
}

function sessionPayload(uid, deviceId, now) {
  return {
    userId: uid,
    deviceId,
    controllerLabel: "Personal Web Dashboard",
    controllerOrigin: "https://dashboard.winkremote.app",
    requestedMode: "SCREEN",
    status: "PENDING",
    screenSharingActive: false,
    cameraActive: false,
    microphoneActive: false,
    remoteSupportActive: false,
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated context: cannot read or write user, device, session, or signal docs", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("devices").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("devices").doc(DEVICE_ID).set(devicePayload(ALICE_UID, now))
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("sessions").get());
});

test("Owner can manage own account, register/update device, and reject ghost fields or cross-user spoofing", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  // User account create & partial update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      themePreference: "DARK",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      themePreference: "LIGHT",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Device registration (valid Kotlin payload without @DocumentId `id`)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc(DEVICE_ID).set(devicePayload(ALICE_UID, created))
  );

  // Reject ghost field `id` on create
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc("WINK-BAD-1").set({
      ...devicePayload(ALICE_UID, created),
      id: "WINK-BAD-1",
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc("WINK-FUTURE-1").set(
      devicePayload(ALICE_UID, new Date(Date.now() + 60000))
    )
  );

  // Partial update on device succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc(DEVICE_ID).update({
      connectionStatus: "IN_SESSION",
      remoteSupportEnabled: true,
      lastSeenAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc(DEVICE_ID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user isolation: Bob cannot read, list, update, or delete Alice's device
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("devices").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("devices").doc(DEVICE_ID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("devices").doc(DEVICE_ID).update({
      connectionStatus: "OFFLINE",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("devices").doc(DEVICE_ID).delete());
});

test("Session lifecycle, terminal state lock, and WebRTC signaling rules", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  // Must have existing registered device before creating session
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc(SESSION_ID).set(
      sessionPayload(ALICE_UID, DEVICE_ID, created)
    )
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc(DEVICE_ID).set(devicePayload(ALICE_UID, created))
  );

  // Reject creating an already-ACTIVE session (must start PENDING with all media flags false)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("bad_sess").set({
      ...sessionPayload(ALICE_UID, DEVICE_ID, created),
      status: "ACTIVE",
    })
  );

  // Valid PENDING session create
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc(SESSION_ID).set(
      sessionPayload(ALICE_UID, DEVICE_ID, created)
    )
  );

  // Accept session
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc(SESSION_ID).update({
      status: "ACTIVE",
      screenSharingActive: true,
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Toggle camera/mic while ACTIVE
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc(SESSION_ID).update({
      cameraActive: true,
      microphoneActive: true,
      updatedAt: new Date(Date.now() - 1500),
    })
  );

  // Create WebRTC OFFER signal
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sessions")
      .doc(SESSION_ID)
      .collection("signals")
      .doc("sig_1")
      .set({
        userId: ALICE_UID,
        sessionId: SESSION_ID,
        senderRole: "DEVICE",
        signalType: "OFFER",
        sdpOrCandidate: "v=0\r\no=- 12345 2 IN IP4 127.0.0.1\r\n",
        createdAt: new Date(Date.now() - 1200),
      })
  );

  // Signals are immutable
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sessions")
      .doc(SESSION_ID)
      .collection("signals")
      .doc("sig_1")
      .update({ sdpOrCandidate: "tampered" })
  );

  // Cross-user cannot read signals
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("sessions").doc(SESSION_ID).collection("signals").get()
  );

  // Cannot terminate while leaving media flag true
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc(SESSION_ID).update({
      status: "TERMINATED",
      screenSharingActive: true,
      cameraActive: false,
      microphoneActive: false,
      remoteSupportActive: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Valid termination clears all media flags
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc(SESSION_ID).update({
      status: "TERMINATED",
      screenSharingActive: false,
      cameraActive: false,
      microphoneActive: false,
      remoteSupportActive: false,
      terminationReason: "Ended by device owner",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Terminal state lock: cannot reactivate terminated session
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc(SESSION_ID).update({
      status: "ACTIVE",
      screenSharingActive: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // List queries succeed on subcollections for owner
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("devices").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("sessions").get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc(SESSION_ID).collection("signals").get()
  );
});
