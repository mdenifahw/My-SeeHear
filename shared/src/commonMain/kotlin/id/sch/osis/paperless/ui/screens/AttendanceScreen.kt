package id.sch.osis.paperless.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.sch.osis.paperless.app.AppUiState
import id.sch.osis.paperless.domain.OrgCatalog
import id.sch.osis.paperless.domain.OrgCategory
import id.sch.osis.paperless.domain.PresenceStatus
import id.sch.osis.paperless.domain.UserProfile
import id.sch.osis.paperless.ui.components.AvatarBadge
import id.sch.osis.paperless.ui.components.OrgBadge
import id.sch.osis.paperless.ui.components.OsisCard
import id.sch.osis.paperless.ui.components.SectionHeader
import id.sch.osis.paperless.ui.components.StatusPill
import id.sch.osis.paperless.ui.theme.OsisColors

@Composable
fun AttendanceScreen(ui: AppUiState) {
    val room = ui.room ?: return
    val participants = room.participants
    var selectedCategory by remember { mutableStateOf<OrgCategory?>(null) }

    val onlineCount = participants.count { room.presenceOf(it.id)?.status == PresenceStatus.ONLINE }
    val leaveCount = participants.count { room.presenceOf(it.id)?.status == PresenceStatus.ON_LEAVE }
    val offlineCount = participants.size - onlineCount - leaveCount

    val filtered = remember(participants, selectedCategory) {
        if (selectedCategory == null) participants else participants.filter { it.category == selectedCategory }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "kpi") {
            SectionHeader(
                title = "Kehadiran Ruang Rapat",
                subtitle = "Data real-time seluruh perwakilan OSIS, Ekstrakurikuler & MPK",
            )
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpiCard("Hadir", onlineCount.toString(), Icons.Rounded.CheckCircle, OsisColors.SuccessSoft, OsisColors.SuccessDark, Modifier.weight(1f))
                KpiCard("Izin Keluar", leaveCount.toString(), Icons.Rounded.DirectionsWalk, OsisColors.WarningSoft, OsisColors.WarningDark, Modifier.weight(1f))
                KpiCard("Belum Hadir", offlineCount.toString(), Icons.Rounded.Schedule, OsisColors.DangerSoft, OsisColors.DangerDark, Modifier.weight(1f))
                KpiCard("Total", participants.size.toString(), Icons.Rounded.Groups, OsisColors.PrimarySoft, OsisColors.PrimaryDark, Modifier.weight(1f))
            }
        }

        // Statistik per Kategori Organisasi
        item(key = "category_stats") {
            OsisCard(modifier = Modifier.fillMaxWidth()) {
                Text("Statistik per Kategori Organisasi", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OrgCatalog.participantCategories.forEach { cat ->
                        val members = participants.filter { it.category == cat }
                        val active = members.count {
                            val st = room.presenceOf(it.id)?.status
                            st == PresenceStatus.ONLINE || st == PresenceStatus.ON_LEAVE
                        }
                        val ratio = if (members.isEmpty()) 0f else active.toFloat() / members.size
                        val (bg, fg) = OsisColors.categoryColors(cat)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(cat.label, style = MaterialTheme.typography.labelLarge, color = OsisColors.TextPrimary)
                                Text("$active / ${members.size} hadir", style = MaterialTheme.typography.labelMedium, color = fg, fontWeight = FontWeight.SemiBold)
                            }
                            Box(
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)).background(bg),
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth(ratio).fillMaxHeight().clip(RoundedCornerShape(999.dp)).background(fg),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Breakdown Rinci: Sekbid 1–7, Fraksi 1 & 3, Ekskul, MPK
        item(key = "unit_breakdown") {
            OsisCard(modifier = Modifier.fillMaxWidth()) {
                Text("Rincian Unit Organisasi", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val grouped = participants.groupBy { it.category to it.unit }
                    .entries
                    .sortedWith(compareBy({ it.key.first.ordinal }, { it.key.second }))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    grouped.forEach { (pair, members) ->
                        val (cat, unit) = pair
                        val present = members.count {
                            val st = room.presenceOf(it.id)?.status
                            st == PresenceStatus.ONLINE || st == PresenceStatus.ON_LEAVE
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OrgBadge(cat, unit)
                                if (cat == OrgCategory.SEKBID) {
                                    Text(
                                        OrgCatalog.sekbidDescriptions[unit].orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OsisColors.TextSecondary,
                                        maxLines = 1,
                                    )
                                }
                            }
                            Text(
                                "$present/${members.size}",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (present == members.size && members.isNotEmpty()) OsisColors.SuccessDark else OsisColors.TextSecondary,
                            )
                        }
                    }
                }
            }
        }

        // Filter daftar peserta
        item(key = "filter_row") {
            SectionHeader("Daftar Peserta (${filtered.size})")
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("Semua") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OsisColors.PrimarySoft,
                            selectedLabelColor = OsisColors.PrimaryDark,
                        ),
                    )
                }
                items(OrgCatalog.participantCategories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat.shortLabel) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OsisColors.PrimarySoft,
                            selectedLabelColor = OsisColors.PrimaryDark,
                        ),
                    )
                }
            }
        }

        items(filtered, key = { it.id }) { member ->
            ParticipantPresenceRow(member, room.presenceOf(member.id)?.status ?: PresenceStatus.OFFLINE, room.presenceOf(member.id)?.device?.label)
        }
    }
}

@Composable
private fun KpiCard(title: String, value: String, icon: ImageVector, bg: Color, fg: Color, modifier: Modifier) {
    OsisCard(modifier = modifier, padding = 10.dp) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(bg),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, color = OsisColors.TextPrimary)
        Text(title, style = MaterialTheme.typography.labelSmall, color = OsisColors.TextSecondary, maxLines = 1)
    }
}

@Composable
private fun ParticipantPresenceRow(user: UserProfile, status: PresenceStatus, deviceLabel: String?) {
    OsisCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AvatarBadge(user.initials, user.category)
            Column(Modifier.weight(1f)) {
                Text(user.name, style = MaterialTheme.typography.titleSmall, color = OsisColors.TextPrimary)
                Text(
                    "${user.kelas} • ${user.orgLabel}" + (deviceLabel?.let { " • $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = OsisColors.TextSecondary,
                )
            }
            val (bg, fg) = when (status) {
                PresenceStatus.ONLINE -> OsisColors.SuccessSoft to OsisColors.SuccessDark
                PresenceStatus.ON_LEAVE -> OsisColors.WarningSoft to OsisColors.WarningDark
                PresenceStatus.OFFLINE -> Color(0xFFF1F5F9) to OsisColors.TextSecondary
            }
            StatusPill(status.label, bg, fg)
        }
    }
}
