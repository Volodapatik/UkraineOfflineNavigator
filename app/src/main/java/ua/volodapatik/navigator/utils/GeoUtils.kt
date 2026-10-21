package ua.volodapatik.navigator.utils

import android.location.Location
import kotlin.math.*

object GeoUtils {

    /**
     * Calculate distance between two points in meters (Haversine)
     */
    fun distanceMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun distanceMeters(from: Location, toLat: Double, toLon: Double): Double {
        return distanceMeters(from.latitude, from.longitude, toLat, toLon)
    }

    /**
     * Estimate remaining time in seconds based on distance and average speed
     */
    fun estimateTimeSeconds(distanceMeters: Double, speedKmh: Double): Long {
        if (speedKmh <= 0) return 0
        val hours = distanceMeters / 1000.0 / speedKmh
        return (hours * 3600).toLong()
    }
}
