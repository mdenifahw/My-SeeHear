package id.sch.osis.paperless.platform

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import id.sch.osis.paperless.core.Ids
import id.sch.osis.paperless.core.nowMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hub bersama antara [OsisNotificationListenerService] dan UI Compose.
 * Hanya menyimpan ringkasan teks aman — tidak pernah menyimpan atau memicu `PendingIntent`
 * agar peserta tetap menerima info notifikasi tanpa bisa berpindah ke aplikasi sumber.
 */
object SafeNotificationHub {
    private val _notifications = MutableStateFlow<List<IncomingNotification>>(emptyList())
    val notifications: StateFlow<List<IncomingNotification>> = _notifications.asStateFlow()

    fun push(item: IncomingNotification) {
        _notifications.value = (listOf(item) + _notifications.value).take(50)
    }

    fun clear() {
        _notifications.value = emptyList()
    }
}

class OsisNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName == packageName) return
        if (sbn.isOngoing) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val body = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            )?.toString()?.trim().orEmpty()

        if (title.isBlank() && body.isBlank()) return

        val appName = runCatching {
            val info = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrElse { sbn.packageName.substringAfterLast('.') }

        SafeNotificationHub.push(
            IncomingNotification(
                id = Ids.newId("n"),
                appName = appName,
                packageName = sbn.packageName,
                title = title.ifBlank { appName },
                body = body,
                receivedAt = sbn.postTime.takeIf { it > 0 } ?: nowMillis(),
            ),
        )
    }
}

class AndroidNotificationFeed(private val context: Context) : NotificationFeed {
    override val notifications: StateFlow<List<IncomingNotification>> = SafeNotificationHub.notifications

    private val _permissionGranted = MutableStateFlow(checkPermission())
    override val isPermissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    fun refreshPermission() {
        _permissionGranted.value = checkPermission()
    }

    private fun checkPermission(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        val flat = ComponentName(context, OsisNotificationListenerService::class.java).flattenToString()
        return enabled.contains(flat) || enabled.contains(context.packageName)
    }

    override fun openPermissionSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    override fun clear() = SafeNotificationHub.clear()

    override fun pushSimulated(appName: String, title: String, body: String) {
        SafeNotificationHub.push(
            IncomingNotification(
                id = Ids.newId("n"),
                appName = appName,
                packageName = "sim.${appName.lowercase()}",
                title = title,
                body = body,
                receivedAt = nowMillis(),
            ),
        )
    }
}
