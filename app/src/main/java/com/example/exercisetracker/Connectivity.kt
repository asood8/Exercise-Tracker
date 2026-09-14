package com.example.exercisetracker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object Connectivity {
    // True when there's a network that Android has confirmed actually reaches the internet
    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
