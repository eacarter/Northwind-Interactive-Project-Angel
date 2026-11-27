# Project Angel — KMP Mobile Application

Project Angel is a venue-based, discretionary safety-alert platform. Visitors at participating
venues can discreetly alert venue security. Alerts only work while the visitor is physically inside
the venue geofence. **It is not a replacement for emergency services.**

This repository holds the visitor mobile app (Kotlin Multiplatform + Compose Multiplatform for
Android and iOS) and the Firebase backend it calls (`/firebase`). The security console is a
separate Flutter web portal.

## Running it

The app picks its backend automatically:

| Firebase config present? | Backend | Location |
|---|---|---|
| No (default) | **Demo backend**: in-memory simulation of the Cloud Functions contract | Simulated, controllable from *Settings → Demo controls* |
| Yes | **Firebase** (Auth, Firestore listeners, callable Functions) | Real device location |

### Android
```bash
./gradlew :androidApp:installDebug
```

### iOS
Open `iosApp/iosApp.xcodeproj` in Xcode and run. The Xcode build phase calls
`./gradlew :shared:embedAndSignAppleFrameworkForXcode`.

> **iOS requires the Firebase iOS SDK to link** because the shared framework uses the GitLive
> Firebase SDK (see *Connecting Firebase* below). This applies even in demo mode.

### Demo walkthrough
1. Create an account (any email, password ≥ 8 characters).
2. Allow location access. The simulated visitor starts inside **Vesper Lounge**.
3. Register (Public), or try **Invite** with code `ANGEL-2026`, or **Approval** (auto-approved after ~10 s).
4. Press and hold **Send Alert** for 2 seconds. Simulated security acknowledges after ~6 s and responds after ~16 s.
5. In *Settings → Demo controls*, move the visitor outside, toggle offline mode, shorten the grace period to 2 minutes, and so on.

## Architecture

```
shared/src/commonMain/kotlin/org/northwindinteractive/project/
├── App.kt                      Root composable, auth-gated navigation, shortcut handling
├── core/
│   ├── domain/model            UserProfile, VenueSummary, VenueRegistration, VenuePresence,
│   │                           BeaconObservation, AlertRequest, IncidentReceipt, AlertEvent…
│   ├── domain/policy           PresencePolicy, EligibilityPolicy, GracePolicy (pure, unit-tested)
│   ├── domain/repository       Auth / Profile / Venue / Alert / Preferences contracts
│   ├── domain/session          VenueSessionCoordinator (visit lifecycle), AlertSubmitter (idempotent)
│   ├── data/firebase           GitLive Firebase implementations + wire DTOs
│   ├── data/demo               DemoBackend enforcing the same server rules, DemoControls
│   ├── platform                PresenceService, BeaconScanner (Kable/Eddystone), Haptics,
│   │                           SecureStore, ShortcutDispatcher, SimulatedPresenceService
│   └── designsystem            Light/dark tokens from the spec, shared components
├── feature/auth                Welcome, Sign in, Create account
├── feature/venue               Home hub (one state per VisitPhase), registration, map card
├── feature/alert               Hold-to-send control, alert details timeline, history
├── feature/settings            Preferences & privacy, demo controls
└── di                          Koin modules (core, backend, view models)

shared/src/androidMain          Fused location + geofencing, permission bridge, haptics, KVault,
                                Quick Settings tile, launcher shortcut intent handling
shared/src/iosMain              Core Location + region monitoring, haptics, Keychain (KVault)
iosApp/                         SwiftUI host, App Intent "Send Discreet Alert" (Shortcuts / Back Tap)
firebase/                       Cloud Functions, Firestore rules and indexes, demo seed script
```

### Visit lifecycle

`VenueSessionCoordinator` combines OS presence observations with the backend registration record:

```
Outside → Inside·not joined → Inside·active → Exited·grace → Reentry before expiry → Inside·active
                                                          ↘ Expiry → rejoin required
```

* Registration and alerts require **fresh** (≤ 2 min), **precise** (≤ 100 m) evidence that the
  visitor is inside the geofence. Stale INSIDE state is never treated as confirmed.
* An exit locks venue actions immediately on the device, then `reportVenueExit` starts the grace
  period on the server (`exitedAt + graceMinutes`, default 300).
* **Grace keeps the registration, not alert eligibility.** Reentry restores access only after
  `resumeVenueRegistration` verifies presence.
* Existing incidents remain open for security after exit or expiry. The app shows the last known
  location as a historical estimate.

### Alert submission
1. A press-and-hold (2 s, with a progress ring; releasing early cancels) or a native shortcut requests an alert.
2. `AlertSubmitter` creates a UUID `requestId`, refreshes the location fix and re-checks eligibility locally.
3. `submitAlert` re-validates registration, presence freshness and rate limits, then writes the incident transactionally.
4. The UI shows **Sent → Received by Project Angel → Acknowledged → Responding**. A server receipt is never shown as staff acknowledgement.
5. Network failures show *Alert not sent*. A retry reuses the same `requestId`, so the server deduplicates.

### Discreet shortcuts (M6)
* **iOS**: the `SendDiscreetAlertIntent` App Intent works from Shortcuts, Siri and the Action button, and Back Tap can be assigned to it.
* **Android**: a launcher shortcut ("Send discreet alert") and a Quick Settings tile ("Angel alert").
* Shortcuts never bypass the on-site check. If you're signed out, the request is dropped.

## Connecting Firebase

1. Create a Firebase project. Enable **Email/Password** auth, **Firestore** and **Cloud Functions** (Blaze plan).
2. **Android**: register app id `org.northwindinteractive.project`, then put `google-services.json` in `androidApp/`. The Gradle plugin is applied automatically when the file exists, and the file is git-ignored.
3. **iOS**: register bundle id `org.northwindinteractive.project.Project-Angel` and add `GoogleService-Info.plist` to the `iosApp` target. In Xcode → *File → Add Package Dependencies…*, add `https://github.com/firebase/firebase-ios-sdk` and link `FirebaseAuth`, `FirebaseFirestore` and `FirebaseFunctions` to the app target.
4. **Backend**:
   ```bash
   cd firebase/functions && npm install
   ```
   ```bash
   firebase use <your-project-id>
   ```
   ```bash
   npm run deploy
   ```
   ```bash
   GOOGLE_APPLICATION_CREDENTIALS=<service-account.json> npm run seed:demo
   ```
5. Optional: enable App Check (Play Integrity / App Attest) and set `ENFORCE_APP_CHECK = true` in `firebase/functions/src/index.ts`.

The security console uses custom claims `{ role: "security" | "admin", venueId }`. It calls
`updateIncidentStatus` and `decideAccessRequest`.

## Tests

```bash
./gradlew :shared:testAndroidHostTest
```

* `PolicyTest`: presence freshness, accuracy, boundary ambiguity, overlapping venues, eligibility, grace maths.
* `VisitJourneyTest`: covers the full lifecycle: join, alert, acknowledgement, exit, grace, reentry and expiry. It also checks idempotent retries and invitation and approval modes.
* `ScreenshotTour` (opt-in): `./gradlew :shared:testAndroidHostTest -Pangel.screenshots --tests "*ScreenshotTour*"` renders the main screens with Robolectric to `shared/build/screenshots` (light and dark).

iOS Kotlin sources can be compile-checked on any host:
```bash
./gradlew :shared:compileKotlinIosSimulatorArm64
```

## Open product decisions (from the spec)
* Supported OS minimums: Android minSdk 28; iOS deployment target 18.2.
* Overlapping geofences: the venue whose centre is relatively closest wins (`PresencePolicy`).
* Alert withdrawal / cancel is **not** in the MVP (needs a separate safety review).
* Approval persistence across visits is a venue policy. The backend currently requires a new request after expiry.
