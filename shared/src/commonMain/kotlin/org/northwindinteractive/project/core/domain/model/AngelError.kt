package org.northwindinteractive.project.core.domain.model

/**
 * Typed failures surfaced by repositories. Mirrors the backend error codes so the UI can show
 * explicit, testable copy instead of raw exception messages.
 */
sealed class AngelError(message: String) : Exception(message) {
    /** No connectivity / request lost. Safe to retry with the same request id. */
    class Network(message: String = "Network unavailable") : AngelError(message)

    class NotAuthenticated : AngelError("Please sign in again")

    /** Presence evidence was rejected by the backend (stale, outside, imprecise). */
    class PresenceRejected(message: String = "We can't confirm you're inside the venue") : AngelError(message)

    class NotEntitled(message: String = "You're not registered for this venue") : AngelError(message)

    class InvalidInvitation(message: String = "That invitation code isn't valid or has expired") : AngelError(message)

    class RateLimited(message: String = "Too many attempts. Please wait a moment.") : AngelError(message)

    class InvalidCredentials(message: String = "Email or password is incorrect") : AngelError(message)

    class AccountExists(message: String = "An account already exists for that email") : AngelError(message)

    class WeakPassword(message: String = "Use at least 8 characters") : AngelError(message)

    class Unknown(message: String = "Something went wrong. Please try again.") : AngelError(message)
}

fun Throwable.toAngelError(): AngelError = this as? AngelError ?: AngelError.Unknown()
