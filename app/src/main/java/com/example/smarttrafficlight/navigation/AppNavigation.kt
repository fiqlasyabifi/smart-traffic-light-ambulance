package com.example.smarttrafficlight.navigation

import android.net.Uri
import android.util.Log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

import com.google.firebase.auth.FirebaseAuth

import com.example.smarttrafficlight.data.firebase.TrafficDashboardStatus
import com.example.smarttrafficlight.data.firebase.TrafficMapConfig
import com.example.smarttrafficlight.data.firebase.TrafficMapRepository
import com.example.smarttrafficlight.data.firebase.TrafficStatusRepository
import com.example.smarttrafficlight.data.firebase.SimulationHistoryRecord
import com.example.smarttrafficlight.data.firebase.SimulationHistoryRepository

import com.example.smarttrafficlight.feature.analysis.EditorRoiScreen
import com.example.smarttrafficlight.feature.analysis.CameraScreen
import com.example.smarttrafficlight.feature.analysis.WebcamLaptopScreen
import com.example.smarttrafficlight.feature.analysis.PhotoAnalysisScreen
import com.example.smarttrafficlight.feature.analysis.PhotoUploadScreen
import com.example.smarttrafficlight.feature.analysis.RoiSelection
import com.example.smarttrafficlight.feature.analysis.SourceSelectionScreen
import com.example.smarttrafficlight.feature.analysis.VideoAnalysisScreen
import com.example.smarttrafficlight.feature.analysis.VideoSelection
import com.example.smarttrafficlight.feature.analysis.VideoUploadScreen

import com.example.smarttrafficlight.feature.history.HistoryScreen

import com.example.smarttrafficlight.feature.home.HomeScreen
import com.example.smarttrafficlight.feature.home.HomeUiState
import com.example.smarttrafficlight.feature.home.TrafficGroupUiState

import com.example.smarttrafficlight.feature.phase.PhaseCheckScreen
import com.example.smarttrafficlight.feature.phase.TrafficPhase

import com.example.smarttrafficlight.feature.setup.SetupScreen
import com.example.smarttrafficlight.feature.setup.SimulationSetup

import com.example.smarttrafficlight.feature.simulation.AmbulanceSimulationScreen
import com.example.smarttrafficlight.feature.simulation.SimulationSummaryData
import com.example.smarttrafficlight.feature.simulation.SimulationSummaryScreen

import com.example.smarttrafficlight.ui.theme.BackgroundLight


@Composable
fun AppNavigation() {

    // =====================================================
    // NAVIGATION
    // =====================================================

    val navController = rememberNavController()

    // =====================================================
    // REPOSITORY
    // =====================================================

    val mapRepository = remember {
        TrafficMapRepository()
    }

    val trafficStatusRepository = remember {
        TrafficStatusRepository()
    }

    val historyRepository = remember {
        SimulationHistoryRepository()
    }

    // =====================================================
    // STATE SETUP SIMULASI
    // =====================================================

    var simulationSetup by remember {
        mutableStateOf(SimulationSetup())
    }

    // =====================================================
    // STATE FOTO
    // =====================================================

    var selectedPhotoUri by remember {
        mutableStateOf<Uri?>(null)
    }

    var selectedPhotoRoi by remember {
        mutableStateOf<RoiSelection?>(null)
    }

    // =====================================================
    // STATE VIDEO
    // =====================================================

    var selectedVideo by remember {
        mutableStateOf<VideoSelection?>(null)
    }

    var selectedVideoRoi by remember {
        mutableStateOf<RoiSelection?>(null)
    }

    // =====================================================
    // STATE HASIL ANALISIS
    // =====================================================

    var initialSignal by remember {
        mutableStateOf("—")
    }

    var selectedAnalysisSource by remember {
        mutableStateOf("Tidak dilakukan")
    }

    var analysisVehicleCount by remember {
        mutableStateOf<Int?>(null)
    }

    var analysisDensityLabel by remember {
        mutableStateOf<String?>(null)
    }

    // =====================================================
    // STATE RINGKASAN SIMULASI
    // =====================================================

    var currentSimulationSummary by remember {
        mutableStateOf<SimulationSummaryData?>(null)
    }

    // =====================================================
    // STATE KONFIGURASI PETA
    // =====================================================

    var trafficMapConfig by remember {
        mutableStateOf<TrafficMapConfig?>(null)
    }

    var mapConfigMessage by remember {
        mutableStateOf<String?>(null)
    }

    var showMapConfigDialog by remember {
        mutableStateOf(false)
    }

    var mapLoading by remember {
        mutableStateOf(true)
    }

    // =====================================================
    // STATE FIREBASE AUTHENTICATION
    // =====================================================

    var firebaseAuthenticated by remember {
        mutableStateOf(
            FirebaseAuth.getInstance().currentUser != null
        )
    }

    // =====================================================
    // STATE DASHBOARD
    // =====================================================

    var dashboardStatus by remember {
        mutableStateOf(TrafficDashboardStatus())
    }

    // =====================================================
    // FIREBASE REAL-TIME LISTENER
    // =====================================================

    DisposableEffect(firebaseAuthenticated) {

        val stopListening: (() -> Unit)? =
            if (firebaseAuthenticated) {

                trafficStatusRepository.observeStatus(

                    onChanged = { status ->

                        dashboardStatus = status
                    },

                    onError = { error ->

                        Log.e(
                            "TrafficStatus",
                            error
                        )
                    }
                )

            } else {

                null
            }

        onDispose {
            stopListening?.invoke()
        }
    }

    // =====================================================
    // FIREBASE AUTH DAN MAP CONFIG
    // =====================================================

    LaunchedEffect(Unit) {

        val auth = FirebaseAuth.getInstance()

        fun readMapConfig() {

            mapLoading = true

            mapRepository.loadConfig(

                onSuccess = { config ->

                    trafficMapConfig = config

                    mapLoading = false

                    mapConfigMessage = buildString {

                        appendLine("KONFIGURASI FIREBASE BERHASIL")
                        appendLine()

                        appendLine(
                            "Koordinat sudut: " +
                                    "${config.intersectionCorners.size}/4"
                        )

                        appendLine(
                            "Titik pendekatan: " +
                                    "${config.approachReferencePoints.size}/4"
                        )

                        appendLine(
                            "Traffic light: " +
                                    "${config.trafficLights.size}/6"
                        )

                        appendLine(
                            "Urutan fase: " +
                                    config.phaseOrder.joinToString(" -> ") {
                                        it.group
                                    }
                        )

                        appendLine()

                        append(
                            "Android berhasil membaca konfigurasi peta."
                        )
                    }

                    showMapConfigDialog = true
                },

                onError = { error ->

                    mapLoading = false

                    mapConfigMessage =
                        "GAGAL MEMBACA FIREBASE\n\n$error"

                    showMapConfigDialog = true
                }
            )
        }

        if (auth.currentUser != null) {

            firebaseAuthenticated = true

            readMapConfig()

        } else {

            auth.signInAnonymously()

                .addOnSuccessListener {

                    firebaseAuthenticated = true

                    readMapConfig()
                }

                .addOnFailureListener { error ->

                    mapLoading = false

                    mapConfigMessage =
                        "AUTENTIKASI FIREBASE GAGAL\n\n" +
                                (
                                        error.localizedMessage
                                            ?: "Kesalahan tidak diketahui."
                                        )

                    showMapConfigDialog = true
                }
        }
    }

    // =====================================================
    // NAVIGASI APLIKASI
    // =====================================================

    NavHost(

        navController = navController,

        startDestination = "home"

    ) {

        // =================================================
        // 1. HOME / DASHBOARD
        // =================================================

        composable("home") {

            HomeScreen(

                onStartSimulation = {

                    navController.navigate("setup")
                },

                onOpenHistory = {

                    navController.navigate("history")
                },

                uiState = dashboardStatus.toHomeUiState()
            )
        }

        // =================================================
        // 2. SETUP SIMULASI
        // =================================================

        composable("setup") {

            SetupScreen(

                onBack = {

                    navController.popBackStack()
                },

                onContinue = { setup ->

                    simulationSetup = setup

                    // Reset data analisis foto.

                    selectedPhotoUri = null

                    selectedPhotoRoi = null

                    // Reset data analisis video.

                    selectedVideo = null

                    selectedVideoRoi = null

                    // Reset sumber analisis.

                    initialSignal = "—"

                    selectedAnalysisSource = "Tidak dilakukan"

                    analysisVehicleCount = null
                    analysisDensityLabel = null

                    currentSimulationSummary = null

                    navController.navigate("phase-check")
                }
            )
        }

        // =================================================
        // 3. PEMERIKSAAN FASE LAMPU
        // =================================================

        composable("phase-check") {

            PhaseCheckScreen(

                trafficLight = simulationSetup.trafficLight,

                trafficGroup = simulationSetup.trafficGroup,

                phase = dashboardStatus.toTrafficPhase(
                    simulationSetup.trafficLight
                ),

                onBack = {

                    navController.popBackStack()
                },

                onContinueRed = {

                    initialSignal = "MERAH"

                    selectedAnalysisSource = "Belum dipilih"

                    analysisVehicleCount = null
                    analysisDensityLabel = null

                    navController.navigate("source-selection")
                },

                onContinueGreen = {

                    initialSignal = "HIJAU"

                    selectedAnalysisSource = "Tidak dilakukan"

                    analysisVehicleCount = null
                    analysisDensityLabel = null

                    navController.navigate("ambulance-simulation")
                }
            )
        }

        // =================================================
        // 4. PILIH SUMBER ANALISIS
        // =================================================

        composable("source-selection") {

            val trafficLight = simulationSetup.trafficLight

            val trafficGroup = when (
                trafficLight.uppercase()
            ) {

                "TL1", "TL2" -> "A"

                "TL5", "TL6" -> "B"

                "TL3", "TL4" -> "C"

                else -> simulationSetup.trafficGroup
            }

            SourceSelectionScreen(

                trafficLight = trafficLight,

                trafficGroup = trafficGroup,

                onBack = {

                    navController.popBackStack()
                },

                onCameraClick = {

                    selectedAnalysisSource = "Kamera HP"

                    analysisVehicleCount = null
                    analysisDensityLabel = null

                    navController.navigate("camera")
                },

                onPhotoClick = {

                    selectedAnalysisSource = "Foto"

                    analysisVehicleCount = null
                    analysisDensityLabel = null

                    navController.navigate("upload-photo")
                },

                onWebcamClick = {

                    selectedAnalysisSource = "Webcam Laptop"

                    analysisVehicleCount = null
                    analysisDensityLabel = null

                    navController.navigate("webcam")
                },

                onVideoClick = {

                    selectedAnalysisSource = "Video"

                    analysisVehicleCount = null
                    analysisDensityLabel = null

                    navController.navigate("upload-video")
                }
            )
        }

        // =================================================
        // 5. KAMERA HP
        // =================================================

        composable("camera") {

            val trafficLight = simulationSetup.trafficLight
            val trafficGroup = when (trafficLight.uppercase()) {
                "TL1", "TL2" -> "A"
                "TL5", "TL6" -> "B"
                "TL3", "TL4" -> "C"
                else -> simulationSetup.trafficGroup
            }

            CameraScreen(
                trafficLight = trafficLight,
                trafficGroup = trafficGroup,
                onBack = { navController.popBackStack() },
                onCaptureForAnalysis = { uri, roi ->
                    selectedPhotoUri = uri
                    selectedPhotoRoi = roi
                    selectedAnalysisSource = "Kamera HP"
                    analysisVehicleCount = null
                    analysisDensityLabel = null
                    navController.navigate("photo-analysis")
                }
            )
        }

        // =================================================
        // 6. UNGGAH FOTO
        // =================================================

        composable("upload-photo") {

            val trafficLight = simulationSetup.trafficLight

            val trafficGroup = when (
                trafficLight.uppercase()
            ) {

                "TL1", "TL2" -> "A"

                "TL5", "TL6" -> "B"

                "TL3", "TL4" -> "C"

                else -> simulationSetup.trafficGroup
            }

            PhotoUploadScreen(

                trafficLight = trafficLight,

                trafficGroup = trafficGroup,

                selectedImageUri = selectedPhotoUri,

                onImageSelected = { uri ->

                    selectedPhotoUri = uri

                    // ROI lama tidak berlaku jika foto diganti.

                    selectedPhotoRoi = null

                    analysisVehicleCount = null
                    analysisDensityLabel = null
                },

                onBack = {

                    navController.popBackStack()
                },

                onContinue = {

                    if (selectedPhotoUri != null) {

                        selectedPhotoRoi = null

                        navController.navigate("photo-roi")
                    }
                }
            )
        }

        // =================================================
        // 7. EDITOR POLYGON ROI FOTO
        // =================================================

        composable("photo-roi") {

            EditorRoiScreen(

                imageUri = selectedPhotoUri,

                trafficLight = simulationSetup.trafficLight,

                trafficGroup = simulationSetup.trafficGroup,

                onBack = {

                    navController.popBackStack()
                },

                onContinue = { roi ->

                    selectedPhotoRoi = roi

                    navController.navigate("photo-analysis")
                }
            )
        }

        // =================================================
        // 8. HASIL ANALISIS FOTO
        // =================================================

        composable("photo-analysis") {

            PhotoAnalysisScreen(

                imageUri = selectedPhotoUri,

                roi = selectedPhotoRoi,

                trafficLight = simulationSetup.trafficLight,

                trafficGroup = simulationSetup.trafficGroup,

                onBack = {

                    navController.popBackStack()
                },

                onRetake = {

                    selectedPhotoRoi = null
                    analysisVehicleCount = null
                    analysisDensityLabel = null

                    val sourceRoute = when (selectedAnalysisSource) {
                        "Kamera HP" -> "camera"
                        "Webcam Laptop" -> "webcam"
                        else -> "upload-photo"
                    }

                    navController.popBackStack(sourceRoute, false)
                },

                onStartSimulation = {

                    navController.navigate(
                        "ambulance-simulation"
                    )
                },

                onAnalysisCompleted = { result ->

                    analysisVehicleCount =
                        result.counts.total
                }
            )
        }

        // =================================================
        // 9. WEBCAM LAPTOP
        // =================================================

        composable("webcam") {

            val trafficLight = simulationSetup.trafficLight
            val trafficGroup = when (trafficLight.uppercase()) {
                "TL1", "TL2" -> "A"
                "TL5", "TL6" -> "B"
                "TL3", "TL4" -> "C"
                else -> simulationSetup.trafficGroup
            }

            WebcamLaptopScreen(
                trafficLight = trafficLight,
                trafficGroup = trafficGroup,
                onBack = { navController.popBackStack() },
                onUseResult = { vehicleCount, densityLabel ->
                    selectedAnalysisSource = "Webcam Laptop"
                    analysisVehicleCount = vehicleCount
                    analysisDensityLabel = densityLabel
                    navController.navigate("ambulance-simulation")
                }
            )
        }

        // 10. UNGGAH VIDEO
        // =================================================

        composable("upload-video") {

            VideoUploadScreen(

                trafficLight = simulationSetup.trafficLight,

                trafficGroup = simulationSetup.trafficGroup,

                onBack = {

                    navController.popBackStack()
                },

                onContinue = { video ->

                    selectedVideo = video

                    selectedVideoRoi = null

                    analysisVehicleCount = null
                    analysisDensityLabel = null

                    navController.navigate("video-roi")
                }
            )
        }

        // =================================================
        // 11. EDITOR POLYGON ROI VIDEO
        // =================================================

        composable("video-roi") {

            EditorRoiScreen(

                imageUri = selectedVideo?.roiFrameUri,

                trafficLight = simulationSetup.trafficLight,

                trafficGroup = simulationSetup.trafficGroup,

                onBack = {

                    navController.popBackStack()
                },

                onContinue = { roi ->

                    selectedVideoRoi = roi

                    navController.navigate("video-analysis")
                }
            )
        }

        // =================================================
        // 12. HASIL ANALISIS VIDEO
        // =================================================

        composable("video-analysis") {

            VideoAnalysisScreen(

                video = selectedVideo,

                roi = selectedVideoRoi,

                trafficLight = simulationSetup.trafficLight,

                trafficGroup = simulationSetup.trafficGroup,

                onBack = {

                    navController.popBackStack()
                },

                onRetryAnalysis = {

                    navController.popBackStack(
                        "video-roi",
                        false
                    )
                },

                onStartSimulation = {

                    navController.navigate(
                        "ambulance-simulation"
                    )
                },

                onAnalysisCompleted = { result ->

                    // Untuk video, angka yang diteruskan
                    // adalah jumlah kendaraan pada interval puncak.

                    analysisVehicleCount =
                        result.peakVehicles
                }
            )
        }

        // =================================================
        // 13. SIMULASI AMBULANS
        // =================================================

        composable("ambulance-simulation") {

            val config = trafficMapConfig

            when {

                config != null -> {

                    val summaryContext =
                        SimulationSummaryData(

                            trafficLight =
                                simulationSetup.trafficLight,

                            trafficGroup =
                                simulationSetup.trafficGroup,

                            initialSignal =
                                initialSignal,

                            sourceLabel =
                                selectedAnalysisSource,

                            vehicleCount =
                                analysisVehicleCount,

                            densityLabel =
                                analysisDensityLabel,

                            initialDistanceMeters =
                                simulationSetup.initialDistance,

                            targetDurationSeconds =
                                simulationSetup.durationSeconds,

                            actualDurationSeconds =
                                0.0
                        )

                    AmbulanceSimulationScreen(

                        config = config,

                        setup = simulationSetup,

                        summaryContext = summaryContext,

                        onBack = {

                            navController.popBackStack()
                        },

                        onSimulationFinished = { finishedSummary ->

                            // Simpan ringkasan untuk halaman berikutnya.

                            currentSimulationSummary =
                                finishedSummary

                            // =================================
                            // SIMPAN RIWAYAT FIREBASE
                            // =================================

                            val historyRecord =
                                SimulationHistoryRecord(

                                    trafficLight =
                                        finishedSummary.trafficLight,

                                    trafficGroup =
                                        finishedSummary.trafficGroup,

                                    initialSignal =
                                        finishedSummary.initialSignal,

                                    sourceLabel =
                                        finishedSummary.sourceLabel,

                                    vehicleCount =
                                        finishedSummary.vehicleCount,

                                    densityLabel =
                                        finishedSummary.densityLabel,

                                    initialDistanceMeters =
                                        finishedSummary.initialDistanceMeters,

                                    targetDurationSeconds =
                                        finishedSummary.targetDurationSeconds,

                                    actualDurationSeconds =
                                        finishedSummary.actualDurationSeconds,

                                    completedAtMillis =
                                        finishedSummary.completedAtMillis
                                )

                            historyRepository.saveHistory(

                                record = historyRecord

                            ) { success, error ->

                                if (!success) {

                                    Log.e(
                                        "SimulationHistory",
                                        error ?: "Gagal menyimpan riwayat."
                                    )
                                }
                            }

                            // =================================
                            // BUKA RINGKASAN
                            // =================================

                            navController.navigate(
                                "simulation-summary"
                            ) {

                                popUpTo(
                                    "ambulance-simulation"
                                ) {
                                    inclusive = true
                                }

                                launchSingleTop = true
                            }
                        }
                    )
                }

                mapLoading -> {

                    PlaceholderScreen(

                        title = "Memuat konfigurasi peta",

                        subtitle =
                            "Android sedang mengambil konfigurasi koordinat dari Firebase.",

                        onBack = {

                            navController.popBackStack()
                        }
                    )
                }

                else -> {

                    PlaceholderScreen(

                        title = "Konfigurasi Firebase gagal",

                        subtitle = mapConfigMessage
                            ?: "Data konfigurasi peta belum tersedia.",

                        onBack = {

                            navController.popBackStack()
                        }
                    )
                }
            }
        }

        // =================================================
        // 14. RINGKASAN SIMULASI
        // =================================================

        composable("simulation-summary") {

            val summary = currentSimulationSummary

            if (summary != null) {

                SimulationSummaryScreen(

                    summary = summary,

                    onBackToHome = {

                        navController.navigate("home") {

                            popUpTo("home") {
                                inclusive = false
                            }

                            launchSingleTop = true
                        }
                    }
                )

            } else {

                PlaceholderScreen(

                    title = "Ringkasan tidak tersedia",

                    subtitle =
                        "Data hasil simulasi belum tersedia.",

                    onBack = {

                        navController.popBackStack()
                    }
                )
            }
        }

        // =================================================
        // 15. RIWAYAT SIMULASI
        // =================================================

        composable("history") {

            HistoryScreen(

                onBackToHome = {

                    navController.navigate("home") {

                        popUpTo("home") {
                            inclusive = false
                        }

                        launchSingleTop = true
                    }
                }
            )
        }
    }

    // =====================================================
    // DIALOG FIREBASE
    // =====================================================

    if (
        showMapConfigDialog &&
        mapConfigMessage != null
    ) {

        AlertDialog(

            onDismissRequest = {
                showMapConfigDialog = false
            },

            title = {
                Text("Pemeriksaan Firebase")
            },

            text = {
                Text(mapConfigMessage.orEmpty())
            },

            confirmButton = {

                TextButton(

                    onClick = {
                        showMapConfigDialog = false
                    }

                ) {

                    Text("Tutup")
                }
            }
        )
    }
}


// =========================================================
// KONVERSI STATUS FIREBASE MENJADI HOME UI STATE
// =========================================================

private fun TrafficDashboardStatus.toHomeUiState(): HomeUiState {

    val currentState = state.uppercase()

    val currentPhase = phase.uppercase()

    val currentCondition = condition.uppercase()

    // MENENTUKAN KELOMPOK AKTIF

    val groupCode = when {

        currentState.startsWith("A_") ||
                currentPhase.contains("FASE A") -> "A"

        currentState.startsWith("B_") ||
                currentPhase.contains("FASE B") -> "B"

        currentState.startsWith("C_") ||
                currentPhase.contains("FASE C") -> "C"

        else -> null
    }

    // MENENTUKAN KONDISI LAMPU

    val signalStatus = when {

        currentCondition.contains("KUNING") ->
            "KUNING"

        currentCondition.contains("HIJAU") ->
            "HIJAU"

        currentCondition.contains("MERAH") ->
            "MERAH"

        else ->
            "MENUNGGU"
    }

    // KELOMPOK AKTIF

    val activeGroup = when {

        !esp32Online -> "—"

        groupCode == "A" -> "TLA"

        groupCode == "B" -> "TLB"

        groupCode == "C" -> "TLC"

        else -> "TRANSISI"
    }

    // PASANGAN TRAFFIC LIGHT

    val activeLights = when {

        groupCode == "A" ->
            "TL1 + TL2"

        groupCode == "B" ->
            "TL5 + TL6"

        groupCode == "C" ->
            "TL3 + TL4"

        !esp32Online ->
            "Menunggu data lampu"

        else ->
            "Semua jalur merah"
    }

    // STATUS UTAMA

    val activeStatus = when {

        !esp32Online ->
            "MENUNGGU DATA"

        groupCode == null ->
            "SEMUA MERAH"

        else ->
            signalStatus
    }

    // DAFTAR KELOMPOK LAMPU

    val groupList = listOf(

        Triple(
            "A",
            "TLA",
            "TL1 + TL2"
        ),

        Triple(
            "B",
            "TLB",
            "TL5 + TL6"
        ),

        Triple(
            "C",
            "TLC",
            "TL3 + TL4"
        )
    )

    val groups = groupList.map { (code, name, lights) ->

        val isActiveGroup = groupCode == code

        val groupStatus = when {

            !esp32Online ->
                "MENUNGGU"

            isActiveGroup ->
                signalStatus

            else ->
                "MERAH"
        }

        TrafficGroupUiState(

            name = name,

            lights = lights,

            status = groupStatus,

            remainingSeconds =
                if (
                    isActiveGroup &&
                    !priorityHold &&
                    (remainingSeconds ?: 0) > 0
                ) {

                    remainingSeconds

                } else {

                    null
                }
        )
    }

    // STATUS BACKEND

    val backendConnection = when {

        !backendOnline ->
            "Offline"

        serialConnected ->
            "Online"

        else ->
            "Serial putus"
    }

    // STATUS ESP32

    val esp32Connection = when {

        !esp32Online ->
            "Offline"

        priorityHold ->
            "Green Hold ${priorityGroup ?: ""}"

        else ->
            "Online"
    }

    return HomeUiState(

        activeGroup = activeGroup,

        activeLights = activeLights,

        activeStatus = activeStatus,

        remainingSeconds =
            remainingSeconds?.takeIf {
                it > 0 && !priorityHold
            },

        backendStatus = backendConnection,

        esp32Status = esp32Connection,

        groups = groups
    )
}


// =========================================================
// KONVERSI STATUS ESP32 MENJADI TRAFFIC PHASE
// =========================================================

private fun TrafficDashboardStatus.toTrafficPhase(
    trafficLightId: String
): TrafficPhase {

    if (!esp32Online || state.isBlank()) {

        return TrafficPhase.WAITING
    }

    // KELOMPOK DARI TRAFFIC LIGHT

    val selectedGroup = when (
        trafficLightId.uppercase()
    ) {

        "TL1", "TL2" -> "A"

        "TL5", "TL6" -> "B"

        "TL3", "TL4" -> "C"

        else -> return TrafficPhase.WAITING
    }

    val currentState = state.uppercase()

    val currentCondition = condition.uppercase()

    // STARTUP DAN ALL RED

    if (
        currentState.startsWith("STARTUP") ||
        currentState.startsWith("ALL_RED")
    ) {

        return TrafficPhase.WAITING
    }

    // KELOMPOK AKTIF

    val activeGroup = when {

        currentState.startsWith("A_") -> "A"

        currentState.startsWith("B_") -> "B"

        currentState.startsWith("C_") -> "C"

        else -> null
    }

    if (activeGroup == null) {

        return TrafficPhase.WAITING
    }

    // KELOMPOK TIDAK AKTIF = MERAH

    if (activeGroup != selectedGroup) {

        return TrafficPhase.RED
    }

    // KONDISI LAMPU

    return when {

        currentCondition.contains("HIJAU") ->
            TrafficPhase.GREEN

        currentCondition.contains("KUNING") ->
            TrafficPhase.YELLOW

        currentCondition.contains("MERAH") ->
            TrafficPhase.RED

        currentState.endsWith("_GREEN") ->
            TrafficPhase.GREEN

        currentState.endsWith("_YELLOW") ->
            TrafficPhase.YELLOW

        currentState.endsWith("_RED_YELLOW") ->
            TrafficPhase.RED

        else ->
            TrafficPhase.WAITING
    }
}


// =========================================================
// PLACEHOLDER SCREEN
// =========================================================

@Composable
private fun PlaceholderScreen(
    title: String,
    onBack: () -> Unit,
    subtitle: String = ""
) {

    Scaffold(

        containerColor = BackgroundLight

    ) { innerPadding ->

        Column(

            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),

            verticalArrangement = Arrangement.Center

        ) {

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall
            )

            if (subtitle.isNotBlank()) {

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            Button(
                onClick = onBack
            ) {

                Text("Kembali")
            }
        }
    }
}