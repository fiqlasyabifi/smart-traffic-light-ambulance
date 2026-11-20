package com.example.smarttrafficlight.feature.setup

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smarttrafficlight.ui.theme.BackgroundLight
import com.example.smarttrafficlight.ui.theme.BorderLight
import com.example.smarttrafficlight.ui.theme.PrimaryRed
import com.example.smarttrafficlight.ui.theme.PrimaryRedDark
import com.example.smarttrafficlight.ui.theme.PrimaryRedLight
import com.example.smarttrafficlight.ui.theme.SurfaceSoft
import com.example.smarttrafficlight.ui.theme.SurfaceWhite
import com.example.smarttrafficlight.ui.theme.TextPrimary
import com.example.smarttrafficlight.ui.theme.TextSecondary

data class SimulationSetup(
    val trafficLight: String = "TL5",
    val trafficGroup: String = "TLB",
    val initialDistance: Int = 150,
    val durationSeconds: Int = 30,
    val markerIntervalSeconds: Float = 1f
)

@Composable
fun SetupScreen(
    onBack: () -> Unit,
    onContinue: (SimulationSetup) -> Unit
) {
    var selectedTrafficLight by remember {
        mutableStateOf("TL5")
    }

    var distanceText by remember {
        mutableStateOf("150")
    }

    var selectedDuration by remember {
        mutableStateOf(30)
    }

    var selectedInterval by remember {
        mutableStateOf(1f)
    }

    val trafficGroup = when (selectedTrafficLight) {
        "TL1", "TL2" -> "TLA"
        "TL3", "TL4" -> "TLC"
        else -> "TLB"
    }

    val initialDistance = distanceText.toIntOrNull()

    val isValidDistance = initialDistance != null &&
            initialDistance in 1..10000

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
                        vertical = 14.dp
                    )
            ) {
                Button(
                    onClick = {
                        onContinue(
                            SimulationSetup(
                                trafficLight = selectedTrafficLight,
                                trafficGroup = trafficGroup,
                                initialDistance = initialDistance!!,
                                durationSeconds = selectedDuration,
                                markerIntervalSeconds = selectedInterval
                            )
                        )
                    },
                    enabled = isValidDistance,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp),
                    shape = RoundedCornerShape(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryRed,
                        contentColor = SurfaceWhite
                    )
                ) {
                    Text(
                        text = "Lanjutkan",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(
                        top = 24.dp,
                        bottom = 24.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(26.dp)
            ) {

                // HEADER
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(SurfaceWhite)
                            .border(
                                width = 1.dp,
                                color = BorderLight,
                                shape = CircleShape
                            )
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        SetupIcon(icon = "back")
                    }

                    Spacer(Modifier.width(22.dp))

                    Text(
                        text = "Setup Simulasi",
                        color = TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // PILIH JALUR
                Column(
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Text(
                        text = "Pilih jalur",
                        color = TextPrimary,
                        fontSize = 36.sp,
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TrafficLightOption(
                                number = "TL1",
                                group = "TLA",
                                selected = selectedTrafficLight == "TL1",
                                onClick = {
                                    selectedTrafficLight = "TL1"
                                },
                                modifier = Modifier.weight(1f)
                            )

                            TrafficLightOption(
                                number = "TL2",
                                group = "TLA",
                                selected = selectedTrafficLight == "TL2",
                                onClick = {
                                    selectedTrafficLight = "TL2"
                                },
                                modifier = Modifier.weight(1f)
                            )

                            TrafficLightOption(
                                number = "TL3",
                                group = "TLC",
                                selected = selectedTrafficLight == "TL3",
                                onClick = {
                                    selectedTrafficLight = "TL3"
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TrafficLightOption(
                                number = "TL4",
                                group = "TLC",
                                selected = selectedTrafficLight == "TL4",
                                onClick = {
                                    selectedTrafficLight = "TL4"
                                },
                                modifier = Modifier.weight(1f)
                            )

                            TrafficLightOption(
                                number = "TL5",
                                group = "TLB",
                                selected = selectedTrafficLight == "TL5",
                                onClick = {
                                    selectedTrafficLight = "TL5"
                                },
                                modifier = Modifier.weight(1f)
                            )

                            TrafficLightOption(
                                number = "TL6",
                                group = "TLB",
                                selected = selectedTrafficLight == "TL6",
                                onClick = {
                                    selectedTrafficLight = "TL6"
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // KARTU PENGATURAN
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = SurfaceWhite,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        BorderLight
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = 24.dp,
                            vertical = 22.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {

                        // JARAK AWAL
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SetupIcon(icon = "distance")

                                Spacer(Modifier.width(12.dp))

                                Text(
                                    text = "Jarak awal",
                                    color = TextSecondary,
                                    fontSize = 18.sp,
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedTextField(
                                    value = distanceText,
                                    onValueChange = { value ->
                                        if (
                                            value.length <= 5 &&
                                            value.all { it.isDigit() }
                                        ) {
                                            distanceText = value
                                        }
                                    },
                                    modifier = Modifier
                                        .width(145.dp)
                                        .height(58.dp),
                                    singleLine = true,
                                    suffix = {
                                        Text(
                                            text = "m",
                                            color = TextSecondary,
                                            fontSize = 16.sp
                                        )
                                    },
                                    textStyle = TextStyle(
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        textAlign = TextAlign.End
                                    ),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number
                                    ),
                                    isError = !isValidDistance
                                )
                            }

                            if (!isValidDistance) {
                                Text(
                                    text = "Masukkan jarak 1 sampai 10.000 meter.",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        HorizontalDivider(
                            color = BorderLight,
                            thickness = 1.dp
                        )

                        // DURASI SIMULASI
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            SettingHeading(
                                icon = "duration",
                                title = "Durasi simulasi"
                            )

                            SegmentedOptions(
                                options = listOf(
                                    15 to "15 dtk",
                                    30 to "30 dtk",
                                    45 to "45 dtk",
                                    60 to "60 dtk"
                                ),
                                selectedValue = selectedDuration,
                                onSelect = {
                                    selectedDuration = it
                                }
                            )
                        }

                        HorizontalDivider(
                            color = BorderLight,
                            thickness = 1.dp
                        )

                        // INTERVAL MARKER
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            SettingHeading(
                                icon = "interval",
                                title = "Interval marker"
                            )

                            SegmentedOptions(
                                options = listOf(
                                    0.5f to "0,5 dtk",
                                    1f to "1 dtk",
                                    2f to "2 dtk"
                                ),
                                selectedValue = selectedInterval,
                                onSelect = {
                                    selectedInterval = it
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// KARTU PILIHAN LAMPU
@Composable
private fun TrafficLightOption(
    number: String,
    group: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(112.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                if (selected) PrimaryRedLight
                else SurfaceWhite
            )
            .border(
                width = if (selected) 2.5.dp else 1.dp,
                color = if (selected) PrimaryRed else BorderLight,
                shape = RoundedCornerShape(28.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = number,
            color = if (selected) PrimaryRedDark else TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = group,
            color = if (selected) PrimaryRedDark else TextSecondary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// JUDUL PENGATURAN DENGAN IKON
@Composable
private fun SettingHeading(
    icon: String,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SetupIcon(icon = icon)

        Text(
            text = title,
            color = TextSecondary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// PILIHAN DURASI DAN INTERVAL
@Composable
private fun <T> SegmentedOptions(
    options: List<Pair<T, String>>,
    selectedValue: T,
    onSelect: (T) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(CircleShape)
            .background(SurfaceSoft)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { (value, label) ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        if (selectedValue == value) PrimaryRed
                        else SurfaceSoft
                    )
                    .clickable {
                        onSelect(value)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (selectedValue == value) {
                        SurfaceWhite
                    } else {
                        TextPrimary
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// IKON DENGAN UKURAN SERAGAM 28 DP
@Composable
private fun SetupIcon(icon: String) {
    Canvas(
        modifier = Modifier.size(28.dp)
    ) {
        val iconColor = if (icon == "back") {
            TextPrimary
        } else {
            TextSecondary
        }

        val stroke = 2.2.dp.toPx()
        val w = size.width
        val h = size.height

        when (icon) {

            // IKON KEMBALI
            "back" -> {
                val path = Path().apply {
                    moveTo(w * 0.68f, h * 0.18f)
                    lineTo(w * 0.34f, h * 0.50f)
                    lineTo(w * 0.68f, h * 0.82f)
                }

                drawPath(
                    path = path,
                    color = iconColor,
                    style = Stroke(
                        width = stroke,
                        cap = StrokeCap.Round
                    )
                )
            }

            // IKON JARAK / PENGGARIS
            "distance" -> {
                drawRoundRect(
                    color = iconColor,
                    topLeft = Offset(
                        w * 0.12f,
                        h * 0.32f
                    ),
                    size = Size(
                        w * 0.76f,
                        h * 0.38f
                    ),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                    style = Stroke(width = stroke)
                )

                for (i in 0..4) {
                    val x = w * (0.25f + i * 0.13f)
                    val tickHeight = if (i % 2 == 0) {
                        0.18f
                    } else {
                        0.11f
                    }

                    drawLine(
                        color = iconColor,
                        start = Offset(x, h * 0.33f),
                        end = Offset(
                            x,
                            h * (0.33f + tickHeight)
                        ),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
            }

            // IKON DURASI / JAM
            "duration" -> {
                drawCircle(
                    color = iconColor,
                    radius = w * 0.34f,
                    center = Offset(
                        w * 0.50f,
                        h * 0.55f
                    ),
                    style = Stroke(width = stroke)
                )

                drawLine(
                    color = iconColor,
                    start = Offset(
                        w * 0.50f,
                        h * 0.08f
                    ),
                    end = Offset(
                        w * 0.50f,
                        h * 0.20f
                    ),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )

                drawLine(
                    color = iconColor,
                    start = Offset(
                        w * 0.50f,
                        h * 0.55f
                    ),
                    end = Offset(
                        w * 0.50f,
                        h * 0.33f
                    ),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )

                drawLine(
                    color = iconColor,
                    start = Offset(
                        w * 0.50f,
                        h * 0.55f
                    ),
                    end = Offset(
                        w * 0.67f,
                        h * 0.65f
                    ),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }

            // IKON INTERVAL / REFRESH
            "interval" -> {
                drawArc(
                    color = iconColor,
                    startAngle = 45f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(
                        w * 0.15f,
                        h * 0.15f
                    ),
                    size = Size(
                        w * 0.70f,
                        h * 0.70f
                    ),
                    style = Stroke(
                        width = stroke,
                        cap = StrokeCap.Round
                    )
                )

                drawLine(
                    color = iconColor,
                    start = Offset(
                        w * 0.74f,
                        h * 0.27f
                    ),
                    end = Offset(
                        w * 0.55f,
                        h * 0.25f
                    ),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )

                drawLine(
                    color = iconColor,
                    start = Offset(
                        w * 0.74f,
                        h * 0.27f
                    ),
                    end = Offset(
                        w * 0.74f,
                        h * 0.45f
                    ),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}