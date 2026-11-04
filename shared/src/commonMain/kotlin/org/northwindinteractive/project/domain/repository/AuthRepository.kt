package org.northwindinteractive.project.domain.repository

import org.northwindinteractive.project.domain.models.User


interface AuthRepository {
    val currentUser: User?

    suspend fun signUp(
        email: String,
        password: String,
        keys: Map<String, String>
    ): Result<User>

    suspend fun signIn(
        email: String,
        password: String
    ): Result<User>

    suspend fun signOut()
}