package com.example.smarttrafficlight.feature.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.VideoLibrary

import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.example.smarttrafficlight.data.firebase.SimulationHistoryRecord
import com.example.smarttrafficlight.data.firebase.SimulationHistoryRepository

import com.example.smarttrafficlight.ui.theme.BackgroundLight
import com.example.smarttrafficlight.ui.theme.BorderLight
import com.example.smarttrafficlight.ui.theme.PrimaryRed
import com.example.smarttrafficlight.ui.theme.SurfaceWhite
import com.example.smarttrafficlight.ui.theme.TextPrimary
import com.example.smarttrafficlight.ui.theme.TextSecondary

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


// =====================================================
// HISTORY SCREEN
// =====================================================

@Composable
fun HistoryScreen(
    onBackToHome: () -> Unit
) {

    val repository = remember {
        SimulationHistoryRepository()
    }

    var records by remember {
        mutableStateOf<List<SimulationHistoryRecord>>(
            emptyList()
        )
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    // =========================================
    // FIREBASE LISTENER
    // =========================================

    DisposableEffect(repository) {

        val stopListening = repository.observeHistory(

            onChanged = { result ->

                records = result
                isLoading = false
                errorMessage = null
            },

            onError = { error ->

                errorMessage = error
                isLoading = false
            }
        )

        onDispose {
            stopListening()
        }
    }

    // =========================================
    // TAMPILAN
    // =========================================

    Scaffold(

        containerColor = BackgroundLight,

        bottomBar = {

            NavigationBar(
                containerColor = SurfaceWhite
            ) {

                NavigationBarItem(

                    selected = false,

                    onClick = onBackToHome,

                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Home,
                            contentDescription = "Beranda"
                        )
                    },

                    label = {
                        Text("Beranda")
                    },

                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryRed,
                        selectedTextColor = PrimaryRed,
                        indicatorColor = Color(0xFFFFE8EB)
                    )
                )

                NavigationBarItem(

                    selected = true,

                    onClick = {},

                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = "Riwayat"
                        )
                    },

                    label = {
                        Text("Riwayat")
                    },

                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryRed,
                        selectedTextColor = PrimaryRed,
                        indicatorColor = Color(0xFFFFE8EB)
                    )
                )
            }
        }

    ) { innerPadding ->

        Column(

            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = 20.dp,
                    vertical = 22.dp
                ),

            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // =====================================
            // HEADER
            // =====================================

            Row(

                modifier = Modifier.fillMaxWidth(),

                verticalAlignment = Alignment.CenterVertically,

                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                Surface(

                    modifier = Modifier.size(42.dp),

                    shape = CircleShape,

                    color = SurfaceWhite,

                    border = BorderStroke(
                        1.dp,
                        BorderLight
                    ),

                    onClick = onBackToHome

                ) {

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {

                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = "Kembali",
                            tint = TextPrimary
                        )
                    }
                }

                Column {

                    Text(
                        text = "Riwayat",
                        color = TextPrimary,
                        style = androidx.compose.material3.MaterialTheme
                            .typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Simulasi yang sudah selesai",
                        color = TextSecondary,
                        style = androidx.compose.material3.MaterialTheme
                            .typography.bodyMedium
                    )
                }
            }

            // =====================================
            // LOADING
            // =====================================

            if (isLoading) {

                Column(

                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(35.dp),

                    horizontalAlignment = Alignment.CenterHorizontally,

                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    CircularProgressIndicator(
                        color = PrimaryRed
                    )

                    Text(
                        text = "Memuat riwayat simulasi...",
                        color = TextSecondary
                    )
                }
            }

            // =====================================
            // ERROR
            // =====================================

            if (errorMessage != null) {

                HistoryMessageCard(
                    message = errorMessage.orEmpty()
                )
            }

            // =====================================
            // DATA KOSONG
            // =====================================

            if (!isLoading && errorMessage == null && records.isEmpty()) {

                HistoryMessageCard(
                    message =
                        "Belum ada simulasi yang tersimpan. " +
                                "Riwayat akan muncul setelah simulasi selesai."
                )
            }

            // =====================================
            // DAFTAR RIWAYAT
            // =====================================

            records.forEach { record ->

                HistoryItemCard(
                    record = record
                )
            }
        }
    }
}


// =====================================================
// KARTU RIWAYAT
// =====================================================

@Composable
private fun HistoryItemCard(
    record: SimulationHistoryRecord
) {

    val (icon, sourceDescription) =
        getSourceIcon(record.sourceLabel)

    Card(

        modifier = Modifier.fillMaxWidth(),

        shape = RoundedCornerShape(22.dp),

        border = BorderStroke(
            1.dp,
            BorderLight
        ),

        colors = CardDefaults.cardColors(
            containerColor = SurfaceWhite
        ),

        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )

    ) {

        Column(

            modifier = Modifier.padding(15.dp),

            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Row(

                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement = Arrangement.spacedBy(12.dp),

                verticalAlignment = Alignment.CenterVertically

            ) {

                Surface(

                    modifier = Modifier.size(54.dp),

                    shape = CircleShape,

                    color = Color(0xFFFFE8EB)

                ) {

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {

                        Icon(
                            imageVector = icon,
                            contentDescription = sourceDescription,
                            tint = PrimaryRed,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {

                    Text(

                        text =
                            "${record.trafficLight} · " +
                                    record.trafficGroup,

                        color = TextPrimary,

                        style = androidx.compose.material3.MaterialTheme
                            .typography.titleMedium,

                        fontWeight = FontWeight.Bold
                    )

                    Text(

                        text =
                            "${formatHistoryDate(record.completedAtMillis)} · " +
                                    record.sourceLabel,

                        color = TextSecondary,

                        style = androidx.compose.material3.MaterialTheme
                            .typography.bodySmall
                    )
                }

                Text(

                    text = formatDuration(
                        record.actualDurationSeconds
                    ),

                    color = TextPrimary,

                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = BackgroundLight,
                shape = RoundedCornerShape(12.dp)
            ) {

                Row(

                    modifier = Modifier.padding(12.dp),

                    horizontalArrangement = Arrangement.SpaceBetween,

                    verticalAlignment = Alignment.CenterVertically

                ) {

                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {

                        Text(
                            text = "Sinyal awal: ${record.initialSignal}",
                            color = TextSecondary,
                            style = androidx.compose.material3.MaterialTheme
                                .typography.bodySmall
                        )

                        Text(
                            text = "Jarak awal: ${record.initialDistanceMeters} m",
                            color = TextSecondary,
                            style = androidx.compose.material3.MaterialTheme
                                .typography.bodySmall
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {

                        Text(
                            text = record.densityLabel ?: "Kepadatan —",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            style = androidx.compose.material3.MaterialTheme
                                .typography.bodySmall
                        )

                        Text(
                            text = record.vehicleCount?.let {
                                "$it kendaraan"
                            } ?: "Tanpa hitungan",
                            color = TextSecondary,
                            style = androidx.compose.material3.MaterialTheme
                                .typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}


// =====================================================
// PESAN LOADING / KOSONG / ERROR
// =====================================================

@Composable
private fun HistoryMessageCard(
    message: String
) {

    Card(

        modifier = Modifier.fillMaxWidth(),

        shape = RoundedCornerShape(18.dp),

        colors = CardDefaults.cardColors(
            containerColor = SurfaceWhite
        ),

        border = BorderStroke(
            1.dp,
            BorderLight
        )

    ) {

        Text(
            text = message,
            modifier = Modifier.padding(18.dp),
            color = TextSecondary,
            style = androidx.compose.material3.MaterialTheme
                .typography.bodyMedium
        )
    }
}


// =====================================================
// IKON BERDASARKAN SUMBER ANALISIS
// =====================================================

private fun getSourceIcon(
    source: String
): Pair<ImageVector, String> {

    return when (source.lowercase()) {

        "webcam laptop" ->
            Icons.Outlined.Laptop to "Webcam Laptop"

        "video" ->
            Icons.Outlined.VideoLibrary to "Video"

        "kamera hp" ->
            Icons.Outlined.CameraAlt to "Kamera HP"

        "foto" ->
            Icons.Outlined.Image to "Foto"

        else ->
            Icons.Outlined.History to "Riwayat Simulasi"
    }
}


// =====================================================
// FORMAT TANGGAL
// =====================================================

private fun formatHistoryDate(
    timestamp: Long
): String {

    if (timestamp <= 0L) {
        return "Waktu tidak tersedia"
    }

    val itemCalendar = Calendar.getInstance().apply {
        timeInMillis = timestamp
    }

    val todayCalendar = Calendar.getInstance()

    val yesterdayCalendar = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -1)
    }

    val timeFormat = SimpleDateFormat(
        "HH:mm",
        Locale("id", "ID")
    )

    val time = timeFormat.format(
        Date(timestamp)
    )

    return when {

        itemCalendar.get(Calendar.YEAR) ==
                todayCalendar.get(Calendar.YEAR) &&

                itemCalendar.get(Calendar.DAY_OF_YEAR) ==
                todayCalendar.get(Calendar.DAY_OF_YEAR) -> {

            "Hari ini, $time"
        }

        itemCalendar.get(Calendar.YEAR) ==
                yesterdayCalendar.get(Calendar.YEAR) &&

                itemCalendar.get(Calendar.DAY_OF_YEAR) ==
                yesterdayCalendar.get(Calendar.DAY_OF_YEAR) -> {

            "Kemarin, $time"
        }

        else -> {

            val dateFormat = SimpleDateFormat(
                "dd MMM, HH:mm",
                Locale("id", "ID")
            )

            dateFormat.format(
                Date(timestamp)
            )
        }
    }
}


// =====================================================
// FORMAT DURASI
// =====================================================

private fun formatDuration(
    seconds: Double
): String {

    return "${seconds.toInt()} dtk"
}