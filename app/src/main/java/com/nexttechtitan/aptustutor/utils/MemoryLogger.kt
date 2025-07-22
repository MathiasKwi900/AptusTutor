package com.nexttechtitan.aptustutor.utils

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryLogger @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    fun logMemory(tag: String, event: String) {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val availableMegs = memoryInfo.availMem / 1048576L // Bytes to MB
        val totalMegs = memoryInfo.totalMem / 1048576L
        Log.i(tag, "MEMORY_LOG | $event | Available RAM: $availableMegs MB / $totalMegs MB")
    }
}