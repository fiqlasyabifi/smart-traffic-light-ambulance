package com.example.smarttrafficlight.feature.phase

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smarttrafficlight.ui.theme.BackgroundLight
import com.example.smarttrafficlight.ui.theme.BorderLight
import com.example.smarttrafficlight.ui.theme.PrimaryRed
import com.example.smarttrafficlight.ui.theme.SurfaceSoft
import com.example.smarttrafficlight.ui.theme.SurfaceWhite
import com.example.smarttrafficlight.ui.theme.TextPrimary
import com.example.smarttrafficlight.ui.theme.TextSecondary
import com.example.smarttrafficlight.ui.theme.TrafficGreen
import com.example.smarttrafficlight.ui.theme.TrafficGreenBackground
import com.example.smarttrafficlight.ui.theme.TrafficRed
import com.example.smarttrafficlight.ui.theme.TrafficRedBackground
import com.example.smarttrafficlight.ui.theme.TrafficYellow
import com.example.smarttrafficlight.ui.theme.TrafficYellowBackground

enum class TrafficPhase {
    RED,
    YELLOW,
    GREEN,
    WAITING
}

@Composable
fun PhaseCheckScreen(
    trafficLight: String,
    trafficGroup: String,
    phase: TrafficPhase = TrafficPhase.WAITING,
    onBack: () -> Unit,
    onContinueRed: () -> Unit,
    onContinueGreen: () -> Unit
) {
    val phaseTitle = when (phase) {
        TrafficPhase.RED -> "LAMPU MERAH"
        TrafficPhase.YELLOW -> "LAMPU KUNING"
        TrafficPhase.GREEN -> "LAMPU HIJAU"
        TrafficPhase.WAITING -> "MENUNGGU DATA"
    }

    val phaseDescription = when (phase) {
        TrafficPhase.RED ->
            "Jalur sedang berada pada fase merah. Analisis kendaraan diperlukan sebelum simulasi ambulans."

        TrafficPhase.YELLOW ->
            "Jalur sedang berada pada fase kuning. Tunggu sampai fase berubah dan stabil sebelum melanjutkan."

        TrafficPhase.GREEN ->
            "Jalur sedang berada pada fase hijau. Analisis kendaraan tidak diperlukan. Simulasi ambulans dapat dilanjutkan."

        TrafficPhase.WAITING ->
            "Status fase lampu belum diterima dari sistem. Aplikasi akan menunggu data dari backend dan ESP32."
    }

    val phaseColor = when (phase) {
        TrafficPhase.RED -> TrafficRed
        TrafficPhase.YELLOW -> TrafficYellow
        TrafficPhase.GREEN -> TrafficGreen
        TrafficPhase.WAITING -> TextSecondary
    }

    val phaseBackground = when (phase) {
        TrafficPhase.RED -> TrafficRedBackground
        TrafficPhase.YELLOW -> TrafficYellowBackground
        TrafficPhase.GREEN -> TrafficGreenBackground
        TrafficPhase.WAITING -> SurfaceSoft
    }

    val canContinue = phase == TrafficPhase.RED ||
            phase == TrafficPhase.GREEN

    Scaffold(
        containerColor = BackgroundLight,
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundLight)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Button(
                    onClick = {
                        when (phase) {
                            TrafficPhase.RED -> onContinueRed()
                            TrafficPhase.GREEN -> onContinueGreen()
                            else -> Unit
                        }
                    },
                    enabled = canContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryRed,
                        contentColor = SurfaceWhite,
                        disabledContainerColor = BorderLight,
                        disabledContentColor = TextSecondary
                    )
                ) {
                    Text(
                        text = when (phase) {
                            TrafficPhase.RED -> "Pilih Sumber Analisis"
                            TrafficPhase.GREEN -> "Lanjut Simulasi Ambulans"
                            TrafficPhase.YELLOW -> "Menunggu Fase Stabil"
                            TrafficPhase.WAITING -> "Menunggu Data Lampu"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .widthIn(max = 760.dp)
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {

            // HEADER
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(54.dp),
                    shape = CircleShape,
                    color = SurfaceWhite,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        BorderLight
                    ),
                    onClick = onBack
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "‹",
                            color = TextPrimary,
                            fontSize = 40.sp
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column {
                    Text(
                        text = "Pemeriksaan Fase",
                        color = TextPrimary,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Periksa kondisi jalur ambulans",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }

            // INFORMASI JALUR
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = SurfaceWhite,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    BorderLight
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Jalur yang dipilih",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = trafficLight,
                                color = TextPrimary,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold
                            )

                            Text(
                                text = "Kelompok $trafficGroup",
                                color = TextSecondary,
                                fontSize = 15.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(SurfaceSoft)
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = 10.dp
                                )
                        ) {
                            Text(
                                text = "Jalur ambulans",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // STATUS FASE LAMPU
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = SurfaceWhite,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    BorderLight
                )
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Text(
                        text = "Status fase lampu saat ini",
                        color = TextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(phaseColor)
                        )

                        Text(
                            text = phaseTitle,
                            color = phaseColor,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(phaseBackground)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = phaseDescription,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            lineHeight = 21.sp
                        )
                    }
                }
            }

            // INFORMASI ALUR BERDASARKAN FASE
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = SurfaceWhite,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    BorderLight
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Langkah berikutnya",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )

                    PhaseInstruction(
                        number = "1",
                        title = "Fase merah",
                        description = "Pilih sumber analisis kendaraan melalui HP atau webcam laptop.",
                        selected = phase == TrafficPhase.RED
                    )

                    PhaseInstruction(
                        number = "2",
                        title = "Fase kuning",
                        description = "Tunggu sampai fase berubah dan stabil.",
                        selected = phase == TrafficPhase.YELLOW
                    )

                    PhaseInstruction(
                        number = "3",
                        title = "Fase hijau",
                        description = "Lanjut langsung ke simulasi ambulans tanpa analisis YOLO.",
                        selected = phase == TrafficPhase.GREEN
                    )
                }
            }

            if (phase == TrafficPhase.WAITING) {
                Text(
                    text = "Catatan: status lampu belum terhubung ke Firebase. Tombol lanjut akan aktif setelah data fase diterima.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun PhaseInstruction(
    number: String,
    title: String,
    description: String,
    selected: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) TrafficRedBackground
                else SurfaceWhite
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    if (selected) PrimaryRed
                    else SurfaceSoft
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = if (selected) SurfaceWhite else TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = description,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}