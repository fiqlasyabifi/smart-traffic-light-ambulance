package com.example.smarttrafficlight.feature.simulation

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.example.smarttrafficlight.data.firebase.AmbulanceSimulationRepository
import com.example.smarttrafficlight.data.firebase.TrafficMapConfig
import com.example.smarttrafficlight.data.simulation.VirtualGpsSimulator
import com.example.smarttrafficlight.data.simulation.VirtualGpsState
import com.example.smarttrafficlight.feature.setup.SimulationSetup
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val RedPrimary = Color(0xFFD52B3A)
private val BackgroundLight = Color(0xFFF6F6F7)
private val TextPrimary = Color(0xFF191B1F)
private val TextSecondary = Color(0xFF686D76)
private val SuccessGreen = Color(0xFF138052)

@Composable
fun AmbulanceSimulationScreen(
    config: TrafficMapConfig,
    setup: SimulationSetup,
    onBack: () -> Unit
) {
    val repository = remember {
        AmbulanceSimulationRepository()
    }

    val scope = rememberCoroutineScope()

    val simulatorResult = remember(config, setup) {
        runCatching {
            VirtualGpsSimulator.forTrafficLight(
                config = config,
                trafficLightId = setup.trafficLight,
                initialDistanceMeters = setup.initialDistance.toDouble(),
                durationSeconds = setup.durationSeconds
            )
        }
    }

    val simulator = simulatorResult.getOrNull()

    val intervalMillis = (
            setup.markerIntervalSeconds * 1000
            ).toLong().coerceAtLeast(100L)

    var isRunning by remember(simulator) {
        mutableStateOf(false)
    }

    var isStarting by remember(simulator) {
        mutableStateOf(false)
    }

    var gpsState by remember(simulator) {
        mutableStateOf(
            simulator?.positionAt(0L)
        )
    }

    var firebaseMessage by remember(simulator) {
        mutableStateOf("Belum ada sesi yang dikirim.")
    }

    // =================================================
    // PERGERAKAN DAN PENGIRIMAN POSISI KE FIREBASE
    // =================================================

    LaunchedEffect(isRunning, simulator, intervalMillis) {

        if (isRunning && simulator != null) {

            val startTime = SystemClock.elapsedRealtime()

            while (true) {

                delay(intervalMillis)

                val elapsedMillis =
                    SystemClock.elapsedRealtime() - startTime

                val newState = simulator.positionAt(elapsedMillis)

                // Perbarui tampilan terlebih dahulu.
                gpsState = newState

                if (newState.status == "ARRIVED") {

                    // Simpan posisi akhir dan selesaikan sesi.
                    try {
                        awaitFinishSession(
                            repository = repository,
                            finalState = newState
                        )

                        firebaseMessage =
                            "Sesi selesai. Posisi akhir berhasil disimpan."
                    } catch (error: Exception) {

                        firebaseMessage =
                            "Ambulans tiba, tetapi penyimpanan akhir gagal: " +
                                    (error.localizedMessage ?: "Kesalahan Firebase")
                    }

                    isRunning = false
                    break
                }

                // Kirim posisi terbaru setiap interval.
                try {

                    awaitUpdatePosition(
                        repository = repository,
                        state = newState,
                        trafficLightId = setup.trafficLight
                    )

                    firebaseMessage =
                        "Terhubung: posisi berhasil diperbarui ke Firebase."

                } catch (error: Exception) {

                    firebaseMessage =
                        "Gagal mengirim posisi: " +
                                (error.localizedMessage ?: "Kesalahan Firebase")

                    // Simulasi lokal tetap berjalan.
                    // Pengiriman akan dicoba lagi pada interval berikutnya.
                }
            }
        }
    }

    Scaffold(
        containerColor = BackgroundLight
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // HEADER
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = TextPrimary
                    )
                ) {
                    Text("Kembali")
                }

                Column {
                    Text(
                        "Simulasi Ambulans",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Text(
                        "Virtual GPS Rawamangun",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            if (simulator == null) {

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            "Simulasi tidak dapat dibuat",
                            fontWeight = FontWeight.Bold,
                            color = RedPrimary
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            simulatorResult.exceptionOrNull()
                                ?.localizedMessage
                                ?: "Periksa konfigurasi Firebase."
                        )
                    }
                }

            } else {

                val current = gpsState
                    ?: simulator.positionAt(0L)

                // INFORMASI TUJUAN
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        Text(
                            "Tujuan ambulans",
                            color = TextSecondary
                        )

                        Text(
                            setup.trafficLight,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = RedPrimary
                        )

                        Text(
                            "Kelompok ${setup.trafficGroup}",
                            color = TextSecondary
                        )

                        Text(
                            "Arah pendekatan: ${
                                config.trafficLights[setup.trafficLight]?.approach
                            }",
                            color = TextPrimary
                        )
                    }
                }

                // STATUS AMBULANS
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {

                        Text(
                            "Status ambulans",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )

                        Text(
                            text = when {
                                current.status == "ARRIVED" ->
                                    "TIBA DI TUJUAN"

                                isRunning ->
                                    "AMBULANS BERGERAK"

                                else ->
                                    "SIAP DIMULAI"
                            },
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (current.status == "ARRIVED") {
                                SuccessGreen
                            } else {
                                RedPrimary
                            }
                        )

                        GpsProgressBar(
                            progress = current.progress.toFloat()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${(current.progress * 100).toInt()}%",
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                "${current.elapsedSeconds.toInt()} / " +
                                        "${setup.durationSeconds} detik",
                                color = TextSecondary
                            )
                        }
                    }
                }

                // DATA GPS
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(15.dp)
                    ) {

                        Text(
                            "Koordinat GPS virtual",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )

                        CoordinateRow(
                            "Latitude",
                            formatCoordinate(current.latitude)
                        )

                        CoordinateRow(
                            "Longitude",
                            formatCoordinate(current.longitude)
                        )

                        CoordinateRow(
                            "Jarak ditempuh",
                            "${current.distanceTravelledMeters.formatOneDecimal()} m"
                        )

                        CoordinateRow(
                            "Jarak tersisa",
                            "${current.distanceRemainingMeters.formatOneDecimal()} m"
                        )

                        CoordinateRow(
                            "Kecepatan virtual",
                            "${current.speedMetersPerSecond.formatOneDecimal()} m/dtk"
                        )
                    }
                }

                // PENGATURAN
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        Text(
                            "Pengaturan simulasi",
                            fontWeight = FontWeight.Bold
                        )

                        CoordinateRow(
                            "Jarak awal",
                            "${setup.initialDistance} m"
                        )

                        CoordinateRow(
                            "Durasi",
                            "${setup.durationSeconds} detik"
                        )

                        CoordinateRow(
                            "Interval update",
                            "${setup.markerIntervalSeconds} detik"
                        )
                    }
                }

                // STATUS FIREBASE
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White
                ) {
                    Text(
                        text = firebaseMessage,
                        modifier = Modifier.padding(16.dp),
                        color = if (
                            firebaseMessage.startsWith("Terhubung") ||
                            firebaseMessage.startsWith("Sesi selesai")
                        ) {
                            SuccessGreen
                        } else {
                            TextSecondary
                        },
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }

                // TOMBOL MULAI / ULANGI
                Button(
                    onClick = {

                        if (!isStarting && !isRunning) {

                            scope.launch {

                                isStarting = true
                                firebaseMessage =
                                    "Menyimpan sesi awal ke Firebase..."

                                try {

                                    val initialState =
                                        simulator.positionAt(0L)

                                    val controllerGroup =
                                        config.trafficLights[
                                            setup.trafficLight
                                        ]?.group
                                            ?: throw IllegalStateException(
                                                "Kelompok traffic light tidak ditemukan."
                                            )

                                    awaitStartSession(
                                        repository = repository,
                                        setup = setup,
                                        controllerGroup = controllerGroup,
                                        initialState = initialState
                                    )

                                    gpsState = initialState

                                    firebaseMessage =
                                        "Terhubung: sesi awal berhasil disimpan."

                                    isRunning = true

                                } catch (error: Exception) {

                                    firebaseMessage =
                                        "Gagal memulai simulasi: " +
                                                (error.localizedMessage
                                                    ?: "Kesalahan Firebase")

                                } finally {

                                    isStarting = false
                                }
                            }
                        }
                    },
                    enabled = !isRunning && !isStarting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RedPrimary,
                        disabledContainerColor = Color.Gray
                    )
                ) {

                    Text(
                        text = when {
                            isStarting -> "Menghubungkan Firebase..."
                            isRunning -> "Ambulans Bergerak"
                            current.status == "ARRIVED" -> "Ulangi Simulasi"
                            else -> "Mulai Simulasi"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    "GPS ini merupakan koordinat virtual. " +
                            "Posisi simulasi dikirim ke Firebase, bukan GPS fisik HP.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// =====================================================
// FUNGSI MENUNGGU HASIL OPERASI FIREBASE
// =====================================================

private suspend fun awaitStartSession(
    repository: AmbulanceSimulationRepository,
    setup: SimulationSetup,
    controllerGroup: String,
    initialState: VirtualGpsState
) {
    suspendCancellableCoroutine<Unit> { continuation ->

        repository.startSession(
            setup = setup,
            controllerGroup = controllerGroup,
            initialState = initialState
        ) { success, error ->

            if (continuation.isActive) {

                if (success) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(
                        IllegalStateException(
                            error ?: "Gagal menyimpan sesi awal."
                        )
                    )
                }
            }
        }
    }
}

private suspend fun awaitUpdatePosition(
    repository: AmbulanceSimulationRepository,
    state: VirtualGpsState,
    trafficLightId: String
) {
    suspendCancellableCoroutine<Unit> { continuation ->

        repository.updatePosition(
            state = state,
            trafficLightId = trafficLightId
        ) { success, error ->

            if (continuation.isActive) {

                if (success) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(
                        IllegalStateException(
                            error ?: "Gagal memperbarui posisi."
                        )
                    )
                }
            }
        }
    }
}

private suspend fun awaitFinishSession(
    repository: AmbulanceSimulationRepository,
    finalState: VirtualGpsState
) {
    suspendCancellableCoroutine<Unit> { continuation ->

        repository.finishSession(
            finalState = finalState
        ) { success, error ->

            if (continuation.isActive) {

                if (success) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(
                        IllegalStateException(
                            error ?: "Gagal menyelesaikan sesi."
                        )
                    )
                }
            }
        }
    }
}

// =====================================================
// KOMPONEN TAMPILAN
// =====================================================

@Composable
private fun GpsProgressBar(
    progress: Float
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
    ) {
        drawRoundRect(
            color = Color(0xFFE5E6E9),
            cornerRadius = CornerRadius(20f)
        )

        val safeProgress = progress.coerceIn(0f, 1f)

        if (safeProgress > 0f) {
            drawRoundRect(
                color = RedPrimary,
                size = Size(
                    width = size.width * safeProgress,
                    height = size.height
                ),
                cornerRadius = CornerRadius(20f)
            )
        }
    }
}

@Composable
private fun CoordinateRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = TextSecondary,
            fontSize = 13.sp
        )

        Text(
            value,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatCoordinate(value: Double): String {
    return String.format(Locale.US, "%.7f", value)
}

private fun Double.formatOneDecimal(): String {
    return String.format(Locale.US, "%.1f", this)
}