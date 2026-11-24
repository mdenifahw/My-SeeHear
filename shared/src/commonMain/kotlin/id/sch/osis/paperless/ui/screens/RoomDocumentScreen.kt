package id.sch.osis.paperless.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.sp
import id.sch.osis.paperless.app.AppController
import id.sch.osis.paperless.app.AppUiState
import id.sch.osis.paperless.domain.BlockKind
import id.sch.osis.paperless.domain.ChatMessage
import id.sch.osis.paperless.domain.DocBlock
import id.sch.osis.paperless.domain.HighlightAnchor
import id.sch.osis.paperless.domain.MessageKind
import id.sch.osis.paperless.domain.Permissions
import id.sch.osis.paperless.domain.SessionStatus
import id.sch.osis.paperless.domain.UserRole
import id.sch.osis.paperless.ui.components.EmptyPlaceholder
import id.sch.osis.paperless.ui.components.FocusModeBanner
import id.sch.osis.paperless.ui.components.OsisCard
import id.sch.osis.paperless.ui.components.StatusPill
import id.sch.osis.paperless.ui.theme.OsisColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoomDocumentScreen(ui: AppUiState, controller: AppController) {
    val room = ui.room ?: return
    val user = ui.user ?: return
    val doc = room.document
    val page = doc?.pages?.getOrNull(ui.viewingPage - 1)
    val pageHighlights = remember(room.messages, ui.viewingPage) {
        room.messages.filter { it.kind == MessageKind.HIGHLIGHT && it.anchor?.page == ui.viewingPage }
    }

    var selectedAnchor by remember { mutableStateOf<HighlightAnchor?>(null) }
    var highlightComment by remember { mutableStateOf("") }
    var showBreakDialog by remember { mutableStateOf(false) }
    var showEndDialog by remember { mutableStateOf(false) }
    var showNewSessionDialog by remember { mutableStateOf(false) }
    var newSessionTitle by remember { mutableStateOf(room.session.title) }

    val listState = rememberLazyListState()

    // Lompat ke blok kalimat tertentu saat datang dari Ruang Diskusi.
    LaunchedEffect(ui.focusedAnchor) {
        val anchor = ui.focusedAnchor ?: return@LaunchedEffect
        val idx = page?.blocks?.indexOfFirst { it.id == anchor.blockId } ?: -1
        if (idx >= 0) {
            listState.animateScrollToItem(idx + 2)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Banner Status Rapat & Mode Fokus
        item(key = "banner") {
            FocusModeBanner(
                sessionStatus = room.session.status,
                isLeader = user.isLeader,
                isLockedForMe = ui.isLockedForMe,
                remainingUnlockMillis = ui.activeUnlockGrant?.expiresAt?.let { (it - ui.now).coerceAtLeast(0) },
                remainingBreakMillis = room.session.breakUntil?.let { (it - ui.now).coerceAtLeast(0) },
            )
        }

        // Kontrol Sesi (Khusus Pimpinan Rapat) atau Info Ringkas (Peserta)
        item(key = "controls") {
            OsisCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(room.session.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Kode Sesi: ${room.session.code}  •  ${doc?.fileName ?: "Belum ada dokumen"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = OsisColors.TextSecondary,
                        )
                    }
                    StatusPill(
                        text = room.session.status.label,
                        bgColor = when (room.session.status) {
                            SessionStatus.LIVE -> OsisColors.DangerSoft
                            SessionStatus.BREAK -> OsisColors.WarningSoft
                            SessionStatus.ENDED -> OsisColors.SuccessSoft
                            SessionStatus.PREPARING -> OsisColors.PrimarySoft
                        },
                        fgColor = when (room.session.status) {
                            SessionStatus.LIVE -> OsisColors.DangerDark
                            SessionStatus.BREAK -> OsisColors.WarningDark
                            SessionStatus.ENDED -> OsisColors.SuccessDark
                            SessionStatus.PREPARING -> OsisColors.PrimaryDark
                        },
                    )
                }

                if (user.isLeader) {
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (Permissions.canUpload(user)) {
                            FilledTonalButton(
                                onClick = controller::pickAndUploadDocument,
                                enabled = !ui.isBusy && room.session.status != SessionStatus.LIVE,
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Icon(Icons.Rounded.UploadFile, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Unggah PDF/Word")
                            }
                        }
                        when (room.session.status) {
                            SessionStatus.PREPARING -> {
                                Button(
                                    onClick = controller::startMeeting,
                                    enabled = doc != null,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = OsisColors.PrimaryDark),
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Mulai Rapat")
                                }
                            }
                            SessionStatus.LIVE -> {
                                OutlinedButton(
                                    onClick = { showBreakDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Icon(Icons.Rounded.Pause, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Jeda / Istirahat")
                                }
                                if (Permissions.canEnd(user)) {
                                    Button(
                                        onClick = { showEndDialog = true },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = OsisColors.Danger),
                                    ) {
                                        Icon(Icons.Rounded.Stop, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Akhiri Rapat")
                                    }
                                }
                            }
                            SessionStatus.BREAK -> {
                                Button(
                                    onClick = controller::resumeMeeting,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = OsisColors.PrimaryDark),
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Lanjutkan Rapat")
                                }
                                if (Permissions.canEnd(user)) {
                                    OutlinedButton(onClick = { showEndDialog = true }, shape = RoundedCornerShape(10.dp)) {
                                        Text("Akhiri", color = OsisColors.Danger)
                                    }
                                }
                            }
                            SessionStatus.ENDED -> {
                                Button(
                                    onClick = { showNewSessionDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = OsisColors.PrimaryDark),
                                ) {
                                    Text("Siapkan Sesi Baru")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Toolbar Penampil Dokumen (Navigasi Halaman, Follow Leader, Zoom)
        if (doc != null && page != null) {
            item(key = "doc_toolbar") {
                OsisCard(modifier = Modifier.fillMaxWidth(), padding = 10.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { controller.changePage(ui.viewingPage - 1) },
                                enabled = ui.viewingPage > 1,
                            ) { Icon(Icons.Rounded.ChevronLeft, "Halaman sebelumnya") }
                            Text(
                                "Hal. ${ui.viewingPage} / ${doc.pageCount}",
                                style = MaterialTheme.typography.titleSmall,
                                color = OsisColors.TextPrimary,
                            )
                            IconButton(
                                onClick = { controller.changePage(ui.viewingPage + 1) },
                                enabled = ui.viewingPage < doc.pageCount,
                            ) { Icon(Icons.Rounded.ChevronRight, "Halaman berikutnya") }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { controller.setZoom(ui.docZoom - 0.1f) }) {
                                Icon(Icons.Rounded.Remove, "Perkecil", modifier = Modifier.size(18.dp))
                            }
                            Text("${(ui.docZoom * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                            IconButton(onClick = { controller.setZoom(ui.docZoom + 0.1f) }) {
                                Icon(Icons.Rounded.Add, "Perbesar", modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    HorizontalDivider(color = OsisColors.Border)
                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        FilterChip(
                            selected = ui.followLeader,
                            onClick = { controller.setFollowLeader(!ui.followLeader) },
                            leadingIcon = { Icon(Icons.Rounded.Sync, null, modifier = Modifier.size(16.dp)) },
                            label = {
                                Text(
                                    if (user.isLeader) "Sinkronkan ke Peserta"
                                    else "Ikuti Pimpinan (Hal. ${room.session.leaderPage})",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OsisColors.PrimarySoft,
                                selectedLabelColor = OsisColors.PrimaryDark,
                                selectedLeadingIconColor = OsisColors.PrimaryDark,
                            ),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Rounded.Highlight, null, tint = OsisColors.TextSecondary, modifier = Modifier.size(15.dp))
                            Text("Ketuk kalimat untuk blok", style = MaterialTheme.typography.labelSmall, color = OsisColors.TextSecondary)
                        }
                    }
                }
            }

            // Kertas Dokumen Interaktif
            items(page.blocks, key = { it.id }) { block ->
                DocumentBlockView(
                    block = block,
                    pageNumber = page.number,
                    zoom = ui.docZoom,
                    highlights = pageHighlights,
                    focusedAnchor = ui.focusedAnchor,
                    onSentenceClick = { sentenceIdx, quote ->
                        selectedAnchor = HighlightAnchor(page.number, block.id, sentenceIdx, sentenceIdx, quote)
                        highlightComment = ""
                        controller.clearFocusedAnchor()
                    },
                )
            }
        } else {
            item(key = "empty_doc") {
                EmptyPlaceholder(
                    icon = Icons.Rounded.Description,
                    title = "Belum Ada Dokumen Laporan",
                    subtitle = if (user.isLeader) "Unggah file PDF atau Microsoft Word (.docx) untuk memulai pembahasan."
                    else "Menunggu Pimpinan Rapat melampirkan laporan.",
                )
            }
        }
    }

    // Dialog Blok Kalimat (Highlight) → Kirim ke Ruang Diskusi
    selectedAnchor?.let { anchor ->
        AlertDialog(
            onDismissRequest = { selectedAnchor = null },
            icon = { Icon(Icons.Rounded.Highlight, null, tint = OsisColors.PrimaryDark) },
            title = { Text("Sorot Kalimat (Hal. ${anchor.page})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = if (user.isLeader) OsisColors.HighlightLeaderBg else OsisColors.HighlightParticipantBg,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (user.isLeader) OsisColors.HighlightLeader else OsisColors.HighlightParticipant,
                        ),
                    ) {
                        Text(
                            "\"${anchor.quote}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                    OutlinedTextField(
                        value = highlightComment,
                        onValueChange = { highlightComment = it },
                        label = { Text("Komentar / Pertanyaan / Usul Revisi") },
                        placeholder = { Text("Opsional — ketik tanggapan Anda…") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        controller.sendHighlight(anchor, highlightComment)
                        selectedAnchor = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OsisColors.PrimaryDark),
                ) { Text("Kirim ke Diskusi") }
            },
            dismissButton = {
                TextButton(onClick = { selectedAnchor = null }) { Text("Batal") }
            },
        )
    }

    // Dialog Jeda / Istirahat (Pimpinan)
    if (showBreakDialog) {
        AlertDialog(
            onDismissRequest = { showBreakDialog = false },
            title = { Text("Jeda / Istirahat Rapat") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Kunci perangkat seluruh peserta akan dibuka sementara selama durasi istirahat, lalu aktif kembali secara otomatis.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15).forEach { m ->
                            FilledTonalButton(
                                onClick = { controller.pauseMeeting(m); showBreakDialog = false },
                                modifier = Modifier.weight(1f),
                            ) { Text("$m mnt") }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showBreakDialog = false }) { Text("Tutup") } },
        )
    }

    // Dialog Konfirmasi Akhiri Rapat
    if (showEndDialog) {
        AlertDialog(
            onDismissRequest = { showEndDialog = false },
            title = { Text("Akhiri Rapat?") },
            text = { Text("Seluruh kunci perangkat peserta akan dilepas dan data kehadiran serta rekap revisi akan disimpan.") },
            confirmButton = {
                Button(
                    onClick = { controller.endMeeting(); showEndDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = OsisColors.Danger),
                ) { Text("Akhiri Rapat") }
            },
            dismissButton = { TextButton(onClick = { showEndDialog = false }) { Text("Batal") } },
        )
    }

    // Dialog Sesi Baru
    if (showNewSessionDialog) {
        AlertDialog(
            onDismissRequest = { showNewSessionDialog = false },
            title = { Text("Siapkan Sesi Rapat Baru") },
            text = {
                OutlinedTextField(
                    value = newSessionTitle,
                    onValueChange = { newSessionTitle = it },
                    label = { Text("Judul Rapat") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
            },
            confirmButton = {
                Button(onClick = { controller.prepareNewSession(newSessionTitle); showNewSessionDialog = false }) {
                    Text("Buat Sesi")
                }
            },
            dismissButton = { TextButton(onClick = { showNewSessionDialog = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun DocumentBlockView(
    block: DocBlock,
    pageNumber: Int,
    zoom: Float,
    highlights: List<ChatMessage>,
    focusedAnchor: HighlightAnchor?,
    onSentenceClick: (Int, String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = OsisColors.Surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, OsisColors.Border),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.sentences.forEachIndexed { idx, sentence ->
                val matching = highlights.filter { it.anchor?.covers(block.id, idx) == true }
                val isFocused = focusedAnchor?.covers(block.id, idx) == true
                val hasLeader = matching.any { it.senderRole == UserRole.LEADER }
                val hasParticipant = matching.any { it.senderRole == UserRole.PARTICIPANT }

                val bg = when {
                    isFocused -> OsisColors.Primary.copy(alpha = 0.35f)
                    hasLeader -> OsisColors.HighlightLeader.copy(alpha = 0.55f)
                    hasParticipant -> OsisColors.HighlightParticipant.copy(alpha = 0.65f)
                    else -> Color.Transparent
                }

                val baseSize = when (block.kind) {
                    BlockKind.TITLE -> 18f
                    BlockKind.HEADING -> 15.5f
                    BlockKind.TABLE_ROW -> 12.5f
                    else -> 14f
                }
                val weight = when (block.kind) {
                    BlockKind.TITLE, BlockKind.HEADING -> FontWeight.Bold
                    else -> FontWeight.Normal
                }
                val prefix = if (block.kind == BlockKind.BULLET && idx == 0) "•  " else ""

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(bg)
                        .clickable { onSentenceClick(idx, sentence) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    Column {
                        Text(
                            text = prefix + sentence,
                            fontSize = (baseSize * zoom).sp,
                            lineHeight = (baseSize * 1.48f * zoom).sp,
                            fontWeight = weight,
                            color = if (block.kind == BlockKind.TITLE) OsisColors.PrimaryDark else OsisColors.TextPrimary,
                        )
                        if (matching.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${matching.size} sorotan • ${matching.last().senderName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = OsisColors.TextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}
