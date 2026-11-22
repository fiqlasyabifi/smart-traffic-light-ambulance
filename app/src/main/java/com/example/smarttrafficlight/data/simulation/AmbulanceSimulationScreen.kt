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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
private val BorderLight = Color(0xFFE5E6E9)

private enum class FinishAction {
    SUMMARY,
    CANCEL
}


// =====================================================
// AMBULANCE SIMULATION SCREEN
// =====================================================

@Composable
fun AmbulanceSimulationScreen(
    config: TrafficMapConfig,
    setup: SimulationSetup,
    onBack: () -> Unit,
    summaryContext: SimulationSummaryData? = null,
    onSimulationFinished: (SimulationSummaryData) -> Unit = {}
) {

    val repository = remember {
        AmbulanceSimulationRepository()
    }

    val scope = rememberCoroutineScope()

    // =================================================
    // MEMBUAT VIRTUAL GPS
    // =================================================

    val simulatorResult = remember(config, setup) {

        runCatching {

            VirtualGpsSimulator.forTrafficLight(

                config = config,

                trafficLightId = setup.trafficLight,

                initialDistanceMeters =
                    setup.initialDistance.toDouble(),

                durationSeconds =
                    setup.durationSeconds
            )
        }
    }

    val simulator = simulatorResult.getOrNull()

    val intervalMillis = (
            setup.markerIntervalSeconds * 1000
            ).toLong().coerceAtLeast(100L)

    // =================================================
    // STATE SIMULASI
    // =================================================

    var isRunning by remember(simulator) {
        mutableStateOf(false)
    }

    var isStarting by remember(simulator) {
        mutableStateOf(false)
    }

    var isFinishing by remember(simulator) {
        mutableStateOf(false)
    }

    var hasStarted by remember(simulator) {
        mutableStateOf(false)
    }

    var gpsState by remember(simulator) {
        mutableStateOf(
            simulator?.positionAt(0L)
        )
    }

    var simulationStartedAt by remember(simulator) {
        mutableStateOf<Long?>(null)
    }

    var firebaseMessage by remember(simulator) {
        mutableStateOf(
            "Belum ada sesi yang dikirim."
        )
    }

    // State ketika penyimpanan akhir gagal.
    // Data tetap disimpan agar dapat dicoba kembali.

    var pendingFinalState by remember(simulator) {
        mutableStateOf<VirtualGpsState?>(null)
    }

    var pendingFinishAction by remember(simulator) {
        mutableStateOf<FinishAction?>(null)
    }

    // =================================================
    // PEMBENTUK DATA RINGKASAN
    // =================================================

    fun createSummary(
        finalState: VirtualGpsState
    ): SimulationSummaryData {

        val base = summaryContext
            ?: SimulationSummaryData(

                trafficLight = setup.trafficLight,

                trafficGroup = setup.trafficGroup,

                initialSignal = "—",

                sourceLabel = "Tidak dilakukan",

                vehicleCount = null,

                densityLabel = null,

                initialDistanceMeters =
                    setup.initialDistance,

                targetDurationSeconds =
                    setup.durationSeconds,

                actualDurationSeconds = 0.0
            )

        return base.copy(

            actualDurationSeconds =
                finalState.elapsedSeconds,

            completedAtMillis =
                System.currentTimeMillis()
        )
    }

    // =================================================
    // SIMPAN STATUS AKHIR FIREBASE
    // =================================================

    suspend fun persistPendingFinish() {

        val finalState = pendingFinalState ?: return

        val action = pendingFinishAction ?: return

        if (isFinishing) return

        isFinishing = true

        try {

            awaitFinishSession(

                repository = repository,

                finalState = finalState
            )

            pendingFinalState = null
            pendingFinishAction = null

            firebaseMessage = when (action) {

                FinishAction.SUMMARY ->
                    "Sesi selesai. Posisi akhir berhasil disimpan."

                FinishAction.CANCEL ->
                    "Simulasi berhasil dibatalkan."
            }

            when (action) {

                FinishAction.SUMMARY -> {

                    // Ringkasan hanya dibuka setelah Firebase
                    // berhasil menyimpan posisi akhir.

                    onSimulationFinished(
                        createSummary(finalState)
                    )
                }

                FinishAction.CANCEL -> {

                    // Simulasi dibatalkan, tidak masuk riwayat.

                    onBack()
                }
            }

        } catch (error: Exception) {

            firebaseMessage =
                "Gagal menyimpan status akhir: " +
                        (
                                error.localizedMessage
                                    ?: "Kesalahan Firebase"
                                )

            // pendingFinalState tidak dikosongkan.
            // Pengguna dapat mencoba menyimpan kembali.

        } finally {

            isFinishing = false
        }
    }

    // =================================================
    // MEMINTA PENYELESAIAN SIMULASI
    // =================================================

    fun requestFinish(
        finalState: VirtualGpsState,
        action: FinishAction
    ) {

        if (
            isFinishing ||
            pendingFinalState != null
        ) {
            return
        }

        pendingFinalState = finalState

        pendingFinishAction = action

        // Menghentikan loop GPS sebelum menyimpan final state.

        isRunning = false

        scope.launch {
            persistPendingFinish()
        }
    }

    // =================================================
    // LOOP PERGERAKAN GPS DAN FIREBASE
    // =================================================

    LaunchedEffect(
        isRunning,
        simulator,
        intervalMillis
    ) {

        if (isRunning && simulator != null) {

            val startTime = simulationStartedAt
                ?: SystemClock.elapsedRealtime()

            while (true) {

                delay(intervalMillis)

                val elapsedMillis =
                    SystemClock.elapsedRealtime() - startTime

                val newState = simulator.positionAt(
                    elapsedMillis
                )

                gpsState = newState

                // =====================================
                // AMBULANS SUDAH TIBA
                // =====================================

                if (newState.status == "ARRIVED") {

                    firebaseMessage =
                        "Ambulans tiba. Menyimpan posisi akhir..."

                    requestFinish(
                        finalState = newState,
                        action = FinishAction.SUMMARY
                    )

                    break
                }

                // =====================================
                // KIRIM POSISI TERBARU KE FIREBASE
                // =====================================

                try {

                    awaitUpdatePosition(

                        repository = repository,

                        state = newState,

                        trafficLightId =
                            setup.trafficLight
                    )

                    firebaseMessage =
                        "Terhubung: posisi berhasil diperbarui ke Firebase."

                } catch (error: Exception) {

                    firebaseMessage =
                        "Gagal mengirim posisi: " +
                                (
                                        error.localizedMessage
                                            ?: "Kesalahan Firebase"
                                        )

                    // Simulasi lokal tetap berjalan.
                    // Update akan dicoba pada interval berikutnya.
                }
            }
        }
    }

    // =================================================
    // FUNGSI MULAI SIMULASI
    // =================================================

    fun startSimulation() {

        if (
            simulator == null ||
            isStarting ||
            isRunning ||
            isFinishing ||
            pendingFinalState != null
        ) {
            return
        }

        scope.launch {

            isStarting = true

            firebaseMessage =
                "Menyimpan sesi awal ke Firebase..."

            try {

                val initialState = simulator.positionAt(0L)

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

                simulationStartedAt =
                    SystemClock.elapsedRealtime()

                hasStarted = true

                firebaseMessage =
                    "Terhubung: sesi awal berhasil disimpan."

                isRunning = true

            } catch (error: Exception) {

                firebaseMessage =
                    "Gagal memulai simulasi: " +
                            (
                                    error.localizedMessage
                                        ?: "Kesalahan Firebase"
                                    )

            } finally {

                isStarting = false
            }
        }
    }

    // =================================================
    // FUNGSI SELESAIKAN SIMULASI MANUAL
    // =================================================

    fun finishSimulationManually() {

        if (
            simulator == null ||
            !isRunning ||
            isFinishing
        ) {
            return
        }

        val startTime = simulationStartedAt
            ?: SystemClock.elapsedRealtime()

        val elapsedMillis = (
                SystemClock.elapsedRealtime() - startTime
                ).coerceAtLeast(0L)

        val finalState = simulator
            .positionAt(elapsedMillis)
            .let { state ->

                if (state.status == "ARRIVED") {
                    state
                } else {
                    state.copy(status = "FINISHED")
                }
            }

        gpsState = finalState

        requestFinish(
            finalState = finalState,
            action = FinishAction.SUMMARY
        )
    }

    // =================================================
    // FUNGSI BATALKAN SIMULASI
    // =================================================

    fun cancelSimulation() {

        if (
            simulator == null ||
            !isRunning ||
            isFinishing
        ) {
            return
        }

        val startTime = simulationStartedAt
            ?: SystemClock.elapsedRealtime()

        val elapsedMillis = (
                SystemClock.elapsedRealtime() - startTime
                ).coerceAtLeast(0L)

        val cancelledState = simulator
            .positionAt(elapsedMillis)
            .copy(status = "CANCELLED")

        gpsState = cancelledState

        requestFinish(
            finalState = cancelledState,
            action = FinishAction.CANCEL
        )
    }

    // =================================================
    // TAMPILAN
    // =================================================

    Scaffold(
        containerColor = BackgroundLight
    ) { innerPadding ->

        Column(

            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = 20.dp,
                    vertical = 20.dp
                ),

            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // =========================================
            // HEADER
            // =========================================

            Row(

                modifier = Modifier.fillMaxWidth(),

                verticalAlignment = Alignment.CenterVertically,

                horizontalArrangement = Arrangement.spacedBy(12.dp)

            ) {

                Button(

                    onClick = onBack,

                    enabled = !isRunning &&
                            !isStarting &&
                            !isFinishing &&
                            pendingFinalState == null,

                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = TextPrimary,
                        disabledContainerColor = BorderLight
                    )

                ) {

                    Text("Kembali")
                }

                Column {

                    Text(
                        text = "Simulasi Ambulans",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Text(
                        text = "Virtual GPS Rawamangun",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            // =========================================
            // SIMULATOR ERROR
            // =========================================

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
                            text = "Simulasi tidak dapat dibuat",
                            fontWeight = FontWeight.Bold,
                            color = RedPrimary
                        )

                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        Text(
                            text = simulatorResult.exceptionOrNull()
                                ?.localizedMessage
                                ?: "Periksa konfigurasi Firebase."
                        )
                    }
                }

            } else {

                val current = gpsState
                    ?: simulator.positionAt(0L)

                val remainingSeconds = (
                        setup.durationSeconds -
                                current.elapsedSeconds
                        ).coerceAtLeast(0.0)

                // =====================================
                // INFORMASI TUJUAN
                // =====================================

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
                            text = "Tujuan ambulans",
                            color = TextSecondary
                        )

                        Text(
                            text = setup.trafficLight,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = RedPrimary
                        )

                        Text(
                            text = "Kelompok ${setup.trafficGroup}",
                            color = TextSecondary
                        )

                        Text(
                            text = "Arah pendekatan: " +
                                    (
                                            config.trafficLights[
                                                setup.trafficLight
                                            ]?.approach ?: "Tidak tersedia"
                                            ),
                            color = TextPrimary
                        )
                    }
                }

                // =====================================
                // STATUS DAN PROGRESS
                // =====================================

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
                            text = "Status ambulans",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )

                        Text(

                            text = when {

                                current.status == "ARRIVED" ->
                                    "TIBA DI TUJUAN"

                                current.status == "CANCELLED" ->
                                    "DIBATALKAN"

                                current.status == "FINISHED" ->
                                    "SELESAI MANUAL"

                                isRunning ->
                                    "AMBULANS BERGERAK"

                                hasStarted ->
                                    "SIMULASI DIHENTIKAN"

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
                                text = "${(current.progress * 100).toInt()}%",
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text =
                                    "${current.elapsedSeconds.toInt()} / " +
                                            "${setup.durationSeconds} detik",
                                color = TextSecondary
                            )
                        }

                        CoordinateRow(
                            label = "Sisa waktu target",
                            value = "${remainingSeconds.toInt()} detik"
                        )
                    }
                }

                // =====================================
                // DATA GPS
                // =====================================

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
                            text = "Koordinat GPS virtual",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )

                        CoordinateRow(
                            label = "Latitude",
                            value = formatCoordinate(current.latitude)
                        )

                        CoordinateRow(
                            label = "Longitude",
                            value = formatCoordinate(current.longitude)
                        )

                        CoordinateRow(
                            label = "Jarak ditempuh",
                            value = "${current.distanceTravelledMeters.formatOneDecimal()} m"
                        )

                        CoordinateRow(
                            label = "Sisa jarak ke tujuan",
                            value = "${current.distanceRemainingMeters.formatOneDecimal()} m"
                        )

                        CoordinateRow(
                            label = "Kecepatan virtual",
                            value = "${current.speedMetersPerSecond.formatOneDecimal()} m/dtk"
                        )
                    }
                }

                // =====================================
                // PENGATURAN
                // =====================================

                Surface(

                    modifier = Modifier.fillMaxWidth(),

                    shape = RoundedCornerShape(22.dp),

                    color = Color.White

                ) {

                    Column(

                        modifier = Modifier.padding(20.dp),

                        verticalArrangement = Arrangement.spacedBy(9.dp)

                    ) {

                        Text(
                            text = "Pengaturan simulasi",
                            fontWeight = FontWeight.Bold
                        )

                        CoordinateRow(
                            "Jarak awal",
                            "${setup.initialDistance} m"
                        )

                        CoordinateRow(
                            "Durasi target",
                            "${setup.durationSeconds} detik"
                        )

                        CoordinateRow(
                            "Interval update",
                            "${setup.markerIntervalSeconds} detik"
                        )
                    }
                }

                // =====================================
                // STATUS FIREBASE
                // =====================================

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
                            firebaseMessage.startsWith("Sesi selesai") ||
                            firebaseMessage.startsWith("Simulasi berhasil")
                        ) {
                            SuccessGreen
                        } else {
                            TextSecondary
                        },

                        fontSize = 12.sp,

                        lineHeight = 18.sp
                    )
                }

                // =====================================
                // TOMBOL SAAT SEDANG BERGERAK
                // =====================================

                if (isRunning) {

                    Button(

                        onClick = {
                            finishSimulationManually()
                        },

                        enabled = !isFinishing,

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
                            text = if (isFinishing) {
                                "Menyelesaikan Simulasi..."
                            } else {
                                "Selesaikan Simulasi"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(

                        onClick = {
                            cancelSimulation()
                        },

                        enabled = !isFinishing,

                        modifier = Modifier.fillMaxWidth(),

                        shape = CircleShape,

                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = TextPrimary,
                            disabledContainerColor = BorderLight
                        )

                    ) {

                        Text("Batalkan")
                    }

                } else if (pendingFinalState != null) {

                    // =================================
                    // COBA ULANG SIMPAN FINAL STATE
                    // =================================

                    Button(

                        onClick = {
                            scope.launch {
                                persistPendingFinish()
                            }
                        },

                        enabled = !isFinishing,

                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),

                        shape = CircleShape,

                        colors = ButtonDefaults.buttonColors(
                            containerColor = RedPrimary
                        )

                    ) {

                        Text(
                            text = if (isFinishing) {
                                "Menyimpan..."
                            } else {
                                "Coba Simpan Lagi"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }

                } else if (!hasStarted) {

                    // =================================
                    // MULAI SIMULASI
                    // =================================

                    Button(

                        onClick = {
                            startSimulation()
                        },

                        enabled = !isStarting && !isFinishing,

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
                            text = if (isStarting) {
                                "Menghubungkan Firebase..."
                            } else {
                                "Mulai Simulasi"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(

                    text =
                        "GPS ini merupakan koordinat virtual. " +
                                "Posisi simulasi dikirim ke Firebase, " +
                                "bukan GPS fisik HP.",

                    color = TextSecondary,

                    fontSize = 11.sp,

                    lineHeight = 16.sp
                )
            }
        }
    }
}


// =====================================================
// FIREBASE HELPER
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
// PROGRESS BAR
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


// =====================================================
// BARIS KOORDINAT / INFORMASI
// =====================================================

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
            text = label,
            color = TextSecondary,
            fontSize = 13.sp
        )

        Text(
            text = value,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}


// =====================================================
// FORMAT KOORDINAT
// =====================================================

private fun formatCoordinate(
    value: Double
): String {

    return String.format(
        Locale.US,
        "%.7f",
        value
    )
}


private fun Double.formatOneDecimal(): String {

    return String.format(
        Locale.US,
        "%.1f",
        this
    )
}