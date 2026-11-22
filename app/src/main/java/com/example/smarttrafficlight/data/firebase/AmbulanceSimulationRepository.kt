package com.example.smarttrafficlight.data.firebase

import com.example.smarttrafficlight.data.simulation.VirtualGpsState
import com.example.smarttrafficlight.feature.setup.SimulationSetup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

/**
 * Repository untuk menyimpan konfigurasi sesi dan posisi GPS virtual ambulans
 * ke Firebase Realtime Database.
 *
 * Struktur:
 * ambulance/sessions/{uid}/simulation
 * ambulance/sessions/{uid}/live
 *
 * Status simulasi dan status GPS dipisahkan:
 * - simulation/status: running, completed, cancelled, finished, failed
 * - live/status: MOVING, ARRIVED, CANCELLED, FINISHED, dan seterusnya
 */
class AmbulanceSimulationRepository(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    /** Mendapatkan referensi sesi untuk pengguna yang sedang login. */
    private fun getSessionReference(): DatabaseReference? {
        val uid = auth.currentUser?.uid ?: return null

        return database
            .getReference("ambulance")
            .child("sessions")
            .child(uid)
    }

    /**
     * Memulai sesi simulasi dan menyimpan konfigurasi serta posisi awal.
     * started_at dan live.updated_at menggunakan waktu server Firebase.
     */
    fun startSession(
        setup: SimulationSetup,
        controllerGroup: String,
        initialState: VirtualGpsState,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val sessionReference = getSessionReference()

        if (sessionReference == null) {
            onComplete(false, "Pengguna belum login ke Firebase.")
            return
        }

        val simulationData = mapOf<String, Any>(
            "target_traffic_light" to setup.trafficLight,
            "display_group" to setup.trafficGroup,
            "controller_group" to controllerGroup,
            "initial_distance_m" to setup.initialDistance,
            "duration_seconds" to setup.durationSeconds,
            "update_interval_seconds" to
                    setup.markerIntervalSeconds.toDouble(),
            "status" to "running",
            "started_at" to ServerValue.TIMESTAMP
        )

        sessionReference
            .child("simulation")
            .setValue(simulationData)
            .addOnSuccessListener {
                sessionReference
                    .child("live")
                    .setValue(createLiveData(initialState, setup.trafficLight))
                    .addOnSuccessListener {
                        onComplete(true, null)
                    }
                    .addOnFailureListener { error ->
                        onComplete(
                            false,
                            error.localizedMessage
                                ?: "Gagal menyimpan posisi awal ambulans."
                        )
                    }
            }
            .addOnFailureListener { error ->
                onComplete(
                    false,
                    error.localizedMessage
                        ?: "Gagal menyimpan konfigurasi simulasi."
                )
            }
    }

    /** Memperbarui posisi ambulans secara berkala. */
    fun updatePosition(
        state: VirtualGpsState,
        trafficLightId: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val sessionReference = getSessionReference()

        if (sessionReference == null) {
            onComplete(false, "Pengguna belum login ke Firebase.")
            return
        }

        sessionReference
            .child("live")
            .updateChildren(createLiveData(state, trafficLightId))
            .addOnSuccessListener {
                onComplete(true, null)
            }
            .addOnFailureListener { error ->
                onComplete(
                    false,
                    error.localizedMessage
                        ?: "Gagal memperbarui posisi ambulans."
                )
            }
    }

    /**
     * Menyimpan status akhir sesi.
     *
     * Penting: sebelumnya semua status akhir selalu ditulis sebagai
     * simulation/status = completed. Hal itu membuat backend salah membaca
     * sesi CANCELLED sebagai AMBULANCE_ARRIVED. Sekarang status simulasi
     * diturunkan dari finalState.status.
     */
    fun finishSession(
        finalState: VirtualGpsState,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val sessionReference = getSessionReference()

        if (sessionReference == null) {
            onComplete(false, "Pengguna belum login ke Firebase.")
            return
        }

        val normalizedLiveStatus = finalState.status.trim().uppercase()

        val simulationStatus = when (normalizedLiveStatus) {
            "ARRIVED" -> "completed"
            "CANCELLED", "CANCELED" -> "cancelled"
            "FINISHED" -> "finished"
            "FAILED" -> "failed"
            "STOPPED" -> "stopped"
            else -> "finished"
        }

        val finalUpdates = mutableMapOf<String, Any>(
            "simulation/status" to simulationStatus,
            "simulation/finished_at" to ServerValue.TIMESTAMP,

            "live/latitude" to finalState.latitude,
            "live/longitude" to finalState.longitude,
            "live/distance_travelled_m" to
                    finalState.distanceTravelledMeters,
            "live/distance_remaining_m" to
                    finalState.distanceRemainingMeters,
            "live/elapsed_seconds" to finalState.elapsedSeconds,
            "live/progress" to finalState.progress,
            "live/status" to normalizedLiveStatus,
            "live/updated_at" to ServerValue.TIMESTAMP
        )

        // Timestamp khusus pembatalan memudahkan audit di Firebase.
        if (simulationStatus == "cancelled") {
            finalUpdates["simulation/cancelled_at"] = ServerValue.TIMESTAMP
        }

        sessionReference
            .updateChildren(finalUpdates)
            .addOnSuccessListener {
                onComplete(true, null)
            }
            .addOnFailureListener { error ->
                onComplete(
                    false,
                    error.localizedMessage
                        ?: "Gagal menyelesaikan sesi simulasi."
                )
            }
    }

    /** Membentuk data posisi terbaru yang ditulis ke node live. */
    private fun createLiveData(
        state: VirtualGpsState,
        trafficLightId: String
    ): Map<String, Any> {
        return mapOf(
            "target_traffic_light" to trafficLightId,
            "latitude" to state.latitude,
            "longitude" to state.longitude,
            "distance_travelled_m" to state.distanceTravelledMeters,
            "distance_remaining_m" to state.distanceRemainingMeters,
            "elapsed_seconds" to state.elapsedSeconds,
            "progress" to state.progress,
            "speed_mps" to state.speedMetersPerSecond,
            "status" to state.status.trim().uppercase(),
            "updated_at" to ServerValue.TIMESTAMP
        )
    }
}