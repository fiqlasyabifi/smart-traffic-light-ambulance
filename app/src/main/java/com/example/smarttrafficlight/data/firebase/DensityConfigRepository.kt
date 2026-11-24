package com.example.smarttrafficlight.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * One shared density configuration for every analysis source:
 * webcam, public CCTV, uploaded photo, and uploaded video.
 *
 * RTDB path: /analysis_config/density
 */
data class DensityConfig(
    val enabled: Boolean = true,
    val lowMax: Int = 5,
    val mediumMax: Int = 15,
    val updatedAtMs: Long = 0L
) {
    fun toFirebaseMap(): Map<String, Any> = mapOf(
        "enabled" to enabled,
        "low_max" to lowMax,
        "medium_max" to mediumMax,
        "updated_at_ms" to (System.currentTimeMillis())
    )

    companion object {
        val DEFAULT = DensityConfig()

        fun fromSnapshot(snapshot: DataSnapshot): DensityConfig {
            val enabled = snapshot.child("enabled")
                .getValue(Boolean::class.javaObjectType) ?: true
            val lowMax = (snapshot.child("low_max").value as? Number)
                ?.toInt() ?: DEFAULT.lowMax
            val mediumMax = (snapshot.child("medium_max").value as? Number)
                ?.toInt() ?: DEFAULT.mediumMax
            val updatedAtMs = (snapshot.child("updated_at_ms").value as? Number)
                ?.toLong() ?: 0L

            // Reject invalid/incomplete values and fall back to safe defaults.
            if (lowMax < 0 || mediumMax <= lowMax) return DEFAULT

            return DensityConfig(
                enabled = enabled,
                lowMax = lowMax,
                mediumMax = mediumMax,
                updatedAtMs = updatedAtMs
            )
        }
    }
}

class DensityConfigRepository(
    private val reference: DatabaseReference = FirebaseDatabase
        .getInstance(DATABASE_URL)
        .getReference("analysis_config/density")
) {
    fun addListener(
        onConfig: (DensityConfig) -> Unit,
        onError: (DatabaseError) -> Unit
    ): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onConfig(
                    if (snapshot.exists()) DensityConfig.fromSnapshot(snapshot)
                    else DensityConfig.DEFAULT
                )
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error)
            }
        }
        reference.addValueEventListener(listener)
        return listener
    }

    fun removeListener(listener: ValueEventListener) {
        reference.removeEventListener(listener)
    }

    fun getOnce(
        onConfig: (DensityConfig) -> Unit,
        onError: (Exception) -> Unit
    ) {
        reference.get()
            .addOnSuccessListener { snapshot ->
                onConfig(
                    if (snapshot.exists()) DensityConfig.fromSnapshot(snapshot)
                    else DensityConfig.DEFAULT
                )
            }
            .addOnFailureListener { error -> onError(error) }
    }

    fun save(
        config: DensityConfig,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (config.lowMax < 0) {
            onFailure(IllegalArgumentException("Batas maksimum Rendah tidak boleh negatif."))
            return
        }
        if (config.mediumMax <= config.lowMax) {
            onFailure(IllegalArgumentException("Batas maksimum Sedang harus lebih besar daripada batas Rendah."))
            return
        }

        reference.setValue(config.toFirebaseMap())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error -> onFailure(error) }
    }

    companion object {
        private const val DATABASE_URL =
            "https://smart-traffic-light-rawa-d711e-default-rtdb.asia-southeast1.firebasedatabase.app"
    }
}
