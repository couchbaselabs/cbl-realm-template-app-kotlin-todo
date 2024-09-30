# Conversion Example of MongoDb Atlas Device Sync to Couchbase Lite for Android Developers 

The original todo list application built with the MongoDb Atlas Device [SDK](https://www.mongodb.com/docs/atlas/device-sdks/sdk/kotlin/) and [Atlas Device Sync](https://www.mongodb.com/docs/atlas/app-services/sync/).  This repository provides a converted version of the same application using Couchbase Mobile (Couchbase Lite for Android SDK along with Capella App Services).  The original Atlas Device SDK repository can be found [here](https://github.com/mongodb/template-app-kotlin-todo). 

## Capella Configuration

You will need to have an Couchbase Capella App Services setup prior to running this application.  Directions on how to setup Couchbase Capella App Services can be found in the [Capella.md](./Capella.md) file.

# Android App Conversion 

## Grale File Changes
The Project build.gradle.kts file requires adding the maven location for Couchbase Lite's Kotlin SDK to be added to the list of repositories found under the buildscript section and the repositories under the allprojects section.

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

The Module build.gradle.kts file requires removing the version information for io.realm.kotlin and adding the Couchbase Lite SDK.

```kotlin
    implementation("com.couchbase.lite:couchbase-lite-android-ktx:3.2.0")
```


## App Services Configuration File

The original source code had the configuration for Atlas App Services stored in the atlasConfig.xml file located in the app/src/main/res/values folder.  This file was removed and the configuration for Capella App Services is now located in the capellaConfig.xml file in the app/src/main/res/value folder.  

The App ID is located in `capellaConfig.json`:

```xml
<resources>
  <string name="capella_app_endpoint_url">PUT YOUR APP ENDPOINT URL HERE</string>
</resources>
```

You will need to modify this file and add your Couchbase Capella App Services endpoint URL, which you should have from following the [Capella](./Capella.md) setup directions.

## Updating Domain Model

