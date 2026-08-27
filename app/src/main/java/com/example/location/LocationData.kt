package com.example.location

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val speed: Float = 0f, // in m/s
    val speedKmh: Float = 0f, // in km/h
    val bearing: Float = 0f, // heading in degrees
    val accuracy: Float = 0f, // in meters
    val timestamp: Long = System.currentTimeMillis(),
    val provider: String = "gps"
) {
    val formattedCoordinates: String
        get() = String.format(java.util.Locale.US, "%.5f, %.5f", latitude, longitude)

    val formattedSpeed: String
        get() = String.format(java.util.Locale.US, "%.1f km/h", speedKmh)

    val formattedAccuracy: String
        get() = String.format(java.util.Locale.US, "±%.1fm", accuracy)
}
