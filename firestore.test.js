const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const crypto = require("node:crypto");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_owner_001";
const ADMIN_EMAIL = "ajimsquishy@gmail.com";
const TEACHER_A_UID = "teacher_a_001";
const TEACHER_A_EMAIL = "budi.guru@smk-tp.sch.id";
const TEACHER_B_UID = "teacher_b_002";
const TEACHER_B_EMAIL = "siti.guru@smk-tp.sch.id";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function sha256Hex(raw) {
  return crypto.createHash("sha256").update(raw, "utf8").digest("hex");
}

const VALID_CODE_PLAIN = "GURU-8K2P-9M4X";
const VALID_CODE_HASH = sha256Hex(VALID_CODE_PLAIN);
const EXPIRED_CODE_HASH = sha256Hex("GURU-7J3N-2Q5V");
const REVOKED_CODE_HASH = sha256Hex("GURU-4H6R-8W3Z");

function adminContext() {
  return testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  });
}

function teacherContext(uid, email, emailVerified = true) {
  return testEnv.authenticatedContext(uid, {
    email,
    email_verified: emailVerified,
  });
}

async function seedActiveCode(codeHash, expiresOffsetMs = 7 * 24 * 60 * 60 * 1000) {
  const now = Date.now();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("activation_codes").doc(codeHash).set({
      codeHash,
      codeHint: "GURU-****-9M4X",
      note: "Untuk Guru Teknik Pemesinan",
      status: "active",
      createdByAdminUid: ADMIN_UID,
      createdAt: new Date(now - 5000),
      expiresAt: new Date(now + expiresOffsetMs),
    });
  });
}

async function seedTeacher(uid, email, status = "active", codeHash = VALID_CODE_HASH) {
  const now = Date.now();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("teachers").doc(uid).set({
      uid,
      fullName: "Budi Santoso, S.Pd",
      email,
      teachingClass: "X TP 1",
      role: "teacher",
      status,
      activationCodeHash: codeHash,
      activatedAt: new Date(now - 10000),
      updatedAt: new Date(now - 10000),
    });
  });
}

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

// ---------------------------------------------------------------------------
// 1. Admin Utama Login & Bootstrap Security (incl. Email Spoofing Test)
// ---------------------------------------------------------------------------
test("1. Admin Utama with verified owner email can bootstrap admins/{uid}; spoofed or regular users cannot", async () => {
  const now = new Date(Date.now() - 1000);
  const adminDb = adminContext().firestore();

  await assertSucceeds(
    adminDb.collection("admins").doc(ADMIN_UID).set({
      uid: ADMIN_UID,
      email: ADMIN_EMAIL,
      displayName: "Admin Utama MEASURE LAB",
      role: "admin",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(adminDb.collection("admins").doc(ADMIN_UID).get());

  // Email spoofing test: same email but email_verified = false MUST FAIL
  const spoofedAdminDb = testEnv
    .authenticatedContext("spoofed_uid", {
      email: ADMIN_EMAIL,
      email_verified: false,
    })
    .firestore();
  await assertFails(
    spoofedAdminDb.collection("admins").doc("spoofed_uid").set({
      uid: "spoofed_uid",
      email: ADMIN_EMAIL,
      displayName: "Fake Admin",
      role: "admin",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Regular teacher CANNOT register as admin
  const teacherDb = teacherContext(TEACHER_A_UID, TEACHER_A_EMAIL).firestore();
  await assertFails(
    teacherDb.collection("admins").doc(TEACHER_A_UID).set({
      uid: TEACHER_A_UID,
      email: TEACHER_A_EMAIL,
      displayName: "Unauthorized Teacher",
      role: "admin",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Unauthenticated user CANNOT read or write anything
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("admins").doc(ADMIN_UID).get());
  await assertFails(unauthDb.collection("teachers").get());
  await assertFails(unauthDb.collection("activation_codes").get());
});

// ---------------------------------------------------------------------------
// 2. Admin Generates, Lists, Revokes, and Deletes Unused Activation Codes
// ---------------------------------------------------------------------------
test("2. Admin Utama can generate 7-day hashed activation codes, list, revoke, and delete unused codes", async () => {
  const adminDb = adminContext().firestore();
  const now = Date.now();

  // Mirror Kotlin ActivationCode create payload
  await assertSucceeds(
    adminDb.collection("activation_codes").doc(VALID_CODE_HASH).set({
      codeHash: VALID_CODE_HASH,
      codeHint: "GURU-****-9M4X",
      note: "Pak Budi - X TP 1",
      status: "active",
      createdByAdminUid: ADMIN_UID,
      createdAt: new Date(now - 1000),
      expiresAt: new Date(now + 7 * 24 * 60 * 60 * 1000),
    })
  );

  // Admin can list all activation codes matching Kotlin repository orderBy query
  await assertSucceeds(
    adminDb.collection("activation_codes").orderBy("createdAt", "desc").get()
  );

  // Admin can revoke an unused active code
  await assertSucceeds(
    adminDb.collection("activation_codes").doc(VALID_CODE_HASH).update({
      status: "revoked",
      revokedAt: new Date(now - 500),
    })
  );

  // Admin can delete a revoked / unused code
  await assertSucceeds(
    adminDb.collection("activation_codes").doc(VALID_CODE_HASH).delete()
  );

  // Shadow update / invalid ghost field rejected
  await assertFails(
    adminDb.collection("activation_codes").doc(REVOKED_CODE_HASH).set({
      codeHash: REVOKED_CODE_HASH,
      codeHint: "GURU-****-8W3Z",
      status: "active",
      createdByAdminUid: ADMIN_UID,
      createdAt: new Date(now - 1000),
      expiresAt: new Date(now + 7 * 24 * 60 * 60 * 1000),
      isUniversal: true,
    })
  );
});

// ---------------------------------------------------------------------------
// 3 & 4. Teacher Atomic Activation Succeeds Once; Second Redemption Rejected
// ---------------------------------------------------------------------------
test("3 & 4. Teacher atomically redeems valid code once; second use of same code is rejected", async () => {
  await seedActiveCode(VALID_CODE_HASH);

  const teacherADb = teacherContext(TEACHER_A_UID, TEACHER_A_EMAIL).firestore();
  const now = new Date(Date.now() - 1000);

  // Atomic batch/transaction: mark activation_codes/{hash} as used AND create teachers/{uid}
  const batchA = teacherADb.batch();
  batchA.update(teacherADb.collection("activation_codes").doc(VALID_CODE_HASH), {
    status: "used",
    usedByUid: TEACHER_A_UID,
    usedAt: now,
  });
  batchA.set(teacherADb.collection("teachers").doc(TEACHER_A_UID), {
    uid: TEACHER_A_UID,
    fullName: "Budi Santoso, S.Pd",
    email: TEACHER_A_EMAIL,
    teachingClass: "X TP 1",
    role: "teacher",
    status: "active",
    activationCodeHash: VALID_CODE_HASH,
    activatedAt: now,
    updatedAt: now,
  });
  await assertSucceeds(batchA.commit());

  // Second teacher attempts to reuse the EXACT SAME code -> MUST FAIL
  const teacherBDb = teacherContext(TEACHER_B_UID, TEACHER_B_EMAIL).firestore();
  const batchB = teacherBDb.batch();
  batchB.update(teacherBDb.collection("activation_codes").doc(VALID_CODE_HASH), {
    status: "used",
    usedByUid: TEACHER_B_UID,
    usedAt: new Date(Date.now() - 500),
  });
  batchB.set(teacherBDb.collection("teachers").doc(TEACHER_B_UID), {
    uid: TEACHER_B_UID,
    fullName: "Siti Rahayu, S.Pd",
    email: TEACHER_B_EMAIL,
    teachingClass: "X TP 2",
    role: "teacher",
    status: "active",
    activationCodeHash: VALID_CODE_HASH,
    activatedAt: new Date(Date.now() - 500),
    updatedAt: new Date(Date.now() - 500),
  });
  await assertFails(batchB.commit());

  // Admin cannot delete an already-used activation code (preserves audit trail)
  const adminDb = adminContext().firestore();
  await assertFails(
    adminDb.collection("activation_codes").doc(VALID_CODE_HASH).delete()
  );
});

// ---------------------------------------------------------------------------
// 5. Expired & Revoked Codes Are Strictly Rejected
// ---------------------------------------------------------------------------
test("5. Expired activation code and revoked activation code are rejected server-side", async () => {
  // Expired 1 minute ago
  await seedActiveCode(EXPIRED_CODE_HASH, -60 * 1000);

  const teacherADb = teacherContext(TEACHER_A_UID, TEACHER_A_EMAIL).firestore();
  const now = new Date(Date.now() - 1000);

  const expiredBatch = teacherADb.batch();
  expiredBatch.update(
    teacherADb.collection("activation_codes").doc(EXPIRED_CODE_HASH),
    {
      status: "used",
      usedByUid: TEACHER_A_UID,
      usedAt: now,
    }
  );
  expiredBatch.set(teacherADb.collection("teachers").doc(TEACHER_A_UID), {
    uid: TEACHER_A_UID,
    fullName: "Budi Santoso",
    email: TEACHER_A_EMAIL,
    teachingClass: "X TP 1",
    role: "teacher",
    status: "active",
    activationCodeHash: EXPIRED_CODE_HASH,
    activatedAt: now,
    updatedAt: now,
  });
  await assertFails(expiredBatch.commit());

  // Revoked code
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("activation_codes").doc(REVOKED_CODE_HASH).set({
      codeHash: REVOKED_CODE_HASH,
      codeHint: "GURU-****-8W3Z",
      status: "revoked",
      createdByAdminUid: ADMIN_UID,
      createdAt: new Date(Date.now() - 10000),
      expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000),
      revokedAt: new Date(Date.now() - 5000),
    });
  });

  const revokedBatch = teacherADb.batch();
  revokedBatch.update(
    teacherADb.collection("activation_codes").doc(REVOKED_CODE_HASH),
    {
      status: "used",
      usedByUid: TEACHER_A_UID,
      usedAt: now,
    }
  );
  revokedBatch.set(teacherADb.collection("teachers").doc(TEACHER_A_UID), {
    uid: TEACHER_A_UID,
    fullName: "Budi Santoso",
    email: TEACHER_A_EMAIL,
    teachingClass: "X TP 1",
    role: "teacher",
    status: "active",
    activationCodeHash: REVOKED_CODE_HASH,
    activatedAt: now,
    updatedAt: now,
  });
  await assertFails(revokedBatch.commit());

  // Direct teacher creation without redeeming code in the same transaction MUST FAIL
  await seedActiveCode(VALID_CODE_HASH);
  await assertFails(
    teacherADb.collection("teachers").doc(TEACHER_A_UID).set({
      uid: TEACHER_A_UID,
      fullName: "Bypass Attempt",
      email: TEACHER_A_EMAIL,
      teachingClass: "X TP 1",
      role: "teacher",
      status: "active",
      activationCodeHash: VALID_CODE_HASH,
      activatedAt: now,
      updatedAt: now,
    })
  );
});

// ---------------------------------------------------------------------------
// 6 & 7. Activated Teacher Reads Own Profile Without Code; Cannot Access Admin or Other Teachers
// ---------------------------------------------------------------------------
test("6 & 7. Activated teacher can read own profile and update teachingClass, but cannot access admin or other teachers", async () => {
  await seedTeacher(TEACHER_A_UID, TEACHER_A_EMAIL, "active", VALID_CODE_HASH);
  await seedTeacher(TEACHER_B_UID, TEACHER_B_EMAIL, "active", REVOKED_CODE_HASH);

  const teacherADb = teacherContext(TEACHER_A_UID, TEACHER_A_EMAIL).firestore();

  // Teacher A reads own profile (login without re-entering activation code)
  await assertSucceeds(teacherADb.collection("teachers").doc(TEACHER_A_UID).get());

  // Active teacher A can update own teachingClass
  await assertSucceeds(
    teacherADb.collection("teachers").doc(TEACHER_A_UID).update({
      teachingClass: "X TP 2",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Teacher A CANNOT read Teacher B's private data (PII isolation)
  await assertFails(teacherADb.collection("teachers").doc(TEACHER_B_UID).get());

  // Teacher A CANNOT list all teachers
  await assertFails(teacherADb.collection("teachers").get());

  // Teacher A CANNOT access Admin collection or list/create activation codes
  await assertFails(teacherADb.collection("admins").doc(ADMIN_UID).get());
  await assertFails(teacherADb.collection("activation_codes").get());
  await assertFails(
    teacherADb.collection("activation_codes").doc(EXPIRED_CODE_HASH).set({
      codeHash: EXPIRED_CODE_HASH,
      codeHint: "GURU-****-2Q5V",
      status: "active",
      createdByAdminUid: TEACHER_A_UID,
      createdAt: new Date(Date.now() - 1000),
      expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000),
    })
  );

  // Teacher A CANNOT escalate role to admin
  await assertFails(
    teacherADb.collection("teachers").doc(TEACHER_A_UID).update({
      role: "admin",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

// ---------------------------------------------------------------------------
// 8 & 9. Admin Disables Teacher Account; Disabled Teacher Is Blocked
// ---------------------------------------------------------------------------
test("8 & 9. Admin can disable/reactivate teacher account; disabled teacher cannot self-reactivate or mutate profile", async () => {
  await seedTeacher(TEACHER_A_UID, TEACHER_A_EMAIL, "active", VALID_CODE_HASH);

  const adminDb = adminContext().firestore();
  const teacherADb = teacherContext(TEACHER_A_UID, TEACHER_A_EMAIL).firestore();

  // Admin can list all teachers and disable Teacher A
  await assertSucceeds(adminDb.collection("teachers").get());
  await assertSucceeds(
    adminDb.collection("teachers").doc(TEACHER_A_UID).update({
      status: "disabled",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Disabled Teacher A CANNOT self-reactivate
  await assertFails(
    teacherADb.collection("teachers").doc(TEACHER_A_UID).update({
      status: "active",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Disabled Teacher A CANNOT update teachingClass while disabled
  await assertFails(
    teacherADb.collection("teachers").doc(TEACHER_A_UID).update({
      teachingClass: "X TP 4",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Admin can reactivate Teacher A
  await assertSucceeds(
    adminDb.collection("teachers").doc(TEACHER_A_UID).update({
      status: "active",
      updatedAt: new Date(Date.now() - 100),
    })
  );
});
