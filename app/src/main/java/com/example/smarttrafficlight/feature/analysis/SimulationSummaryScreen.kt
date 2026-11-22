package com.example.smarttrafficlight.feature.simulation

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

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.smarttrafficlight.ui.theme.BackgroundLight
import com.example.smarttrafficlight.ui.theme.BorderLight
import com.example.smarttrafficlight.ui.theme.PrimaryRed
import com.example.smarttrafficlight.ui.theme.SurfaceWhite
import com.example.smarttrafficlight.ui.theme.TextPrimary
import com.example.smarttrafficlight.ui.theme.TextSecondary

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


// =====================================================
// DATA RINGKASAN SIMULASI
// =====================================================

data class SimulationSummaryData(

    val trafficLight: String,

    val trafficGroup: String,

    val initialSignal: String,

    val sourceLabel: String,

    val vehicleCount: Int?,

    val densityLabel: String?,

    val initialDistanceMeters: Int,

    val targetDurationSeconds: Int,

    val actualDurationSeconds: Double,

    val completedAtMillis: Long = System.currentTimeMillis()
)


// =====================================================
// SIMULATION SUMMARY SCREEN
// =====================================================

@Composable
fun SimulationSummaryScreen(

    summary: SimulationSummaryData,

    onBackToHome: () -> Unit

) {

    val completedDate = rememberFormattedDate(
        summary.completedAtMillis
    )

    val densityDescription = when {

        summary.vehicleCount == null ->
            "Tidak dilakukan"

        summary.densityLabel.isNullOrBlank() ->
            "Belum diklasifikasikan · " +
                    "${summary.vehicleCount} kendaraan"

        else ->
            "${summary.densityLabel} · " +
                    "${summary.vehicleCount} kendaraan"
    }

    val actualDuration = formatDuration(
        summary.actualDurationSeconds
    )

    Scaffold(

        containerColor = BackgroundLight,

        bottomBar = {

            Column(

                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 20.dp,
                        vertical = 14.dp
                    ),

                verticalArrangement = Arrangement.spacedBy(8.dp)

            ) {

                Button(

                    onClick = onBackToHome,

                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),

                    shape = CircleShape,

                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryRed,
                        contentColor = SurfaceWhite
                    )

                ) {

                    Icon(
                        imageVector = Icons.Outlined.Home,
                        contentDescription = null
                    )

                    Spacer(
                        modifier = Modifier.size(9.dp)
                    )

                    Text(
                        text = "Kembali ke Beranda",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
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
                    vertical = 20.dp
                ),

            verticalArrangement = Arrangement.spacedBy(17.dp)

        ) {

            // =========================================
            // HEADER
            // =========================================

            Text(

                text = "Ringkasan",

                fontSize = 23.sp,

                fontWeight = FontWeight.Bold,

                color = TextPrimary
            )

            // =========================================
            // STATUS SIMULASI SELESAI
            // =========================================

            Surface(

                modifier = Modifier.fillMaxWidth(),

                shape = RoundedCornerShape(20.dp),

                color = Color(0xFFE8F5EE)

            ) {

                Row(

                    modifier = Modifier.padding(16.dp),

                    verticalAlignment = Alignment.CenterVertically,

                    horizontalArrangement = Arrangement.spacedBy(10.dp)

                ) {

                    Icon(

                        imageVector = Icons.Outlined.CheckCircle,

                        contentDescription = null,

                        tint = Color(0xFF138052),

                        modifier = Modifier.size(23.dp)
                    )

                    Column {

                        Text(
                            text = "Simulasi selesai",
                            color = Color(0xFF138052),
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = completedDate,
                            color = Color(0xFF348260),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // =========================================
            // RINGKASAN DATA
            // =========================================

            Card(

                modifier = Modifier.fillMaxWidth(),

                shape = RoundedCornerShape(23.dp),

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

                    modifier = Modifier.padding(18.dp),

                    verticalArrangement = Arrangement.spacedBy(0.dp)

                ) {

                    SummaryRow(

                        label = "Jalur",

                        value =
                            "${summary.trafficLight} · " +
                                    summary.trafficGroup
                    )

                    SummaryDivider()

                    SummaryRow(

                        label = "Sinyal awal",

                        value = summary.initialSignal.uppercase()
                    )

                    SummaryDivider()

                    SummaryRow(

                        label = "Sumber",

                        value = summary.sourceLabel
                    )

                    SummaryDivider()

                    SummaryRow(

                        label = "Kepadatan",

                        value = densityDescription
                    )

                    SummaryDivider()

                    SummaryRow(

                        label = "Jarak awal",

                        value = "${summary.initialDistanceMeters} m"
                    )

                    SummaryDivider()

                    SummaryRow(

                        label = "Durasi",

                        value =
                            "${summary.targetDurationSeconds} dtk " +
                                    "(aktual $actualDuration)"
                    )
                }
            }

            // =========================================
            // CATATAN
            // =========================================

            if (summary.vehicleCount != null &&
                summary.densityLabel.isNullOrBlank()
            ) {

                Text(

                    text = "Jumlah kendaraan ditampilkan berdasarkan " +
                            "hasil deteksi. Kategori kepadatan belum ditetapkan.",

                    color = TextSecondary,

                    fontSize = 12.sp,

                    lineHeight = 18.sp
                )
            }
        }
    }
}


// =====================================================
// BARIS RINGKASAN
// =====================================================

@Composable
private fun SummaryRow(
    label: String,
    value: String
) {

    Row(

        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 15.dp),

        horizontalArrangement = Arrangement.SpaceBetween,

        verticalAlignment = Alignment.CenterVertically

    ) {

        Text(

            text = label,

            color = TextSecondary,

            fontSize = 14.sp
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
// PEMBATAS ANTAR BARIS
// =====================================================

@Composable
private fun SummaryDivider() {

    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 0.dp)
            .then(
                Modifier
            )
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp),
        color = BorderLight
    ) {}
}


// =====================================================
// FORMAT TANGGAL SELESAI
// =====================================================

private fun rememberFormattedDate(
    timestamp: Long
): String {

    return SimpleDateFormat(
        "dd MMM yyyy, HH:mm",
        Locale("id", "ID")
    ).format(
        Date(timestamp)
    )
}


// =====================================================
// FORMAT DURASI
// =====================================================

private fun formatDuration(
    seconds: Double
): String {

    val rounded = seconds.toInt()

    return if (
        kotlin.math.abs(seconds - rounded) < 0.05
    ) {

        "$rounded dtk"

    } else {

        String.format(
            Locale.US,
            "%.1f dtk",
            seconds
        )
    }
}