package com.mongodb.app

import android.app.Application
import android.util.Log
import com.couchbase.lite.CouchbaseLite

lateinit var app: CBLiteApp

// global Kotlin extension that resolves to the short version
// of the name of the current class. Used for labelling logs.
inline fun <reified T> T.TAG(): String = T::class.java.simpleName

/*
*  Sets up the App and enables Realm-specific logging in debug mode.
*/
class TemplateApp: Application() {

    override fun onCreate() {
        super.onCreate()
        //this must be done to initialize the library for use on Android specifically
        //https://docs.couchbase.com/couchbase-lite/current/android/gs-build.html#sample-code-in-detail
        CouchbaseLite.init(this)

        app = CBLiteApp(getString(R.string.capella_app_endpoint_url), this.filesDir.toString())
        Log.v(TAG(), "Initialized the App with Endpoint URL: ${app.endpointUrl}")
    }
}
