/**
 * Seeds fictional demo venues and an invitation code into a Firebase project or the emulator.
 *
 *   GOOGLE_APPLICATION_CREDENTIALS=service-account.json npm run seed:demo
 *   FIRESTORE_EMULATOR_HOST=localhost:8080 GCLOUD_PROJECT=demo-angel npm run seed:demo
 *
 * Fictional venues and coordinates only (spec presentation note).
 */
import { createHash } from "node:crypto";
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { VenueDoc } from "./model";

initializeApp();
const db = getFirestore();

const venues: VenueDoc[] = [
  {
    venueId: "vesper-lounge",
    displayName: "Vesper Lounge",
    publicAddress: "12 Mercer St, Melbourne",
    tagline: "Cocktails • Music • Late Night",
    latitude: -37.8126,
    longitude: 144.9617,
    radiusMeters: 60,
    enrollmentModes: ["PUBLIC", "INVITATION", "APPROVAL"],
    graceMinutes: 300,
    hasBeaconZones: false,
    zoneLabels: { A1B2C3D4E5F6: "Second Floor Bar" },
    active: true,
  },
  {
    venueId: "harbour-social",
    displayName: "Harbour Social",
    publicAddress: "4 Wharf Rd, Docklands",
    tagline: "Members & guests",
    latitude: -37.8179,
    longitude: 144.946,
    radiusMeters: 80,
    enrollmentModes: ["INVITATION"],
    graceMinutes: 300,
    active: true,
  },
  {
    venueId: "atrium-club",
    displayName: "The Atrium Club",
    publicAddress: "88 Collins St, Melbourne",
    tagline: "Approval required",
    latitude: -37.815,
    longitude: 144.97,
    radiusMeters: 50,
    enrollmentModes: ["APPROVAL"],
    graceMinutes: 300,
    active: true,
  },
];

async function main() {
  const batch = db.batch();
  for (const v of venues) batch.set(db.collection("venues").doc(v.venueId), v);
  const token = "ANGEL-2026";
  const hash = createHash("sha256").update(token).digest("hex");
  for (const venueId of ["vesper-lounge", "harbour-social"]) {
    batch.set(db.collection("venues").doc(venueId).collection("invites").doc(hash), {
      expiresAtMs: Date.now() + 30 * 24 * 60 * 60 * 1000,
      remainingUses: 100,
      revoked: false,
    });
  }
  await batch.commit();
  console.log(`Seeded ${venues.length} venues and invitation code ${token}`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
