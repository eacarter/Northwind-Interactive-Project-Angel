/**
 * Project Angel privileged function contract (spec: "Proposed privileged function contract").
 *
 * The backend is authoritative for registration, entitlement and incident transitions. Every
 * mutation runs here; Firestore rules deny client writes to registrations and incidents.
 */
import { createHash, randomUUID } from "node:crypto";
import { initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore, Transaction } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { CallableRequest, HttpsError, onCall } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import {
  DEFAULT_GRACE_MINUTES,
  EnrollmentMode,
  EvidenceDto,
  IncidentDoc,
  IncidentStatus,
  MAX_ALERTS_PER_10_MIN,
  MAX_SESSION_VALIDITY_MS,
  RegistrationDoc,
  TimelineEvent,
  VenueDoc,
} from "./model";
import { distanceMeters, evaluatePresence } from "./presence";

initializeApp();
const db = getFirestore();

/**
 * App Check: turn on once App Check is configured for both apps
 * (Play Integrity on Android, App Attest / DeviceCheck on iOS).
 */
const ENFORCE_APP_CHECK = false;
const callable = { enforceAppCheck: ENFORCE_APP_CHECK, region: "us-central1" } as const;

// region Helpers

const venueRef = (venueId: string) => db.collection("venues").doc(venueId);
const registrationRef = (uid: string, venueId: string) =>
  db.collection("users").doc(uid).collection("registrations").doc(venueId);
const incidentRef = (incidentId: string) => db.collection("incidents").doc(incidentId);

function requireAuth(request: CallableRequest<unknown>): string {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required", { reason: "NOT_AUTHENTICATED" });
  return uid;
}

function requireString(value: unknown, field: string): string {
  if (typeof value !== "string" || value.length === 0 || value.length > 200) {
    throw new HttpsError("invalid-argument", `Missing ${field}`);
  }
  return value;
}

async function loadVenue(venueId: string, tx?: Transaction): Promise<VenueDoc> {
  const snap = tx ? await tx.get(venueRef(venueId)) : await venueRef(venueId).get();
  const venue = snap.data() as VenueDoc | undefined;
  if (!snap.exists || !venue || venue.active === false) {
    throw new HttpsError("not-found", "Unknown venue", { reason: "UNKNOWN_VENUE" });
  }
  return { ...venue, venueId: snap.id };
}

function requireMode(venue: VenueDoc, mode: EnrollmentMode) {
  if (!venue.enrollmentModes.includes(mode)) {
    throw new HttpsError("permission-denied", "This venue doesn't allow that enrollment method", { reason: "NOT_ENTITLED" });
  }
}

function requireOnSite(venue: VenueDoc, evidence: EvidenceDto | undefined, nowMs: number) {
  if (evidence?.venueId && evidence.venueId !== venue.venueId) {
    throw new HttpsError("failed-precondition", "Evidence is for a different venue", { reason: "PRESENCE_REJECTED" });
  }
  const verdict = evaluatePresence(evidence, venue, nowMs);
  if (verdict !== "INSIDE") {
    throw new HttpsError("failed-precondition", "We can't confirm you're inside the venue", {
      reason: "PRESENCE_REJECTED",
      verdict,
    });
  }
}

function newActiveRegistration(uid: string, venueId: string, mode: EnrollmentMode, previousVersion: number, nowMs: number): RegistrationDoc {
  return {
    id: randomUUID(),
    venueId,
    uid,
    mode,
    status: "ACTIVE_INSIDE",
    registeredAtMs: nowMs,
    exitedAtMs: null,
    expiresAtMs: null,
    maxValidUntilMs: nowMs + MAX_SESSION_VALIDITY_MS,
    version: previousVersion + 1,
  };
}

function assertNotBlocked(existing: RegistrationDoc | undefined) {
  if (existing && (existing.status === "SUSPENDED" || existing.status === "REVOKED")) {
    throw new HttpsError("permission-denied", "Access to this venue has been disabled", { reason: "NOT_ENTITLED" });
  }
}

function hashToken(token: string): string {
  return createHash("sha256").update(token.trim().toUpperCase()).digest("hex");
}

function toVenueDto(v: VenueDoc) {
  return {
    venueId: v.venueId,
    displayName: v.displayName,
    publicAddress: v.publicAddress,
    tagline: v.tagline ?? "",
    latitude: v.latitude,
    longitude: v.longitude,
    radiusMeters: v.radiusMeters,
    enrollmentModes: v.enrollmentModes,
    graceMinutes: v.graceMinutes ?? DEFAULT_GRACE_MINUTES,
    hasBeaconZones: v.hasBeaconZones ?? false,
    beaconNamespace: v.beaconNamespace ?? null,
    zoneLabels: v.zoneLabels ?? {},
  };
}

function event(actor: string, eventType: TimelineEvent["eventType"], serverTimeMs: number): TimelineEvent {
  return { actor, eventType, serverTimeMs };
}

// endregion

// region Venue directory & registration

/** getEligibleVenues: minimal public data only. Never reveals private beacons or staff. */
export const getEligibleVenues = onCall(callable, async (request) => {
  requireAuth(request);
  const { latitude, longitude } = (request.data ?? {}) as { latitude?: number; longitude?: number };
  const snap = await db.collection("venues").where("active", "==", true).limit(200).get();
  let venues = snap.docs.map((d) => ({ ...(d.data() as VenueDoc), venueId: d.id }));
  if (typeof latitude === "number" && typeof longitude === "number") {
    venues = venues.filter((v) => distanceMeters(latitude, longitude, v.latitude, v.longitude) <= 25_000);
  }
  return { venues: venues.map(toVenueDto) };
});

/** joinVenue: public enrollment while confirmed on site. */
export const joinVenue = onCall(callable, async (request) => {
  const uid = requireAuth(request);
  const { venueId, evidence } = request.data as { venueId: string; evidence: EvidenceDto };
  requireString(venueId, "venueId");
  const now = Date.now();
  return db.runTransaction(async (tx) => {
    const venue = await loadVenue(venueId, tx);
    requireMode(venue, "PUBLIC");
    requireOnSite(venue, evidence, now);
    const ref = registrationRef(uid, venueId);
    const existing = (await tx.get(ref)).data() as RegistrationDoc | undefined;
    assertNotBlocked(existing);
    if (existing?.status === "ACTIVE_INSIDE") return existing;
    const registration = newActiveRegistration(uid, venueId, "PUBLIC", existing?.version ?? 0, now);
    tx.set(ref, registration);
    return registration;
  });
});

/** redeemVenueInvite: limited, expiring, server-validated token; only while on site. */
export const redeemVenueInvite = onCall(callable, async (request) => {
  const uid = requireAuth(request);
  const { venueId, token, evidence } = request.data as { venueId: string; token: string; evidence: EvidenceDto };
  requireString(venueId, "venueId");
  requireString(token, "token");
  const now = Date.now();
  return db.runTransaction(async (tx) => {
    const venue = await loadVenue(venueId, tx);
    requireMode(venue, "INVITATION");
    requireOnSite(venue, evidence, now);

    const inviteRef = venueRef(venueId).collection("invites").doc(hashToken(token));
    const invite = (await tx.get(inviteRef)).data() as
      | { expiresAtMs: number; remainingUses: number; revoked?: boolean; requiresApproval?: boolean }
      | undefined;
    if (!invite || invite.revoked || invite.expiresAtMs < now || invite.remainingUses <= 0) {
      throw new HttpsError("invalid-argument", "That invitation code isn't valid or has expired", { reason: "INVALID_INVITATION" });
    }

    const ref = registrationRef(uid, venueId);
    const existing = (await tx.get(ref)).data() as RegistrationDoc | undefined;
    assertNotBlocked(existing);
    if (existing?.status === "ACTIVE_INSIDE") return existing;

    tx.update(inviteRef, { remainingUses: FieldValue.increment(-1), lastRedeemedAtMs: now });
    let registration: RegistrationDoc;
    if (invite.requiresApproval) {
      registration = {
        ...newActiveRegistration(uid, venueId, "INVITATION", existing?.version ?? 0, now),
        status: "PENDING_APPROVAL",
        maxValidUntilMs: null,
      };
      tx.set(venueRef(venueId).collection("accessRequests").doc(uid), { uid, status: "PENDING", requestedAtMs: now, via: "INVITATION" });
    } else {
      registration = newActiveRegistration(uid, venueId, "INVITATION", existing?.version ?? 0, now);
    }
    tx.set(ref, registration);
    return registration;
  });
});

/** requestVenueAccess: on-site request only; creates a pending approval record. */
export const requestVenueAccess = onCall(callable, async (request) => {
  const uid = requireAuth(request);
  const { venueId, evidence } = request.data as { venueId: string; evidence: EvidenceDto };
  requireString(venueId, "venueId");
  const now = Date.now();
  return db.runTransaction(async (tx) => {
    const venue = await loadVenue(venueId, tx);
    requireMode(venue, "APPROVAL");
    requireOnSite(venue, evidence, now);
    const ref = registrationRef(uid, venueId);
    const existing = (await tx.get(ref)).data() as RegistrationDoc | undefined;
    assertNotBlocked(existing);
    if (existing?.status === "ACTIVE_INSIDE" || existing?.status === "PENDING_APPROVAL") return existing;
    const registration: RegistrationDoc = {
      id: randomUUID(),
      venueId,
      uid,
      mode: "APPROVAL",
      status: "PENDING_APPROVAL",
      registeredAtMs: now,
      exitedAtMs: null,
      expiresAtMs: null,
      maxValidUntilMs: null,
      version: (existing?.version ?? 0) + 1,
    };
    tx.set(ref, registration);
    tx.set(venueRef(venueId).collection("accessRequests").doc(uid), { uid, status: "PENDING", requestedAtMs: now, via: "APPROVAL" });
    return registration;
  });
});

/** resumeVenueRegistration: within grace → active; past expiry → expired (new enrollment needed). */
export const resumeVenueRegistration = onCall(callable, async (request) => {
  const uid = requireAuth(request);
  const { venueId, evidence } = request.data as { venueId: string; evidence: EvidenceDto };
  requireString(venueId, "venueId");
  const now = Date.now();
  return db.runTransaction(async (tx) => {
    const venue = await loadVenue(venueId, tx);
    const ref = registrationRef(uid, venueId);
    const existing = (await tx.get(ref)).data() as RegistrationDoc | undefined;
    if (!existing) throw new HttpsError("permission-denied", "Not registered", { reason: "NOT_ENTITLED" });
    requireOnSite(venue, evidence, now);

    if (existing.status !== "GRACE_OUTSIDE") return existing;
    if (existing.expiresAtMs !== null && existing.expiresAtMs <= now) {
      const expired: RegistrationDoc = { ...existing, status: "EXPIRED", version: existing.version + 1 };
      tx.set(ref, expired);
      return expired;
    }
    const resumed: RegistrationDoc = {
      ...existing,
      status: "ACTIVE_INSIDE",
      exitedAtMs: null,
      expiresAtMs: null,
      version: existing.version + 1,
    };
    tx.set(ref, resumed);
    return resumed;
  });
});

/**
 * reportVenueExit: starts the grace period (exitedAt + configured grace, default 300 minutes)
 * and marks open incidents as post-exit. No further location is collected.
 */
export const reportVenueExit = onCall(callable, async (request) => {
  const uid = requireAuth(request);
  const { venueId } = request.data as { venueId: string; evidence?: EvidenceDto };
  requireString(venueId, "venueId");
  const now = Date.now();
  const result = await db.runTransaction(async (tx) => {
    const venue = await loadVenue(venueId, tx);
    const ref = registrationRef(uid, venueId);
    const existing = (await tx.get(ref)).data() as RegistrationDoc | undefined;
    if (!existing) throw new HttpsError("permission-denied", "Not registered", { reason: "NOT_ENTITLED" });
    if (existing.status !== "ACTIVE_INSIDE") return existing;
    const graceMinutes = venue.graceMinutes ?? DEFAULT_GRACE_MINUTES;
    const updated: RegistrationDoc = {
      ...existing,
      status: "GRACE_OUTSIDE",
      exitedAtMs: now,
      expiresAtMs: now + graceMinutes * 60_000,
      version: existing.version + 1,
    };
    tx.set(ref, updated);
    return updated;
  });

  const open = await db
    .collection("incidents")
    .where("uid", "==", uid)
    .where("venueId", "==", venueId)
    .where("status", "in", ["RECEIVED", "ACKNOWLEDGED", "RESPONDING"])
    .get();
  await Promise.all(
    open.docs
      .filter((d) => !(d.data() as IncidentDoc).visitorExited)
      .map((d) =>
        d.ref.update({
          visitorExited: true,
          timeline: FieldValue.arrayUnion(event("system", "VISITOR_EXITED", now)),
        }),
      ),
  );
  return result;
});

// endregion

// region Alerts

/**
 * submitAlert: authenticates, verifies registration, presence freshness and rate limits, then
 * writes the incident and its first timeline event transactionally. A retry with the same
 * requestId returns the same incident. Notification fan-out to staff is separate and retryable.
 */
export const submitAlert = onCall(callable, async (request) => {
  const uid = requireAuth(request);
  const data = request.data as {
    requestId: string;
    venueId: string;
    evidence: EvidenceDto;
    activationMethod: string;
    platform: string;
    osVersion: string;
    appVersion: string;
  };
  const requestId = requireString(data.requestId, "requestId");
  const venueId = requireString(data.venueId, "venueId");
  const now = Date.now();

  const receipt = await db.runTransaction(async (tx) => {
    const idemRef = db.collection("alertRequests").doc(`${uid}_${requestId}`);
    const idem = await tx.get(idemRef);
    if (idem.exists) {
      const incident = (await tx.get(incidentRef(idem.get("incidentId")))).data() as IncidentDoc;
      return { incidentId: incident.incidentId, requestId, receivedAtMs: incident.createdAtMs, status: incident.status, duplicate: true };
    }

    const venue = await loadVenue(venueId, tx);
    const registration = (await tx.get(registrationRef(uid, venueId))).data() as RegistrationDoc | undefined;
    if (registration?.status !== "ACTIVE_INSIDE") {
      throw new HttpsError("permission-denied", "You're not registered for this venue", { reason: "NOT_ENTITLED" });
    }
    if (registration.maxValidUntilMs !== null && registration.maxValidUntilMs < now) {
      throw new HttpsError("permission-denied", "This visit session has expired", { reason: "NOT_ENTITLED" });
    }
    requireOnSite(venue, data.evidence, now);

    const recent = await tx.get(
      db.collection("incidents").where("uid", "==", uid).where("createdAtMs", ">=", now - 10 * 60_000),
    );
    if (recent.size >= MAX_ALERTS_PER_10_MIN) {
      throw new HttpsError("resource-exhausted", "Too many alerts", { reason: "RATE_LIMITED" });
    }

    const strongest = [...(data.evidence.beacons ?? [])]
      .filter((b) => b.zoneId && now - b.observedAtMs <= 30_000)
      .sort((a, b) => b.rssi - a.rssi)[0];
    const zoneLabel = strongest?.zoneId ? venue.zoneLabels?.[strongest.zoneId] : undefined;

    const incidentId = `INC-${randomUUID().slice(0, 8).toUpperCase()}`;
    const incident: IncidentDoc = {
      incidentId,
      requestId,
      uid,
      venueId,
      venueName: venue.displayName,
      status: "RECEIVED",
      createdAtMs: now,
      timeline: [event("visitor", "SUBMITTED", now), event("system", "RECEIVED", now)],
      lastKnownZone: strongest?.zoneId && zoneLabel ? { zoneId: strongest.zoneId, label: zoneLabel, observedAtMs: strongest.observedAtMs } : null,
      lastKnownAtMs: data.evidence.location?.observedAtMs ?? now,
      lastKnownAccuracyMeters: data.evidence.location?.accuracyMeters ?? null,
      lastKnownIsEstimate: true,
      visitorExited: false,
      activationMethod: String(data.activationMethod ?? "IN_APP_HOLD"),
      device: {
        platform: String(data.platform ?? ""),
        osVersion: String(data.osVersion ?? ""),
        appVersion: String(data.appVersion ?? ""),
      },
    };
    tx.set(incidentRef(incidentId), incident);
    tx.set(incidentRef(incidentId).collection("events").doc(), { actor: uid, eventType: "SUBMITTED", serverTimeMs: now });
    tx.set(idemRef, { uid, requestId, incidentId, createdAtMs: now });
    return { incidentId, requestId, receivedAtMs: now, status: "RECEIVED" as IncidentStatus, duplicate: false };
  });

  if (!receipt.duplicate) {
    // Notification delivery (FCM to venue security / console) is initiated separately and is
    // retryable; it never blocks the visitor's receipt.
    await db.collection("notificationQueue").add({ incidentId: receipt.incidentId, venueId, createdAtMs: now, attempts: 0 });
  }
  return { incidentId: receipt.incidentId, requestId: receipt.requestId, receivedAtMs: receipt.receivedAtMs, status: receipt.status };
});

/** getMyIncident: caller must own the incident; returns permitted timeline and location snapshot. */
export const getMyIncident = onCall(callable, async (request) => {
  const uid = requireAuth(request);
  const incidentId = requireString((request.data as { incidentId: string }).incidentId, "incidentId");
  const snap = await incidentRef(incidentId).get();
  const incident = snap.data() as IncidentDoc | undefined;
  if (!incident || incident.uid !== uid) throw new HttpsError("not-found", "Incident not found");
  return {
    incidentId: incident.incidentId,
    requestId: incident.requestId,
    venueId: incident.venueId,
    venueName: incident.venueName,
    status: incident.status,
    createdAtMs: incident.createdAtMs,
    timeline: incident.timeline,
    lastKnownZone: incident.lastKnownZone,
    lastKnownAtMs: incident.lastKnownAtMs,
    visitorExited: incident.visitorExited,
  };
});

// endregion

// region Venue staff (Flutter console)

function requireStaff(request: CallableRequest<unknown>, venueId: string, roles: string[]) {
  const token = request.auth?.token as { role?: string; venueId?: string } | undefined;
  if (!token || !roles.includes(token.role ?? "") || token.venueId !== venueId) {
    throw new HttpsError("permission-denied", "Venue staff only");
  }
}

const STAFF_TRANSITIONS: Record<IncidentStatus, IncidentStatus[]> = {
  RECEIVED: ["ACKNOWLEDGED", "RESPONDING", "RESOLVED"],
  ACKNOWLEDGED: ["RESPONDING", "RESOLVED"],
  RESPONDING: ["RESOLVED"],
  RESOLVED: [],
};

/** Security console: acknowledge / respond / resolve. Continues to work after visitor exit or expiry. */
export const updateIncidentStatus = onCall(callable, async (request) => {
  const { incidentId, status } = request.data as { incidentId: string; status: IncidentStatus };
  requireString(incidentId, "incidentId");
  const now = Date.now();
  return db.runTransaction(async (tx) => {
    const ref = incidentRef(incidentId);
    const incident = (await tx.get(ref)).data() as IncidentDoc | undefined;
    if (!incident) throw new HttpsError("not-found", "Incident not found");
    requireStaff(request, incident.venueId, ["security", "admin"]);
    if (!STAFF_TRANSITIONS[incident.status].includes(status)) {
      throw new HttpsError("failed-precondition", `Cannot move from ${incident.status} to ${status}`);
    }
    const ev = event("security", status as TimelineEvent["eventType"], now);
    tx.update(ref, { status, timeline: FieldValue.arrayUnion(ev) });
    tx.set(ref.collection("events").doc(), { actor: request.auth!.uid, eventType: status, serverTimeMs: now });
    return { incidentId, status };
  });
});

/** Venue admin portal: approve or reject a pending access request. */
export const decideAccessRequest = onCall(callable, async (request) => {
  const { venueId, uid, approve } = request.data as { venueId: string; uid: string; approve: boolean };
  requireString(venueId, "venueId");
  requireString(uid, "uid");
  requireStaff(request, venueId, ["admin"]);
  const now = Date.now();
  return db.runTransaction(async (tx) => {
    const ref = registrationRef(uid, venueId);
    const existing = (await tx.get(ref)).data() as RegistrationDoc | undefined;
    if (existing?.status !== "PENDING_APPROVAL") throw new HttpsError("failed-precondition", "No pending request");
    const updated: RegistrationDoc = approve
      ? { ...existing, status: "ACTIVE_INSIDE", registeredAtMs: now, maxValidUntilMs: now + MAX_SESSION_VALIDITY_MS, version: existing.version + 1 }
      : { ...existing, status: "REJECTED", version: existing.version + 1 };
    tx.set(ref, updated);
    tx.set(venueRef(venueId).collection("accessRequests").doc(uid), { status: approve ? "APPROVED" : "REJECTED", decidedAtMs: now }, { merge: true });
    return updated;
  });
});

// endregion

// region Scheduled expiry

/**
 * Expires registrations whose grace deadline passed. Versioned conditional update: a resumed
 * visit (new version) is never invalidated by a stale expiry pass. Incidents are never deleted.
 */
export const expireRegistrations = onSchedule({ schedule: "every 5 minutes", region: "us-central1" }, async () => {
  const now = Date.now();
  const due = await db
    .collectionGroup("registrations")
    .where("status", "==", "GRACE_OUTSIDE")
    .where("expiresAtMs", "<=", now)
    .limit(500)
    .get();
  let expired = 0;
  for (const doc of due.docs) {
    const seen = doc.data() as RegistrationDoc;
    await db.runTransaction(async (tx) => {
      const latest = (await tx.get(doc.ref)).data() as RegistrationDoc | undefined;
      if (!latest || latest.version !== seen.version || latest.status !== "GRACE_OUTSIDE") return;
      tx.set(doc.ref, { ...latest, status: "EXPIRED", version: latest.version + 1 });
      expired++;
    });
    const open = await db
      .collection("incidents")
      .where("uid", "==", seen.uid)
      .where("venueId", "==", seen.venueId)
      .where("status", "in", ["RECEIVED", "ACKNOWLEDGED", "RESPONDING"])
      .get();
    await Promise.all(
      open.docs.map((d) => d.ref.update({ timeline: FieldValue.arrayUnion(event("system", "REGISTRATION_EXPIRED", now)) })),
    );
  }

  // Safety net for missed exits: active sessions past their maximum validity expire too.
  const stale = await db
    .collectionGroup("registrations")
    .where("status", "==", "ACTIVE_INSIDE")
    .where("maxValidUntilMs", "<=", now)
    .limit(500)
    .get();
  for (const doc of stale.docs) {
    const seen = doc.data() as RegistrationDoc;
    await db.runTransaction(async (tx) => {
      const latest = (await tx.get(doc.ref)).data() as RegistrationDoc | undefined;
      if (!latest || latest.version !== seen.version || latest.status !== "ACTIVE_INSIDE") return;
      tx.set(doc.ref, { ...latest, status: "EXPIRED", version: latest.version + 1 });
      expired++;
    });
  }
  logger.info(`Expired ${expired} registrations`);
});

// endregion
