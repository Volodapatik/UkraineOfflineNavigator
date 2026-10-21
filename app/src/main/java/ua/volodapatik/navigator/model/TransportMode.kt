package ua.volodapatik.navigator.model

enum class TransportMode(
    val titleUk: String,
    val averageSpeedKmh: Double,   // average speed for ETA calculation (MVP)
    val iconEmoji: String
) {
    CAR("Авто", 50.0, "🚗"),
    WALK("Пішки", 5.0, "🚶"),
    BIKE("Велик", 15.0, "🚲");

    companion object {
        fun fromOrdinal(ordinal: Int): TransportMode {
            return entries.getOrElse(ordinal) { CAR }
        }
    }
}
