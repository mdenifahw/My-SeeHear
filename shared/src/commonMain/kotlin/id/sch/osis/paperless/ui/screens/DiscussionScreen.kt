package id.sch.osis.paperless.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Reply
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.sch.osis.paperless.app.AppController
import id.sch.osis.paperless.app.AppUiState
import id.sch.osis.paperless.app.ChatFilter
import id.sch.osis.paperless.core.formatTime
import id.sch.osis.paperless.domain.ChatMessage
import id.sch.osis.paperless.domain.MessageKind
import id.sch.osis.paperless.domain.Permissions
import id.sch.osis.paperless.domain.UserRole
import id.sch.osis.paperless.ui.components.EmptyPlaceholder
import id.sch.osis.paperless.ui.components.OsisCard
import id.sch.osis.paperless.ui.components.StatusPill
import id.sch.osis.paperless.ui.theme.OsisColors

@Composable
fun DiscussionScreen(ui: AppUiState, controller: AppController) {
    val room = ui.room ?: return
    val me = ui.user ?: return

    var composer by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<ChatMessage?>(null) }
    var revisionTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var revisionNote by remember { mutableStateOf("") }

    val filtered = remember(room.messages, ui.chatFilter) {
        when (ui.chatFilter) {
            ChatFilter.ALL -> room.messages
            ChatFilter.LEADER_HIGHLIGHTS -> room.messages.filter { it.kind == MessageKind.HIGHLIGHT && it.senderRole == UserRole.LEADER }
            ChatFilter.PARTICIPANT_HIGHLIGHTS -> room.messages.filter { it.kind == MessageKind.HIGHLIGHT && it.senderRole == UserRole.PARTICIPANT }
            ChatFilter.REVISIONS -> room.messages.filter { it.revision }
        }
    }
    val revisionCount = remember(room.messages) { room.messages.count { it.revision } }

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter bar + Ekspor Notulensi (Sekretaris / Ketua Umum)
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Ruang Diskusi & Sorotan", style = MaterialTheme.typography.titleMedium)
                if (Permissions.canExport(me)) {
                    FilledTonalButton(
                        onClick = controller::exportRevisions,
                        enabled = !ui.isBusy && revisionCount > 0,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Ekspor Revisi ($revisionCount)", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ChatFilter.entries) { filter ->
                    val label = if (filter == ChatFilter.REVISIONS) "${filter.label} ($revisionCount)" else filter.label
                    FilterChip(
                        selected = ui.chatFilter == filter,
                        onClick = { controller.setChatFilter(filter) },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OsisColors.PrimarySoft,
                            selectedLabelColor = OsisColors.PrimaryDark,
                        ),
                    )
                }
            }
        }

        // Daftar pesan & kartu kutipan highlight
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (filtered.isEmpty()) {
                item {
                    EmptyPlaceholder(
                        icon = Icons.Rounded.Forum,
                        title = "Belum Ada Diskusi",
                        subtitle = "Blok kalimat di halaman Dokumen atau kirim tanggapan di bawah ini.",
                    )
                }
            } else {
                items(filtered, key = { it.id }) { msg ->
                    if (msg.kind == MessageKind.SYSTEM) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            StatusPill(msg.text, OsisColors.PrimarySoft, OsisColors.PrimaryDark)
                        }
                    } else {
                        val replied = msg.replyToId?.let { rid -> room.messages.firstOrNull { it.id == rid } }
                        MessageCard(
                            msg = msg,
                            replied = replied,
                            canMarkRevision = Permissions.canMarkRevision(me),
                            onJump = { msg.anchor?.let(controller::jumpToHighlight) },
                            onReply = { replyTo = msg },
                            onToggleRevision = {
                                if (msg.revision) {
                                    controller.toggleRevision(msg.id, marked = false)
                                } else {
                                    revisionTarget = msg
                                    revisionNote = msg.revisionNote.orEmpty()
                                }
                            },
                        )
                    }
                }
            }
        }

        // Composer pesan
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = OsisColors.Surface,
            border = BorderStroke(1.dp, OsisColors.Border),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                replyTo?.let { target ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(OsisColors.PrimarySoft)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Membalas ${target.senderName}: ${target.text.ifBlank { target.anchor?.quote.orEmpty() }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = OsisColors.PrimaryDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { replyTo = null }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Rounded.Close, null, tint = OsisColors.PrimaryDark, modifier = Modifier.size(14.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = composer,
                        onValueChange = { composer = it },
                        placeholder = { Text("Tulis tanggapan diskusi…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    )
                    FilledIconButton(
                        onClick = {
                            if (composer.isNotBlank()) {
                                controller.sendChat(composer, replyTo?.id)
                                composer = ""
                                replyTo = null
                            }
                        },
                        enabled = composer.isNotBlank(),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = OsisColors.PrimaryDark, contentColor = Color.White),
                    ) { Icon(Icons.AutoMirrored.Rounded.Send, "Kirim") }
                }
            }
        }
    }

    // Dialog Catatan Poin Revisi (Sekretaris / Ketua Umum)
    revisionTarget?.let { msg ->
        AlertDialog(
            onDismissRequest = { revisionTarget = null },
            title = { Text("Tandai Sebagai Poin Revisi") },
            text = {
                OutlinedTextField(
                    value = revisionNote,
                    onValueChange = { revisionNote = it },
                    label = { Text("Catatan notulensi / tindak lanjut (opsional)") },
                    placeholder = { Text("Contoh: Perbaiki rincian dana konsumsi Bab III") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        controller.toggleRevision(msg.id, marked = true, note = revisionNote)
                        revisionTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OsisColors.PrimaryDark),
                ) { Text("Simpan Poin Revisi") }
            },
            dismissButton = { TextButton(onClick = { revisionTarget = null }) { Text("Batal") } },
        )
    }
}

@Composable
private fun MessageCard(
    msg: ChatMessage,
    replied: ChatMessage?,
    canMarkRevision: Boolean,
    onJump: () -> Unit,
    onReply: () -> Unit,
    onToggleRevision: () -> Unit,
) {
    val isLeader = msg.senderRole == UserRole.LEADER
    OsisCard(
        modifier = Modifier.fillMaxWidth(),
        padding = 12.dp,
        borderColor = if (msg.revision) OsisColors.PrimaryDark else OsisColors.Border,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                Text(msg.senderName, style = MaterialTheme.typography.titleSmall, color = OsisColors.TextPrimary)
                StatusPill(
                    text = msg.senderOrg,
                    bgColor = if (isLeader) OsisColors.PrimarySoft else Color(0xFFF1F5F9),
                    fgColor = if (isLeader) OsisColors.PrimaryDark else OsisColors.TextSecondary,
                )
            }
            Text(formatTime(msg.createdAt), style = MaterialTheme.typography.labelSmall, color = OsisColors.TextSecondary)
        }

        if (replied != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "↩ ${replied.senderName}: ${replied.text.ifBlank { replied.anchor?.quote.orEmpty() }}",
                style = MaterialTheme.typography.labelSmall,
                color = OsisColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Kartu Kutipan Sorotan Dokumen (klik → jump-to-page)
        msg.anchor?.let { anchor ->
            Spacer(Modifier.height(8.dp))
            val bg = if (isLeader) OsisColors.HighlightLeaderBg else OsisColors.HighlightParticipantBg
            val bar = if (isLeader) OsisColors.HighlightLeader else OsisColors.HighlightParticipant
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onJump),
                color = bg,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, bar),
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (isLeader) "Sorotan Pimpinan • Hal. ${anchor.page}" else "Sorotan Peserta • Hal. ${anchor.page}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = OsisColors.TextPrimary,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Buka Halaman", style = MaterialTheme.typography.labelSmall, color = OsisColors.PrimaryDark)
                            Spacer(Modifier.width(2.dp))
                            Icon(Icons.Rounded.OpenInNew, null, tint = OsisColors.PrimaryDark, modifier = Modifier.size(13.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("\"${anchor.quote}\"", style = MaterialTheme.typography.bodyMedium, color = OsisColors.TextPrimary)
                }
            }
        }

        if (msg.text.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(msg.text, style = MaterialTheme.typography.bodyMedium, color = OsisColors.TextPrimary)
        }

        if (msg.revision) {
            Spacer(Modifier.height(6.dp))
            Surface(color = OsisColors.PrimarySoft, shape = RoundedCornerShape(8.dp)) {
                Text(
                    "📌 Poin Revisi" + (msg.revisionNote?.let { ": $it" } ?: "") + (msg.revisionMarkedBy?.let { " (oleh $it)" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = OsisColors.PrimaryDark,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onReply, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                Icon(Icons.Rounded.Reply, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Balas", style = MaterialTheme.typography.labelMedium)
            }
            if (canMarkRevision) {
                TextButton(onClick = onToggleRevision, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                    Icon(if (msg.revision) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (msg.revision) "Hapus Revisi" else "Tandai Revisi", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
