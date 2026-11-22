package com.example.smarttrafficlight.feature.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RestartAlt

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

import com.example.smarttrafficlight.ui.theme.BackgroundLight
import com.example.smarttrafficlight.ui.theme.BorderLight
import com.example.smarttrafficlight.ui.theme.PrimaryRed
import com.example.smarttrafficlight.ui.theme.PrimaryRedDark
import com.example.smarttrafficlight.ui.theme.PrimaryRedLight
import com.example.smarttrafficlight.ui.theme.SurfaceSoft
import com.example.smarttrafficlight.ui.theme.SurfaceWhite
import com.example.smarttrafficlight.ui.theme.TextPrimary
import com.example.smarttrafficlight.ui.theme.TextSecondary


// =====================================================
// DATA TITIK ROI
// Koordinat relatif terhadap foto: 0.0 sampai 1.0
// =====================================================

data class RoiPoint(
    val x: Float,
    val y: Float
)


// =====================================================
// DATA POLYGON ROI
// =====================================================

data class RoiSelection(
    val points: List<RoiPoint>
) {

    init {
        require(points.size >= 3) {
            "Polygon ROI harus memiliki minimal tiga titik."
        }

        require(points.all {
            it.x in 0f..1f && it.y in 0f..1f
        }) {
            "Koordinat titik ROI tidak valid."
        }
    }

    // Bounding envelope untuk kompatibilitas
    // dengan tampilan AppNavigation yang lama.
    // Bukan bentuk polygon yang digunakan YOLO.

    val left: Float
        get() = points.minOfOrNull { it.x } ?: 0f

    val top: Float
        get() = points.minOfOrNull { it.y } ?: 0f

    val right: Float
        get() = points.maxOfOrNull { it.x } ?: 0f

    val bottom: Float
        get() = points.maxOfOrNull { it.y } ?: 0f
}


// =====================================================
// KONFIGURASI EDITOR
// =====================================================

private const val MAX_ROI_POINTS = 12

private val RoiPreviewDark = Color(0xFF1F2226)
private val RoiValidGreen = Color(0xFF17794A)
private val RoiValidBackground = Color(0xFFE9F6EF)


// =====================================================
// EDITOR ROI SCREEN
// =====================================================

@Composable
fun EditorRoiScreen(
    imageUri: Uri?,
    trafficLight: String,
    trafficGroup: String,
    onBack: () -> Unit,
    onContinue: (RoiSelection) -> Unit
) {

    val context = LocalContext.current

    var bitmap by remember(imageUri) {
        mutableStateOf<Bitmap?>(null)
    }

    var isLoading by remember(imageUri) {
        mutableStateOf(imageUri != null)
    }

    var loadError by remember(imageUri) {
        mutableStateOf<String?>(null)
    }

    // Daftar titik polygon.

    var roiPoints by remember(imageUri) {
        mutableStateOf<List<RoiPoint>>(emptyList())
    }

    // Mode penambahan titik.

    var isAddingPoint by remember(imageUri) {
        mutableStateOf(true)
    }

    val isValidRoi = remember(roiPoints) {
        isValidPolygon(roiPoints)
    }

    // =================================================
    // MEMUAT FOTO
    // =================================================

    LaunchedEffect(imageUri) {

        bitmap = null
        loadError = null
        roiPoints = emptyList()
        isAddingPoint = true

        if (imageUri == null) {

            isLoading = false

            loadError = "Foto belum tersedia."

            return@LaunchedEffect
        }

        isLoading = true

        try {

            bitmap = withContext(Dispatchers.IO) {
                decodeImage(context, imageUri)
            }

            if (bitmap == null) {
                loadError = "Foto tidak dapat dibuka."
            }

        } catch (error: Exception) {

            loadError = error.localizedMessage
                ?: "Gagal membaca foto."

        } finally {

            isLoading = false
        }
    }

    // Membersihkan bitmap ketika halaman ditutup
    // atau gambar diganti.

    DisposableEffect(bitmap) {

        val currentBitmap = bitmap

        onDispose {

            if (
                currentBitmap != null &&
                !currentBitmap.isRecycled
            ) {
                currentBitmap.recycle()
            }
        }
    }

    // =================================================
    // TAMPILAN
    // =================================================

    Scaffold(

        containerColor = BackgroundLight,

        bottomBar = {

            Column(

                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundLight)
                    .navigationBarsPadding()
                    .padding(
                        horizontal = 20.dp,
                        vertical = 12.dp
                    ),

                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                // SIMPAN ROI

                Button(

                    onClick = {

                        if (isValidRoi) {

                            onContinue(
                                RoiSelection(
                                    points = roiPoints.toList()
                                )
                            )
                        }
                    },

                    enabled = isValidRoi,

                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),

                    shape = CircleShape,

                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryRed,
                        contentColor = SurfaceWhite,
                        disabledContainerColor = BorderLight,
                        disabledContentColor = TextSecondary
                    )

                ) {

                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null
                    )

                    Spacer(
                        modifier = Modifier.size(8.dp)
                    )

                    Text(
                        text = "Simpan ROI",
                        fontWeight = FontWeight.Bold
                    )
                }

                // BATALKAN

                OutlinedButton(

                    onClick = onBack,

                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),

                    shape = CircleShape,

                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = SurfaceWhite,
                        contentColor = TextPrimary
                    ),

                    border = BorderStroke(
                        1.dp,
                        BorderLight
                    )

                ) {

                    Text("Batalkan")
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
                    vertical = 18.dp
                ),

            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // =================================================
            // HEADER
            // =================================================

            Row(

                modifier = Modifier.fillMaxWidth(),

                verticalAlignment = Alignment.CenterVertically,

                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                Box(

                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SurfaceWhite)
                        .border(
                            1.dp,
                            BorderLight,
                            CircleShape
                        )
                        .clickable(onClick = onBack),

                    contentAlignment = Alignment.Center

                ) {

                    Icon(
                        imageVector = Icons.Outlined.ArrowBack,
                        contentDescription = "Kembali",
                        tint = TextPrimary
                    )
                }

                Text(

                    text = "Editor ROI",

                    style = MaterialTheme.typography.titleLarge,

                    color = TextPrimary,

                    fontWeight = FontWeight.Bold,

                    modifier = Modifier.weight(1f)
                )

                Surface(

                    shape = CircleShape,

                    color = SurfaceSoft
                ) {

                    Text(

                        text = "$trafficLight · $trafficGroup",

                        modifier = Modifier.padding(
                            horizontal = 10.dp,
                            vertical = 7.dp
                        ),

                        color = TextSecondary,

                        fontSize = 11.sp,

                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // =================================================
            // PETUNJUK DAN PREVIEW
            // =================================================

            when {

                isLoading -> {

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(316.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(RoiPreviewDark),

                        contentAlignment = Alignment.Center
                    ) {

                        Text(
                            text = "Memuat foto...",
                            color = SurfaceWhite
                        )
                    }
                }

                bitmap != null -> {

                    RoiImageEditor(

                        bitmap = bitmap!!,

                        points = roiPoints,

                        isAddingPoint = isAddingPoint,

                        onPointsChanged = { updated ->
                            roiPoints = updated
                        }
                    )
                }

                else -> {

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(316.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(RoiPreviewDark),

                        contentAlignment = Alignment.Center
                    ) {

                        Text(
                            text = loadError ?: "Foto tidak tersedia",
                            color = SurfaceWhite,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            }

            // =================================================
            // JUMLAH TITIK DAN STATUS VALIDITAS
            // =================================================

            Row(

                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement = Arrangement.SpaceBetween,

                verticalAlignment = Alignment.CenterVertically
            ) {

                val pointText = if (roiPoints.size >= 3) {

                    "${roiPoints.size} titik · satu poligon"

                } else {

                    "${roiPoints.size} titik · minimal 3 titik"
                }

                Text(
                    text = pointText,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )

                Surface(

                    shape = CircleShape,

                    color = if (isValidRoi) {
                        RoiValidBackground
                    } else {
                        SurfaceSoft
                    }
                ) {

                    Row(

                        modifier = Modifier.padding(
                            horizontal = 11.dp,
                            vertical = 6.dp
                        ),

                        verticalAlignment = Alignment.CenterVertically,

                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {

                        if (isValidRoi) {

                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = RoiValidGreen
                            )
                        }

                        Text(

                            text = if (isValidRoi) {
                                "Valid"
                            } else {
                                "Belum valid"
                            },

                            fontSize = 11.sp,

                            fontWeight = FontWeight.Bold,

                            color = if (isValidRoi) {
                                RoiValidGreen
                            } else {
                                TextSecondary
                            }
                        )
                    }
                }
            }

            // =================================================
            // TOMBOL TAMBAH / HAPUS / RESET
            // =================================================

            Row(

                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                RoiActionButton(

                    title = "Tambah",

                    icon = {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                    },

                    modifier = Modifier.weight(1f),

                    enabled = roiPoints.size < MAX_ROI_POINTS,

                    onClick = {
                        isAddingPoint = true
                    }
                )

                RoiActionButton(

                    title = "Hapus",

                    icon = {
                        Icon(
                            Icons.Outlined.Remove,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                    },

                    modifier = Modifier.weight(1f),

                    enabled = roiPoints.isNotEmpty(),

                    onClick = {

                        roiPoints = roiPoints.dropLast(1)

                        isAddingPoint = true
                    }
                )

                RoiActionButton(

                    title = "Reset",

                    icon = {
                        Icon(
                            Icons.Outlined.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                    },

                    modifier = Modifier.weight(1f),

                    enabled = roiPoints.isNotEmpty(),

                    onClick = {

                        roiPoints = emptyList()

                        isAddingPoint = true
                    }
                )
            }

            // =================================================
            // PETUNJUK BOTTOM CENTER
            // =================================================

            Row(

                modifier = Modifier.fillMaxWidth(),

                verticalAlignment = Alignment.CenterVertically,

                horizontalArrangement = Arrangement.Center
            ) {

                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = TextSecondary
                )

                Spacer(
                    modifier = Modifier.size(6.dp)
                )

                Text(
                    text = "Kendaraan dihitung dari titik tengah bawah.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            // PETUNJUK PENGGUNAAN

            if (bitmap != null && roiPoints.isNotEmpty()) {

                Text(
                    text = if (roiPoints.size < 3) {
                        "Tambahkan minimal tiga titik lagi untuk membentuk area ROI."
                    } else if (!isValidRoi) {
                        "Periksa posisi titik. Polygon tidak boleh saling berpotongan."
                    } else {
                        "Geser titik merah untuk menyesuaikan batas area jalan."
                    },
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )

            } else if (bitmap != null) {

                Text(
                    text = "Tekan Tambah, lalu ketuk foto untuk menempatkan titik pertama.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}


// =====================================================
// TOMBOL AKSI ROI
// =====================================================

@Composable
private fun RoiActionButton(
    title: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {

    OutlinedButton(

        onClick = onClick,

        enabled = enabled,

        modifier = modifier.height(44.dp),

        shape = RoundedCornerShape(18.dp),

        contentPadding = PaddingValues(
            horizontal = 4.dp,
            vertical = 4.dp
        ),

        border = BorderStroke(
            1.dp,
            BorderLight
        ),

        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = SurfaceWhite,
            contentColor = TextPrimary,
            disabledContainerColor = SurfaceWhite,
            disabledContentColor = TextSecondary.copy(alpha = 0.5f)
        )

    ) {

        icon()

        Spacer(
            modifier = Modifier.size(5.dp)
        )

        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}


// =====================================================
// KOMPONEN FOTO DENGAN POLYGON ROI
// =====================================================

@Composable
private fun RoiImageEditor(
    bitmap: Bitmap,
    points: List<RoiPoint>,
    isAddingPoint: Boolean,
    onPointsChanged: (List<RoiPoint>) -> Unit
) {

    val imageBitmap = remember(bitmap) {
        bitmap.asImageBitmap()
    }

    var canvasSize by remember(bitmap) {
        mutableStateOf(IntSize.Zero)
    }

    // Menggunakan state terbaru tanpa membuat
    // gesture drag ter-reset saat titik berpindah.

    val latestPoints = rememberUpdatedState(points)

    val latestAddMode = rememberUpdatedState(isAddingPoint)

    val latestOnPointsChanged =
        rememberUpdatedState(onPointsChanged)

    Box(

        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(350f / 316f)
            .clip(RoundedCornerShape(24.dp))
            .background(RoiPreviewDark)
    ) {

        Canvas(

            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged {
                    canvasSize = it
                }

                // =====================================
                // TAP UNTUK MENAMBAHKAN TITIK
                // =====================================

                .pointerInput(canvasSize) {

                    detectTapGestures { tap ->

                        if (!latestAddMode.value) {
                            return@detectTapGestures
                        }

                        val currentPoints =
                            latestPoints.value

                        if (
                            currentPoints.size >= MAX_ROI_POINTS
                        ) {
                            return@detectTapGestures
                        }

                        val bounds = calculateImageBounds(
                            canvasSize.width.toFloat(),
                            canvasSize.height.toFloat(),
                            bitmap.width,
                            bitmap.height
                        )

                        if (!bounds.contains(tap)) {
                            return@detectTapGestures
                        }

                        val hitRadius = 34.dp.toPx()

                        // Hindari penambahan titik
                        // apabila mengetuk titik yang sudah ada.

                        val hitsExistingPoint =
                            currentPoints.any { point ->

                                val px =
                                    bounds.left +
                                            point.x * bounds.width

                                val py =
                                    bounds.top +
                                            point.y * bounds.height

                                val dx = tap.x - px
                                val dy = tap.y - py

                                dx * dx + dy * dy <
                                        hitRadius * hitRadius
                            }

                        if (hitsExistingPoint) {
                            return@detectTapGestures
                        }

                        val normalized = normalizePoint(
                            tap,
                            bounds
                        )

                        latestOnPointsChanged.value(
                            currentPoints + normalized
                        )
                    }
                }

                // =====================================
                // DRAG UNTUK MENGGESER TITIK
                // =====================================

                .pointerInput(canvasSize) {

                    var draggedIndex: Int? = null

                    detectDragGestures(

                        onDragStart = { start ->

                            val currentPoints =
                                latestPoints.value

                            val bounds = calculateImageBounds(
                                canvasSize.width.toFloat(),
                                canvasSize.height.toFloat(),
                                bitmap.width,
                                bitmap.height
                            )

                            val hitRadius = 36.dp.toPx()

                            val nearestIndex =
                                currentPoints.indices.minByOrNull { index ->

                                    val point =
                                        currentPoints[index]

                                    val px =
                                        bounds.left +
                                                point.x * bounds.width

                                    val py =
                                        bounds.top +
                                                point.y * bounds.height

                                    val dx = start.x - px
                                    val dy = start.y - py

                                    dx * dx + dy * dy
                                }

                            draggedIndex = nearestIndex?.takeIf { index ->

                                val point = currentPoints[index]

                                val px =
                                    bounds.left +
                                            point.x * bounds.width

                                val py =
                                    bounds.top +
                                            point.y * bounds.height

                                val dx = start.x - px
                                val dy = start.y - py

                                dx * dx + dy * dy <=
                                        hitRadius * hitRadius
                            }
                        },

                        onDrag = { change, _ ->

                            val index = draggedIndex
                                ?: return@detectDragGestures

                            change.consume()

                            val bounds = calculateImageBounds(
                                canvasSize.width.toFloat(),
                                canvasSize.height.toFloat(),
                                bitmap.width,
                                bitmap.height
                            )

                            if (!bounds.contains(change.position)) {
                                return@detectDragGestures
                            }

                            val currentPoints =
                                latestPoints.value

                            if (index !in currentPoints.indices) {
                                return@detectDragGestures
                            }

                            val updated = currentPoints.toMutableList()

                            updated[index] = normalizePoint(
                                change.position,
                                bounds
                            )

                            latestOnPointsChanged.value(
                                updated
                            )
                        },

                        onDragEnd = {
                            draggedIndex = null
                        },

                        onDragCancel = {
                            draggedIndex = null
                        }
                    )
                }

        ) {

            val bounds = calculateImageBounds(
                size.width,
                size.height,
                bitmap.width,
                bitmap.height
            )

            // =====================================
            // GAMBAR FOTO
            // =====================================

            drawImage(
                image = imageBitmap,

                dstOffset = IntOffset(
                    bounds.left.roundToInt(),
                    bounds.top.roundToInt()
                ),

                dstSize = IntSize(
                    bounds.width.roundToInt(),
                    bounds.height.roundToInt()
                ),

                filterQuality = FilterQuality.Medium
            )

            // =====================================
            // KONVERSI TITIK ROI
            // =====================================

            val pixelPoints = points.map { point ->

                Offset(
                    x = bounds.left +
                            point.x * bounds.width,

                    y = bounds.top +
                            point.y * bounds.height
                )
            }

            // =====================================
            // GAMBAR POLYGON
            // =====================================

            if (pixelPoints.size >= 2) {

                val path = Path().apply {

                    moveTo(
                        pixelPoints.first().x,
                        pixelPoints.first().y
                    )

                    pixelPoints.drop(1).forEach { point ->

                        lineTo(
                            point.x,
                            point.y
                        )
                    }

                    if (pixelPoints.size >= 3) {
                        close()
                    }
                }

                if (pixelPoints.size >= 3) {

                    drawPath(
                        path = path,
                        color = Color.White.copy(alpha = 0.12f)
                    )
                }

                drawPath(

                    path = path,

                    color = Color.White,

                    style = Stroke(

                        width = 2.dp.toPx(),

                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(
                                9.dp.toPx(),
                                6.dp.toPx()
                            )
                        )
                    )
                )
            }

            // =====================================
            // GAMBAR TITIK MERAH
            // =====================================

            pixelPoints.forEach { point ->

                drawCircle(
                    color = PrimaryRed,
                    radius = 10.dp.toPx(),
                    center = point
                )

                drawCircle(
                    color = Color.White,
                    radius = 10.dp.toPx(),
                    center = point,
                    style = Stroke(
                        width = 2.dp.toPx()
                    )
                )
            }
        }

        // LABEL DI ATAS FOTO

        Surface(

            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),

            color = Color.Black.copy(alpha = 0.60f),

            shape = CircleShape

        ) {

            Text(

                text = "Seret titik untuk mengatur",

                modifier = Modifier.padding(
                    horizontal = 10.dp,
                    vertical = 6.dp
                ),

                color = SurfaceWhite,

                fontSize = 11.sp,

                fontWeight = FontWeight.SemiBold
            )
        }
    }
}


// =====================================================
// MENGHITUNG POSISI FOTO DI CANVAS
// =====================================================

private fun calculateImageBounds(
    canvasWidth: Float,
    canvasHeight: Float,
    imageWidth: Int,
    imageHeight: Int
): Rect {

    if (
        canvasWidth <= 0f ||
        canvasHeight <= 0f ||
        imageWidth <= 0 ||
        imageHeight <= 0
    ) {
        return Rect.Zero
    }

    val scale = min(
        canvasWidth / imageWidth,
        canvasHeight / imageHeight
    )

    val displayedWidth = imageWidth * scale
    val displayedHeight = imageHeight * scale

    val left = (
            canvasWidth - displayedWidth
            ) / 2f

    val top = (
            canvasHeight - displayedHeight
            ) / 2f

    return Rect(
        left = left,
        top = top,
        right = left + displayedWidth,
        bottom = top + displayedHeight
    )
}


// =====================================================
// MENGUBAH POSISI SENTUH MENJADI KOORDINAT 0-1
// =====================================================

private fun normalizePoint(
    point: Offset,
    bounds: Rect
): RoiPoint {

    if (
        bounds.width <= 0f ||
        bounds.height <= 0f
    ) {
        return RoiPoint(0f, 0f)
    }

    val x = (
            (point.x - bounds.left) / bounds.width
            ).coerceIn(0f, 1f)

    val y = (
            (point.y - bounds.top) / bounds.height
            ).coerceIn(0f, 1f)

    return RoiPoint(x, y)
}


// =====================================================
// VALIDASI POLYGON ROI
// =====================================================

private fun isValidPolygon(
    points: List<RoiPoint>
): Boolean {

    if (points.size < 3) {
        return false
    }

    // Minimal tiga titik berbeda.

    val uniquePoints = points.distinctBy {
        Pair(
            (it.x * 100000).roundToInt(),
            (it.y * 100000).roundToInt()
        )
    }

    if (uniquePoints.size < 3) {
        return false
    }

    // Rumus shoelace untuk luas polygon.

    var area = 0f

    for (i in points.indices) {

        val current = points[i]

        val next = points[
            (i + 1) % points.size
        ]

        area += (
                current.x * next.y -
                        next.x * current.y
                )
    }

    area = abs(area) / 2f

    if (area < 0.0001f) {
        return false
    }

    // Polygon tidak boleh berpotongan dengan dirinya sendiri.

    val count = points.size

    for (i in 0 until count) {

        val iNext = (i + 1) % count

        for (j in i + 1 until count) {

            val jNext = (j + 1) % count

            // Abaikan garis yang bersebelahan.

            if (
                i == j ||
                iNext == j ||
                jNext == i
            ) {
                continue
            }

            if (
                segmentsProperlyIntersect(
                    points[i],
                    points[iNext],
                    points[j],
                    points[jNext]
                )
            ) {
                return false
            }
        }
    }

    return true
}


// =====================================================
// VALIDASI PERPOTONGAN DUA GARIS
// =====================================================

private fun segmentsProperlyIntersect(
    a: RoiPoint,
    b: RoiPoint,
    c: RoiPoint,
    d: RoiPoint
): Boolean {

    fun orientation(
        p1: RoiPoint,
        p2: RoiPoint,
        p3: RoiPoint
    ): Float {

        return (
                (p2.x - p1.x) * (p3.y - p1.y) -
                        (p2.y - p1.y) * (p3.x - p1.x)
                )
    }

    val o1 = orientation(a, b, c)
    val o2 = orientation(a, b, d)
    val o3 = orientation(c, d, a)
    val o4 = orientation(c, d, b)

    return (
            (o1 > 0f && o2 < 0f) ||
                    (o1 < 0f && o2 > 0f)
            ) && (
            (o3 > 0f && o4 < 0f) ||
                    (o3 < 0f && o4 > 0f)
            )
}


// =====================================================
// MEMBACA FOTO DARI URI
// =====================================================

private fun decodeImage(
    context: Context,
    uri: Uri
): Bitmap? {

    val boundsOptions = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }

    context.contentResolver.openInputStream(uri)?.use {

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

    val decodeOptions = BitmapFactory.Options().apply {

        inSampleSize = sampleSize

        inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    return context.contentResolver.openInputStream(uri)?.use {

        BitmapFactory.decodeStream(
            it,
            null,
            decodeOptions
        )
    }
}