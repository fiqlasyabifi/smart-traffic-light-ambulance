package com.example.smarttrafficlight.data.simulation

import com.example.smarttrafficlight.data.firebase.TrafficMapConfig
import com.example.smarttrafficlight.data.firebase.GeoCoordinateConfig
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0

/**
 * Koordinat GPS virtual ambulans.
 * Menggunakan sistem koordinat WGS84.
 */
data class VirtualGpsCoordinate(
    val latitude: Double,
    val longitude: Double
)

/**
 * Status posisi ambulans pada satu waktu simulasi.
 */
data class VirtualGpsState(
    val latitude: Double,
    val longitude: Double,
    val distanceTravelledMeters: Double,
    val distanceRemainingMeters: Double,
    val elapsedSeconds: Double,
    val progress: Double,
    val speedMetersPerSecond: Double,
    val status: String
)

/**
 * Mesin simulasi GPS virtual.
 *
 * Tidak menggunakan GPS fisik HP.
 * Posisi dihitung dari koordinat jalur yang diberikan.
 *
 * Mendukung:
 * - Jalur lurus.
 * - Jalur dengan beberapa titik belokan.
 * - Durasi simulasi.
 * - Perhitungan jarak tersisa.
 */
class VirtualGpsSimulator private constructor(
    private val route: List<VirtualGpsCoordinate>,
    val durationSeconds: Int
) {

    private val geometry = RouteGeometry(route)

    val initialDistanceMeters: Double
        get() = geometry.totalDistanceMeters

    val speedMetersPerSecond: Double
        get() = initialDistanceMeters / durationSeconds

    init {
        require(durationSeconds > 0) {
            "Durasi simulasi harus lebih dari nol."
        }

        require(geometry.totalDistanceMeters > 0.0) {
            "Panjang jalur harus lebih dari nol."
        }
    }

    /**
     * Menghitung posisi ambulans berdasarkan waktu yang berjalan.
     *
     * elapsedMillis berasal dari waktu monotonic,
     * bukan jam kalender perangkat.
     */
    fun positionAt(elapsedMillis: Long): VirtualGpsState {

        val totalDurationMillis = durationSeconds * 1000L

        val safeElapsedMillis = elapsedMillis.coerceIn(
            0L,
            totalDurationMillis
        )

        val progress = (
                safeElapsedMillis.toDouble() /
                        totalDurationMillis.toDouble()
                ).coerceIn(0.0, 1.0)

        val elapsedSeconds = safeElapsedMillis / 1000.0

        val distanceTravelled =
            geometry.totalDistanceMeters * progress

        val distanceRemaining =
            (geometry.totalDistanceMeters - distanceTravelled)
                .coerceAtLeast(0.0)

        val currentCoordinate =
            geometry.coordinateAtDistance(distanceTravelled)

        return VirtualGpsState(
            latitude = currentCoordinate.latitude,
            longitude = currentCoordinate.longitude,
            distanceTravelledMeters = distanceTravelled,
            distanceRemainingMeters = distanceRemaining,
            elapsedSeconds = elapsedSeconds,
            progress = progress,
            speedMetersPerSecond = speedMetersPerSecond,
            status = if (progress >= 1.0) {
                "ARRIVED"
            } else {
                "MOVING"
            }
        )
    }

    companion object {

        /**
         * Membuat simulasi dari TL yang dipilih.
         *
         * Untuk tahap awal, posisi tujuan menggunakan
         * approach_reference_points dari Firebase.
         *
         * Titik awal dihitung berdasarkan arah pendekatan
         * dan jarak awal yang dipilih pengguna.
         */
        fun forTrafficLight(
            config: TrafficMapConfig,
            trafficLightId: String,
            initialDistanceMeters: Double,
            durationSeconds: Int
        ): VirtualGpsSimulator {

            require(initialDistanceMeters > 0.0) {
                "Jarak awal harus lebih dari nol."
            }

            require(initialDistanceMeters.isFinite()) {
                "Jarak awal tidak valid."
            }

            require(durationSeconds > 0) {
                "Durasi harus lebih dari nol."
            }

            val trafficLight = config.trafficLights[
                trafficLightId
            ] ?: throw IllegalArgumentException(
                "Traffic light $trafficLightId tidak ditemukan."
            )

            require(trafficLight.enabled) {
                "Traffic light $trafficLightId tidak aktif."
            }

            val target = config.approachReferencePoints[
                trafficLight.approach
            ] ?: throw IllegalArgumentException(
                "Titik pendekatan ${trafficLight.approach} tidak ditemukan."
            )

            val destination = VirtualGpsCoordinate(
                latitude = target.latitude,
                longitude = target.longitude
            )

            val startingPoint = calculateStartingPoint(
                destination = destination,
                approach = trafficLight.approach,
                distanceMeters = initialDistanceMeters
            )

            return VirtualGpsSimulator(
                route = listOf(
                    startingPoint,
                    destination
                ),
                durationSeconds = durationSeconds
            )
        }

        /**
         * Membuat simulasi dari jalur yang memiliki
         * beberapa titik koordinat, termasuk belokan.
         *
         * Urutan titik harus dimulai dari posisi awal
         * ambulans dan berakhir di titik tujuan.
         */
        fun fromRoute(
            routePoints: List<VirtualGpsCoordinate>,
            durationSeconds: Int
        ): VirtualGpsSimulator {

            return VirtualGpsSimulator(
                route = routePoints,
                durationSeconds = durationSeconds
            )
        }

        /**
         * Menghitung posisi awal ambulans di luar
         * persimpangan sesuai arah pendekatan.
         */
        private fun calculateStartingPoint(
            destination: VirtualGpsCoordinate,
            approach: String,
            distanceMeters: Double
        ): VirtualGpsCoordinate {

            val angularDistance =
                distanceMeters / EARTH_RADIUS_METERS

            val latitudeOffset =
                Math.toDegrees(angularDistance)

            val longitudeOffset =
                Math.toDegrees(
                    angularDistance /
                            cos(Math.toRadians(destination.latitude))
                )

            return when (approach.lowercase()) {

                // Ambulans datang dari utara menuju selatan.
                "north" -> VirtualGpsCoordinate(
                    latitude = destination.latitude + latitudeOffset,
                    longitude = destination.longitude
                )

                // Ambulans datang dari selatan menuju utara.
                "south" -> VirtualGpsCoordinate(
                    latitude = destination.latitude - latitudeOffset,
                    longitude = destination.longitude
                )

                // Ambulans datang dari barat menuju timur.
                "west" -> VirtualGpsCoordinate(
                    latitude = destination.latitude,
                    longitude = destination.longitude - longitudeOffset
                )

                // Ambulans datang dari timur menuju barat.
                "east" -> VirtualGpsCoordinate(
                    latitude = destination.latitude,
                    longitude = destination.longitude + longitudeOffset
                )

                else -> throw IllegalArgumentException(
                    "Arah pendekatan tidak dikenal: $approach"
                )
            }
        }
    }
}

/**
 * Perhitungan geometri jalur menggunakan polyline.
 *
 * Setiap dua titik berurutan membentuk satu segmen.
 * Dengan demikian, jalur dapat memiliki banyak belokan.
 */
private class RouteGeometry(
    private val points: List<VirtualGpsCoordinate>
) {

    private val segmentLengths: List<Double>

    val totalDistanceMeters: Double

    init {

        require(points.size >= 2) {
            "Jalur harus memiliki minimal dua titik koordinat."
        }

        points.forEach { point ->

            require(point.latitude.isFinite() &&
                    point.longitude.isFinite()
            ) {
                "Koordinat jalur harus berupa angka valid."
            }

            require(point.latitude in -90.0..90.0) {
                "Latitude di luar rentang."
            }

            require(point.longitude in -180.0..180.0) {
                "Longitude di luar rentang."
            }
        }

        segmentLengths = points.zipWithNext { start, end ->
            distanceBetween(start, end)
        }

        totalDistanceMeters = segmentLengths.sum()

        require(totalDistanceMeters > 0.0) {
            "Jalur tidak boleh memiliki panjang nol."
        }
    }

    /**
     * Menghasilkan koordinat berdasarkan jarak tempuh
     * dari titik awal jalur.
     */
    fun coordinateAtDistance(
        distanceMeters: Double
    ): VirtualGpsCoordinate {

        if (distanceMeters <= 0.0) {
            return points.first()
        }

        if (distanceMeters >= totalDistanceMeters) {
            return points.last()
        }

        var remainingDistance = distanceMeters

        for (index in segmentLengths.indices) {

            val segmentLength = segmentLengths[index]

            if (remainingDistance <= segmentLength) {

                val start = points[index]
                val end = points[index + 1]

                val fraction = (
                        remainingDistance / segmentLength
                        ).coerceIn(0.0, 1.0)

                return VirtualGpsCoordinate(
                    latitude = start.latitude +
                            (end.latitude - start.latitude) * fraction,

                    longitude = start.longitude +
                            (end.longitude - start.longitude) * fraction
                )
            }

            remainingDistance -= segmentLength
        }

        return points.last()
    }
}

/**
 * Menghitung jarak dua koordinat GPS menggunakan Haversine.
 */
fun distanceBetween(
    first: VirtualGpsCoordinate,
    second: VirtualGpsCoordinate
): Double {

    val latitude1 = Math.toRadians(first.latitude)
    val latitude2 = Math.toRadians(second.latitude)

    val deltaLatitude = Math.toRadians(
        second.latitude - first.latitude
    )

    val deltaLongitude = Math.toRadians(
        second.longitude - first.longitude
    )

    val a =
        sin(deltaLatitude / 2.0).pow(2.0) +
                cos(latitude1) *
                cos(latitude2) *
                sin(deltaLongitude / 2.0).pow(2.0)

    val centralAngle = 2.0 * asin(
        sqrt(a.coerceIn(0.0, 1.0))
    )

    return EARTH_RADIUS_METERS * centralAngle
}