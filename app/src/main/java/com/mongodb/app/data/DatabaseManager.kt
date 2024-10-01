package com.mongodb.app.data

import android.content.Context
import com.couchbase.lite.*
import com.mongodb.app.domain.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class DatabaseManager(private val context: Context) {
    var itemsDatabase: Database? = null
    var loggedInUser: User? = null
    init {
        //turn on uber logging - in production apps this shouldn't be turn on
        Database.log.console.domains = LogDomain.ALL_DOMAINS
        Database.log.console.level = LogLevel.VERBOSE
    }

    fun closeDatabase() {
        try {
            itemsDatabase?.close()
        } catch (e: java.lang.Exception) {
            android.util.Log.e(e.message, e.stackTraceToString())
        }
    }

    fun initializeDatabase(currentUser: User) {
        try {
            this.loggedInUser = currentUser
            val dbConfig =
                DatabaseConfigurationFactory.newConfig(context.filesDir.toString())
            val sanitizedUsername = currentUser.username
                .replace("@", "-")
                .lowercase(Locale.getDefault())
            val dbName = "items-${sanitizedUsername}"
            this.itemsDatabase = Database(dbName, dbConfig)
        } catch (e: Exception) {
            android.util.Log.e(e.message, e.stackTraceToString())
        }
    }
}