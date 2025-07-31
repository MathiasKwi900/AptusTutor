package com.nexttechtitan.aptustutor.utils

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A utility that wraps Android's `PowerManager` to provide a simplified interface
 * for checking the device's thermal status. This helps prevent the app from
 * overheating during intensive AI tasks.
 */
@Singleton
class ThermalManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val powerManager by lazy {
        ContextCompat.getSystemService(context, PowerManager::class.java)
    }

    companion object {
        // If thermal headroom usage is above 75%, we should pause.
        private const val SAFE_THERMAL_HEADROOM = 0.75f
    }

    /**
     * Checks if it's safe to proceed with a heavy computational task.
     * It's safe if the device is not under moderate or severe throttling and has
     * sufficient thermal headroom.
     * @return true if safe to proceed, false otherwise.
     */
    fun isSafeToProceed(): Boolean {
        // If PowerManager is not available, fail open (assume it's safe).
        if (powerManager == null) return true

        // Check currentThermalStatus (Requires API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val status = powerManager.currentThermalStatus
            if (status >= PowerManager.THERMAL_STATUS_MODERATE) {
                return false
            }
        }

        // Check getThermalHeadroom (Requires API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // getThermalHeadroom(0) gives the current forecast.
            // A value closer to 1.0 means closer to severe throttling.
            val headroom = powerManager.getThermalHeadroom(0)

            // NaN indicates the API is not supported or has been called too frequently.
            // Treat unsupported as "safe" to not block functionality.
            if (headroom.isNaN()) return true

            return headroom < SAFE_THERMAL_HEADROOM
        }

        // If on a device with API < 29, we can't check, so we proceed.
        return true
    }

    /**
     * Gets the thermal headroom forecast from the PowerManager.
     * Requires API 30 (Android R) or higher.
     * @return A float representing the headroom (lower is better), or null if not available/supported.
     */
    fun getThermalHeadroom(): Float? {
        if (powerManager == null) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val headroom = powerManager.getThermalHeadroom(0)
            // The API can return NaN if called too frequently or not supported.
            // We treat NaN as "not available" by returning null.
            return if (headroom.isNaN()) null else headroom
        }
        return null
    }
}