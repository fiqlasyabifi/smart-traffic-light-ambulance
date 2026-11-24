package com.example.smarttrafficlight.feature.analysis

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smarttrafficlight.ui.theme.BackgroundLight
import com.example.smarttrafficlight.ui.theme.BorderLight
import com.example.smarttrafficlight.ui.theme.PrimaryRed
import com.example.smarttrafficlight.ui.theme.PrimaryRedDark
import com.example.smarttrafficlight.ui.theme.PrimaryRedLight
import com.example.smarttrafficlight.ui.theme.SurfaceWhite
import com.example.smarttrafficlight.ui.theme.TextPrimary
import com.example.smarttrafficlight.ui.theme.TextSecondary
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Android hanya membaca hasil analisis dari Python.
 * Python menangani sumber video (webcam/CCTV), YOLO, ROI dan Bottom Center.
 * RTDB: /webcam_analysis/latest
 */
private data class WebcamFirebaseResult(
    val online: Boolean = false,
    val status: String = "UNKNOWN",
    val sourceType: String = "",
    val sourceName: String = "",
    val vehicleCount: Int = 0,
    val car: Int = 0,
    val motorcycle: Int = 0,
    val bus: Int = 0,
    val truck: Int = 0,
    val densityLabel: String = "Belum diklasifikasikan",
    val roiReady: Boolean = false,
    val updatedAtMs: Long = 0L,
    val inferenceMs: Long? = null,
    val error: String? = null
)

private const val WEBCAM_DATABASE_URL =
    "https://smart-traffic-light-rawa-d711e-default-rtdb.asia-southeast1.firebasedatabase.app"

@Composable
fun WebcamLaptopScreen(
    trafficLight: String,
    trafficGroup: String,
    onBack: () -> Unit,
    onUseResult: (vehicleCount: Int, densityLabel: String) -> Unit
) {
    // Gunakan URL RTDB yang sama persis dengan backend Python.
    val reference = remember {
        FirebaseDatabase.getInstance(WEBCAM_DATABASE_URL)
            .getReference("webcam_analysis/latest")
    }
    val firebaseAuth = remember { FirebaseAuth.getInstance() }

    var result by remember { mutableStateOf<WebcamFirebaseResult?>(null) }
    var firebaseError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }

    // Pastikan Android sudah memiliki Firebase Auth user sebelum membaca RTDB.
    // Jika belum login, gunakan Anonymous Authentication.
    DisposableEffect(reference, firebaseAuth) {
        var disposed = false
        var databaseListener: ValueEventListener? = null

        fun attachDatabaseListener() {
            if (disposed || databaseListener != null) return

            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    isLoading = false
                    firebaseError = null

                    if (!snapshot.exists()) {
                        result = null
                        return
                    }

                    fun readString(key: String): String =
                        snapshot.child(key).getValue(String::class.java).orEmpty()

                    fun readInt(path: String): Int =
                        (snapshot.child(path).value as? Number)?.toInt() ?: 0

                    val car = readInt("counts/car")
                    val motorcycle = readInt("counts/motorcycle")
                    val bus = readInt("counts/bus")
                    val truck = readInt("counts/truck")

                    // Python menyimpan timestamp Unix dalam detik.
                    val timestampSeconds =
                        (snapshot.child("timestamp").value as? Number)?.toLong() ?: 0L
                    val updatedAtMs = if (timestampSeconds > 0L) {
                        timestampSeconds * 1000L
                    } else {
                        0L
                    }

                    val sourceType = readString("source_type").ifBlank {
                        when (readString("source")) {
                            "laptop_webcam" -> "webcam"
                            "public_cctv" -> "cctv"
                            else -> ""
                        }
                    }.lowercase()

                    val defaultSourceName = when (sourceType) {
                        "webcam" -> "Webcam Laptop"
                        "cctv" -> "CCTV Publik"
                        else -> "Sumber belum diketahui"
                    }

                    val roiReady = snapshot.child("roi_configured")
                        .getValue(Boolean::class.javaObjectType)
                        ?: (snapshot.child("roi_points").childrenCount >= 3L)

                    val totalVehicles =
                        (snapshot.child("total_vehicles").value as? Number)?.toInt()
                            ?: readInt("vehicle_count").takeIf { it > 0 }
                            ?: (car + motorcycle + bus + truck)

                    result = WebcamFirebaseResult(
                        online = snapshot.child("online")
                            .getValue(Boolean::class.javaObjectType) ?: false,
                        status = readString("status").ifBlank { "UNKNOWN" },
                        sourceType = sourceType,
                        sourceName = readString("source_name").ifBlank { defaultSourceName },
                        vehicleCount = totalVehicles,
                        car = car,
                        motorcycle = motorcycle,
                        bus = bus,
                        truck = truck,
                        densityLabel = readString("density_label")
                            .ifBlank { "Belum diklasifikasikan" },
                        roiReady = roiReady,
                        updatedAtMs = updatedAtMs,
                        inferenceMs = (snapshot.child("inference_ms").value as? Number)?.toLong(),
                        error = readString("error").ifBlank { null }
                    )
                }

                override fun onCancelled(error: DatabaseError) {
                    isLoading = false
                    firebaseError = "Tidak dapat membaca Firebase: ${error.message} (code ${error.code})"
                }
            }

            databaseListener = listener
            reference.addValueEventListener(listener)
        }

        val authStateListener = FirebaseAuth.AuthStateListener { auth ->
            if (auth.currentUser != null) {
                attachDatabaseListener()
            }
        }

        firebaseAuth.addAuthStateListener(authStateListener)

        if (firebaseAuth.currentUser != null) {
            attachDatabaseListener()
        } else {
            isLoading = true
            firebaseError = null
            firebaseAuth.signInAnonymously()
                .addOnCompleteListener { task ->
                    if (disposed) return@addOnCompleteListener

                    if (task.isSuccessful && firebaseAuth.currentUser != null) {
                        attachDatabaseListener()
                    } else {
                        isLoading = false
                        firebaseError = "Login Firebase Anonymous gagal: ${task.exception?.localizedMessage ?: "periksa Authentication > Sign-in method > Anonymous"}"
                    }
                }
        }

        onDispose {
            disposed = true
            firebaseAuth.removeAuthStateListener(authStateListener)
            databaseListener?.let { reference.removeEventListener(it) }
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val current = result
    val ageMs = if (current != null && current.updatedAtMs > 0L) {
        nowMs - current.updatedAtMs
    } else {
        Long.MAX_VALUE
    }

    // Payload Python diperbarui sekitar setiap 1 detik.
    val isFresh = ageMs in -10_000L..15_000L
    val isConnected = current?.online == true &&
            current.status.equals("running", ignoreCase = true) &&
            isFresh
    val isReady = isConnected && current?.roiReady == true

    val sourceLabel = when (current?.sourceType) {
        "webcam" -> "Webcam Laptop"
        "cctv" -> "CCTV Publik"
        else -> current?.sourceName?.ifBlank { "Sumber belum diketahui" }
            ?: "Belum ada sumber video"
    }

    val statusLabel = when {
        isLoading -> "MEMUAT"
        firebaseError != null -> "ERROR"
        isConnected -> "LIVE"
        else -> "OFFLINE"
    }
    val statusColor = if (isConnected) Color(0xFF17794A) else PrimaryRedDark
    val statusBackground = if (isConnected) Color(0xFFE8F6EE) else PrimaryRedLight

    Scaffold(containerColor = BackgroundLight) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali", tint = TextPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Webcam Laptop / CCTV",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        "Hasil analisis Python melalui Firebase",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                Surface(shape = CircleShape, color = statusBackground) {
                    Text(
                        statusLabel,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                border = BorderStroke(1.dp, BorderLight)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "SUMBER VIDEO AKTIF",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        current?.sourceName?.ifBlank { sourceLabel } ?: sourceLabel,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                    Text(
                        when (current?.sourceType) {
                            "webcam" -> "Sumber: Webcam Laptop"
                            "cctv" -> "Sumber: CCTV Publik"
                            else -> "Sumber video belum dikenali dari Firebase"
                        },
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    if (trafficLight.isNotBlank() || trafficGroup.isNotBlank()) {
                        Text(
                            "Target simulasi: ${trafficLight.ifBlank { "—" }} · Kelompok ${trafficGroup.ifBlank { "—" }}",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        text = when {
                            isLoading -> "Memeriksa login dan mengambil data Firebase..."
                            firebaseError != null -> firebaseError.orEmpty()
                            current == null -> "Belum ada data dari Python pada /webcam_analysis/latest."
                            !current.online -> "Program Python tidak sedang mengirim video. Status: ${current.status}."
                            !isFresh -> "Data terakhir kedaluwarsa. Periksa proses Python dan koneksi sumber video."
                            !current.roiReady -> "Sumber video aktif, tetapi ROI belum dibuat atau disimpan. Tekan E di jendela Python, gambar minimal 3 titik, lalu tekan S."
                            else -> "Sumber video aktif dan hasil deteksi diterima dari Python."
                        },
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = PrimaryRed,
                            strokeWidth = 2.dp
                        )
                    }
                    current?.error?.takeIf { it.isNotBlank() }?.let { message ->
                        Text("Pesan Python: $message", color = PrimaryRedDark, fontSize = 12.sp)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                border = BorderStroke(1.dp, BorderLight)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Hasil analisis Python", color = TextSecondary, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            current?.vehicleCount?.toString() ?: "—",
                            color = TextPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 40.sp
                        )
                        Spacer(Modifier.size(10.dp))
                        Column {
                            Text("kendaraan dalam ROI", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text("Deteksi dihitung pada laptop", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        VehicleValue("Mobil", current?.car ?: 0)
                        VehicleValue("Motor", current?.motorcycle ?: 0)
                        VehicleValue("Bus", current?.bus ?: 0)
                        VehicleValue("Truk", current?.truck ?: 0)
                    }
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF3F4F6)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Kepadatan", color = TextSecondary, fontSize = 13.sp)
                            Text(
                                current?.densityLabel ?: "Belum diklasifikasikan",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                    Text(
                        text = buildString {
                            append("Pembaruan: ")
                            if (current == null || current.updatedAtMs <= 0L) {
                                append("belum tersedia")
                            } else {
                                append("${(ageMs / 1000L).coerceAtLeast(0L)} detik lalu")
                            }
                            current?.inferenceMs?.let { append(" · Inferensi ${it} ms") }
                        },
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    current?.let { onUseResult(it.vehicleCount, it.densityLabel) }
                },
                enabled = isReady && current != null,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed)
            ) {
                Icon(Icons.Filled.CloudDownload, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Gunakan Hasil untuk Simulasi", fontWeight = FontWeight.Bold)
            }

            Text(
                text = "Android hanya membaca hasil Firebase. Pemilihan sumber video, YOLO, dan pengaturan ROI tetap dilakukan di laptop.",
                modifier = Modifier.fillMaxWidth(),
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun VehicleValue(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, color = TextSecondary, fontSize = 10.sp)
    }
}