package com.example.smarttrafficlight.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

data class TrafficDashboardStatus(
    val backendOnline: Boolean = false,
    val serialConnected: Boolean = false,
    val backendLastSeen: String? = null,

    val esp32Online: Boolean = false,
    val state: String = "",
    val phase: String = "",
    val condition: String = "",
    val remainingSeconds: Int? = null,
    val priorityActive: Boolean = false,
    val priorityGroup: String? = null,
    val priorityHold: Boolean = false,
    val esp32UpdatedAt: String? = null
)

class TrafficStatusRepository {

    private val database = FirebaseDatabase.getInstance()

    private val esp32Reference =
        database.getReference("esp32/status")

    private val backendReference =
        database.getReference("backend/status")

    fun observeStatus(
        onChanged: (TrafficDashboardStatus) -> Unit,
        onError: (String) -> Unit
    ): () -> Unit {

        var latestEsp32 = TrafficDashboardStatus()
        var backendOnline = false
        var serialConnected = false
        var backendLastSeen: String? = null

        fun publishStatus() {
            onChanged(
                latestEsp32.copy(
                    backendOnline = backendOnline,
                    serialConnected = serialConnected,
                    backendLastSeen = backendLastSeen
                )
            )
        }

        val esp32Listener = object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                latestEsp32 = TrafficDashboardStatus(
                    esp32Online =
                        snapshot.child("online")
                            .getValue(Boolean::class.javaObjectType)
                            ?: false,

                    state =
                        snapshot.child("state")
                            .getValue(String::class.java) ?: "",

                    phase =
                        snapshot.child("phase")
                            .getValue(String::class.java) ?: "",

                    condition =
                        snapshot.child("condition")
                            .getValue(String::class.java) ?: "",

                    remainingSeconds =
                        snapshot.child("remaining_seconds")
                            .getValue(Long::class.javaObjectType)
                            ?.toInt(),

                    priorityActive =
                        snapshot.child("priority_active")
                            .getValue(Boolean::class.javaObjectType)
                            ?: false,

                    priorityGroup =
                        snapshot.child("priority_group")
                            .getValue(String::class.java)
                            ?.takeUnless { it == "-" },

                    priorityHold =
                        snapshot.child("priority_hold")
                            .getValue(Boolean::class.javaObjectType)
                            ?: false,

                    esp32UpdatedAt =
                        snapshot.child("updated_at")
                            .getValue(String::class.java)
                )

                publishStatus()
            }

            override fun onCancelled(error: DatabaseError) {
                onError("Gagal membaca status ESP32: ${error.message}")
            }
        }

        val backendListener = object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                backendOnline =
                    snapshot.child("online")
                        .getValue(Boolean::class.javaObjectType)
                        ?: false

                serialConnected =
                    snapshot.child("serial_connected")
                        .getValue(Boolean::class.javaObjectType)
                        ?: false

                backendLastSeen =
                    snapshot.child("last_seen")
                        .getValue(String::class.java)

                publishStatus()
            }

            override fun onCancelled(error: DatabaseError) {
                onError("Gagal membaca status backend: ${error.message}")
            }
        }

        esp32Reference.addValueEventListener(esp32Listener)
        backendReference.addValueEventListener(backendListener)

        return {
            esp32Reference.removeEventListener(esp32Listener)
            backendReference.removeEventListener(backendListener)
        }
    }
}