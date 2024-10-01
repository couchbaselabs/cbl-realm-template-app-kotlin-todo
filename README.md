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
        classpath("com.android.tools.build:gradle:7.3.1")
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

## TemplateApp changes

The original source code had the Android [Application](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/java/com/mongodb/app/TemplateApp.kt#L17) inherriting from a custom TemplateApp that creates a local io.realm.kotlin.mongodb.App that is used to reference things in the Realm SDK like authentication and what the current authenticated user is.  This becomes a "singleton" in memory and because of this pattern, it is required to touch most of the code that uses the app reference to be updated. 

This was updated to initialize the Couchbase Lite Library, which is required for Android specifically and should be called before any Intents use the Couchbase Lite SDK for Android.

A CBLiteApp was created to mimic some of the behavior of the io.realm.kotlin.mongodb.App  class to miminimze the amount of code changes required, but most apps should follow modern Android application patterns like using dependency injection and seperation out of features and [SoC](https://en.wikipedia.org/wiki/Separation_of_concerns).  


## Authentication 

The [Couchbase Lite SDK](https://docs.couchbase.com/couchbase-lite/current/android/replication.html#lbl-user-auth) doesn't handle authentication in the same means as the [Realm SDK](https://www.mongodb.com/docs/atlas/device-sdks/sdk/kotlin/users/authenticate-users/#std-label-kotlin-authenticate).  Because of this, some new implementations were added to the app to resolve these differences without having to refactor large chunks of the code.   

>**NOTE**
>Registering new users is out of scope of the conversion, so this functionaliy was removed.  Capella App Services allows the creating of Users per endpoint via the [UI](https://docs.couchbase.com/cloud/app-services/user-management/create-user.html#usermanagement/create-app-role.adoc) or the [REST API](https://docs.couchbase.com/cloud/app-services/references/rest_api_admin.html).  For large scale applications it's highly recommended to use a 3rd party [OpendID Connect](https://docs.couchbase.com/cloud/app-services/user-management/set-up-authentication-provider.html) provider. 
>

### Create User Model - Domain

The Couchbase Lite SDK doesn't provide the same user object for authentication, so a [new model](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/java/com/mongodb/app/domain/User.kt) was created to represent the user. 

### Authentication Exceptions

Two new exceptions where created to mimic the Realm SDK exceptions for authentication: 
- [ConnectionException](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/java/com/mongodb/app/data/AuthExceptions.kt#L4C7-L4C26) - is thrown if the app can't reach the Capella App Services REST API
- [InvalidCredentialsException](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/java/com/mongodb/app/data/AuthExceptions.kt#L3C7-L3C34) - is thrown if the username or password is incorrect 

### Updating the ComposeLoginActivity and LoginViewModel

The ComposeLoginActivity was modified to pull the App Services Endpoint URL from the capellaConfig.xml file and pass it to the LoginViewModel so that the AuthenticationService can use it to authenticate the user. 

The LoginEvent GoToTask was modified to send in the authenticated User model information to the ComposeItemAcitivity so that can be used for authentication for the Sync Repository and Database Manager.  No database can be created or open until we validate that the user is a valid user.

### Handling Authencation of the App

The authentication of the app is handled by the [AuthRepository](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/java/com/mongodb/app/data/AuthRepository.kt#L9) interface.  A new implementation [AppEndpointAuthRepository](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/java/com/mongodb/app/data/AuthRepository.kt#L19) was created to handle the authentication of the app. Authentication is done via the Couchbase Capella App Services Endpoint [REST API](https://docs.couchbase.com/cloud/app-services/references/rest_api_admin.html), validating the username and password provided can authenticate with the endpoint or throwing an exception if they can't. 

If the user is a validate user LoginViewModel login method was updated to pass the user information to the ComposeItemActivity.

### Updating the ComposeItemActivity for DatabaseManager

The ComposeItemActivity was required to be modified to pull out the authenticated user from the intent and pass it to the DatabaseManager so that the database can be created or opened.  Once the intent is memory the DatababaseManager will initialize the database.

Updating the ComposeItemActivity required changes to the TaskViewModel, ToolbarViewModel, SubscriptionTypeViewModel,, ItemContextualMenuViewModel, and AddItemViewModel to take in the repository as a possible nullable.

## Updating Item Domain Model

The [Item.kt](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/java/com/mongodb/app/domain/Item.kt) file needed modifications to remove the Realm annotations and refactoring of some properties to meet standard Android conventions.  Couchbase Lite supports the use of Kotlin data classes for domain models, so the Item class was converted to a data class.  The Kotlin serialization library makes it easy to convert the data class to a JSON string for storage in Couchbase Lite, to changes were made to the data class to make it serializable by the Kotlin serialization library.

## Updating Sync Repository

A heavy amount of the conversion code is was done in the [SyncRepository](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/java/com/mongodb/app/data/SyncRepository.kt#L26) interface along with the  implementation which was renamed to [CouchbaseSyncRepository](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/java/com/mongodb/app/data/SyncRepository.kt#L82).  This is the class that does full CRUD to the database and enables syncing to Capella App Services.



