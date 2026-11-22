package com.example.smarttrafficlight.feature.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

import java.io.Closeable
import java.util.Locale

import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min


// =====================================================
// HASIL ANALISIS SATU INTERVAL VIDEO
// =====================================================

data class VideoIntervalResult(

    val intervalStartMs: Long,

    val intervalEndMs: Long,

    val sampleTimeMs: Long,

    val counts: VehicleCounts,

    val totalVehicles: Int,

    val fullImageDetections: Int,

    val inferenceTimeMs: Long
)


// =====================================================
// PROGRESS ANALISIS VIDEO
// =====================================================

data class VideoAnalysisProgress(

    val processedFrames: Int,

    val totalFrames: Int,

    val currentTimeMs: Long,

    val durationMs: Long,

    val intervalStartMs: Long,

    val intervalEndMs: Long
) {

    val fraction: Float
        get() = if (totalFrames <= 0) {
            0f
        } else {
            (processedFrames.toFloat() / totalFrames)
                .coerceIn(0f, 1f)
        }
}


// =====================================================
// RINGKASAN ANALISIS VIDEO
// =====================================================

data class VideoAnalysisSummary(

    val durationMs: Long,

    val intervalSeconds: Int,

    val intervals: List<VideoIntervalResult>,

    val averageVehicles: Double,

    val peakVehicles: Int,

    val peakIntervalStartMs: Long,

    val peakIntervalEndMs: Long,

    val totalInferenceTimeMs: Long,

    // Gambar hasil dari interval dengan jumlah kendaraan tertinggi.
    // Bitmap ini harus dibersihkan oleh pemanggil setelah selesai digunakan.

    val peakPreviewBitmap: Bitmap?
)


// =====================================================
// VIDEO FRAME ANALYZER
// =====================================================

class VideoFrameAnalyzer(
    private val context: Context,
    private val confidenceThreshold: Float = 0.25f,
    private val iouThreshold: Float = 0.45f,
    private val numThreads: Int = 4
) {

    companion object {

        private const val MAX_FRAME_DIMENSION = 1600

        private const val DEFAULT_INTERVAL_SECONDS = 5
    }


    // =================================================
    // METADATA VIDEO
    // =================================================

    private data class VideoMetadata(

        val durationMs: Long,

        val rotationDegrees: Int
    )


    // =================================================
    // FUNGSI UTAMA ANALISIS VIDEO
    // =================================================

    suspend fun analyze(

        videoUri: Uri,

        roi: RoiSelection,

        intervalSeconds: Int = DEFAULT_INTERVAL_SECONDS,

        onProgress: (VideoAnalysisProgress) -> Unit = {}

    ): VideoAnalysisSummary {

        require(intervalSeconds > 0) {
            "Interval analisis harus lebih dari nol."
        }

        require(roi.points.size >= 3) {
            "Polygon ROI minimal memiliki tiga titik."
        }

        require(
            roi.points.all {
                it.x in 0f..1f && it.y in 0f..1f
            }
        ) {
            "Koordinat ROI tidak valid."
        }

        val retriever = MediaMetadataRetriever()

        var detector: YoloDetector? = null

        var peakPreviewBitmap: Bitmap? = null

        var returnedPreview = false

        try {

            // =========================================
            // 1. MEMBACA METADATA VIDEO
            // =========================================

            val metadata = withContext(Dispatchers.IO) {

                retriever.setDataSource(
                    context.applicationContext,
                    videoUri
                )

                val duration = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull()
                    ?: throw IllegalArgumentException(
                        "Durasi video tidak dapat dibaca."
                    )

                val rotation = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
                )?.toIntOrNull() ?: 0

                if (duration <= 0L) {
                    throw IllegalArgumentException(
                        "Durasi video tidak valid."
                    )
                }

                VideoMetadata(
                    durationMs = duration,
                    rotationDegrees = rotation
                )
            }

            // =========================================
            // 2. MENENTUKAN INTERVAL FRAME
            // =========================================

            val intervalMs = intervalSeconds * 1000L

            val totalFrames = (
                    (metadata.durationMs + intervalMs - 1L) /
                            intervalMs
                    ).toInt().coerceAtLeast(1)

            val sampleTimes = (0 until totalFrames).map { index ->

                index.toLong() * intervalMs
            }.filter { time ->

                time < metadata.durationMs
            }

            require(sampleTimes.isNotEmpty()) {
                "Tidak ada frame yang dapat dianalisis."
            }

            // =========================================
            // 3. MEMUAT YOLO SEKALI SAJA
            // =========================================

            detector = withContext(Dispatchers.Default) {

                YoloDetector(

                    context = context.applicationContext,

                    confidenceThreshold = confidenceThreshold,

                    iouThreshold = iouThreshold,

                    numThreads = numThreads
                )
            }

            val activeDetector = detector

                ?: throw IllegalStateException(
                    "Model YOLO gagal dimuat."
                )

            // =========================================
            // 4. MENYIAPKAN HASIL
            // =========================================

            val intervalResults =
                mutableListOf<VideoIntervalResult>()

            var peakVehicles = -1

            var peakStartMs = 0L

            var peakEndMs = 0L

            var totalInferenceTimeMs = 0L

            // =========================================
            // 5. LOOP SETIAP INTERVAL
            // =========================================

            sampleTimes.forEachIndexed { index, sampleStartMs ->

                coroutineContext.ensureActive()

                val sampleEndMs = min(
                    sampleStartMs + intervalMs,
                    metadata.durationMs
                )

                // =====================================
                // A. AMBIL FRAME VIDEO
                // =====================================

                var frameBitmap: Bitmap? =
                    withContext(Dispatchers.IO) {

                        val extractedFrame =
                            retriever.getFrameAtTime(

                                sampleStartMs * 1000L,

                                MediaMetadataRetriever.OPTION_CLOSEST
                            ) ?: throw IllegalStateException(
                                "Gagal mengambil frame pada detik " +
                                        "${sampleStartMs / 1000}."
                            )

                        normalizeVideoFrame(
                            extractedFrame,
                            metadata.rotationDegrees
                        )
                    }

                try {

                    // =================================
                    // B. INFERENSI YOLO
                    // =================================

                    val frame = frameBitmap
                        ?: throw IllegalStateException(
                            "Frame video kosong."
                        )

                    val result = withContext(Dispatchers.Default) {

                        activeDetector.detect(
                            sourceBitmap = frame,
                            roi = roi
                        )
                    }

                    totalInferenceTimeMs += result.inferenceTimeMs

                    // =================================
                    // C. SIMPAN HASIL INTERVAL
                    // =================================

                    val intervalResult = VideoIntervalResult(

                        intervalStartMs = sampleStartMs,

                        intervalEndMs = sampleEndMs,

                        sampleTimeMs = sampleStartMs,

                        counts = result.counts,

                        totalVehicles = result.counts.total,

                        fullImageDetections =
                            result.detections.size,

                        inferenceTimeMs =
                            result.inferenceTimeMs
                    )

                    intervalResults.add(intervalResult)

                    // =================================
                    // D. SIMPAN FRAME PUNCAK
                    // =================================

                    if (result.counts.total > peakVehicles) {

                        peakVehicles = result.counts.total

                        peakStartMs = sampleStartMs

                        peakEndMs = sampleEndMs

                        peakPreviewBitmap?.recycle()

                        peakPreviewBitmap =
                            renderAnnotatedFrame(
                                source = frame,
                                roi = roi,
                                result = result
                            )
                    }

                    // =================================
                    // E. UPDATE PROGRESS
                    // =================================

                    onProgress(

                        VideoAnalysisProgress(

                            processedFrames = index + 1,

                            totalFrames = sampleTimes.size,

                            currentTimeMs = sampleEndMs,

                            durationMs = metadata.durationMs,

                            intervalStartMs = sampleStartMs,

                            intervalEndMs = sampleEndMs
                        )
                    )

                } finally {

                    frameBitmap?.let {

                        if (!it.isRecycled) {
                            it.recycle()
                        }
                    }

                    frameBitmap = null
                }
            }

            // =========================================
            // 6. HITUNG RATA-RATA
            // =========================================

            val averageVehicles = if (
                intervalResults.isNotEmpty()
            ) {

                intervalResults.sumOf {
                    it.totalVehicles.toLong()
                }.toDouble() / intervalResults.size

            } else {

                0.0
            }

            // =========================================
            // 7. BENTUK RINGKASAN
            // =========================================

            val summary = VideoAnalysisSummary(

                durationMs = metadata.durationMs,

                intervalSeconds = intervalSeconds,

                intervals = intervalResults.toList(),

                averageVehicles = averageVehicles,

                peakVehicles = peakVehicles.coerceAtLeast(0),

                peakIntervalStartMs = peakStartMs,

                peakIntervalEndMs = peakEndMs,

                totalInferenceTimeMs = totalInferenceTimeMs,

                peakPreviewBitmap = peakPreviewBitmap
            )

            // Bitmap peak dipindahkan kepemilikannya ke hasil.

            returnedPreview = true

            return summary

        } finally {

            // =========================================
            // 8. BERSIHKAN RESOURCE
            // =========================================

            withContext(Dispatchers.Default) {

                detector?.close()
            }

            withContext(Dispatchers.IO) {

                retriever.release()
            }

            if (!returnedPreview) {

                peakPreviewBitmap?.let {

                    if (!it.isRecycled) {
                        it.recycle()
                    }
                }
            }
        }
    }


    // =================================================
    // NORMALISASI ORIENTASI DAN UKURAN FRAME
    // =================================================

    private fun normalizeVideoFrame(
        source: Bitmap,
        rotationDegrees: Int
    ): Bitmap {

        var current = source

        val rotation = (
                rotationDegrees % 360 + 360
                ) % 360

        // =========================================
        // ROTASI VIDEO
        // =========================================

        if (
            rotation == 90 ||
            rotation == 180 ||
            rotation == 270
        ) {

            val matrix = Matrix().apply {

                postRotate(
                    rotation.toFloat()
                )
            }

            val rotated = Bitmap.createBitmap(

                current,

                0,

                0,

                current.width,

                current.height,

                matrix,

                true
            )

            if (rotated !== current) {
                current.recycle()
            }

            current = rotated
        }

        // =========================================
        // RESIZE MAKSIMAL 1600 PX
        // =========================================

        val largestDimension = max(
            current.width,
            current.height
        )

        if (largestDimension > MAX_FRAME_DIMENSION) {

            val scale =
                MAX_FRAME_DIMENSION.toFloat() /
                        largestDimension

            val newWidth = max(
                1,
                (current.width * scale).toInt()
            )

            val newHeight = max(
                1,
                (current.height * scale).toInt()
            )

            val resized = Bitmap.createScaledBitmap(

                current,

                newWidth,

                newHeight,

                true
            )

            if (resized !== current) {
                current.recycle()
            }

            current = resized
        }

        return current
    }


    // =================================================
    // GAMBAR POLYGON DAN DETEKSI PADA FRAME PUNCAK
    // =================================================

    private fun renderAnnotatedFrame(
        source: Bitmap,
        roi: RoiSelection,
        result: YoloAnalysisResult
    ): Bitmap {

        val output = source.copy(
            Bitmap.Config.ARGB_8888,
            true
        ) ?: throw IllegalStateException(
            "Gagal membuat gambar hasil video."
        )

        val canvas = Canvas(output)

        val imageWidth = output.width.toFloat()
        val imageHeight = output.height.toFloat()

        // =========================================
        // POLYGON ROI
        // =========================================

        val roiPath = Path()

        roi.points.forEachIndexed { index, point ->

            val x = point.x * imageWidth
            val y = point.y * imageHeight

            if (index == 0) {
                roiPath.moveTo(x, y)
            } else {
                roiPath.lineTo(x, y)
            }
        }

        roiPath.close()

        val roiFill = Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {

            color = Color.argb(
                30,
                213,
                43,
                58
            )

            style = Paint.Style.FILL
        }

        canvas.drawPath(
            roiPath,
            roiFill
        )

        val roiStroke = Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {

            color = Color.rgb(
                213,
                43,
                58
            )

            style = Paint.Style.STROKE

            strokeWidth = max(
                3f,
                imageWidth * 0.003f
            )
        }

        canvas.drawPath(
            roiPath,
            roiStroke
        )

        // =========================================
        // TITIK POLYGON
        // =========================================

        val roiPointPaint = Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {

            color = Color.rgb(
                213,
                43,
                58
            )
        }

        roi.points.forEach { point ->

            canvas.drawCircle(

                point.x * imageWidth,

                point.y * imageHeight,

                max(5f, imageWidth * 0.004f),

                roiPointPaint
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
                imageWidth * 0.002f
            )
        }

        val labelPaint = Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {

            color = Color.WHITE

            textSize = max(
                18f,
                imageWidth * 0.018f
            )

            typeface = android.graphics.Typeface.create(
                "sans-serif-medium",
                android.graphics.Typeface.NORMAL
            )
        }

        val labelBackgroundPaint = Paint(
            Paint.ANTI_ALIAS_FLAG
        )

        val bottomCenterPaint = Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {

            color = Color.rgb(
                255,
                210,
                0
            )
        }

        result.detections.forEach { detection ->

            val boxColor = if (
                detection.isInsideRoi
            ) {

                Color.rgb(
                    40,
                    190,
                    40
                )

            } else {

                Color.rgb(
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

            // Bottom Center.

            canvas.drawCircle(

                detection.bottomCenterX,

                detection.bottomCenterY,

                max(
                    5f,
                    imageWidth * 0.004f
                ),

                bottomCenterPaint
            )

            // Label kelas dan confidence.

            val label = String.format(
                Locale.US,
                "%s %.0f%%",
                detection.className,
                detection.confidence * 100f
            )

            val labelWidth =
                labelPaint.measureText(label) + 12f

            val labelHeight =
                labelPaint.textSize + 12f

            val labelLeft = detection.left.coerceIn(
                0f,
                max(0f, imageWidth - labelWidth)
            )

            val labelTop = max(
                0f,
                detection.top - labelHeight
            )

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

                labelPaint
            )
        }

        return output
    }
}