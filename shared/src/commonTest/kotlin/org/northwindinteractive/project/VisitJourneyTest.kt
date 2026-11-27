package org.northwindinteractive.project

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.northwindinteractive.project.core.data.demo.DemoAlertRepository
import org.northwindinteractive.project.core.data.demo.DemoAuthRepository
import org.northwindinteractive.project.core.data.demo.DemoBackend
import org.northwindinteractive.project.core.data.demo.DemoVenueRepository
import org.northwindinteractive.project.core.domain.model.ActivationMethod
import org.northwindinteractive.project.core.domain.model.AngelError
import org.northwindinteractive.project.core.domain.model.AuthState
import org.northwindinteractive.project.core.domain.model.EnrollmentMode
import org.northwindinteractive.project.core.domain.model.IncidentStatus
import org.northwindinteractive.project.core.domain.model.RegistrationStatus
import org.northwindinteractive.project.core.domain.session.AlertSubmitter
import org.northwindinteractive.project.core.domain.session.SubmissionState
import org.northwindinteractive.project.core.domain.session.VenueSessionCoordinator
import org.northwindinteractive.project.core.domain.session.VisitPhase
import org.northwindinteractive.project.core.platform.Haptics
import org.northwindinteractive.project.core.platform.NoBeaconScanner
import org.northwindinteractive.project.core.platform.PlatformInfo
import org.northwindinteractive.project.core.platform.SecureStore
import org.northwindinteractive.project.core.platform.SimulatedPresenceService
import org.northwindinteractive.project.core.platform.SimulatedPresenceService.Scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * End-to-end visit lifecycle against the demo backend (which enforces the server rules):
 * sign in → permission → join on site → alert → acknowledgement → exit → grace → reentry → expiry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VisitJourneyTest {

    private class CountingHaptics : Haptics {
        var confirms = 0
        override fun confirm() { confirms++ }
        override fun tick() = Unit
        override fun warning() = Unit
    }

    private class Harness(scope: TestScope) {
        val clock = object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(1_800_000_000_000 + scope.testScheduler.currentTime)
        }
        val store = object : SecureStore {
            val map = mutableMapOf<String, Any>()
            override fun getString(key: String) = map[key] as? String
            override fun putString(key: String, value: String) { map[key] = value }
            override fun getBoolean(key: String) = map[key] as? Boolean
            override fun putBoolean(key: String, value: Boolean) { map[key] = value }
            override fun remove(key: String) { map.remove(key) }
        }
        val haptics = CountingHaptics()
        val platform = object : PlatformInfo {
            override val platformName = "Test"
            override val osVersion = "1"
            override val appVersion = "0"
            override fun openAppSettings() = Unit
            override fun openLocationSettings() = Unit
        }
        val backend = DemoBackend(clock, scope.backgroundScope)
        val presence = SimulatedPresenceService(clock)
        val auth = DemoAuthRepository(backend, store, scope.backgroundScope)
        val venues = DemoVenueRepository(backend)
        val alerts = DemoAlertRepository(backend)
        val coordinator = VenueSessionCoordinator(auth, venues, alerts, presence, NoBeaconScanner, clock, scope.backgroundScope)
        val submitter = AlertSubmitter(coordinator, alerts, platform, haptics, clock)
        val vesper get() = backend.venues.first { it.venueId == "vesper-lounge" }
    }

    private suspend fun TestScope.settle() {
        advanceTimeBy(2.seconds)
        runCurrent()
    }

    @Test
    fun fullVisitLifecycle() = runTest {
        val h = Harness(this)
        h.presence.setScenario(Scenario.InsideVenue(h.vesper))

        h.auth.createAccount("visitor@example.com", "correct-horse", null).getOrThrow()
        h.auth.authState.first { it is AuthState.SignedIn }
        settle()
        assertEquals(VisitPhase.PERMISSION_REQUIRED, h.coordinator.state.value.phase)

        h.coordinator.requestLocationPermission()
        settle()
        assertEquals(VisitPhase.INSIDE_NOT_JOINED, h.coordinator.state.value.phase)

        // Alerts are refused before registration, without contacting the backend.
        assertIs<SubmissionState.Blocked>(h.submitter.submit(ActivationMethod.IN_APP_HOLD))

        h.coordinator.enroll(EnrollmentMode.PUBLIC).getOrThrow()
        settle()
        val active = h.coordinator.state.value
        assertEquals(VisitPhase.INSIDE_ACTIVE, active.phase)
        assertTrue(active.canSendAlert)

        // One alert, receipt distinct from acknowledgement.
        val sent = h.submitter.submit(ActivationMethod.IN_APP_HOLD)
        val receipt = assertIs<SubmissionState.Received>(sent).receipt
        assertEquals(IncidentStatus.RECEIVED, receipt.status)
        assertEquals(1, h.haptics.confirms)
        settle()
        assertEquals(IncidentStatus.RECEIVED, h.coordinator.state.value.openIncident?.status)
        advanceTimeBy(7.seconds); runCurrent()
        assertEquals(IncidentStatus.ACKNOWLEDGED, h.coordinator.state.value.openIncident?.status)

        // Exit: venue actions lock immediately and the backend starts the grace period.
        h.presence.setScenario(Scenario.OutsideCoverage)
        settle()
        settle()
        val exited = h.coordinator.state.value
        assertEquals(VisitPhase.EXITED_GRACE, exited.phase)
        assertEquals(RegistrationStatus.GRACE_OUTSIDE, exited.registration?.status)
        assertNotNull(exited.registration?.expiresAt)
        assertTrue(!exited.canSendAlert)
        // The existing incident stays open and is marked as post-exit.
        val incident = exited.incidents.first { it.incidentId == receipt.incidentId }
        assertTrue(incident.isOpen)
        assertTrue(incident.visitorExited)

        // Reentry before expiry restores access only after backend verification.
        h.presence.setScenario(Scenario.InsideVenue(h.vesper))
        settle()
        settle()
        assertEquals(VisitPhase.INSIDE_ACTIVE, h.coordinator.state.value.phase)
        assertEquals(RegistrationStatus.ACTIVE_INSIDE, h.coordinator.state.value.registration?.status)
    }

    @Test
    fun graceExpiryRequiresNewRegistration() = runTest {
        val h = Harness(this)
        h.backend.updateControls { it.copy(graceOverride = 2.minutes) }
        h.presence.setScenario(Scenario.InsideVenue(h.vesper))
        h.presence.setPermission(org.northwindinteractive.project.core.domain.model.LocationPermission.GRANTED_FOREGROUND)
        h.auth.createAccount("visitor@example.com", "correct-horse", null).getOrThrow()
        settle()
        h.coordinator.enroll(EnrollmentMode.PUBLIC).getOrThrow()
        settle()

        h.presence.setScenario(Scenario.OutsideCoverage)
        settle()
        settle()
        assertEquals(VisitPhase.EXITED_GRACE, h.coordinator.state.value.phase)

        advanceTimeBy(3.minutes); runCurrent()
        assertEquals(VisitPhase.EXPIRED, h.coordinator.state.value.phase)

        // Back on site after expiry: must register again (no automatic renewal).
        h.presence.setScenario(Scenario.InsideVenue(h.vesper))
        settle()
        settle()
        assertEquals(VisitPhase.EXPIRED, h.coordinator.state.value.phase)
        assertTrue(!h.coordinator.state.value.canSendAlert)
    }

    @Test
    fun retryReusesRequestIdAndServerDeduplicates() = runTest {
        val h = Harness(this)
        h.presence.setScenario(Scenario.InsideVenue(h.vesper))
        h.presence.setPermission(org.northwindinteractive.project.core.domain.model.LocationPermission.GRANTED_FOREGROUND)
        h.auth.createAccount("visitor@example.com", "correct-horse", null).getOrThrow()
        settle()
        h.coordinator.enroll(EnrollmentMode.PUBLIC).getOrThrow()
        settle()

        h.backend.updateControls { it.copy(offline = true) }
        val unsent = assertIs<SubmissionState.Unsent>(h.submitter.submit(ActivationMethod.APP_INTENT))
        assertIs<AngelError.Network>(unsent.error)

        h.backend.updateControls { it.copy(offline = false) }
        val first = assertIs<SubmissionState.Received>(h.submitter.retry()).receipt
        assertEquals(unsent.requestId, first.requestId)

        // A lost response retried with the same id returns the same incident.
        val again = h.backend.submitAlert(
            org.northwindinteractive.project.core.domain.model.AlertRequest(
                requestId = first.requestId,
                venueId = "vesper-lounge",
                evidence = h.coordinator.freshEvidence("vesper-lounge"),
                activationMethod = ActivationMethod.APP_INTENT,
                deviceContext = org.northwindinteractive.project.core.domain.model.DeviceContext("t", "1", "0"),
            ),
        )
        assertEquals(first.incidentId, again.incidentId)
        settle()
        assertEquals(1, h.coordinator.state.value.incidents.size)
    }

    @Test
    fun invitationAndApprovalModesAreValidatedServerSide() = runTest {
        val h = Harness(this)
        val harbour = h.backend.venues.first { it.venueId == "harbour-social" }
        val atrium = h.backend.venues.first { it.venueId == "atrium-club" }
        h.presence.setPermission(org.northwindinteractive.project.core.domain.model.LocationPermission.GRANTED_FOREGROUND)
        h.auth.createAccount("visitor@example.com", "correct-horse", null).getOrThrow()

        h.presence.setScenario(Scenario.InsideVenue(harbour))
        settle()
        assertIs<AngelError.NotEntitled>(h.coordinator.enroll(EnrollmentMode.PUBLIC).exceptionOrNull())
        assertIs<AngelError.InvalidInvitation>(h.coordinator.enroll(EnrollmentMode.INVITATION, "WRONG").exceptionOrNull())
        assertEquals(RegistrationStatus.ACTIVE_INSIDE, h.coordinator.enroll(EnrollmentMode.INVITATION, DemoBackend.DEMO_INVITE_CODE).getOrThrow().status)

        h.presence.setScenario(Scenario.InsideVenue(atrium))
        settle()
        assertEquals(RegistrationStatus.PENDING_APPROVAL, h.coordinator.enroll(EnrollmentMode.APPROVAL).getOrThrow().status)
        settle()
        assertEquals(VisitPhase.PENDING_APPROVAL, h.coordinator.state.value.phase)
        assertTrue(!h.coordinator.state.value.canSendAlert)
        advanceTimeBy(12.seconds); runCurrent()
        assertEquals(VisitPhase.INSIDE_ACTIVE, h.coordinator.state.value.phase)
    }

    @Test
    fun enrollmentRequiresOnSitePresence() = runTest {
        val h = Harness(this)
        h.presence.setPermission(org.northwindinteractive.project.core.domain.model.LocationPermission.GRANTED_FOREGROUND)
        h.presence.setScenario(Scenario.Imprecise(h.vesper))
        h.auth.createAccount("visitor@example.com", "correct-horse", null).getOrThrow()
        settle()
        assertEquals(VisitPhase.LOCATION_UNKNOWN, h.coordinator.state.value.phase)
        assertIs<AngelError.PresenceRejected>(h.coordinator.enroll(EnrollmentMode.PUBLIC).exceptionOrNull())
    }
}
