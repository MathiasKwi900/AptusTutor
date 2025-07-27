package com.nexttechtitan.aptustutor.utils

import android.app.ActivityManager
import android.content.Context
import com.nexttechtitan.aptustutor.ai.ThermalManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class DeviceCapability {
    UNSUPPORTED, // Cannot run the model safely
    LIMITED,     // Can run a single inference task
    CAPABLE      // Can run batch inference tasks
}

data class CapabilityResult(
    val capability: DeviceCapability,
    val message: String
)

@Singleton
class DeviceHealthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val thermalManager: ThermalManager
) {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    companion object {
        private const val MIN_SUPPORTED_TOTAL_RAM_MB = 1500L // 1.5 GB
        private const val MIN_CAPABLE_AVAILABLE_RAM_MB = 750L // 750 MB
        private const val MIN_LIMITED_AVAILABLE_RAM_MB = 400L // 400 MB
    }

    fun checkDeviceCapability(): CapabilityResult {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val totalRamMb = memoryInfo.totalMem / 1048576L
        val availableRamMb = memoryInfo.availMem / 1048576L

        if (totalRamMb < MIN_SUPPORTED_TOTAL_RAM_MB) {
            return CapabilityResult(
                DeviceCapability.UNSUPPORTED,
                "Device Unsupported: This device has less than 1.5GB of total RAM ($totalRamMb MB), which is insufficient to run the AI model reliably."
            )
        }

        if (!thermalManager.isSafeToProceed()) {
            return CapabilityResult(
                DeviceCapability.UNSUPPORTED,
                "Device Overheating: The device is currently under thermal stress. Please allow it to cool down before running AI tasks."
            )
        }

        if (availableRamMb < MIN_LIMITED_AVAILABLE_RAM_MB) {
            return CapabilityResult(
                DeviceCapability.UNSUPPORTED,
                "Insufficient Memory: The device has very low available memory ($availableRamMb MB). Please close other applications and try again."
            )
        }

        if (availableRamMb < MIN_CAPABLE_AVAILABLE_RAM_MB) {
            return CapabilityResult(
                DeviceCapability.LIMITED,
                "Limited Memory Mode: Available RAM ($availableRamMb MB) is low. The app will grade one question at a time to ensure stability."
            )
        }

        return CapabilityResult(
            DeviceCapability.CAPABLE,
            "Device Ready: Sufficient resources detected ($availableRamMb MB RAM available). Ready for batch grading."
        )
    }
}