package id.sch.osis.paperless.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.sch.osis.paperless.app.AppController
import id.sch.osis.paperless.app.AppUiState
import id.sch.osis.paperless.core.formatDate
import id.sch.osis.paperless.data.CsvAccountParser
import id.sch.osis.paperless.domain.AccountInput
import id.sch.osis.paperless.domain.OrgCatalog
import id.sch.osis.paperless.domain.OrgCategory
import id.sch.osis.paperless.domain.Permissions
import id.sch.osis.paperless.ui.components.AvatarBadge
import id.sch.osis.paperless.ui.components.OrgBadge
import id.sch.osis.paperless.ui.components.OsisCard
import id.sch.osis.paperless.ui.components.SectionHeader
import id.sch.osis.paperless.ui.components.StatusPill
import id.sch.osis.paperless.ui.theme.OsisColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(ui: AppUiState, controller: AppController) {
    val room = ui.room ?: return
    val me = ui.user ?: return
    val canManage = Permissions.canManageAccounts(me)

    var showAddDialog by remember { mutableStateOf(false) }
    var showCsvDialog by remember { mutableStateOf(false) }
    var csvText by remember { mutableStateOf(CsvAccountParser.TEMPLATE) }

    val revisions = remember(room.messages) { room.messages.filter { it.revision } }
    val myAttendance = remember(room.attendance, me.id) { room.attendance.filter { it.userId == me.id }.sortedByDescending { it.date } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Kartu Identitas Digital
        item(key = "id_card") {
            OsisCard(modifier = Modifier.fillMaxWidth(), containerColor = OsisColors.Surface, borderColor = OsisColors.Primary) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AvatarBadge(me.initials, me.category, size = 54.dp)
                    Column(Modifier.weight(1f)) {
                        Text(me.name, style = MaterialTheme.typography.titleLarge, color = OsisColors.TextPrimary)
                        Text("${me.jabatan} • Kelas ${me.kelas}", style = MaterialTheme.typography.bodyMedium, color = OsisColors.TextSecondary)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OrgBadge(me.category, me.orgLabel)
                            StatusPill("@${me.username}", OsisColors.PrimarySoft, OsisColors.PrimaryDark)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Hak Akses Peran:", style = MaterialTheme.typography.labelLarge, color = OsisColors.TextPrimary)
                Spacer(Modifier.height(4.dp))
                Permissions.describe(me).forEach { desc ->
                    Text("• $desc", style = MaterialTheme.typography.bodySmall, color = OsisColors.TextSecondary)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = controller::logout,
                    enabled = !ui.isLockedForMe,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (ui.isLockedForMe) "Keluar Akun (Terkunci Saat Rapat)" else "Keluar Akun / Ganti Peran")
                }
            }
        }

        // Panel Manajemen Akun Peserta (Ketua Umum & Sekretaris)
        if (canManage) {
            item(key = "manage_hdr") {
                SectionHeader(
                    title = "Manajemen Akun Peserta (${room.participants.size})",
                    subtitle = "Tambah akun, impor CSV, dan atur hak masuk ruang rapat aktif",
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OsisColors.PrimaryDark),
                    ) {
                        Icon(Icons.Rounded.PersonAdd, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tambah Peserta")
                    }
                    FilledTonalButton(
                        onClick = { showCsvDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.Rounded.FileUpload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Impor CSV")
                    }
                }
            }

            items(room.participants, key = { it.id }) { p ->
                OsisCard(modifier = Modifier.fillMaxWidth(), padding = 11.dp) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleSmall)
                            Text("${p.kelas} • ${p.orgLabel} • @${p.username}", style = MaterialTheme.typography.bodySmall, color = OsisColors.TextSecondary)
                        }
                        Switch(
                            checked = p.roomAccess,
                            onCheckedChange = { controller.setRoomAccess(p.id, it) },
                        )
                        IconButton(onClick = { controller.deleteAccount(p.id) }) {
                            Icon(Icons.Rounded.Delete, "Hapus", tint = OsisColors.TextSecondary, modifier = Modifier.size(19.dp))
                        }
                    }
                }
            }
        } else if (!me.isLeader) {
            // Peserta: Rekap Revisi Read-Only & Riwayat Kehadiran
            item(key = "revisions_hdr") {
                SectionHeader(
                    title = "Rekap Poin Revisi Laporan (${revisions.size})",
                    subtitle = "Akses baca-saja terhadap daftar perbaikan yang ditandai Sekretaris",
                )
            }
            if (revisions.isEmpty()) {
                item(key = "no_rev") {
                    OsisCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Belum ada poin revisi yang ditandai pada sesi ini.", style = MaterialTheme.typography.bodySmall, color = OsisColors.TextSecondary)
                    }
                }
            } else {
                items(revisions, key = { "rev_${it.id}" }) { rev ->
                    OsisCard(modifier = Modifier.fillMaxWidth(), padding = 11.dp) {
                        rev.anchor?.let { a ->
                            Text("Hal. ${a.page}: \"${a.quote}\"", style = MaterialTheme.typography.labelLarge, color = OsisColors.PrimaryDark)
                        }
                        if (rev.text.isNotBlank()) Text(rev.text, style = MaterialTheme.typography.bodyMedium)
                        rev.revisionNote?.let { Text("Catatan Sekretaris: $it", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium) }
                    }
                }
            }

            item(key = "att_hdr") {
                SectionHeader("Riwayat Kehadiran Rapat (${myAttendance.size})")
            }
            items(myAttendance, key = { "${it.sessionId}_${it.date}" }) { rec ->
                OsisCard(modifier = Modifier.fillMaxWidth(), padding = 11.dp) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rec.sessionTitle, style = MaterialTheme.typography.titleSmall)
                            Text(formatDate(rec.date), style = MaterialTheme.typography.bodySmall, color = OsisColors.TextSecondary)
                        }
                        StatusPill(
                            rec.status,
                            if (rec.status.startsWith("Hadir")) OsisColors.SuccessSoft else OsisColors.WarningSoft,
                            if (rec.status.startsWith("Hadir")) OsisColors.SuccessDark else OsisColors.WarningDark,
                            icon = Icons.Rounded.VerifiedUser,
                        )
                    }
                }
            }
        }
    }

    // Dialog Tambah Akun Peserta Baru
    if (showAddDialog) {
        AddParticipantDialog(
            onDismiss = { showAddDialog = false },
            onSave = { input -> controller.upsertAccount(input); showAddDialog = false },
        )
    }

    // Dialog Impor Massal CSV
    if (showCsvDialog) {
        AlertDialog(
            onDismissRequest = { showCsvDialog = false },
            title = { Text("Impor Akun Peserta (CSV)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Format: nama,kelas,jabatan,kategori(SEKBID/FRAKSI/EKSKUL/MPK),unit,username,pin",
                        style = MaterialTheme.typography.labelSmall,
                        color = OsisColors.TextSecondary,
                    )
                    OutlinedTextField(
                        value = csvText,
                        onValueChange = { csvText = it },
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(10.dp),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = CsvAccountParser.parse(csvText)
                        if (parsed.accounts.isNotEmpty()) controller.importAccounts(parsed.accounts)
                        showCsvDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OsisColors.PrimaryDark),
                ) { Text("Impor Sekarang") }
            },
            dismissButton = { TextButton(onClick = { showCsvDialog = false }) { Text("Batal") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddParticipantDialog(onDismiss: () -> Unit, onSave: (AccountInput) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kelas by remember { mutableStateOf("XI-1") }
    var jabatan by remember { mutableStateOf("Anggota") }
    var category by remember { mutableStateOf(OrgCategory.SEKBID) }
    var unit by remember { mutableStateOf(OrgCatalog.sekbidUnits.first()) }
    var username by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("1234") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daftarkan Akun Peserta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Lengkap") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = kelas, onValueChange = { kelas = it }, label = { Text("Kelas") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = jabatan, onValueChange = { jabatan = it }, label = { Text("Jabatan") }, singleLine = true, modifier = Modifier.weight(1.5f))
                }
                Text("Kategori Organisasi:", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OrgCatalog.participantCategories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat; unit = OrgCatalog.unitsFor(cat).first() },
                            label = { Text(cat.shortLabel, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = OsisColors.PrimarySoft, selectedLabelColor = OsisColors.PrimaryDark),
                        )
                    }
                }
                OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Unit / Asal Organisasi (mis. Sekbid 3 / Fraksi 1 / Basket)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = username, onValueChange = { username = it.lowercase() }, label = { Text("Username") }, singleLine = true, modifier = Modifier.weight(1.4f))
                    OutlinedTextField(value = pin, onValueChange = { if (it.length <= 6) pin = it.filter(Char::isDigit) }, label = { Text("PIN") }, singleLine = true, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(AccountInput(name, kelas, jabatan, category, unit, username, pin)) },
                enabled = name.isNotBlank() && username.length >= 3 && pin.length >= 4,
                colors = ButtonDefaults.buttonColors(containerColor = OsisColors.PrimaryDark),
            ) { Text("Simpan Akun") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
