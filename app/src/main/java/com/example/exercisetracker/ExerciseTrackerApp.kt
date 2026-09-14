package com.example.exercisetracker

import android.app.Application
import com.google.firebase.appcheck.FirebaseAppCheck

class ExerciseTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // App Check lets Firebase tell requests from this app apart from scripts using the public
        // API key. The provider differs per build type; see AppCheckSetup.kt in src/debug and
        // src/release. Nothing is blocked until enforcement is turned on in the Firebase console.
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(appCheckProviderFactory())
    }
}
