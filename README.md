# Conversion Example of MongoDb Atlas Device Sync to Couchbase Lite for Android Developers 

The original todo list application built with the MongoDb Atlas Device [SDK](https://www.mongodb.com/docs/atlas/device-sdks/sdk/kotlin/) and [Atlas Device Sync](https://www.mongodb.com/docs/atlas/app-services/sync/).  This repository provides a converted version of the same application using Couchbase Mobile (Couchbase Lite for Android SDK along with Capella App Services).  The original Atlas Device SDK repository can be found [here](https://github.com/mongodb/template-app-kotlin-todo). 

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

## Create User Model - Domain

The Couchbase Lite SDK doesn't provide the same user object, so a [new model](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/java/com/mongodb/app/domain/User.kt) was created to represent the user.  

## Create Database Manager 

Manging the Database files should be handled by a seperate class so that if you have multiple repositories you can share the same database instance.  The [DatabaseManager](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/java/com/mongodb/app/data/DatabaseManager.kt) handles the creation of the database, the indexes required for the database, and the creation of the sync configuration. 

## Update the AuthRepository
Two new exceptions where made to handle authentication failure or conductivity issues between Capella App Services and the mobile app.  These exceptions can be found in the [AuthExceptions.kt]() file.

The authentication of the app is handled by the [AuthRepository](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/java/com/mongodb/app/data/AuthRepository.kt#L9) interface and the implementation.  

Registering new users is out of scope of the conversion, so this functionaliy was removed. 

Couchbase Capella App Services will be handling the authentication, so the login function was updated to use the Couchbase Capella App Services REST API to authenticate the user.

## Updating Item Domain Model

The [Item.kt](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/java/com/mongodb/app/domain/Item.kt) file needed modifications to remove the Realm annotations and refactoring of some properties to meet standard Android conventions.  Couchbase Lite supports the use of Kotlin data classes for domain models, so the Item class was converted to a data class.  The Kotlin serialization library makes it easy to convert the data class to a JSON string for storage in Couchbase Lite, to changes were made to the data class to make it serializable by the Kotlin serialization library.

## Updating Sync Repository

A heavy amount of the conversion code is was done in the [SyncRepository](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/java/com/mongodb/app/data/SyncRepository.kt#L26) interface along with the  implementation which was renamed to [CouchbaseSyncRepository](https://github.com/couchbaselabs/cbl-realm-template-app-kotlin-todo/blob/main/app/src/main/java/com/mongodb/app/data/SyncRepository.kt#L82).  This is the class that does full CRUD to the database and enables syncing to Capella App Services.



