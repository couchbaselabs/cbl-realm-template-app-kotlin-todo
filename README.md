# Conversion Example of MongoDb Atlas Device Sync to Couchbase Lite for Android Developers 

The original todo list application built with the MongoDb Atlas Device [SDK](https://www.mongodb.com/docs/atlas/device-sdks/sdk/kotlin/) and [Atlas Device Sync](https://www.mongodb.com/docs/atlas/app-services/sync/).  

This repository provides a converted version of the same application using Couchbase Mobile (Couchbase Lite for Android SDK along with Capella App Services).  The original Atlas Device SDK repository can be found [here](https://github.com/mongodb/template-app-kotlin-todo). 

> **NOTE**
>The original application is a basic To Do list.  The original source code has it's own opinionated way of implementing an Android application and communicating between different layers.  This conversion is by no means a best practice for Android development or a show case on how to properly communicate between layers of an application.  It's more of an example of the process that a developer will have to go through to convert an application from one SDK to another.
>

## Capella Configuration

You will need to have an Couchbase Capella App Services setup prior to running this application.  Directions on how to setup Couchbase Capella App Services can be found in the [Capella.md](./Capella.md) file.

# Android App Conversion 

## Grale File Changes
The Project [build.gradle.kts](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/build.gradle.kts) file required adding the maven location for Couchbase Lite's Kotlin SDK to be added to the list of repositories found under the buildscript section and the repositories under the allprojects section.

```kotlin
buildscript {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven(url = "https://mobile.maven.couchbase.com/maven2/dev/")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.5.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.20")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://mobile.maven.couchbase.com/maven2/dev/")
    }
}
```

Other gradle file changes were made to update the applications gradle version and allow the use of the Kotlin serialization library.  

The Module [build.gradle.kts](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/build.gradle.kts) file requires removing the version information for io.realm.kotlin and adding the Couchbase Lite SDK.

```kotlin
    implementation("com.couchbase.lite:couchbase-lite-android-ktx:3.2.0")
```

## App Services Configuration File

The original source code had the configuration for Atlas App Services stored in the atlasConfig.xml file located in the app/src/main/res/values folder.  This file was removed and the configuration for Capella App Services is now located in the [capellaConfig.xml](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/res/values/capellaConfig.xml) file in the app/src/main/res/value folder.  

```xml
<resources>
  <string name="capella_app_endpoint_url">PUT YOUR APP ENDPOINT URL HERE</string>
</resources>
```

You will need to modify this file and add your Couchbase Capella App Services endpoint URL, which you should have from following the [Capella](./Capella.md) setup directions.

## TemplateApp changes and CBLiteApp

The original source code had the Android [Application](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/java/com/mongodb/app/TemplateApp.kt#L17) inherriting from a custom TemplateApp that creates a local io.realm.kotlin.mongodb.App that is used to reference features in the Realm SDK like authentication and whom is the current authenticated user.  This becomes a global variable for the entire app and because of this pattern, it is required to touch most of the code that uses the app reference to be updated. 

The TemplateApp was updated to [initialize](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/java/com/mongodb/app/TemplateApp.kt#L22) the Couchbase Lite SDK, which is required for Android specifically and should be called before any Intents use the Couchbase Lite SDK for Android.

```kotlin
 override fun onCreate() {
        super.onCreate()

        CouchbaseLite.init(this)

        app = CBLiteApp(getString(R.string.capella_app_endpoint_url))
        Log.v(TAG(), "Initialized the App with Endpoint URL: ${app.endpointUrl}")
    }
```
## Authentication 

The [Couchbase Lite SDK](https://docs.couchbase.com/couchbase-lite/current/android/replication.html#lbl-user-auth) doesn't handle authentication in the same means as the [Realm SDK](https://www.mongodb.com/docs/atlas/device-sdks/sdk/kotlin/users/authenticate-users/#std-label-kotlin-authenticate).    

### Handling Authencation of the App

The authentication of the app is handled by the [AuthRepository](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/java/com/mongodb/app/data/AuthRepository.kt#L9) interface.  The implementation [AppEndpointAuthRepository](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/java/com/mongodb/app/data/AuthRepository.kt#L19) was renamed to [AppServicesAuthRepository](AppServicesAuthRepository). 

Authentication process is done via the Couchbase Capella App Services Endpoint [REST API](https://docs.couchbase.com/cloud/app-services/references/rest_api_admin.html) in the CBLiteApp [login function](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/java/com/mongodb/app/CBLiteApp.kt#L15), validating the username and password provided can authenticate with the endpoint or throwing an exception if they can't. The decision to put the login method in CBLiteApp was made so it matched Realm's SDK pattern and thus having to change less code. 

The login method was added to the [CBLiteApp](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/java/com/mongodb/app/CBLiteApp.kt#L15) to resolve the SDK differences between Realm SDK and Couchbase Lite SDK without having to refactor large chunks of code. 

>**NOTE**
>Registering new users is out of scope of the conversion, so this functionaliy was removed.  Capella App Services allows the creating of Users per endpoint via the [UI](https://docs.couchbase.com/cloud/app-services/user-management/create-user.html#usermanagement/create-app-role.adoc) or the [REST API](https://docs.couchbase.com/cloud/app-services/references/rest_api_admin.html).  For large scale applications it's highly recommended to use a 3rd party [OpendID Connect](https://docs.couchbase.com/cloud/app-services/user-management/set-up-authentication-provider.html) provider. 
>

### Authentication Exceptions

Two new exceptions where created to mimic the Realm SDK exceptions for authentication: 
- [ConnectionException](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/java/com/mongodb/app/data/AuthExceptions.kt#L4C7-L4C26) - is thrown if the app can't reach the Capella App Services REST API
- [InvalidCredentialsException](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/java/com/mongodb/app/data/AuthExceptions.kt#L3C7-L3C34) - is thrown if the username or password is incorrect 

### Create User Model

The Couchbase Lite SDK doesn't provide the same user object for tracking the authenticated user, so a [new model](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/java/com/mongodb/app/domain/User.kt) was created. 

## Updating Item Domain Model

The [Item.kt](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/java/com/mongodb/app/domain/Item.kt) file needed modifications to remove the Realm annotations and refactoring of some properties to meet standard Android conventions.  

Couchbase Lite supports the use of Kotlin data classes for domain models, so the Item class was converted to a data class.  The Kotlin serialization library makes it easy to convert the data class to a JSON string for storage in Couchbase Lite, to changes were made to the data class to make it serializable by the Kotlin serialization library.

Finally, a DAO(Data Access Object) was created to help with the deserialization of the Query Results that come back from a SQL++ QueryChange object without needing to further change the ViewModels.


## Updating Sync Repository

A heavy amount of the conversion code is was done in the [SyncRepository](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/java/com/mongodb/app/data/SyncRepository.kt#L26) interface along with the  implementation which was renamed to [CouchbaseSyncRepository](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/java/com/mongodb/app/data/SyncRepository.kt#L82).  This is the class that does full CRUD to the database and enables syncing to Capella App Services.

### Sync Configuration

Most changes for implementing Couchbase Lie where done in the [SyncRepository](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/java/com/mongodb/app/data/SyncRepository.kt) file.

A new [CouchbaseSyncRepository](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/java/com/mongodb/app/data/SyncRepository.kt) class was created that implements the original [SyncRepository](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/java/com/mongodb/app/data/SyncRepository.kt#L18) interface with as little changes as possible to limit the amount of code that is required to change.

Couchbase Lite doesn't suport the [ResultsChange](https://www.mongodb.com/docs/realm-sdks/kotlin/1.0.1/library-base/-realm%20-kotlin%20-s-d-k/io.realm.kotlin.notifications/-results-change/index.html) interfaces and pattern that Realm provides for tracking changes in a Realm.  Instead Couchbase Lite has a QueryChange via the [LiveQuery](https://docs.couchbase.com/couchbase-lite/current/android/query-live.html#activating-a-live-query).  If any information in the query change, the query is ran again and the results presented.  Couchbase Lite for Android with Kotlin specifically supports the Flow Co-Routine API for handling the [LiveQuery](https://docs.couchbase.com/couchbase-lite/current/android/query-live.html#activating-a-live-query) results which will return a [QueryChange](https://docs.couchbase.com/mobile/3.2.0/couchbase-lite-android/com/couchbase/lite/QueryChange.html) that allows you to manitpluate the results before returning them.

Developers should review the documentation on Ordering of replication events in the [Couchbase Lite SDK documentation](https://docs.couchbase.com/couchbase-lite/current/android/replication.html#lbl-repl-ord) prior to making decisions on how to setup the replicator in environments with heavy replication traffic.




