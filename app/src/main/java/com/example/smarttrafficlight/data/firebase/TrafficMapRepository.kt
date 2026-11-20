package com.example.smarttrafficlight.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

data class GeoCoordinateConfig(
    val latitude: Double,
    val longitude: Double,
    val position: String? = null,
    val status: String? = null
)

data class TrafficLightConfig(
    val id: String,
    val group: String,
    val approach: String,
    val enabled: Boolean
)

data class TrafficPhaseConfig(
    val id: String,
    val group: String,
    val trafficLightIds: List<String>
)

data class TrafficMapConfig(
    val schemaVersion: Int,
    val coordinateSystem: String,
    val coordinateSource: String,
    val intersectionCorners: Map<String, GeoCoordinateConfig>,
    val approachReferencePoints: Map<String, GeoCoordinateConfig>,
    val trafficLights: Map<String, TrafficLightConfig>,
    val phaseOrder: List<TrafficPhaseConfig>
)

class TrafficMapRepository(
    private val reference: DatabaseReference =
        FirebaseDatabase.getInstance().getReference("map_config")
) {

    fun loadConfig(
        onSuccess: (TrafficMapConfig) -> Unit,
        onError: (String) -> Unit
    ) {
        reference.get()
            .addOnSuccessListener { snapshot ->
                try {
                    val config = parseConfig(snapshot)
                    validateConfig(config)
                    onSuccess(config)
                } catch (error: Exception) {
                    onError(
                        error.message ?: "Konfigurasi peta tidak valid."
                    )
                }
            }
            .addOnFailureListener { error ->
                onError(
                    error.localizedMessage
                        ?: "Gagal mengambil data Firebase."
                )
            }
    }

    private fun parseConfig(
        snapshot: DataSnapshot
    ): TrafficMapConfig {

        require(snapshot.exists()) {
            "Data map_config tidak ditemukan."
        }

        val corners = parseCoordinates(
            snapshot.child("intersection_corners"),
            "position"
        )

        val approachPoints = parseCoordinates(
            snapshot.child("approach_reference_points"),
            "status"
        )

        val lights = mutableMapOf<String, TrafficLightConfig>()

        snapshot.child("traffic_lights").children.forEach { node ->
            val id = node.key ?: return@forEach

            lights[id] = TrafficLightConfig(
                id = id,
                group = node.child("group")
                    .value as? String ?: "",
                approach = node.child("approach")
                    .value as? String ?: "",
                enabled = node.child("enabled")
                    .value as? Boolean ?: false
            )
        }

        val phases = snapshot.child("phase_order")
            .children
            .mapNotNull { node ->

                val id = node.key ?: return@mapNotNull null

                val lightIds = node.child("traffic_lights")
                    .children
                    .filter {
                        it.value == true
                    }
                    .mapNotNull {
                        it.key
                    }

                TrafficPhaseConfig(
                    id = id,
                    group = node.child("group")
                        .value as? String ?: "",
                    trafficLightIds = lightIds
                )
            }
            .sortedBy {
                it.id.substringAfter("phase_")
                    .toIntOrNull() ?: Int.MAX_VALUE
            }

        return TrafficMapConfig(
            schemaVersion = (
                    snapshot.child("schema_version").value as? Number
                    )?.toInt() ?: 1,

            coordinateSystem =
                snapshot.child("coordinate_system")
                    .value as? String ?: "",

            coordinateSource =
                snapshot.child("coordinate_source")
                    .value as? String ?: "",

            intersectionCorners = corners,
            approachReferencePoints = approachPoints,
            trafficLights = lights,
            phaseOrder = phases
        )
    }

    private fun parseCoordinates(
        snapshot: DataSnapshot,
        metadataKey: String
    ): Map<String, GeoCoordinateConfig> {

        val result = mutableMapOf<String, GeoCoordinateConfig>()

        snapshot.children.forEach { node ->

            val id = node.key ?: return@forEach

            val latitude = (node.child("latitude").value as? Number)
                ?.toDouble()

            val longitude = (node.child("longitude").value as? Number)
                ?.toDouble()

            if (latitude == null || longitude == null) {
                return@forEach
            }

            require(latitude in -90.0..90.0) {
                "Latitude $id berada di luar rentang."
            }

            require(longitude in -180.0..180.0) {
                "Longitude $id berada di luar rentang."
            }

            val metadata = node.child(metadataKey)
                .value as? String

            result[id] = GeoCoordinateConfig(
                latitude = latitude,
                longitude = longitude,
                position = if (metadataKey == "position") metadata else null,
                status = if (metadataKey == "status") metadata else null
            )
        }

        return result
    }

    private fun validateConfig(config: TrafficMapConfig) {

        require(config.intersectionCorners.keys.containsAll(
            listOf("P1", "P2", "P3", "P4")
        )) {
            "Empat koordinat sudut belum lengkap."
        }

        require(config.approachReferencePoints.keys.containsAll(
            listOf("north", "south", "east", "west")
        )) {
            "Empat titik referensi pendekatan belum lengkap."
        }

        require(config.trafficLights.keys.containsAll(
            listOf("TL1", "TL2", "TL3", "TL4", "TL5", "TL6")
        )) {
            "Konfigurasi TL1 sampai TL6 belum lengkap."
        }

        require(config.phaseOrder.size == 3) {
            "Konfigurasi fase A, B, dan C belum lengkap."
        }

        require(config.coordinateSystem == "WGS84") {
            "Sistem koordinat harus menggunakan WGS84."
        }
    }
}