package com.nexttechtitan.aptustutor.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkUtils @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "AptusTutorDebug"

    fun isWifiConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        if (network == null) {
            Log.d(TAG, "No active network.")
            return false
        }
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        if (capabilities == null) {
            Log.d(TAG, "No network capabilities found for the active network.")
            return false
        }
        Log.d(TAG, "Network Capabilities: $capabilities")
        Log.d(TAG, "Has WIFI: ${capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)}")
        Log.d(TAG, "Has Cellular: ${capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)}")
        Log.d(TAG, "Has Ethernet: ${capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)}")

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}