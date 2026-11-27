package org.northwindinteractive.project.core.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.northwindinteractive.project.core.domain.model.AlertRequest
import org.northwindinteractive.project.core.domain.model.AuthState
import org.northwindinteractive.project.core.domain.model.GeoPoint
import org.northwindinteractive.project.core.domain.model.Incident
import org.northwindinteractive.project.core.domain.model.IncidentReceipt
import org.northwindinteractive.project.core.domain.model.PresenceEvidence
import org.northwindinteractive.project.core.domain.model.UserPreferences
import org.northwindinteractive.project.core.domain.model.UserProfile
import org.northwindinteractive.project.core.domain.model.VenueRegistration
import org.northwindinteractive.project.core.domain.model.VenueSummary

/** Sign-in, session restore and account lifecycle (feature/auth). Backed by Firebase Auth. */
interface AuthRepository {
    val authState: StateFlow<AuthState>
    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun createAccount(email: String, password: String, displayAlias: String?): Result<Unit>
    suspend fun sendPasswordReset(email: String): Result<Unit>
    suspend fun signOut()
}

interface ProfileRepository {
    fun observeProfile(): Flow<UserProfile?>
}

/**
 * Venue discovery and the temporary registration lifecycle (feature/venue).
 * All mutations go through privileged callable functions; the client only reads registrations.
 */
interface VenueRepository {
    /** getEligibleVenues: minimal public directory data near an optional coarse location. */
    suspend fun getEligibleVenues(near: GeoPoint?): Result<List<VenueSummary>>

    /** Realtime listener on the visitor's registrations, keyed by venueId. */
    fun observeRegistrations(): Flow<Map<String, VenueRegistration>>

    /** joinVenue: public enrollment. Requires fresh on-site presence evidence. */
    suspend fun joinVenue(venueId: String, evidence: PresenceEvidence): Result<VenueRegistration>

    /** redeemVenueInvite: one-time token, validated server side, only while on site. */
    suspend fun redeemVenueInvite(venueId: String, token: String, evidence: PresenceEvidence): Result<VenueRegistration>

    /** requestVenueAccess: on-site request creating a PENDING_APPROVAL record. */
    suspend fun requestVenueAccess(venueId: String, evidence: PresenceEvidence): Result<VenueRegistration>

    /** resumeVenueRegistration: within grace → ACTIVE_INSIDE; expired → requires new enrollment. */
    suspend fun resumeVenueRegistration(venueId: String, evidence: PresenceEvidence): Result<VenueRegistration>

    /** reportVenueExit: records an exit so the backend can start the grace period. */
    suspend fun reportVenueExit(venueId: String, evidence: PresenceEvidence): Result<VenueRegistration>
}

/** Alert activation, receipt, history and status timeline (feature/alert). */
interface AlertRepository {
    /** submitAlert: idempotent on [AlertRequest.requestId]. */
    suspend fun submitAlert(request: AlertRequest): Result<IncidentReceipt>

    /** Realtime listener on incidents the visitor owns, newest first. */
    fun observeMyIncidents(): Flow<List<Incident>>

    fun observeIncident(incidentId: String): Flow<Incident?>

    /** getMyIncident: one-shot fetch of permitted timeline and location snapshot. */
    suspend fun getMyIncident(incidentId: String): Result<Incident>
}

/** Local, per-device preferences (theme, reduced motion, onboarding state). */
interface PreferencesRepository {
    val preferences: StateFlow<UserPreferences>
    val hasSeenLocationPrimer: StateFlow<Boolean>
    fun update(transform: (UserPreferences) -> UserPreferences)
    fun markLocationPrimerSeen()
}
