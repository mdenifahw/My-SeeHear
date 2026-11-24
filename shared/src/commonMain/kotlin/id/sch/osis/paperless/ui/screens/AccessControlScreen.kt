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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.sch.osis.paperless.app.AppController
import id.sch.osis.paperless.app.AppUiState
import id.sch.osis.paperless.core.formatCountdown
import id.sch.osis.paperless.core.formatRelative
import id.sch.osis.paperless.core.formatTime
import id.sch.osis.paperless.domain.DeviceLockState
import id.sch.osis.paperless.domain.Permissions
import id.sch.osis.paperless.domain.PresenceStatus
import id.sch.osis.paperless.domain.SessionStatus
import id.sch.osis.paperless.domain.UnlockRequest
import id.sch.osis.paperless.domain.UnlockStatus
import id.sch.osis.paperless.ui.components.EmptyPlaceholder
import id.sch.osis.paperless.ui.components.OsisCard
import id.sch.osis.paperless.ui.components.SectionHeader
import id.sch.osis.paperless.ui.components.StatusPill
import id.sch.osis.paperless.ui.theme.OsisColors

@Composable
fun AccessControlScreen(ui: AppUiState, controller: AppController) {
    val user = ui.user ?: return
    if (user.isLeader) LeaderAccessControl(ui, controller) else ParticipantUnlockCenter(ui, controller)
}

// ───────────────────────────── PIMPINAN RAPAT ─────────────────────────────

@Composable
private fun LeaderAccessControl(ui: AppUiState, controller: AppController) {
    val room = ui.room ?: return
    val me = ui.user ?: return
    val pending = room.unlockRequests.filter { it.status == UnlockStatus.PENDING }
    val active = room.unlockRequests.filter { it.isActiveAt(ui.now) }
    val onlineDevices = room.presence.filter { p -> p.status != PresenceStatus.OFFLINE && room.user(p.userId)?.isLeader == false }

    var rejectTarget by remember { mutableStateOf<UnlockRequest?>(null) }
    var rejectNote by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "pending_hdr") {
            SectionHeader(
                title = "Antrean Permintaan Izin (${pending.size})",
                subtitle = "Setujui (1/3/5 menit) atau tolak permintaan buka aplikasi lain",
            )
        }

        if (pending.isEmpty()) {
            item(key = "no_pending") {
                OsisCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Tidak ada permintaan izin yang menunggu persetujuan.", style = MaterialTheme.typography.bodyMedium, color = OsisColors.TextSecondary)
                }
            }
        } else {
            items(pending, key = { it.id }) { req ->
                OsisCard(modifier = Modifier.fillMaxWidth(), borderColor = OsisColors.Primary) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(req.userName, style = MaterialTheme.typography.titleMedium)
                            Text("${req.userOrg} • Minta ${req.requestedMinutes} menit • ${formatRelative(req.createdAt, ui.now)}", style = MaterialTheme.typography.bodySmall, color = OsisColors.TextSecondary)
                        }
                        StatusPill("Menunggu", OsisColors.WarningSoft, OsisColors.WarningDark)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Alasan: \"${req.reason}\"", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 3, 5).forEach { mins ->
                            Button(
                                onClick = { controller.decideUnlock(req.id, approve = true, minutes = mins) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = OsisColors.SuccessDark),
                            ) { Text("$mins mnt", style = MaterialTheme.typography.labelMedium) }
                        }
                        OutlinedButton(
                            onClick = { rejectTarget = req; rejectNote = "" },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(10.dp),
                        ) { Text("Tolak", color = OsisColors.Danger, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        }

        if (active.isNotEmpty()) {
            item(key = "active_hdr") {
                SectionHeader("Sedang Izin Keluar (${active.size})")
            }
            items(active, key = { "act_${it.id}" }) { req ->
                val rem = ((req.expiresAt ?: 0L) - ui.now).coerceAtLeast(0)
                OsisCard(modifier = Modifier.fillMaxWidth(), containerColor = OsisColors.SuccessSoft.copy(alpha = 0.45f)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(req.userName, style = MaterialTheme.typography.titleSmall)
                            Text("Disetujui ${req.decidedByName} • Sisa ${formatCountdown(rem)}", style = MaterialTheme.typography.bodySmall, color = OsisColors.SuccessDark)
                        }
                        if (Permissions.canVeto(me)) {
                            OutlinedButton(onClick = { controller.revokeUnlock(req.id) }, shape = RoundedCornerShape(10.dp)) {
                                Icon(Icons.Rounded.Gavel, null, tint = OsisColors.Danger, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Veto", color = OsisColors.Danger)
                            }
                        }
                    }
                }
            }
        }

        // Status Perangkat Peserta
        item(key = "devices_hdr") {
            SectionHeader("Perangkat Peserta Aktif (${onlineDevices.size})", "Status platform dan penguncian layar")
        }
        items(onlineDevices, key = { "dev_${it.userId}" }) { p ->
            val u = room.user(p.userId) ?: return@items
            val (bg, fg, icon) = when (p.lockState) {
                DeviceLockState.LOCKED -> Triple(OsisColors.SuccessSoft, OsisColors.SuccessDark, Icons.Rounded.Lock)
                DeviceLockState.TEMP_UNLOCKED -> Triple(OsisColors.WarningSoft, OsisColors.WarningDark, Icons.Rounded.LockOpen)
                DeviceLockState.UNLOCKED -> Triple(OsisColors.PrimarySoft, OsisColors.PrimaryDark, Icons.Rounded.LockOpen)
                DeviceLockState.NOT_SUPPORTED -> Triple(OsisColors.DangerSoft, OsisColors.DangerDark, Icons.Rounded.Security)
            }
            OsisCard(modifier = Modifier.fillMaxWidth(), padding = 11.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Devices, null, tint = OsisColors.PrimaryDark, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(u.name, style = MaterialTheme.typography.titleSmall)
                            Text("${u.orgLabel} • ${p.device.label}", style = MaterialTheme.typography.bodySmall, color = OsisColors.TextSecondary)
                        }
                    }
                    StatusPill(p.lockState.label, bg, fg, icon = icon)
                }
            }
        }

        // Audit Log Keamanan
        item(key = "audit_hdr") {
            SectionHeader("Log Aktivitas Keamanan", "Riwayat izin & percobaan keluar paksa")
        }
        items(room.auditLog.take(30), key = { it.id }) { entry ->
            OsisCard(
                modifier = Modifier.fillMaxWidth(),
                padding = 10.dp,
                containerColor = if (entry.type.isViolation) OsisColors.DangerSoft.copy(alpha = 0.5f) else OsisColors.Surface,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        entry.type.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (entry.type.isViolation) OsisColors.DangerDark else OsisColors.TextPrimary,
                    )
                    Text(formatTime(entry.createdAt), style = MaterialTheme.typography.labelSmall, color = OsisColors.TextSecondary)
                }
                Text(entry.detail, style = MaterialTheme.typography.bodySmall, color = OsisColors.TextSecondary)
            }
        }
    }

    rejectTarget?.let { req ->
        AlertDialog(
            onDismissRequest = { rejectTarget = null },
            title = { Text("Tolak Permintaan ${req.userName}") },
            text = {
                OutlinedTextField(
                    value = rejectNote,
                    onValueChange = { rejectNote = it },
                    label = { Text("Catatan untuk peserta (opsional)") },
                    placeholder = { Text("Mohon tetap fokus mengikuti rapat.") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
            },
            confirmButton = {
                Button(
                    onClick = { controller.decideUnlock(req.id, approve = false, note = rejectNote); rejectTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = OsisColors.Danger),
                ) { Text("Tolak") }
            },
            dismissButton = { TextButton(onClick = { rejectTarget = null }) { Text("Batal") } },
        )
    }
}

// ───────────────────────────── PESERTA RAPAT ─────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParticipantUnlockCenter(ui: AppUiState, controller: AppController) {
    val room = ui.room ?: return
    val me = ui.user ?: return
    val myRequests = room.unlockRequests.filter { it.userId == me.id }.sortedByDescending { it.createdAt }
    val latest = myRequests.firstOrNull()
    val hasPending = latest?.status == UnlockStatus.PENDING
    val activeGrant = ui.activeUnlockGrant

    val presets = listOf("Menghubungi orang tua", "Membuka data laporan pendukung", "Keperluan darurat", "Cek jadwal sekolah")
    var reason by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf(3) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Status izin saat ini
        item(key = "status_card") {
            when {
                activeGrant != null -> {
                    val rem = ((activeGrant.expiresAt ?: 0L) - ui.now).coerceAtLeast(0)
                    OsisCard(modifier = Modifier.fillMaxWidth(), containerColor = OsisColors.SuccessSoft, borderColor = OsisColors.Success) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Timer, null, tint = OsisColors.SuccessDark, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Izin Sementara Disetujui", style = MaterialTheme.typography.titleMedium, color = OsisColors.SuccessDark)
                                Text(
                                    "Sisa waktu ${formatCountdown(rem)} • Disetujui oleh ${activeGrant.decidedByName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OsisColors.SuccessDark,
                                )
                                activeGrant.note?.let { Text("Catatan: $it", style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
                hasPending && latest != null -> {
                    OsisCard(modifier = Modifier.fillMaxWidth(), containerColor = OsisColors.WarningSoft, borderColor = OsisColors.Warning) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Menunggu Persetujuan Pimpinan", style = MaterialTheme.typography.titleMedium, color = OsisColors.WarningDark)
                                Text("${latest.requestedMinutes} menit • \"${latest.reason}\"", style = MaterialTheme.typography.bodySmall, color = OsisColors.WarningDark)
                            }
                            TextButton(onClick = { controller.cancelUnlock(latest.id) }) { Text("Batalkan") }
                        }
                    }
                }
                latest?.status == UnlockStatus.REJECTED -> {
                    OsisCard(modifier = Modifier.fillMaxWidth(), containerColor = OsisColors.DangerSoft, borderColor = OsisColors.Danger) {
                        Text("Permintaan Terakhir Ditolak", style = MaterialTheme.typography.titleSmall, color = OsisColors.DangerDark)
                        Text("Oleh ${latest.decidedByName}: ${latest.note ?: "-"}", style = MaterialTheme.typography.bodySmall, color = OsisColors.DangerDark)
                    }
                }
            }
        }

        // Formulir Pengajuan Izin
        item(key = "form") {
            OsisCard(modifier = Modifier.fillMaxWidth()) {
                Text("Minta Izin Membuka Aplikasi Lain", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (room.session.status == SessionStatus.LIVE)
                        "Jika disetujui Ketua Umum, Ketua 1, atau Sekretaris, kunci terbuka sementara sesuai durasi lalu otomatis terkunci kembali."
                    else "Mode Fokus sedang tidak aktif — Anda dapat menggunakan perangkat dengan bebas saat ini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OsisColors.TextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = reason == preset,
                            onClick = { reason = preset },
                            label = { Text(preset, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OsisColors.PrimarySoft,
                                selectedLabelColor = OsisColors.PrimaryDark,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Alasan keperluan") },
                    placeholder = { Text("Tulis alasan singkat…") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text("Estimasi durasi:", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3, 5).forEach { m ->
                        FilterChip(
                            selected = minutes == m,
                            onClick = { minutes = m },
                            label = { Text("$m mnt") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OsisColors.PrimarySoft,
                                selectedLabelColor = OsisColors.PrimaryDark,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { controller.requestUnlock(reason, minutes); reason = "" },
                    enabled = room.session.status == SessionStatus.LIVE && !hasPending && activeGrant == null && reason.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OsisColors.PrimaryDark),
                ) {
                    Icon(Icons.Rounded.LockOpen, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Kirim Permintaan Izin")
                }
            }
        }

        // Panel Notifikasi Masuk (Baca-Saja saat Mode Fokus)
        item(key = "notif_hdr") {
            SectionHeader(
                title = "Notifikasi Masuk (Pratinjau Aman)",
                subtitle = "Notifikasi tetap masuk saat terkunci, tetapi tidak membuka aplikasi sumber",
                trailing = {
                    if (!ui.notificationPermissionGranted) {
                        TextButton(onClick = { controller.platform.notificationFeed.openPermissionSettings() }) {
                            Text("Aktifkan Akses")
                        }
                    }
                },
            )
        }
        item(key = "notif_actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ui.demoMode) {
                    FilledTonalButton(
                        onClick = {
                            controller.platform.notificationFeed.pushSimulated(
                                "WhatsApp",
                                "Ibu (Keluarga)",
                                "Nanti pulang rapat jam berapa? Ibu jemput di gerbang depan ya.",
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.Rounded.Notifications, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Simulasi Notifikasi")
                    }
                }
                if (ui.notifications.isNotEmpty()) {
                    TextButton(onClick = { controller.platform.notificationFeed.clear() }) { Text("Bersihkan") }
                }
            }
        }
        if (ui.notifications.isEmpty()) {
            item(key = "notif_empty") {
                EmptyPlaceholder(
                    icon = Icons.Rounded.NotificationsOff,
                    title = "Belum Ada Notifikasi Masuk",
                    subtitle = "Notifikasi dari aplikasi lain selama rapat akan muncul sebagai pratinjau aman di sini.",
                )
            }
        } else {
            items(ui.notifications, key = { it.id }) { n ->
                OsisCard(modifier = Modifier.fillMaxWidth(), padding = 11.dp) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(n.appName, style = MaterialTheme.typography.labelLarge, color = OsisColors.PrimaryDark)
                        Text(formatTime(n.receivedAt), style = MaterialTheme.typography.labelSmall, color = OsisColors.TextSecondary)
                    }
                    Text(n.title, style = MaterialTheme.typography.titleSmall)
                    if (n.body.isNotBlank()) {
                        Text(n.body, style = MaterialTheme.typography.bodySmall, color = OsisColors.TextSecondary)
                    }
                }
            }
        }
    }
}
