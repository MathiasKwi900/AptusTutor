package com.nexttechtitan.aptustutor.utils

import android.app.ActivityManager
import android.content.Context
import com.nexttechtitan.aptustutor.utils.ThermalManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class DeviceCapability {
    UNSUPPORTED,
    LIMITED,
    CAPABLE
}

// MODIFIED: Added raw metric fields for accurate UI display.
data class CapabilityResult(
    val capability: DeviceCapability,
    val message: String,
    val availableRamMb: Long,
    val thermalHeadroom: Float? // Can be null if API returns NaN
)

@Singleton
class DeviceHealthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val thermalManager: ThermalManager
) {
    private val activityManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    companion object {
        private const val MIN_SUPPORTED_TOTAL_RAM_MB = 1500L
        private const val MIN_CAPABLE_AVAILABLE_RAM_MB = 750L
        private const val MIN_LIMITED_AVAILABLE_RAM_MB = 400L
    }

    fun checkDeviceCapability(): CapabilityResult {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val totalRamMb = memoryInfo.totalMem / 1048576L
        val availableRamMb = memoryInfo.availMem / 1048576L
        val thermalHeadroom = thermalManager.getThermalHeadroom()

        if (totalRamMb < MIN_SUPPORTED_TOTAL_RAM_MB) {
            return CapabilityResult(
                DeviceCapability.UNSUPPORTED,
                "Device Unsupported: Less than 1.5GB of total RAM.",
                availableRamMb,
                thermalHeadroom
            )
        }

        if (!thermalManager.isSafeToProceed()) {
            return CapabilityResult(
                DeviceCapability.UNSUPPORTED,
                "Device Overheating: AI tasks paused to allow cooling.",
                availableRamMb,
                thermalHeadroom
            )
        }

        if (availableRamMb < MIN_LIMITED_AVAILABLE_RAM_MB) {
            return CapabilityResult(
                DeviceCapability.UNSUPPORTED,
                "Insufficient Memory: Only $availableRamMb MB RAM available.",
                availableRamMb,
                thermalHeadroom
            )
        }

        if (availableRamMb < MIN_CAPABLE_AVAILABLE_RAM_MB) {
            return CapabilityResult(
                DeviceCapability.LIMITED,
                "Limited Memory Mode: Grading one by one for stability.",
                availableRamMb,
                thermalHeadroom
            )
        }

        return CapabilityResult(
            DeviceCapability.CAPABLE,
            "Device Ready: Sufficient resources detected.",
            availableRamMb,
            thermalHeadroom
        )
    }
}