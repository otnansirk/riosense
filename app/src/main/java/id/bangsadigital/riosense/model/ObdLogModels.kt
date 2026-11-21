package id.bangsadigital.riosense.model

/**
 * Severity level for OBD-II Diagnostic Trouble Codes (DTC) and vehicle fault alerts.
 */
enum class ObdErrorSeverity(val label: String) {
    CRITICAL("Critical"),
    WARNING("Warning"),
    INFO("Info")
}

/**
 * Represents a single OBD-II detected error or vehicle fault entry for the Error Log.
 */
data class ObdErrorEntry(
    val id: Long,
    val timestampMs: Long,
    val code: String,
    val title: String,
    val subsystem: String,
    val pid: String,
    val rawFrame: String,
    val severity: ObdErrorSeverity,
    val description: String,
    val recommendedAction: String,
    val source: String = "OBD-II"
)

/**
 * Communication direction for OBD-II / CAN frames.
 */
enum class ObdDirection(val tag: String) {
    TX("TX"),
    RX("RX")
}

/**
 * Represents a real-time OBD-II frame sent (TX) or received (RX) for the Activity Log.
 */
data class ObdActivityEntry(
    val id: Long,
    val timestampMs: Long,
    val direction: ObdDirection,
    val pid: String,
    val parameterName: String,
    val quickValue: String,
    val ecuHeader: String,
    val rawFrame: String,
    val decodedDetail: String,
    val formula: String,
    val source: String = "OBD-II"
)
