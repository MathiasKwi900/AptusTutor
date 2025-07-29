package com.nexttechtitan.aptustutor.utils

import android.app.ActivityManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Categorizes the device's current capability to run heavy AI tasks. */
enum class DeviceCapability {
    UNSUPPORTED, // Should not run AI tasks.
    LIMITED,     // Can run, but may be slow; app should take precautions.
    CAPABLE      // Can run tasks optimally.
}

/** A data class holding the result of a device health check, including raw metrics. */
data class CapabilityResult(
    val capability: DeviceCapability,
    val message: String,
    val availableRamMb: Long,
    val thermalHeadroom: Float? // Null if API is not supported.
)

/**
 * A singleton utility to assess the device's real-time health (RAM, thermal status).
 * This is critical for deciding if and how to run on-device AI tasks, ensuring a
 * stable user experience on resource-constrained hardware.
 */
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

    /**
     * Performs a tiered check of device resources to determine its capability.
     * The checks are ordered from most critical to least:
     * 1. Total RAM check (a hard gate for unsupported devices).
     * 2. Thermal status check (to prevent running on an overheating device).
     * 3. Available RAM check (to ensure enough memory is free *right now*).
     * @return A [CapabilityResult] object with the final status and metrics.
     */
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