package com.example.smarttrafficlight.feature.analysis

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext

import com.example.smarttrafficlight.ui.theme.BackgroundLight
import com.example.smarttrafficlight.ui.theme.BorderLight
import com.example.smarttrafficlight.ui.theme.PrimaryRed
import com.example.smarttrafficlight.ui.theme.SurfaceWhite
import com.example.smarttrafficlight.ui.theme.TextPrimary
import com.example.smarttrafficlight.ui.theme.TextSecondary

import com.example.smarttrafficlight.data.firebase.DensityConfig
import com.example.smarttrafficlight.data.analysis.DensityClassifier

import kotlinx.coroutines.CancellationException

import java.util.Locale
import kotlin.math.roundToInt


// =====================================================
// VIDEO ANALYSIS SCREEN
// =====================================================

@Composable
fun VideoAnalysisScreen(

    video: VideoSelection?,

    roi: RoiSelection?,

    trafficLight: String,

    trafficGroup: String,

    onBack: () -> Unit,

    onRetryAnalysis: () -> Unit,

    onStartSimulation: () -> Unit,

    onAnalysisCompleted: (VideoAnalysisSummary) -> Unit = {},

    densityConfig: DensityConfig = DensityConfig.DEFAULT,

    onDensityClassified: (vehicleCount: Int, densityLabel: String) -> Unit = { _, _ -> }

) {

    val context = LocalContext.current

    var isAnalyzing by remember(
        video?.videoUri,
        roi?.points
    ) {
        mutableStateOf(true)
    }

    var progress by remember(
        video?.videoUri,
        roi?.points
    ) {
        mutableStateOf<VideoAnalysisProgress?>(null)
    }

    var summary by remember(
        video?.videoUri,
        roi?.points
    ) {
        mutableStateOf<VideoAnalysisSummary?>(null)
    }

    var errorMessage by remember(
        video?.videoUri,
        roi?.points
    ) {
        mutableStateOf<String?>(null)
    }

    // =================================================
    // MENJALANKAN ANALISIS VIDEO
    // =================================================

    LaunchedEffect(
        video?.videoUri,
        roi?.points,
        video?.analysisIntervalSeconds
    ) {

        summary = null
        progress = null
        errorMessage = null

        if (video == null) {

            isAnalyzing = false

            errorMessage = "Data video belum tersedia."

            return@LaunchedEffect
        }

        if (roi == null || roi.points.size < 3) {

            isAnalyzing = false

            errorMessage = "Polygon ROI belum tersedia atau belum valid."

            return@LaunchedEffect
        }

        isAnalyzing = true

        try {

            val analyzer = VideoFrameAnalyzer(

                context = context.applicationContext,

                confidenceThreshold = 0.25f,

                iouThreshold = 0.45f,

                numThreads = 4
            )

            val result = analyzer.analyze(

                videoUri = video.videoUri,

                roi = roi,

                intervalSeconds =
                    video.analysisIntervalSeconds,

                onProgress = { currentProgress ->

                    progress = currentProgress
                }
            )

            summary = result

            // Mengirim hasil puncak video ke AppNavigation.

            onAnalysisCompleted(result)

        } catch (error: CancellationException) {

            throw error

        } catch (error: Exception) {

            errorMessage = error.localizedMessage
                ?: "Terjadi kesalahan saat analisis video."

        } finally {

            isAnalyzing = false
        }
    }

    // =================================================
    // KLASIFIKASI KEPADATAN BERDASARKAN RATA-RATA INTERVAL
    // =====================================================

    LaunchedEffect(summary, densityConfig) {
        val result = summary ?: return@LaunchedEffect
        val sampledCounts = result.intervals.map { it.totalVehicles }
        val representativeCount = DensityClassifier.averageSampledFrameCount(sampledCounts)
            ?: result.averageVehicles.roundToInt()
        val label = DensityClassifier.classify(representativeCount, densityConfig)
        onDensityClassified(representativeCount, label)
    }

    // =================================================
    // MEMBERSIHKAN FRAME PUNCAK
    // =================================================

    DisposableEffect(summary?.peakPreviewBitmap) {

        val previewBitmap =
            summary?.peakPreviewBitmap

        onDispose {

            if (
                previewBitmap != null &&
                !previewBitmap.isRecycled
            ) {
                previewBitmap.recycle()
            }
        }
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
                    vertical = 18.dp
                ),

            verticalArrangement = Arrangement.spacedBy(16.dp)

        ) {

            // =========================================
            // HEADER
            // =========================================

            Row(

                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement = Arrangement.SpaceBetween,

                verticalAlignment = Alignment.CenterVertically

            ) {

                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {

                    Text(

                        text = if (isAnalyzing) {
                            "Analisis Video"
                        } else {
                            "Hasil Analisis"
                        },

                        style = MaterialTheme.typography.headlineSmall,

                        fontWeight = FontWeight.Bold,

                        color = TextPrimary
                    )

                    Text(
                        text = video?.fileName ?: "Video tidak tersedia",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Surface(

                    shape = CircleShape,

                    color = SurfaceWhite,

                    border = BorderStroke(
                        1.dp,
                        BorderLight
                    )

                ) {

                    Text(
                        text = "$trafficLight · $trafficGroup",
                        modifier = Modifier.padding(
                            horizontal = 10.dp,
                            vertical = 7.dp
                        ),
                        fontSize = 11.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // =========================================
            // PROGRESS ANALISIS
            // =========================================

            if (isAnalyzing) {

                Card(

                    modifier = Modifier.fillMaxWidth(),

                    shape = RoundedCornerShape(20.dp),

                    colors = CardDefaults.cardColors(
                        containerColor = SurfaceWhite
                    ),

                    border = BorderStroke(
                        1.dp,
                        BorderLight
                    )

                ) {

                    Column(

                        modifier = Modifier.padding(18.dp),

                        verticalArrangement = Arrangement.spacedBy(12.dp)

                    ) {

                        Row(

                            verticalAlignment = Alignment.CenterVertically,

                            horizontalArrangement = Arrangement.spacedBy(10.dp)

                        ) {

                            CircularProgressIndicator(
                                modifier = Modifier
                                    .then(Modifier),
                                color = PrimaryRed,
                                strokeWidth = 2.5.dp
                            )

                            Column {

                                Text(
                                    text = "YOLO sedang menganalisis video",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = "Memproses frame setiap " +
                                            "${video?.analysisIntervalSeconds ?: 5} detik",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        val currentProgress = progress

                        LinearProgressIndicator(

                            progress = currentProgress?.fraction ?: 0f,

                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp),

                            color = PrimaryRed,

                            trackColor = BorderLight
                        )

                        Text(

                            text = if (currentProgress != null) {

                                "${currentProgress.processedFrames} dari " +
                                        "${currentProgress.totalFrames} frame selesai"

                            } else {

                                "Menyiapkan model YOLO..."
                            },

                            style = MaterialTheme.typography.bodySmall,

                            color = TextSecondary
                        )

                        if (currentProgress != null) {

                            Text(

                                text =
                                    "Interval: " +
                                            "${formatVideoTime(currentProgress.intervalStartMs)} – " +
                                            formatVideoTime(currentProgress.intervalEndMs),

                                style = MaterialTheme.typography.bodySmall,

                                fontWeight = FontWeight.SemiBold,

                                color = TextPrimary
                            )

                            Text(

                                text =
                                    "${formatVideoTime(currentProgress.currentTimeMs)} / " +
                                            formatVideoTime(currentProgress.durationMs),

                                style = MaterialTheme.typography.bodySmall,

                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // =========================================
            // ERROR
            // =========================================

            if (errorMessage != null) {

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

                    Column(

                        modifier = Modifier.padding(18.dp),

                        verticalArrangement = Arrangement.spacedBy(10.dp)

                    ) {

                        Text(
                            text = "Analisis gagal",
                            color = PrimaryRed,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = errorMessage.orEmpty(),
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Button(
                            onClick = onRetryAnalysis,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryRed
                            )
                        ) {

                            Text("Atur Ulang ROI")
                        }
                    }
                }
            }

            // =========================================
            // HASIL ANALISIS
            // =========================================

            val result = summary

            if (result != null) {

                val peakInterval = result.intervals.firstOrNull {

                    it.intervalStartMs ==
                            result.peakIntervalStartMs
                }

                // =====================================
                // PREVIEW FRAME PUNCAK
                // =====================================

                Text(
                    text = "Frame Analisis",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                val previewBitmap =
                    result.peakPreviewBitmap

                if (previewBitmap != null) {

                    Card(

                        modifier = Modifier.fillMaxWidth(),

                        shape = RoundedCornerShape(20.dp),

                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1F2226)
                        )

                    ) {

                        Image(

                            bitmap = previewBitmap.asImageBitmap(),

                            contentDescription =
                                "Frame hasil deteksi YOLO pada interval puncak",

                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(
                                    previewBitmap.width.toFloat() /
                                            previewBitmap.height.toFloat()
                                )
                        )
                    }
                }

                // =====================================
                // INFORMASI PROSES
                // =====================================

                Card(

                    modifier = Modifier.fillMaxWidth(),

                    shape = RoundedCornerShape(16.dp),

                    colors = CardDefaults.cardColors(
                        containerColor = SurfaceWhite
                    ),

                    border = BorderStroke(
                        1.dp,
                        BorderLight
                    )

                ) {

                    Column(

                        modifier = Modifier.padding(16.dp),

                        verticalArrangement = Arrangement.spacedBy(8.dp)

                    ) {

                        VideoInfoRow(
                            label = "Durasi video",
                            value = formatVideoTime(result.durationMs)
                        )

                        VideoInfoRow(
                            label = "Interval analisis",
                            value = "${result.intervalSeconds} detik"
                        )

                        VideoInfoRow(
                            label = "Frame dianalisis",
                            value = "${result.intervals.size} frame"
                        )

                        VideoInfoRow(
                            label = "Total waktu inferensi",
                            value = "${result.totalInferenceTimeMs} ms"
                        )
                    }
                }

                // =====================================
                // RATA-RATA DAN PUNCAK
                // =====================================

                Text(
                    text = "Ringkasan Kendaraan di ROI",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Row(

                    modifier = Modifier.fillMaxWidth(),

                    horizontalArrangement = Arrangement.spacedBy(10.dp)

                ) {

                    VideoMetricCard(

                        title = "Rata-rata",

                        value = String.format(
                            Locale.US,
                            "%.1f",
                            result.averageVehicles
                        ),

                        subtitle = "kendaraan / interval",

                        modifier = Modifier.weight(1f)
                    )

                    VideoMetricCard(

                        title = "Puncak",

                        value = result.peakVehicles.toString(),

                        subtitle = "kendaraan di ROI",

                        modifier = Modifier.weight(1f)
                    )
                }

                // =====================================
                // INTERVAL PUNCAK
                // =====================================

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

                    Column(

                        modifier = Modifier.padding(16.dp),

                        verticalArrangement = Arrangement.spacedBy(8.dp)

                    ) {

                        Text(
                            text = "Interval Puncak",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        Text(

                            text =
                                "${formatVideoTime(result.peakIntervalStartMs)} – " +
                                        formatVideoTime(result.peakIntervalEndMs),

                            style = MaterialTheme.typography.headlineSmall,

                            fontWeight = FontWeight.Bold,

                            color = PrimaryRed
                        )

                        if (peakInterval != null) {

                            Text(
                                text = "Terdapat ${peakInterval.totalVehicles} " +
                                        "kendaraan pada interval ini.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // =====================================
                // KENDARAAN DI INTERVAL PUNCAK
                // =====================================

                if (peakInterval != null) {

                    Text(
                        text = "Kendaraan pada Interval Puncak",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        VideoVehicleCard(
                            title = "Mobil",
                            count = peakInterval.counts.car,
                            modifier = Modifier.weight(1f)
                        )

                        VideoVehicleCard(
                            title = "Motor",
                            count = peakInterval.counts.motorcycle,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        VideoVehicleCard(
                            title = "Bus",
                            count = peakInterval.counts.bus,
                            modifier = Modifier.weight(1f)
                        )

                        VideoVehicleCard(
                            title = "Truk",
                            count = peakInterval.counts.truck,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // =====================================
                // KEPADATAN
                // =====================================

                Card(

                    modifier = Modifier.fillMaxWidth(),

                    shape = RoundedCornerShape(18.dp),

                    border = BorderStroke(
                        1.dp,
                        BorderLight
                    ),

                    colors = CardDefaults.cardColors(
                        containerColor = SurfaceWhite
                    )

                ) {

                    Column(

                        modifier = Modifier.padding(16.dp),

                        verticalArrangement = Arrangement.spacedBy(8.dp)

                    ) {

                        Text(
                            text = "Estimasi Kepadatan",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        val sampledCounts = result.intervals.map { it.totalVehicles }
                        val densityAverageCount =
                            DensityClassifier.averageSampledFrameCount(sampledCounts)
                                ?: result.averageVehicles.roundToInt()
                        val densityLabel = DensityClassifier.classify(
                            densityAverageCount,
                            densityConfig
                        )
                        val densityColor = when (densityLabel) {
                            DensityClassifier.LOW -> Color(0xFF17794A)
                            DensityClassifier.MEDIUM -> Color(0xFF9A6700)
                            DensityClassifier.HIGH -> Color(0xFFB42318)
                            else -> TextSecondary
                        }

                        Text(
                            text = densityLabel,
                            color = densityColor,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = if (densityConfig.enabled) {
                                "Klasifikasi menggunakan rata-rata $densityAverageCount kendaraan " +
                                        "dari ${result.intervals.size} interval sampel. " +
                                        "Rendah: 0–${densityConfig.lowMax}; " +
                                        "Sedang: ${densityConfig.lowMax + 1}–${densityConfig.mediumMax}; " +
                                        "Tinggi: di atas ${densityConfig.mediumMax}."
                            } else {
                                "Klasifikasi kepadatan sedang dinonaktifkan pada Pengaturan Analisis."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                // =====================================
                // HASIL PER INTERVAL
                // =====================================

                Text(
                    text = "Hasil Per Interval",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                result.intervals.forEach { interval ->

                    Card(

                        modifier = Modifier.fillMaxWidth(),

                        shape = RoundedCornerShape(14.dp),

                        border = BorderStroke(
                            1.dp,
                            BorderLight
                        ),

                        colors = CardDefaults.cardColors(
                            containerColor = SurfaceWhite
                        )

                    ) {

                        Row(

                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),

                            horizontalArrangement = Arrangement.SpaceBetween,

                            verticalAlignment = Alignment.CenterVertically

                        ) {

                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {

                                Text(
                                    text =
                                        "${formatVideoTime(interval.intervalStartMs)} – " +
                                                formatVideoTime(interval.intervalEndMs),
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )

                                Text(
                                    text =
                                        "Mobil ${interval.counts.car} · " +
                                                "Motor ${interval.counts.motorcycle} · " +
                                                "Bus ${interval.counts.bus} · " +
                                                "Truk ${interval.counts.truck}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }

                            Surface(

                                shape = CircleShape,

                                color = if (
                                    interval.intervalStartMs ==
                                    result.peakIntervalStartMs
                                ) {
                                    Color(0xFFFFECEE)
                                } else {
                                    BackgroundLight
                                }

                            ) {

                                Text(
                                    text = interval.totalVehicles.toString(),
                                    modifier = Modifier.padding(
                                        horizontal = 13.dp,
                                        vertical = 8.dp
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    color = if (
                                        interval.intervalStartMs ==
                                        result.peakIntervalStartMs
                                    ) {
                                        PrimaryRed
                                    } else {
                                        TextPrimary
                                    }
                                )
                            }
                        }
                    }
                }

                // =====================================
                // MULAI SIMULASI
                // =====================================

                Button(

                    onClick = onStartSimulation,

                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),

                    shape = CircleShape,

                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryRed,
                        contentColor = SurfaceWhite
                    )

                ) {

                    Text(
                        text = "Mulai Simulasi",
                        fontWeight = FontWeight.Bold
                    )
                }

                // =====================================
                // ULANGI ANALISIS
                // =====================================

                OutlinedButton(

                    onClick = onRetryAnalysis,

                    modifier = Modifier.fillMaxWidth(),

                    shape = CircleShape,

                    border = BorderStroke(
                        1.dp,
                        BorderLight
                    )

                ) {

                    Text(
                        text = "Ulangi Analisis",
                        color = TextPrimary
                    )
                }
            }

            // =========================================
            // KEMBALI
            // =========================================

            OutlinedButton(

                onClick = onBack,

                modifier = Modifier.fillMaxWidth(),

                shape = CircleShape,

                border = BorderStroke(
                    1.dp,
                    BorderLight
                )

            ) {

                Text(
                    text = "Kembali",
                    color = TextPrimary
                )
            }
        }
    }
}


// =====================================================
// KARTU METRIK
// =====================================================

@Composable
private fun VideoMetricCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {

    Card(

        modifier = modifier,

        shape = RoundedCornerShape(18.dp),

        colors = CardDefaults.cardColors(
            containerColor = SurfaceWhite
        ),

        border = BorderStroke(
            1.dp,
            BorderLight
        )

    ) {

        Column(

            modifier = Modifier.padding(14.dp),

            verticalArrangement = Arrangement.spacedBy(5.dp)

        ) {

            Text(
                text = title,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = value,
                color = TextPrimary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 10.sp
            )
        }
    }
}


// =====================================================
// KARTU JUMLAH KENDARAAN
// =====================================================

@Composable
private fun VideoVehicleCard(
    title: String,
    count: Int,
    modifier: Modifier = Modifier
) {

    Card(

        modifier = modifier,

        shape = RoundedCornerShape(15.dp),

        colors = CardDefaults.cardColors(
            containerColor = SurfaceWhite
        ),

        border = BorderStroke(
            1.dp,
            BorderLight
        )

    ) {

        Column(

            modifier = Modifier.padding(13.dp),

            verticalArrangement = Arrangement.spacedBy(3.dp)

        ) {

            Text(
                text = title,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = count.toString(),
                color = TextPrimary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


// =====================================================
// BARIS INFORMASI VIDEO
// =====================================================

@Composable
private fun VideoInfoRow(
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
            style = MaterialTheme.typography.bodySmall
        )

        Text(
            text = value,
            color = TextPrimary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}


// =====================================================
// FORMAT WAKTU VIDEO
// =====================================================

private fun formatVideoTime(
    milliseconds: Long
): String {

    val totalSeconds = milliseconds / 1000L

    val minutes = totalSeconds / 60L

    val seconds = totalSeconds % 60L

    return String.format(
        Locale.US,
        "%02d:%02d",
        minutes,
        seconds
    )
}