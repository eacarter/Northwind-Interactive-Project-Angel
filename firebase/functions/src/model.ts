/**
 * Wire format shared with the KMP client (shared/.../core/data/firebase/Dtos.kt).
 * Timestamps are epoch milliseconds issued by the server wherever they matter.
 */

export type EnrollmentMode = "PUBLIC" | "INVITATION" | "APPROVAL";

export type RegistrationStatus =
  | "NOT_REGISTERED"
  | "PENDING_APPROVAL"
  | "ACTIVE_INSIDE"
  | "GRACE_OUTSIDE"
  | "EXPIRED"
  | "REJECTED"
  | "SUSPENDED"
  | "REVOKED";

export type IncidentStatus = "RECEIVED" | "ACKNOWLEDGED" | "RESPONDING" | "RESOLVED";

export type AlertEventType =
  | "SUBMITTED"
  | "RECEIVED"
  | "ACKNOWLEDGED"
  | "RESPONDING"
  | "RESOLVED"
  | "VISITOR_EXITED"
  | "REGISTRATION_EXPIRED";

/** Public directory fields (venues/{venueId}). Private beacon/staff data lives elsewhere. */
export interface VenueDoc {
  venueId: string;
  displayName: string;
  publicAddress: string;
  tagline?: string;
  latitude: number;
  longitude: number;
  radiusMeters: number;
  enrollmentModes: EnrollmentMode[];
  graceMinutes?: number;
  hasBeaconZones?: boolean;
  beaconNamespace?: string | null;
  zoneLabels?: Record<string, string>;
  active?: boolean;
}

export interface RegistrationDoc {
  id: string;
  venueId: string;
  uid: string;
  mode: EnrollmentMode;
  status: RegistrationStatus;
  registeredAtMs: number | null;
  exitedAtMs: number | null;
  expiresAtMs: number | null;
  maxValidUntilMs: number | null;
  version: number;
}

export interface LocationDto {
  latitude: number;
  longitude: number;
  accuracyMeters: number;
  observedAtMs: number;
  source: string;
}

export interface BeaconDto {
  beaconId: string;
  zoneId: string | null;
  rssi: number;
  observedAtMs: number;
}

export interface EvidenceDto {
  venueId: string;
  location: LocationDto | null;
  beacons: BeaconDto[];
  clientState: string;
  capturedAtMs: number;
}

export interface TimelineEvent {
  actor: string;
  eventType: AlertEventType;
  serverTimeMs: number;
}

export interface ZoneDto {
  zoneId: string;
  label: string;
  observedAtMs: number;
}

export interface IncidentDoc {
  incidentId: string;
  requestId: string;
  uid: string;
  venueId: string;
  venueName: string;
  status: IncidentStatus;
  createdAtMs: number;
  timeline: TimelineEvent[];
  lastKnownZone: ZoneDto | null;
  lastKnownAtMs: number | null;
  /** Accuracy and estimate flags kept for responders; never presented as a live fix. */
  lastKnownAccuracyMeters: number | null;
  lastKnownIsEstimate: boolean;
  visitorExited: boolean;
  activationMethod: string;
  device: { platform: string; osVersion: string; appVersion: string };
}

export const DEFAULT_GRACE_MINUTES = 300;
export const MAX_SESSION_VALIDITY_MS = 12 * 60 * 60 * 1000;
export const MAX_OBSERVATION_AGE_MS = 2 * 60 * 1000;
export const MAX_CLOCK_SKEW_MS = 30 * 1000;
export const MAX_ACCURACY_METERS = 100;
export const ACCURACY_MARGIN_FACTOR = 0.5;
export const MAX_ALERTS_PER_10_MIN = 5;
