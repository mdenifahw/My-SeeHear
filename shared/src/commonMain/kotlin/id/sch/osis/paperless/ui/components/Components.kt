package id.sch.osis.paperless.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.sch.osis.paperless.core.formatCountdown
import id.sch.osis.paperless.domain.OrgCategory
import id.sch.osis.paperless.domain.SessionStatus
import id.sch.osis.paperless.platform.IncomingNotification
import id.sch.osis.paperless.ui.theme.OsisColors

@Composable
fun OsisCard(
    modifier: Modifier = Modifier,
    containerColor: Color = OsisColors.Surface,
    borderColor: Color = OsisColors.Border,
    padding: Dp = 14.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(15.dp)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
        ) {
            Column(Modifier.padding(padding), content = content)
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
        ) {
            Column(Modifier.padding(padding), content = content)
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    bgColor: Color,
    fgColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Surface(
        modifier = modifier,
        color = bgColor,
        shape = RoundedCornerShape(999.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = fgColor, modifier = Modifier.size(13.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = fgColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun OrgBadge(category: OrgCategory, label: String, modifier: Modifier = Modifier) {
    val (bg, fg) = OsisColors.categoryColors(category)
    StatusPill(text = label, bgColor = bg, fgColor = fg, modifier = modifier)
}

@Composable
fun AvatarBadge(initials: String, category: OrgCategory, size: Dp = 38.dp) {
    val (bg, fg) = OsisColors.categoryColors(category)
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = fg,
        )
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = OsisColors.TextPrimary)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OsisColors.TextSecondary)
            }
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun FocusModeBanner(
    sessionStatus: SessionStatus,
    isLeader: Boolean,
    isLockedForMe: Boolean,
    remainingUnlockMillis: Long?,
    remainingBreakMillis: Long?,
) {
    val (bg, fg, icon, text) = when {
        sessionStatus == SessionStatus.LIVE && isLockedForMe -> Quad(
            OsisColors.DangerSoft,
            OsisColors.DangerDark,
            Icons.Rounded.Lock,
            "Rapat Berlangsung — Mode Fokus Aktif (Perangkat Terkunci)",
        )
        sessionStatus == SessionStatus.LIVE && !isLeader && remainingUnlockMillis != null -> Quad(
            OsisColors.SuccessSoft,
            OsisColors.SuccessDark,
            Icons.Rounded.LockOpen,
            "Izin Sementara Aktif — Sisa ${formatCountdown(remainingUnlockMillis)} sebelum terkunci kembali",
        )
        sessionStatus == SessionStatus.LIVE && isLeader -> Quad(
            OsisColors.PrimarySoft,
            OsisColors.PrimaryDark,
            Icons.Rounded.Lock,
            "Rapat Berlangsung — Mode Fokus Peserta Aktif",
        )
        sessionStatus == SessionStatus.BREAK -> Quad(
            OsisColors.WarningSoft,
            OsisColors.WarningDark,
            Icons.Rounded.PauseCircle,
            "Istirahat" + (remainingBreakMillis?.let { " — Sisa ${formatCountdown(it)}" } ?: "") + " (Kunci Dibuka Sementara)",
        )
        sessionStatus == SessionStatus.ENDED -> Quad(
            OsisColors.SuccessSoft,
            OsisColors.SuccessDark,
            Icons.Rounded.LockOpen,
            "Rapat Selesai — Rekap revisi tersedia di Profil",
        )
        else -> Quad(
            OsisColors.PrimarySoft,
            OsisColors.PrimaryDark,
            Icons.Rounded.LockOpen,
            "Persiapan Rapat — Menunggu Pimpinan memulai sesi",
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = bg,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = fg, modifier = Modifier.weight(1f))
        }
    }
}

/** Banner pratinjau notifikasi masuk (baca-saja, klik aplikasi eksternal diblokir). */
@Composable
fun IncomingNotificationBanner(notif: IncomingNotification, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        color = OsisColors.TextPrimary,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.NotificationsActive, null, tint = OsisColors.Primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${notif.appName} • Pratinjau Aman (Terkunci)",
                    style = MaterialTheme.typography.labelSmall,
                    color = OsisColors.Primary,
                )
                Text(
                    notif.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (notif.body.isNotBlank()) {
                    Text(
                        notif.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Tutup", tint = Color(0xFFCBD5E1), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun EmptyPlaceholder(icon: ImageVector, title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(OsisColors.PrimarySoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = OsisColors.PrimaryDark, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = OsisColors.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OsisColors.TextSecondary)
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
