package com.mongodb.app

import com.mongodb.app.data.ConnectionException
import com.mongodb.app.data.InvalidCredentialsException
import com.mongodb.app.domain.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

class CBLiteApp(val endpointUrl: String){
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
