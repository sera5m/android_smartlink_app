package test.smartlinksoft

/**
 * One reading from the watch. All fields optional so a partial
 * packet still routes. Timestamp is millis since epoch.
 */
data class HealthSample(
    val timestampMs: Long = System.currentTimeMillis(),
    val bpm: Int? = null,
    val spo2: Int? = null,
    val rmssdMs: Int? = null,
    val note: String? = null,
)
