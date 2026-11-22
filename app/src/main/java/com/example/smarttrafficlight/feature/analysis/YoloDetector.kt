package com.example.smarttrafficlight.feature.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock

import org.tensorflow.lite.Interpreter

import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


// =====================================================
// DATA HASIL DETEKSI
// =====================================================

data class YoloDetection(

    val classId: Int,

    val className: String,

    val confidence: Float,

    // Koordinat bounding box dalam pixel foto asli.

    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,

    // Bottom Center dalam pixel foto asli.

    val bottomCenterX: Float,
    val bottomCenterY: Float,

    // Status apakah Bottom Center berada
    // di dalam polygon ROI.

    val isInsideRoi: Boolean = false
)


// =====================================================
// JUMLAH KENDARAAN
// =====================================================

data class VehicleCounts(

    val car: Int = 0,

    val motorcycle: Int = 0,

    val bus: Int = 0,

    val truck: Int = 0

) {

    val total: Int
        get() = car + motorcycle + bus + truck
}


// =====================================================
// HASIL ANALISIS YOLO
// =====================================================

data class YoloAnalysisResult(

    val imageWidth: Int,

    val imageHeight: Int,

    // Semua hasil deteksi pada foto.

    val detections: List<YoloDetection>,

    // Hanya kendaraan yang Bottom Center-nya
    // berada di dalam polygon ROI.

    val roiDetections: List<YoloDetection>,

    val counts: VehicleCounts,

    val inferenceTimeMs: Long
)


// =====================================================
// YOLOV8N TFLITE DETECTOR
// =====================================================

class YoloDetector(

    context: Context,

    private val confidenceThreshold: Float = 0.25f,

    private val iouThreshold: Float = 0.45f,

    numThreads: Int = 4,

    private val maxDetections: Int = 300

) : Closeable {

    companion object {

        const val MODEL_FILE =
            "yolov8n_traffic_fp32.tflite"

        const val INPUT_SIZE = 960

        private const val INPUT_CHANNELS = 3

        private const val OUTPUT_CHANNELS = 8

        private const val NUM_CLASSES = 4

        private const val MODEL_PADDING_COLOR = 114

        private val CLASS_NAMES = listOf(
            "Mobil",
            "Sepeda Motor",
            "Bus",
            "Truk"
        )
    }


    // =================================================
    // FORMAT TENSOR INPUT
    // =================================================

    private enum class InputLayout {
        NCHW,
        NHWC
    }


    // =================================================
    // FORMAT TENSOR OUTPUT
    // =================================================

    private enum class OutputLayout {
        CHANNEL_FIRST,
        CANDIDATE_FIRST
    }


    // =================================================
    // INFORMASI LETTERBOX
    // =================================================

    private data class LetterboxInfo(

        val bitmap: Bitmap,

        val scale: Float,

        val padX: Int,

        val padY: Int
    )


    // =================================================
    // INTERPRETER DAN BUFFER
    // =================================================

    private val interpreter: Interpreter

    private val inputBuffer: ByteBuffer

    private val outputBuffer: ByteBuffer

    private val inputLayout: InputLayout

    private val outputLayout: OutputLayout

    private val inputWidth: Int

    private val inputHeight: Int

    private val numCandidates: Int

    @Volatile
    private var isClosed = false


    // =================================================
    // INISIALISASI MODEL
    // =================================================

    init {

        require(confidenceThreshold in 0f..1f) {
            "Confidence threshold harus antara 0 dan 1."
        }

        require(iouThreshold in 0f..1f) {
            "IoU threshold harus antara 0 dan 1."
        }

        require(numThreads >= 1) {
            "Jumlah thread minimal 1."
        }

        require(maxDetections >= 1) {
            "Max detections minimal 1."
        }

        // Memuat model dari assets.

        val modelBuffer = loadModelBuffer(
            context.applicationContext
        )

        val options = Interpreter.Options().apply {
            setNumThreads(numThreads)
        }

        interpreter = Interpreter(
            modelBuffer,
            options
        )

        // =====================================
        // MEMERIKSA INPUT TENSOR
        // =====================================

        val inputTensor = interpreter.getInputTensor(0)

        val inputShape = inputTensor.shape()

        require(inputTensor.dataType().name == "FLOAT32") {
            "Model harus menggunakan input Float32."
        }

        require(inputShape.size == 4 && inputShape[0] == 1) {
            "Bentuk input model tidak dikenali: " +
                    inputShape.contentToString()
        }

        inputLayout = when {

            inputShape[1] == 3 &&
                    inputShape[2] == 960 &&
                    inputShape[3] == 960 -> InputLayout.NCHW

            inputShape[1] == 960 &&
                    inputShape[2] == 960 &&
                    inputShape[3] == 3 -> InputLayout.NHWC

            else -> throw IllegalArgumentException(
                "Input model tidak sesuai. " +
                        inputShape.contentToString()
            )
        }

        inputHeight = when (inputLayout) {
            InputLayout.NCHW -> inputShape[2]
            InputLayout.NHWC -> inputShape[1]
        }

        inputWidth = when (inputLayout) {
            InputLayout.NCHW -> inputShape[3]
            InputLayout.NHWC -> inputShape[2]
        }

        // =====================================
        // MEMERIKSA OUTPUT TENSOR
        // =====================================

        val outputTensor = interpreter.getOutputTensor(0)

        val outputShape = outputTensor.shape()

        require(outputTensor.dataType().name == "FLOAT32") {
            "Model harus menghasilkan output Float32."
        }

        require(
            outputShape.size == 3 &&
                    outputShape[0] == 1
        ) {
            "Bentuk output model tidak dikenali: " +
                    outputShape.contentToString()
        }

        outputLayout = when {

            outputShape[1] == OUTPUT_CHANNELS -> {
                OutputLayout.CHANNEL_FIRST
            }

            outputShape[2] == OUTPUT_CHANNELS -> {
                OutputLayout.CANDIDATE_FIRST
            }

            else -> throw IllegalArgumentException(
                "Output model harus memiliki 8 fitur. " +
                        outputShape.contentToString()
            )
        }

        numCandidates = when (outputLayout) {

            OutputLayout.CHANNEL_FIRST ->
                outputShape[2]

            OutputLayout.CANDIDATE_FIRST ->
                outputShape[1]
        }

        require(numCandidates > 0) {
            "Jumlah kandidat deteksi tidak valid."
        }

        // =====================================
        // BUFFER INPUT DAN OUTPUT
        // =====================================

        val inputElements =
            INPUT_CHANNELS * inputWidth * inputHeight

        inputBuffer = ByteBuffer.allocateDirect(
            inputElements * 4
        ).order(ByteOrder.nativeOrder())

        val outputElements =
            OUTPUT_CHANNELS * numCandidates

        outputBuffer = ByteBuffer.allocateDirect(
            outputElements * 4
        ).order(ByteOrder.nativeOrder())
    }


    // =================================================
    // FUNGSI UTAMA DETEKSI
    // =================================================

    @Synchronized
    fun detect(
        sourceBitmap: Bitmap,
        roi: RoiSelection
    ): YoloAnalysisResult {

        check(!isClosed) {
            "YoloDetector sudah ditutup."
        }

        require(
            !sourceBitmap.isRecycled &&
                    sourceBitmap.width > 0 &&
                    sourceBitmap.height > 0
        ) {
            "Foto tidak valid."
        }

        require(isValidRoi(roi)) {
            "Polygon ROI tidak valid."
        }

        val startTime = SystemClock.elapsedRealtime()

        // =====================================
        // 1. PREPROCESSING LETTERBOX
        // =====================================

        val letterbox = createLetterbox(
            sourceBitmap
        )

        try {

            // =================================
            // 2. MENGUBAH FOTO MENJADI INPUT YOLO
            // =================================

            prepareInput(
                letterbox.bitmap
            )

            // =================================
            // 3. MENJALANKAN INFERENSI
            // =================================

            inputBuffer.rewind()
            outputBuffer.clear()

            interpreter.run(
                inputBuffer,
                outputBuffer
            )

            // =================================
            // 4. MEMBACA OUTPUT
            // =================================

            outputBuffer.rewind()

            val rawOutput = FloatArray(
                OUTPUT_CHANNELS * numCandidates
            )

            outputBuffer
                .asFloatBuffer()
                .get(rawOutput)

            // =================================
            // 5. PARSING BOUNDING BOX
            // =================================

            val candidates = parseOutput(
                rawOutput = rawOutput,
                sourceBitmap = sourceBitmap,
                letterbox = letterbox
            )

            // =================================
            // 6. NON MAXIMUM SUPPRESSION
            // =================================

            val detections = applyNms(
                candidates
            )

            // =================================
            // 7. BOTTOM CENTER TERHADAP POLYGON
            // =================================

            val classifiedDetections =
                detections.map { detection ->

                    val inside = isBottomCenterInsideRoi(
                        detection = detection,
                        roi = roi,
                        imageWidth = sourceBitmap.width,
                        imageHeight = sourceBitmap.height
                    )

                    detection.copy(
                        isInsideRoi = inside
                    )
                }

            // =================================
            // 8. FILTER KENDARAAN DI DALAM ROI
            // =================================

            val roiDetections =
                classifiedDetections.filter {
                    it.isInsideRoi
                }

            // =================================
            // 9. HITUNG SETIAP KELAS
            // =================================

            val counts = VehicleCounts(

                car = roiDetections.count {
                    it.classId == 0
                },

                motorcycle = roiDetections.count {
                    it.classId == 1
                },

                bus = roiDetections.count {
                    it.classId == 2
                },

                truck = roiDetections.count {
                    it.classId == 3
                }
            )

            val inferenceTime =
                SystemClock.elapsedRealtime() - startTime

            return YoloAnalysisResult(

                imageWidth = sourceBitmap.width,

                imageHeight = sourceBitmap.height,

                detections = classifiedDetections,

                roiDetections = roiDetections,

                counts = counts,

                inferenceTimeMs = inferenceTime
            )

        } finally {

            // Foto sumber tidak dihapus.
            // Hanya bitmap hasil letterbox yang dibersihkan.

            if (!letterbox.bitmap.isRecycled) {
                letterbox.bitmap.recycle()
            }
        }
    }


    // =================================================
    // MEMUAT MODEL DARI ASSETS
    // =================================================

    private fun loadModelBuffer(
        context: Context
    ): ByteBuffer {

        val modelBytes = context.assets
            .open(MODEL_FILE)
            .use {
                it.readBytes()
            }

        return ByteBuffer.allocateDirect(
            modelBytes.size
        ).order(ByteOrder.nativeOrder()).apply {

            put(modelBytes)

            rewind()
        }
    }


    // =================================================
    // LETTERBOX
    // Mempertahankan rasio foto asli.
    // =================================================

    private fun createLetterbox(
        source: Bitmap
    ): LetterboxInfo {

        val scale = min(

            inputWidth.toFloat() / source.width,

            inputHeight.toFloat() / source.height
        )

        val resizedWidth = (
                source.width * scale
                ).roundToInt().coerceIn(1, inputWidth)

        val resizedHeight = (
                source.height * scale
                ).roundToInt().coerceIn(1, inputHeight)

        val padX = (
                inputWidth - resizedWidth
                ) / 2

        val padY = (
                inputHeight - resizedHeight
                ) / 2

        val result = Bitmap.createBitmap(

            inputWidth,

            inputHeight,

            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(result)

        canvas.drawColor(
            Color.rgb(
                MODEL_PADDING_COLOR,
                MODEL_PADDING_COLOR,
                MODEL_PADDING_COLOR
            )
        )

        val paint = Paint(
            Paint.ANTI_ALIAS_FLAG or
                    Paint.FILTER_BITMAP_FLAG or
                    Paint.DITHER_FLAG
        )

        val destination = Rect(

            padX,

            padY,

            padX + resizedWidth,

            padY + resizedHeight
        )

        canvas.drawBitmap(
            source,
            null,
            destination,
            paint
        )

        return LetterboxInfo(

            bitmap = result,

            scale = scale,

            padX = padX,

            padY = padY
        )
    }


    // =================================================
    // PREPROCESSING RGB FLOAT32
    // =================================================

    private fun prepareInput(
        bitmap: Bitmap
    ) {

        val pixels = IntArray(
            inputWidth * inputHeight
        )

        bitmap.getPixels(
            pixels,
            0,
            inputWidth,
            0,
            0,
            inputWidth,
            inputHeight
        )

        inputBuffer.clear()

        when (inputLayout) {

            // =====================================
            // NCHW: [1, 3, H, W]
            // =====================================

            InputLayout.NCHW -> {

                for (channel in 0 until 3) {

                    val shift = when (channel) {
                        0 -> 16
                        1 -> 8
                        else -> 0
                    }

                    for (pixel in pixels) {

                        val value = (
                                (pixel shr shift) and 0xFF
                                ) / 255.0f

                        inputBuffer.putFloat(value)
                    }
                }
            }

            // =====================================
            // NHWC: [1, H, W, 3]
            // =====================================

            InputLayout.NHWC -> {

                for (pixel in pixels) {

                    val red = (
                            (pixel shr 16) and 0xFF
                            ) / 255.0f

                    val green = (
                            (pixel shr 8) and 0xFF
                            ) / 255.0f

                    val blue = (
                            pixel and 0xFF
                            ) / 255.0f

                    inputBuffer.putFloat(red)
                    inputBuffer.putFloat(green)
                    inputBuffer.putFloat(blue)
                }
            }
        }

        inputBuffer.rewind()
    }


    // =================================================
    // PEMBACA OUTPUT MODEL
    // =================================================

    private fun outputAt(
        output: FloatArray,
        channel: Int,
        candidate: Int
    ): Float {

        return when (outputLayout) {

            OutputLayout.CHANNEL_FIRST -> {

                output[
                    channel * numCandidates + candidate
                ]
            }

            OutputLayout.CANDIDATE_FIRST -> {

                output[
                    candidate * OUTPUT_CHANNELS + channel
                ]
            }
        }
    }


    // =================================================
    // MENENTUKAN SKALA KOORDINAT OUTPUT
    // =================================================

    private fun outputCoordinateScale(
        output: FloatArray
    ): Float {

        var maxCoordinate = 0f

        for (index in 0 until numCandidates) {

            for (channel in 0 until 4) {

                val value = outputAt(
                    output,
                    channel,
                    index
                )

                if (value.isFinite()) {

                    maxCoordinate = max(
                        maxCoordinate,
                        abs(value)
                    )
                }
            }
        }

        // Output model dapat berupa koordinat
        // pixel input atau koordinat ternormalisasi.

        return if (maxCoordinate <= 2f) {
            max(inputWidth, inputHeight).toFloat()
        } else {
            1f
        }
    }


    // =================================================
    // PARSING OUTPUT YOLOV8
    // =================================================

    private fun parseOutput(
        rawOutput: FloatArray,
        sourceBitmap: Bitmap,
        letterbox: LetterboxInfo
    ): List<YoloDetection> {

        val result = mutableListOf<YoloDetection>()

        val imageWidth = sourceBitmap.width.toFloat()

        val imageHeight = sourceBitmap.height.toFloat()

        val coordinateScale = outputCoordinateScale(
            rawOutput
        )

        for (index in 0 until numCandidates) {

            // =====================================
            // 1. MEMILIH KELAS DENGAN SKOR TERTINGGI
            // =====================================

            var bestClass = -1

            var bestConfidence = 0f

            for (classId in 0 until NUM_CLASSES) {

                val confidence = outputAt(
                    rawOutput,
                    4 + classId,
                    index
                )

                if (
                    confidence.isFinite() &&
                    confidence > bestConfidence
                ) {

                    bestConfidence = confidence

                    bestClass = classId
                }
            }

            if (
                bestClass == -1 ||
                bestConfidence < confidenceThreshold
            ) {
                continue
            }

            // =====================================
            // 2. MEMBACA XYWH
            // =====================================

            val centerX =
                outputAt(rawOutput, 0, index) * coordinateScale

            val centerY =
                outputAt(rawOutput, 1, index) * coordinateScale

            val boxWidth =
                outputAt(rawOutput, 2, index) * coordinateScale

            val boxHeight =
                outputAt(rawOutput, 3, index) * coordinateScale

            if (
                !centerX.isFinite() ||
                !centerY.isFinite() ||
                !boxWidth.isFinite() ||
                !boxHeight.isFinite() ||
                boxWidth <= 0f ||
                boxHeight <= 0f
            ) {
                continue
            }

            // =====================================
            // 3. XYWH MENJADI XYXY
            // =====================================

            val modelLeft =
                centerX - boxWidth / 2f

            val modelTop =
                centerY - boxHeight / 2f

            val modelRight =
                centerX + boxWidth / 2f

            val modelBottom =
                centerY + boxHeight / 2f

            // =====================================
            // 4. KEMBALIKAN KE KOORDINAT FOTO ASLI
            // =====================================

            val left = (
                    (modelLeft - letterbox.padX) /
                            letterbox.scale
                    ).coerceIn(0f, imageWidth)

            val top = (
                    (modelTop - letterbox.padY) /
                            letterbox.scale
                    ).coerceIn(0f, imageHeight)

            val right = (
                    (modelRight - letterbox.padX) /
                            letterbox.scale
                    ).coerceIn(0f, imageWidth)

            val bottom = (
                    (modelBottom - letterbox.padY) /
                            letterbox.scale
                    ).coerceIn(0f, imageHeight)

            if (
                right <= left ||
                bottom <= top
            ) {
                continue
            }

            // =====================================
            // 5. BOTTOM CENTER
            // =====================================

            val bottomCenterX =
                (left + right) / 2f

            val bottomCenterY =
                bottom

            result.add(

                YoloDetection(

                    classId = bestClass,

                    className = CLASS_NAMES[bestClass],

                    confidence = bestConfidence,

                    left = left,

                    top = top,

                    right = right,

                    bottom = bottom,

                    bottomCenterX = bottomCenterX,

                    bottomCenterY = bottomCenterY
                )
            )
        }

        return result
    }


    // =================================================
    // NON MAXIMUM SUPPRESSION
    // Diproses terpisah untuk setiap kelas.
    // =================================================

    private fun applyNms(
        candidates: List<YoloDetection>
    ): List<YoloDetection> {

        val finalDetections =
            mutableListOf<YoloDetection>()

        for (classId in 0 until NUM_CLASSES) {

            val remaining = candidates

                .filter {
                    it.classId == classId
                }

                .sortedByDescending {
                    it.confidence
                }

                .toMutableList()

            while (remaining.isNotEmpty()) {

                val best = remaining.removeAt(0)

                finalDetections.add(best)

                if (
                    finalDetections.size >= maxDetections
                ) {

                    return finalDetections
                        .sortedByDescending {
                            it.confidence
                        }
                }

                val iterator = remaining.iterator()

                while (iterator.hasNext()) {

                    val candidate = iterator.next()

                    val iou = calculateIoU(
                        best,
                        candidate
                    )

                    if (iou > iouThreshold) {
                        iterator.remove()
                    }
                }
            }
        }

        return finalDetections

            .sortedByDescending {
                it.confidence
            }

            .take(maxDetections)
    }


    // =================================================
    // INTERSECTION OVER UNION
    // =================================================

    private fun calculateIoU(
        first: YoloDetection,
        second: YoloDetection
    ): Float {

        val intersectionLeft = max(
            first.left,
            second.left
        )

        val intersectionTop = max(
            first.top,
            second.top
        )

        val intersectionRight = min(
            first.right,
            second.right
        )

        val intersectionBottom = min(
            first.bottom,
            second.bottom
        )

        val intersectionWidth = max(
            0f,
            intersectionRight - intersectionLeft
        )

        val intersectionHeight = max(
            0f,
            intersectionBottom - intersectionTop
        )

        val intersectionArea =
            intersectionWidth * intersectionHeight

        val firstArea =
            (first.right - first.left) *
                    (first.bottom - first.top)

        val secondArea =
            (second.right - second.left) *
                    (second.bottom - second.top)

        val unionArea =
            firstArea + secondArea - intersectionArea

        if (unionArea <= 0f) {
            return 0f
        }

        return intersectionArea / unionArea
    }


    // =================================================
    // MEMERIKSA VALIDITAS POLYGON ROI
    // =================================================

    private fun isValidRoi(
        roi: RoiSelection
    ): Boolean {

        if (roi.points.size < 3) {
            return false
        }

        if (
            roi.points.any {
                !it.x.isFinite() ||
                        !it.y.isFinite() ||
                        it.x !in 0f..1f ||
                        it.y !in 0f..1f
            }
        ) {
            return false
        }

        val uniquePoints = roi.points.distinctBy {
            Pair(
                (it.x * 100000).roundToInt(),
                (it.y * 100000).roundToInt()
            )
        }

        if (uniquePoints.size < 3) {
            return false
        }

        return calculatePolygonArea(
            roi.points
        ) > 0.0001f
    }


    // =================================================
    // MENGHITUNG LUAS POLYGON
    // =================================================

    private fun calculatePolygonArea(
        points: List<RoiPoint>
    ): Float {

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

        return abs(area) / 2f
    }


    // =================================================
    // BOTTOM CENTER TERHADAP POLYGON ROI
    // =================================================

    private fun isBottomCenterInsideRoi(
        detection: YoloDetection,
        roi: RoiSelection,
        imageWidth: Int,
        imageHeight: Int
    ): Boolean {

        if (!isValidRoi(roi)) {
            return false
        }

        // Bottom Center dikembalikan
        // ke koordinat relatif foto.

        val pointX =
            detection.bottomCenterX / imageWidth.toFloat()

        val pointY =
            detection.bottomCenterY / imageHeight.toFloat()

        val polygon = roi.points.map { point ->

            floatArrayOf(
                point.x,
                point.y
            )

        }.toTypedArray()

        return isPointInsidePolygon(
            x = pointX,
            y = pointY,
            polygon = polygon
        )
    }


    // =================================================
    // POINT IN POLYGON
    // Ray Casting Algorithm
    // =================================================

    private fun isPointInsidePolygon(
        x: Float,
        y: Float,
        polygon: Array<FloatArray>
    ): Boolean {

        var inside = false

        var j = polygon.lastIndex

        for (i in polygon.indices) {

            val xi = polygon[i][0]
            val yi = polygon[i][1]

            val xj = polygon[j][0]
            val yj = polygon[j][1]

            // Titik yang berada di garis polygon
            // tetap dianggap berada di dalam ROI.

            if (
                isPointOnSegment(
                    px = x,
                    py = y,
                    x1 = xi,
                    y1 = yi,
                    x2 = xj,
                    y2 = yj
                )
            ) {

                return true
            }

            val intersects = (
                    (yi > y) != (yj > y)
                    ) && (
                    x < (
                            (xj - xi) * (y - yi) /
                                    (yj - yi) + xi
                            )
                    )

            if (intersects) {
                inside = !inside
            }

            j = i
        }

        return inside
    }


    // =================================================
    // MEMERIKSA TITIK PADA GARIS POLYGON
    // =================================================

    private fun isPointOnSegment(
        px: Float,
        py: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float
    ): Boolean {

        val cross =
            (px - x1) * (y2 - y1) -
                    (py - y1) * (x2 - x1)

        if (abs(cross) > 0.000001f) {
            return false
        }

        val dot =
            (px - x1) * (x2 - x1) +
                    (py - y1) * (y2 - y1)

        if (dot < 0f) {
            return false
        }

        val lengthSquared =
            (x2 - x1) * (x2 - x1) +
                    (y2 - y1) * (y2 - y1)

        return dot <= lengthSquared
    }


    // =================================================
    // MENUTUP MODEL
    // =================================================

    @Synchronized
    override fun close() {

        if (!isClosed) {

            interpreter.close()

            isClosed = true
        }
    }
}