package com.example.smarttrafficlight.data.analysis

import com.example.smarttrafficlight.data.firebase.DensityConfig
import kotlin.math.roundToInt

/** Shared classifier used by webcam, CCTV, photo and video analysis results. */
object DensityClassifier {
    const val LOW = "Rendah"
    const val MEDIUM = "Sedang"
    const val HIGH = "Tinggi"
    const val DISABLED = "Belum diklasifikasikan"

    /** Classify one representative vehicle count (e.g. one uploaded photo). */
    fun classify(vehicleCount: Int, config: DensityConfig): String {
        if (!config.enabled || vehicleCount < 0) return DISABLED

        return when {
            vehicleCount <= config.lowMax -> LOW
            vehicleCount <= config.mediumMax -> MEDIUM
            else -> HIGH
        }
    }

    /** Average sampled-frame counts before classifying a video. */
    fun averageSampledFrameCount(frameCounts: List<Int>): Int? {
        val validCounts = frameCounts.filter { it >= 0 }
        if (validCounts.isEmpty()) return null
        return validCounts.average().roundToInt()
    }

    fun classifyVideo(frameCounts: List<Int>, config: DensityConfig): String {
        val averageCount = averageSampledFrameCount(frameCounts) ?: return DISABLED
        return classify(averageCount, config)
    }

    fun rangeDescription(config: DensityConfig): Triple<String, String, String> {
        return Triple(
            "0–${config.lowMax}",
            "${config.lowMax + 1}–${config.mediumMax}",
            "${config.mediumMax + 1}+"
        )
    }
}
