package com.example.smarttrafficlight.feature.analysis

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.MediaController
import android.widget.VideoView

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.VideoLibrary

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import java.io.File
import java.io.FileOutputStream
import java.util.Locale

import com.example.smarttrafficlight.ui.theme.BackgroundLight
import com.example.smarttrafficlight.ui.theme.BorderLight
import com.example.smarttrafficlight.ui.theme.PrimaryRed
import com.example.smarttrafficlight.ui.theme.SurfaceWhite
import com.example.smarttrafficlight.ui.theme.TextPrimary
import com.example.smarttrafficlight.ui.theme.TextSecondary


// =====================================================
// DATA VIDEO TERPILIH
// =====================================================

data class VideoSelection(

    val videoUri: Uri,

    // Frame yang digunakan untuk menentukan polygon ROI.

    val roiFrameUri: Uri,

    val fileName: String,

    val durationMs: Long,

    val width: Int,

    val height: Int,

    val analysisIntervalSeconds: Int = 5
)


// =====================================================
// VIDEO UPLOAD SCREEN
// =====================================================

@Composable
fun VideoUploadScreen(

    trafficLight: String,

    trafficGroup: String,

    onBack: () -> Unit,

    onContinue: (VideoSelection) -> Unit

) {

    val context = LocalContext.current

    var selectedVideoUri by remember {
        mutableStateOf<Uri?>(null)
    }

    var videoSelection by remember {
        mutableStateOf<VideoSelection?>(null)
    }

    var isLoading by remember {
        mutableStateOf(false)
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    // =================================================
    // PEMILIH VIDEO
    // =================================================

    val videoPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri != null) {

                try {

                    context.contentResolver
                        .takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )

                } catch (_: SecurityException) {
                    // Izin permanen tidak tersedia.
                    // URI tetap digunakan selama akses masih berlaku.
                }

                selectedVideoUri = uri
                videoSelection = null
                errorMessage = null
            }
        }

    // =================================================
    // MEMBACA METADATA DAN MENGAMBIL FRAME VIDEO
    // =================================================

    LaunchedEffect(selectedVideoUri) {

        val uri = selectedVideoUri

        if (uri == null) {
            videoSelection = null
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true
        errorMessage = null

        try {

            videoSelection = withContext(Dispatchers.IO) {

                prepareVideoSelection(
                    context = context,
                    uri = uri
                )
            }

        } catch (error: Exception) {

            videoSelection = null

            errorMessage = error.localizedMessage
                ?: "Gagal membaca video."

        } finally {

            isLoading = false
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
                    vertical = 20.dp
                ),

            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // =========================================
            // HEADER
            // =========================================

            Text(
                text = "Unggah Video",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Text(
                text = "Pilih video kondisi jalan untuk dianalisis berdasarkan interval waktu.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            // =========================================
            // INFORMASI JALUR
            // =========================================

            Card(

                modifier = Modifier.fillMaxWidth(),

                shape = RoundedCornerShape(16.dp),

                border = BorderStroke(1.dp, BorderLight),

                colors = CardDefaults.cardColors(
                    containerColor = SurfaceWhite
                )

            ) {

                Column(

                    modifier = Modifier.padding(16.dp),

                    verticalArrangement = Arrangement.spacedBy(6.dp)

                ) {

                    Text(
                        text = "Informasi Jalur",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Text(
                        text = "Traffic Light: $trafficLight",
                        color = TextSecondary
                    )

                    Text(
                        text = "Kelompok: $trafficGroup",
                        color = TextSecondary
                    )
                }
            }

            // =========================================
            // VIDEO TERPILIH
            // =========================================

            Text(
                text = "Video Terpilih",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            if (selectedVideoUri != null) {

                // PRATINJAU VIDEO

                Card(

                    modifier = Modifier.fillMaxWidth(),

                    shape = RoundedCornerShape(18.dp),

                    colors = CardDefaults.cardColors(
                        containerColor = androidx.compose.ui.graphics.Color(
                            0xFF1F2226
                        )
                    )

                ) {

                    AndroidView(

                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp),

                        factory = { ctx ->

                            VideoView(ctx).apply {

                                tag = selectedVideoUri

                                val controller =
                                    MediaController(ctx)

                                controller.setAnchorView(this)

                                setMediaController(controller)

                                setOnPreparedListener { player ->

                                    player.isLooping = true

                                    start()
                                }

                                setVideoURI(selectedVideoUri)
                            }
                        },

                        update = { videoView ->

                            if (videoView.tag != selectedVideoUri) {

                                videoView.tag = selectedVideoUri

                                videoView.setVideoURI(
                                    selectedVideoUri
                                )
                            }
                        }
                    )
                }

            } else {

                // VIDEO BELUM DIPILIH

                Card(

                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp),

                    shape = RoundedCornerShape(18.dp),

                    border = BorderStroke(1.dp, BorderLight),

                    colors = CardDefaults.cardColors(
                        containerColor = SurfaceWhite
                    )

                ) {

                    Column(

                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),

                        horizontalAlignment = Alignment.CenterHorizontally,

                        verticalArrangement = Arrangement.Center

                    ) {

                        Icon(
                            imageVector = Icons.Outlined.VideoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = PrimaryRed
                        )

                        Spacer(
                            modifier = Modifier.height(12.dp)
                        )

                        Text(
                            text = "Belum ada video dipilih",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Pilih video dari perangkat untuk melihat pratinjau.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // =========================================
            // PROSES MEMBACA VIDEO
            // =========================================

            if (isLoading) {

                Row(

                    modifier = Modifier.fillMaxWidth(),

                    verticalAlignment = Alignment.CenterVertically,

                    horizontalArrangement = Arrangement.spacedBy(10.dp)

                ) {

                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = PrimaryRed,
                        strokeWidth = 2.dp
                    )

                    Text(
                        text = "Membaca informasi video...",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // =========================================
            // ERROR VIDEO
            // =========================================

            if (errorMessage != null) {

                Card(

                    modifier = Modifier.fillMaxWidth(),

                    shape = RoundedCornerShape(14.dp),

                    colors = CardDefaults.cardColors(
                        containerColor = SurfaceWhite
                    ),

                    border = BorderStroke(1.dp, BorderLight)

                ) {

                    Text(
                        text = errorMessage.orEmpty(),
                        modifier = Modifier.padding(16.dp),
                        color = PrimaryRed,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // =========================================
            // INFORMASI BERKAS
            // =========================================

            if (videoSelection != null) {

                val video = videoSelection!!

                Card(

                    modifier = Modifier.fillMaxWidth(),

                    shape = RoundedCornerShape(16.dp),

                    border = BorderStroke(1.dp, BorderLight),

                    colors = CardDefaults.cardColors(
                        containerColor = SurfaceWhite
                    )

                ) {

                    Column(

                        modifier = Modifier.padding(16.dp),

                        verticalArrangement = Arrangement.spacedBy(12.dp)

                    ) {

                        Text(
                            text = "Informasi Video",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        VideoInfoRow(
                            label = "Berkas",
                            value = video.fileName
                        )

                        VideoInfoRow(
                            label = "Durasi",
                            value = formatVideoDuration(
                                video.durationMs
                            )
                        )

                        VideoInfoRow(
                            label = "Resolusi",
                            value = "${video.width} × ${video.height}"
                        )

                        VideoInfoRow(
                            label = "Interval analisis",
                            value = "${video.analysisIntervalSeconds} detik"
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {

                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                                tint = PrimaryRed
                            )

                            Text(
                                text = "Video berhasil dipilih",
                                color = TextPrimary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // =========================================
            // INFORMASI INTERVAL
            // =========================================

            Card(

                modifier = Modifier.fillMaxWidth(),

                shape = RoundedCornerShape(14.dp),

                border = BorderStroke(1.dp, BorderLight),

                colors = CardDefaults.cardColors(
                    containerColor = SurfaceWhite
                )

            ) {

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {

                    Text(
                        text = "Pengaturan Analisis",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Text(
                        text = "Video akan dianalisis setiap 5 detik. " +
                                "Area ROI ditentukan menggunakan frame pratinjau video.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // =========================================
            // TOMBOL PILIH VIDEO
            // =========================================

            Button(

                onClick = {
                    videoPickerLauncher.launch(
                        arrayOf("video/*")
                    )
                },

                modifier = Modifier.fillMaxWidth(),

                shape = CircleShape,

                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryRed,
                    contentColor = SurfaceWhite
                )

            ) {

                Icon(
                    imageVector = Icons.Outlined.FolderOpen,
                    contentDescription = null
                )

                Spacer(
                    modifier = Modifier.size(8.dp)
                )

                Text(
                    text = if (selectedVideoUri == null) {
                        "Pilih Video"
                    } else {
                        "Ganti Video"
                    }
                )
            }

            // =========================================
            // ATUR ROI
            // =========================================

            Button(

                onClick = {

                    videoSelection?.let {
                        onContinue(it)
                    }
                },

                enabled = videoSelection != null && !isLoading,

                modifier = Modifier.fillMaxWidth(),

                shape = CircleShape,

                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryRed,
                    contentColor = SurfaceWhite,
                    disabledContainerColor = BorderLight,
                    disabledContentColor = TextSecondary
                )

            ) {

                Text(
                    text = "Atur ROI",
                    fontWeight = FontWeight.Bold
                )
            }

            // =========================================
            // KEMBALI
            // =========================================

            OutlinedButton(

                onClick = onBack,

                modifier = Modifier.fillMaxWidth(),

                shape = CircleShape,

                border = BorderStroke(1.dp, BorderLight),

                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = SurfaceWhite,
                    contentColor = TextPrimary
                )

            ) {

                Text("Kembali")
            }
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

        verticalAlignment = Alignment.Top

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
// MEMBACA VIDEO DAN MENGEKSTRAK FRAME UNTUK ROI
// =====================================================

private fun prepareVideoSelection(
    context: Context,
    uri: Uri
): VideoSelection {

    val retriever = MediaMetadataRetriever()

    var sourceFrame: Bitmap? = null
    var rotatedFrame: Bitmap? = null

    try {

        retriever.setDataSource(
            context,
            uri
        )

        val durationMs = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_DURATION
        )?.toLongOrNull()
            ?: throw IllegalArgumentException(
                "Durasi video tidak dapat dibaca."
            )

        val originalWidth = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
        )?.toIntOrNull() ?: 0

        val originalHeight = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
        )?.toIntOrNull() ?: 0

        val rotation = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
        )?.toIntOrNull() ?: 0

        // Mengambil frame pada bagian tengah video.

        val timestampUs = if (durationMs > 0L) {
            durationMs * 500L
        } else {
            0L
        }

        sourceFrame = retriever.getFrameAtTime(
            timestampUs,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
        ) ?: throw IllegalArgumentException(
            "Frame pratinjau tidak dapat diambil dari video."
        )

        // Memperbaiki orientasi frame jika diperlukan.

        val normalizedRotation = (
                rotation % 360 + 360
                ) % 360

        val displayFrame = if (
            normalizedRotation == 90 ||
            normalizedRotation == 180 ||
            normalizedRotation == 270
        ) {

            val matrix = Matrix().apply {
                postRotate(normalizedRotation.toFloat())
            }

            Bitmap.createBitmap(
                sourceFrame!!,
                0,
                0,
                sourceFrame!!.width,
                sourceFrame!!.height,
                matrix,
                true
            ).also {
                rotatedFrame = it
            }

        } else {

            sourceFrame!!
        }

        // Menyimpan frame ke cache aplikasi.
        // Frame ini akan dibaca EditorRoiScreen.

        val frameDirectory = File(
            context.cacheDir,
            "video_roi_frames"
        ).apply {
            if (!exists()) {
                mkdirs()
            }
        }

        val frameFile = File(
            frameDirectory,
            "roi_frame_${System.currentTimeMillis()}.jpg"
        )

        FileOutputStream(frameFile).use { stream ->

            val saved = displayFrame.compress(
                Bitmap.CompressFormat.JPEG,
                95,
                stream
            )

            if (!saved) {
                throw IllegalStateException(
                    "Gagal menyimpan frame ROI."
                )
            }
        }

        val fileName = getVideoFileName(
            context,
            uri
        )

        return VideoSelection(

            videoUri = uri,

            roiFrameUri = Uri.fromFile(frameFile),

            fileName = fileName,

            durationMs = durationMs,

            width = displayFrame.width,

            height = displayFrame.height,

            analysisIntervalSeconds = 5
        )

    } finally {

        if (
            rotatedFrame != null &&
            rotatedFrame !== sourceFrame &&
            !rotatedFrame!!.isRecycled
        ) {
            rotatedFrame!!.recycle()
        }

        sourceFrame?.let {
            if (!it.isRecycled) {
                it.recycle()
            }
        }

        retriever.release()
    }
}


// =====================================================
// MEMBACA NAMA FILE
// =====================================================

private fun getVideoFileName(
    context: Context,
    uri: Uri
): String {

    var name: String? = null

    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->

        if (cursor.moveToFirst()) {

            val index = cursor.getColumnIndex(
                OpenableColumns.DISPLAY_NAME
            )

            if (index >= 0) {
                name = cursor.getString(index)
            }
        }
    }

    return name ?: "video_terpilih"
}


// =====================================================
// FORMAT DURASI VIDEO
// =====================================================

private fun formatVideoDuration(
    durationMs: Long
): String {

    val totalSeconds = durationMs / 1000L

    val minutes = totalSeconds / 60L

    val seconds = totalSeconds % 60L

    return String.format(
        Locale.US,
        "%02d:%02d",
        minutes,
        seconds
    )
}