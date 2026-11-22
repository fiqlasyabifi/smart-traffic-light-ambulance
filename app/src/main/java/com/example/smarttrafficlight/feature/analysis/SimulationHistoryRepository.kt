package com.example.smarttrafficlight.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener


// =====================================================
// DATA RIWAYAT SIMULASI
// =====================================================

data class SimulationHistoryRecord(

    val id: String = "",

    val trafficLight: String = "",

    val trafficGroup: String = "",

    val initialSignal: String = "",

    val sourceLabel: String = "",

    val vehicleCount: Int? = null,

    val densityLabel: String? = null,

    val initialDistanceMeters: Int = 0,

    val targetDurationSeconds: Int = 0,

    val actualDurationSeconds: Double = 0.0,

    val completedAtMillis: Long = 0L
)


// =====================================================
// SIMULATION HISTORY REPOSITORY
// =====================================================

class SimulationHistoryRepository(

    private val database: FirebaseDatabase =
        FirebaseDatabase.getInstance(),

    private val auth: FirebaseAuth =
        FirebaseAuth.getInstance()

) {

    // =================================================
    // REFERENSI RIWAYAT MILIK USER
    // =================================================

    private fun getHistoryReference() =
        auth.currentUser?.uid?.let { uid ->

            database
                .getReference("ambulance")
                .child("history")
                .child(uid)
        }


    // =================================================
    // MENYIMPAN RIWAYAT SIMULASI
    // =================================================

    fun saveHistory(

        record: SimulationHistoryRecord,

        onComplete: (Boolean, String?) -> Unit

    ) {

        val historyReference = getHistoryReference()

        if (historyReference == null) {

            onComplete(
                false,
                "Pengguna belum login ke Firebase."
            )

            return
        }

        val newRecord = historyReference.push()

        val data = mutableMapOf<String, Any>(

            "traffic_light" to record.trafficLight,

            "traffic_group" to record.trafficGroup,

            "initial_signal" to record.initialSignal,

            "source_label" to record.sourceLabel,

            "initial_distance_m" to
                    record.initialDistanceMeters,

            "target_duration_seconds" to
                    record.targetDurationSeconds,

            "actual_duration_seconds" to
                    record.actualDurationSeconds,

            "completed_at" to ServerValue.TIMESTAMP
        )

        record.vehicleCount?.let {
            data["vehicle_count"] = it
        }

        record.densityLabel?.let {
            data["density_label"] = it
        }

        newRecord.setValue(data)

            .addOnSuccessListener {
                onComplete(true, null)
            }

            .addOnFailureListener { error ->

                onComplete(
                    false,
                    error.localizedMessage
                        ?: "Gagal menyimpan riwayat simulasi."
                )
            }
    }


    // =================================================
    // MEMBACA RIWAYAT SIMULASI SECARA REAL-TIME
    // =================================================

    fun observeHistory(

        onChanged: (List<SimulationHistoryRecord>) -> Unit,

        onError: (String) -> Unit

    ): () -> Unit {

        val historyReference = getHistoryReference()

        if (historyReference == null) {

            onError("Pengguna belum login ke Firebase.")

            return {}
        }

        val listener = object : ValueEventListener {

            override fun onDataChange(
                snapshot: DataSnapshot
            ) {

                val records = snapshot.children.mapNotNull {

                        child ->

                    val id = child.key ?: return@mapNotNull null

                    val vehicleCount = child
                        .child("vehicle_count")
                        .getValue(Long::class.javaObjectType)
                        ?.toInt()

                    val actualDuration = child
                        .child("actual_duration_seconds")
                        .getValue(Double::class.javaObjectType)
                        ?: child
                            .child("actual_duration_seconds")
                            .getValue(Long::class.javaObjectType)
                            ?.toDouble()
                        ?: 0.0

                    SimulationHistoryRecord(

                        id = id,

                        trafficLight = child
                            .child("traffic_light")
                            .getValue(String::class.java)
                            ?: "",

                        trafficGroup = child
                            .child("traffic_group")
                            .getValue(String::class.java)
                            ?: "",

                        initialSignal = child
                            .child("initial_signal")
                            .getValue(String::class.java)
                            ?: "",

                        sourceLabel = child
                            .child("source_label")
                            .getValue(String::class.java)
                            ?: "",

                        vehicleCount = vehicleCount,

                        densityLabel = child
                            .child("density_label")
                            .getValue(String::class.java),

                        initialDistanceMeters = child
                            .child("initial_distance_m")
                            .getValue(Long::class.javaObjectType)
                            ?.toInt()
                            ?: 0,

                        targetDurationSeconds = child
                            .child("target_duration_seconds")
                            .getValue(Long::class.javaObjectType)
                            ?.toInt()
                            ?: 0,

                        actualDurationSeconds = actualDuration,

                        completedAtMillis = child
                            .child("completed_at")
                            .getValue(Long::class.javaObjectType)
                            ?: 0L
                    )
                }.sortedByDescending {
                    it.completedAtMillis
                }

                onChanged(records)
            }

            override fun onCancelled(
                error: DatabaseError
            ) {

                onError(
                    "Gagal membaca riwayat: ${error.message}"
                )
            }
        }

        historyReference.addValueEventListener(listener)

        return {
            historyReference.removeEventListener(listener)
        }
    }
}