package com.example.smarttrafficlight.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smarttrafficlight.data.firebase.DensityConfig
import com.example.smarttrafficlight.data.firebase.DensityConfigRepository
import com.example.smarttrafficlight.ui.theme.BackgroundLight
import com.example.smarttrafficlight.ui.theme.BorderLight
import com.example.smarttrafficlight.ui.theme.PrimaryRed
import com.example.smarttrafficlight.ui.theme.SurfaceWhite
import com.example.smarttrafficlight.ui.theme.TextPrimary
import com.example.smarttrafficlight.ui.theme.TextSecondary
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ValueEventListener

@Composable
fun DensitySettingsScreen(
    onBack: () -> Unit
) {
    val repository = remember { DensityConfigRepository() }
    val auth = remember { FirebaseAuth.getInstance() }

    var config by remember { mutableStateOf(DensityConfig.DEFAULT) }
    var lowMaxText by remember { mutableStateOf(DensityConfig.DEFAULT.lowMax.toString()) }
    var mediumMaxText by remember { mutableStateOf(DensityConfig.DEFAULT.mediumMax.toString()) }
    var enabled by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var isAuthenticated by remember { mutableStateOf(auth.currentUser != null) }
    var message by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(repository, auth) {
        var disposed = false
        var databaseListener: ValueEventListener? = null

        fun attachListener() {
            if (disposed || databaseListener != null) return

            databaseListener = repository.addListener(
                onConfig = { loaded ->
                    if (!disposed) {
                        config = loaded
                        lowMaxText = loaded.lowMax.toString()
                        mediumMaxText = loaded.mediumMax.toString()
                        enabled = loaded.enabled
                        isLoading = false
                        errorMessage = null
                    }
                },
                onError = { error ->
                    if (!disposed) {
                        isLoading = false
                        errorMessage = "Gagal membaca konfigurasi Firebase: ${error.message}"
                    }
                }
            )
        }

        val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val signedIn = firebaseAuth.currentUser != null
            isAuthenticated = signedIn
            if (signedIn) attachListener()
        }
        auth.addAuthStateListener(authListener)

        if (auth.currentUser != null) {
            isAuthenticated = true
            attachListener()
        } else {
            auth.signInAnonymously().addOnCompleteListener { task ->
                if (disposed) return@addOnCompleteListener
                if (task.isSuccessful && auth.currentUser != null) {
                    isAuthenticated = true
                    attachListener()
                } else {
                    isLoading = false
                    errorMessage = "Login Firebase gagal: ${task.exception?.localizedMessage ?: "aktifkan Anonymous Authentication"}"
                }
            }
        }

        onDispose {
            disposed = true
            auth.removeAuthStateListener(authListener)
            databaseListener?.let(repository::removeListener)
        }
    }

    val lowMax = lowMaxText.toIntOrNull()
    val mediumMax = mediumMaxText.toIntOrNull()
    val validInput = lowMax != null && mediumMax != null &&
        lowMax >= 0 && mediumMax > lowMax

    val validationMessage = when {
        lowMax == null || mediumMax == null -> "Masukkan angka yang valid pada kedua batas."
        lowMax < 0 -> "Batas Rendah tidak boleh negatif."
        mediumMax <= lowMax -> "Batas Sedang harus lebih besar daripada batas Rendah."
        else -> null
    }

    Scaffold(containerColor = BackgroundLight) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali", tint = TextPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Pengaturan Analisis",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        "Klasifikasi kepadatan kendaraan",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Berlaku untuk semua sumber analisis", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(
                        "Webcam Laptop, CCTV Publik, Upload Foto, dan Upload Video menggunakan batas klasifikasi yang sama.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Aktifkan klasifikasi", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text("Jika nonaktif, hasil akan ditandai belum diklasifikasikan.", color = TextSecondary, fontSize = 11.sp)
                        }
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Batas jumlah kendaraan", fontWeight = FontWeight.Bold, color = TextPrimary)
                    OutlinedTextField(
                        value = lowMaxText,
                        onValueChange = { lowMaxText = it.filter(Char::isDigit) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Maksimum kategori Rendah") },
                        supportingText = { Text("Rendah: 0 sampai batas ini") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = mediumMaxText,
                        onValueChange = { mediumMaxText = it.filter(Char::isDigit) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Maksimum kategori Sedang") },
                        supportingText = { Text("Sedang: setelah batas Rendah sampai batas ini") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    if (validationMessage != null) {
                        Text(validationMessage, color = Color(0xFFB42318), fontSize = 12.sp)
                    }
                }
            }

            val lowDisplay = lowMax ?: config.lowMax
            val mediumDisplay = mediumMax ?: config.mediumMax

            Text("Pratinjau kategori", fontWeight = FontWeight.Bold, color = TextPrimary)
            DensityPreviewCard(
                title = "RENDAH",
                range = "0–$lowDisplay kendaraan",
                color = Color(0xFF17794A),
                background = Color(0xFFE8F6EE)
            )
            DensityPreviewCard(
                title = "SEDANG",
                range = "${lowDisplay + 1}–$mediumDisplay kendaraan",
                color = Color(0xFF9A6700),
                background = Color(0xFFFFF4CC)
            )
            DensityPreviewCard(
                title = "TINGGI",
                range = "${mediumDisplay + 1}+ kendaraan",
                color = Color(0xFFB42318),
                background = Color(0xFFFEE4E2)
            )

            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text("  Mengambil konfigurasi Firebase...", color = TextSecondary, fontSize = 12.sp)
                }
            }
            errorMessage?.let {
                Text(it, color = Color(0xFFB42318), fontSize = 12.sp)
            }
            message?.let {
                Text(it, color = Color(0xFF17794A), fontSize = 12.sp)
            }

            Button(
                onClick = {
                    val low = lowMax ?: return@Button
                    val medium = mediumMax ?: return@Button
                    isSaving = true
                    message = null
                    errorMessage = null
                    repository.save(
                        DensityConfig(
                            enabled = enabled,
                            lowMax = low,
                            mediumMax = medium
                        ),
                        onSuccess = {
                            isSaving = false
                            message = "Pengaturan kepadatan berhasil disimpan. Python akan mengambil perubahan secara berkala."
                        },
                        onFailure = { error ->
                            isSaving = false
                            errorMessage = "Gagal menyimpan konfigurasi: ${error.localizedMessage}"
                        }
                    )
                },
                enabled = isAuthenticated && !isLoading && !isSaving && validInput,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Simpan Pengaturan", fontWeight = FontWeight.Bold)
                }
            }

            Text(
                "Catatan: batas awal 5 dan 15 adalah nilai contoh. Kalibrasikan berdasarkan luas ROI dan hasil pengujian kamera.",
                color = TextSecondary,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DensityPreviewCard(
    title: String,
    range: String,
    color: Color,
    background: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = background
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, color = color, fontWeight = FontWeight.Bold)
            Text(range, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}
