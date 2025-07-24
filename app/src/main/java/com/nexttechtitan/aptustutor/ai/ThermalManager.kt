package com.nexttechtitan.aptustutor.ai

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThermalManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val powerManager = ContextCompat.getSystemService(context, PowerManager::class.java)

    companion object {
        // A conservative threshold. If thermal headroom is below 25%, we should pause.
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
            Log.d("AptusTutorDebug", "Current thermal status: $status")
            // See documentation for status codes: THERMAL_STATUS_MODERATE is 2
            if (status >= PowerManager.THERMAL_STATUS_MODERATE) {
                return false
            }
        }

        // Check getThermalHeadroom (Requires API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // getThermalHeadroom(0) gives the current forecast.
            // A value closer to 1.0 means closer to severe throttling.
            val headroom = powerManager.getThermalHeadroom(0)
            Log.d("AptusTutorDebug", "Current thermal headroom: $headroom")

            // NaN indicates the API is not supported or has been called too frequently.
            // Treat unsupported as "safe" to not block functionality.
            if (headroom.isNaN()) return true

            return headroom < SAFE_THERMAL_HEADROOM
        }

        // If on a device with API < 29, we can't check, so we proceed.
        return true
    }
}