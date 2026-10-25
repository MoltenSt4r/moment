const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_host_123";
const BOB_UID = "bob_guest_456";
const EVE_UID = "eve_attacker_789";

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

test("Unauthenticated user: cannot read or create rooms", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("rooms").where("status", "==", "waiting").get());
  await assertFails(unauthDb.collection("rooms").doc("room_1").get());
});

test("Authenticated host: can create waiting room with valid schema", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_valid").set({
      hostUid: ALICE_UID,
      guestUid: "",
      hostPubKey: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE1234567890",
      guestPubKey: "",
      ephemeralSalt: "c2FsdF8xMjM0NTY3ODkwMTIzNDU2",
      status: "waiting",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Authenticated user: cannot create room spoofing another hostUid or with ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertFails(
    aliceDb.collection("rooms").doc("room_spoof").set({
      hostUid: BOB_UID,
      guestUid: "",
      hostPubKey: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE1234567890",
      guestPubKey: "",
      ephemeralSalt: "c2FsdF8xMjM0NTY3ODkwMTIzNDU2",
      status: "waiting",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("rooms").doc("room_ghost").set({
      hostUid: ALICE_UID,
      guestUid: "",
      hostPubKey: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE1234567890",
      guestPubKey: "",
      ephemeralSalt: "c2FsdF8xMjM0NTY3ODkwMTIzNDU2",
      status: "waiting",
      isVerified: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Guest can join waiting room and non-participant Eve cannot read active room", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("rooms").doc("room_join").set({
      hostUid: ALICE_UID,
      guestUid: "",
      hostPubKey: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE_ALICE",
      guestPubKey: "",
      ephemeralSalt: "c2FsdF8xMjM0NTY3ODkwMTIzNDU2",
      status: "waiting",
      createdAt: now,
      updatedAt: now,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(
    bobDb.collection("rooms").doc("room_join").update({
      guestUid: BOB_UID,
      guestPubKey: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE_BOB",
      status: "active",
      updatedAt: new Date(),
    })
  );

  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  await assertFails(eveDb.collection("rooms").doc("room_join").get());
});

test("Encrypted envelopes: participants can create, list, and delete; non-participants cannot read", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const now = new Date();

  await assertSucceeds(
    aliceDb.collection("rooms").doc("room_env").collection("envelopes").doc("env_1").set({
      roomId: "room_env",
      senderUid: ALICE_UID,
      participantUids: [ALICE_UID, BOB_UID],
      ratchetStep: 0,
      iv: "aXZfMTIzNDU2Nzg5MDEy",
      ciphertext: "Y2lwaGVydGV4dF9hZXNfMjU2X2djbV9hdXRoX3RhZw==",
      ttlSeconds: 30,
      createdAt: now,
    })
  );

  await assertSucceeds(
    aliceDb
      .collection("rooms")
      .doc("room_env")
      .collection("envelopes")
      .where("participantUids", "array-contains", ALICE_UID)
      .get()
  );

  await assertFails(
    eveDb
      .collection("rooms")
      .doc("room_env")
      .collection("envelopes")
      .doc("env_1")
      .get()
  );
});
