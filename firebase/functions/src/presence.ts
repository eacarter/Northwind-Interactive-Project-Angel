import {
  ACCURACY_MARGIN_FACTOR,
  EvidenceDto,
  MAX_ACCURACY_METERS,
  MAX_CLOCK_SKEW_MS,
  MAX_OBSERVATION_AGE_MS,
  VenueDoc,
} from "./model";

export type PresenceVerdict = "INSIDE" | "OUTSIDE" | "UNVERIFIED";

/** Great-circle distance in meters (haversine), mirroring PresencePolicy.distanceMeters. */
export function distanceMeters(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const R = 6_371_000;
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)));
}

/**
 * Server-side evaluation of untrusted client evidence with bounded freshness.
 * Client geofence detection is not proof of presence: this check, app attestation (App Check),
 * enrollment constraints and abuse signals together decide entitlement. BLE observations are
 * proximity hints only and never satisfy presence on their own.
 */
export function evaluatePresence(evidence: EvidenceDto | null | undefined, venue: VenueDoc, nowMs: number): PresenceVerdict {
  const loc = evidence?.location;
  if (!loc) return "UNVERIFIED";
  if (!Number.isFinite(loc.latitude) || !Number.isFinite(loc.longitude) || !Number.isFinite(loc.accuracyMeters)) {
    return "UNVERIFIED";
  }
  const age = nowMs - loc.observedAtMs;
  if (age > MAX_OBSERVATION_AGE_MS || age < -MAX_CLOCK_SKEW_MS) return "UNVERIFIED";
  if (loc.accuracyMeters <= 0 || loc.accuracyMeters > MAX_ACCURACY_METERS) return "UNVERIFIED";

  const d = distanceMeters(loc.latitude, loc.longitude, venue.latitude, venue.longitude);
  const margin = loc.accuracyMeters * ACCURACY_MARGIN_FACTOR;
  if (d + margin <= venue.radiusMeters) return "INSIDE";
  if (d - margin > venue.radiusMeters) return "OUTSIDE";
  return "UNVERIFIED";
}
