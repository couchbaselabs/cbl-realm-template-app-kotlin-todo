package com.mongodb.app

import com.couchbase.lite.Database
import com.couchbase.lite.Collection
import com.couchbase.lite.DatabaseConfigurationFactory
import com.couchbase.lite.LogDomain
import com.couchbase.lite.LogLevel
import com.couchbase.lite.Replicator
import com.couchbase.lite.ReplicatorConfigurationFactory
import com.couchbase.lite.ReplicatorType
import com.couchbase.lite.URLEndpoint
import com.couchbase.lite.newConfig

import com.mongodb.app.data.ConnectionException
import com.mongodb.app.data.InvalidCredentialsException
import com.mongodb.app.domain.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Base64
import java.util.Locale

class CBLiteApp(val endpointUrl: String, val filesDir: String){

    var currentUser: User? = null

    suspend fun login(username: String, password: String) {
        if (!isUrlReachable(endpointUrl)) {
            throw ConnectionException("Could not reach the endpoint URL.")
        }
        val url = URL(endpointUrl)
        val connection = withContext(Dispatchers.IO) {
            url.openConnection()
        } as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Content-Type", "application/json")
        val auth = "$username:$password"
        val encodedAuth = Base64.getEncoder().encodeToString(auth.toByteArray())
        connection.setRequestProperty("Authorization", "Basic $encodedAuth")

        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            throw InvalidCredentialsException("Invalid username or password.")
        } else if (responseCode != HttpURLConnection.HTTP_OK) {
            throw Exception("Error: $responseCode")
        }
        currentUser = User(username, password)
    }

    private fun isUrlReachable(urlString: String): Boolean {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.responseCode == HttpURLConnection.HTTP_OK
        } catch (e: Exception) {
            return false
        }
    }
}
