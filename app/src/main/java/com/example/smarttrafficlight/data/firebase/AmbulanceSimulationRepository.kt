package com.example.smarttrafficlight.data.firebase

import com.example.smarttrafficlight.data.simulation.VirtualGpsState
import com.example.smarttrafficlight.feature.setup.SimulationSetup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

/**
 * Repository untuk mengirim posisi GPS virtual ambulans
 * ke Firebase Realtime Database.
 *
 * Struktur:
 * ambulance/sessions/{uid}/simulation
 * ambulance/sessions/{uid}/live
 */
class AmbulanceSimulationRepository(
    private val database: FirebaseDatabase =
        FirebaseDatabase.getInstance(),

    private val auth: FirebaseAuth =
        FirebaseAuth.getInstance()
) {

    /**
     * Mendapatkan referensi sesi pengguna yang sedang login.
     */
    private fun getSessionReference(): DatabaseReference? {

        val uid = auth.currentUser?.uid ?: return null

        return database
            .getReference("ambulance")
            .child("sessions")
            .child(uid)
    }

    /**
     * Memulai sesi simulasi dan menyimpan
     * konfigurasi serta posisi awal ambulans.
     */
    fun startSession(
        setup: SimulationSetup,
        controllerGroup: String,
        initialState: VirtualGpsState,
        onComplete: (Boolean, String?) -> Unit
    ) {

        val sessionReference = getSessionReference()

        if (sessionReference == null) {
            onComplete(
                false,
                "Pengguna belum login ke Firebase."
            )
            return
        }

        val simulationData = mapOf(
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

    /**
     * Memperbarui posisi ambulans secara berkala.
     *
     * Fungsi ini dipanggil setiap interval marker:
     * 0,5 detik, 1 detik, atau 2 detik.
     */

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
            .updateChildren(
                createLiveData(state, trafficLightId)
            )
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
     * Menyelesaikan sesi simulasi.
     */
    fun finishSession(
        finalState: VirtualGpsState,
        onComplete: (Boolean, String?) -> Unit
    ) {

        val sessionReference = getSessionReference()

        if (sessionReference == null) {
            onComplete(
                false,
                "Pengguna belum login ke Firebase."
            )
            return
        }

        val finalUpdates = mapOf(
            "simulation/status" to "completed",
            "simulation/finished_at" to ServerValue.TIMESTAMP,

            "live/latitude" to finalState.latitude,
            "live/longitude" to finalState.longitude,
            "live/distance_travelled_m" to
                    finalState.distanceTravelledMeters,
            "live/distance_remaining_m" to
                    finalState.distanceRemainingMeters,
            "live/elapsed_seconds" to
                    finalState.elapsedSeconds,
            "live/progress" to finalState.progress,
            "live/status" to finalState.status,
            "live/updated_at" to ServerValue.TIMESTAMP
        )

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

    /**
     * Membentuk data posisi terbaru untuk Firebase.
     */
    private fun createLiveData(
        state: VirtualGpsState,
        trafficLightId: String
    ): Map<String, Any> {

        return mapOf(
            "target_traffic_light" to trafficLightId,

            "latitude" to state.latitude,
            "longitude" to state.longitude,

            "distance_travelled_m" to
                    state.distanceTravelledMeters,

            "distance_remaining_m" to
                    state.distanceRemainingMeters,

            "elapsed_seconds" to
                    state.elapsedSeconds,

            "progress" to state.progress,

            "speed_mps" to
                    state.speedMetersPerSecond,

            "status" to state.status,

            "updated_at" to ServerValue.TIMESTAMP
        )
    }
}