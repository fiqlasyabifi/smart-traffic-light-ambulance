package com.example.smarttrafficlight.feature.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri

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
import androidx.compose.ui.graphics.Color

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import java.util.Locale

import kotlin.math.max


// =====================================================
// DATA OUTPUT FOTO
// =====================================================

private data class PhotoAnalysisOutput(

    val annotatedBitmap: Bitmap,

    val result: YoloAnalysisResult
)


// =====================================================
// PHOTO ANALYSIS SCREEN
// =====================================================

@Composable
fun PhotoAnalysisScreen(

    imageUri: Uri?,

    roi: RoiSelection?,

    trafficLight: String,

    trafficGroup: String,

    onBack: () -> Unit,

    onRetake: () -> Unit,

    onStartSimulation: () -> Unit,

    onAnalysisCompleted: (YoloAnalysisResult) -> Unit = {},

    densityConfig: DensityConfig = DensityConfig.DEFAULT,

    onDensityClassified: (vehicleCount: Int, densityLabel: String) -> Unit = { _, _ -> }

) {

    val context = LocalContext.current

    var analysisOutput by remember(
        imageUri,
        roi?.points
    ) {
        mutableStateOf<PhotoAnalysisOutput?>(null)
    }

    var isLoading by remember(
        imageUri,
        roi?.points
    ) {
        mutableStateOf(true)
    }

    var errorMessage by remember(
        imageUri,
        roi?.points
    ) {
        mutableStateOf<String?>(null)
    }

    // =================================================
    // MENJALANKAN ANALISIS YOLO
    // =================================================

    LaunchedEffect(
        imageUri,
        roi?.points
    ) {

        analysisOutput = null
        errorMessage = null

        if (imageUri == null) {

            isLoading = false

            errorMessage = "Foto belum tersedia."

            return@LaunchedEffect
        }

        if (roi == null || roi.points.size < 3) {

            isLoading = false

            errorMessage = "Polygon ROI belum tersedia atau belum valid."

            return@LaunchedEffect
        }

        isLoading = true

        var sourceBitmap: Bitmap? = null

        try {

            // =====================================
            // 1. MEMBACA FOTO
            // =====================================

            sourceBitmap = withContext(Dispatchers.IO) {

                decodePhotoForAnalysis(
                    context = context,
                    uri = imageUri
                )
            }

            if (sourceBitmap == null) {

                errorMessage = "Foto tidak dapat dibaca."

                return@LaunchedEffect
            }

            // =====================================
            // 2. INFERENSI YOLO DAN GAMBAR HASIL
            // =====================================

            val source = sourceBitmap

                ?: throw IllegalStateException(
                    "Foto tidak tersedia."
                )

            val currentRoi = roi

            val output = withContext(Dispatchers.Default) {

                val detector = YoloDetector(

                    context = context.applicationContext,

                    confidenceThreshold = 0.25f,

                    iouThreshold = 0.45f,

                    numThreads = 4
                )

                val result = detector.use {

                    it.detect(
                        sourceBitmap = source,
                        roi = currentRoi
                    )
                }

                val annotatedBitmap = renderAnalysisImage(

                    source = source,

                    roi = currentRoi,

                    result = result
                )

                PhotoAnalysisOutput(

                    annotatedBitmap = annotatedBitmap,

                    result = result
                )
            }

            analysisOutput = output

            // Mengirim hasil ke state AppNavigation.

            onAnalysisCompleted(output.result)

        } catch (error: Exception) {

            errorMessage = error.localizedMessage
                ?: "Terjadi kesalahan saat menganalisis foto."

        } finally {

            sourceBitmap?.let {

                if (!it.isRecycled) {
                    it.recycle()
                }
            }

            isLoading = false
        }
    }

    // =================================================
    // KLASIFIKASI DENSITAS DARI KONFIGURASI FIREBASE
    // =====================================================

    LaunchedEffect(analysisOutput?.result, densityConfig) {
        val result = analysisOutput?.result ?: return@LaunchedEffect
        val count = result.counts.total
        val label = DensityClassifier.classify(count, densityConfig)
        onDensityClassified(count, label)
    }

    // =================================================
    // MEMBERSIHKAN BITMAP HASIL
    // =================================================

    DisposableEffect(analysisOutput) {

        val outputBitmap =
            analysisOutput?.annotatedBitmap

        onDispose {

            if (
                outputBitmap != null &&
                !outputBitmap.isRecycled
            ) {
                outputBitmap.recycle()
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

                verticalAlignment = Alignment.CenterVertically,

                horizontalArrangement = Arrangement.SpaceBetween

            ) {

                Column {

                    Text(
                        text = "Hasil Analisis",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Text(
                        text = "Deteksi kendaraan dari foto",
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
                            horizontal = 11.dp,
                            vertical = 7.dp
                        ),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
            }

            // =========================================
            // STATUS PROSES
            // =========================================

            if (isLoading) {

                Card(

                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),

                    shape = RoundedCornerShape(22.dp),

                    colors = CardDefaults.cardColors(
                        containerColor = ColorDark
                    )

                ) {

                    Column(

                        modifier = Modifier.fillMaxSize(),

                        horizontalAlignment = Alignment.CenterHorizontally,

                        verticalArrangement = Arrangement.Center

                    ) {

                        CircularProgressIndicator(
                            color = SurfaceWhite
                        )

                        Spacer(
                            modifier = Modifier.height(14.dp)
                        )

                        Text(
                            text = "Sedang menganalisis foto...",
                            color = SurfaceWhite,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = "YOLOv8n TensorFlow Lite",
                            color = ColorLight,
                            fontSize = 11.sp
                        )
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

                    border = BorderStroke(
                        1.dp,
                        BorderLight
                    ),

                    colors = CardDefaults.cardColors(
                        containerColor = SurfaceWhite
                    )

                ) {

                    Text(
                        text = errorMessage.orEmpty(),
                        modifier = Modifier.padding(18.dp),
                        color = PrimaryRed
                    )
                }

                Button(

                    onClick = onRetake,

                    modifier = Modifier.fillMaxWidth(),

                    shape = CircleShape,

                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryRed
                    )

                ) {

                    Text("Pilih Foto Kembali")
                }
            }

            // =========================================
            // FOTO HASIL DETEKSI
            // =========================================

            val output = analysisOutput

            if (output != null) {

                Text(
                    text = "Deteksi Kendaraan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Card(

                    modifier = Modifier.fillMaxWidth(),

                    shape = RoundedCornerShape(20.dp),

                    colors = CardDefaults.cardColors(
                        containerColor = ColorDark
                    )

                ) {

                    Image(

                        bitmap = output.annotatedBitmap.asImageBitmap(),

                        contentDescription =
                            "Hasil deteksi kendaraan dan polygon ROI",

                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(
                                output.annotatedBitmap.width.toFloat() /
                                        output.annotatedBitmap.height.toFloat()
                            ),

                        contentScale = ContentScale.Fit
                    )
                }

                // =====================================
                // TOTAL KENDARAAN DI ROI
                // =====================================

                Card(

                    modifier = Modifier.fillMaxWidth(),

                    shape = RoundedCornerShape(20.dp),

                    border = BorderStroke(
                        1.dp,
                        BorderLight
                    ),

                    colors = CardDefaults.cardColors(
                        containerColor = SurfaceWhite
                    )

                ) {

                    Column(

                        modifier = Modifier.padding(18.dp),

                        verticalArrangement = Arrangement.spacedBy(6.dp)

                    ) {

                        Text(
                            text = "Deteksi di ROI",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Row(

                            verticalAlignment = Alignment.Bottom,

                            horizontalArrangement = Arrangement.spacedBy(8.dp)

                        ) {

                            Text(
                                text = output.result.counts.total.toString(),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )

                            Text(
                                text = "kendaraan",
                                modifier = Modifier.padding(bottom = 7.dp),
                                color = TextSecondary
                            )
                        }

                        Text(
                            text =
                                "${output.result.detections.size} deteksi seluruh gambar",

                            style = MaterialTheme.typography.bodySmall,

                            color = TextSecondary
                        )

                        Text(
                            text =
                                "Waktu inferensi: " +
                                        "${output.result.inferenceTimeMs} ms",

                            style = MaterialTheme.typography.bodySmall,

                            color = TextSecondary
                        )
                    }
                }

                // =====================================
                // JUMLAH PER KELAS
                // =====================================

                Text(
                    text = "Jumlah Kendaraan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {

                    PhotoVehicleCountCard(
                        title = "Mobil",
                        count = output.result.counts.car,
                        modifier = Modifier.weight(1f)
                    )

                    PhotoVehicleCountCard(
                        title = "Motor",
                        count = output.result.counts.motorcycle,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {

                    PhotoVehicleCountCard(
                        title = "Bus",
                        count = output.result.counts.bus,
                        modifier = Modifier.weight(1f)
                    )

                    PhotoVehicleCountCard(
                        title = "Truk",
                        count = output.result.counts.truck,
                        modifier = Modifier.weight(1f)
                    )
                }

                // =====================================
                // ESTIMASI KEPADATAN
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
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )

                        val densityLabel = DensityClassifier.classify(
                            output.result.counts.total,
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
                                "Klasifikasi berdasarkan ${output.result.counts.total} kendaraan pada foto ini. " +
                                        "Rendah: 0–${densityConfig.lowMax}; " +
                                        "Sedang: ${densityConfig.lowMax + 1}–${densityConfig.mediumMax}; " +
                                        "Tinggi: di atas ${densityConfig.mediumMax}."
                            } else {
                                "Klasifikasi kepadatan sedang dinonaktifkan pada Pengaturan Analisis."
                            },
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
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
                // ULANGI
                // =====================================

                OutlinedButton(

                    onClick = onRetake,

                    modifier = Modifier.fillMaxWidth(),

                    shape = CircleShape,

                    border = BorderStroke(
                        1.dp,
                        BorderLight
                    )

                ) {

                    Text(
                        text = "Unggah Ulang Foto",
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
// KARTU JUMLAH KENDARAAN
// =====================================================

@Composable
private fun PhotoVehicleCountCard(
    title: String,
    count: Int,
    modifier: Modifier = Modifier
) {

    Card(

        modifier = modifier,

        shape = RoundedCornerShape(16.dp),

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

            verticalArrangement = Arrangement.spacedBy(5.dp)

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
// MENGGAMBAR HASIL ANALISIS
// Polygon + Bounding Box + Bottom Center
// =====================================================

private fun renderAnalysisImage(
    source: Bitmap,
    roi: RoiSelection,
    result: YoloAnalysisResult
): Bitmap {

    val output = source.copy(
        Bitmap.Config.ARGB_8888,
        true
    ) ?: throw IllegalStateException(
        "Gagal membuat gambar hasil analisis."
    )

    val canvas = AndroidCanvas(output)

    val width = output.width.toFloat()
    val height = output.height.toFloat()

    // =========================================
    // POLYGON ROI
    // =========================================

    val roiPath = Path()

    roi.points.forEachIndexed { index, point ->

        val x = point.x * width
        val y = point.y * height

        if (index == 0) {
            roiPath.moveTo(x, y)
        } else {
            roiPath.lineTo(x, y)
        }
    }

    roiPath.close()

    val roiFillPaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {

        color = AndroidColor.argb(
            35,
            213,
            43,
            58
        )

        style = Paint.Style.FILL
    }

    canvas.drawPath(
        roiPath,
        roiFillPaint
    )

    val roiStrokePaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {

        color = AndroidColor.rgb(
            213,
            43,
            58
        )

        style = Paint.Style.STROKE

        strokeWidth = max(
            3f,
            width * 0.003f
        )
    }

    canvas.drawPath(
        roiPath,
        roiStrokePaint
    )

    // =========================================
    // TITIK POLYGON
    // =========================================

    val roiPointPaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {

        color = AndroidColor.rgb(
            213,
            43,
            58
        )
    }

    val roiPointBorder = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {

        color = AndroidColor.WHITE

        style = Paint.Style.STROKE

        strokeWidth = max(
            2f,
            width * 0.0015f
        )
    }

    roi.points.forEach { point ->

        val x = point.x * width
        val y = point.y * height

        val radius = max(
            5f,
            width * 0.004f
        )

        canvas.drawCircle(
            x,
            y,
            radius,
            roiPointPaint
        )

        canvas.drawCircle(
            x,
            y,
            radius,
            roiPointBorder
        )
    }

    // =========================================
    // BOUNDING BOX
    // =========================================

    val boxPaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {

        style = Paint.Style.STROKE

        strokeWidth = max(
            2f,
            width * 0.0022f
        )
    }

    val textPaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {

        color = AndroidColor.WHITE

        textSize = max(
            18f,
            width * 0.018f
        )

        typeface = android.graphics.Typeface.create(
            "sans-serif-medium",
            android.graphics.Typeface.NORMAL
        )
    }

    val labelBackgroundPaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    )

    // =========================================
    // BOTTOM CENTER
    // =========================================

    val bottomCenterPaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {

        color = AndroidColor.rgb(
            255,
            210,
            0
        )
    }

    result.detections.forEach { detection ->

        val boxColor = if (
            detection.isInsideRoi
        ) {

            AndroidColor.rgb(
                40,
                190,
                40
            )

        } else {

            AndroidColor.rgb(
                145,
                145,
                145
            )
        }

        boxPaint.color = boxColor

        canvas.drawRect(

            RectF(
                detection.left,
                detection.top,
                detection.right,
                detection.bottom
            ),

            boxPaint
        )

        // Titik kuning Bottom Center.

        canvas.drawCircle(

            detection.bottomCenterX,

            detection.bottomCenterY,

            max(
                5f,
                width * 0.004f
            ),

            bottomCenterPaint
        )

        // Label kendaraan.

        val label = String.format(
            Locale.US,
            "%s %.0f%%",
            detection.className,
            detection.confidence * 100f
        )

        val labelWidth =
            textPaint.measureText(label) + 12f

        val labelHeight =
            textPaint.textSize + 12f

        val labelLeft = detection.left.coerceIn(
            0f,
            max(0f, width - labelWidth)
        )

        val labelTop = (
                detection.top - labelHeight
                ).coerceAtLeast(0f)

        labelBackgroundPaint.color = boxColor

        canvas.drawRoundRect(

            labelLeft,

            labelTop,

            labelLeft + labelWidth,

            labelTop + labelHeight,

            5f,

            5f,

            labelBackgroundPaint
        )

        canvas.drawText(

            label,

            labelLeft + 6f,

            labelTop + labelHeight - 6f,

            textPaint
        )
    }

    // =========================================
    // PANEL JUMLAH KENDARAAN
    // =========================================

    val panelPaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {

        color = AndroidColor.argb(
            215,
            24,
            24,
            24
        )
    }

    val panelTextPaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {

        color = AndroidColor.WHITE

        textSize = max(
            18f,
            width * 0.019f
        )

        typeface = android.graphics.Typeface.create(
            "sans-serif-medium",
            android.graphics.Typeface.NORMAL
        )
    }

    val lines = listOf(

        "ROI COUNT - BOTTOM CENTER",

        "Total kendaraan: ${result.counts.total}",

        "Mobil: ${result.counts.car}",

        "Motor: ${result.counts.motorcycle}",

        "Bus: ${result.counts.bus}",

        "Truk: ${result.counts.truck}"
    )

    val padding = max(
        12f,
        width * 0.012f
    )

    val lineHeight =
        panelTextPaint.textSize * 1.5f

    val panelWidth = minOf(

        width * 0.76f,

        panelTextPaint.measureText(
            "ROI COUNT - BOTTOM CENTER"
        ) + padding * 2
    )

    val panelHeight =
        padding * 2 +
                lineHeight * lines.size

    canvas.drawRoundRect(

        padding,

        padding,

        padding + panelWidth,

        padding + panelHeight,

        12f,

        12f,

        panelPaint
    )

    lines.forEachIndexed { index, line ->

        canvas.drawText(

            line,

            padding * 2,

            padding * 2 +
                    panelTextPaint.textSize +
                    index * lineHeight,

            panelTextPaint
        )
    }

    return output
}


// =====================================================
// MEMBACA FOTO UNTUK ANALISIS
// =====================================================

private fun decodePhotoForAnalysis(
    context: Context,
    uri: Uri
): Bitmap? {

    val boundsOptions = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }

    context.contentResolver
        .openInputStream(uri)
        ?.use {

            BitmapFactory.decodeStream(
                it,
                null,
                boundsOptions
            )
        }

    if (
        boundsOptions.outWidth <= 0 ||
        boundsOptions.outHeight <= 0
    ) {
        return null
    }

    var sampleSize = 1

    while (
        boundsOptions.outWidth / sampleSize > 1600 ||
        boundsOptions.outHeight / sampleSize > 1600
    ) {

        sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply {

        inSampleSize = sampleSize

        inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    return context.contentResolver
        .openInputStream(uri)
        ?.use {

            BitmapFactory.decodeStream(
                it,
                null,
                options
            )
        }
}


// =====================================================
// WARNA AREA PREVIEW
// =====================================================

private val ColorDark = Color(0xFF1F2226)
private val ColorLight = Color(0xFFCFD2D8)