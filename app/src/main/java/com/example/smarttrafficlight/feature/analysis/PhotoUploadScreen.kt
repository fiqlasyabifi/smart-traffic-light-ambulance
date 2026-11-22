package com.example.smarttrafficlight.feature.analysis

import android.content.Intent
import android.net.Uri
import android.widget.ImageView

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.CheckCircle

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext

import com.example.smarttrafficlight.ui.theme.BackgroundLight
import com.example.smarttrafficlight.ui.theme.BorderLight
import com.example.smarttrafficlight.ui.theme.PrimaryRed
import com.example.smarttrafficlight.ui.theme.SurfaceWhite
import com.example.smarttrafficlight.ui.theme.TextPrimary
import com.example.smarttrafficlight.ui.theme.TextSecondary


@Composable
fun PhotoUploadScreen(

    trafficLight: String,
    trafficGroup: String,

    selectedImageUri: Uri?,

    onImageSelected: (Uri) -> Unit,

    onBack: () -> Unit,

    onContinue: () -> Unit

) {

    val context = LocalContext.current

    // =========================================
    // PEMILIH FOTO DARI PERANGKAT
    // =========================================

    val imagePickerLauncher =
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
                    // Akses URI tetap digunakan selama izin tersedia.
                }

                onImageSelected(uri)
            }
        }

    // =========================================
    // TAMPILAN HALAMAN
    // =========================================

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

            // JUDUL

            Text(
                text = "Unggah Foto",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Text(
                text = "Pilih foto kondisi jalan untuk melakukan analisis kepadatan kendaraan.",
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

            // =====================================
            // AREA PREVIEW FOTO
            // =====================================

            Text(
                text = "Pratinjau Foto",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            if (selectedImageUri != null) {

                // FOTO BERHASIL DIPILIH

                Card(

                    modifier = Modifier.fillMaxWidth(),

                    shape = RoundedCornerShape(16.dp),

                    border = BorderStroke(
                        1.dp,
                        BorderLight
                    ),

                    colors = CardDefaults.cardColors(
                        containerColor = SurfaceWhite
                    )

                ) {

                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {

                        AndroidView(

                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(12.dp)),

                            factory = { ctx ->

                                ImageView(ctx).apply {

                                    scaleType =
                                        ImageView.ScaleType.FIT_CENTER

                                    setBackgroundColor(
                                        android.graphics.Color.LTGRAY
                                    )
                                }
                            },

                            update = { imageView ->

                                imageView.setImageURI(
                                    selectedImageUri
                                )
                            }
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {

                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = PrimaryRed
                            )

                            Text(
                                text = "Foto berhasil dipilih",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                    }
                }

            } else {

                // BELUM ADA FOTO

                Card(

                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),

                    shape = RoundedCornerShape(16.dp),

                    border = BorderStroke(
                        1.dp,
                        BorderLight
                    ),

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
                            imageVector = Icons.Outlined.Image,
                            contentDescription = "Pratinjau foto",
                            modifier = Modifier.size(64.dp),
                            tint = PrimaryRed
                        )

                        Spacer(
                            modifier = Modifier.height(12.dp)
                        )

                        Text(
                            text = "Belum ada foto dipilih",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Text(
                            text = "Pilih foto dari galeri perangkat untuk melihat pratinjau.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            // =====================================
            // TOMBOL PILIH FOTO
            // =====================================

            Button(

                onClick = {
                    imagePickerLauncher.launch(
                        arrayOf("image/*")
                    )
                },

                modifier = Modifier.fillMaxWidth(),

                shape = RoundedCornerShape(50),

                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryRed,
                    contentColor = SurfaceWhite
                )

            ) {

                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = null
                )

                Spacer(
                    modifier = Modifier.size(8.dp)
                )

                Text(
                    text = if (selectedImageUri == null) {
                        "Pilih Foto dari Galeri"
                    } else {
                        "Ganti Foto"
                    }
                )
            }

            // =====================================
            // INFORMASI
            // =====================================

            Card(

                modifier = Modifier.fillMaxWidth(),

                shape = RoundedCornerShape(14.dp),

                colors = CardDefaults.cardColors(
                    containerColor = SurfaceWhite
                ),

                border = BorderStroke(
                    1.dp,
                    BorderLight
                )

            ) {

                Text(
                    text = "Gunakan foto yang memperlihatkan kondisi jalan dan kendaraan dengan jelas agar proses analisis dapat dilakukan dengan lebih baik.",

                    modifier = Modifier.padding(16.dp),

                    style = MaterialTheme.typography.bodySmall,

                    color = TextSecondary
                )
            }

            // =====================================
            // TOMBOL LANJUT
            // =====================================

            Button(

                onClick = onContinue,

                enabled = selectedImageUri != null,

                modifier = Modifier.fillMaxWidth(),

                shape = RoundedCornerShape(50),

                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryRed,
                    contentColor = SurfaceWhite,
                    disabledContainerColor = BorderLight,
                    disabledContentColor = TextSecondary
                )

            ) {

                Text("Lanjut ke Editor ROI")
            }

            // KEMBALI

            Button(

                onClick = onBack,

                modifier = Modifier.fillMaxWidth(),

                shape = RoundedCornerShape(50),

                colors = ButtonDefaults.buttonColors(
                    containerColor = SurfaceWhite,
                    contentColor = TextPrimary
                )

            ) {

                Text("Kembali")
            }
        }
    }
}