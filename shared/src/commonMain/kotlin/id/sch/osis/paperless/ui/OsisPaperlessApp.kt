package id.sch.osis.paperless.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LockPerson
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.sch.osis.paperless.app.AppController
import id.sch.osis.paperless.app.AppTab
import id.sch.osis.paperless.domain.UnlockStatus
import id.sch.osis.paperless.protocol.NoticeLevel
import id.sch.osis.paperless.ui.components.IncomingNotificationBanner
import id.sch.osis.paperless.ui.components.StatusPill
import id.sch.osis.paperless.ui.screens.AccessControlScreen
import id.sch.osis.paperless.ui.screens.AttendanceScreen
import id.sch.osis.paperless.ui.screens.DiscussionScreen
import id.sch.osis.paperless.ui.screens.LoginScreen
import id.sch.osis.paperless.ui.screens.ProfileScreen
import id.sch.osis.paperless.ui.screens.RoomDocumentScreen
import id.sch.osis.paperless.ui.theme.OsisColors
import id.sch.osis.paperless.ui.theme.OsisPaperlessTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OsisPaperlessApp(controller: AppController) {
    OsisPaperlessTheme {
        val ui by controller.ui.collectAsState()
        val user = ui.user

        if (user == null || ui.room == null) {
            LoginScreen(ui, controller)
            return@OsisPaperlessTheme
        }

        val pendingUnlockCount = if (user.isLeader) {
            ui.room!!.unlockRequests.count { it.status == UnlockStatus.PENDING }
        } else 0

        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(OsisColors.Background)) {
            val useRail = maxWidth >= 720.dp

            Scaffold(
                containerColor = OsisColors.Background,
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = OsisColors.Surface),
                        title = {
                            Column {
                                Text(
                                    "My SeeHear",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = OsisColors.PrimaryDark,
                                )
                                Text(
                                    "${user.name} • ${user.orgLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OsisColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        actions = {
                            StatusPill(
                                text = ui.connection.label,
                                bgColor = OsisColors.PrimarySoft,
                                fgColor = OsisColors.PrimaryDark,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        },
                    )
                },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(containerColor = OsisColors.Surface, tonalElevation = 0.dp) {
                            AppTab.entries.forEach { tab ->
                                val label = if (user.isLeader) tab.leaderLabel else tab.participantLabel
                                val badgeCount = if (tab == AppTab.ACCESS) pendingUnlockCount else 0
                                NavigationBarItem(
                                    selected = ui.activeTab == tab,
                                    onClick = { controller.selectTab(tab) },
                                    icon = { TabIcon(tab, badgeCount) },
                                    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = OsisColors.PrimaryDark,
                                        selectedTextColor = OsisColors.PrimaryDark,
                                        indicatorColor = OsisColors.PrimarySoft,
                                        unselectedIconColor = OsisColors.TextSecondary,
                                        unselectedTextColor = OsisColors.TextSecondary,
                                    ),
                                )
                            }
                        }
                    }
                },
            ) { innerPadding ->
                Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    if (useRail) {
                        NavigationRail(containerColor = OsisColors.Surface, modifier = Modifier.fillMaxHeight()) {
                            AppTab.entries.forEach { tab ->
                                val label = if (user.isLeader) tab.leaderLabel else tab.participantLabel
                                val badgeCount = if (tab == AppTab.ACCESS) pendingUnlockCount else 0
                                NavigationRailItem(
                                    selected = ui.activeTab == tab,
                                    onClick = { controller.selectTab(tab) },
                                    icon = { TabIcon(tab, badgeCount) },
                                    label = { Text(label, maxLines = 1) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = OsisColors.PrimaryDark,
                                        selectedTextColor = OsisColors.PrimaryDark,
                                        indicatorColor = OsisColors.PrimarySoft,
                                    ),
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        // Banner notifikasi masuk aman (baca-saja saat Mode Fokus aktif)
                        ui.latestNotificationBanner?.let { notif ->
                            IncomingNotificationBanner(notif, onDismiss = controller::dismissNotificationBanner)
                        }

                        // Banner umpan balik singkat
                        ui.banner?.let { b ->
                            val (bg, fg) = when (b.level) {
                                NoticeLevel.SUCCESS -> OsisColors.SuccessSoft to OsisColors.SuccessDark
                                NoticeLevel.WARNING -> OsisColors.WarningSoft to OsisColors.WarningDark
                                NoticeLevel.ERROR -> OsisColors.DangerSoft to OsisColors.DangerDark
                                NoticeLevel.INFO -> OsisColors.PrimarySoft to OsisColors.PrimaryDark
                            }
                            Surface(
                                onClick = controller::dismissBanner,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                color = bg,
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text(
                                    b.message,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = fg,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            when (ui.activeTab) {
                                AppTab.ROOM -> RoomDocumentScreen(ui, controller)
                                AppTab.ATTENDANCE -> AttendanceScreen(ui)
                                AppTab.ACCESS -> AccessControlScreen(ui, controller)
                                AppTab.CHAT -> DiscussionScreen(ui, controller)
                                AppTab.PROFILE -> ProfileScreen(ui, controller)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabIcon(tab: AppTab, badgeCount: Int) {
    val icon: ImageVector = when (tab) {
        AppTab.ROOM -> Icons.Rounded.Description
        AppTab.ATTENDANCE -> Icons.Rounded.Groups
        AppTab.ACCESS -> Icons.Rounded.LockPerson
        AppTab.CHAT -> Icons.Rounded.Forum
        AppTab.PROFILE -> Icons.Rounded.Badge
    }
    if (badgeCount > 0) {
        BadgedBox(badge = { Badge(containerColor = OsisColors.Danger, contentColor = Color.White) { Text("$badgeCount") } }) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        }
    } else {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
    }
}
