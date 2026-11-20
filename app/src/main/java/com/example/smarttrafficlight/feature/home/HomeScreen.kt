package com.example.smarttrafficlight.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smarttrafficlight.ui.theme.BackgroundLight
import com.example.smarttrafficlight.ui.theme.BorderLight
import com.example.smarttrafficlight.ui.theme.PrimaryRed
import com.example.smarttrafficlight.ui.theme.PrimaryRedChip
import com.example.smarttrafficlight.ui.theme.PrimaryRedDark
import com.example.smarttrafficlight.ui.theme.SurfaceWhite
import com.example.smarttrafficlight.ui.theme.TextPrimary
import com.example.smarttrafficlight.ui.theme.TextSecondary
import com.example.smarttrafficlight.ui.theme.TrafficGreen
import com.example.smarttrafficlight.ui.theme.TrafficGreenBackground
import com.example.smarttrafficlight.ui.theme.TrafficRed
import com.example.smarttrafficlight.ui.theme.TrafficRedBackground
import com.example.smarttrafficlight.ui.theme.TrafficYellow
import com.example.smarttrafficlight.ui.theme.TrafficYellowBackground

data class TrafficGroupUiState(
    val name: String,
    val lights: String,
    val status: String = "MENUNGGU",
    val remainingSeconds: Int? = null
)

data class HomeUiState(
    val activeGroup: String = "—",
    val activeLights: String = "Menunggu data lampu",
    val activeStatus: String = "MENUNGGU DATA",
    val remainingSeconds: Int? = null,
    val backendStatus: String = "Menunggu",
    val esp32Status: String = "Menunggu",
    val groups: List<TrafficGroupUiState> = listOf(
        TrafficGroupUiState("TLA", "TL1 + TL2"),
        TrafficGroupUiState("TLB", "TL5 + TL6"),
        TrafficGroupUiState("TLC", "TL3 + TL4")
    )
)

@Composable
fun HomeScreen(
    onStartSimulation: () -> Unit,
    onOpenHistory: () -> Unit,
    uiState: HomeUiState = HomeUiState()
) {
    Scaffold(
        containerColor = BackgroundLight,
        bottomBar = {
            HomeBottomNavigation(
                onHomeClick = {},
                onSimulationClick = onStartSimulation,
                onHistoryClick = onOpenHistory
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            HomeHeader()

            ActivePhaseCard(
                activeGroup = uiState.activeGroup,
                activeLights = uiState.activeLights,
                status = uiState.activeStatus,
                remainingSeconds = uiState.remainingSeconds,
                backendStatus = uiState.backendStatus,
                esp32Status = uiState.esp32Status,
                onStartSimulation = onStartSimulation
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Kelompok lampu",
                    color = TextSecondary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium
                )

                TrafficGroupsCard(groups = uiState.groups)
            }
        }
    }
}

@Composable
private fun HomeHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = "Halo, Operator",
                color = TextPrimary,
                fontSize = 25.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Smart Traffic Light Rawamangun",
                color = TextSecondary,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(SurfaceWhite)
                .border(1.dp, BorderLight, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "K12",
                color = PrimaryRedDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ActivePhaseCard(
    activeGroup: String,
    activeLights: String,
    status: String,
    remainingSeconds: Int?,
    backendStatus: String,
    esp32Status: String,
    onStartSimulation: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(PrimaryRed)
            .padding(horizontal = 20.dp, vertical = 21.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (activeGroup == "—") "Fase aktif · Menunggu data"
            else "Fase aktif · $activeGroup ($activeLights)",
            color = SurfaceWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = status,
                color = SurfaceWhite,
                fontSize = if (status.length > 8) 27.sp else 38.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (remainingSeconds != null) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = remainingSeconds.toString(),
                        color = SurfaceWhite,
                        fontSize = 52.sp,
                        lineHeight = 56.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "dtk",
                        color = SurfaceWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 7.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConnectionChip(
                modifier = Modifier.weight(1f),
                iconText = "▤",
                label = "Backend · $backendStatus"
            )
            ConnectionChip(
                modifier = Modifier.weight(1f),
                iconText = "▦",
                label = "ESP32 · $esp32Status"
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clickable(onClick = onStartSimulation),
            shape = CircleShape,
            color = SurfaceWhite
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayOutlineIcon(color = PrimaryRedDark, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Mulai Simulasi Ambulans",
                    color = PrimaryRedDark,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ConnectionChip(
    modifier: Modifier = Modifier,
    iconText: String,
    label: String
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(PrimaryRedChip)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = iconText,
            color = SurfaceWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = SurfaceWhite,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TrafficGroupsCard(groups: List<TrafficGroupUiState>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp)) {
            groups.forEachIndexed { index, group ->
                TrafficGroupRow(group = group)
                if (index != groups.lastIndex) {
                    androidx.compose.material3.HorizontalDivider(
                        color = BorderLight,
                        thickness = 1.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun TrafficGroupRow(group: TrafficGroupUiState) {
    val (accent, pale) = when (group.status.uppercase()) {
        "HIJAU", "GREEN" -> TrafficGreen to TrafficGreenBackground
        "MERAH", "RED" -> TrafficRed to TrafficRedBackground
        "KUNING", "YELLOW" -> TrafficYellow to TrafficYellowBackground
        else -> TextSecondary to BackgroundLight
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(pale),
            contentAlignment = Alignment.Center
        ) {
            TrafficSignalIcon(
                activeStatus = group.status,
                modifier = Modifier.size(22.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = group.name,
                color = TextPrimary,
                fontSize = 19.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = group.lights,
                color = TextSecondary,
                fontSize = 14.sp
            )
        }

        if (group.remainingSeconds != null) {
            Text(
                text = "${group.remainingSeconds} dtk",
                color = TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(pale)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                text = group.status,
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun HomeBottomNavigation(
    onHomeClick: () -> Unit,
    onSimulationClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 10.dp)
            .navigationBarsPadding(),
        shape = CircleShape,
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                modifier = Modifier.weight(1.15f),
                selected = true,
                label = "Beranda",
                icon = "home",
                onClick = onHomeClick
            )
            BottomNavItem(
                modifier = Modifier.weight(1f),
                selected = false,
                label = null,
                icon = "play",
                onClick = onSimulationClick
            )
            BottomNavItem(
                modifier = Modifier.weight(1f),
                selected = false,
                label = null,
                icon = "history",
                onClick = onHistoryClick
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    modifier: Modifier,
    selected: Boolean,
    label: String?,
    icon: String,
    onClick: () -> Unit
) {
    val contentColor = if (selected) SurfaceWhite else TextSecondary

    Row(
        modifier = modifier
            .height(54.dp)
            .clip(CircleShape)
            .background(if (selected) PrimaryRed else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = if (selected) 12.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        when (icon) {
            "home" -> HomeOutlineIcon(contentColor, Modifier.size(22.dp))
            "play" -> PlayOutlineIcon(contentColor, Modifier.size(22.dp))
            else -> HistoryOutlineIcon(contentColor, Modifier.size(22.dp))
        }

        if (selected && label != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TrafficSignalIcon(
    activeStatus: String,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = 1.8.dp.toPx()
        val shellWidth = size.width * 0.58f
        val shellHeight = size.height * 0.88f
        val left = (size.width - shellWidth) / 2f
        val top = (size.height - shellHeight) / 2f

        drawRoundRect(
            color = when (activeStatus.uppercase()) {
                "HIJAU", "GREEN" -> TrafficGreen
                "MERAH", "RED" -> TrafficRed
                "KUNING", "YELLOW" -> TrafficYellow
                else -> TextSecondary
            },
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(shellWidth, shellHeight),
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = Stroke(width = stroke)
        )

        val active = activeStatus.uppercase()
        val colors = listOf(
            if (active == "MERAH" || active == "RED") TrafficRed else TrafficRed.copy(alpha = 0.28f),
            if (active == "KUNING" || active == "YELLOW") TrafficYellow else TrafficYellow.copy(alpha = 0.28f),
            if (active == "HIJAU" || active == "GREEN") TrafficGreen else TrafficGreen.copy(alpha = 0.28f)
        )

        colors.forEachIndexed { index, color ->
            drawCircle(
                color = color,
                radius = shellWidth * 0.19f,
                center = Offset(
                    x = size.width / 2f,
                    y = top + shellHeight * (0.22f + index * 0.28f)
                )
            )
        }
    }
}

@Composable
private fun HomeOutlineIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val p = Path().apply {
            moveTo(size.width * 0.12f, size.height * 0.48f)
            lineTo(size.width * 0.50f, size.height * 0.14f)
            lineTo(size.width * 0.88f, size.height * 0.48f)
        }
        drawPath(p, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        drawLine(
            color = color,
            start = Offset(size.width * 0.22f, size.height * 0.43f),
            end = Offset(size.width * 0.22f, size.height * 0.88f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.78f, size.height * 0.43f),
            end = Offset(size.width * 0.78f, size.height * 0.88f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.22f, size.height * 0.88f),
            end = Offset(size.width * 0.78f, size.height * 0.88f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun PlayOutlineIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val p = Path().apply {
            moveTo(size.width * 0.30f, size.height * 0.16f)
            lineTo(size.width * 0.78f, size.height * 0.50f)
            lineTo(size.width * 0.30f, size.height * 0.84f)
            close()
        }
        drawPath(
            path = p,
            color = color,
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun HistoryOutlineIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        drawCircle(
            color = color,
            radius = size.minDimension * 0.40f,
            style = Stroke(width = 2.dp.toPx())
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.50f, size.height * 0.27f),
            end = Offset(size.width * 0.50f, size.height * 0.51f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.50f, size.height * 0.51f),
            end = Offset(size.width * 0.68f, size.height * 0.62f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}