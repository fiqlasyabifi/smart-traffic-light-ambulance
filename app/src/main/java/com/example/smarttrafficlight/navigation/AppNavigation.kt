package com.example.smarttrafficlight.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.smarttrafficlight.feature.home.HomeScreen
import com.example.smarttrafficlight.feature.phase.PhaseCheckScreen
import com.example.smarttrafficlight.feature.phase.TrafficPhase
import com.example.smarttrafficlight.feature.setup.SetupScreen
import com.example.smarttrafficlight.feature.setup.SimulationSetup
import com.example.smarttrafficlight.ui.theme.BackgroundLight

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    var simulationSetup by remember {
        mutableStateOf(SimulationSetup())
    }

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

                // Sementara menunggu integrasi Firebase dan ESP32.
                phase = TrafficPhase.WAITING,

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
                subtitle = "Tahap berikutnya: pilih analisis melalui kamera HP atau webcam laptop.",
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // SIMULASI AMBULANS
        composable("ambulance-simulation") {
            PlaceholderScreen(
                title = "Simulasi Ambulans",
                subtitle = """
                    Jalur: ${simulationSetup.trafficLight}
                    Kelompok: ${simulationSetup.trafficGroup}
                    Jarak awal: ${simulationSetup.initialDistance} meter
                    Durasi: ${simulationSetup.durationSeconds} detik
                    Interval marker: ${simulationSetup.markerIntervalSeconds} detik
                """.trimIndent(),
                onBack = {
                    navController.popBackStack()
                }
            )
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
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(onClick = onBack) {
                Text("Kembali")
            }
        }
    }
}