package com.example.exercisetracker

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

// Debug builds can't pass Play Integrity, so they use the debug provider. On first launch it logs a
// debug token (search logcat for "DebugAppCheckProvider"). Add it in the Firebase console under
// App Check > Apps > Manage debug tokens so debug builds keep working once App Check is enforced.
fun appCheckProviderFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
