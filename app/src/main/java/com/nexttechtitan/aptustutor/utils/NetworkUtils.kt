package com.nexttechtitan.aptustutor.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** A utility class for checking the device's network connectivity status. */
@Singleton
class NetworkUtils @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun isWifiConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        if (network == null) {
            return false
        }
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        if (capabilities == null) {
            return false
        }
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}