package id.sch.osis.paperless.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.sch.osis.paperless.app.AppController
import id.sch.osis.paperless.app.AppUiState
import id.sch.osis.paperless.ui.components.OsisCard
import id.sch.osis.paperless.ui.theme.OsisColors

private data class QuickDemoAccount(val label: String, val username: String, val roleHint: String)

private val quickAccounts = listOf(
    QuickDemoAccount("Ketua Umum", "ketum", "Pimpinan"),
    QuickDemoAccount("Ketua 1", "ketua1", "Pimpinan"),
    QuickDemoAccount("Sekretaris", "sekretaris", "Pimpinan"),
    QuickDemoAccount("Sekbid 4", "sekbid4", "Peserta"),
    QuickDemoAccount("OSIS Fraksi 1", "fraksi1a", "Peserta"),
    QuickDemoAccount("OSIS Fraksi 3", "fraksi3a", "Peserta"),
    QuickDemoAccount("Ekskul Basket", "basket", "Peserta"),
    QuickDemoAccount("Anggota MPK", "mpk1", "Peserta"),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(ui: AppUiState, controller: AppController) {
    var username by remember { mutableStateOf("ketum") }
    var pin by remember { mutableStateOf("1234") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFE7F0FF), OsisColors.Background, OsisColors.Background),
                ),
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Identitas visual SMA
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(OsisColors.Primary, OsisColors.PrimaryDark))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.AutoStories, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("My SeeHear", style = MaterialTheme.typography.headlineMedium, color = OsisColors.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Rapat Paperless & Mode Fokus OSIS · MPK SMA",
                style = MaterialTheme.typography.bodySmall,
                color = OsisColors.TextSecondary,
            )
            Spacer(Modifier.height(22.dp))

            OsisCard(modifier = Modifier.fillMaxWidth(), padding = 18.dp) {
                // Mode koneksi: Demo (offline 1 perangkat) vs Server Wi-Fi / LAN
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = ui.demoMode,
                        onClick = { controller.setDemoMode(true) },
                        label = { Text("Mode Demo") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OsisColors.PrimarySoft,
                            selectedLabelColor = OsisColors.PrimaryDark,
                        ),
                    )
                    FilterChip(
                        selected = !ui.demoMode,
                        onClick = { controller.setDemoMode(false) },
                        label = { Text("Server Rapat") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OsisColors.PrimarySoft,
                            selectedLabelColor = OsisColors.PrimaryDark,
                        ),
                    )
                }
                Spacer(Modifier.height(10.dp))

                if (!ui.demoMode) {
                    OutlinedTextField(
                        value = ui.serverUrl,
                        onValueChange = controller::setServerUrl,
                        label = { Text("Alamat WebSocket Server") },
                        placeholder = { Text("ws://192.168.1.10:8080/ws") },
                        leadingIcon = { Icon(Icons.Rounded.Wifi, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                } else {
                    Text(
                        "Mode Demo menjalankan server lokal & peserta simulasi langsung di perangkat ini (PIN default: 1234).",
                        style = MaterialTheme.typography.bodySmall,
                        color = OsisColors.TextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    leadingIcon = { Icon(Icons.Rounded.Person, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6) pin = it.filter(Char::isDigit) },
                    label = { Text("PIN (4–6 digit)") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )

                if (ui.loginError != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(ui.loginError, style = MaterialTheme.typography.bodySmall, color = OsisColors.DangerDark)
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { controller.login(username, pin) },
                    enabled = !ui.isLoggingIn && username.isNotBlank() && pin.length >= 4,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OsisColors.PrimaryDark),
                ) {
                    if (ui.isLoggingIn) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Masuk…")
                    } else {
                        Text("Masuk Ruang Rapat", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Pintasan akun demo untuk pengujian cepat peran Pimpinan & Peserta
            OsisCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp) {
                Text("Pintasan Uji Peran (PIN: 1234)", style = MaterialTheme.typography.titleSmall, color = OsisColors.TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Pilih salah satu akun untuk mencoba antarmuka Pimpinan Rapat atau Peserta Rapat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OsisColors.TextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    quickAccounts.forEach { acc ->
                        SuggestionChip(
                            onClick = {
                                username = acc.username
                                pin = "1234"
                                controller.login(acc.username, "1234")
                            },
                            label = { Text("${acc.label} (${acc.roleHint})", style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }
        }
    }
}
