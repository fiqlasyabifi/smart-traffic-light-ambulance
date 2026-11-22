package com.example.smarttrafficlight.feature.analysis

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface as AndroidSurface

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

import com.example.smarttrafficlight.ui.theme.BackgroundLight
import com.example.smarttrafficlight.ui.theme.BorderLight
import com.example.smarttrafficlight.ui.theme.PrimaryRed
import com.example.smarttrafficlight.ui.theme.PrimaryRedDark
import com.example.smarttrafficlight.ui.theme.PrimaryRedLight
import com.example.smarttrafficlight.ui.theme.SurfaceSoft
import com.example.smarttrafficlight.ui.theme.SurfaceWhite
import com.example.smarttrafficlight.ui.theme.TextPrimary
import com.example.smarttrafficlight.ui.theme.TextSecondary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

import kotlin.math.max

/**
 * Layar Kamera HP.
 *
 * Fitur:
 * - Preview kamera belakang menggunakan CameraX.
 * - Deteksi kendaraan menggunakan YoloDetector.
 * - Pengaturan polygon ROI melalui sentuhan layar.
 * - Penghitungan kendaraan berdasarkan titik Bottom Center.
 * - Pengambilan frame terakhir untuk analisis foto.
 */
@Composable
fun CameraScreen(
    trafficLight: String,
    trafficGroup: String,
    onBack: () -> Unit,
    onCaptureForAnalysis: (Uri, RoiSelection) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val mainHandler = remember {
        Handler(Looper.getMainLooper())
    }

    val analysisExecutor = remember {
        Executors.newSingleThreadExecutor()
    }

    val frameStore = remember {
        LatestCameraFrameStore()
    }

    val roiReference = remember {
        AtomicReference(FULL_FRAME_ROI)
    }

    val screenActive = remember {
        AtomicBoolean(true)
    }

    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var detector by remember {
        mutableStateOf<YoloDetector?>(null)
    }

    var cameraReady by remember {
        mutableStateOf(false)
    }

    var cameraError by remember {
        mutableStateOf<String?>(null)
    }

    var currentResult by remember {
        mutableStateOf<YoloAnalysisResult?>(null)
    }

    // Ukuran frame diisi sebelum inferensi YOLO dijalankan.
    var frameSize by remember {
        mutableStateOf(IntSize.Zero)
    }

    var isEditingRoi by remember {
        mutableStateOf(false)
    }

    var roiPoints by remember {
        mutableStateOf<List<RoiPoint>>(emptyList())
    }

    var savedRoi by remember {
        mutableStateOf<RoiSelection?>(null)
    }

    var isSavingFrame by remember {
        mutableStateOf(false)
    }

    // ---------------------------------------------------------
    // PERMISSION KAMERA
    // ---------------------------------------------------------

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->

        hasCameraPermission = granted

        if (!granted) {
            cameraError =
                "Izin kamera belum diberikan. Aktifkan izin kamera untuk menggunakan fitur ini."
        } else {
            cameraError = null
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(
                Manifest.permission.CAMERA
            )
        }
    }

    // ---------------------------------------------------------
    // MEMUAT MODEL YOLO
    // ---------------------------------------------------------

    LaunchedEffect(context) {
        try {
            detector = withContext(Dispatchers.IO) {
                YoloDetector(
                    context = context.applicationContext,
                    confidenceThreshold = 0.25f,
                    iouThreshold = 0.45f,
                    numThreads = 4
                )
            }
        } catch (error: Exception) {
            cameraError = error.localizedMessage
                ?: "Model YOLO gagal dimuat."
        }
    }

    // ---------------------------------------------------------
    // MEMPERBARUI ROI UNTUK THREAD YOLO
    // ---------------------------------------------------------

    SideEffect {

        val roiForLiveDetection = when {

            savedRoi != null -> {
                savedRoi!!
            }

            isEditingRoi && roiPoints.size >= 3 -> {
                runCatching {
                    RoiSelection(roiPoints)
                }.getOrNull() ?: FULL_FRAME_ROI
            }

            else -> {
                FULL_FRAME_ROI
            }
        }

        roiReference.set(roiForLiveDetection)
    }

    // ---------------------------------------------------------
    // CAMERA X
    // ---------------------------------------------------------

    DisposableEffect(
        previewView,
        hasCameraPermission,
        detector,
        lifecycleOwner
    ) {

        val disposed = AtomicBoolean(false)

        var boundProvider: ProcessCameraProvider? = null
        var boundAnalysis: ImageAnalysis? = null

        if (hasCameraPermission && detector != null) {

            cameraReady = false

            val providerFuture =
                ProcessCameraProvider.getInstance(context)

            providerFuture.addListener({

                if (disposed.get()) {
                    return@addListener
                }

                try {

                    val provider = providerFuture.get()

                    val rotation = previewView.display?.rotation
                        ?: AndroidSurface.ROTATION_0

                    // Preview kamera.
                    val preview = Preview.Builder()
                        .setTargetRotation(rotation)
                        .build()
                        .also {
                            it.setSurfaceProvider(
                                previewView.surfaceProvider
                            )
                        }

                    // Analisis frame kamera.
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetRotation(rotation)
                        .setOutputImageFormat(
                            ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
                        )
                        .setBackpressureStrategy(
                            ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                        )
                        .build()

                    val activeDetector = detector
                        ?: throw IllegalStateException(
                            "Model YOLO belum siap."
                        )

                    var lastInferenceAt = 0L

                    imageAnalysis.setAnalyzer(
                        analysisExecutor
                    ) { imageProxy ->

                        var frameBitmap: Bitmap? = null
                        var frameStored = false

                        try {

                            val now =
                                SystemClock.elapsedRealtime()

                            // Batasi inferensi sekitar 3-4 FPS.
                            if (now - lastInferenceAt < 300L) {
                                return@setAnalyzer
                            }

                            lastInferenceAt = now

                            val bitmap = imageProxyToBitmap(
                                imageProxy
                            ) ?: return@setAnalyzer

                            frameBitmap = bitmap

                            // PENTING:
                            // Ukuran frame diupdate sebelum YOLO berjalan.
                            // Dengan demikian, pengguna tetap bisa mengetuk
                            // layar meskipun inferensi belum selesai.
                            val analyzedWidth = bitmap.width
                            val analyzedHeight = bitmap.height

                            mainHandler.post {

                                if (screenActive.get()) {

                                    frameSize = IntSize(
                                        analyzedWidth,
                                        analyzedHeight
                                    )
                                }
                            }

                            // Jalankan deteksi YOLO.
                            val result = activeDetector.detect(
                                sourceBitmap = bitmap,
                                roi = roiReference.get()
                            )

                            // Simpan frame terbaru.
                            frameStore.update(
                                bitmap,
                                result
                            )

                            frameStored = true

                            mainHandler.post {

                                if (screenActive.get()) {

                                    currentResult = result
                                    cameraError = null
                                }
                            }

                        } catch (error: Exception) {

                            mainHandler.post {

                                if (screenActive.get()) {

                                    cameraError =
                                        error.localizedMessage
                                            ?: "Gagal menganalisis frame kamera."
                                }
                            }

                        } finally {

                            // Bitmap yang belum disimpan ke frameStore
                            // harus dibersihkan.
                            if (!frameStored) {

                                frameBitmap?.let {
                                    if (!it.isRecycled) {
                                        it.recycle()
                                    }
                                }
                            }

                            imageProxy.close()
                        }
                    }

                    // Pasang use case kamera.
                    provider.unbindAll()

                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )

                    boundProvider = provider
                    boundAnalysis = imageAnalysis

                    mainHandler.post {

                        if (screenActive.get()) {

                            cameraReady = true
                            cameraError = null
                        }
                    }

                } catch (error: Exception) {

                    mainHandler.post {

                        if (screenActive.get()) {

                            cameraReady = false

                            cameraError =
                                error.localizedMessage
                                    ?: "Kamera tidak dapat diaktifkan pada perangkat ini."
                        }
                    }
                }

            }, ContextCompat.getMainExecutor(context))
        }

        onDispose {

            disposed.set(true)

            boundAnalysis?.clearAnalyzer()
            boundProvider?.unbindAll()
        }
    }

    // ---------------------------------------------------------
    // MENUTUP MODEL YOLO
    // ---------------------------------------------------------

    DisposableEffect(detector) {

        onDispose {
            detector?.close()
        }
    }

    // ---------------------------------------------------------
    // MEMBERSIHKAN RESOURCE SAAT KELUAR
    // ---------------------------------------------------------

    DisposableEffect(Unit) {

        onDispose {

            screenActive.set(false)

            frameStore.clear()

            analysisExecutor.shutdownNow()
        }
    }

    // ---------------------------------------------------------
    // FUNGSI MENGATUR ROI
    // ---------------------------------------------------------

    fun beginRoiEditing() {

        roiPoints = savedRoi?.points ?: emptyList()

        savedRoi = null

        isEditingRoi = true

        currentResult = null

        cameraError = null
    }

    fun saveRoi() {

        if (roiPoints.size < 3) {

            cameraError =
                "Tambahkan minimal 3 titik untuk membuat ROI."

            return
        }

        val polygon = runCatching {
            RoiSelection(roiPoints)
        }.getOrNull()

        if (polygon == null) {

            cameraError =
                "Polygon ROI belum valid. Atur ulang titiknya."

            return
        }

        savedRoi = polygon

        roiPoints = polygon.points

        isEditingRoi = false

        cameraError = null
    }

    // ---------------------------------------------------------
    // MENYIMPAN FRAME TERAKHIR
    // ---------------------------------------------------------

    fun captureLatestFrame() {

        val activeRoi = savedRoi ?: return

        if (isSavingFrame) return

        coroutineScope.launch {

            isSavingFrame = true

            var snapshot: Bitmap? = null

            try {

                val frameSnapshot = withContext(
                    Dispatchers.IO
                ) {

                    frameStore.snapshot()

                } ?: throw IllegalStateException(
                    "Frame kamera belum tersedia. Arahkan kamera hingga deteksi muncul."
                )

                snapshot = frameSnapshot

                val savedFile = withContext(
                    Dispatchers.IO
                ) {

                    val outputFile = File(
                        context.cacheDir,
                        "camera_frame_${System.currentTimeMillis()}.jpg"
                    )

                    FileOutputStream(outputFile).use { output ->

                        val compressed = frameSnapshot.compress(
                            Bitmap.CompressFormat.JPEG,
                            94,
                            output
                        )

                        check(compressed) {
                            "Frame kamera gagal disimpan."
                        }
                    }

                    outputFile
                }

                snapshot?.recycle()
                snapshot = null

                isSavingFrame = false

                onCaptureForAnalysis(
                    Uri.fromFile(savedFile),
                    activeRoi
                )

            } catch (error: Exception) {

                cameraError = error.localizedMessage
                    ?: "Gagal menyimpan frame kamera."

            } finally {

                snapshot?.let {

                    if (!it.isRecycled) {
                        it.recycle()
                    }
                }

                isSavingFrame = false
            }
        }
    }

    // ---------------------------------------------------------
    // TAMPILAN UTAMA
    // ---------------------------------------------------------

    Scaffold(
        containerColor = BackgroundLight
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(
                    horizontal = 20.dp,
                    vertical = 12.dp
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // -------------------------------------------------
            // HEADER
            // -------------------------------------------------

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = SurfaceWhite,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        BorderLight
                    )
                ) {

                    IconButton(
                        onClick = onBack
                    ) {

                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = TextPrimary
                        )
                    }
                }

                Spacer(
                    Modifier.width(12.dp)
                )

                Text(
                    text = "Kamera HP",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    Modifier.weight(1f)
                )

                Surface(
                    shape = CircleShape,
                    color = SurfaceSoft
                ) {

                    Text(
                        text = "$trafficLight · TL$trafficGroup",
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 7.dp
                        ),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // -------------------------------------------------
            // PREVIEW KAMERA DAN OVERLAY ROI
            // -------------------------------------------------

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(
                        RoundedCornerShape(24.dp)
                    )
                    .background(
                        Color(0xFF1F2226)
                    )
                    .border(
                        width = 1.dp,
                        color = BorderLight,
                        shape = RoundedCornerShape(24.dp)
                    )
            ) {

                if (hasCameraPermission) {

                    // Preview kamera.
                    AndroidView(
                        factory = {
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Overlay deteksi dan pengaturan ROI.
                    CameraDetectionOverlay(
                        result = currentResult,
                        imageSize = frameSize,
                        roiPoints = if (isEditingRoi) {
                            roiPoints
                        } else {
                            savedRoi?.points.orEmpty()
                        },
                        isEditingRoi = isEditingRoi,
                        onAddPoint = { newPoint ->

                            if (
                                isEditingRoi &&
                                roiPoints.size < 10
                            ) {

                                roiPoints =
                                    roiPoints + newPoint

                                cameraError = null
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                } else {

                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )

                        Text(
                            text = "Izin kamera diperlukan",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )

                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    Manifest.permission.CAMERA
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryRed
                            )
                        ) {

                            Text("Izinkan Kamera")
                        }
                    }
                }

                // -------------------------------------------------
                // STATUS KAMERA
                // -------------------------------------------------

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    shape = CircleShape,
                    color = Color(0xB3000000)
                ) {

                    Row(
                        modifier = Modifier.padding(
                            horizontal = 10.dp,
                            vertical = 6.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {

                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (cameraReady) {
                                        Color(0xFF39C477)
                                    } else {
                                        Color(0xFFFFC247)
                                    }
                                )
                        )

                        Text(
                            text = if (cameraReady) {
                                "LIVE · YOLO"
                            } else {
                                "MENGHUBUNGKAN"
                            },
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // -------------------------------------------------
                // LOADING
                // -------------------------------------------------

                if (
                    hasCameraPermission &&
                    !cameraReady &&
                    detector != null
                ) {

                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(34.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                }

                // -------------------------------------------------
                // PETUNJUK JUMLAH TITIK ROI
                // -------------------------------------------------

                if (isEditingRoi) {

                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(10.dp),
                        shape = CircleShape,
                        color = Color(0xB3000000)
                    ) {

                        Text(
                            text = "Titik ROI: ${roiPoints.size}/10",
                            modifier = Modifier.padding(
                                horizontal = 12.dp,
                                vertical = 7.dp
                            ),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // -------------------------------------------------
            // PETUNJUK
            // -------------------------------------------------

            Text(
                text = when {

                    isEditingRoi -> {
                        "Ketuk beberapa titik pada preview kamera untuk membentuk area jalan. Minimal 3 titik."
                    }

                    savedRoi != null -> {
                        "Deteksi kendaraan berjalan pada polygon ROI yang dipilih."
                    }

                    else -> {
                        "Arahkan kamera ke jalur. Tekan Atur ROI untuk menentukan area analisis."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                color = TextSecondary,
                fontSize = 13.sp
            )

            // -------------------------------------------------
            // KARTU JUMLAH KENDARAAN
            // -------------------------------------------------

            CameraLiveCountCard(
                result = currentResult,
                roiReady = savedRoi != null ||
                        (isEditingRoi && roiPoints.size >= 3),
                isModelReady = detector != null
            )

            // -------------------------------------------------
            // PESAN ERROR
            // -------------------------------------------------

            if (!cameraError.isNullOrBlank()) {

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFFF1F1)
                ) {

                    Text(
                        text = cameraError.orEmpty(),
                        modifier = Modifier.padding(12.dp),
                        color = PrimaryRedDark,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(
                Modifier.weight(1f)
            )

            // -------------------------------------------------
            // TOMBOL ROI DAN ANALISIS
            // -------------------------------------------------

            when {

                // Saat mengatur ROI.
                isEditingRoi -> {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {

                        OutlinedButton(
                            onClick = {
                                roiPoints = emptyList()
                                cameraError = null
                            },
                            modifier = Modifier.weight(1f),
                            shape = CircleShape
                        ) {

                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null
                            )

                            Spacer(
                                Modifier.width(6.dp)
                            )

                            Text("Reset")
                        }

                        Button(
                            onClick = {
                                saveRoi()
                            },
                            enabled = roiPoints.size >= 3,
                            modifier = Modifier.weight(1.4f),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryRed,
                                disabledContainerColor = Color(0xFFE8A7AD)
                            )
                        ) {

                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null
                            )

                            Spacer(
                                Modifier.width(6.dp)
                            )

                            Text("Simpan ROI")
                        }
                    }
                }

                // ROI belum dibuat.
                savedRoi == null -> {

                    Button(
                        onClick = {
                            beginRoiEditing()
                        },
                        enabled = hasCameraPermission && cameraReady,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryRed
                        )
                    ) {

                        Text(
                            text = "Atur ROI",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // ROI sudah disimpan.
                else -> {

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        Button(
                            onClick = {
                                captureLatestFrame()
                            },
                            enabled = cameraReady &&
                                    currentResult != null &&
                                    !isSavingFrame,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryRed
                            )
                        ) {

                            if (isSavingFrame) {

                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )

                                Spacer(
                                    Modifier.width(8.dp)
                                )

                                Text("Menyimpan frame...")

                            } else {

                                Icon(
                                    imageVector = Icons.Filled.CameraAlt,
                                    contentDescription = null
                                )

                                Spacer(
                                    Modifier.width(8.dp)
                                )

                                Text(
                                    text = "Analisis Frame Terakhir",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                beginRoiEditing()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = CircleShape
                        ) {

                            Text("Ubah ROI")
                        }
                    }
                }
            }
        }
    }
}

// =============================================================
// KARTU JUMLAH KENDARAAN
// =============================================================

@Composable
private fun CameraLiveCountCard(
    result: YoloAnalysisResult?,
    roiReady: Boolean,
    isModelReady: Boolean
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceWhite
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            BorderLight
        )
    ) {

        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                Text(
                    text = if (roiReady) {
                        "Kendaraan dalam ROI"
                    } else {
                        "Kendaraan terdeteksi"
                    },
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = when {

                        !isModelReady -> {
                            "Memuat model"
                        }

                        result == null -> {
                            "Menunggu frame"
                        }

                        else -> {
                            "${result.inferenceTimeMs} ms"
                        }
                    },
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = (result?.counts?.total ?: 0).toString(),
                    color = TextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    Modifier.width(12.dp)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {

                    Text(
                        text = "kendaraan",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )

                    Text(
                        text = "Berdasarkan titik Bottom Center",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Spacer(
                    Modifier.weight(1f)
                )

                Surface(
                    shape = CircleShape,
                    color = if (roiReady) {
                        Color(0xFFE9F6EF)
                    } else {
                        PrimaryRedLight
                    }
                ) {

                    Text(
                        text = if (roiReady) {
                            "ROI AKTIF"
                        } else {
                            "ROI BELUM DIATUR"
                        },
                        modifier = Modifier.padding(
                            horizontal = 9.dp,
                            vertical = 6.dp
                        ),
                        color = if (roiReady) {
                            Color(0xFF17794A)
                        } else {
                            PrimaryRedDark
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                CameraVehicleCount(
                    label = "Mobil",
                    count = result?.counts?.car ?: 0
                )

                CameraVehicleCount(
                    label = "Motor",
                    count = result?.counts?.motorcycle ?: 0
                )

                CameraVehicleCount(
                    label = "Bus",
                    count = result?.counts?.bus ?: 0
                )

                CameraVehicleCount(
                    label = "Truk",
                    count = result?.counts?.truck ?: 0
                )
            }
        }
    }
}

// =============================================================
// JUMLAH KENDARAAN PER KELAS
// =============================================================

@Composable
private fun CameraVehicleCount(
    label: String,
    count: Int
) {

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = count.toString(),
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )

        Text(
            text = label,
            color = TextSecondary,
            fontSize = 10.sp
        )
    }
}

// =============================================================
// OVERLAY DETEKSI DAN ROI
// =============================================================

@Composable
private fun CameraDetectionOverlay(
    result: YoloAnalysisResult?,
    imageSize: IntSize,
    roiPoints: List<RoiPoint>,
    isEditingRoi: Boolean,
    onAddPoint: (RoiPoint) -> Unit,
    modifier: Modifier = Modifier
) {

    /*
     * PENTING:
     * pointerInput dipasang pada Box pembungkus,
     * bukan hanya pada Canvas.
     *
     * Dengan demikian, area overlay menjadi area
     * penerima sentuhan untuk penentuan titik ROI.
     */

    Box(
        modifier = modifier.pointerInput(
            isEditingRoi,
            imageSize,
            roiPoints
        ) {

            if (isEditingRoi) {

                detectTapGestures { tap ->

                    if (
                        imageSize.width > 0 &&
                        imageSize.height > 0
                    ) {

                        val mapping = calculatePreviewMapping(
                            canvasWidth = size.width.toFloat(),
                            canvasHeight = size.height.toFloat(),
                            imageWidth = imageSize.width.toFloat(),
                            imageHeight = imageSize.height.toFloat()
                        )

                        val point = mapping.toNormalizedPoint(
                            tap
                        )

                        if (point != null) {
                            onAddPoint(point)
                        }
                    }
                }
            }
        }
    ) {

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {

            if (
                imageSize.width <= 0 ||
                imageSize.height <= 0
            ) {
                return@Canvas
            }

            val mapping = calculatePreviewMapping(
                canvasWidth = size.width,
                canvasHeight = size.height,
                imageWidth = imageSize.width.toFloat(),
                imageHeight = imageSize.height.toFloat()
            )

            // -------------------------------------------------
            // GAMBAR BOUNDING BOX KENDARAAN
            // -------------------------------------------------

            result?.detections?.forEach { detection ->

                val topLeft = mapping.project(
                    detection.left,
                    detection.top
                )

                val bottomRight = mapping.project(
                    detection.right,
                    detection.bottom
                )

                drawRect(
                    color = if (detection.isInsideRoi) {
                        Color(0xFF35D07F)
                    } else {
                        Color(0xFFB4BAC2)
                    },
                    topLeft = topLeft,
                    size = androidx.compose.ui.geometry.Size(
                        width = bottomRight.x - topLeft.x,
                        height = bottomRight.y - topLeft.y
                    ),
                    style = Stroke(
                        width = 2.dp.toPx()
                    )
                )

                // Titik Bottom Center.
                drawCircle(
                    color = Color(0xFFFFD400),
                    radius = 4.5.dp.toPx(),
                    center = mapping.project(
                        detection.bottomCenterX,
                        detection.bottomCenterY
                    )
                )
            }

            // -------------------------------------------------
            // GAMBAR POLYGON ROI
            // -------------------------------------------------

            if (roiPoints.isNotEmpty()) {

                val path = Path()

                roiPoints.forEachIndexed { index, point ->

                    val canvasPoint =
                        mapping.projectNormalized(point)

                    if (index == 0) {

                        path.moveTo(
                            canvasPoint.x,
                            canvasPoint.y
                        )

                    } else {

                        path.lineTo(
                            canvasPoint.x,
                            canvasPoint.y
                        )
                    }
                }

                // Hubungkan titik terakhir dengan titik pertama.
                if (roiPoints.size >= 3) {
                    path.close()
                }

                drawPath(
                    path = path,
                    color = if (isEditingRoi) {
                        Color(0xFFFFD15C)
                    } else {
                        Color(0xFF35D07F)
                    },
                    style = Stroke(
                        width = 3.dp.toPx()
                    )
                )

                // -------------------------------------------------
                // GAMBAR TITIK-TITIK ROI
                // -------------------------------------------------

                roiPoints.forEach { point ->

                    val canvasPoint =
                        mapping.projectNormalized(point)

                    // Lingkaran luar.
                    drawCircle(
                        color = Color.White,
                        radius = 8.dp.toPx(),
                        center = canvasPoint
                    )

                    // Lingkaran dalam.
                    drawCircle(
                        color = if (isEditingRoi) {
                            Color(0xFFD52B3A)
                        } else {
                            Color(0xFF138052)
                        },
                        radius = 5.dp.toPx(),
                        center = canvasPoint
                    )
                }
            }
        }
    }
}

// =============================================================
// PEMETAAN KOORDINAT PREVIEW KAMERA
// =============================================================

private data class PreviewMapping(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val imageWidth: Float,
    val imageHeight: Float
) {

    fun project(
        x: Float,
        y: Float
    ) = androidx.compose.ui.geometry.Offset(
        offsetX + x * scale,
        offsetY + y * scale
    )

    fun projectNormalized(
        point: RoiPoint
    ) = project(
        point.x * imageWidth,
        point.y * imageHeight
    )

    fun toNormalizedPoint(
        offset: androidx.compose.ui.geometry.Offset
    ): RoiPoint? {

        if (
            imageWidth <= 0f ||
            imageHeight <= 0f ||
            scale <= 0f
        ) {
            return null
        }

        val x = (
                (offset.x - offsetX) /
                        scale /
                        imageWidth
                )

        val y = (
                (offset.y - offsetY) /
                        scale /
                        imageHeight
                )

        if (
            !x.isFinite() ||
            !y.isFinite()
        ) {
            return null
        }

        // Abaikan ketukan pada bagian preview
        // yang berada di luar gambar akibat crop.
        if (
            x < 0f ||
            x > 1f ||
            y < 0f ||
            y > 1f
        ) {
            return null
        }

        return RoiPoint(
            x = x.coerceIn(0f, 1f),
            y = y.coerceIn(0f, 1f)
        )
    }
}

// =============================================================
// MENGHITUNG SKALA GAMBAR PREVIEW
// =============================================================

private fun calculatePreviewMapping(
    canvasWidth: Float,
    canvasHeight: Float,
    imageWidth: Float,
    imageHeight: Float
): PreviewMapping {

    val scale = max(
        canvasWidth / imageWidth,
        canvasHeight / imageHeight
    )

    val displayedWidth = imageWidth * scale
    val displayedHeight = imageHeight * scale

    return PreviewMapping(
        scale = scale,
        offsetX = (canvasWidth - displayedWidth) / 2f,
        offsetY = (canvasHeight - displayedHeight) / 2f,
        imageWidth = imageWidth,
        imageHeight = imageHeight
    )
}

// =============================================================
// KONVERSI IMAGE PROXY MENJADI BITMAP
// =============================================================

private fun imageProxyToBitmap(
    imageProxy: ImageProxy
): Bitmap? {

    val plane = imageProxy.planes.firstOrNull()
        ?: return null

    val buffer = plane.buffer.duplicate()

    val width = imageProxy.width
    val height = imageProxy.height

    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride

    if (
        width <= 0 ||
        height <= 0 ||
        pixelStride < 4
    ) {
        return null
    }

    val pixels = IntArray(
        width * height
    )

    val bufferStart = buffer.position()

    for (y in 0 until height) {

        val rowOffset =
            bufferStart + y * rowStride

        for (x in 0 until width) {

            val pixelOffset =
                rowOffset + x * pixelStride

            if (pixelOffset + 3 >= buffer.limit()) {
                return null
            }

            val red =
                buffer.get(pixelOffset).toInt() and 0xFF

            val green =
                buffer.get(pixelOffset + 1).toInt() and 0xFF

            val blue =
                buffer.get(pixelOffset + 2).toInt() and 0xFF

            val alpha =
                buffer.get(pixelOffset + 3).toInt() and 0xFF

            pixels[y * width + x] =
                android.graphics.Color.argb(
                    alpha,
                    red,
                    green,
                    blue
                )
        }
    }

    val unrotated = Bitmap.createBitmap(
        width,
        height,
        Bitmap.Config.ARGB_8888
    )

    unrotated.setPixels(
        pixels,
        0,
        width,
        0,
        0,
        width,
        height
    )

    val rotationDegrees =
        imageProxy.imageInfo.rotationDegrees

    if (rotationDegrees == 0) {
        return unrotated
    }

    val matrix = Matrix().apply {
        postRotate(
            rotationDegrees.toFloat()
        )
    }

    val rotated = Bitmap.createBitmap(
        unrotated,
        0,
        0,
        unrotated.width,
        unrotated.height,
        matrix,
        true
    )

    if (rotated !== unrotated) {
        unrotated.recycle()
    }

    return rotated
}

// =============================================================
// PENYIMPAN FRAME KAMERA TERBARU
// =============================================================

private class LatestCameraFrameStore {

    private val lock = Any()

    private var latestBitmap: Bitmap? = null

    private var latestResult: YoloAnalysisResult? = null

    fun update(
        bitmap: Bitmap,
        result: YoloAnalysisResult
    ) {

        synchronized(lock) {

            val previous = latestBitmap

            latestBitmap = bitmap
            latestResult = result

            if (
                previous != null &&
                previous !== bitmap &&
                !previous.isRecycled
            ) {
                previous.recycle()
            }
        }
    }

    fun snapshot(): Bitmap? {

        return synchronized(lock) {

            latestBitmap
                ?.takeUnless { it.isRecycled }
                ?.copy(
                    Bitmap.Config.ARGB_8888,
                    false
                )
        }
    }

    fun clear() {

        synchronized(lock) {

            latestBitmap?.let {

                if (!it.isRecycled) {
                    it.recycle()
                }
            }

            latestBitmap = null
            latestResult = null
        }
    }
}

// =============================================================
// ROI DEFAULT: SELURUH FRAME
// =============================================================

private val FULL_FRAME_ROI = RoiSelection(
    points = listOf(
        RoiPoint(0f, 0f),
        RoiPoint(1f, 0f),
        RoiPoint(1f, 1f),
        RoiPoint(0f, 1f)
    )
)