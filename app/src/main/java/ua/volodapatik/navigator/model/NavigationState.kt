package ua.volodapatik.navigator.model

data class NavigationState(
    val isNavigating: Boolean = false,
    val remainingDistanceMeters: Double = 0.0,
    val remainingTimeSeconds: Long = 0L,
    val destinationName: String = "",
    val transportMode: TransportMode = TransportMode.CAR
) {
    val remainingDistanceText: String
        get() {
            return when {
                remainingDistanceMeters >= 1000 -> {
                    val km = remainingDistanceMeters / 1000.0
                    String.format("%.1f км", km)
                }
                else -> {
                    "${remainingDistanceMeters.toInt()} м"
                }
            }
        }

    val remainingTimeText: String
        get() {
            val totalSeconds = remainingTimeSeconds
            if (totalSeconds <= 0) return "—"

            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60

            return when {
                hours > 0 -> String.format("%d год %d хв", hours, minutes)
                minutes > 0 -> String.format("%d хв %d сек", minutes, seconds)
                else -> String.format("%d сек", seconds)
            }
        }
}
