package com.example.smarttrafficlight.feature.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LampRed = Color(0xFFE53945)
private val LampYellow = Color(0xFFFFB51B)
private val LampGreen = Color(0xFF239B62)

@Composable
fun SplashScreen() {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF991B2E),
                        Color(0xFFD9233D),
                        Color(0xFFB71932)
                    )
                )
            )
    ) {

        // Dekorasi latar belakang
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 65.dp, y = (-65).dp)
                .size(230.dp)
                .background(
                    Color.White.copy(alpha = 0.055f),
                    CircleShape
                )
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-100).dp, y = 70.dp)
                .size(280.dp)
                .background(
                    Color.White.copy(alpha = 0.045f),
                    CircleShape
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(
                    horizontal = 30.dp,
                    vertical = 24.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.weight(0.8f))

            // Logo lampu lalu lintas
            TrafficLightLogo()

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Smart Traffic Light",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Rawamangun · Kelompok 12",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                color = Color.White.copy(alpha = 0.13f),
                shape = RoundedCornerShape(50.dp)
            ) {
                Text(
                    text = "INTELLIGENT TRAFFIC MANAGEMENT SYSTEM",
                    modifier = Modifier.padding(
                        horizontal = 14.dp,
                        vertical = 8.dp
                    ),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.weight(1.1f))

            // Loading aplikasi
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {

                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.28f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Memuat aplikasi",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = "Menyiapkan sistem pemantauan lalu lintas...",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun TrafficLightLogo() {

    Surface(
        modifier = Modifier
            .width(116.dp)
            .height(218.dp),
        shape = RoundedCornerShape(34.dp),
        color = Color.White,
        shadowElevation = 16.dp
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            TrafficLamp(LampRed)

            Spacer(modifier = Modifier.height(12.dp))

            TrafficLamp(LampYellow)

            Spacer(modifier = Modifier.height(12.dp))

            TrafficLamp(LampGreen)
        }
    }
}

@Composable
private fun TrafficLamp(color: Color) {

    Box(
        modifier = Modifier.size(52.dp),
        contentAlignment = Alignment.Center
    ) {

        Box(
            modifier = Modifier
                .size(52.dp)
                .background(
                    color.copy(alpha = 0.15f),
                    CircleShape
                )
        )

        // Lampu polos tanpa tanda plus
        Box(
            modifier = Modifier
                .size(42.dp)
                .shadow(
                    elevation = 5.dp,
                    shape = CircleShape
                )
                .background(
                    color = color,
                    shape = CircleShape
                )
        )
    }
}