package com.mongodb.app.data

import com.mongodb.app.app
import io.realm.kotlin.mongodb.Credentials

/**
 * Repository allowing users to create accounts or log in to the app with an existing account.
 */
interface AuthRepository {
    /**
     * Logs in with the specified [email] and [password].
     */
    suspend fun login(email: String, password: String)
}

/**
 * [AuthRepository] for authenticating with Capella App Services
 */
object AppServicesAuthRepository : AuthRepository {

    override suspend fun login(email: String, password: String) {
    }
}
