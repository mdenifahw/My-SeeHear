package id.sch.osis.paperless.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun nowMillis(): Long = System.currentTimeMillis()

actual val currentDeviceType: DeviceType = DeviceType.DESKTOP

private val idLocale = Locale.forLanguageTag("id-ID")

actual fun formatTime(epochMillis: Long): String =
    SimpleDateFormat("HH:mm", idLocale).format(Date(epochMillis))

actual fun formatDate(epochMillis: Long): String =
    SimpleDateFormat("dd MMM yyyy", idLocale).format(Date(epochMillis))
