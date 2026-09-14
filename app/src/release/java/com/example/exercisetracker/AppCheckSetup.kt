package com.example.exercisetracker

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

// Release builds prove they're the real app with Play Integrity. This only passes for copies
// installed from Google Play, so don't enforce App Check until the app is published there.
fun appCheckProviderFactory(): AppCheckProviderFactory = PlayIntegrityAppCheckProviderFactory.getInstance()
