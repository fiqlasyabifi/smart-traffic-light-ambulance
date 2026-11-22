package com.example.smarttrafficlight.feature.analysis

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.VideoLibrary

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.example.smarttrafficlight.ui.theme.BackgroundLight
import com.example.smarttrafficlight.ui.theme.BorderLight
import com.example.smarttrafficlight.ui.theme.PrimaryRed
import com.example.smarttrafficlight.ui.theme.SurfaceWhite
import com.example.smarttrafficlight.ui.theme.TextPrimary
import com.example.smarttrafficlight.ui.theme.TextSecondary


@Composable
fun SourceSelectionScreen(
    trafficLight: String,
    trafficGroup: String,
    onBack: () -> Unit,
    onCameraClick: () -> Unit,
    onPhotoClick: () -> Unit,
    onWebcamClick: () -> Unit,
    onVideoClick: () -> Unit
) {

    Scaffold(
        containerColor = BackgroundLight
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),

            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // JUDUL HALAMAN

            Text(
                text = "Pilih Sumber Analisis",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Text(
                text = "Pilih sumber gambar atau video untuk melakukan analisis kepadatan kendaraan.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            // INFORMASI JALUR

            Card(
                modifier = Modifier.fillMaxWidth(),

                shape = RoundedCornerShape(16.dp),

                border = BorderStroke(
                    1.dp,
                    BorderLight
                ),

                colors = CardDefaults.cardColors(
                    containerColor = SurfaceWhite
                ),

                elevation = CardDefaults.cardElevation(
                    defaultElevation = 0.dp
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
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    Text(
                        text = "Kelompok: $trafficGroup",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            // KAMERA HP

            SourceOptionCard(
                title = "Kamera HP",
                description = "Ambil gambar kendaraan secara langsung menggunakan kamera handphone.",
                icon = Icons.Outlined.CameraAlt,
                onClick = onCameraClick
            )

            // UNGGAH FOTO

            SourceOptionCard(
                title = "Unggah Foto",
                description = "Pilih foto kendaraan dari galeri perangkat untuk dianalisis.",
                icon = Icons.Outlined.Image,
                onClick = onPhotoClick
            )

            // WEBCAM LAPTOP

            SourceOptionCard(
                title = "Webcam Laptop",
                description = "Gunakan webcam laptop sebagai sumber gambar kendaraan.",
                icon = Icons.Outlined.Laptop,
                onClick = onWebcamClick
            )

            // UNGGAH VIDEO

            SourceOptionCard(
                title = "Unggah Video",
                description = "Pilih video kendaraan dari perangkat untuk dianalisis.",
                icon = Icons.Outlined.VideoLibrary,
                onClick = onVideoClick
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            // INFORMASI TAMBAHAN

            Card(
                modifier = Modifier.fillMaxWidth(),

                shape = RoundedCornerShape(14.dp),

                border = BorderStroke(
                    1.dp,
                    BorderLight
                ),

                colors = CardDefaults.cardColors(
                    containerColor = SurfaceWhite
                ),

                elevation = CardDefaults.cardElevation(
                    defaultElevation = 0.dp
                )
            ) {

                Text(
                    text = "Pilih salah satu sumber analisis. Sistem akan menggunakan data gambar atau video untuk tahap analisis kendaraan.",

                    modifier = Modifier.padding(16.dp),

                    style = MaterialTheme.typography.bodySmall,

                    color = TextSecondary
                )
            }

            // TOMBOL KEMBALI

            Button(
                onClick = onBack,

                modifier = Modifier.fillMaxWidth(),

                shape = RoundedCornerShape(50),

                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryRed,
                    contentColor = SurfaceWhite
                )
            ) {

                Text(
                    text = "Kembali",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}


// =====================================================
// KOMPONEN KARTU PILIHAN SUMBER
// =====================================================

@Composable
private fun SourceOptionCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            },

        shape = RoundedCornerShape(18.dp),

        border = BorderStroke(
            1.dp,
            BorderLight
        ),

        colors = CardDefaults.cardColors(
            containerColor = SurfaceWhite
        ),

        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),

            verticalAlignment = Alignment.CenterVertically,

            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // IKON SUMBER

            Surface(
                shape = RoundedCornerShape(14.dp),

                color = PrimaryRed.copy(
                    alpha = 0.10f
                )
            ) {

                Box(
                    modifier = Modifier.size(58.dp),

                    contentAlignment = Alignment.Center
                ) {

                    Icon(
                        imageVector = icon,

                        contentDescription = title,

                        modifier = Modifier.size(30.dp),

                        tint = PrimaryRed
                    )
                }
            }

            // JUDUL DAN DESKRIPSI

            Column(
                modifier = Modifier.weight(1f),

                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {

                Text(
                    text = title,

                    style = MaterialTheme.typography.titleMedium,

                    fontWeight = FontWeight.Bold,

                    color = TextPrimary
                )

                Text(
                    text = description,

                    style = MaterialTheme.typography.bodySmall,

                    color = TextSecondary
                )
            }
        }
    }
}