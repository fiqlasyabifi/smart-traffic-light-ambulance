package com.example.smarttrafficlight.navigation

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
import com.example.smarttrafficlight.data.firebase.TrafficMapConfig
import com.example.smarttrafficlight.data.firebase.TrafficMapRepository
import com.example.smarttrafficlight.feature.home.HomeScreen
import com.example.smarttrafficlight.feature.phase.PhaseCheckScreen
import com.example.smarttrafficlight.feature.phase.TrafficPhase
import com.example.smarttrafficlight.feature.setup.SetupScreen
import com.example.smarttrafficlight.feature.setup.SimulationSetup
import com.example.smarttrafficlight.feature.simulation.AmbulanceSimulationScreen
import com.example.smarttrafficlight.ui.theme.BackgroundLight
import com.google.firebase.auth.FirebaseAuth

/**
 * MODE PENGUJIAN SEMENTARA.
 *
 * true  = langsung menampilkan fase hijau agar halaman GPS
 *         virtual bisa diuji tanpa menunggu ESP32.
 *
 * false = menunggu integrasi status fase dari sistem sebenarnya.
 *
 * Jangan gunakan true pada operasi sistem lalu lintas sebenarnya.
 */
private const val GPS_TEST_MODE = true

@Composable
fun AppNavigation() {

    val navController = rememberNavController()

    val mapRepository = remember {
        TrafficMapRepository()
    }

    var simulationSetup by remember {
        mutableStateOf(SimulationSetup())
    }

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
    // AUTENTIKASI FIREBASE DAN PEMBACAAN MAP CONFIG
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
                        append("Android berhasil membaca konfigurasi peta.")
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

            readMapConfig()

        } else {

            auth.signInAnonymously()
                .addOnSuccessListener {

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

        // BERANDA
        composable("home") {

            HomeScreen(

                onStartSimulation = {
                    navController.navigate("setup")
                },

                onOpenHistory = {
                    navController.navigate("history")
                }
            )
        }

        // SETUP SIMULASI
        composable("setup") {

            SetupScreen(

                onBack = {
                    navController.popBackStack()
                },

                onContinue = { setup ->

                    simulationSetup = setup

                    navController.navigate("phase-check")
                }
            )
        }

        // PEMERIKSAAN FASE LAMPU
        composable("phase-check") {

            PhaseCheckScreen(

                trafficLight = simulationSetup.trafficLight,

                trafficGroup = simulationSetup.trafficGroup,

                // Fase hijau sementara hanya untuk menguji GPS virtual.
                phase = if (GPS_TEST_MODE) {
                    TrafficPhase.GREEN
                } else {
                    TrafficPhase.WAITING
                },

                onBack = {
                    navController.popBackStack()
                },

                onContinueRed = {
                    navController.navigate("source-selection")
                },

                onContinueGreen = {
                    navController.navigate("ambulance-simulation")
                }
            )
        }

        // PILIH SUMBER ANALISIS
        composable("source-selection") {

            PlaceholderScreen(

                title = "Sumber Analisis Kendaraan",

                subtitle =
                    "Tahap berikutnya: pilih analisis melalui kamera HP atau webcam laptop.",

                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // SIMULASI AMBULANS
        composable("ambulance-simulation") {

            val config = trafficMapConfig

            when {

                config != null -> {

                    AmbulanceSimulationScreen(

                        config = config,

                        setup = simulationSetup,

                        onBack = {
                            navController.popBackStack()
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

        // RIWAYAT
        composable("history") {

            PlaceholderScreen(

                title = "Riwayat Simulasi",

                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }

    // =====================================================
    // DIALOG HASIL PEMBACAAN FIREBASE
    // =====================================================

    if (showMapConfigDialog && mapConfigMessage != null) {

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