package com.mpowernet.shesafe.security

import android.util.Log

object BehavioralPatternEngine {

    private const val TAG = "BehavioralPatternEngine"
    
    // Window time frame to evaluate velocity (30 seconds)
    private const val TIME_WINDOW_MS = 30000L
    
    // Maximum allowed sensitive permission requests in that window
    private const val MAX_REQUESTS_THRESHOLD = 3

    // Cache to hold package request timestamps: Map<PackageName, List<Timestamp>>
    private val requestHistory = mutableMapOf<String, MutableList<Long>>()

    /**
     * Records a permission request event.
     * Returns true if a velocity anomaly (excessive permission requests in a short time) is detected.
     */
    @Synchronized
    fun recordRequestAndCheckAnomaly(packageName: String): Boolean {
        val now = System.currentTimeMillis()
        val timestamps = requestHistory.getOrPut(packageName) { mutableListOf() }
        
        // Add current request
        timestamps.add(now)
        
        // Prune older timestamps outside of our evaluation window
        val thresholdTime = now - TIME_WINDOW_MS
        timestamps.removeAll { it < thresholdTime }

        val requestCount = timestamps.size
        Log.d(TAG, "Recorded request for package: $packageName. Total requests in last 30s: $requestCount")

        if (requestCount >= MAX_REQUESTS_THRESHOLD) {
            Log.w(TAG, "VELOCITY ANOMALY DETECTED for package $packageName: $requestCount requests in 30 seconds.")
            return true
        }
        
        return false
    }

    /**
     * Reset tracking history for a package (e.g., after the user takes corrective action).
     */
    @Synchronized
    fun resetTracking(packageName: String) {
        requestHistory.remove(packageName)
    }
}
